package online.youcd.krond.data

data class JobMetrics(
    val jobName: String,
    val total: Long = 0,
    val failures: Long = 0,
    val avgDurationMs: Double = 0.0,
    val lastExitCode: Int = 0,
    val lastDurationMs: Double = 0.0,
)

data class KrondMetrics(
    val uptime: Long = 0,
    val jobMetrics: Map<String, JobMetrics> = emptyMap(),
)

fun parsePrometheusMetrics(text: String): KrondMetrics {
    val counters = mutableMapOf<String, MutableMap<String, Double>>()
    val gauges = mutableMapOf<String, MutableMap<String, Double>>()
    val histogramSums = mutableMapOf<String, MutableMap<String, Double>>()
    val histogramCounts = mutableMapOf<String, MutableMap<String, Double>>()

    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) continue

        // 找最后一个空格分隔值，避免标签值中的空格干扰
        val lastSpace = trimmed.lastIndexOf(' ')
        if (lastSpace < 0) continue
        val rawName = trimmed.substring(0, lastSpace)
        val value = trimmed.substring(lastSpace + 1).toDoubleOrNull() ?: continue

        val labels = mutableMapOf<String, String>()
        val name: String
        if (rawName.contains('{') && rawName.contains('}')) {
            name = rawName.substring(0, rawName.indexOf('{'))
            val labelsStr = rawName.substring(rawName.indexOf('{') + 1, rawName.lastIndexOf('}'))
            labelsStr.split(",").forEach { kv ->
                val eq = kv.indexOf('=')
                if (eq > 0) {
                    labels[kv.substring(0, eq).trim()] = kv.substring(eq + 1).trim('"')
                }
            }
        } else {
            name = rawName
        }

        when {
            name.endsWith("_sum") && labels.containsKey("job_name") -> {
                histogramSums.getOrPut(name.removeSuffix("_sum")) { mutableMapOf() }[labels["job_name"]!!] = value
            }
            name.endsWith("_count") && labels.containsKey("job_name") -> {
                histogramCounts.getOrPut(name.removeSuffix("_count")) { mutableMapOf() }[labels["job_name"]!!] = value
            }
            name.endsWith("_total") && labels.containsKey("job_name") -> {
                counters.getOrPut(name.removeSuffix("_total")) { mutableMapOf() }[labels["job_name"]!!] = value
            }
            labels.containsKey("job_name") -> {
                gauges.getOrPut(name) { mutableMapOf() }[labels["job_name"]!!] = value
            }
        }
    }

    val allJobNames = (counters.keys + gauges.keys + histogramSums.keys + histogramCounts.keys)
        .flatMap { key ->
            (counters[key]?.keys ?: emptySet()) +
            (gauges[key]?.keys ?: emptySet()) +
            (histogramSums[key]?.keys ?: emptySet()) +
            (histogramCounts[key]?.keys ?: emptySet())
        }
        .filter { it.isNotBlank() }
        .toSet()

    val jobMetrics = allJobNames.mapNotNull { jobName ->
        val total = (counters["krond_job_executions"]?.get(jobName)?.toLong()) ?: 0
        if (total == 0L) return@mapNotNull null

        val failures = (counters["krond_job_failures"]?.get(jobName)?.toLong()) ?: 0
        val sum = histogramSums["krond_job_duration_seconds"]?.get(jobName) ?: 0.0
        val count = histogramCounts["krond_job_duration_seconds"]?.get(jobName) ?: 1.0
        val avgDurationMs = if (count > 0) (sum / count) * 1000 else 0.0
        val exitCode = (gauges["krond_job_last_exit_code"]?.get(jobName)?.toInt()) ?: 0
        val lastDur = (gauges["krond_job_last_duration_seconds"]?.get(jobName)) ?: 0.0

        JobMetrics(
            jobName = jobName,
            total = total,
            failures = failures,
            avgDurationMs = avgDurationMs,
            lastExitCode = exitCode,
            lastDurationMs = lastDur * 1000,
        )
    }

    val uptime = (gauges["krond_uptime_seconds"]?.get("")?.toLong()) ?: 0
    return KrondMetrics(uptime = uptime, jobMetrics = jobMetrics.associateBy { it.jobName })
}
