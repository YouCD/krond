package com.cronapp.data

data class CronJob(
    val id: Int,
    val name: String = "",
    val schedule: String,
    val command: String,
    val enabled: Boolean
) {
    fun toCronLine(): String = if (enabled) "$schedule $command" else "# $schedule $command"
}

/**
 * 门面（facade）：组合三个职责单一的子仓库，对外维持原有的统一接口，
 * ViewModel 无需感知内部拆分。
 */
class CronRepository(
    private val cronTab: CronTabRepository = CronTabRepository(SuShellExecutor()),
    private val service: CronServiceRepository = CronServiceRepository(SuShellExecutor()),
    private val logs: LogRepository = LogRepository(SuShellExecutor())
) {
    fun hasRoot(): Boolean = cronTab.hasRoot()
    fun getCronJobs(): List<CronJob> = cronTab.getCronJobs()
    fun setCronJobs(jobs: List<CronJob>) = cronTab.setCronJobs(jobs)
    fun isCrondRunning(): Boolean = service.isCrondRunning()
    fun startCrond() = service.startCrond()
    fun stopCrond() = service.stopCrond()
    fun restartCrond() = service.restartCrond()
    fun fetchLogs(): List<String> = logs.fetchLogs()
    fun clearLogs() = logs.clearLogs()
}
