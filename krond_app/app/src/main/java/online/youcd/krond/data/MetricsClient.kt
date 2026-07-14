package online.youcd.krond.data

class MetricsClient(private val client: KrondClient = KrondClient()) {

    fun fetchMetrics(): KrondMetrics {
        val raw = client.fetchMetrics()
        return parsePrometheusMetrics(raw)
    }
}
