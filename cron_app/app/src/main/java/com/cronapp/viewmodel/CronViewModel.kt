package com.cronapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cronapp.data.CronJob
import com.cronapp.data.CronRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CronUiState(
    val jobs: List<CronJob> = emptyList(),
    val hasRoot: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val editingJob: CronJob? = null,
    val snackbarMessage: String? = null,
    val showLogs: Boolean = false,
    val logs: List<String> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val isCrondRunning: Boolean = false,
    val isRefreshing: Boolean = false,
    val pendingDeleteJob: CronJob? = null
)

class CronViewModel : ViewModel() {

    private val repository = CronRepository()

    private val _state = MutableStateFlow(CronUiState())
    val state = _state.asStateFlow()

    init {
        checkRootAndLoad()
    }

    private fun checkRootAndLoad() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) { repository.hasRoot() }
            if (hasRoot) {
                val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
                val running = withContext(Dispatchers.IO) { repository.isCrondRunning() }
                _state.update { it.copy(hasRoot = true, jobs = jobs, isCrondRunning = running, isLoading = false) }
            } else {
                _state.update { it.copy(hasRoot = false, isLoading = false) }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val jobs = withContext(Dispatchers.IO) { repository.getCronJobs() }
            val running = withContext(Dispatchers.IO) { repository.isCrondRunning() }
            _state.update { it.copy(jobs = jobs, isCrondRunning = running, isRefreshing = false) }
        }
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
                refresh()
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
                refresh()
                _state.update { it.copy(snackbarMessage = "已保存修改") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "保存失败") }
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
                refresh()
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
                refresh()
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

    private var logStreamJob: kotlinx.coroutines.Job? = null
    private val logMaxLines = 500

    fun openLogs() {
        _state.update { it.copy(showLogs = true) }
        startLogStream()
    }

    fun closeLogs() {
        _state.update { it.copy(showLogs = false) }
        stopLogStream()
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

    fun startCrond() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.startCrond() }
                val running = withContext(Dispatchers.IO) { repository.isCrondRunning() }
                _state.update { it.copy(isCrondRunning = running, snackbarMessage = if (running) "crond 已启动" else "crond 启动失败") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "启动失败") }
            }
        }
    }

    fun stopCrond() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.stopCrond() }
                val running = withContext(Dispatchers.IO) { repository.isCrondRunning() }
                _state.update { it.copy(isCrondRunning = running, snackbarMessage = if (!running) "crond 已停止" else "crond 停止失败") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "停止失败") }
            }
        }
    }

    fun restartCrond() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.restartCrond() }
                val running = withContext(Dispatchers.IO) { repository.isCrondRunning() }
                _state.update { it.copy(isCrondRunning = running, snackbarMessage = if (running) "crond 已重启" else "crond 重启失败") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = e.message ?: "重启失败") }
            }
        }
    }
}
