package online.youcd.krond.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import online.youcd.krond.data.KrondMetrics

@Composable
fun SummaryCards(metrics: KrondMetrics) {
    val total = metrics.jobMetrics.values.sumOf { it.total }
    val failures = metrics.jobMetrics.values.sumOf { it.failures }
    val successCount = total - failures
    val successRate = if (total > 0) successCount.toDouble() / total * 100 else 0.0

    val uptimeHours = metrics.uptime / 3600
    val timeLabel = when {
        uptimeHours < 1 -> "不到1小时"
        uptimeHours < 24 -> "近${uptimeHours}小时"
        uptimeHours < 24 * 7 -> "近${uptimeHours / 24}天"
        else -> "近${uptimeHours / 24}天"
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCardSection(
                icon = "\u26A1",
                title = "总执行",
                subtitle = timeLabel,
                value = total.toFloat(),
                isInt = true,
                suffix = "次",
                accentColor = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            SummaryCardSection(
                icon = "\u2713",
                title = "成功率",
                subtitle = timeLabel,
                value = successRate.toFloat(),
                isInt = false,
                suffix = "%",
                accentColor = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
            SummaryCardSection(
                icon = "\u2717",
                title = "失败数",
                subtitle = timeLabel,
                value = failures.toFloat(),
                isInt = true,
                suffix = "次",
                accentColor = if (failures > 0) Color(0xFFF44336) else Color(0xFF616161),
                modifier = Modifier.weight(1f),
                neutralWhenZero = true
            )
        }
    }
}

@Composable
private fun SummaryCardSection(
    icon: String,
    title: String,
    subtitle: String,
    value: Float,
    isInt: Boolean,
    suffix: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    neutralWhenZero: Boolean = false,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        started = true
    }

    val animValue by animateFloatAsState(
        targetValue = if (started) value else 0f,
        animationSpec = tween(600)
    )

    val displayText = if (started) {
        if (isInt) "${"%.0f".format(animValue.toDouble())}$suffix"
        else "${"%.1f".format(animValue.toDouble())}$suffix"
    } else {
        "0$suffix"
    }

    val bgColor = if (neutralWhenZero && value == 0f)
        Color(0xFF424242).copy(alpha = 0.3f)
    else
        accentColor.copy(alpha = 0.12f)

    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(
            Modifier.fillMaxSize().padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(text = icon, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (neutralWhenZero && value == 0f) Color(0xFF9E9E9E)
                        else accentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = if (neutralWhenZero && value == 0f) Color(0xFFBDBDBD)
                    else accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp
                ),
                color = if (neutralWhenZero && value == 0f) Color(0xFF757575)
                    else accentColor.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}
