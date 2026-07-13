package com.cronapp.data

/**
 * 负责 crontab 的读取 / 写入 / 解析。非本应用管理的原始行会在内存中暂存（lastPreserved），
 * 写回时原样拼回，避免清空用户手动添加的 @reboot、环境变量等。
 */
class CronTabRepository(private val shell: ShellExecutor) {

    private var lastPreserved: List<String> = emptyList()

    private fun readRaw(): List<String> {
        val out = shell.exec("su", "-c", "crontab -l")
        if (shell.lastExitCode() != 0) return emptyList()
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
        if (lastPreserved.isEmpty()) {
            lastPreserved = CronParser.parseCrontab(readRaw()).second
        }
        val content = CronParser.renderCrontab(jobs, lastPreserved)
        shell.execPipe("crontab -", content)
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("保存定时任务失败 (crontab 退出码 ${shell.lastExitCode()})")
        }
    }
}
