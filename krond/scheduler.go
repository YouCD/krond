package main

import (
	"context"
	"io"
	"log"
	"time"

	"github.com/robfig/cron/v3"
)

type Scheduler struct {
	cron    *cron.Cron
	entries map[int]cron.EntryID
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
	s.cron.Start()
}

func (s *Scheduler) Stop() context.Context {
	return s.cron.Stop()
}

func (s *Scheduler) LoadJobs(jobs []Job) {
	// Remove all existing entries
	for _, entryID := range s.entries {
		s.cron.Remove(entryID)
	}
	s.entries = make(map[int]cron.EntryID)

	// Add enabled jobs
	for _, job := range jobs {
		if job.Enabled {
			s.addJobEntry(job)
		}
	}
}

func (s *Scheduler) AddJob(job Job) {
	s.entries[job.ID] = 0 // placeholder to track ID
	if job.Enabled {
		s.addJobEntry(job)
	}
}

func (s *Scheduler) RemoveJob(id int) {
	if entryID, ok := s.entries[id]; ok {
		s.cron.Remove(entryID)
		delete(s.entries, id)
	}
}

func (s *Scheduler) UpdateJob(job Job) {
	// Remove old entry if exists
	if entryID, ok := s.entries[job.ID]; ok {
		s.cron.Remove(entryID)
	}
	// Add new entry if enabled
	if job.Enabled {
		s.addJobEntry(job)
	} else {
		delete(s.entries, job.ID)
	}
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

func (s *Scheduler) AllNextInPast() bool {
	now := time.Now()
	for _, e := range s.cron.Entries() {
		if e.Next.IsZero() || e.Next.After(now) {
			return false
		}
	}
	return len(s.cron.Entries()) > 0
}

func (s *Scheduler) Reload(jobs []Job) {
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
