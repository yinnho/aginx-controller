package net.aginx.controller.ui.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.aginx.controller.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAginxScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: MainViewModel
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("agent://") }
    var pairCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加 Aginx") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "输入 Aginx 服务器的信息来绑定",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("显示名称") },
                placeholder = { Text("如：本地服务器") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("服务器地址") },
                placeholder = { Text("agent://192.168.1.100 或 agent://xxx.relay.yinnho.cn") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = pairCode,
                onValueChange = { pairCode = it },
                label = { Text("配对码") },
                placeholder = { Text("在服务器上运行 aginx pair 获取") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            error?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isBlank() || url.isBlank() || pairCode.isBlank()) {
                        error = "请填写所有字段"
                        return@Button
                    }

                    isLoading = true
                    error = null

                    viewModel.addAginx(
                        name = name,
                        url = url,
                        pairCode = pairCode,
                        onSuccess = {
                            isLoading = false
                            onSuccess()
                        },
                        onError = { errorMessage ->
                            isLoading = false
                            error = errorMessage
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("绑定")
                }
            }

            // 帮助信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "如何获取配对码？",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "1. 在服务器上运行 aginx pair\n" +
                                "2. 复制显示的配对码\n" +
                                "3. 在此输入配对码完成绑定",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
