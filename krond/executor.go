package main

import (
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"
)

var (
	jobResultsMu sync.RWMutex
	jobResults   = make(map[int]JobResult)
)

type JobResult struct {
	LastRun      time.Time
	LastDuration time.Duration
	LastExitCode int
}

func recordJobResult(id int, start time.Time, exitCode int) {
	jobResultsMu.Lock()
	jobResults[id] = JobResult{
		LastRun:      start,
		LastDuration: time.Since(start),
		LastExitCode: exitCode,
	}
	jobResultsMu.Unlock()
}

func getJobResult(id int) (JobResult, bool) {
	jobResultsMu.RLock()
	r, ok := jobResults[id]
	jobResultsMu.RUnlock()
	return r, ok
}

func broadcastJobResult(jobID int, jobName string, exitCode int, duration time.Duration) {
	dur := duration.Round(time.Millisecond).String()
	exec.Command("am", "broadcast",
		"-n", "online.youcd.krond/.data.JobResultReceiver",
		"--ei", "job_id", strconv.Itoa(jobID),
		"--ei", "exit_code", strconv.Itoa(exitCode),
		"--es", "duration", dur,
		"--es", "job_name", jobName,
	).Run()
}

func executeJob(job Job) {
	appLogger.Printf("[%s] — 开始执行: %s", job.Name, job.Command)

	start := time.Now()
	cmd := exec.Command("/system/bin/sh", "-c", job.Command)
	out, err := cmd.CombinedOutput()
	elapsed := time.Since(start)

	output := strings.TrimSpace(string(out))
	if output != "" {
		appLogger.Printf("[%s] 输出:\n%s", job.Name, output)
	}

	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			appLogger.Printf("[%s] 失败 (退出码 %d, 耗时 %v)", job.Name, exitErr.ExitCode(), elapsed)
			recordJobResult(job.ID, start, exitErr.ExitCode())
			broadcastJobResult(job.ID, job.Name, exitErr.ExitCode(), elapsed)
		} else {
			appLogger.Printf("[%s] 执行错误: %v (耗时 %v)", job.Name, err, elapsed)
			recordJobResult(job.ID, start, -1)
			broadcastJobResult(job.ID, job.Name, -1, elapsed)
		}
	} else {
		appLogger.Printf("[%s] 完成 (退出码 0, 耗时 %v)", job.Name, elapsed)
		recordJobResult(job.ID, start, 0)
		broadcastJobResult(job.ID, job.Name, 0, elapsed)
	}
}
