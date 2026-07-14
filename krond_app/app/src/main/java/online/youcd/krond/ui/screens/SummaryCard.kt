package online.youcd.krond.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import online.youcd.krond.data.KrondMetrics

@Composable
fun SummaryCards(metrics: KrondMetrics) {
    val total = metrics.jobMetrics.values.sumOf { it.total }
    val failures = metrics.jobMetrics.values.sumOf { it.failures }
    val successRate = if (total > 0) "%.1f%%".format((total - failures).toDouble() / total * 100) else "N/A"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val cards = listOf(
            Triple("总执行", "$total", Color(0xFF4CAF50)),
            Triple("成功率", successRate, Color(0xFF2196F3)),
            Triple("失败数", "$failures", Color(0xFFF44336)),
        )
        cards.forEachIndexed { i, (title, value, color) ->
            SummaryCardAnimated(
                title = title,
                value = value,
                color = color,
                modifier = Modifier.weight(1f),
                delayMs = i * 150
            )
        }
    }
}

@Composable
private fun SummaryCardAnimated(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    delayMs: Int = 0
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        started = true
    }

    val suffix = if (value.endsWith("%")) "%" else ""
    val targetNum = value.removeSuffix("%").toDoubleOrNull() ?: 0.0
    val isInt = targetNum == targetNum.toLong().toDouble()

    val animValue by animateFloatAsState(
        targetValue = if (started) targetNum.toFloat() else 0f,
        animationSpec = tween(600)
    )

    val displayText = if (started) {
        if (isInt) "%.0f%s".format(animValue.toDouble(), suffix)
        else "%.1f%s".format(animValue.toDouble(), suffix)
    } else {
        if (isInt) "0$suffix" else "0.0$suffix"
    }

    Card(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
