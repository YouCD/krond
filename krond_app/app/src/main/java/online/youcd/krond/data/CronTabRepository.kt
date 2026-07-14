package online.youcd.krond.data

import android.util.Log

class CronTabRepository(private val client: KrondClient, private val shell: ShellExecutor) {

    private val tag = "CronTabRepo"

    fun hasRoot(): Boolean {
        return try {
            val out = shell.exec("su", "-c", "id")
            out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun getCronJobs(): List<CronJob> {
        return try {
            val jobs = client.getJobs()
            Log.d(tag, "getCronJobs: ${jobs.size} jobs")
            jobs
        } catch (e: Exception) {
            Log.e(tag, "getCronJobs 失败", e)
            emptyList()
        }
    }

    fun runJob(id: Int) {
        Log.d(tag, "runJob: id=$id")
        try {
            client.runJob(id)
            Log.d(tag, "runJob 成功")
        } catch (e: Exception) {
            Log.e(tag, "runJob 失败", e)
            throw IllegalStateException("立即执行失败: ${e.message}")
        }
    }

    fun setCronJobs(jobs: List<CronJob>) {
        Log.d(tag, "setCronJobs: ${jobs.size} jobs")
        try {
            client.replaceAllJobs(jobs)
            Log.d(tag, "setCronJobs 成功")
        } catch (e: Exception) {
            Log.e(tag, "setCronJobs 失败", e)
            throw IllegalStateException("保存定时任务失败: ${e.message}")
        }
    }
}
