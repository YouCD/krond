package com.cronapp.data

import org.json.JSONArray
import org.json.JSONObject

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

    fun exportToJson(): String {
        val jobs = getCronJobs()
        val arr = JSONArray()
        for (job in jobs) {
            val obj = JSONObject()
            obj.put("name", job.name)
            obj.put("schedule", job.schedule)
            obj.put("command", job.command)
            obj.put("enabled", job.enabled)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    fun importFromJson(json: String): Int {
        val arr = JSONArray(json)
        val imported = mutableListOf<CronJob>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            imported.add(
                CronJob(
                    id = 0,
                    name = obj.optString("name", ""),
                    schedule = obj.getString("schedule"),
                    command = obj.getString("command"),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        if (imported.isEmpty()) return 0

        val current = getCronJobs().toMutableList()
        val maxId = current.maxOfOrNull { it.id } ?: 0
        for ((idx, job) in imported.withIndex()) {
            current.add(job.copy(id = maxId + idx + 1))
        }
        setCronJobs(current)
        return imported.size
    }
}
