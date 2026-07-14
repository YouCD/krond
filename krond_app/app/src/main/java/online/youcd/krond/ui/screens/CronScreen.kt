package online.youcd.krond.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptsDialog(
    scripts: List<ScriptDetail>,
    onView: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("脚本管理 (${scripts.size})", modifier = Modifier.weight(1f))
                IconButton(onClick = onUpload) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "上传脚本")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        },
        text = {
            if (scripts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无脚本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onUpload) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("上传第一个脚本")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(scripts) { script ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = { onView(script.name) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (script.name.endsWith(".sh"))
                                    Icons.Default.Terminal
                                else
                                    Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = script.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (script.size.isNotBlank()) {
                                    Text(
                                        text = "${script.permissions}  ${script.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(script.name) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ScriptContentDialog(
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("脚本内容") },
        text = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun NoRootView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "需要 Root 权限",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "请确保已安装 Krond Injector 模块\n并授予 Root 权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CronJobList(
    jobs: List<CronJob>,
    onToggle: (CronJob) -> Unit,
    onEdit: (CronJob) -> Unit,
    onDelete: (CronJob) -> Unit,
    modifier: Modifier = Modifier
) {
    if (jobs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无定时任务\n点击 + 添加",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val enabledJobs = jobs.filter { it.enabled }
    val disabledJobs = jobs.filter { !it.enabled }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (enabledJobs.isNotEmpty()) {
            stickyHeader { SectionHeader("已开启", enabledJobs.size, Color(0xFF4CAF50)) }
            items(enabledJobs, key = { "enabled_${it.id}" }) { job ->
                CronJobCard(
                    job = job,
                    onToggle = { onToggle(job) },
                    onEdit = { onEdit(job) },
                    onDelete = { onDelete(job) }
                )
            }
        }
        if (disabledJobs.isNotEmpty()) {
            stickyHeader { SectionHeader("未开启", disabledJobs.size, Color(0xFFF44336)) }
            items(disabledJobs, key = { "disabled_${it.id}" }) { job ->
                CronJobCard(
                    job = job,
                    onToggle = { onToggle(job) },
                    onEdit = { onEdit(job) },
                    onDelete = { onDelete(job) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, color: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CronJobCard(
    job: CronJob,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (job.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Switch(
                    checked = job.enabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (job.name.isNotBlank()) {
                        Text(
                            text = job.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = job.schedule,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (job.next.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "下次执行: ${formatNextTime(job.next)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = job.command,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (job.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KrondControlButton(
    isRunning: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit
) {
    if (isRunning) {
        Box {
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "krond 控制",
                    tint = Color(0xFF4CAF50)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("重启 krond") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = onRestart
                )
                DropdownMenuItem(
                    text = { Text("停止 krond") },
                    leadingIcon = { Icon(Icons.Default.PowerOff, contentDescription = null) },
                    onClick = onStop
                )
            }
        }
    } else {
        IconButton(onClick = onStart) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "启动 krond",
                tint = Color(0xFFF44336)
            )
        }
    }
}

@Composable
fun CronJobDialog(
    title: String,
    initialName: String = "",
    initialSchedule: String = "",
    initialCommand: String = "",
    scripts: List<ScriptDetail> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (schedule: String, command: String, name: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var schedule by remember { mutableStateOf(initialSchedule) }
    var command by remember { mutableStateOf(initialCommand) }
    var error by remember { mutableStateOf<String?>(null) }
    var scriptMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("任务名称（可选）") },
                    placeholder = { Text("每天 03:00 清理临时文件") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it; error = null },
                    label = { Text("Cron 表达式") },
                    placeholder = { Text("*/5 * * * *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it; error = null },
                        label = { Text("命令") },
                        placeholder = { Text("/system/bin/echo hello") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = error != null
                    )
                    Spacer(Modifier.width(4.dp))
                    Box {
                        IconButton(onClick = { scriptMenuExpanded = true }) {
                            Icon(Icons.Default.Folder, contentDescription = "选择脚本")
                        }
                        DropdownMenu(
                            expanded = scriptMenuExpanded,
                            onDismissRequest = { scriptMenuExpanded = false }
                        ) {
                            if (scripts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无脚本") },
                                    onClick = { scriptMenuExpanded = false }
                                )
                            } else {
                                scripts.forEach { script ->
                                    DropdownMenuItem(
                                        text = { Text(script.name) },
                                        onClick = {
                                            command = "/data/krond/scripts/${script.name}"
                                            scriptMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (schedule.isBlank() || command.isBlank()) {
                        error = "请填写完整"
                    } else {
                        onConfirm(schedule.trim(), command.trim(), name.trim())
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatNextTime(rfc3339: String): String {
    return try {
        val zdt = ZonedDateTime.parse(rfc3339)
        val now = ZonedDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        val prefix = when {
            zdt.isBefore(now) -> "已过"
            zdt.minusHours(1).isBefore(now) -> "即将 "
            else -> ""
        }
        "$prefix${zdt.format(formatter)}"
    } catch (_: Exception) {
        rfc3339
    }
}

@Composable
private fun AppMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onUploadScript: () -> Unit,
    onManageScripts: () -> Unit
) {
    Box {
        IconButton(onClick = { onMenuExpandedChange(true) }) {
            Icon(Icons.Default.FileDownload, contentDescription = "导入/导出")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("导入任务") },
                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                onClick = onImport
            )
            DropdownMenuItem(
                text = { Text("导出任务") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = onExport
            )
            DropdownMenuItem(
                text = { Text("上传脚本") },
                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                onClick = onUploadScript
            )
            DropdownMenuItem(
                text = { Text("脚本管理") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = onManageScripts
            )
        }
    }
}


