package net.aginx.controller.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import kotlinx.coroutines.launch
import net.aginx.controller.client.ConversationMessage
import net.aginx.controller.client.SendMessageResult
import net.aginx.controller.data.model.RequestPermissionNotification
import net.aginx.controller.ui.MainViewModel
import net.aginx.controller.ui.common.DirectoryBrowser
import net.aginx.controller.ui.common.FileBrowser

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 对话信息 — conversationId 即 Claude session ID
    var serverSessionId by remember { mutableStateOf(conversationId) }

    // 流式状态
    val streamingText by viewModel.streamingText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()

    // 权限提示状态 (from ViewModel, set by onPermissionRequest callback)
    val pendingPermission by viewModel.pendingPermission.collectAsState()

    // 消息列表 — 从服务端获取，不缓存到 Room DB
    var serverMessages by remember { mutableStateOf<List<ConversationMessage>>(emptyList()) }

    // 获取 agent 信息
    val agentList by viewModel.agentList.collectAsState()
    val agentInfo = remember(agentList, agentId) {
        agentList.find { it.id == agentId }
    }

    // 工作目录
    var workdir by remember { mutableStateOf("") }
    var workdirLoaded by remember { mutableStateOf(false) }
    var showWorkdirDialog by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }

    // 服务端对话信息（从对话列表点进来时有值）
    val serverConversation by viewModel.selectedServerConversation.collectAsState()
    val isServerConversation = conversationId != null && serverConversation?.sessionId == conversationId

    // 从服务端拉取消息
    suspend fun refreshMessages() {
        val sid = serverSessionId ?: return
        val messages = viewModel.getConversationMessages(sid, 10)
        if (messages != null) {
            serverMessages = messages
            Log.d("ChatScreen", "Refreshed ${messages.size} messages from server")
        }
    }

    // 创建服务器会话（新对话）
    suspend fun initServerSession() {
        if (serverSessionId == null) {
            serverSessionId = viewModel.createSession(agentId, workdir.takeIf { it.isNotBlank() })
            Log.d("ChatScreen", "Created server session: $serverSessionId")
        }
    }

    // 处理权限响应 (using ACP sendPermissionResponse)
    fun handlePermissionChoice(optionId: String) {
        val sid = serverSessionId ?: return

        // Clear permission state
        viewModel.clearPendingPermission()

        scope.launch {
            viewModel.sendPermissionResponse(sid, optionId)
        }
    }

    // 处理发送消息结果 — 完成后刷新消息
    fun handleSendMessageResult(result: SendMessageResult?, retryMessage: String? = null) {
        Log.d("ChatScreen", "handleSendMessageResult: result=$result")
        when (result) {
            is SendMessageResult.Response -> {
                isLoading = false
                // 直接将响应添加到消息列表
                serverMessages = serverMessages + ConversationMessage(isFromUser = false, content = result.content)
            }
            is SendMessageResult.PermissionNeeded -> {
                isLoading = false
            }
            is SendMessageResult.SessionNotFound -> {
                Log.w("ChatScreen", "Session not found, recreating session...")
                serverSessionId = null
                scope.launch {
                    initServerSession()
                    val newSid = serverSessionId
                    if (newSid != null && retryMessage != null) {
                        viewModel.sendMessageWithSession(newSid, retryMessage) { retryResult ->
                            handleSendMessageResult(retryResult, null)
                        }
                    } else {
                        isLoading = false
                    }
                }
            }
            null -> {
                Log.w("ChatScreen", "handleSendMessageResult: result is NULL")
                isLoading = false
            }
        }
    }

    // 初始化
    LaunchedEffect(conversationId) {
        if (conversationId != null) {
            // 从对话列表进入 — conversationId 就是 Claude session ID
            serverSessionId = conversationId
            workdir = serverConversation?.workdir ?: ""
            workdirLoaded = true

            // 恢复会话 + 加载最近消息
            Log.d("ChatScreen", "Loading server conversation: $conversationId")
            val loadedSid = viewModel.loadSession(conversationId)
            if (loadedSid != null) {
                serverSessionId = loadedSid
            }
            refreshMessages()
        } else {
            // 新对话
            val savedWorkdir = viewModel.getAgentWorkdir(aginxId, agentId)
            workdir = savedWorkdir ?: agentInfo?.workingDir ?: ""
            workdirLoaded = true

            if (agentInfo?.requireWorkdir == true && workdir.isBlank()) {
                showWorkdirDialog = true
            }
        }
    }

    // 新对话：当工作目录就绪后创建服务端会话
    LaunchedEffect(workdir, workdirLoaded) {
        if (workdirLoaded && workdir.isNotBlank() && serverSessionId == null && conversationId == null) {
            initServerSession()
        }
    }

    // 滚动到底部
    LaunchedEffect(serverMessages.size, streamingText, pendingPermission) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    // 工作目录选择对话框
    if (showWorkdirDialog) {
        DirectoryBrowser(
            viewModel = viewModel,
            onSelectDirectory = { newWorkdir ->
                workdir = newWorkdir
                showWorkdirDialog = false
                scope.launch {
                    viewModel.saveAgentWorkdir(aginxId, agentId, newWorkdir.takeIf { it.isNotBlank() })
                }
            },
            onDismiss = { showWorkdirDialog = false }
        )
    }

    // 文件浏览对话框
    if (showFileBrowser) {
        FileBrowser(
            viewModel = viewModel,
            initialPath = workdir.takeIf { it.isNotBlank() },
            onDismiss = { showFileBrowser = false }
        )
    }

    // 权限提示对话框
    pendingPermission?.let { permission ->
        PermissionDialog(
            notification = permission,
            onChoice = { optionId -> handlePermissionChoice(optionId) },
            onDismiss = {
                // User cancelled permission
                viewModel.clearPendingPermission()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(agentInfo?.name ?: agentId, style = MaterialTheme.typography.titleMedium)
                        if (isServerConversation) {
                            Text(
                                serverConversation?.title ?: serverConversation?.workdir ?: "历史对话",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        } else if (workdir.isNotBlank()) {
                            Text(
                                "目录: $workdir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        } else {
                            Text(
                                if (serverMessages.isEmpty()) "新对话" else "${serverMessages.size} 条消息",
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
                    // 文件浏览按钮
                    IconButton(onClick = { showFileBrowser = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "文件浏览")
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
                // 显示服务端消息
                items(serverMessages) { message ->
                    if (isToolMessage(message.content)) {
                        ToolCallCard(content = message.content)
                    } else {
                        MessageBubble(
                            content = message.content,
                            isFromUser = message.isFromUser,
                            senderName = if (message.isFromUser) null else (agentInfo?.name ?: agentId)
                        )
                    }
                }

                // Streaming text bubble (shown while agent is producing text)
                if (isLoading && isStreaming && streamingText.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = agentInfo?.name ?: agentId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 16.dp
                                    ),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(0.95f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        ChatMarkdown(
                                            text = streamingText,
                                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Blinking cursor
                                        Text(
                                            text = "▌",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Loading indicator (shown when waiting for response or agent is doing tool calls)
                if (isLoading && streamingText.isBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = agentInfo?.name ?: agentId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 16.dp
                                    ),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(0.95f)
                                ) {
                                    TypingIndicator()
                                }
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
                    Log.d("ChatScreen", "onSend: inputText.len=${inputText.length}, isLoading=$isLoading, serverSessionId=$serverSessionId")
                    if (inputText.isNotBlank() && !isLoading) {
                        // 检查是否需要工作目录
                        if (agentInfo?.requireWorkdir == true && workdir.isBlank()) {
                            showWorkdirDialog = true
                            return@InputBar
                        }

                        val messageToSend = inputText
                        inputText = ""
                        isLoading = true
                        keyboardController?.hide()

                        // 立即显示用户消息
                        serverMessages = serverMessages + ConversationMessage(isFromUser = true, content = messageToSend)

                        val sid = serverSessionId
                        if (sid != null) {
                            viewModel.sendMessageWithSession(sid, messageToSend) { result ->
                                viewModel.viewModelScope.launch {
                                    handleSendMessageResult(result, messageToSend)
                                }
                            }
                        } else {
                            // 先创建服务器会话
                            scope.launch {
                                initServerSession()
                                val newSid = serverSessionId
                                if (newSid != null) {
                                    viewModel.sendMessageWithSession(newSid, messageToSend) { result ->
                                        viewModel.viewModelScope.launch {
                                            handleSendMessageResult(result, messageToSend)
                                        }
                                    }
                                } else {
                                    isLoading = false
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading && pendingPermission == null,
                focusRequester = inputFocusRequester
            )
        }
    }
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
    enabled: Boolean,
    focusRequester: FocusRequester? = null
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
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
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
 * 权限提示对话框 (ACP-based)
 */
@Composable
private fun PermissionDialog(
    notification: RequestPermissionNotification,
    onChoice: (String) -> Unit,
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
                // 工具调用信息
                notification.toolCall?.let { tc ->
                    tc.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 描述
                notification.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 选项按钮
                notification.options.forEachIndexed { index, option ->
                    if (index == 0) {
                        Button(
                            onClick = { onChoice(option.optionId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onChoice(option.optionId) },
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

/**
 * 打字中动画指示器 "..."
 */
@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 400 },
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 400; delayMillis = 150 },
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 400; delayMillis = 300 },
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha1))
        Text("●", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha2))
        Text("●", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha3))
    }
}

/**
 * 判断是否为工具调用消息（通过内容格式检测）
 */
private fun isToolMessage(content: String): Boolean {
    return content.startsWith("> **") || content.startsWith("🔧 **")
}

/**
 * 解析工具消息内容，提取工具名和详情
 */
private data class ToolContent(
    val name: String,
    val args: String,
    val detail: String
)

private fun parseToolContent(content: String): ToolContent {
    // 格式: > **ToolName(args)**\n\ndetail...
    val headerRegex = Regex("""> \*\*(.+?)\*\*\s*\n\n(.*)""", RegexOption.DOT_MATCHES_ALL)
    val match = headerRegex.find(content)
    if (match != null) {
        val header = match.groupValues[1]
        val detail = match.groupValues[2].trim()
        // header 格式: "ToolName(args)" 或 "ToolName"
        val parenIdx = header.indexOf('(')
        return if (parenIdx > 0) {
            ToolContent(
                name = header.substring(0, parenIdx),
                args = header.substring(parenIdx + 1, header.length - 1),
                detail = detail
            )
        } else {
            ToolContent(name = header, args = "", detail = detail)
        }
    }
    // 旧格式: 🔧 **ToolName** `args`
    val oldRegex = Regex("""🔧 \*\*(\w+)\*\* `(.+?)`""")
    val oldMatch = oldRegex.find(content)
    if (oldMatch != null) {
        return ToolContent(oldMatch.groupValues[1], oldMatch.groupValues[2], "")
    }
    return ToolContent("", "", content)
}

/**
 * Claude 风格的工具调用卡片 - 紧凑可折叠
 */
@Composable
private fun ToolCallCard(content: String) {
    var expanded by remember { mutableStateOf(false) }
    val parsed = remember(content) { parseToolContent(content) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Column {
            // Header row - always visible, clickable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (parsed.detail.isNotBlank()) expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔧", fontSize = 13.sp)
                    Text(
                        text = parsed.name,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (parsed.args.isNotBlank()) {
                        Text(
                            text = parsed.args,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                if (parsed.detail.isNotBlank()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable detail section
            if (expanded && parsed.detail.isNotBlank()) {
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                SelectionContainer {
                    Text(
                        text = parsed.detail,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 30,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
