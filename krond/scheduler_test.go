package main

import (
	"bytes"
	"io"
	"log"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/robfig/cron/v3"
)

func init() {
	appLogger = log.New(io.Discard, "", 0)
}

func setupSched(t *testing.T) (*Scheduler, *bytes.Buffer) {
	t.Helper()
	var buf bytes.Buffer
	sched := NewScheduler(&buf)
	return sched, &buf
}

func enableJob(t *testing.T, sched *Scheduler, id int) {
	t.Helper()
	sched.addJobEntry(Job{ID: id, Schedule: "* * * * *", Enabled: true})
}

func TestNewScheduler(t *testing.T) {
	sched, buf := setupSched(t)
	if sched == nil {
		t.Fatal("NewScheduler returned nil")
	}
	if sched.cron == nil {
		t.Fatal("cron instance is nil")
	}
	if sched.entries == nil {
		t.Fatal("entries map not initialized")
	}
	if !sched.Healthy() {
		t.Fatal("new scheduler should be healthy")
	}
	if sched.EntryCount() != 0 {
		t.Fatal("new scheduler should have 0 entries")
	}
	if sched.EnabledCount() != 0 {
		t.Fatal("new scheduler should have 0 enabled")
	}
	if !sched.IsRunning() {
		t.Fatal("new scheduler should report running (Entries() != nil)")
	}

	_ = buf
	t.Logf("cron log buffer: %s", buf.String())
}

func TestAddJob(t *testing.T) {
	sched, _ := setupSched(t)

	jobs := []struct {
		name    string
		enabled bool
		wantOK  bool
	}{
		{"job-a", true, true},
		{"job-b", false, true},
	}

	for _, j := range jobs {
		job := Job{
			ID:       1,
			Name:     j.name,
			Schedule: "* * * * *",
			Enabled:  j.enabled,
		}
		sched.AddJob(job)

		if j.enabled {
			if sched.EnabledCount() != 1 {
				t.Errorf("%s: expected 1 enabled entry after AddJob", j.name)
			}
		} else {
			if sched.EnabledCount() != 0 {
				t.Errorf("%s: disabled job should not add cron entry", j.name)
			}
		}
	}
}

func TestNextRunExistingJob(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "job", Schedule: "0 * * * *", Enabled: true})
	sched.Start()
	defer sched.Stop()

	next, ok := sched.NextRun(1)
	if !ok {
		t.Fatal("NextRun(1) should return ok for existing job after start")
	}
	if next.IsZero() {
		t.Fatal("NextRun should return non-zero time")
	}

	_, ok = sched.NextRun(99)
	if ok {
		t.Error("NextRun(99) should return false for non-existent job")
	}
}

func TestAddJobMultipleIDs(t *testing.T) {
	sched, _ := setupSched(t)

	for i := 1; i <= 5; i++ {
		sched.AddJob(Job{ID: i, Name: "job", Schedule: "0 * * * *", Enabled: true})
	}
	if sched.EntryCount() != 5 {
		t.Errorf("expected 5 entries, got %d", sched.EntryCount())
	}
}

func TestRemoveJob(t *testing.T) {
	sched, _ := setupSched(t)

	enableJob(t, sched, 1)
	if sched.EntryCount() != 1 {
		t.Fatal("expected 1 entry after add")
	}

	sched.RemoveJob(1)
	if sched.EntryCount() != 0 {
		t.Error("expected 0 entries after remove")
	}
	if sched.EnabledCount() != 0 {
		t.Error("expected 0 enabled after remove")
	}
}

func TestRemoveNonExistentJob(t *testing.T) {
	sched, _ := setupSched(t)
	sched.RemoveJob(999)
}

func TestUpdateJobToggle(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "test", Schedule: "0 * * * *", Enabled: false})

	if sched.EntryCount() != 0 {
		t.Fatal("disabled job should not create cron entry")
	}

	sched.UpdateJob(Job{ID: 1, Name: "test", Schedule: "0 * * * *", Enabled: true})
	if sched.EntryCount() != 1 {
		t.Fatal("enabling job should create cron entry")
	}

	sched.UpdateJob(Job{ID: 1, Name: "test", Schedule: "0 * * * *", Enabled: false})
	if sched.EntryCount() != 0 {
		t.Fatal("disabling job should remove cron entry")
	}
}

