package online.youcd.krond.data

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object CronExpressionTranslator {

    private val dayOfWeekNames = mapOf(
        0 to "日", 1 to "一", 2 to "二", 3 to "三",
        4 to "四", 5 to "五", 6 to "六", 7 to "日"
    )

    fun translate(expression: String): String {
        val trimmed = expression.trim()

        if (trimmed.startsWith("@")) {
            return translateAtFormat(trimmed)
        }

        val fields = trimmed.split("\\s+".toRegex())
        if (fields.size < 5) return trimmed

        val minute = fields[0]
        val hour = fields[1]
        val dom = fields[2]
        val month = fields[3]
        val dow = fields[4]

        return translateStandard(minute, hour, dom, month, dow) ?: trimmed
    }

    fun isValid(expression: String): Boolean {
        val trimmed = expression.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("@")) return true
        val fields = trimmed.split("\\s+".toRegex())
        if (fields.size != 5) return false
        val validators = listOf(
            0..59 to listOf(fields[0]),
            0..23 to listOf(fields[1]),
            1..31 to listOf(fields[2]),
            1..12 to listOf(fields[3]),
            0..7 to listOf(fields[4])
        )
        return validators.all { (range, fieldList) ->
            fieldList.all { field -> isValidField(field, range) }
        }
    }

    fun computeNextRun(expression: String, now: ZonedDateTime = ZonedDateTime.now()): String? {
        val trimmed = expression.trim()
        if (!isValid(trimmed)) return null

        if (trimmed.startsWith("@")) {
            return when (trimmed) {
                "@hourly" -> "每小时的 0 分"
                "@daily", "@midnight" -> "每天 00:00"
                "@weekly" -> "每周日 00:00"
                "@monthly" -> "每月1日 00:00"
                "@yearly", "@annually" -> "每年1月1日 00:00"
                else -> translate(trimmed)
            }
        }

        val fields = trimmed.split("\\s+".toRegex())
        val next = computeNextMatch(fields, now) ?: return null
        val fmt = next.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

        val interval = describeInterval(fields)
        return if (interval != null) "$fmt，$interval" else fmt
    }

    private fun describeInterval(fields: List<String>): String? {
        val minute = fields[0]
        val hour = fields[1]
        val dom = fields[2]
        val month = fields[3]
        val dow = fields[4]

        val minuteNum = minute.toIntOrNull()
        val hourNum = hour.toIntOrNull()
        val domNum = dom.toIntOrNull()

        when {
            minute == "*" && hour == "*" -> return "每分钟执行"
            minute.startsWith("*/") && hour == "*" -> {
                val n = minute.removePrefix("*/")
                return "每${n}分钟执行"
            }
            minuteNum == 0 && hour.startsWith("*/") -> {
                val n = hour.removePrefix("*/")
                return "每${n}小时执行"
            }
            minuteNum != null && hourNum != null && dom == "*" && month == "*" && dow == "*" -> {
                return "每天 ${formatTime(hourNum, minuteNum)} 执行"
            }
            minuteNum != null && hourNum != null && dom == "*" && month == "*" && dow == "1-5" -> {
                return "工作日 ${formatTime(hourNum, minuteNum)} 执行"
            }
            minuteNum != null && hourNum != null && dom == "*" && month == "*" -> {
                val dowName = parseDow(dow) ?: parseDowRange(dow)
                if (dowName != null) return "$dowName ${formatTime(hourNum, minuteNum)} 执行"
            }
            minuteNum != null && hourNum != null && domNum != null -> {
                val d = dom.toIntOrNull() ?: return null
                if (month == "*") return "每月${d}日 ${formatTime(hourNum, minuteNum)} 执行"
                val m = month.toIntOrNull() ?: return null
                return "${m}月${d}日 ${formatTime(hourNum, minuteNum)} 执行"
            }
        }
        return null
    }

    private fun computeNextMatch(fields: List<String>, now: ZonedDateTime): ZonedDateTime? {
        val minute = fields[0]; val hour = fields[1]
        val dom = fields[2]; val month = fields[3]; val dow = fields[4]

        var candidate = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val deadline = now.plusDays(366)

        while (candidate.isBefore(deadline)) {
            val m = candidate.minute; val h = candidate.hour
            val d = candidate.dayOfMonth; val mon = candidate.monthValue
            val w = candidate.dayOfWeek.value % 7

            if (fieldMatches(minute, m, 0..59) &&
                fieldMatches(hour, h, 0..23) &&
                fieldMatches(dom, d, 1..31) &&
                fieldMatches(month, mon, 1..12) &&
                fieldMatches(dow, w, 0..7)
            ) return candidate

            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    private fun fieldMatches(field: String, value: Int, range: IntRange): Boolean {
        if (field == "*") return true
        if (field.startsWith("*/")) {
            val step = field.removePrefix("*/").toIntOrNull() ?: return false
            if (step == 0) return false
            return value % step == 0
        }
        return field.split(",").any { part ->
            if (part.contains("-")) {
                val rangeParts = part.split("-")
                val start = rangeParts[0].toIntOrNull() ?: return@any false
                val end = rangeParts[1].toIntOrNull() ?: return@any false
                value in start..end
            } else {
                part.toIntOrNull() == value
            }
        }
    }

    private fun isValidField(field: String, range: IntRange): Boolean {
        if (field == "*") return true
        if (field.startsWith("*/")) {
            val step = field.removePrefix("*/").toIntOrNull() ?: return false
            return step > 0
        }
        return field.split(",").all { part ->
            if (part.contains("-")) {
                val parts = part.split("-")
                val start = parts[0].toIntOrNull() ?: return@all false
                val end = parts[1].toIntOrNull() ?: return@all false
                start in range && end in range && start <= end
            } else {
                val num = part.toIntOrNull()
                num != null && num in range
            }
        }
    }

    private fun translateAtFormat(expr: String): String {
        return when {
            expr == "@yearly" || expr == "@annually" -> "每年1月1日午夜"
            expr == "@monthly" -> "每月1日午夜"
            expr == "@weekly" -> "每周日午夜"
            expr == "@daily" || expr == "@midnight" -> "每天午夜"
            expr == "@hourly" -> "每小时"
            expr.startsWith("@every ") -> {
                val interval = expr.removePrefix("@every ").trim()
                "每 $interval"
            }
            else -> expr
        }
    }

    private fun translateStandard(
        minute: String, hour: String, dom: String, month: String, dow: String
    ): String? {
        val isMinuteStep = minute.startsWith("*/")
        val isHourStep = hour.startsWith("*/")
        val minuteNum = minute.toIntOrNull()
        val hourNum = hour.toIntOrNull()
        val domNum = dom.toIntOrNull()
        val monthNum = month.toIntOrNull()

        if (minute == "*" && hour == "*" && dom == "*" && month == "*" && dow == "*") {
            return "每分钟"
        }
        if (isMinuteStep && hour == "*" && dom == "*" && month == "*" && dow == "*") {
            val n = minute.removePrefix("*/")
            return "每${n}分钟"
        }
        if (minuteNum != null && hourNum != null && dom == "*" && month == "*" && dow == "*") {
            return "每天 ${formatTime(hourNum, minuteNum)}"
        }
        if (minuteNum != null && hourNum != null && dom == "*" && month == "*") {
            val dowResult = parseDow(dow) ?: parseDowRange(dow)
            if (dowResult != null) return "$dowResult ${formatTime(hourNum, minuteNum)}"
        }
        if (minuteNum != null && hourNum != null && domNum != null && month == "*" && dow == "*") {
            return "每月${domNum}日 ${formatTime(hourNum, minuteNum)}"
        }
        if (minuteNum != null && hourNum != null && domNum != null && monthNum != null && dow == "*") {
            return "${monthNum}月${domNum}日 ${formatTime(hourNum, minuteNum)}"
        }
        if (minute == "0" && isHourStep && dom == "*" && month == "*" && dow == "*") {
            val n = hour.removePrefix("*/")
            return "每${n}小时整点"
        }
        if (minuteNum != null && hour == "*" && dom == "*" && month == "*" && dow == "*") {
            return "每小时${minuteNum}分"
        }
        if (minute == "0" && hour == "0" && domNum != null && month == "*" && dow == "*") {
            return "每月${domNum}日午夜"
        }
        return null
    }

    private fun parseDow(field: String): String? {
        val num = field.toIntOrNull()
        if (num != null && num in dayOfWeekNames) return "每周${dayOfWeekNames[num]}"
        return null
    }

    private fun parseDowRange(field: String): String? {
        if (field == "1-5") return "工作日"
        if (field == "0,6" || field == "6,0") return "每周末"
        if (field == "0-5") return "周日至周五"
        if (field == "1-6") return "周一至周六"
        val rangeMatch = Regex("""^(\d)-(\d)$""").find(field)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: return null
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: return null
            if (start in dayOfWeekNames && end in dayOfWeekNames) {
                return "每周${dayOfWeekNames[start]}至${dayOfWeekNames[end]}"
            }
        }
        val parts = field.split(",").mapNotNull { it.toIntOrNull() }
        if (parts.isNotEmpty() && parts.all { it in dayOfWeekNames }) {
            return "每周${parts.map { dayOfWeekNames[it] }.joinToString("、")}"
        }
        return null
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val h = if (hour < 10) "0$hour" else "$hour"
        val m = if (minute < 10) "0$minute" else "$minute"
        return "$h:$m"
    }
}
