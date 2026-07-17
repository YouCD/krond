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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.youcd.krond.data.ScriptDetail

data class FolderItem(
    val name: String,
    val isDirectory: Boolean,
    val fullPath: String,
    val permissions: String = ""
)

fun getFolderItems(scripts: List<ScriptDetail>, currentPath: String): List<FolderItem> {
    val seenDirs = mutableSetOf<String>()
    val seenFiles = mutableSetOf<String>()
    val items = mutableListOf<FolderItem>()
    val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"

    for (s in scripts) {
        val relative = if (currentPath.isEmpty()) s.name else s.name.removePrefix(prefix)
        if (relative == s.name && currentPath.isNotEmpty()) continue

        if (s.isDirectory) {
            if (relative.isNotEmpty() && !relative.contains("/") && seenDirs.add(relative))
                items.add(FolderItem(relative, true, s.name, s.permissions))
        } else {
            val parts = relative.split("/")
            val fileName = parts[0]
            if (parts.size == 1 && fileName.isNotEmpty() && seenFiles.add(fileName))
                items.add(FolderItem(fileName, false, s.name, s.permissions))
            else if (parts.size > 1 && fileName.isNotEmpty() && seenDirs.add(fileName)) {
                val full = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
                items.add(FolderItem(fileName, true, full, ""))
            }
        }
    }
    return items.sortedBy { if (it.isDirectory) "0" else "1" + it.name }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderFileList(
    items: List<FolderItem>,
    modifier: Modifier = Modifier,
    maxHeight: androidx.compose.ui.unit.Dp = 380.dp,
    onFolderClick: (FolderItem) -> Unit,
    onFileClick: (FolderItem) -> Unit,
    folderActions: @Composable (FolderItem) -> Unit = {},
    fileActions: @Composable (FolderItem) -> Unit = {},
    sectionHeader: @Composable (String) -> Unit = { title ->
        Text(
            "  $title",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )
    }
) {
    val folders = items.filter { it.isDirectory }
    val files = items.filter { !it.isDirectory }

    LazyColumn(modifier = modifier.heightIn(max = maxHeight)) {
        if (folders.isNotEmpty()) {
            item { sectionHeader("文件夹") }
            items(folders) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .combinedClickable(onClick = { onFolderClick(item) })
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.permissions.isNotBlank()) {
                            Text(
                                text = item.permissions,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                    folderActions(item)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (files.isNotEmpty()) {
            item { sectionHeader("脚本文件") }
            items(files) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .combinedClickable(onClick = { onFileClick(item) })
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.permissions.isNotBlank()) {
                            Text(
                                text = item.permissions,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                    fileActions(item)
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "此文件夹为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
    }
}
