package main

import (
	"context"
	"time"

	"github.com/robfig/cron/v3"
)

type Scheduler struct {
	cron    *cron.Cron
	entries map[int]cron.EntryID
}

func NewScheduler() *Scheduler {
	return &Scheduler{
		cron:    cron.New(),
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
		executeJob(job)
	})
	if err != nil {
		appLogger.Printf("[%s] 调度表达式无效 \"%s\": %v", job.Name, job.Schedule, err)
		return
	}
	s.entries[job.ID] = entryID
}
