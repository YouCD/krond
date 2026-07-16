package online.youcd.krond.data

import org.json.JSONArray
import org.json.JSONObject
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class ScriptDetail(
    val name: String,
    val size: String = "",
    val permissions: String = "",
    val isDirectory: Boolean = false
)

data class CronJob(
    val id: Int,
    val name: String = "",
    val schedule: String,
    val command: String,
    val enabled: Boolean,
    val next: String = "",
    val lastRun: String = "",
    val lastDuration: String = "",
    val lastExitCode: Int? = null
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
    fun runJob(id: Int) = cronTab.runJob(id)

    fun fetchLogs(): List<String> = logs.fetchLogs()
    fun clearLogs() = logs.clearLogs()

    fun getLogTarget(): String = client.getLogTarget()
    fun setLogTarget(target: String) = client.updateLogTarget(target)
    fun fetchMetrics(): KrondMetrics = MetricsClient(client).fetchMetrics()

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
        val out = shell.exec("su", "-c",
            "cd '$scriptsDir' && find . \\( -type f -o -type d \\) ! -path '*/logs/*' ! -path '.' -exec ls -ld {} + 2>/dev/null || echo __EMPTY__")
        return out.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed == "__EMPTY__") return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 8) return@mapNotNull null
            if (parts[0].first() !in "-dlcbps") return@mapNotNull null
            val path = parts.drop(7).joinToString(" ").removePrefix("./")
            val isDir = parts[0].startsWith("d")
            ScriptDetail(
                name = path,
                size = if (isDir) "" else parts[4],
                permissions = parts[0],
                isDirectory = isDir
            )
        }
    }

    /** 确保文件名安全（允许子目录路径，清除 `..` 路径穿越） */
    private fun safeScriptName(name: String): String {
        var clean = name.substringBefore("?").trim('/')
        clean = clean.split("/").filter { it != ".." && !it.startsWith("..") }.joinToString("/")
        if (clean.isEmpty()) return "_"
        return clean
    }

    fun uploadScript(name: String, content: ByteArray, mode: Int = 0x1ED): Boolean {
        val safe = safeScriptName(name)
        shell.exec("su", "-c", "mkdir -p '$scriptsDir'")
        val dir = safe.substringBeforeLast("/", "").let { if (it.isEmpty() || it == safe) "" else it }
        if (dir.isNotEmpty()) {
            shell.exec("su", "-c", "mkdir -p '$scriptsDir/$dir'")
        }
        shell.execPipe("cat > '$scriptsDir/$safe'", content.decodeToString())
        if (shell.lastExitCode() != 0) return false
        shell.exec("su", "-c", "chmod ${(mode and 0xFFF).toString(8)} '$scriptsDir/$safe'")
        return shell.lastExitCode() == 0
    }

    fun createScriptFolder(path: String): Boolean {
        shell.exec("su", "-c", "mkdir -p '$scriptsDir/${safeScriptName(path)}'")
        return shell.lastExitCode() == 0
    }

    fun deleteScript(name: String): Boolean {
        shell.exec("su", "-c", "rm -rf '$scriptsDir/$name'")
        return shell.lastExitCode() == 0
    }

    fun readScriptContent(name: String): String? {
        val out = shell.exec("su", "-c", "cat '$scriptsDir/$name'")
        if (shell.lastExitCode() != 0) return null
        return out
    }

    private fun permissionsToMode(perms: String): Int {
        val p = perms.takeLast(9)
        var mode = 0
        for ((i, c) in p.withIndex()) {
            if (c != '-') mode = mode or (1 shl (8 - i))
        }
        return mode
    }

    fun exportToTar(): ByteArray {
        val bos = ByteArrayOutputStream()
        TarArchiveOutputStream(bos).use { tos ->
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            val jsonBytes = exportToJson().toByteArray()
            val jsonEntry = TarArchiveEntry("tasks.json")
            jsonEntry.size = jsonBytes.size.toLong()
            tos.putArchiveEntry(jsonEntry)
            tos.write(jsonBytes)
            tos.closeArchiveEntry()

            for (script in listScriptDetails()) {
                val content = readScriptContent(script.name) ?: continue
                val bytes = content.toByteArray()
                val entry = TarArchiveEntry("scripts/${script.name}")
                entry.size = bytes.size.toLong()
                entry.mode = 0x8000 or permissionsToMode(script.permissions)
                tos.putArchiveEntry(entry)
                tos.write(bytes)
                tos.closeArchiveEntry()
            }
            tos.finish()
        }
        return bos.toByteArray()
    }

    fun importFromTar(tarBytes: ByteArray): Pair<Int, Int> {
        var taskCount = 0
        var scriptCount = 0
        val tasksJson = StringBuilder()
        val scriptsToUpload = mutableListOf<Triple<String, ByteArray, Int>>()

        TarArchiveInputStream(ByteArrayInputStream(tarBytes)).use { tis ->
            var entry: TarArchiveEntry? = tis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "tasks.json" -> {
                        val baos = ByteArrayOutputStream()
                        tis.copyTo(baos)
                        tasksJson.append(baos.toString())
                    }
                    entry.name.startsWith("scripts/") && !entry.isDirectory -> {
                        val name = entry.name.removePrefix("scripts/")
                        val baos = ByteArrayOutputStream()
                        tis.copyTo(baos)
                        scriptsToUpload.add(Triple(name, baos.toByteArray(), entry.mode))
                    }
                }
                entry = tis.nextEntry
            }
        }

        if (tasksJson.isNotBlank()) {
            taskCount = importFromJson(tasksJson.toString())
        }
        for ((name, content, mode) in scriptsToUpload) {
            if (uploadScript(name, content, mode)) scriptCount++
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
