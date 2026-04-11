package net.aginx.controller.client

import android.util.Log
import com.aginx.aginxium.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.aginx.controller.data.model.*

/**
 * Aginxium FFI 适配器
 *
 * 将 aginxium Rust 引擎（通过 UniFFI）包装成统一的客户端接口。
 */
class AginxiumAdapter(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "AginxiumAdapter"
    }

    private val gson = Gson()

    // FFI 客户端
    private var ffiClient: FfiAginxClient? = null

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ACP 流式更新回调
    var onSessionUpdate: ((SessionUpdateNotification) -> Unit)? = null

    // ACP 权限请求回调
    var onPermissionRequest: ((RequestPermissionNotification) -> Unit)? = null

    // 流式响应追踪（synchronized 保护，防止 FFI 线程和协程竞争）
    private val streamingLock = Any()
    private var streamingSessionId: String? = null
    private var streamingCallback: ((SendMessageResult?) -> Unit)? = null

    /**
     * 连接到 aginx
     * @param url 格式: agent://host:port 或 agent://id.relay.yinnho.cn
     * @param authToken 绑定设备时获得的 token，首次绑定传空字符串
     */
    suspend fun connect(url: String, authToken: String = ""): Boolean {
        _connectionState.value = ConnectionState.Connecting
        return try {
            val client = FfiAginxClient.connect(url, authToken)
            ffiClient = client

            // 设置事件监听器
            client.setEventListener(object : FfiEventListener {
                override fun onEvent(event: FfiEvent) {
                    handleEvent(event)
                }
            })

            _connectionState.value = ConnectionState.Connected
            true
        } catch (e: Exception) {
            Log.e(TAG, "connect: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "连接失败")
            false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        val client = ffiClient
        ffiClient = null
        _connectionState.value = ConnectionState.Disconnected
        synchronized(streamingLock) {
            streamingSessionId = null
            streamingCallback = null
        }
        // disconnect is async, fire and forget
        scope.launch {
            try { client?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = ffiClient != null && _connectionState.value is ConnectionState.Connected

    // ========== Agent 操作 ==========

    suspend fun listAgents(): List<AgentInfo>? {
        return try {
            val json = ffiClient?.listAgents() ?: return null
            parseAgentsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "listAgents error: ${e.message}")
            null
        }
    }

    // ========== 设备绑定 ==========

    suspend fun bindDevice(pairCode: String, deviceName: String): BindResult? {
        return try {
            val token = ffiClient?.bindDevice(pairCode, deviceName) ?: return BindResult(error = "绑定失败")
            BindResult(success = true, token = token)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Already bound", ignoreCase = true)) {
                BindResult(success = true)
            } else {
                Log.e(TAG, "bindDevice error: $msg")
                BindResult(error = msg)
            }
        }
    }

    // ========== 会话 ==========

    suspend fun loadSession(sessionId: String): SessionResult? {
        return try {
            ffiClient?.loadSession(sessionId)
            SessionResult(success = true, sessionId = sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "loadSession error: ${e.message}")
            SessionResult(error = e.message)
        }
    }

    suspend fun createSession(agentId: String, workdir: String? = null): SessionResult? {
        return try {
            val sessionId = ffiClient?.createSession(agentId, workdir)
            if (sessionId != null) {
                SessionResult(success = true, sessionId = sessionId)
            } else {
                SessionResult(error = "创建会话失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "createSession error: ${e.message}")
            SessionResult(error = e.message)
        }
    }

    /**
     * 发送消息（流式响应通过事件回调）
     */
    fun sendMessageSession(sessionId: String, message: String, onResponse: (SendMessageResult?) -> Unit) {
        val client = ffiClient
        if (client == null || !isConnected()) {
            onResponse(null)
            return
        }

        // 记录当前流式会话和回调
        synchronized(streamingLock) {
            streamingSessionId = sessionId
            streamingCallback = onResponse
        }

        // 发起 prompt（异步，响应通过事件回调）
        scope.launch(Dispatchers.IO) {
            try {
                client.prompt(sessionId, message)

                // prompt 返回 = 流式结束
                val callback = synchronized(streamingLock) {
                    if (streamingSessionId == sessionId) {
                        streamingSessionId = null
                        streamingCallback
                    } else null
                }
                // 在锁外调用回调，避免死锁
                callback?.invoke(SendMessageResult.Response(""))
            } catch (e: Exception) {
                Log.e(TAG, "sendMessageSession prompt error: ${e.message}")
                synchronized(streamingLock) {
                    streamingSessionId = null
                    streamingCallback = null
                }
                onResponse(SendMessageResult.Response("发送失败: ${e.message}"))
            }
        }
    }

    suspend fun closeSession(sessionId: String): Boolean {
        return try {
            ffiClient?.cancelSession(sessionId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "closeSession error: ${e.message}")
            false
        }
    }

    // ========== 权限 ==========

    suspend fun sendPermissionResponse(sessionId: String, optionId: String?): PermissionResponseResult? {
        return try {
            if (optionId != null) {
                ffiClient?.respondPermission(sessionId, optionId)
            }
            PermissionResponseResult.Completed("done", null)
        } catch (e: Exception) {
            Log.e(TAG, "sendPermissionResponse error: ${e.message}")
            PermissionResponseResult.Error(e.message ?: "权限响应失败")
        }
    }

    // ========== 对话 ==========

    suspend fun listConversations(agentId: String): List<ServerConversation>? {
        return try {
            val json = ffiClient?.listConversations(agentId) ?: return null
            parseConversationsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "listConversations error: ${e.message}")
            null
        }
    }

    suspend fun deleteConversation(sessionId: String, agentId: String): Boolean {
        return try {
            val result = ffiClient?.rawRequest(
                "deleteConversation",
                gson.toJson(mapOf("sessionId" to sessionId, "agentId" to agentId))
            ) ?: return false
            val map = gson.fromJson(result, Map::class.java) as Map<*, *>
            map["success"] as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "deleteConversation error: ${e.message}")
            false
        }
    }

    suspend fun getConversationMessages(sessionId: String, limit: Int = 10): List<ConversationMessage>? {
        return try {
            val result = ffiClient?.rawRequest(
                "getConversationMessages",
                gson.toJson(mapOf("sessionId" to sessionId, "limit" to limit))
            ) ?: return null
            val map = gson.fromJson(result, Map::class.java) as Map<*, *>
            val messagesList = map["messages"] as? List<*> ?: return null
            messagesList.mapNotNull { item ->
                (item as? Map<*, *>)?.let { m ->
                    val role = m["role"] as? String ?: return@let null
                    val content = m["content"] as? String ?: return@let null
                    ConversationMessage(isFromUser = role == "user", content = content)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConversationMessages error: ${e.message}")
            null
        }
    }

    // ========== 文件浏览 ==========

    suspend fun listDirectory(path: String? = null): DirectoryListing? {
        return try {
            val json = ffiClient?.listDirectory(path ?: "") ?: return null
            parseDirectoryJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "listDirectory error: ${e.message}")
            null
        }
    }

    suspend fun readFile(path: String): FileContent? {
        return try {
            val json = ffiClient?.readFile(path) ?: return null
            parseFileContentJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "readFile error: ${e.message}")
            null
        }
    }

    // ========== 事件处理 ==========

    private fun handleEvent(event: FfiEvent) {
        when (event) {
            is FfiEvent.ConnectionChanged -> {
                val state = when (event.state) {
                    FfiConnectionState.CONNECTED -> ConnectionState.Connected
                    FfiConnectionState.CONNECTING -> ConnectionState.Connecting
                    FfiConnectionState.DISCONNECTED -> ConnectionState.Disconnected
                    FfiConnectionState.RECONNECTING -> ConnectionState.Connecting
                }
                _connectionState.value = state
            }
            is FfiEvent.SessionEvent -> {
                handleSessionEvent(event.sessionId, event.eventJson)
            }
            is FfiEvent.PermissionRequest -> {
                handlePermissionEvent(event.requestJson)
            }
            is FfiEvent.AgentsUpdated -> {
                // Agent 列表更新，暂不处理
            }
        }
    }

    private fun handleSessionEvent(sessionId: String, eventJson: String) {
        try {
            val eventMap = gson.fromJson(eventJson, Map::class.java) as Map<*, *>
            val kind = eventMap["kind"] as? String ?: return

            when (kind) {
                "TextChunk" -> {
                    val text = eventMap["text"] as? String ?: return
                    onSessionUpdate?.invoke(
                        SessionUpdateNotification(
                            sessionId = sessionId,
                            update = SessionUpdate.AgentMessageChunk(
                                content = MessageContent.Text(text)
                            )
                        )
                    )
                }
                "ToolCallStart" -> {
                    val tc = eventMap["tool_call"] as? Map<*, *> ?: return
                    onSessionUpdate?.invoke(
                        SessionUpdateNotification(
                            sessionId = sessionId,
                            update = SessionUpdate.ToolCall(
                                toolCallId = tc["id"] as? String ?: "",
                                title = tc["name"] as? String ?: "",
                                status = ToolCallStatus.IN_PROGRESS,
                                kind = null
                            )
                        )
                    )
                }
                "ToolCallUpdate" -> {
                    val update = eventMap["update"] as? Map<*, *> ?: return
                    onSessionUpdate?.invoke(
                        SessionUpdateNotification(
                            sessionId = sessionId,
                            update = SessionUpdate.ToolCallUpdate(
                                toolCallId = update["id"] as? String ?: "",
                                status = (update["state"] as? String)?.let { ToolCallStatus.fromString(it) },
                                content = null
                            )
                        )
                    )
                }
                "Done" -> {
                    val callback = synchronized(streamingLock) {
                        if (streamingSessionId == sessionId) {
                            streamingSessionId = null
                            streamingCallback
                        } else null
                    }
                    callback?.invoke(SendMessageResult.Response(""))
                }
                "Error" -> {
                    val msg = eventMap["message"] as? String ?: "未知错误"
                    val callback = synchronized(streamingLock) {
                        if (streamingSessionId == sessionId) {
                            streamingSessionId = null
                            streamingCallback
                        } else null
                    }
                    callback?.invoke(SendMessageResult.Response("错误: $msg"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleSessionEvent error: ${e.message}", e)
        }
    }

    private fun handlePermissionEvent(requestJson: String) {
        try {
            val map = gson.fromJson(requestJson, Map::class.java) as Map<*, *>
            val notification = parseRequestPermission(map)
            if (notification != null) {
                onPermissionRequest?.invoke(notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "handlePermissionEvent error: ${e.message}", e)
        }
    }

    // ========== JSON 解析 ==========

    private fun parseAgentsJson(json: String): List<AgentInfo>? {
        return try {
            val list = gson.fromJson(json, List::class.java) as List<*>
            list.mapNotNull { item ->
                (item as? Map<*, *>)?.let { AgentInfo.fromMap(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAgentsJson error: ${e.message}")
            null
        }
    }

    private fun parseConversationsJson(json: String): List<ServerConversation>? {
        return try {
            val list = gson.fromJson(json, List::class.java) as List<*>
            list.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    try {
                        ServerConversation(
                            sessionId = map["sessionId"] as? String ?: return@let null,
                            agentId = map["agentId"] as? String ?: return@let null,
                            workdir = map["workdir"] as? String,
                            title = map["title"] as? String,
                            lastMessage = map["lastMessage"] as? String,
                            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseConversationsJson error: ${e.message}")
            null
        }
    }

    private fun parseDirectoryJson(json: String): DirectoryListing? {
        return try {
            val list = gson.fromJson(json, List::class.java) as List<*>
            val entries = list.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    FileEntry(
                        name = map["name"] as? String ?: return@let null,
                        type = map["type"] as? String ?: "file",
                        size = (map["size"] as? Number)?.toLong(),
                        modified = (map["modified"] as? Number)?.toLong(),
                        isHidden = map["isHidden"] as? Boolean ?: false
                    )
                }
            }
            DirectoryListing(path = "", entries = entries)
        } catch (e: Exception) {
            Log.e(TAG, "parseDirectoryJson error: ${e.message}")
            null
        }
    }

    private fun parseFileContentJson(json: String): FileContent? {
        return try {
            val map = gson.fromJson(json, Map::class.java) as Map<*, *>
            FileContent(
                name = map["name"] as? String ?: "unknown",
                size = (map["size"] as? Number)?.toLong() ?: 0L,
                content = map["content"] as? String ?: "",
                mimeType = map["mimeType"] as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseFileContentJson error: ${e.message}")
            null
        }
    }
}
