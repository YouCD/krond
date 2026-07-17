package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus/promhttp"
)

const (
	logMaxSize  = 1024 * 1024  // 1MB 触发轮转
	logMaxFiles = 3             // 保留 3 个备份
)

type dynamicWriter struct {
	mu        sync.RWMutex
	file      *os.File
	filePath  string
	target    string
	writeSize int64
}

func (w *dynamicWriter) maybeRotate() error {
	fi, err := w.file.Stat()
	if err != nil {
		return err
	}
	if fi.Size() < logMaxSize {
		return nil
	}

	w.file.Close()

	// 删除最旧的备份
	os.Remove(w.filePath + ".3")

	// 依次重命名 .2 -> .3, .1 -> .2
	for i := logMaxFiles - 1; i >= 1; i-- {
		old := w.filePath + fmt.Sprintf(".%d", i)
		new := w.filePath + fmt.Sprintf(".%d", i+1)
		os.Rename(old, new)
	}
	os.Rename(w.filePath, w.filePath+".1")

	f, err := os.OpenFile(w.filePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return err
	}
	w.file = f
	return nil
}

func androidLogPrint(msg string) {
	for _, line := range strings.Split(strings.TrimSuffix(msg, "\n"), "\n") {
		if err := exec.Command("/system/bin/log", "-t", "krond", "-p", "I", line).Run(); err != nil {
			if f, e := os.OpenFile("/dev/kmsg", os.O_WRONLY, 0); e == nil {
				f.Write([]byte("<4>krond: " + line + "\n"))
				f.Close()
			}
		}
	}
}

func (w *dynamicWriter) Write(p []byte) (int, error) {
	w.mu.RLock()
	target := w.target
	w.mu.RUnlock()

	switch target {
	case "file":
		w.maybeRotate()
		return w.file.Write(p)
	case "logcat":
		androidLogPrint(string(p))
		return len(p), nil
	case "both":
		w.maybeRotate()
		n, err := w.file.Write(p)
		androidLogPrint(string(p))
		return n, err
	default:
		w.maybeRotate()
		return w.file.Write(p)
	}
}

func (w *dynamicWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.file.Close()
}

func (w *dynamicWriter) SetTarget(target string) error {
	w.mu.Lock()
	defer w.mu.Unlock()
	switch target {
	case "file", "logcat", "both":
		w.target = target
		return nil
	default:
		return errInvalidTarget
	}
}

func startHTTPServer(cfg *Config, sched *Scheduler) *http.Server {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /api/jobs", handleGetJobs(sched))
	mux.HandleFunc("POST /api/jobs", handleCreateJob)
	mux.HandleFunc("PUT /api/jobs", handleReplaceJobs)
	mux.HandleFunc("PUT /api/jobs/{id}", handleUpdateJob)
	mux.HandleFunc("DELETE /api/jobs/{id}", handleDeleteJob)
	mux.HandleFunc("POST /api/jobs/{id}/toggle", handleToggleJob)
	mux.HandleFunc("POST /api/jobs/{id}/run", handleRunJob)
	mux.HandleFunc("GET /api/status", handleStatus(sched))
	mux.HandleFunc("GET /api/logs", handleGetLogs)
	mux.HandleFunc("POST /api/logs/clear", handleClearLogs)
	mux.HandleFunc("GET /api/config", handleGetConfig)
	mux.HandleFunc("PUT /api/config", handleUpdateConfig(cfg))
	mux.HandleFunc("GET /api/update/status", handleUpdateStatus)
	mux.HandleFunc("POST /api/update/apply", handleUpdateApply)
	mux.HandleFunc("GET /metrics", func(w http.ResponseWriter, r *http.Request) {
		krondUptime.Set(time.Since(startTime).Seconds())
		promhttp.Handler().ServeHTTP(w, r)
	})

	ln, err := net.Listen("unix", cfg.Socket)
	if err != nil {
		log.Fatalf("监听 socket %s 失败: %v", cfg.Socket, err)
	}

	server := &http.Server{
		Handler:      mux,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	go func() {
		if err := server.Serve(ln); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP server 错误: %v", err)
		}
	}()

	return server
}

// --- job handlers ---

func fillJobResult(j *Job) {
	if r, ok := getJobResult(j.ID); ok {
		j.LastRun = &r.LastRun
		j.LastDuration = r.LastDuration.Round(time.Millisecond).String()
		if r.LastExitCode != 0 {
			j.LastExitCode = &r.LastExitCode
		}
	}
}

func handleGetJobs(sched *Scheduler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		jobs := make([]Job, len(cfg.Jobs))
		for i, j := range cfg.Jobs {
			if j.Enabled {
				if next, ok := sched.NextRun(j.ID); ok {
					j.Next = &next
				}
			}
			fillJobResult(&j)
			jobs[i] = j
		}
		writeJSON(w, http.StatusOK, jobs)
	}
}

