package online.youcd.krond.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import online.youcd.krond.data.JobMetrics
import online.youcd.krond.data.KrondMetrics
import online.youcd.krond.viewmodel.CronViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: CronViewModel) {
    val state by viewModel.state.collectAsState()
    var rotateTrigger by remember { mutableStateOf(0L) }
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "rotation"
    )
    val refreshRotation = if (state.isLoadingMetrics) rotation else 0f
    LaunchedEffect(rotateTrigger) { viewModel.refreshMetrics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("统计")
                        if (state.lastMetricsUpdateTime != null) {
                            val timeStr = remember(state.lastMetricsUpdateTime) {
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(state.lastMetricsUpdateTime!!))
                            }
                            Text(
                                "数据更新于 $timeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { rotateTrigger = System.currentTimeMillis() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.rotate(refreshRotation)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoadingMetrics && state.metrics == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoadingMetrics,
                onRefresh = { viewModel.refreshMetrics() },
                modifier = Modifier.padding(padding).fillMaxSize()
            ) {
                StatsContent(state.metrics, Modifier)
            }
        }
    }
}

@Composable
private fun StatsContent(metrics: KrondMetrics?, modifier: Modifier = Modifier) {
    if (metrics == null || metrics.jobMetrics.isEmpty()) {
        Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "暂无统计数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "暂无执行记录，去添加并运行任务 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SummaryCards(metrics) }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    SimpleBarChart(
                        "各任务执行次数",
                        metrics.jobMetrics.map { (k, v) -> k to v.total.toFloat() },
                        Color(0xFF4CAF50),
                        valueFormatter = { v -> "${v.toInt()}次" }
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    SimpleBarChart(
                        "各任务平均耗时",
                        metrics.jobMetrics.map { (k, v) -> k to v.avgDurationMs.toFloat() },
                        Color(0xFF2196F3),
                        valueFormatter = { v -> formatDuration(v.toDouble()) }
                    )
                }
            }
        }
        item { JobStatusList(metrics) }
    }
}

private fun formatDuration(ms: Double): String {
    return when {
        ms < 1.0 -> "<1ms"
        ms < 1000 -> "${"%.0f".format(ms)}ms"
        ms < 60_000 -> "${"%.2f".format(ms / 1000)}s"
        else -> "${"%.2f".format(ms / 60_000)}min"
    }
}

@Composable
private fun SimpleBarChart(
    title: String,
    data: List<Pair<String, Float>>,
    color: Color,
    valueFormatter: (Float) -> String = { "%.2f".format(it.toDouble()) }
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    val maxVal = data.maxOfOrNull { it.second } ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEachIndexed { i, (label, value) ->
            var started by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay((i * 150).toLong())
                started = true
            }
            val animFraction by animateFloatAsState(
                targetValue = if (started) (value / maxVal).coerceIn(0f, 1f) else 0f,
                animationSpec = tween(500)
            )
            val animValue by animateFloatAsState(
                targetValue = if (started) value else 0f,
                animationSpec = tween(500)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(88.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animFraction)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = valueFormatter(animValue),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun JobStatusList(metrics: KrondMetrics) {
    Text("任务状态", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))

    val failedJobs = metrics.jobMetrics.filter { (_, m) -> m.failures > 0 }
    if (failedJobs.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "有 ${failedJobs.size} 个任务存在失败",
                    color = Color(0xFFF44336),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall
                )
                failedJobs.forEach { (name, _) ->
                    Text(
                        "  · $name",
                        color = Color(0xFFF44336).copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    metrics.jobMetrics.forEach { (name, m) ->
        val hasRun = m.total > 0
        val successRate = if (hasRun) (m.total - m.failures).toFloat() / m.total else 0f

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!hasRun) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else if (m.lastExitCode != 0) Color(0xFFF44336).copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!hasRun) {
                        Text(
                            "从未执行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                if (hasRun) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE0E0E0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = successRate)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (successRate >= 1f) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${"%.0f".format(successRate * 100)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (successRate >= 1f) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "成功率",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "✓ 成功 ${m.total - m.failures}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "✗ 失败 ${m.failures}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (m.failures > 0) Color(0xFFF44336) else Color(0xFF9E9E9E)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${m.total}次执行 · ${m.failures}次失败 · 平均 ${formatDuration(m.avgDurationMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
