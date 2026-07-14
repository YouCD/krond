package online.youcd.krond.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import online.youcd.krond.data.CronJob
import online.youcd.krond.data.CronRepository
import online.youcd.krond.data.KrondMetrics
import online.youcd.krond.data.ScriptDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CronUiState(
    val selectedTab: Int = 0,
    val jobs: List<CronJob> = emptyList(),
    val hasRoot: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val editingJob: CronJob? = null,
    val snackbarMessage: String? = null,
    val logs: List<String> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val isKrondRunning: Boolean = false,
    val isRefreshing: Boolean = false,
    val pendingDeleteJob: CronJob? = null,
    val currentLogTarget: String = "both",
    val scripts: List<ScriptDetail> = emptyList(),
    val showScriptsDialog: Boolean = false,
    val selectedScriptContent: String? = null,
    val showScriptContent: Boolean = false,
    val metrics: KrondMetrics? = null,
    val isLoadingMetrics: Boolean = false,
)

class CronViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CronRepository()
    private val appContext = application

    private val _state = MutableStateFlow(CronUiState())
    val state = _state.asStateFlow()

    init {
        checkRootAndLoad()
        startAutoCheck()
    }

    private fun startAutoCheck() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5 * 60 * 1000L) // 每 5 分钟刷新任务列表
                val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
                _state.update { it.copy(jobs = jobs) }
            }
        }
    }

    private fun checkRootAndLoad() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) { repository.hasRoot() }
            if (hasRoot) {
                val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
                val running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
                _state.update { it.copy(hasRoot = true, jobs = jobs, isKrondRunning = running, isLoading = false) }
            } else {
                _state.update { it.copy(hasRoot = false, isLoading = false) }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            try {
                val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
                val running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
                _state.update { it.copy(jobs = jobs, isKrondRunning = running) }
            } finally {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed < 500) kotlinx.coroutines.delay(500 - elapsed)
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** 同步刷新任务列表和 krond 状态（在调用协程内执行） */
    private suspend fun syncRefresh() {
        val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
        val running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
        _state.update { it.copy(jobs = jobs, isKrondRunning = running) }
    }

    fun addJob(schedule: String, command: String, name: String = "") {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = repository.getCronJobs().toMutableList()
                    val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
                    current.add(CronJob(id = newId, name = name, schedule = schedule, command = command, enabled = true))
                    repository.setCronJobs(current)
                }
                syncRefresh()
                _state.update { it.copy(snackbarMessage = "已添加任务") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "添加失败") }
            }
        }
    }

    fun updateJob(job: CronJob, newSchedule: String, newCommand: String, newName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = repository.getCronJobs().toMutableList()
                    val idx = current.indexOfFirst { it.id == job.id }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(schedule = newSchedule, command = newCommand, name = newName)
                        repository.setCronJobs(current)
                    }
                }
                syncRefresh()
                _state.update { it.copy(snackbarMessage = "已保存修改") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "保存失败") }
            }
        }
    }

    fun runJob(job: CronJob) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.runJob(job.id) }
                _state.update { it.copy(snackbarMessage = "已触发: ${job.name}") }
                // 短轮询用于 UI 刷新执行状态（通知由 krond 广播直推）
                for (i in 0 until 15) {
                    kotlinx.coroutines.delay(300)
                    val current = withContext(Dispatchers.IO) { repository.getCronJobs() }
                    val found = current.find { it.id == job.id }
                    if (found != null && found.lastRun.isNotBlank() && found.lastRun != job.lastRun) {
                        break
                    }
                }
                val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
                _state.update { it.copy(jobs = jobs) }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "执行失败: ${e.message}") }
            }
        }
    }

    fun toggleJob(job: CronJob) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = repository.getCronJobs().toMutableList()
                    val idx = current.indexOfFirst { it.id == job.id }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(enabled = !current[idx].enabled)
                        repository.setCronJobs(current)
                    }
                }
                syncRefresh()
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "操作失败") }
            }
        }
    }

    fun requestDeleteJob(job: CronJob) {
        _state.update { it.copy(pendingDeleteJob = job) }
    }

    fun cancelDeleteJob() {
        _state.update { it.copy(pendingDeleteJob = null) }
    }

    fun confirmDeleteJob() {
        val job = _state.value.pendingDeleteJob ?: return
        _state.update { it.copy(pendingDeleteJob = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = repository.getCronJobs().toMutableList()
                    current.removeAll { it.id == job.id }
                    repository.setCronJobs(current)
                }
                syncRefresh()
                _state.update { it.copy(snackbarMessage = "已删除任务") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun showEditDialog(job: CronJob) {
        _state.update { it.copy(editingJob = job) }
    }

    fun hideEditDialog() {
        _state.update { it.copy(editingJob = null) }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun exportToUri(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val zip = withContext(Dispatchers.IO) { repository.exportToZip() }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(zip) }
                }
                _state.update { it.copy(snackbarMessage = "已导出 ${state.value.jobs.size} 个任务") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "导出失败: ${e.message}") }
            }
        }
    }

    fun importFromUri(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    _state.update { it.copy(snackbarMessage = "读取文件失败") }
                    return@launch
                }
                val (tasks, scripts) = withContext(Dispatchers.IO) {
                    repository.importFromZip(bytes)
                }
                val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
                val running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
                val parts = mutableListOf<String>()
                if (tasks > 0) parts.add("$tasks 个任务")
                if (scripts > 0) parts.add("$scripts 个脚本")
                _state.update { it.copy(jobs = jobs, isKrondRunning = running, snackbarMessage = "已导入 ${parts.joinToString(", ")}") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "导入失败：" + (e.message ?: "格式错误")) }
            }
        }
    }

    fun loadScripts() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.listScriptDetails() }
            _state.update { it.copy(scripts = list) }
        }
    }

    fun uploadScript(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    var n: String? = null
                    try {
                        val cursor = contentResolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) n = it.getString(idx)
                            }
                        }
                    } catch (_: Exception) {}
                    if (n.isNullOrBlank()) n = uri.lastPathSegment
                    if (n.isNullOrBlank()) "script.sh" else n!!
                }
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    _state.update { it.copy(snackbarMessage = "读取文件失败") }
                    return@launch
                }
                val ok = withContext(Dispatchers.IO) { repository.uploadScript(name, bytes) }
                _state.update { it.copy(snackbarMessage = if (ok) "已上传: $name" else "上传失败") }
                if (ok) loadScripts()
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "上传失败: ${e.message}") }
            }
        }
    }

    fun deleteScript(name: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.deleteScript(name) }
            _state.update { it.copy(snackbarMessage = if (ok) "已删除: $name" else "删除失败") }
            if (ok) loadScripts()
        }
    }

    fun viewScript(name: String) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { repository.readScriptContent(name) }
            _state.update { it.copy(selectedScriptContent = content ?: "读取失败", showScriptContent = true) }
        }
    }

    fun hideScriptContent() {
        _state.update { it.copy(selectedScriptContent = null, showScriptContent = false) }
    }

    fun showScriptsDialog() {
        loadScripts()
        _state.update { it.copy(showScriptsDialog = true) }
    }

    fun hideScriptsDialog() {
        _state.update { it.copy(showScriptsDialog = false) }
    }

    private var logStreamJob: kotlinx.coroutines.Job? = null
    private val logMaxLines = 500

    fun selectTab(tab: Int) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == 1) {
            startLogStream()
            fetchConfig()
        } else if (tab == 0) {
            stopLogStream()
            reloadJobs()
        } else if (tab == 2) {
            loadMetrics()
        }
    }

    fun openLogs() {
        selectTab(1)
    }

    fun closeLogs() {
        selectTab(0)
    }

    fun loadMetrics() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMetrics = true) }
            try {
                val metrics = withContext(Dispatchers.IO) { repository.fetchMetrics() }
                _state.update { it.copy(metrics = metrics, isLoadingMetrics = false) }
            } catch (e: Exception) {
                _state.update { it.copy(metrics = null, isLoadingMetrics = false) }
            }
        }
    }

    fun refreshMetrics() {
        loadMetrics()
    }

    private fun reloadJobs() {
        viewModelScope.launch {
            val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
            val running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
            _state.update { it.copy(jobs = jobs, isKrondRunning = running) }
        }
    }

    private fun startLogStream() {
        if (logStreamJob?.isActive == true) return
        _state.update { it.copy(isLoadingLogs = true) }
        logStreamJob = viewModelScope.launch {
            while (true) {
                val logs = withContext(Dispatchers.IO) { repository.fetchLogs() }
                _state.update {
                    it.copy(
                        logs = if (logs.size > logMaxLines) logs.takeLast(logMaxLines) else logs,
                        isLoadingLogs = false
                    )
                }
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    private fun stopLogStream() {
        logStreamJob?.cancel()
        logStreamJob = null
    }

    fun refreshLogs() {
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) { repository.fetchLogs() }
            _state.update {
                it.copy(
                    logs = if (logs.size > logMaxLines) logs.takeLast(logMaxLines) else logs,
                    isLoadingLogs = false
                )
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearLogs() }
            val logs = withContext(Dispatchers.IO) { repository.fetchLogs() }
            _state.update { it.copy(logs = logs, isLoadingLogs = false) }
        }
    }

    fun fetchConfig() {
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) { repository.getLogTarget() }
            _state.update { it.copy(currentLogTarget = target) }
        }
    }

    fun setLogTarget(target: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.setLogTarget(target) }
                _state.update { it.copy(currentLogTarget = target, snackbarMessage = "日志目标: $target") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "设置失败") }
            }
        }
    }

    fun startKrond() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.startKrond() }
                var running = false
                for (i in 0 until 15) {
                    running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
                    if (running) break
                    kotlinx.coroutines.delay(300)
                }
                if (running) syncRefresh()
                _state.update { it.copy(isKrondRunning = running, snackbarMessage = if (running) "krond 已启动" else "krond 启动失败") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "启动失败") }
            }
        }
    }

    fun stopKrond() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.stopKrond() }
                val running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
                if (!running) syncRefresh()
                _state.update { it.copy(isKrondRunning = running, snackbarMessage = if (!running) "krond 已停止" else "krond 停止失败") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "停止失败") }
            }
        }
    }

    fun restartKrond() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.restartKrond() }
                var running = false
                for (i in 0 until 15) {
                    running = withContext(Dispatchers.IO) { repository.isKrondRunning() }
                    if (running) break
                    kotlinx.coroutines.delay(300)
                }
                if (running) syncRefresh()
                _state.update { it.copy(isKrondRunning = running, snackbarMessage = if (running) "krond 已重启" else "krond 重启失败") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "重启失败") }
            }
        }
    }
}
