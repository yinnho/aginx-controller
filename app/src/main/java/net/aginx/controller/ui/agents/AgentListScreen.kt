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
import net.aginx.controller.client.AgentInfo
import net.aginx.controller.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    onBack: () -> Unit,
    onSelectAgent: (String) -> Unit,
    viewModel: MainViewModel,
    aginxId: String
) {
    val agentList by viewModel.agentList.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // 进入时连接并加载 agents
    LaunchedEffect(aginxId) {
        viewModel.connectAginxById(aginxId)
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(agentList, key = { it.id }) { agent ->
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