func handleRunJob(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "无效的 id")
		return
	}

	for _, j := range cfg.Jobs {
		if j.ID == id {
			go executeJob(j)
			writeJSON(w, http.StatusAccepted, map[string]interface{}{"status": "启动", "job": j.Name})
			return
		}
	}
	writeError(w, http.StatusNotFound, "任务未找到")
}

func handleCreateJob(w http.ResponseWriter, r *http.Request) {
	var job Job
	if err := json.NewDecoder(r.Body).Decode(&job); err != nil {
		appLogger.Printf("create job: 无效请求体: %v", err)
		writeError(w, http.StatusBadRequest, "无效的请求体")
		return
	}

	if job.Schedule == "" || job.Command == "" {
		appLogger.Println("create job: schedule 或 command 为空")
		writeError(w, http.StatusBadRequest, "schedule 和 command 不能为空")
		return
	}

	maxID := 0
	for _, j := range cfg.Jobs {
		if j.ID > maxID {
			maxID = j.ID
		}
	}
	job.ID = maxID + 1
	job.Enabled = true

	cfg.Jobs = append(cfg.Jobs, job)
	sched.AddJob(job)
	SaveConfig(cfg, DefaultConfigFile)

	appLogger.Printf("create job: id=%d name=%q schedule=%q command=%q", job.ID, job.Name, job.Schedule, job.Command)
	writeJSON(w, http.StatusCreated, job)
}

func handleReplaceJobs(w http.ResponseWriter, r *http.Request) {
	var jobs []Job
	if err := json.NewDecoder(r.Body).Decode(&jobs); err != nil {
		appLogger.Printf("replace jobs: 无效请求体: %v", err)
		writeError(w, http.StatusBadRequest, "无效的请求体")
		return
	}

	seen := make(map[int]bool)
	for _, job := range jobs {
		if job.Schedule == "" || job.Command == "" {
			appLogger.Printf("replace jobs: schedule 或 command 为空 (id=%d)", job.ID)
			writeError(w, http.StatusBadRequest, "schedule 和 command 不能为空")
			return
		}
		if job.ID == 0 {
			appLogger.Println("replace jobs: id 为 0")
			writeError(w, http.StatusBadRequest, "id 不能为 0")
			return
		}
		if seen[job.ID] {
			appLogger.Printf("replace jobs: 重复 id=%d", job.ID)
			writeError(w, http.StatusBadRequest, "重复的 id")
			return
		}
		seen[job.ID] = true
	}

	cfg.Jobs = jobs
	sched.LoadJobs(jobs)
	SaveConfig(cfg, DefaultConfigFile)
	appLogger.Printf("replace jobs: %d 个任务", len(jobs))
	for _, j := range jobs {
		appLogger.Printf("  job id=%d name=%q schedule=%q command=%q enabled=%v",
			j.ID, j.Name, j.Schedule, j.Command, j.Enabled)
	}
	writeJSON(w, http.StatusOK, jobs)
}

func handleUpdateJob(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		appLogger.Printf("update job: 无效 id=%q", r.PathValue("id"))
		writeError(w, http.StatusBadRequest, "无效的 id")
		return
	}

	var updated Job
	if err := json.NewDecoder(r.Body).Decode(&updated); err != nil {
		appLogger.Printf("update job id=%d: 无效请求体: %v", id, err)
		writeError(w, http.StatusBadRequest, "无效的请求体")
		return
	}
	updated.ID = id

	found := false
	for i, j := range cfg.Jobs {
		if j.ID == id {
			cfg.Jobs[i] = updated
			sched.UpdateJob(updated)
			SaveConfig(cfg, DefaultConfigFile)
			appLogger.Printf("update job id=%d: name=%q schedule=%q command=%q enabled=%v",
				id, updated.Name, updated.Schedule, updated.Command, updated.Enabled)
			found = true
			break
		}
	}
	if !found {
		appLogger.Printf("update job id=%d: 未找到", id)
		writeError(w, http.StatusNotFound, "任务未找到")
		return
	}

	writeJSON(w, http.StatusOK, updated)
}

