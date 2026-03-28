package net.aginx.controller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.aginx.controller.client.AgentClient
import net.aginx.controller.client.AgentInfo
import net.aginx.controller.client.ConnectionState
import net.aginx.controller.client.DiscoveredAgent
import net.aginx.controller.client.DiscoverResult
import net.aginx.controller.client.SendMessageResult
import net.aginx.controller.data.model.Aginx
import net.aginx.controller.db.AppDatabase
import net.aginx.controller.db.entities.AginxEntity
import net.aginx.controller.db.entities.ConversationEntity
import net.aginx.controller.db.entities.MessageEntity
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val aginxDao = db.aginxDao()
    private val agentDao = db.agentDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

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

    // 当前客户端
    private var currentClient: AgentClient? = null

    init {
        loadAginxList()
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
                val client = AgentClient()
                val (host, port) = parseUrl(url) ?: run {
                    withContext(Dispatchers.Main) { onError("无效的 URL 格式") }
                    return@launch
                }

                val connected = withContext(Dispatchers.IO) { client.connect(host, port) }
                if (!connected) {
                    withContext(Dispatchers.Main) { onError("连接失败") }
                    return@launch
                }

                val deviceName = android.os.Build.MODEL ?: "Android Device"
                val bindResult = withContext(Dispatchers.IO) { client.bindDevice(pairCode, deviceName) }

                if (bindResult == null || !bindResult.success) {
                    withContext(Dispatchers.Main) { onError(bindResult?.error ?: "配对码无效或已过期") }
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
                currentClient = client

                val agents = withContext(Dispatchers.IO) { client.listAgents() }
                _agentList.value = agents ?: emptyList()

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("发生错误: ${e.message}") }
            }
        }
    }

    fun connectAginx(aginx: Aginx, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val (host, port) = parseUrl(aginx.url) ?: run {
                    withContext(Dispatchers.Main) { onError("无效的 URL 格式") }
                    return@launch
                }

                _connectionState.value = ConnectionState.Connecting

                val client = AgentClient()
                val connected = withContext(Dispatchers.IO) { client.connect(host, port) }

                if (connected) {
                    currentClient?.disconnect()
                    currentClient = client
                    _selectedAginx.value = aginx
                    _connectionState.value = ConnectionState.Connected

                    val agents = withContext(Dispatchers.IO) { client.listAgents() }
                    _agentList.value = agents ?: emptyList()

                    withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    _connectionState.value = ConnectionState.Error("连接失败")
                    withContext(Dispatchers.Main) { onError("连接失败") }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "连接错误")
                withContext(Dispatchers.Main) { onError("发生错误: ${e.message}") }
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

    // ========== Agent 发现和注册 ==========

    suspend fun discoverAgents(path: String? = null, maxDepth: Int = 5): DiscoverResult? {
        if (currentClient == null || currentClient?.isConnected() != true) return null
        return withContext(Dispatchers.IO) { currentClient?.discoverAgents(path, maxDepth) }
    }

    suspend fun registerAgent(configPath: String): Boolean {
        if (currentClient == null || currentClient?.isConnected() != true) return false
        return withContext(Dispatchers.IO) {
            val success = currentClient?.registerAgent(configPath) ?: false
            if (success) {
                val agents = currentClient?.listAgents()
                _agentList.value = agents ?: emptyList()
            }
            success
        }
    }

    // ========== 会话和消息 ==========

    suspend fun createSession(agentId: String, workdir: String? = null): String? {
        if (currentClient == null || currentClient?.isConnected() != true) return null
        return withContext(Dispatchers.IO) {
            currentClient?.createSession(agentId, workdir)?.sessionId
        }
    }

    fun sendMessageWithSession(sessionId: String, message: String, onResponse: (SendMessageResult?) -> Unit) {
        if (currentClient == null || currentClient?.isConnected() != true) {
            onResponse(null)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                currentClient?.sendMessageSession(sessionId, message, onResponse)
            }
        }
    }

    fun respondPermission(sessionId: String, choice: Int, onResponse: (SendMessageResult?) -> Unit) {
        if (currentClient == null || currentClient?.isConnected() != true) {
            onResponse(null)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                currentClient?.respondPermission(sessionId, choice, onResponse)
            }
        }
    }

    suspend fun closeSession(sessionId: String): Boolean {
        if (currentClient == null || currentClient?.isConnected() != true) return false
        return withContext(Dispatchers.IO) { currentClient?.closeSession(sessionId) ?: false }
    }

    // ========== 对话管理 ==========

    fun getConversationsForAgent(aginxId: String, agentId: String): Flow<List<ConversationEntity>> {
        return conversationDao.getByAgent(aginxId, agentId)
    }

    suspend fun getConversationCount(aginxId: String, agentId: String): Int {
        return conversationDao.countByAgent(aginxId, agentId)
    }

    suspend fun createConversation(aginxId: String, agentId: String, workdir: String?): ConversationEntity {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = id,
            aginxId = aginxId,
            agentId = agentId,
            workdir = workdir,
            title = null,
            sessionId = null,
            createdAt = now,
            updatedAt = now,
            lastMessage = null,
            lastMessageTime = null
        )
        conversationDao.insert(conversation)
        return conversation
    }

    suspend fun getConversation(aginxId: String, conversationId: String): ConversationEntity? {
        return conversationDao.getById(conversationId, aginxId)
    }

    fun updateConversationSessionId(aginxId: String, conversationId: String, sessionId: String) {
        viewModelScope.launch { conversationDao.updateSessionId(conversationId, aginxId, sessionId) }
    }

    fun updateConversationLastMessage(aginxId: String, conversationId: String, message: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            conversationDao.updateLastMessage(conversationId, aginxId, message, now)
        }
    }

    fun updateConversationWorkdir(aginxId: String, conversationId: String, workdir: String?) {
        viewModelScope.launch { conversationDao.updateWorkdir(conversationId, aginxId, workdir) }
    }

    suspend fun deleteConversation(aginxId: String, conversationId: String) {
        conversationDao.deleteById(conversationId, aginxId)
        messageDao.deleteByConversation(conversationId)
    }

    // ========== 消息管理 ==========

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getByConversation(conversationId)
    }

    suspend fun saveMessage(
        conversationId: String,
        content: String,
        isFromUser: Boolean,
        senderName: String?,
        aginxId: String
    ) {
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            id = "${conversationId}-${now}-${UUID.randomUUID()}",
            conversationId = conversationId,
            senderId = if (isFromUser) "user" else "agent",
            senderName = senderName,
            senderAvatar = null,
            content = content,
            timestamp = now,
            isFromUser = isFromUser
        )
        messageDao.insert(message)
        updateConversationLastMessage(aginxId, conversationId, content)
    }

    // ========== Agent 工作目录 ==========

    suspend fun getAgentWorkdir(aginxId: String, agentId: String): String? {
        return agentDao.getById(agentId, aginxId)?.workdir
    }

    fun saveAgentWorkdir(aginxId: String, agentId: String, workdir: String?) {
        viewModelScope.launch {
            agentDao.updateWorkdir(agentId, aginxId, workdir)
        }
    }

    // ========== 工具方法 ==========

    private fun parseUrl(url: String): Pair<String, Int>? {
        val stripped = url.removePrefix("agent://")
        val parts = stripped.split(":")
        val host = parts.getOrNull(0) ?: return null
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 86
        return Pair(host, port)
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
