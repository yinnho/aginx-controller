package net.aginx.controller.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import kotlinx.coroutines.launch
import net.aginx.controller.client.AgentInfo
import net.aginx.controller.client.PermissionPrompt
import net.aginx.controller.client.SendMessageResult
import net.aginx.controller.db.entities.MessageEntity
import net.aginx.controller.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    aginxId: String,
    agentId: String,
    conversationId: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNewConversation: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 对话信息
    var currentConversationId by remember { mutableStateOf(conversationId) }
    var serverSessionId by remember { mutableStateOf<String?>(null) }

    // 权限提示状态
    var pendingPermission by remember { mutableStateOf<PermissionPrompt?>(null) }

    // 从数据库加载消息
    val dbMessages by viewModel.getMessagesForConversation(currentConversationId ?: "").collectAsState(initial = emptyList())

    // 获取 agent 信息
    val agentList by viewModel.agentList.collectAsState()
    val agentInfo = remember(agentList, agentId) {
        agentList.find { it.id == agentId }
    }

    // 工作目录
    var workdir by remember { mutableStateOf("") }
    var workdirLoaded by remember { mutableStateOf(false) }
    var showWorkdirDialog by remember { mutableStateOf(false) }

    // 创建服务器会话
    suspend fun initServerSession() {
        if (serverSessionId == null) {
            serverSessionId = viewModel.createSession(agentId, workdir.takeIf { it.isNotBlank() })
            android.util.Log.d("ChatScreen", "Created server session: $serverSessionId")

            // 保存 sessionId 到对话
            serverSessionId?.let { sid ->
                currentConversationId?.let { cid ->
                    viewModel.updateConversationSessionId(aginxId, cid, sid)
                }
            }
        }
    }

    // 处理权限响应
    fun handlePermissionChoice(choice: Int) {
        if (pendingPermission == null || serverSessionId == null) return
        val sid = serverSessionId!!

        pendingPermission = null
        isLoading = true

        viewModel.respondPermission(sid, choice) { result ->
            scope.launch {
                isLoading = false
                when (result) {
                    is SendMessageResult.Response -> {
                        // 保存响应消息
                        currentConversationId?.let { cid ->
                            viewModel.saveMessage(
                                conversationId = cid,
                                content = result.content,
                                isFromUser = false,
                                senderName = agentInfo?.name ?: agentId,
                                aginxId = aginxId
                            )
                        }
                    }
                    is SendMessageResult.PermissionNeeded -> {
                        // 还有更多权限需要确认
                        pendingPermission = result.prompt
                    }
                    is SendMessageResult.SessionNotFound -> {
                        currentConversationId?.let { cid ->
                            viewModel.saveMessage(
                                conversationId = cid,
                                content = "会话已失效，请重新发送消息",
                                isFromUser = false,
                                senderName = agentInfo?.name ?: agentId,
                                aginxId = aginxId
                            )
                        }
                    }
                    null -> {
                        currentConversationId?.let { cid ->
                            viewModel.saveMessage(
                                conversationId = cid,
                                content = "权限响应失败",
                                isFromUser = false,
                                senderName = agentInfo?.name ?: agentId,
                                aginxId = aginxId
                            )
                        }
                    }
                }
            }
        }
    }

    // 处理发送消息结果
    fun handleSendMessageResult(result: SendMessageResult?, retryMessage: String? = null) {
        Log.d("ChatScreen", "handleSendMessageResult: result=$result, retryMessage=$retryMessage")
        when (result) {
            is SendMessageResult.Response -> {
                Log.d("ChatScreen", "handleSendMessageResult: Response received, content length=${result.content.length}")
                isLoading = false
                currentConversationId?.let { cid ->
                    Log.d("ChatScreen", "handleSendMessageResult: Saving message to conversation $cid")
                    scope.launch {
                        viewModel.saveMessage(
                            conversationId = cid,
                            content = result.content,
                            isFromUser = false,
                            senderName = agentInfo?.name ?: agentId,
                            aginxId = aginxId
                        )
                        Log.d("ChatScreen", "handleSendMessageResult: Message saved")
                    }
                }
            }
            is SendMessageResult.PermissionNeeded -> {
                // 需要权限确认，显示权限提示
                isLoading = false
                pendingPermission = result.prompt
            }
            is SendMessageResult.SessionNotFound -> {
                // 会话失效，需要重新创建
                Log.w("ChatScreen", "Session not found, recreating session...")
                serverSessionId = null  // 清除旧 sessionId
                scope.launch {
                    // 重新创建会话
                    initServerSession()
                    val newSid = serverSessionId
                    if (newSid != null && retryMessage != null) {
                        // 重试发送消息
                        viewModel.sendMessageWithSession(newSid, retryMessage) { retryResult ->
                            handleSendMessageResult(retryResult, null)  // 只重试一次，避免无限循环
                        }
                    } else {
                        isLoading = false
                        viewModel.saveMessage(
                            conversationId = currentConversationId!!,
                            content = "会话已失效，请重新发送消息",
                            isFromUser = false,
                            senderName = agentInfo?.name ?: agentId,
                            aginxId = aginxId
                        )
                    }
                }
            }
            null -> {
                Log.w("ChatScreen", "handleSendMessageResult: result is NULL")
                isLoading = false
                currentConversationId?.let { cid ->
                    scope.launch {
                        viewModel.saveMessage(
                            conversationId = cid,
                            content = "无响应",
                            isFromUser = false,
                            senderName = agentInfo?.name ?: agentId,
                            aginxId = aginxId
                        )
                    }
                }
            }
        }
    }

    // 初始化
    LaunchedEffect(currentConversationId) {
        if (currentConversationId != null) {
            // 加载已有对话
            val conversation = viewModel.getConversation(aginxId, currentConversationId!!)
            if (conversation != null) {
                workdir = conversation.workdir ?: ""
                serverSessionId = conversation.sessionId
                workdirLoaded = true
            }
        } else {
            // 新对话：从 agent 加载默认工作目录
            val savedWorkdir = viewModel.getAgentWorkdir(aginxId, agentId)
            workdir = savedWorkdir ?: agentInfo?.workingDir ?: ""
            workdirLoaded = true

            // 创建新对话记录
            val newConversation = viewModel.createConversation(aginxId, agentId, workdir.takeIf { it.isNotBlank() })
            currentConversationId = newConversation.id

            // 如果需要工作目录且没有配置，弹出对话框
            if (agentInfo?.requireWorkdir == true && workdir.isBlank()) {
                showWorkdirDialog = true
            }
        }
    }

    // 当工作目录设置后，创建服务器会话
    LaunchedEffect(workdir, workdirLoaded) {
        if (workdirLoaded && workdir.isNotBlank() && serverSessionId == null) {
            initServerSession()
        }
    }

    // 滚动到底部
    LaunchedEffect(dbMessages.size, pendingPermission) {
        if (dbMessages.isNotEmpty()) {
            listState.animateScrollToItem(dbMessages.size - 1)
        }
    }

    // 工作目录选择对话框
    if (showWorkdirDialog) {
        WorkdirDialog(
            currentWorkdir = workdir,
            onDismiss = { showWorkdirDialog = false },
            onConfirm = { newWorkdir ->
                workdir = newWorkdir
                showWorkdirDialog = false
                // 保存工作目录
                viewModel.saveAgentWorkdir(aginxId, agentId, newWorkdir.takeIf { it.isNotBlank() })
                // 更新对话的 workdir
                currentConversationId?.let { cid ->
                    scope.launch {
                        val conv = viewModel.getConversation(aginxId, cid)
                        if (conv != null) {
                            viewModel.updateConversationWorkdir(aginxId, cid, newWorkdir.takeIf { it.isNotBlank() })
                        }
                    }
                }
            }
        )
    }

    // 权限提示对话框
    pendingPermission?.let { permission ->
        PermissionDialog(
            prompt = permission,
            onChoice = { choice -> handlePermissionChoice(choice) },
            onDismiss = {
                // 用户取消，清除权限提示
                pendingPermission = null
                isLoading = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(agentInfo?.name ?: agentId, style = MaterialTheme.typography.titleMedium)
                        if (workdir.isNotBlank()) {
                            Text(
                                "目录: $workdir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        } else {
                            Text(
                                if (dbMessages.isEmpty()) "新对话" else "${dbMessages.size} 条消息",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新对话按钮
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Default.Add, contentDescription = "新对话")
                    }
                    // 工作目录按钮
                    if (agentInfo?.requireWorkdir == true) {
                        IconButton(onClick = { showWorkdirDialog = true }) {
                            Icon(Icons.Default.Folder, contentDescription = "选择目录")
                        }
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
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 显示数据库中的消息
                items(dbMessages) { message ->
                    MessageBubble(
                        content = message.content,
                        isFromUser = message.isFromUser,
                        senderName = if (message.isFromUser) null else message.senderName
                    )
                }

                // 加载指示器
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }

            // 输入区域
            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isLoading && currentConversationId != null) {
                        // 检查是否需要工作目录
                        if (agentInfo?.requireWorkdir == true && workdir.isBlank()) {
                            showWorkdirDialog = true
                            return@InputBar
                        }

                        val messageToSend = inputText
                        inputText = ""
                        isLoading = true

                        // 保存用户消息
                        scope.launch {
                            viewModel.saveMessage(
                                conversationId = currentConversationId!!,
                                content = messageToSend,
                                isFromUser = true,
                                senderName = null,
                                aginxId = aginxId
                            )
                        }

                        // 发送消息
                        val sid = serverSessionId
                        if (sid != null) {
                            viewModel.sendMessageWithSession(sid, messageToSend) { result ->
                                handleSendMessageResult(result, messageToSend)
                            }
                        } else {
                            // 先创建服务器会话
                            scope.launch {
                                initServerSession()
                                val newSid = serverSessionId
                                if (newSid != null) {
                                    viewModel.sendMessageWithSession(newSid, messageToSend) { result ->
                                        handleSendMessageResult(result, messageToSend)
                                    }
                                } else {
                                    isLoading = false
                                    viewModel.saveMessage(
                                        conversationId = currentConversationId!!,
                                        content = "无法创建会话",
                                        isFromUser = false,
                                        senderName = agentInfo?.name ?: agentId,
                                        aginxId = aginxId
                                    )
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading && currentConversationId != null && pendingPermission == null
            )
        }
    }
}

@Composable
private fun WorkdirDialog(
    currentWorkdir: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentWorkdir) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("工作目录") },
        text = {
            Column {
                Text(
                    "请输入 Claude 的工作目录路径：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("/Users/xxx/projects/myproject") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
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

@Composable
private fun MessageBubble(
    content: String,
    isFromUser: Boolean,
    senderName: String? = null
) {
    val alignment = if (isFromUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isFromUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isFromUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = alignment
        ) {
            // 发送者名称（非用户消息显示）
            if (!isFromUser && senderName != null) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isFromUser) 16.dp else 4.dp,
                    bottomEnd = if (isFromUser) 4.dp else 16.dp
                ),
                color = backgroundColor,
                modifier = Modifier.fillMaxWidth(if (isFromUser) 0.85f else 0.95f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (isFromUser) {
                        // 用户消息使用简单文本
                        Text(
                            text = content,
                            color = textColor,
                            fontSize = 16.sp
                        )
                    } else {
                        // Agent 消息使用 Markdown 渲染
                        ChatMarkdown(text = content, textColor = textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            FilledIconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送")
            }
        }
    }
}

/**
 * 权限提示对话框
 */
@Composable
private fun PermissionDialog(
    prompt: PermissionPrompt,
    onChoice: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, contentDescription = null) },
        title = { Text("权限请求") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 描述
                Text(
                    text = prompt.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 选项按钮
                prompt.options.forEach { option ->
                    if (option.isDefault) {
                        Button(
                            onClick = { onChoice(option.index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onChoice(option.index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.label)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
