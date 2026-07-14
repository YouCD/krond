package online.youcd.krond.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.youcd.krond.data.CronJob

private val EnabledGreen = Color(0xFF4CAF50)
private val DisabledOrange = Color(0xFFFF9800)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CronJobList(
    jobs: List<CronJob>,
    onToggle: (CronJob) -> Unit,
    onRun: (CronJob) -> Unit,
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
            stickyHeader { SectionHeader("已开启", enabledJobs.size, EnabledGreen) }
            items(enabledJobs, key = { "enabled_${it.id}" }) { job ->
                CronJobCard(
                    job = job,
                    onToggle = { onToggle(job) },
                    onRun = { onRun(job) },
                    onEdit = { onEdit(job) },
                    onDelete = { onDelete(job) }
                )
            }
        }
        if (disabledJobs.isNotEmpty()) {
            stickyHeader { SectionHeader("未开启", disabledJobs.size, DisabledOrange) }
            items(disabledJobs, key = { "disabled_${it.id}" }) { job ->
                CronJobCard(
                    job = job,
                    onToggle = { onToggle(job) },
                    onRun = { onRun(job) },
                    onEdit = { onEdit(job) },
                    onDelete = { onDelete(job) }
                )
            }
        }
    }
}
