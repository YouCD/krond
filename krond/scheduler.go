package main

import (
	"context"
	"io"
	"log"
	"sync"
	"time"

	"github.com/robfig/cron/v3"
	"golang.org/x/sys/unix"
)

type Scheduler struct {
	mu         sync.Mutex
	cron       *cron.Cron
	entries    map[int]cron.EntryID
	jobs       []Job
	wakeCancel chan struct{}
	wakeStop   chan struct{}
}

func NewScheduler(writer io.Writer) *Scheduler {
	cronLogger := log.New(writer, "[cron] ", log.Ldate|log.Ltime)
	return &Scheduler{
		cron: cron.New(
			cron.WithLocation(time.Local),
			cron.WithLogger(cron.VerbosePrintfLogger(cronLogger)),
			cron.WithChain(cron.Recover(cron.VerbosePrintfLogger(cronLogger))),
		),
		entries: make(map[int]cron.EntryID),
	}
}

func (s *Scheduler) Start() {
	s.mu.Lock()
	s.wakeCancel = make(chan struct{}, 1)
	s.wakeStop = make(chan struct{})
	s.mu.Unlock()

	s.cron.Start()
	go s.runWakeLoop()
}

func (s *Scheduler) Stop() context.Context {
	s.mu.Lock()
	if s.wakeStop != nil {
		close(s.wakeStop)
	}
	s.mu.Unlock()
	return s.cron.Stop()
}

func (s *Scheduler) LoadJobs(jobs []Job) {
	s.mu.Lock()
	s.jobs = append([]Job{}, jobs...)
	s.mu.Unlock()

	for _, entryID := range s.entries {
		s.cron.Remove(entryID)
	}
	s.entries = make(map[int]cron.EntryID)

	for _, job := range jobs {
		if job.Enabled {
			s.addJobEntry(job)
		}
	}

	s.notifyWake()
}

func (s *Scheduler) AddJob(job Job) {
	s.entries[job.ID] = 0
	if job.Enabled {
		s.addJobEntry(job)
	}
	s.notifyWake()
}

func (s *Scheduler) RemoveJob(id int) {
	if entryID, ok := s.entries[id]; ok {
		s.cron.Remove(entryID)
		delete(s.entries, id)
	}
	s.notifyWake()
}

func (s *Scheduler) UpdateJob(job Job) {
	if entryID, ok := s.entries[job.ID]; ok {
		s.cron.Remove(entryID)
	}
	if job.Enabled {
		s.addJobEntry(job)
	} else {
		delete(s.entries, job.ID)
	}
	s.notifyWake()
}

func (s *Scheduler) IsRunning() bool {
	return s.cron.Entries() != nil
}

func (s *Scheduler) CronEntries() []cron.Entry {
	return s.cron.Entries()
}

func (s *Scheduler) EnabledCount() int {
	count := 0
	for _, id := range s.entries {
		if id != 0 {
			count++
		}
	}
	return count
}

func (s *Scheduler) Healthy() bool {
	if s.cron.Entries() == nil {
		return false
	}
	return true
}

func (s *Scheduler) EntryCount() int {
	return len(s.cron.Entries())
}

func (s *Scheduler) HasStaleEntry() bool {
	now := time.Now()
	const staleThreshold = 2 * time.Minute
	for _, e := range s.cron.Entries() {
		if !e.Next.IsZero() && now.Sub(e.Next) > staleThreshold {
			return true
		}
	}
	return false
}

func (s *Scheduler) Reload(jobs []Job) {
	s.mu.Lock()
	s.jobs = append([]Job{}, jobs...)
	s.mu.Unlock()

	appLogger.Println("[调度器] 重新加载任务...")
	s.cron.Stop()
	s.cron = cron.New(
		cron.WithLocation(time.Local),
		cron.WithLogger(cron.VerbosePrintfLogger(log.New(logWriter, "[cron] ", log.Ldate|log.Ltime))),
		cron.WithChain(cron.Recover(cron.VerbosePrintfLogger(log.New(logWriter, "[cron] ", log.Ldate|log.Ltime)))),
	)
	s.entries = make(map[int]cron.EntryID)
	for _, job := range jobs {
		if job.Enabled {
			s.addJobEntry(job)
		}
	}
	s.cron.Start()
	appLogger.Printf("[调度器] 重新加载完成, %d 个活跃任务", len(s.entries))

	s.notifyWake()
}

func (s *Scheduler) NearestJobTime() time.Time {
	s.mu.Lock()
	defer s.mu.Unlock()

	var earliest time.Time
	now := time.Now()
	for _, job := range s.jobs {
		if !job.Enabled {
			continue
		}
		entryID, ok := s.entries[job.ID]
		if !ok {
			continue
		}
		entry := s.cron.Entry(entryID)
		if entry.Next.IsZero() {
			continue
		}
		if entry.Next.After(now) && (earliest.IsZero() || entry.Next.Before(earliest)) {
			earliest = entry.Next
		}
	}
	return earliest
}

