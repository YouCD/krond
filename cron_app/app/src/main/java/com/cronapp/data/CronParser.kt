package com.cronapp.data

/**
 * 纯解析逻辑（不依赖 root shell），便于单元测试。
 *
 * 本应用管理的 crontab 条目采用锚点注释标记：
 *   # [cronapp] id=N enabled=true|false name=...
 *   下一行即对应的 cron 命令；命令行以 '#' 开头（任意层数）表示禁用。
 * 非本应用管理的行（@reboot、@daily、环境变量、普通注释等）原样保留。
 */
object CronParser {
    private val cronFieldRegex = Regex("""^(\*|\d+)([/\-,]\d+)*$""")
    private val anchorRegex =
        Regex("""#\s*\[cronapp\]\s*id=(\d+)\s+enabled=(true|false)(?:\s+name=(.*))?""")

    fun isCronLine(text: String): Boolean {
        val content = if (text.startsWith("# ")) text.removePrefix("# ") else text
        val parts = content.trimStart().split("\\s+".toRegex())
        if (parts.isEmpty()) return false
        if (parts[0].matches(Regex("^@\\w+$"))) return true
        return parts.size >= 5 && parts.take(5).all { it.matches(cronFieldRegex) }
    }

    private fun splitCron(line: String): Pair<String, String> {
        val parts = line.trimStart().split("\\s+".toRegex())
        return parts.take(5).joinToString(" ") to parts.drop(5).joinToString(" ")
    }

    /** 解析命令行为 (schedule, command, enabled)；命令行被 '#' 注释即视为禁用（支持多层注释）。 */
    fun parseCommandLine(raw: String): Triple<String, String, Boolean>? {
        val trimmed = raw.trimStart()
        val commented = trimmed.startsWith("#")
        var actual = trimmed
        while (actual.startsWith("#")) {
            actual = actual.removePrefix("#").trimStart()
        }
        if (!isCronLine(actual)) return null
        val (schedule, command) = splitCron(actual)
        return Triple(schedule, command, !commented)
    }

    /**
     * 解析 crontab 原始行，返回 (本应用管理的任务, 需原样保留的其它行)。
     */
    fun parseCrontab(lines: List<String>): Pair<List<CronJob>, List<String>> {
        val jobs = mutableListOf<CronJob>()
        val preserved = mutableListOf<String>()
        var i = 0
        var maxId = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            val anchor = anchorRegex.find(line)
            if (anchor != null) {
                val id = anchor.groupValues[1].toInt()
                val name = anchor.groupValues[3].trim()
                maxId = maxOf(maxId, id)
                var j = i + 1
                while (j < lines.size && lines[j].isBlank()) j++
                val parsed = if (j < lines.size) parseCommandLine(lines[j]) else null
                if (parsed != null) {
                    val (schedule, command, enabled) = parsed
                    jobs.add(
                        CronJob(
                            id = id,
                            name = name,
                            schedule = schedule,
                            command = command,
                            enabled = enabled
                        )
                    )
                    i = j + 1
                    continue
                }
                preserved.add(line)
                i++
                continue
            }
            val trimmed = line.trimStart()
            if (trimmed.startsWith("# ") && !trimmed.startsWith("# [cronapp]")) {
                val potentialName = trimmed.removePrefix("# ").trim()
                var j = i + 1
                while (j < lines.size && lines[j].isBlank()) j++
                val parsed = if (j < lines.size) parseCommandLine(lines[j]) else null
                if (parsed != null) {
                    maxId++
                    val (schedule, command, enabled) = parsed
                    jobs.add(
                        CronJob(
                            id = maxId,
                            name = potentialName,
                            schedule = schedule,
                            command = command,
                            enabled = enabled
                        )
                    )
                    i = j + 1
                    continue
                }
            }
            preserved.add(line)
            i++
        }
        return jobs to preserved
    }

    /** 根据本应用任务 + 需保留的原始行，渲染出完整 crontab 文本。 */
    fun renderCrontab(jobs: List<CronJob>, preserved: List<String>): String {
        val lines = mutableListOf<String>()
        lines.addAll(preserved)
        if (preserved.isNotEmpty() && jobs.isNotEmpty()) lines.add("")
        for ((idx, job) in jobs.withIndex()) {
            if (idx > 0) lines.add("")
            lines.add("# [cronapp] id=${job.id} enabled=${job.enabled} name=${job.name}")
            lines.add(job.toCronLine())
        }
        return lines.joinToString("\n") + "\n"
    }
}