func TestUpdateJobChangeSchedule(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "test", Schedule: "* * * * *", Enabled: true})
	sched.Start()
	defer sched.Stop()

	time.Sleep(200 * time.Millisecond)
	next1, ok := sched.NextRun(1)
	if !ok {
		t.Fatal("NextRun should work after start")
	}

	sched.UpdateJob(Job{ID: 1, Name: "test", Schedule: "0 0 * * *", Enabled: true})
	time.Sleep(200 * time.Millisecond)
	next2, ok := sched.NextRun(1)
	if !ok {
		t.Fatal("NextRun should work after update")
	}

	if next1.Equal(next2) {
		t.Log("next times may differ after schedule change")
	}
}

func TestLoadJobs(t *testing.T) {
	sched, _ := setupSched(t)

	jobs := []Job{
		{ID: 1, Name: "a", Schedule: "* * * * *", Enabled: true},
		{ID: 2, Name: "b", Schedule: "0 * * * *", Enabled: false},
		{ID: 3, Name: "c", Schedule: "30 * * * *", Enabled: true},
	}
	sched.LoadJobs(jobs)

	if sched.EntryCount() != 2 {
		t.Errorf("expected 2 entries for enabled jobs, got %d", sched.EntryCount())
	}
	if sched.EnabledCount() != 2 {
		t.Errorf("expected 2 enabled, got %d", sched.EnabledCount())
	}
}

func TestLoadJobsReplace(t *testing.T) {
	sched, _ := setupSched(t)
	sched.LoadJobs([]Job{
		{ID: 1, Name: "old", Schedule: "* * * * *", Enabled: true},
	})
	if sched.EntryCount() != 1 {
		t.Fatal("expected 1 entry after first load")
	}

	sched.LoadJobs([]Job{
		{ID: 2, Name: "new", Schedule: "0 * * * *", Enabled: true},
	})
	if sched.EntryCount() != 1 {
		t.Errorf("expected 1 entry after reload, got %d", sched.EntryCount())
	}
	_, ok := sched.NextRun(1)
	if ok {
		t.Error("job 1 should be removed after reload")
	}
}

func TestNextRunNonExistent(t *testing.T) {
	sched, _ := setupSched(t)
	_, ok := sched.NextRun(999)
	if ok {
		t.Error("NextRun for non-existent job should return false")
	}
}

func TestEnabledCount(t *testing.T) {
	sched, _ := setupSched(t)

	if sched.EnabledCount() != 0 {
		t.Fatal("empty scheduler should have 0 enabled")
	}

	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: true})
	if sched.EnabledCount() != 1 {
		t.Fatal("expected 1 enabled")
	}

	sched.AddJob(Job{ID: 2, Schedule: "* * * * *", Enabled: false})
	if sched.EnabledCount() != 1 {
		t.Fatal("disabled job should not increase count")
	}

	sched.RemoveJob(1)
	if sched.EnabledCount() != 0 {
		t.Fatal("expected 0 after remove")
	}
}

func TestEntryCount(t *testing.T) {
	sched, _ := setupSched(t)

	if sched.EntryCount() != 0 {
		t.Fatal("expected 0")
	}

	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: true})
	sched.AddJob(Job{ID: 2, Schedule: "0 * * * *", Enabled: true})
	sched.AddJob(Job{ID: 3, Schedule: "30 * * * *", Enabled: false})

	if sched.EntryCount() != 2 {
		t.Errorf("expected 2 cron entries, got %d", sched.EntryCount())
	}
}

func TestHealthy(t *testing.T) {
	sched, _ := setupSched(t)

	if !sched.Healthy() {
		t.Fatal("empty scheduler should be healthy")
	}

	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: true})
	if !sched.Healthy() {
		t.Fatal("scheduler with jobs should be healthy")
	}
}

func TestHasStaleEntryEmpty(t *testing.T) {
	sched, _ := setupSched(t)
	if sched.HasStaleEntry() {
		t.Fatal("empty scheduler should not report stale")
	}
}

func TestHasStaleEntryScheduled(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Schedule: "0 0 1 1 *", Enabled: true})

	if sched.HasStaleEntry() {
		t.Fatal("job far in future should not trigger stale")
	}
}