func handleDeleteJob(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		appLogger.Printf("delete job: 无效 id=%q", r.PathValue("id"))
		writeError(w, http.StatusBadRequest, "无效的 id")
		return
	}

	found := false
	for i, j := range cfg.Jobs {
		if j.ID == id {
			cfg.Jobs = append(cfg.Jobs[:i], cfg.Jobs[i+1:]...)
			sched.RemoveJob(id)
			SaveConfig(cfg, DefaultConfigFile)
			appLogger.Printf("delete job id=%d (name=%q)", id, j.Name)
			found = true
			break
		}
	}
	if !found {
		appLogger.Printf("delete job id=%d: 未找到", id)
		writeError(w, http.StatusNotFound, "任务未找到")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleToggleJob(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(r.PathValue("id"))
	if err != nil {
		appLogger.Printf("toggle job: 无效 id=%q", r.PathValue("id"))
		writeError(w, http.StatusBadRequest, "无效的 id")
		return
	}

	found := false
	for i, j := range cfg.Jobs {
		if j.ID == id {
			cfg.Jobs[i].Enabled = !j.Enabled
			sched.UpdateJob(cfg.Jobs[i])
			SaveConfig(cfg, DefaultConfigFile)
			appLogger.Printf("toggle job id=%d: enabled=%v", id, cfg.Jobs[i].Enabled)
			found = true
			writeJSON(w, http.StatusOK, cfg.Jobs[i])
			break
		}
	}
	if !found {
		appLogger.Printf("toggle job id=%d: 未找到", id)
		writeError(w, http.StatusNotFound, "任务未找到")
	}
}

// --- status ---

func handleStatus(sched *Scheduler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"running":    true,
			"version":    Version,
			"uptime":     time.Since(startTime).String(),
			"jobsCount":  len(cfg.Jobs),
		})
	}
}

// --- logs ---

func handleGetLogs(w http.ResponseWriter, r *http.Request) {
	linesStr := r.URL.Query().Get("lines")
	maxLines := 500
	if linesStr != "" {
		if n, err := strconv.Atoi(linesStr); err == nil && n > 0 && n <= 5000 {
			maxLines = n
		}
	}

	data, err := os.ReadFile(cfg.LogFile)
	if err != nil {
		if os.IsNotExist(err) {
			w.Write([]byte{})
			return
		}
		writeError(w, http.StatusInternalServerError, "读取日志失败")
		return
	}

	lines := splitLines(string(data))
	if len(lines) > maxLines {
		lines = lines[len(lines)-maxLines:]
	}

	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	for _, line := range lines {
		io.WriteString(w, line+"\n")
	}
}

func handleClearLogs(w http.ResponseWriter, r *http.Request) {
	if err := os.Truncate(cfg.LogFile, 0); err != nil {
		writeError(w, http.StatusInternalServerError, "清空日志失败")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// --- config ---

func handleGetConfig(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"log_target": cfg.LogTarget,
	})
}

func handleUpdateConfig(cfg *Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			LogTarget string `json:"log_target"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeError(w, http.StatusBadRequest, "无效的请求体")
			return
		}

		if body.LogTarget != "" {
			if err := logWriter.SetTarget(body.LogTarget); err != nil {
				writeError(w, http.StatusBadRequest, err.Error())
				return
			}
			cfg.LogTarget = body.LogTarget
			SaveConfig(cfg, DefaultConfigFile)
		}

		writeJSON(w, http.StatusOK, map[string]interface{}{
			"log_target": cfg.LogTarget,
		})
	}
}

// --- update ---

func handleUpdateStatus(w http.ResponseWriter, r *http.Request) {
	status, err := checkUpdate()
	if err != nil {
		appLogger.Printf("检查更新失败: %v", err)
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"currentVersion": Version,
			"error":          err.Error(),
			"hasUpdate":      false,
		})
		return
	}
	writeJSON(w, http.StatusOK, status)
}

func handleUpdateApply(w http.ResponseWriter, r *http.Request) {
	status, err := checkUpdate()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "检查更新失败: "+err.Error())
		return
	}
	if !status.HasUpdate {
		writeError(w, http.StatusBadRequest, "当前已是最新版本")
		return
	}
	if status.DownloadURL == "" {
		writeError(w, http.StatusBadRequest, "未找到更新包下载链接")
		return
	}

	if err := downloadUpdate(status.DownloadURL); err != nil {
		appLogger.Printf("下载更新失败: %v", err)
		writeError(w, http.StatusInternalServerError, "下载更新失败: "+err.Error())
		return
	}

	if err := triggerUpdate(); err != nil {
		appLogger.Printf("启动更新脚本失败: %v", err)
		writeError(w, http.StatusInternalServerError, "启动更新失败: "+err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{
		"status":  "ok",
		"message": "更新已启动，krond 即将关闭重启",
	})

	go func() {
		time.Sleep(500 * time.Millisecond)
		shutdownForUpdate()
	}()
}

// --- helpers ---

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func splitLines(s string) []string {
	var lines []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			if i > start || len(lines) > 0 || s[i] == '\n' {
				lines = append(lines, s[start:i])
			}
			start = i + 1
		}
	}
	if start < len(s) {
		lines = append(lines, s[start:])
	}
	return lines
}