func (s *Scheduler) PrintJobs(jobs []Job) {
	rev := make(map[cron.EntryID]int)
	for jobID, entryID := range s.entries {
		rev[entryID] = jobID
	}
	nameMap := make(map[int]string)
	for _, j := range jobs {
		nameMap[j.ID] = j.Name
	}

	entries := s.cron.Entries()
	appLogger.Printf("调度器: %d 个活跃条目", len(entries))
	for _, e := range entries {
		jobID, ok := rev[e.ID]
		name := "?"
		if ok {
			if n, found := nameMap[jobID]; found {
				name = n
			}
		}
		next := "-"
		if !e.Next.IsZero() {
			next = e.Next.Format("15:04:05")
		}
		prev := "-"
		if !e.Prev.IsZero() {
			prev = e.Prev.Format("15:04:05")
		}
		appLogger.Printf("  [%s] job=%d next=%s prev=%s", name, jobID, next, prev)
	}
}

func (s *Scheduler) NextRun(id int) (time.Time, bool) {
	entryID, ok := s.entries[id]
	if !ok {
		return time.Time{}, false
	}
	entry := s.cron.Entry(entryID)
	if entry.Next.IsZero() {
		return time.Time{}, false
	}
	return entry.Next, true
}

func (s *Scheduler) MissedJobs(jobs []Job) []Job {
	var missed []Job
	now := time.Now()
	for _, job := range jobs {
		if !job.Enabled {
			continue
		}
		next, ok := s.NextRun(job.ID)
		if !ok || next.IsZero() {
			continue
		}
		if now.Sub(next) > 2*time.Minute {
			missed = append(missed, job)
		}
	}
	return missed
}

func (s *Scheduler) addJobEntry(job Job) {
	entryID, err := s.cron.AddFunc(job.Schedule, func() {
		executeJobFn(job)
	})
	if err != nil {
		appLogger.Printf("[%s] 调度表达式无效 \"%s\": %v", job.Name, job.Schedule, err)
		return
	}
	s.entries[job.ID] = entryID
}

func (s *Scheduler) notifyWake() {
	s.mu.Lock()
	defer s.mu.Unlock()
	select {
	case s.wakeCancel <- struct{}{}:
	default:
	}
}

func (s *Scheduler) dueJobs(jobs []Job) []Job {
	var due []Job
	now := time.Now()
	for _, job := range jobs {
		if !job.Enabled {
			continue
		}
		next, ok := s.NextRun(job.ID)
		if !ok || next.IsZero() {
			continue
		}
		if !next.After(now) {
			due = append(due, job)
		}
	}
	return due
}

func (s *Scheduler) checkStaleAndCatchup() {
	s.mu.Lock()
	jobs := append([]Job{}, s.jobs...)
	s.mu.Unlock()

	if due := s.dueJobs(jobs); len(due) > 0 {
		appLogger.Printf("[wakeup] 发现 %d 个到期任务, 执行中...", len(due))
		for _, job := range due {
			appLogger.Printf("[wakeup] 执行到期任务 [%s] (原定 %s)", job.Name, job.Schedule)
			executeJobFn(job)
		}
		s.Reload(jobs)
	}
}

func (s *Scheduler) runWakeLoop() {
	timer, err := NewWakeTimer()
	if err != nil {
		appLogger.Printf("[wakeup] timerfd 创建失败: %v, 降级为纯 cron 调度", err)
		return
	}
	defer timer.Close()

	appLogger.Println("[wakeup] timerfd(CLOCK_BOOTTIME_ALARM) 初始化成功, 唤醒调度已启用")

	for {
		next := s.NearestJobTime()
		if next.IsZero() {
			appLogger.Printf("[wakeup] 无启用任务, 等待变更信号")
			select {
			case <-s.wakeStop:
				return
			case <-s.wakeCancel:
				continue
			case <-time.After(30 * time.Second):
				continue
			}
		}

		if err := timer.Set(next); err != nil {
			appLogger.Printf("[wakeup] 设置唤醒闹钟失败: %v, 30秒后重试", err)
			select {
			case <-s.wakeStop:
				return
			case <-s.wakeCancel:
				continue
			case <-time.After(30 * time.Second):
				continue
			}
		}

		appLogger.Printf("[wakeup] 下次唤醒: %s", next.Format("01/02 15:04:05"))

		var buf [8]byte
		_, err = unix.Read(timer.Fd(), buf[:])
		if err != nil {
			appLogger.Printf("[wakeup] timerfd read: %v, 10秒后重试", err)
			select {
			case <-s.wakeStop:
				return
			case <-time.After(10 * time.Second):
				continue
			}
		}

		s.checkStaleAndCatchup()
	}
}
