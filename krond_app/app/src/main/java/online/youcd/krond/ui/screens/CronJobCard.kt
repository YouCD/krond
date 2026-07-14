package online.youcd.krond.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.youcd.krond.data.CronJob
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private fun formatTime(rfc3339: String): String {
    return try {
        val zdt = ZonedDateTime.parse(rfc3339)
        zdt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } catch (_: Exception) {
        rfc3339
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CronJobCard(
    job: CronJob,
    onToggle: () -> Unit,
    onRun: () -> Unit,
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
                    if (job.lastRun.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        val statusColor = if (job.lastExitCode != null && job.lastExitCode != 0)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        val label = buildString {
                            append("上次: ${formatTime(job.lastRun)}")
                            if (job.lastDuration.isNotBlank()) append(" (${job.lastDuration})")
                            if (job.lastExitCode != null) append(" 退出码=${job.lastExitCode}")
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
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
                                text = { Text("立即执行") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = { menuExpanded = false; onRun() }
                            )
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

@Composable
fun SectionHeader(title: String, count: Int, color: Color) {
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
