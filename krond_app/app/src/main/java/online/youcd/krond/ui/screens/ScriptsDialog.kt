package online.youcd.krond.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import online.youcd.krond.data.ScriptDetail

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScriptsDialog(
    scripts: List<ScriptDetail>,
    currentFolder: String,
    onView: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onNavigateFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<String?>(null) }
    val items = remember(scripts, currentFolder) { getFolderItems(scripts, currentFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("脚本管理", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUpload, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "上传脚本", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "根目录",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (currentFolder.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentFolder.isEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.combinedClickable(
                            onClick = { onNavigateFolder("") }
                        )
                    )
                    if (currentFolder.isNotEmpty()) {
                        val segments = currentFolder.split("/")
                        for ((i, seg) in segments.withIndex()) {
                            Text(
                                text = " > ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val path = segments.take(i + 1).joinToString("/")
                            Text(
                                text = seg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (i == segments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (i == segments.lastIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onNavigateFolder(path) }
                                )
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${items.size} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FolderFileList(
                    items = items,
                    onFolderClick = { onNavigateFolder(it.fullPath) },
                    onFileClick = { onView(it.fullPath) },
                    folderActions = { item ->
                        Box {
                            IconButton(onClick = { menuTarget = item.fullPath }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = menuTarget == item.fullPath,
                                onDismissRequest = { menuTarget = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("删除文件夹", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuTarget = null; onDelete(item.fullPath) }
                                )
                            }
                        }
                    },
                    fileActions = { item ->
                        Box {
                            IconButton(onClick = { menuTarget = item.fullPath }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = menuTarget == item.fullPath,
                                onDismissRequest = { menuTarget = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuTarget = null; onDelete(item.fullPath) }
                                )
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {}
    )

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            onCreateFolder(folderName)
                            showNewFolderDialog = false
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun ScriptContentDialog(
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("脚本内容") },
        text = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
