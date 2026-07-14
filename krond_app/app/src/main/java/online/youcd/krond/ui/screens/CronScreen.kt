package online.youcd.krond.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            viewModel.uploadScript(uri, context.contentResolver)
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
                        Text("Cron Manager")
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
                    IconButton(onClick = { viewModel.openLogs() }) {
                        Icon(Icons.Default.Terminal, contentDescription = "日志")
                    }
                    AppMenuButton(
                        menuExpanded = appMenuExpanded,
                        onMenuExpandedChange = { appMenuExpanded = it },
                        onImport = { appMenuExpanded = false; importLauncher.launch(arrayOf("*/*")) },
                        onExport = {
                            appMenuExpanded = false
                            val fileName = java.text.SimpleDateFormat(
                                "yyyy_MM_dd-HHmmss", java.util.Locale.getDefault()
                            ).format(java.util.Date()) + "_krond.zip"
                            exportLauncher.launch(fileName)
                        },
                        onUploadScript = {
                            appMenuExpanded = false
                            scriptUploadLauncher.launch(arrayOf("*/*"))
                        },
                        onManageScripts = {
                            appMenuExpanded = false
                            viewModel.showScriptsDialog()
                        }
                    )
                    KrondControlButton(
                        isRunning = state.isKrondRunning,
                        menuExpanded = krondMenuExpanded,
                        onMenuExpandedChange = { krondMenuExpanded = it },
                        onStart = { viewModel.startKrond() },
                        onRestart = { viewModel.restartKrond(); krondMenuExpanded = false },
                        onStop = { viewModel.stopKrond(); krondMenuExpanded = false }
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
            onView = { viewModel.viewScript(it) },
            onDelete = { viewModel.deleteScript(it) },
            onRefresh = { viewModel.loadScripts() },
            onUpload = { scriptUploadLauncher.launch(arrayOf("*/*")) },
            onDismiss = { viewModel.hideScriptsDialog() }
        )
    }

    if (state.showScriptContent) {
        ScriptContentDialog(
            content = state.selectedScriptContent ?: "",
            onDismiss = { viewModel.hideScriptContent() }
        )
    }

}




