package online.youcd.krond.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val RunningGreen = Color(0xFF4CAF50)
private val StoppedRed = Color(0xFFF44336)
private val DangerRed = Color(0xFFFF6B6B)

@Composable
fun AppMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onManageScripts: () -> Unit
) {
    Box {
        IconButton(onClick = { onMenuExpandedChange(true) }) {
            Icon(Icons.Default.SwapVert, contentDescription = "导入/导出")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("导入任务") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = onImport
            )
            DropdownMenuItem(
                text = { Text("导出任务") },
                leadingIcon = {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = onExport
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            DropdownMenuItem(
                text = { Text("脚本管理") },
                leadingIcon = {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = onManageScripts
            )
        }
    }
}

@Composable
fun KrondControlButton(
    isRunning: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit
) {
    Box {
        IconButton(onClick = {
            if (!isRunning) {
                onStart()
            } else {
                onMenuExpandedChange(true)
            }
        }) {
            Icon(
                if (isRunning) Icons.Default.Settings else Icons.Default.PlayArrow,
                contentDescription = if (isRunning) "krond 控制" else "启动 krond",
                tint = if (isRunning) RunningGreen else StoppedRed
            )
        }

        if (isRunning && menuExpanded) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(RunningGreen, CircleShape)
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        text = "当前状态：运行中",
                        style = MaterialTheme.typography.labelSmall,
                        color = RunningGreen,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenuItem(
                    text = { Text("重启 krond") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onMenuExpandedChange(false)
                        onRestart()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                DropdownMenuItem(
                    text = {
                        Text(
                            "停止 krond",
                            color = DangerRed,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            tint = DangerRed
                        )
                    },
                    onClick = {
                        onMenuExpandedChange(false)
                        onStop()
                    }
                )
            }
        }
    }
}
