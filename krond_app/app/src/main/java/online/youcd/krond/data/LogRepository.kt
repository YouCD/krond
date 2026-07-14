package online.youcd.krond.data

class LogRepository(private val client: KrondClient) {

    fun fetchLogs(): List<String> = client.fetchLogs()

    fun clearLogs() = client.clearLogs()
}
