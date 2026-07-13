package com.cronapp.data

/**
 * 负责 crond 服务的启停与运行状态查询。
 */
class CronServiceRepository(private val shell: ShellExecutor) {

    fun isCrondRunning(): Boolean {
        val out = shell.exec("su", "-c", "pidof crond")
        return out.trim().isNotBlank()
    }

    fun startCrond() {
        shell.exec("su", "-c", "${CronConfig.CROND_BIN} -b -L ${CronConfig.CRON_LOG} -l info")
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("启动 crond 失败 (退出码 ${shell.lastExitCode()})")
        }
    }

    fun stopCrond() {
        shell.exec("su", "-c", "kill \$(pidof crond) 2>/dev/null")
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("停止 crond 失败 (退出码 ${shell.lastExitCode()})")
        }
    }

    fun restartCrond() {
        shell.exec(
            "su", "-c",
            "kill \$(pidof crond) 2>/dev/null; sleep 1; ${CronConfig.CROND_BIN} -b -L ${CronConfig.CRON_LOG} -l info"
        )
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("重启 crond 失败 (退出码 ${shell.lastExitCode()})")
        }
    }
}
