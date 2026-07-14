package main

import (
	"os/exec"
	"strings"
	"time"
)

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
		} else {
			appLogger.Printf("[%s] 执行错误: %v (耗时 %v)", job.Name, err, elapsed)
		}
	} else {
		appLogger.Printf("[%s] 完成 (退出码 0, 耗时 %v)", job.Name, elapsed)
	}
}
