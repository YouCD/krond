package online.youcd.krond.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import online.youcd.krond.data.CronJob
import online.youcd.krond.data.ScriptDetail
import online.youcd.krond.viewmodel.CronViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronScreen(viewModel: CronViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var krondMenuExpanded by remember { mutableStateOf(false) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var pendingUploadFolder by remember { mutableStateOf("") }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri, context.contentResolver)
        }
    }

    val scriptUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadScript(uri, context.contentResolver, pendingUploadFolder)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri, context.contentResolver)
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Krond")
                        val statusColor = if (state.isKrondRunning)
                            Color(0xFF4CAF50)
                        else
                            Color(0xFFF44336)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = if (state.isKrondRunning) "krond 运行中" else "krond 已停止",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    AppMenuButton(
                        menuExpanded = appMenuExpanded,
                        onMenuExpandedChange = { appMenuExpanded = it },
                        onImport = { appMenuExpanded = false; importLauncher.launch(arrayOf("*/*")) },
                        onExport = {
                            appMenuExpanded = false
                            val fileName = "krond_" + java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss", java.util.Locale.getDefault()
                            ).format(java.util.Date()) + ".tar"
                            exportLauncher.launch(fileName)
                        },
                        onManageScripts = {
                            appMenuExpanded = false
                            viewModel.showScriptsDialog()
                        },
                        onCheckUpdate = { viewModel.checkForUpdate() }
                    )
                    KrondControlButton(
                        isRunning = state.isKrondRunning,
                        menuExpanded = krondMenuExpanded,
                        onMenuExpandedChange = { krondMenuExpanded = it },
                        onStart = { viewModel.startKrond() },
                        onRestart = { viewModel.restartKrond(); krondMenuExpanded = false },
                        onStop = { showStopConfirmDialog = true; krondMenuExpanded = false }
                    )
                }
            )
        },
        floatingActionButton = {
            if (state.hasRoot) {
                FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "添加任务")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (!state.hasRoot) {
            NoRootView(modifier = Modifier.padding(padding))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.weight(1f)
                ) {
                CronJobList(
                    jobs = state.jobs,
                    onToggle = { viewModel.toggleJob(it) },
                    onRun = { viewModel.runJob(it) },
                    onEdit = { viewModel.showEditDialog(it) },
                    onDelete = { viewModel.requestDeleteJob(it) },
                    modifier = Modifier.fillMaxSize()
                )
                }
            }
        }
    }

    if (state.showAddDialog) {
        LaunchedEffect(state.showAddDialog) {
            viewModel.loadScripts()
        }
        CronJobDialog(
            title = "添加任务",
            scripts = state.scripts,
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { schedule, command, name ->
                viewModel.addJob(schedule, command, name)
                viewModel.hideAddDialog()
            }
        )
    }

    state.editingJob?.let { job ->
        LaunchedEffect(job) {
            viewModel.loadScripts()
        }
        CronJobDialog(
            title = "编辑任务",
            initialName = job.name,
            initialSchedule = job.schedule,
            initialCommand = job.command,
            scripts = state.scripts,
            onDismiss = { viewModel.hideEditDialog() },
            onConfirm = { schedule, command, name ->
                viewModel.updateJob(job, schedule, command, name)
                viewModel.hideEditDialog()
            }
        )
    }

    state.pendingDeleteJob?.let { job ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteJob() },
            title = { Text("删除任务") },
            text = {
                Text(
                    "确定要删除任务${if (job.name.isNotBlank()) "「${job.name}」" else ""}吗？此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteJob() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteJob() }) {
                    Text("取消")
                }
            }
        )
    }

    if (state.showScriptsDialog) {
        ScriptsDialog(
            scripts = state.scripts,
            currentFolder = state.currentScriptFolder,
            onView = { viewModel.viewScript(it) },
            onDelete = { viewModel.deleteScript(it) },
            onRefresh = { viewModel.loadScripts() },
            onUpload = {
                pendingUploadFolder = state.currentScriptFolder
                scriptUploadLauncher.launch(arrayOf("*/*"))
            },
            onCreateFolder = { viewModel.createScriptFolder(it) },
            onNavigateFolder = { viewModel.setScriptFolder(it) },
            onDismiss = { viewModel.hideScriptsDialog() }
        )
    }

    if (state.showScriptContent) {
        ScriptContentDialog(
            content = state.selectedScriptContent ?: "",
            onDismiss = { viewModel.hideScriptContent() }
        )
    }

    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text("停止 krond") },
            text = { Text("停止 krond 将中断所有定时任务，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmDialog = false
                    viewModel.stopKrond()
                }) {
                    Text("停止", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (state.showUpdateDialog) {
        UpdateDialog(
            updateStatus = state.updateStatus,
            isChecking = state.isCheckingUpdate,
            isApplying = state.isApplyingUpdate,
            onDismiss = { viewModel.hideUpdateDialog() },
            onApply = { viewModel.applyUpdate() }
        )
    }

}

@Composable
private fun UpdateDialog(
    updateStatus: online.youcd.krond.data.UpdateStatus?,
    isChecking: Boolean,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isChecking && !isApplying) onDismiss() },
        title = { Text("检查更新") },
        text = {
            when {
                isChecking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("正在检查更新...")
                    }
                }
                isApplying -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("正在应用更新...")
                    }
                }
                updateStatus == null -> {
                    Text("获取更新信息失败")
                }
                updateStatus.hasUpdate -> {
                    HasUpdateContent(updateStatus)
                }
                else -> {
                    NoUpdateContent(updateStatus)
                }
            }
        },
        confirmButton = {
            if (updateStatus != null && updateStatus.hasUpdate && !isApplying) {
                TextButton(
                    onClick = onApply,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Text("🚀 立即更新", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun HasUpdateContent(status: online.youcd.krond.data.UpdateStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 头部卡片
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🎉 发现新版本", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("v${status.currentVersion}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    Icon(Icons.Default.ArrowForward, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp))
                    Text("v${status.latestVersion}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    "${if (status.isPreRelease) "🛠 测试版" else "✅ 稳定版"} · ${formatDate(status.publishedAt)} · ${formatSize(status.assetSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // 更新日志
        if (status.changelog.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            val lines = status.changelog.lines().filter { it.isNotBlank() }
            val displayLines = if (expanded) lines else lines.take(8)

            Text("更新日志", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = displayLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (lines.size > 8) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (expanded) "收起" else "展开更多 (${lines.size - 8} 条)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoUpdateContent(status: online.youcd.krond.data.UpdateStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("✅ 已是最新版本", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("v${status.currentVersion}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${if (status.isPreRelease) "测试版" else "稳定版"} · ${formatDate(status.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatDate(iso: String): String {
    return iso.substringBefore("T")
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}



