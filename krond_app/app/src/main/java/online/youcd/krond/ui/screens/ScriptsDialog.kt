package online.youcd.krond.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.youcd.krond.data.ScriptDetail

data class FolderItem(
    val name: String,
    val isDirectory: Boolean,
    val fullPath: String
)

private fun getFolderItems(scripts: List<ScriptDetail>, currentPath: String): List<FolderItem> {
    val seenDirs = mutableSetOf<String>()
    val seenFiles = mutableSetOf<String>()
    val items = mutableListOf<FolderItem>()
    val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"

    for (s in scripts) {
        val relative = if (currentPath.isEmpty()) s.name
            else s.name.removePrefix(prefix)
        if (relative == s.name && currentPath.isNotEmpty()) continue

        if (s.isDirectory) {
            if (relative.isNotEmpty() && !relative.contains("/") && seenDirs.add(relative)) {
                items.add(FolderItem(relative, true, s.name))
            }
        } else {
            val parts = relative.split("/")
            val fileName = parts[0]
            if (parts.size == 1 && fileName.isNotEmpty() && seenFiles.add(fileName)) {
                items.add(FolderItem(fileName, false, s.name))
            } else if (parts.size > 1 && fileName.isNotEmpty() && seenDirs.add(fileName)) {
                val full = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
                items.add(FolderItem(fileName, true, full))
            }
        }
    }
    return items.sortedBy { if (it.isDirectory) "0" else "1" + it.name }
}

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
    val items = remember(scripts, currentFolder) { getFolderItems(scripts, currentFolder) }
    val folders = items.filter { it.isDirectory }
    val files = items.filter { !it.isDirectory }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("脚本管理", modifier = Modifier.weight(1f))
                IconButton(onClick = onUpload) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "上传脚本")
                }
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "根目录",
                        style = MaterialTheme.typography.labelMedium,
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
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val path = segments.take(i + 1).joinToString("/")
                            Text(
                                text = seg,
                                style = MaterialTheme.typography.labelMedium,
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

                if (items.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "此文件夹为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(folders) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .combinedClickable(onClick = { onNavigateFolder(item.fullPath) }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDelete(item.fullPath) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除文件夹",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        items(files) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .combinedClickable(onClick = { onView(item.fullPath) }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.name.endsWith(".sh"))
                                        Icons.Default.Terminal else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDelete(item.fullPath) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
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
