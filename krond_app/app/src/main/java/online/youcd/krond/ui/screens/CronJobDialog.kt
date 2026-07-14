package online.youcd.krond.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.youcd.krond.data.CronExpressionTranslator
import online.youcd.krond.data.ScriptDetail

private data class CronPreset(val label: String, val expression: String)

private val cronPresets = listOf(
    CronPreset("每5分", "*/5 * * * *"),
    CronPreset("每小时", "0 * * * *"),
    CronPreset("每天0点", "0 0 * * *"),
    CronPreset("每天8点", "0 8 * * *"),
    CronPreset("每周一0点", "0 0 * * 1"),
    CronPreset("每月1号", "0 0 1 * *"),
)

@OptIn(ExperimentalLayoutApi::class)
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

    val isCronValid = schedule.isNotBlank() && CronExpressionTranslator.isValid(schedule)
    val cronPreview = if (schedule.isNotBlank()) {
        if (isCronValid) {
            val translation = CronExpressionTranslator.translate(schedule)
            val next = CronExpressionTranslator.computeNextRun(schedule)
            if (next != null) "⏰ 下次执行：$next"
            else "📋 $translation"
        } else {
            null
        }
    } else null
    val cronTranslation = if (isCronValid) CronExpressionTranslator.translate(schedule) else null

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(scrollState)) {
                Text(
                    text = "触发条件",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it; error = null },
                    label = { Text("Cron 表达式 *") },
                    placeholder = { Text("*/5 * * * *") },
                    supportingText = { Text("分 时 日 月 周") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = schedule.isNotBlank() && !isCronValid
                )

                if (schedule.isNotBlank() && !isCronValid) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "无效的 Cron 表达式，请检查格式",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (cronPreview != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = cronPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    cronPresets.forEach { preset ->
                        val selected = schedule == preset.expression
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { schedule = preset.expression; error = null }
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "执行动作",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it; error = null },
                    label = { Text("命令 *") },
                    placeholder = { Text("/system/bin/echo hello") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null && command.isBlank(),
                    trailingIcon = {
                        Box {
                            IconButton(onClick = { scriptMenuExpanded = true }) {
                                Icon(Icons.Default.Description, contentDescription = "选择脚本")
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
                                    Text(
                                        text = "选择脚本",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
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
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "任务名称",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称（可选）") },
                    placeholder = { Text("清理临时文件") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
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
                    when {
                        schedule.isBlank() || command.isBlank() -> {
                            error = "请填写 Cron 表达式和命令"
                        }
                        !isCronValid -> {
                            error = "Cron 表达式格式错误"
                        }
                        else -> {
                            onConfirm(schedule.trim(), command.trim(), name.trim())
                        }
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
