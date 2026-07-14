package online.youcd.krond.data

import android.util.Log

class CronServiceRepository(private val client: KrondClient, private val shell: ShellExecutor) {

    private val tag = "CronServiceRepo"

    fun isKrondRunning(): Boolean {
        val running = client.isRunning()
        Log.d(tag, "isKrondRunning=$running")
        return running
    }

    fun startKrond() {
        val out = shell.exec("su", "-c", KrondConfig.KROND_BIN, "start")
        Log.d(tag, "start: $out")
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("启动 krond 失败 (退出码 ${shell.lastExitCode()}): $out")
        }
    }

    fun stopKrond() {
        val out = shell.exec("su", "-c", KrondConfig.KROND_BIN, "stop")
        Log.d(tag, "stop: $out")
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("停止 krond 失败 (退出码 ${shell.lastExitCode()}): $out")
        }
    }

    fun restartKrond() {
        val out = shell.exec("su", "-c", KrondConfig.KROND_BIN, "restart")
        Log.d(tag, "restart: $out")
        if (shell.lastExitCode() != 0) {
            throw IllegalStateException("重启 krond 失败 (退出码 ${shell.lastExitCode()}): $out")
        }
    }
}
