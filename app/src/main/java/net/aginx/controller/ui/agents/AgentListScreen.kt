package net.aginx.controller.ui.agents

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.aginx.controller.client.AgentInfo
import net.aginx.controller.client.DiscoveredAgent
import net.aginx.controller.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    onBack: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onLLMConfig: () -> Unit = {},
    viewModel: MainViewModel,
    aginxId: String
) {
    val agentList by viewModel.agentList.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val scope = rememberCoroutineScope()

    // 扫描对话框状态
    var showScanDialog by remember { mutableStateOf(false) }
    var scanPath by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var discoveredAgents by remember { mutableStateOf<List<DiscoveredAgent>>(emptyList()) }

    // 进入时连接并加载 agents
    LaunchedEffect(aginxId) {
        viewModel.connectAginxById(aginxId)
    }

    // 扫描对话框
    if (showScanDialog) {
        ScanAgentsDialog(
            scanPath = scanPath,
            isScanning = isScanning,
            discoveredAgents = discoveredAgents,
            onScanPathChange = { scanPath = it },
            onScan = {
                scope.launch {
                    isScanning = true
                    val result = viewModel.discoverAgents(scanPath.ifEmpty { null })
                    isScanning = false
                    discoveredAgents = result?.agents ?: emptyList()
                }
            },
            onRegister = { agent ->
                scope.launch {
                    val success = viewModel.registerAgent(agent.configPath)
                    if (success) {
                        // 刷新列表
                        discoveredAgents = discoveredAgents.map {
                            if (it.configPath == agent.configPath) it.copy(registered = true) else it
                        }
                    }
                }
            },
            onDismiss = {
                showScanDialog = false
                discoveredAgents = emptyList()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents (${agentList.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onLLMConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "LLM 配置")
                    }
                    IconButton(onClick = { showScanDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "扫描发现")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showScanDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "扫描发现 Agent")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 连接状态
            when (connectionState) {
                is net.aginx.controller.client.ConnectionState.Connecting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is net.aginx.controller.client.ConnectionState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = (connectionState as net.aginx.controller.client.ConnectionState.Error).message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }

            if (agentList.isEmpty()) {
                EmptyAgentsState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onScanClick = { showScanDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(agentList) { agent ->
                        AgentItem(
                            agent = agent,
                            onClick = { onSelectAgent(agent.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanAgentsDialog(
    scanPath: String,
    isScanning: Boolean,
    discoveredAgents: List<DiscoveredAgent>,
    onScanPathChange: (String) -> Unit,
    onScan: () -> Unit,
    onRegister: (DiscoveredAgent) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描发现 Agent") },
        text = {
            Column {
                OutlinedTextField(
                    value = scanPath,
                    onValueChange = onScanPathChange,
                    label = { Text("扫描路径（留空使用默认）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在扫描...", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                } else if (discoveredAgents.isNotEmpty()) {
                    Text("发现 ${discoveredAgents.size} 个 Agent:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(discoveredAgents) { agent ->
                            DiscoveredAgentItem(
                                agent = agent,
                                onRegister = { onRegister(agent) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onScan, enabled = !isScanning) {
                Text("扫描")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun DiscoveredAgentItem(
    agent: DiscoveredAgent,
    onRegister: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = agent.agentType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (agent.error != null) {
                    Text(
                        text = agent.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            when {
                agent.registered -> {
                    Text(
                        text = "已注册",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                agent.available -> {
                    Button(onClick = onRegister) {
                        Text("注册")
                    }
                }
                else -> {
                    Text(
                        text = "不可用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentItem(
    agent: AgentInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Text(
                text = agent.avatar ?: "🤖",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.nickname ?: agent.name,
                    style = MaterialTheme.typography.titleMedium
                )
                agent.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (agent.capabilities.isNotEmpty()) {
                    Text(
                        text = agent.capabilities.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyAgentsState(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "没有可用的 Agent",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onScanClick) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("扫描发现")
        }
    }
}
