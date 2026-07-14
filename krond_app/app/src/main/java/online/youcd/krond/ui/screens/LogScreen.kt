package online.youcd.krond.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logs: List<String>,
    isLoading: Boolean,
    currentLogTarget: String,
    onClear: () -> Unit,
    onLogTargetChange: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var showTargetMenu by remember { mutableStateOf(false) }

    val logsState = rememberUpdatedState(logs)
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> autoScroll = idx >= (logsState.value.lastIndex - 1) }
    }
    LaunchedEffect(logs.lastOrNull()) {
        if (autoScroll && logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("执行日志") },
                actions = {
                    Box {
                        IconButton(onClick = { showTargetMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "日志目标")
                        }
                        DropdownMenu(
                            expanded = showTargetMenu,
                            onDismissRequest = { showTargetMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (currentLogTarget == "file") "✓ 仅文件" else "仅文件") },
                                onClick = { onLogTargetChange("file"); showTargetMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentLogTarget == "logcat") "✓ 仅 Logcat" else "仅 Logcat") },
                                onClick = { onLogTargetChange("logcat"); showTargetMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentLogTarget == "both") "✓ 双写" else "双写") },
                                onClick = { onLogTargetChange("both"); showTargetMenu = false }
                            )
                        }
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!autoScroll) {
                FloatingActionButton(onClick = {
                    autoScroll = true
                    if (logs.isNotEmpty()) scope.launch { listState.scrollToItem(logs.lastIndex) }
                }) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "回到底部")
                }
            }
        }
    ) { padding ->
        when {
            isLoading && logs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            logs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
