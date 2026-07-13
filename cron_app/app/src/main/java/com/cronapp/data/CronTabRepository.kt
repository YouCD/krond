package com.cronapp.data

import android.util.Log

/**
 * 负责 crontab 的读取 / 写入 / 解析。非本应用管理的原始行会在内存中暂存（lastPreserved），
 * 写回时原样拼回，避免清空用户手动添加的 @reboot、环境变量等。
 */
class CronTabRepository(private val shell: ShellExecutor) {

    private var lastPreserved: List<String> = emptyList()
    private val tag = "CronTabRepo"

    private val crontabFile = "${CronConfig.CRON_DIRS[0]}/root"

    private fun readRaw(): List<String> {
        Log.d(tag, "readRaw: $crontabFile")
        val out = shell.exec("su", "-c", "cat", crontabFile)
        val code = shell.lastExitCode()
        Log.d(tag, "readRaw exit=$code lines=${out.lines().size}")
        if (code != 0) return emptyList()
        return out.lines()
    }

    fun hasRoot(): Boolean {
        return try {
            val out = shell.exec("su", "-c", "id")
            out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun getCronJobs(): List<CronJob> {
        val (jobs, preserved) = CronParser.parseCrontab(readRaw())
        lastPreserved = preserved
        return jobs
    }

    fun setCronJobs(jobs: List<CronJob>) {
        Log.d(tag, "setCronJobs: jobs=${jobs.size}")
        if (lastPreserved.isEmpty()) {
            lastPreserved = CronParser.parseCrontab(readRaw()).second
        }
        val content = CronParser.renderCrontab(jobs, lastPreserved)
        Log.d(tag, "content=${content.length}B")
        shell.execPipe(content, "su", "-c", "dd", "of=$crontabFile")
        var code = shell.lastExitCode()
        Log.d(tag, "dd exit=$code")
        if (code != 0) {
            throw IllegalStateException("保存定时任务失败 (退出码 $code)")
        }
        // 通知 dcron 重新读取 crontab（dcron 靠 cron.update 信号文件触发）
        // 直接用模块内的 crontab binary，避免系统 overlay 未更新
        shell.exec("su", "-c", "/data/adb/modules/crond_injector/crond/bin/crontab", crontabFile)
        code = shell.lastExitCode()
        Log.d(tag, "crontab signal exit=$code")
    }
}
