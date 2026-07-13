package com.cronapp.data

/**
 * 负责 crond 运行日志的读取与清空。
 */
class LogRepository(private val shell: ShellExecutor) {

    fun fetchLogs(): List<String> {
        val output = shell.exec("su", "-c", "cat ${CronConfig.CRON_LOG} 2>/dev/null")
        return output.lines().filter { it.isNotBlank() }
    }

    fun clearLogs() {
        // 仅截断日志文件；dcron 以 O_APPEND 方式打开日志，截断后下次写入自动落到文件末尾，无需重开
        shell.exec("su", "-c", "cat /dev/null > ${CronConfig.CRON_LOG}")
    }
}