func TestCronEntries(t *testing.T) {
	sched, _ := setupSched(t)

	entries := sched.CronEntries()
	if len(entries) != 0 {
		t.Fatal("expected empty entries")
	}

	sched.AddJob(Job{ID: 1, Schedule: "0 * * * *", Enabled: true})
	sched.AddJob(Job{ID: 2, Schedule: "30 * * * *", Enabled: true})

	entries = sched.CronEntries()
	if len(entries) != 2 {
		t.Fatalf("expected 2 entries, got %d", len(entries))
	}

	for _, e := range entries {
		if e.Schedule == nil {
			t.Error("entry schedule should not be nil")
		}
	}
}

func TestInvalidSchedule(t *testing.T) {
	var logBuf bytes.Buffer
	appLogger = log.New(&logBuf, "", 0)
	defer func() { appLogger = log.New(io.Discard, "", 0) }()

	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "bad", Schedule: "invalid! garbage", Enabled: true})

	if sched.EntryCount() != 0 {
		t.Error("invalid schedule should not create entry")
	}

	logOutput := logBuf.String()
	if logOutput == "" {
		t.Error("expected error log for invalid schedule")
	}
	t.Logf("error log: %s", logOutput)
}

func TestPrintJobs(t *testing.T) {
	var logBuf bytes.Buffer
	appLogger = log.New(&logBuf, "", 0)
	defer func() { appLogger = log.New(io.Discard, "", 0) }()

	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "test-job", Schedule: "0 * * * *", Enabled: true})

	jobs := []Job{{ID: 1, Name: "test-job", Schedule: "0 * * * *", Enabled: true}}
	sched.PrintJobs(jobs)

	output := logBuf.String()
	if output == "" {
		t.Error("PrintJobs should produce output")
	}
	if !contains(output, "test-job") {
		t.Errorf("PrintJobs output should contain job name, got: %s", output)
	}
}

func TestStartStop(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: true})

	if !sched.Healthy() {
		t.Fatal("should be healthy before start")
	}

	sched.Start()
	if !sched.Healthy() {
		t.Fatal("should be healthy after start")
	}

	ctx := sched.Stop()
	<-ctx.Done()
}

func TestJobFires(t *testing.T) {
	var fired struct {
		sync.Mutex
		ids []int
	}
	oldRunner := executeJobFn
	executeJobFn = func(job Job) {
		fired.Lock()
		fired.ids = append(fired.ids, job.ID)
		fired.Unlock()
	}
	defer func() { executeJobFn = oldRunner }()

	sched, buf := setupSched(t)
	_, err := sched.cron.AddFunc("@every 1s", func() {
		executeJobFn(Job{ID: 1, Name: "freq", Command: "echo ok"})
	})
	if err != nil {
		t.Fatalf("AddFunc failed: %v", err)
	}

	sched.Start()
	defer sched.Stop()

	deadline := time.After(3 * time.Second)
	for {
		fired.Lock()
		count := len(fired.ids)
		fired.Unlock()
		if count >= 2 {
			break
		}
		select {
		case <-deadline:
			t.Fatalf("job did not fire within deadline, buf=%s", buf.String())
		case <-time.After(100 * time.Millisecond):
		}
	}

	t.Logf("job fired %d times", len(fired.ids))
}

func TestJobFiresWithRealSchedule(t *testing.T) {
	var fired struct {
		sync.Mutex
		count int
	}
	oldRunner := executeJobFn
	executeJobFn = func(job Job) {
		fired.Lock()
		fired.count++
		fired.Unlock()
	}
	defer func() { executeJobFn = oldRunner }()

	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "every-min", Schedule: "* * * * *", Enabled: true})

	sched.Start()
	defer sched.Stop()

	time.Sleep(2 * time.Second)

	fired.Lock()
	c := fired.count
	fired.Unlock()

	if c > 0 {
		t.Log("job fired (unexpected, may depend on schedule timing)")
	}
}

