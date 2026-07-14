package online.youcd.krond.ui.screens

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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.youcd.krond.data.KrondMetrics
import online.youcd.krond.viewmodel.CronViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: CronViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.refreshMetrics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoadingMetrics) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            StatsContent(state.metrics, Modifier.padding(padding))
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
                        Color(0xFF4CAF50)
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    SimpleBarChart(
                        "各任务平均耗时 (秒)",
                        metrics.jobMetrics.map { (k, v) -> k to (v.avgDurationMs / 1000).toFloat() },
                        Color(0xFF2196F3)
                    )
                }
            }
        }
        item { JobStatusList(metrics) }
    }
}



@Composable
private fun SimpleBarChart(title: String, data: List<Pair<String, Float>>, color: Color) {
    Text(title, style = MaterialTheme.typography.titleSmall)
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
                    modifier = Modifier.widthIn(max = 88.dp)
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
                    text = "%.2f".format(animValue.toDouble()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.widthIn(min = 36.dp)
                )
            }
        }
    }
}

@Composable
private fun JobStatusList(metrics: KrondMetrics) {
    Text("任务状态", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    metrics.jobMetrics.forEach { (name, m) ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (m.lastExitCode != 0) Color(0xFFF44336).copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${m.total}次 · ${m.failures}次失败 · ${"%.0f".format(m.avgDurationMs)}ms/次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (m.lastExitCode == 0) "✓ 0" else "✗ ${m.lastExitCode}",
                    color = if (m.lastExitCode == 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
