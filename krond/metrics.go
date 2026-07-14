package main

import (
	"net/http"
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
	jobExecTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "krond_job_executions_total",
		Help: "任务执行总数",
	}, []string{"job_id", "job_name"})

	jobFailTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "krond_job_failures_total",
		Help: "任务失败总数",
	}, []string{"job_id", "job_name"})

	jobDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "krond_job_duration_seconds",
		Help:    "任务执行耗时分布",
		Buckets: []float64{0.1, 0.5, 1, 2, 5, 10, 30, 60, 120},
	}, []string{"job_id", "job_name"})

	jobLastExitCode = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "krond_job_last_exit_code",
		Help: "最近一次退出码",
	}, []string{"job_id", "job_name"})

	jobLastDurationSecs = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "krond_job_last_duration_seconds",
		Help: "最近一次执行耗时",
	}, []string{"job_id", "job_name"})

	jobLastRunTs = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "krond_job_last_run_timestamp",
		Help: "最近一次执行时间戳",
	}, []string{"job_id", "job_name"})

	krondUptime = promauto.NewGauge(prometheus.GaugeOpts{
		Name: "krond_uptime_seconds",
		Help: "krond 运行时长",
	})
)

func recordMetrics(jobID int, jobName string, exitCode int, duration time.Duration) {
	sid := strconv.Itoa(jobID)
	jobExecTotal.WithLabelValues(sid, jobName).Inc()
	if exitCode != 0 {
		jobFailTotal.WithLabelValues(sid, jobName).Inc()
	}
	jobDuration.WithLabelValues(sid, jobName).Observe(duration.Seconds())
	jobLastExitCode.WithLabelValues(sid, jobName).Set(float64(exitCode))
	jobLastDurationSecs.WithLabelValues(sid, jobName).Set(duration.Seconds())
	jobLastRunTs.WithLabelValues(sid, jobName).Set(float64(time.Now().Unix()))
}

func metricsHandler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		krondUptime.Set(time.Since(startTime).Seconds())
		promhttp.Handler().ServeHTTP(w, r)
	})
}
