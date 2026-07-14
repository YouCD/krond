package online.youcd.krond.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ScriptDetail(
    val name: String,
    val size: String = "",
    val permissions: String = ""
)

data class CronJob(
    val id: Int,
    val name: String = "",
    val schedule: String,
    val command: String,
    val enabled: Boolean,
    val next: String = ""
)

class CronRepository(
    private val client: KrondClient = KrondClient(),
    private val shell: ShellExecutor = SuShellExecutor()
) {
    private val cronTab = CronTabRepository(client, shell)
    private val service = CronServiceRepository(client, shell)
    private val logs = LogRepository(client)

    fun hasRoot(): Boolean = cronTab.hasRoot()
    fun getCronJobs(): List<CronJob> = cronTab.getCronJobs()
    fun setCronJobs(jobs: List<CronJob>) = cronTab.setCronJobs(jobs)

    fun isKrondRunning(): Boolean = service.isKrondRunning()
    fun startKrond() = service.startKrond()
    fun stopKrond() = service.stopKrond()
    fun restartKrond() = service.restartKrond()

    fun fetchLogs(): List<String> = logs.fetchLogs()
    fun clearLogs() = logs.clearLogs()

    fun getLogTarget(): String = client.getLogTarget()
    fun setLogTarget(target: String) = client.updateLogTarget(target)

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

    private val scriptsDir = "/data/krond/scripts"

    fun listScriptDetails(): List<ScriptDetail> {
        val out = shell.exec("su", "-c", "ls -l '$scriptsDir' 2>/dev/null || echo __EMPTY__")
        return out.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed == "__EMPTY__" || trimmed.startsWith("total")) return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 8) return@mapNotNull null
            ScriptDetail(
                name = parts.drop(7).joinToString(" "),
                size = parts[4],
                permissions = parts[0]
            )
        }
    }

    /** 确保文件名安全（不含路径分隔符、无特殊 shell 字符） */
    private fun safeScriptName(name: String): String {
        return name.substringAfterLast("/").substringBefore("?")
    }

    fun uploadScript(name: String, content: ByteArray): Boolean {
        val safe = safeScriptName(name)
        shell.execPipe("cat > '$scriptsDir/$safe'", content.decodeToString())
        if (shell.lastExitCode() != 0) return false
        shell.exec("su", "-c", "chmod 755 '$scriptsDir/$safe'")
        return shell.lastExitCode() == 0
    }

    fun deleteScript(name: String): Boolean {
        shell.exec("su", "-c", "rm -f '$scriptsDir/$name'")
        return shell.lastExitCode() == 0
    }

    fun readScriptContent(name: String): String? {
        val out = shell.exec("su", "-c", "cat '$scriptsDir/$name'")
        if (shell.lastExitCode() != 0) return null
        return out
    }

    fun exportToZip(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("tasks.json"))
            zos.write(exportToJson().toByteArray())
            zos.closeEntry()

            for (script in listScriptDetails()) {
                val content = readScriptContent(script.name) ?: continue
                zos.putNextEntry(ZipEntry("scripts/${script.name}"))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    fun importFromZip(zipBytes: ByteArray): Pair<Int, Int> {
        var taskCount = 0
        var scriptCount = 0
        val tasksJson = StringBuilder()
        val scriptsToUpload = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "tasks.json" -> {
                        tasksJson.append(zis.readBytes().decodeToString())
                    }
                    entry.name.startsWith("scripts/") && !entry.isDirectory -> {
                        val name = entry.name.removePrefix("scripts/")
                        scriptsToUpload.add(name to zis.readBytes())
                    }
                }
                entry = zis.nextEntry
            }
        }

        if (tasksJson.isNotBlank()) {
            taskCount = importFromJson(tasksJson.toString())
        }
        for ((name, content) in scriptsToUpload) {
            if (uploadScript(name, content)) scriptCount++
        }
        return taskCount to scriptCount
    }

    fun importFromJson(json: String): Int {
        val arr = JSONArray(json)
        val current = getCronJobs().toMutableList()
        var count = 0
        var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("name", "")
            val schedule = obj.getString("schedule")
            val command = obj.getString("command")
            val enabled = obj.optBoolean("enabled", true)

            val existing = if (name.isNotBlank()) current.find { it.name == name } else null
            if (existing != null) {
                val idx = current.indexOfFirst { it.id == existing.id }
                if (idx >= 0) {
                    current[idx] = existing.copy(schedule = schedule, command = command, enabled = enabled)
                    count++
                }
            } else {
                current.add(CronJob(id = nextId++, name = name, schedule = schedule, command = command, enabled = enabled))
                count++
            }
        }
        if (count == 0) return 0
        setCronJobs(current)
        return count
    }
}
