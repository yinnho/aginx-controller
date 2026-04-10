package net.aginx.controller.ui.common

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.aginx.controller.client.DirectoryListing
import net.aginx.controller.client.FileContent
import net.aginx.controller.client.FileEntry
import net.aginx.controller.ui.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(
    viewModel: MainViewModel,
    initialPath: String? = null,
    onDismiss: () -> Unit
) {
    val rootPath = initialPath  // 锁定根目录，不允许向上超出
    var currentPath by remember { mutableStateOf(initialPath) }
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadingFile by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadDirectory(path: String? = null) {
        // 限制在 rootPath 范围内
        val targetPath = if (rootPath != null && path != null && !path.startsWith(rootPath)) {
            rootPath
        } else {
            path
        }
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = viewModel.listDirectory(targetPath)
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

    fun downloadFile(fileName: String, filePath: String) {
        scope.launch {
            downloadingFile = fileName
            error = null
            try {
                val fileContent = viewModel.readFile(filePath)
                if (fileContent == null) {
                    error = "读取文件失败"
                    downloadingFile = null
                    return@launch
                }

                val saved = saveFileToDownloads(context, fileContent)
                if (saved != null) {
                    Toast.makeText(context, "已保存到 Downloads/$saved", Toast.LENGTH_SHORT).show()
                } else {
                    error = "保存文件失败"
                }
            } catch (e: Exception) {
                error = e.message ?: "下载失败"
            }
            downloadingFile = null
        }
    }

    LaunchedEffect(Unit) {
        loadDirectory(initialPath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp,
        title = { Text("文件浏览") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Breadcrumb - 仅显示工作目录下的路径
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    if (currentPath != null && rootPath != null) {
                        // 只显示 rootPath 之后的路径部分
                        val relativePath = currentPath!!.removePrefix(rootPath).removePrefix("/")
                        if (relativePath.isNotEmpty()) {
                            Text(
                                text = rootPath.substringAfterLast("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { loadDirectory(rootPath) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val parts = relativePath.split("/")
                            var accumulated = rootPath
                            parts.filter { it.isNotEmpty() }.forEach { part ->
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
                        } else {
                            Text(
                                text = rootPath.substringAfterLast("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (currentPath != null) {
                        val parts = currentPath!!.split("/")
                        var accumulated = ""
                        parts.filter { it.isNotEmpty() }.forEach { part ->
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
                            // Parent directory (不在根目录时才显示)
                            if (currentPath != "/" && currentPath != null && currentPath != rootPath) {
                                item {
                                    val parentPath = currentPath!!.substringBeforeLast("/")
                                    FileBrowserEntry(
                                        name = "..",
                                        isDirectory = true,
                                        onClick = { loadDirectory(if (parentPath.isEmpty()) "/" else parentPath) }
                                    )
                                }
                            }

                            // Directories
                            items(listing!!.entries.filter { it.type == "directory" && !it.isHidden }) { entry ->
                                FileBrowserEntry(
                                    name = entry.name,
                                    isDirectory = true,
                                    onClick = { loadDirectory("${currentPath}/${entry.name}") }
                                )
                            }

                            // Files (clickable to download)
                            items(listing!!.entries.filter { it.type == "file" && !it.isHidden }) { entry ->
                                val isDownloading = downloadingFile == entry.name
                                FileBrowserEntry(
                                    name = entry.name,
                                    isDirectory = false,
                                    size = entry.size,
                                    isDownloading = isDownloading,
                                    onClick = {
                                        if (!isDownloading && downloadingFile == null) {
                                            downloadFile(entry.name, "${currentPath}/${entry.name}")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun FileBrowserEntry(
    name: String,
    isDirectory: Boolean,
    size: Long? = null,
    isDownloading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when {
                isDirectory -> Icons.Default.Folder
                isDownloading -> Icons.Default.Downloading
                else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                isDirectory -> MaterialTheme.colorScheme.primary
                isDownloading -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
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

        if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        } else if (size != null) {
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
        } else if (!isDownloading) {
            Icon(
                Icons.Default.Download,
                contentDescription = "下载",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun saveFileToDownloads(context: Context, fileContent: FileContent): String? {
    val bytes = try {
        android.util.Base64.decode(fileContent.content, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        return null
    }

    val fileName = fileContent.name

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, fileContent.mimeType ?: "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(bytes)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)

            fileName
        } else {
            // Android 9 and below: Use external Downloads directory
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadsDir, fileName)
            targetFile.writeBytes(bytes)
            fileName
        }
    } catch (e: Exception) {
        android.util.Log.e("FileBrowser", "Failed to save file: ${e.message}", e)
        null
    }
}
