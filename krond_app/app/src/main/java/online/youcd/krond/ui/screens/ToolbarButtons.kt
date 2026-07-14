package online.youcd.krond.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AppMenuButton(
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

@Composable
fun KrondControlButton(
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
