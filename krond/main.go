package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"
	_ "time/tzdata"
)

var Version = "0.0.0-dev"
var GithubToken = ""

var (
	startTime  time.Time
	cfg        *Config
	sched      *Scheduler
	appLogger  *log.Logger
	logWriter  *dynamicWriter
	httpServer *http.Server

	errInvalidTarget = errors.New("log_target 必须是 file、logcat 或 both")
)

func main() {
	startTime = time.Now()

	if len(os.Args) < 2 || os.Args[1] == "run" {
		run()
		return
	}

	switch os.Args[1] {
	case "start":
		cmdStart()
	case "stop":
		cmdStop()
	case "restart":
		cmdRestart()
	case "version":
		fmt.Println("krond version", Version)
	default:
		fmt.Fprintf(os.Stderr, "用法: %s [run|start|stop|restart|version]\n", os.Args[0])
		os.Exit(1)
	}
}

func run() {
	// 时区初始化：优先读取环境变量 TZ，否则从 Android 系统属性获取
	tz := os.Getenv("TZ")
	if tz == "" {
		if data, err := exec.Command("getprop", "persist.sys.timezone").Output(); err == nil {
			tz = strings.TrimSpace(string(data))
		}
	}
	if tz != "" {
		if loc, err := time.LoadLocation(tz); err == nil {
			time.Local = loc
		}
	}

	var err error
	cfg, err = LoadConfig(DefaultConfigFile)
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// Write pidfile
	os.WriteFile(cfg.PidFile, []byte(strconv.Itoa(os.Getpid())), 0644)
	defer os.Remove(cfg.PidFile)

	// Setup logger
	setupLogger(cfg)

	// Setup scheduler
	sched = NewScheduler(logWriter)
	sched.LoadJobs(cfg.Jobs)
	sched.Start()
	appLogger.Printf("krond v%s 启动 (pid %d), %d 个任务加载", Version, os.Getpid(), len(cfg.Jobs))
	sched.PrintJobs(cfg.Jobs)

	go func() {
		time.Sleep(15 * time.Second)
		ticker := time.NewTicker(10 * time.Minute)
		defer ticker.Stop()
		for {
			entries := sched.CronEntries()
			enabled := 0
			for _, j := range cfg.Jobs {
				if j.Enabled {
					enabled++
				}
			}
			appLogger.Printf("[诊断] cron 状态: %d 条目, 存活=%v, 健康=%v, 应启用=%d",
				len(entries), sched.IsRunning(), sched.Healthy(), enabled)
			for _, e := range entries {
				next := "-"
				if !e.Next.IsZero() {
					next = e.Next.Format("15:04:05")
				}
				prev := "-"
				if !e.Prev.IsZero() {
					prev = e.Prev.Format("15:04:05")
				}
				appLogger.Printf("[诊断]   entry=%d next=%s prev=%s", e.ID, next, prev)
			}

			if enabled > 0 && len(entries) == 0 {
				appLogger.Println("[诊断] ⚠ cron 条目为空但应有启用任务, 触发热修复")
				sched.Reload(cfg.Jobs)
			} else if sched.AllNextInPast() {
				appLogger.Println("[诊断] ⚠ cron 所有条目的下次运行时间已过, 触发热修复")
				sched.Reload(cfg.Jobs)
			}

			<-ticker.C
		}
	}()

	// Start HTTP server
	httpServer = startHTTPServer(cfg, sched)

	// Wait for signal
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGTERM, syscall.SIGINT)
	<-sig

	appLogger.Println("正在关闭...")

	// 停止调度器，等待正在执行的任务完成（最多 30s）
	waitJobs := sched.Stop()
	select {
	case <-waitJobs.Done():
		appLogger.Println("所有任务执行完毕")
	case <-time.After(30 * time.Second):
		appLogger.Println("等待任务超时，强制关闭")
	}

	// 优雅关闭 HTTP server（5s 超时）
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(ctx); err != nil {
		appLogger.Printf("HTTP server 关闭: %v", err)
	} else {
		appLogger.Println("HTTP server 已关闭")
	}

	// 刷新并关闭日志文件
	if logWriter != nil {
		logWriter.Close()
	}
}

func setupLogger(cfg *Config) {
	logFile, err := os.OpenFile(cfg.LogFile, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		log.Fatalf("打开日志文件 %s 失败: %v", cfg.LogFile, err)
	}

	logWriter = &dynamicWriter{
		file:     logFile,
		filePath: cfg.LogFile,
		target:   cfg.LogTarget,
	}

	appLogger = log.New(logWriter, "", log.Ldate|log.Ltime|log.Lshortfile)
}

func cmdStart() {
	pidData, err := os.ReadFile(DefaultPidFile)
	if err == nil {
		pid, _ := strconv.Atoi(strings.TrimSpace(string(pidData)))
		if pid > 0 && isProcessRunning(pid) {
			fmt.Printf("krond 已在运行 (pid %d)\n", pid)
			return
		}
	}

	self, err := os.Executable()
	if err != nil {
		log.Fatalf("获取自身路径失败: %v", err)
	}

	nullDev, err := os.OpenFile("/dev/null", os.O_RDWR, 0)
	if err != nil {
		log.Fatalf("打开 /dev/null 失败: %v", err)
	}

	attr := &os.ProcAttr{
		Files: []*os.File{nullDev, nullDev, nullDev},
		Sys:   &syscall.SysProcAttr{Setsid: true},
	}

	proc, err := os.StartProcess(self, []string{self, "run"}, attr)
	if err != nil {
		log.Fatalf("启动失败: %v", err)
	}

	os.WriteFile(DefaultPidFile, []byte(strconv.Itoa(proc.Pid)), 0644)
	fmt.Printf("krond 已启动 (pid %d)\n", proc.Pid)
}

func cmdStop() {
	pidData, err := os.ReadFile(DefaultPidFile)
	if err != nil {
		fmt.Println("krond 未运行")
		return
	}

	pid, _ := strconv.Atoi(strings.TrimSpace(string(pidData)))
	if pid == 0 || !isProcessRunning(pid) {
		os.Remove(DefaultPidFile)
		fmt.Println("krond 未运行")
		return
	}

	process, err := os.FindProcess(pid)
	if err != nil {
		os.Remove(DefaultPidFile)
		return
	}

	process.Signal(syscall.SIGTERM)
	time.Sleep(3 * time.Second)

	if isProcessRunning(pid) {
		process.Kill()
		fmt.Println("krond 已强制停止")
	} else {
		fmt.Println("krond 已停止")
	}

	os.Remove(DefaultPidFile)
}

func cmdRestart() {
	cmdStop()
	time.Sleep(1 * time.Second)
	cmdStart()
}

func isProcessRunning(pid int) bool {
	return syscall.Kill(pid, 0) == nil
}
