package net.aginx.controller.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.aginx.controller.client.DirectoryListing
import net.aginx.controller.client.FileEntry
import net.aginx.controller.ui.MainViewModel

/**
 * 服务端文件浏览器
 * @param viewModel MainViewModel
 * @param on_select_directory 选中目录后的回调，参数为选中目录的绝对路径
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowser(
    viewModel: MainViewModel,
    onSelectDirectory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 加载目录
    fun loadDirectory(path: String? = null) {
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = viewModel.listDirectory(path)
                if (result != null) {
                    listing = result
                    currentPath = result.path
                } else {
                    error = "无法读取目录"
                }
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            }
            isLoading = false
        }
    }

    // 初始加载 home 目录
    LaunchedEffect(Unit) {
        loadDirectory(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp,
        title = { Text("选择目录") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 面包屑导航
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { loadDirectory(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(18.dp))
                    }

                    if (currentPath != null) {
                        // 显示路径 breadcrumb
                        val parts = currentPath!!.split("/")
                        var accumulated = ""
                        parts.filter { it.isNotEmpty() }.forEachIndexed { index, part ->
                            accumulated += "/$part"
                            val path = accumulated
                            Text(
                                text = " / ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = part,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { loadDirectory(path) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Divider()

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(error!!, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { loadDirectory(currentPath) }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                    listing != null -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            // 返回上级目录
                            if (currentPath != "/" && currentPath != null) {
                                item {
                                    val parentPath = currentPath!!.substringBeforeLast("/")
                                    DirectoryEntry(
                                        name = "..",
                                        isDirectory = true,
                                        onClick = { loadDirectory(if (parentPath.isEmpty()) "/" else parentPath) }
                                    )
                                }
                            }

                            items(listing!!.entries.filter { it.type == "directory" && !it.isHidden }) { entry ->
                                DirectoryEntry(
                                    name = entry.name,
                                    isDirectory = true,
                                    onClick = { loadDirectory("${currentPath}/${entry.name}") }
                                )
                            }

                            // 显示文件（较淡，不可点击）
                            items(listing!!.entries.filter { it.type == "file" && !it.isHidden }) { entry ->
                                DirectoryEntry(
                                    name = entry.name,
                                    isDirectory = false,
                                    size = entry.size,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentPath != null) {
                TextButton(onClick = { onSelectDirectory(currentPath!!) }) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("选择此目录")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DirectoryEntry(
    name: String,
    isDirectory: Boolean,
    size: Long? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDirectory, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (size != null) {
            Text(
                text = formatFileSize(size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
