package net.aginx.controller.ui.agents

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.aginx.controller.client.ServerConversation
import net.aginx.controller.ui.MainViewModel
import net.aginx.controller.ui.common.DirectoryBrowser
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    aginxId: String,
    agentId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSelectConversation: (sessionId: String) -> Unit,
    onCreateNewConversation: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<ServerConversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val agentList by viewModel.agentList.collectAsState()
    val agentInfo = remember(agentList, agentId) {
        agentList.find { it.id == agentId }
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showWorkdirDialog by remember { mutableStateOf(false) }

    // 是否首次加载（避免 LaunchedEffect 和 ON_RESUME 重复请求）
    var initialLoadDone by remember { mutableStateOf(false) }

    // 从服务端加载对话列表
    LaunchedEffect(agentId) {
        isLoading = true
        error = null
        try {
            val result = viewModel.listConversations(agentId)
            if (result != null) {
                conversations = result
            } else {
                error = "无法获取对话列表"
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        isLoading = false
        initialLoadDone = true
    }

    // 从 ChatScreen 返回时自动刷新（跳过首次）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && initialLoadDone) {
                scope.launch {
                    try {
                        val result = viewModel.listConversations(agentId)
                        if (result != null) {
                            conversations = result
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 刷新函数
    fun refresh() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = viewModel.listConversations(agentId)
                if (result != null) {
                    conversations = result
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            agentInfo?.name ?: agentId,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${conversations.size} 个对话",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showWorkdirDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新对话")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showWorkdirDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新对话")
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { refresh() }) {
                            Text("重试")
                        }
                    }
                }
            }
            conversations.isEmpty() -> {
                EmptyConversationsState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onCreateNew = { showWorkdirDialog = true }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(conversations, key = { it.sessionId }) { conversation ->
                        SwipeableConversationItem(
                            conversation = conversation,
                            onClick = {
                                viewModel.selectServerConversation(conversation)
                                onSelectConversation(conversation.sessionId)
                            },
                            onDelete = {
                                val sessionId = conversation.sessionId
                                scope.launch {
                                    val success = viewModel.deleteServerConversation(sessionId, agentId)
                                    if (success) {
                                        conversations = conversations.filterNot { it.sessionId == sessionId }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 新对话：先选择工作目录
    if (showWorkdirDialog) {
        DirectoryBrowser(
            viewModel = viewModel,
            onSelectDirectory = { path ->
                scope.launch {
                    viewModel.saveAgentWorkdir(aginxId, agentId, path)
                    showWorkdirDialog = false
                    onCreateNewConversation()
                }
            },
            onDismiss = {
                showWorkdirDialog = false
            }
        )
    }
}

@Composable
private fun SwipeableConversationItem(
    conversation: ServerConversation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 72.dp.toPx() }
    val maxOffset = -deleteWidthPx

    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "swipe"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        // 红色删除背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        // 卡片内容
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = with(density) { animatedOffset.toDp() })
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < maxOffset / 2) maxOffset else 0f
                        },
                        onDragCancel = {
                            offsetX = if (offsetX < maxOffset / 2) maxOffset else 0f
                        }
                    ) { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(maxOffset, 0f)
                    }
                }
                .clickable {
                    if (offsetX != 0f) {
                        offsetX = 0f
                    } else {
                        onClick()
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.title ?: conversation.workdir ?: "对话",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    conversation.lastMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (conversation.updatedAt > 0) {
                        Text(
                            text = formatTime(conversation.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                offsetX = 0f
            },
            title = { Text("删除对话") },
            text = { Text("确定要删除这个对话吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        offsetX = 0f
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyConversationsState(
    modifier: Modifier = Modifier,
    onCreateNew: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有对话",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击下方按钮开始新对话",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCreateNew) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("开始对话")
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        diff < 604800_000 -> "${diff / 86400_000} 天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