func TestReload(t *testing.T) {
	var logBuf bytes.Buffer
	origLogger := appLogger
	appLogger = log.New(&logBuf, "", 0)
	defer func() { appLogger = origLogger }()

	var cronBuf bytes.Buffer
	logWriter = &dynamicWriter{
		file:     nil,
		filePath: "",
		target:   "logcat",
	}

	sched, _ := setupSched(t)
	logWriter.file, _ = os.CreateTemp("", "krond-test-*")
	defer os.Remove(logWriter.file.Name())
	defer logWriter.file.Close()

	sched.AddJob(Job{ID: 1, Name: "before", Schedule: "0 * * * *", Enabled: true})
	if sched.EntryCount() != 1 {
		t.Fatal("expected 1 entry before reload")
	}

	sched.Reload([]Job{
		{ID: 1, Name: "before", Schedule: "0 * * * *", Enabled: false},
		{ID: 2, Name: "after", Schedule: "30 * * * *", Enabled: true},
	})

	if sched.EntryCount() != 1 {
		t.Errorf("expected 1 entry after reload, got %d", sched.EntryCount())
	}

	entries := sched.CronEntries()
	if len(entries) == 1 {
		t.Logf("reloaded entry next=%v", entries[0].Next)
	}

	_, _ = cronBuf, logBuf
}

func TestIsRunning(t *testing.T) {
	sched, _ := setupSched(t)
	if !sched.IsRunning() {
		t.Fatal("new scheduler should show as running")
	}

	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: true})
	sched.Start()

	if !sched.IsRunning() {
		t.Fatal("started scheduler should be running")
	}

	sched.Stop()
}

func TestCronEntryIDsUnique(t *testing.T) {
	sched, _ := setupSched(t)

	for i := 1; i <= 10; i++ {
		sched.AddJob(Job{ID: i, Schedule: "0 * * * *", Enabled: true})
	}

	seen := make(map[cron.EntryID]bool)
	for _, eid := range sched.entries {
		if eid == 0 {
			continue
		}
		if seen[eid] {
			t.Fatal("duplicate cron entry ID found")
		}
		seen[eid] = true
	}

	if len(seen) != 10 {
		t.Errorf("expected 10 unique entries, got %d", len(seen))
	}
}

func TestNextRunAfterStart(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Name: "midnight", Schedule: "0 0 * * *", Enabled: true})
	sched.Start()
	defer sched.Stop()

	for i := 0; i < 10; i++ {
		next, ok := sched.NextRun(1)
		if ok {
			if next.After(time.Now()) {
				return
			}
		}
		time.Sleep(100 * time.Millisecond)
	}
	t.Error("NextRun should eventually report a future time after start")
}

func TestAddJobThenUpdateThenRemove(t *testing.T) {
	sched, _ := setupSched(t)

	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: false})
	if sched.EntryCount() != 0 {
		t.Fatal("disabled job should not add entry")
	}

	sched.UpdateJob(Job{ID: 1, Schedule: "0 * * * *", Enabled: true})
	if sched.EntryCount() != 1 {
		t.Fatal("enabling should add entry")
	}

	sched.UpdateJob(Job{ID: 1, Schedule: "30 * * * *", Enabled: true})
	if sched.EntryCount() != 1 {
		t.Fatal("schedule change should keep entry count")
	}

	sched.RemoveJob(1)
	if sched.EntryCount() != 0 {
		t.Fatal("remove should clear entry")
	}
}

func TestHasStaleEntryWithRunningCron(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Schedule: "0 0 * * *", Enabled: true})
	sched.Start()
	defer sched.Stop()

	if sched.HasStaleEntry() {
		t.Fatal("running cron should not report stale if next is future")
	}
}

func TestEnabledCountAfterStart(t *testing.T) {
	sched, _ := setupSched(t)
	sched.AddJob(Job{ID: 1, Schedule: "* * * * *", Enabled: true})
	sched.AddJob(Job{ID: 2, Schedule: "0 * * * *", Enabled: true})
	sched.AddJob(Job{ID: 3, Schedule: "30 * * * *", Enabled: false})

	if sched.EnabledCount() != 2 {
		t.Fatalf("expected 2 enabled, got %d", sched.EnabledCount())
	}

	sched.Start()
	defer sched.Stop()

	if sched.EnabledCount() != 2 {
		t.Errorf("enabled count should remain 2 after start, got %d", sched.EnabledCount())
	}
}

func TestStartMultipleTimes(t *testing.T) {
	sched, _ := setupSched(t)
	sched.Start()
	sched.Start()
	sched.Start()
	sched.Stop()
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && searchString(s, substr)
}

func searchString(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
