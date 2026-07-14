package online.youcd.krond.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.newsclub.net.unix.AFSocketFactory
import org.newsclub.net.unix.AFUNIXSocketAddress
import java.io.IOException
import java.util.concurrent.TimeUnit

class KrondClient {
    private val address = AFUNIXSocketAddress.inAbstractNamespace(KrondConfig.SOCKET_NAME)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .socketFactory(AFSocketFactory.FixedAddressSocketFactory(address))
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://localhost"

    fun getJobs(): List<CronJob> {
        val request = Request.Builder().url("$baseUrl/api/jobs").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body!!.string()
            val arr = JSONArray(body)
            val jobs = mutableListOf<CronJob>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                jobs.add(
                    CronJob(
                        id = obj.getInt("id"),
                        name = obj.optString("name", ""),
                        schedule = obj.getString("schedule"),
                        command = obj.getString("command"),
                        enabled = obj.getBoolean("enabled"),
                        next = obj.optString("next", ""),
                        lastRun = obj.optString("last_run", ""),
                        lastDuration = obj.optString("last_duration", ""),
                        lastExitCode = if (obj.has("last_exit_code") && !obj.isNull("last_exit_code")) obj.getInt("last_exit_code") else null
                    )
                )
            }
            return jobs
        }
    }

    fun replaceAllJobs(jobs: List<CronJob>) {
        val arr = JSONArray()
        for (job in jobs) {
            arr.put(
                JSONObject().apply {
                    put("id", job.id)
                    put("name", job.name)
                    put("schedule", job.schedule)
                    put("command", job.command)
                    put("enabled", job.enabled)
                }
            )
        }
        val request = Request.Builder()
            .url("$baseUrl/api/jobs")
            .put(arr.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        }
    }

    fun isRunning(): Boolean {
        return try {
            val request = Request.Builder().url("$baseUrl/api/status").build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }
    }

    fun fetchLogs(lines: Int = 500): List<String> {
        return try {
            val request = Request.Builder().url("$baseUrl/api/logs?lines=$lines").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                response.body!!.string().lines().filter { it.isNotBlank() }
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    fun clearLogs() {
        val request = Request.Builder()
            .url("$baseUrl/api/logs/clear")
            .post("".toRequestBody(null))
            .build()
        client.newCall(request).execute().use { }
    }

    fun getLogTarget(): String {
        return try {
            val request = Request.Builder().url("$baseUrl/api/config").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use "both"
                JSONObject(response.body!!.string()).getString("log_target")
            }
        } catch (_: Exception) {
            "both"
        }
    }

    fun runJob(id: Int) {
        val request = Request.Builder()
            .url("$baseUrl/api/jobs/$id/run")
            .post("".toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        }
    }

    fun fetchMetrics(): String {
        return try {
            val request = Request.Builder().url("$baseUrl/metrics").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                response.body!!.string()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun updateLogTarget(target: String) {
        val body = JSONObject().apply { put("log_target", target) }
        val request = Request.Builder()
            .url("$baseUrl/api/config")
            .put(body.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        }
    }
}
