package net.aginx.controller.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.aginx.controller.client.AginxiumAdapter
import net.aginx.controller.client.AgentInfo
import net.aginx.controller.client.ConnectionState
import net.aginx.controller.client.SendMessageResult
import net.aginx.controller.client.ServerConversation
import net.aginx.controller.data.model.Aginx
import net.aginx.controller.data.model.MessageContent
import net.aginx.controller.data.model.RequestPermissionNotification
import net.aginx.controller.data.model.SessionUpdate
import net.aginx.controller.data.model.SessionUpdateNotification
import net.aginx.controller.db.AppDatabase
import net.aginx.controller.db.entities.AgentEntity
import net.aginx.controller.db.entities.AginxEntity

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val db = AppDatabase.getInstance(application)
    private val aginxDao = db.aginxDao()
    private val agentDao = db.agentDao()

    // Aginx 列表
    private val _aginxList = MutableStateFlow<List<Aginx>>(emptyList())
    val aginxList: StateFlow<List<Aginx>> = _aginxList.asStateFlow()

    // 当前选中的 Aginx
    private val _selectedAginx = MutableStateFlow<Aginx?>(null)
    val selectedAginx: StateFlow<Aginx?> = _selectedAginx.asStateFlow()

    // Agent 列表
    private val _agentList = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agentList: StateFlow<List<AgentInfo>> = _agentList.asStateFlow()

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 当前客户端（aginxium Rust 引擎）
    private var currentClient: AginxiumAdapter? = null

    // 流式状态
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // 权限请求
    private val _pendingPermission = MutableStateFlow<RequestPermissionNotification?>(null)
    val pendingPermission: StateFlow<RequestPermissionNotification?> = _pendingPermission.asStateFlow()

    // 从服务端列表选中的对话（用于 ChatScreen 显示）
    private val _selectedServerConversation = MutableStateFlow<ServerConversation?>(null)
    val selectedServerConversation: StateFlow<ServerConversation?> = _selectedServerConversation.asStateFlow()

    fun selectServerConversation(conversation: ServerConversation?) {
        _selectedServerConversation.value = conversation
    }

    // 重连协程（确保只有一个）
    private var reconnectJob: kotlinx.coroutines.Job? = null

    init {
        loadAginxList()
    }

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        currentClient?.disconnect()
        currentClient = null
    }

    private fun wireClientCallbacks(client: AginxiumAdapter) {
        setupClientCallbacks(client)
        // 取消旧的重连协程，确保只有一个
        reconnectJob?.cancel()
        reconnectJob = startAutoReconnect()
    }

    /**
     * 自动重连（返回 Job 以便跟踪和取消）
     */
    private fun startAutoReconnect(): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            var retryDelay = 2000L
            val maxDelay = 30_000L
            while (true) {
                val client = currentClient ?: break
                client.connectionState.first { it is ConnectionState.Error || it is ConnectionState.Disconnected }
                if (_selectedAginx.value == null) break

                Log.i(TAG, "Connection lost, auto-reconnecting in ${retryDelay}ms...")
                _connectionState.value = ConnectionState.Connecting
                delay(retryDelay)

                try {
                    val aginx = _selectedAginx.value ?: break
                    client.disconnect()
                    val newClient = AginxiumAdapter(viewModelScope)
                    val connected = withContext(Dispatchers.IO) { newClient.connect(aginx.url) }
                    if (connected) {
                        setupClientCallbacks(newClient)
                        currentClient = newClient
                        retryDelay = 2000L
                        _connectionState.value = ConnectionState.Connected
                        val agents = withContext(Dispatchers.IO) { newClient.listAgents() }
                        _agentList.value = agents ?: emptyList()
                        Log.i(TAG, "Auto-reconnect succeeded")
                    } else {
                        retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
                        _connectionState.value = ConnectionState.Error("重连失败，${retryDelay / 1000}秒后重试")
                    }
                } catch (e: Exception) {
                    retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
                    Log.e(TAG, "Auto-reconnect failed: ${e.message}")
                    _connectionState.value = ConnectionState.Error("重连失败，${retryDelay / 1000}秒后重试")
                }
            }
        }
    }

    private fun setupClientCallbacks(client: AginxiumAdapter) {
        client.onSessionUpdate = { notification ->
            viewModelScope.launch {
                when (val update = notification.update) {
                    is SessionUpdate.AgentMessageChunk -> {
                        val text = (update.content as? MessageContent.Text)?.text ?: ""
                        _streamingText.value += text
                    }
                    is SessionUpdate.ToolCall -> {}
                    is SessionUpdate.ToolCallUpdate -> {}
                    is SessionUpdate.AvailableCommandsUpdate -> {}
                }
            }
        }
        client.onPermissionRequest = { notification ->
            viewModelScope.launch {
                _pendingPermission.value = notification
            }
        }
    }

    private fun loadAginxList() {
        viewModelScope.launch {
            aginxDao.getAll().collect { entities ->
                _aginxList.value = entities.map { it.toModel() }
            }
        }
    }

    fun addAginx(name: String, url: String, pairCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val client = AginxiumAdapter(viewModelScope)
                val connected = withContext(Dispatchers.IO) { client.connect(url) }
                if (!connected) {
                    onError("连接失败")
                    return@launch
                }

                val deviceName = android.os.Build.MODEL ?: "Android Device"
                val bindResult = withContext(Dispatchers.IO) { client.bindDevice(pairCode, deviceName) }

                if (bindResult == null || !bindResult.success) {
                    onError(bindResult?.error ?: "配对码无效或已过期")
                    client.disconnect()
                    return@launch
                }

                val aginxId = "aginx-${System.currentTimeMillis()}"
                val entity = AginxEntity(
                    id = aginxId,
                    name = name,
                    url = url,
                    token = bindResult.token ?: "",
                    lastConnected = System.currentTimeMillis(),
                    isOnline = true
                )

                aginxDao.insert(entity)

                wireClientCallbacks(client)
                currentClient = client

                val agents = withContext(Dispatchers.IO) { client.listAgents() }
                _agentList.value = agents ?: emptyList()

                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "addAginx error: ${e.message}")
                onError("发生错误: ${e.message}")
            }
        }
    }

    fun connectAginx(aginx: Aginx, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting

                val client = AginxiumAdapter(viewModelScope)
                val connected = withContext(Dispatchers.IO) { client.connect(aginx.url) }

                if (connected) {
                    currentClient?.disconnect()

                    wireClientCallbacks(client)
                    currentClient = client
                    _selectedAginx.value = aginx
                    _connectionState.value = ConnectionState.Connected

                    val agents = withContext(Dispatchers.IO) { client.listAgents() }
                    _agentList.value = agents ?: emptyList()

                    onSuccess()
                } else {
                    _connectionState.value = ConnectionState.Error("连接失败")
                    onError("连接失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectAginx error: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "连接错误")
                onError("发生错误: ${e.message}")
            }
        }
    }

    fun connectAginxById(aginxId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val aginx = _aginxList.value.find { it.id == aginxId }
        if (aginx != null) {
            connectAginx(aginx, onSuccess, onError)
        } else {
            onError("找不到 Aginx: $aginxId")
        }
    }

    fun disconnect() {
        currentClient?.disconnect()
        currentClient = null
        _selectedAginx.value = null
        _agentList.value = emptyList()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun deleteAginx(aginx: Aginx) {
        viewModelScope.launch {
            aginxDao.deleteById(aginx.id)
        }
    }

    // ========== 文件浏览 ==========

    suspend fun listDirectory(path: String? = null): net.aginx.controller.client.DirectoryListing? {
        val client = currentClient ?: return null
        if (!client.isConnected()) return null
        return withContext(Dispatchers.IO) { client.listDirectory(path) }
    }

    suspend fun readFile(path: String): net.aginx.controller.client.FileContent? {
        val client = currentClient ?: return null
        if (!client.isConnected()) return null
        return withContext(Dispatchers.IO) { client.readFile(path) }
    }

    // ========== 会话和消息 ==========

    suspend fun createSession(agentId: String, workdir: String? = null): String? {
        val client = currentClient ?: return null
        if (!client.isConnected()) return null
        return withContext(Dispatchers.IO) {
            client.createSession(agentId, workdir)?.sessionId
        }
    }

    suspend fun loadSession(sessionId: String): String? {
        val client = currentClient ?: return null
        if (!client.isConnected()) return null
        return withContext(Dispatchers.IO) {
            client.loadSession(sessionId)?.sessionId
        }
    }

    fun sendMessageWithSession(sessionId: String, message: String, onResponse: (SendMessageResult?) -> Unit) {
        val client = currentClient
        if (client == null || !client.isConnected()) {
            onResponse(null)
            return
        }

        _streamingText.value = ""
        _isStreaming.value = true

        client.sendMessageSession(sessionId, message) { result ->
            viewModelScope.launch {
                val finalText = _streamingText.value
                _isStreaming.value = false
                _streamingText.value = ""
                if (finalText.isNotBlank() && result is SendMessageResult.Response) {
                    onResponse(SendMessageResult.Response(finalText))
                } else {
                    onResponse(result)
                }
            }
        }
    }

    fun clearPendingPermission() {
        _pendingPermission.value = null
    }

    suspend fun sendPermissionResponse(sessionId: String, optionId: String?) {
        val client = currentClient ?: return
        if (!client.isConnected()) return
        withContext(Dispatchers.IO) {
            client.sendPermissionResponse(sessionId, optionId)
        }
    }

    suspend fun closeSession(sessionId: String): Boolean {
        val client = currentClient ?: return false
        if (!client.isConnected()) return false
        return withContext(Dispatchers.IO) { client.closeSession(sessionId) ?: false }
    }

    // ========== 对话管理 ==========

    suspend fun listConversations(agentId: String): List<net.aginx.controller.client.ServerConversation>? {
        val client = currentClient ?: return null
        if (!client.isConnected()) return null
        return withContext(Dispatchers.IO) { client.listConversations(agentId) }
    }

    suspend fun deleteServerConversation(sessionId: String, agentId: String): Boolean {
        val client = currentClient ?: return false
        if (!client.isConnected()) return false
        return withContext(Dispatchers.IO) { client.deleteConversation(sessionId, agentId) ?: false }
    }

    suspend fun getConversationMessages(sessionId: String, limit: Int = 10): List<net.aginx.controller.client.ConversationMessage>? {
        val client = currentClient ?: return null
        if (!client.isConnected()) return null
        return withContext(Dispatchers.IO) { client.getConversationMessages(sessionId, limit) }
    }

    // ========== Agent 工作目录 ==========

    suspend fun getAgentWorkdir(aginxId: String, agentId: String): String? {
        return agentDao.getById(agentId, aginxId)?.workdir
    }

    suspend fun saveAgentWorkdir(aginxId: String, agentId: String, workdir: String?) {
        withContext(Dispatchers.IO) {
            val existing = agentDao.getById(agentId, aginxId)
            if (existing == null) {
                val agentInfo = _agentList.value.find { it.id == agentId }
                if (agentInfo != null) {
                    agentDao.insert(AgentEntity(
                        id = agentInfo.id,
                        numericId = agentInfo.numericId ?: 0L,
                        localId = agentInfo.id,
                        aginxId = aginxId,
                        nickname = agentInfo.name,
                        avatar = agentInfo.avatar,
                        description = agentInfo.description,
                        personality = null,
                        capabilities = agentInfo.capabilities.joinToString(","),
                        workdir = workdir
                    ))
                } else {
                    agentDao.updateWorkdir(agentId, aginxId, workdir)
                }
            } else {
                agentDao.updateWorkdir(agentId, aginxId, workdir)
            }
        }
    }
}

fun AginxEntity.toModel() = Aginx(
    id = id,
    name = name,
    url = url,
    token = token,
    lastConnected = lastConnected,
    isOnline = isOnline,
    isFavorite = isFavorite
)
