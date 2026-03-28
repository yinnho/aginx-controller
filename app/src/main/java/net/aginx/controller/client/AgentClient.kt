package net.aginx.controller.client

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.aginx.controller.data.model.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Aginx 客户端
 * TCP + JSON-RPC 2.0
 */
class AgentClient {

    companion object {
        private const val TAG = "AgentClient"
        private const val DEFAULT_PORT = 86
    }

    private val gson = Gson()

    // 网络组件
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var receiveJob: Job? = null

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 请求 ID
    private var requestId = 0

    // 响应回调 - 传递原始 result 对象
    private val responseCallbacks = mutableMapOf<String, (Any?) -> Unit>()

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 消息回调（旧版，保持兼容）
    var onMessage: ((sessionId: String, content: String) -> Unit)? = null
    var onError: ((error: String) -> Unit)? = null

    // ACP 流式更新回调
    var onSessionUpdate: ((SessionUpdateNotification) -> Unit)? = null

    // ACP 权限请求回调
    var onPermissionRequest: ((RequestPermissionNotification) -> Unit)? = null

    /**
     * 连接到 aginx
     */
    suspend fun connect(host: String, port: Int = DEFAULT_PORT): Boolean {
        android.util.Log.d(TAG, "connect: host=$host, port=$port")
        _connectionState.value = ConnectionState.Connecting

        return withContext(Dispatchers.IO) {
            try {
                socket = Socket(host, port)
                socket!!.soTimeout = 0
                writer = PrintWriter(socket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                _connectionState.value = ConnectionState.Connected
                startReceiveLoop()
                android.util.Log.d(TAG, "connect: 成功")
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "connect: 失败 - ${e.message}", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "连接失败")
                false
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        receiveJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        writer = null
        reader = null
        responseCallbacks.clear()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Connected

    /**
     * 获取 Agent 列表
     */
    suspend fun listAgents(): List<AgentInfo>? {
        val response = call("listAgents", null)

        if (response?.error != null) {
            return null
        }

        val result = response?.result as? Map<*, *>
        val agentsList = result?.get("agents") as? List<*>
        return agentsList?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { AgentInfo.fromMap(it) }
        }
    }

    /**
     * 扫描发现 Agent
     */
    suspend fun discoverAgents(path: String? = null, maxDepth: Int = 5): DiscoverResult? {
        val params = mutableMapOf<String, Any?>("maxDepth" to maxDepth)
        if (path != null) {
            params["path"] = path
        }

        val response = call("discoverAgents", params)

        if (response?.error != null) {
            return null
        }

        val result = response?.result as? Map<*, *>
        val agentsList = result?.get("agents") as? List<*>
        val scanPath = result?.get("scanPath") as? String ?: ""

        val agents = agentsList?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { DiscoveredAgent.fromMap(it) }
        } ?: emptyList()

        return DiscoverResult(agents = agents, scanPath = scanPath)
    }

    /**
     * 注册发现的 Agent
     */
    suspend fun registerAgent(configPath: String): Boolean {
        val response = call("registerAgent", mapOf("configPath" to configPath))

        if (response?.error != null) {
            return false
        }

        val result = response?.result as? Map<*, *>
        return result?.get("success") as? Boolean ?: false
    }

    /**
     * 发送消息（Direct 模式）
     */
    fun sendMessageDirect(agentId: String, message: String, workdir: String? = null, onResponse: (String?) -> Unit) {
        android.util.Log.d(TAG, "sendMessageDirect: agentId=$agentId, workdir=$workdir, isConnected=${isConnected()}")

        if (!isConnected()) {
            android.util.Log.e(TAG, "sendMessageDirect: not connected!")
            onResponse(null)
            return
        }

        val id = (++requestId).toString()
        val params = mutableMapOf(
            "agentId" to agentId,
            "message" to message
        )
        if (workdir != null) {
            params["workdir"] = workdir
        }

        val request = JsonRpcRequest(
            id = id,
            method = "sendMessage",
            params = params
        )

        responseCallbacks[id] = { result ->
            android.util.Log.d(TAG, "sendMessageDirect callback: result=$result")
            // 将 result 转换为字符串给回调
            val resultMap = result as? Map<*, *>
            val resultText = if (resultMap?.containsKey("error") == true) {
                // 错误响应
                "错误: ${resultMap["error"]}"
            } else {
                resultMap?.get("response") as? String ?: result?.toString()
            }
            onResponse(resultText)
        }

        val json = gson.toJson(request)
        android.util.Log.d(TAG, "sendMessageDirect: sending $json")
        writer?.println(json)
    }

    /**
     * 获取服务器信息
     */
    suspend fun getServerInfo(): Map<String, Any?>? {
        val response = call("getServerInfo", null)

        if (response?.error != null) {
            return null
        }

        return response?.result as? Map<String, Any?>
    }

    /**
     * 绑定设备（使用配对码）
     */
    suspend fun bindDevice(pairCode: String, deviceName: String): BindResult? {
        android.util.Log.d(TAG, "bindDevice: pairCode=$pairCode, deviceName=$deviceName")
        val response = call("bindDevice", mapOf(
            "pairCode" to pairCode,
            "deviceName" to deviceName
        ))

        android.util.Log.d(TAG, "bindDevice response: $response, result type: ${response?.result?.javaClass}")

        if (response?.error != null) {
            android.util.Log.e(TAG, "bindDevice error: ${response.error.message}")
            return BindResult(error = response.error.message)
        }

        val result = response?.result
        if (result == null) {
            android.util.Log.e(TAG, "bindDevice: result is null")
            return BindResult(error = "服务器无响应")
        }

        val resultMap = result as? Map<*, *>
        if (resultMap == null) {
            android.util.Log.e(TAG, "bindDevice: result is not a Map: $result")
            return BindResult(error = "响应格式错误")
        }

        android.util.Log.d(TAG, "bindDevice resultMap: $resultMap")

        // 更灵活地解析 success 字段
        val successValue = resultMap["success"]
        val success = when (successValue) {
            is Boolean -> successValue
            is String -> successValue.toBoolean()
            is Number -> successValue.toInt() != 0
            else -> {
                android.util.Log.e(TAG, "bindDevice: unknown success type: ${successValue?.javaClass}")
                false
            }
        }
        android.util.Log.d(TAG, "bindDevice success: $success")

        return if (success) {
            BindResult(
                success = true,
                deviceId = resultMap["deviceId"] as? String,
                token = resultMap["token"] as? String
            )
        } else {
            BindResult(error = "绑定失败")
        }
    }

    /**
     * 创建会话
     */
    suspend fun createSession(agentId: String, workdir: String? = null): SessionResult? {
        android.util.Log.d(TAG, "createSession: agentId=$agentId, workdir=$workdir, isConnected=${isConnected()}")

        if (!isConnected()) {
            android.util.Log.e(TAG, "createSession: 未连接")
            return SessionResult(error = "未连接到服务器")
        }

        val params = mutableMapOf<String, Any?>("agentId" to agentId)
        if (workdir != null) {
            params["cwd"] = workdir  // ACP 协议使用 cwd 参数
        }

        val response = call("newSession", params)
        android.util.Log.d(TAG, "createSession response: $response, result=${response?.result}, error=${response?.error}")

        if (response == null) {
            android.util.Log.e(TAG, "createSession: 响应为空")
            return SessionResult(error = "服务器无响应")
        }

        if (response.error != null) {
            android.util.Log.e(TAG, "createSession: 错误 ${response.error.message}")
            return SessionResult(error = response.error.message)
        }

        val resultMap = response.result as? Map<*, *>
        android.util.Log.d(TAG, "createSession: resultMap=$resultMap, type=${response.result?.javaClass}")

        val sessionId = resultMap?.get("sessionId") as? String
        android.util.Log.d(TAG, "createSession: sessionId=$sessionId")

        return if (sessionId != null) {
            SessionResult(success = true, sessionId = sessionId)
        } else {
            SessionResult(error = "创建会话失败: 无法获取 sessionId")
        }
    }

    /**
     * 发送消息（Session 模式）
     */
    fun sendMessageSession(sessionId: String, message: String, onResponse: (SendMessageResult?) -> Unit) {
        android.util.Log.d(TAG, "sendMessageSession: sessionId=$sessionId, isConnected=${isConnected()}")

        if (!isConnected()) {
            android.util.Log.e(TAG, "sendMessageSession: not connected!")
            onResponse(null)
            return
        }

        val id = (++requestId).toString()
        // ACP prompt 格式: {"sessionId": "...", "prompt": [{"type": "text", "text": "..."}]}
        val params = mapOf(
            "sessionId" to sessionId,
            "prompt" to listOf(mapOf("type" to "text", "text" to message))
        )

        val request = JsonRpcRequest(
            id = id,
            method = "prompt",
            params = params
        )

        responseCallbacks[id] = { result ->
            android.util.Log.d(TAG, "sendMessageSession callback: result=$result")
            val resultMap = result as? Map<*, *>

            if (resultMap?.containsKey("error") == true) {
                val errorMsg = resultMap["error"]?.toString() ?: ""
                // 检查是否是 Session not found 错误
                if (errorMsg.contains("Session not found", ignoreCase = true)) {
                    android.util.Log.w(TAG, "Session not found, need to recreate session")
                    onResponse(SendMessageResult.SessionNotFound(errorMsg))
                } else {
                    onResponse(SendMessageResult.Response("错误: $errorMsg"))
                }
            } else {
                // 解析响应
                val responseObj = resultMap?.get("response")
                val parsedResult = parseSendMessageResult(responseObj)
                onResponse(parsedResult)
            }
        }

        val json = gson.toJson(request)
        android.util.Log.d(TAG, "sendMessageSession: sending $json")
        writer?.println(json)
    }

    /**
     * 关闭会话
     */
    suspend fun closeSession(sessionId: String): Boolean {
        android.util.Log.d(TAG, "closeSession: sessionId=$sessionId")
        val response = call("closeSession", mapOf("sessionId" to sessionId))

        if (response?.error != null) {
            return false
        }

        val resultMap = response?.result as? Map<*, *>
        return resultMap?.get("success") as? Boolean ?: false
    }

    /**
     * 发送权限响应（Phase 3 - ACP）
     * @param sessionId 会话 ID
     * @param optionId 选择的选项 ID，null 表示取消
     */
    suspend fun sendPermissionResponse(sessionId: String, optionId: String?): PermissionResponseResult? {
        android.util.Log.d(TAG, "sendPermissionResponse: sessionId=$sessionId, optionId=$optionId")

        val outcome = if (optionId != null) {
            mapOf("outcome" to "selected", "optionId" to optionId)
        } else {
            mapOf("outcome" to "cancelled")
        }

        val response = call("permissionResponse", mapOf(
            "sessionId" to sessionId,
            "outcome" to outcome
        ))

        if (response?.error != null) {
            return PermissionResponseResult.Error(response.error.message)
        }

        val resultMap = response?.result as? Map<*, *> ?: return null

        val stopReason = resultMap["stopReason"] as? String ?: "unknown"
        val permissionRequest = (resultMap["permissionRequest"] as? Map<*, *>)?.let {
            parseRequestPermission(it)
        }

        return if (stopReason == "permission_required" && permissionRequest != null) {
            // 需要另一个权限
            PermissionResponseResult.PermissionNeeded(permissionRequest)
        } else {
            // 完成
            val responseText = resultMap["response"] as? String
            PermissionResponseResult.Completed(stopReason, responseText)
        }
    }

    /**
     * 权限响应结果
     */
    sealed class PermissionResponseResult {
        data class Completed(val stopReason: String, val response: String?) : PermissionResponseResult()
        data class PermissionNeeded(val request: RequestPermissionNotification) : PermissionResponseResult()
        data class Error(val message: String) : PermissionResponseResult()
    }

    /**
     * 获取 Agent 配置
     */
    suspend fun getAgentConfig(agentId: String): AgentConfig? {
        android.util.Log.d(TAG, "getAgentConfig: agentId=$agentId")
        val response = call("getAgentConfig", mapOf("agentId" to agentId))

        if (response?.error != null) {
            android.util.Log.e(TAG, "getAgentConfig error: ${response.error.message}")
            return null
        }

        val resultMap = response?.result as? Map<*, *>
        android.util.Log.d(TAG, "getAgentConfig result: $resultMap")

        return resultMap?.let { AgentConfig.fromMap(it) }
    }

    /**
     * 获取 LLM 配置
     */
    suspend fun getLLMConfig(): LLMConfigResult? {
        android.util.Log.d(TAG, "getLLMConfig")
        val response = call("getLLMConfig", null)

        if (response?.error != null) {
            android.util.Log.e(TAG, "getLLMConfig error: ${response.error.message}")
            return null
        }

        val resultMap = response?.result as? Map<*, *> ?: return null

        return LLMConfigResult(
            configured = resultMap["configured"] as? Boolean ?: false,
            provider = resultMap["provider"] as? String ?: "",
            apiKey = resultMap["apiKey"] as? String ?: "",
            endpoint = resultMap["endpoint"] as? String ?: "",
            format = resultMap["format"] as? String ?: "openai",
            model = resultMap["model"] as? String ?: "",
            modality = resultMap["modality"] as? String ?: "chat"
        )
    }

    /**
     * 设置 LLM 配置
     */
    suspend fun setLLMConfig(
        provider: String,
        apiKey: String,
        endpoint: String,
        format: String,
        model: String,
        modality: String = "chat"
    ): Boolean {
        android.util.Log.d(TAG, "setLLMConfig: provider=$provider")
        val response = call("setLLMConfig", mapOf(
            "provider" to provider,
            "apiKey" to apiKey,
            "endpoint" to endpoint,
            "format" to format,
            "model" to model,
            "modality" to modality
        ))

        if (response?.error != null) {
            android.util.Log.e(TAG, "setLLMConfig error: ${response.error.message}")
            return false
        }

        val resultMap = response?.result as? Map<*, *>
        return resultMap?.get("success") as? Boolean ?: false
    }

    /**
     * 响应权限请求
     */
    fun respondPermission(sessionId: String, choice: Int, onResponse: (SendMessageResult?) -> Unit) {
        android.util.Log.d(TAG, "respondPermission: sessionId=$sessionId, choice=$choice, isConnected=${isConnected()}")

        if (!isConnected()) {
            android.util.Log.e(TAG, "respondPermission: not connected!")
            onResponse(null)
            return
        }

        val id = (++requestId).toString()
        val params = mapOf(
            "sessionId" to sessionId,
            "choice" to choice
        )

        val request = JsonRpcRequest(
            id = id,
            method = "respondPermission",
            params = params
        )

        responseCallbacks[id] = { result ->
            android.util.Log.d(TAG, "respondPermission callback: result=$result")
            val resultMap = result as? Map<*, *>

            if (resultMap?.containsKey("error") == true) {
                onResponse(SendMessageResult.Response("错误: ${resultMap["error"]}"))
            } else {
                // 解析响应
                val responseObj = resultMap?.get("response")
                val parsedResult = parseSendMessageResult(responseObj)
                onResponse(parsedResult)
            }
        }

        val json = gson.toJson(request)
        android.util.Log.d(TAG, "respondPermission: sending $json")
        writer?.println(json)
    }

    /**
     * 解析发送消息结果
     */
    private fun parseSendMessageResult(result: Any?): SendMessageResult? {
        android.util.Log.d(TAG, "parseSendMessageResult: result=$result, type=${result?.javaClass}")

        when (result) {
            is String -> return SendMessageResult.Response(result)
            is Map<*, *> -> {
                val map = result

                // 检查是否是权限提示
                val permissionNeeded = (map["PermissionNeeded"] as? Map<*, *>)
                if (permissionNeeded != null) {
                    val prompt = PermissionPrompt.fromMap(permissionNeeded)
                    if (prompt != null) {
                        return SendMessageResult.PermissionNeeded(prompt)
                    }
                }

                // 检查是否是普通响应
                val responseStr = (map["Response"] as? String)
                if (responseStr != null) {
                    return SendMessageResult.Response(responseStr)
                }

                // 直接返回字符串内容
                val content = map["response"] as? String ?: map["content"] as? String
                if (content != null) {
                    return SendMessageResult.Response(content)
                }

                // 尝试将整个 map 转换为字符串
                return SendMessageResult.Response(map.toString())
            }
            else -> return null
        }
    }

    // ========== 内部方法 ==========

    private suspend fun call(method: String, params: Map<String, Any?>?): JsonRpcResponse? {
        if (!isConnected()) {
            android.util.Log.e(TAG, "call: 未连接")
            return null
        }

        val id = (++requestId).toString()
        val request = JsonRpcRequest(id = id, method = method, params = params)
        val json = gson.toJson(request)
        android.util.Log.d(TAG, "call: 发送 $json")

        val deferred = CompletableDeferred<JsonRpcResponse?>()
        responseCallbacks[id] = { result ->
            android.util.Log.d(TAG, "call: 收到响应 id=$id, result=$result, type=${result?.javaClass}")
            val response = if (result != null) {
                JsonRpcResponse(id = id, result = result, error = null)
            } else {
                JsonRpcResponse(id = id, result = null, error = null)
            }
            deferred.complete(response)
        }

        writer?.println(json)

        return try {
            withTimeout(30000) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            android.util.Log.e(TAG, "call: 超时")
            responseCallbacks.remove(id)
            null
        }
    }

    private fun startReceiveLoop() {
        receiveJob = scope.launch {
            try {
                var line: String?
                while (reader?.readLine().also { line = it } != null) {
                    line?.let { processResponse(it) }
                }
            } catch (e: Exception) {
                if (_connectionState.value == ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Error(e.message ?: "连接断开")
                }
            }
        }
    }

    private fun processResponse(line: String) {
        try {
            android.util.Log.d(TAG, "processResponse: raw=$line")
            val map = gson.fromJson(line, Map::class.java) as Map<*, *>
            val response = JsonRpcResponse.fromMap(map) ?: return

            android.util.Log.d(TAG, "processResponse: id=${response.id}, result=${response.result}, error=${response.error}")

            when {
                response.id != null -> {
                    val callback = responseCallbacks.remove(response.id)
                    if (callback != null) {
                        // 如果有错误，传递错误信息；否则传递 result
                        if (response.error != null) {
                            callback(mapOf("error" to response.error.message))
                        } else {
                            callback(response.result)
                        }
                    }
                }
                response.result != null -> {
                    handleNotification(response.result)
                }
                response.error != null -> {
                    onError?.invoke(response.error.message)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "processResponse error: ${e.message}", e)
        }
    }

    private fun handleNotification(result: Any?) {
        val map = result as? Map<*, *> ?: return

        // 检查是否是 ACP sessionUpdate 通知
        val method = map["method"] as? String
        val params = map["params"] as? Map<*, *>

        if (method == "sessionUpdate" && params != null) {
            // ACP sessionUpdate 通知
            val notification = parseSessionUpdate(params)
            if (notification != null) {
                android.util.Log.d(TAG, "Received sessionUpdate: ${notification.update.javaClass.simpleName}")
                onSessionUpdate?.invoke(notification)
                return
            }
        }

        if (method == "requestPermission" && params != null) {
            // ACP 权限请求通知
            val notification = parseRequestPermission(params)
            if (notification != null) {
                android.util.Log.d(TAG, "Received requestPermission: ${notification.requestId}")
                onPermissionRequest?.invoke(notification)
                return
            }
        }

        // 兼容旧版通知格式
        val sessionId = map["sessionId"] as? String
        val content = map["content"] as? String ?: map["response"] as? String

        if (sessionId != null && content != null) {
            onMessage?.invoke(sessionId, content)
        }
    }
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * JSON-RPC 请求
 */
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: Map<String, Any?>? = null
)

/**
 * JSON-RPC 响应
 */
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: Any?,
    val error: JsonRpcError?
) {
    companion object {
        fun fromMap(map: Map<*, *>): JsonRpcResponse? {
            return try {
                val idValue = map["id"]
                val idString = when (idValue) {
                    is String -> idValue
                    is Number -> idValue.toString()
                    null -> null
                    else -> idValue.toString()
                }
                JsonRpcResponse(
                    jsonrpc = map["jsonrpc"] as? String ?: "2.0",
                    id = idString,
                    result = map["result"],
                    error = (map["error"] as? Map<*, *>)?.let {
                        JsonRpcError(
                            code = (it["code"] as? Number)?.toInt() ?: -1,
                            message = it["message"] as? String ?: "Unknown error"
                        )
                    }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class JsonRpcError(
    val code: Int,
    val message: String
)

/**
 * 绑定结果
 */
data class BindResult(
    val success: Boolean = false,
    val deviceId: String? = null,
    val token: String? = null,
    val error: String? = null
)

/**
 * Agent 信息
 */
data class AgentInfo(
    val id: String,
    val name: String,
    val type: String,
    val capabilities: List<String>,
    val description: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val numericId: Long? = null,
    val requireWorkdir: Boolean = false,
    val workingDir: String? = null
) {
    companion object {
        fun fromMap(map: Map<*, *>): AgentInfo? {
            return try {
                AgentInfo(
                    id = map["id"] as? String ?: return null,
                    name = map["name"] as? String ?: map["id"] as String,
                    type = map["agent_type"] as? String ?: map["type"] as? String ?: "unknown",
                    capabilities = (map["capabilities"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    description = map["description"] as? String,
                    nickname = map["nickname"] as? String,
                    avatar = map["avatar"] as? String,
                    numericId = (map["numeric_id"] as? Number)?.toLong(),
                    requireWorkdir = (map["require_workdir"] as? Boolean) ?: false,
                    workingDir = map["working_dir"] as? String
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 会话结果
 */
data class SessionResult(
    val success: Boolean = false,
    val sessionId: String? = null,
    val error: String? = null
)

/**
 * Agent 完整配置信息
 */
data class AgentConfig(
    val id: String,
    val name: String,
    val agentType: String,
    val capabilities: List<String>,
    val command: String,
    val args: List<String>,
    val helpCommand: String,
    val workingDir: String?,
    val requireWorkdir: Boolean,
    val env: Map<String, String>
) {
    companion object {
        fun fromMap(map: Map<*, *>): AgentConfig? {
            return try {
                @Suppress("UNCHECKED_CAST")
                val envRaw = map["env"] as? Map<*, *>
                val stringEnv = mutableMapOf<String, String>()
                envRaw?.forEach { (key, value) ->
                    if (key is String && value is String) {
                        stringEnv[key] = value
                    }
                }

                AgentConfig(
                    id = map["id"] as? String ?: return null,
                    name = map["name"] as? String ?: return null,
                    agentType = map["agentType"] as? String ?: return null,
                    capabilities = (map["capabilities"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    command = map["command"] as? String ?: "",
                    args = (map["args"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    helpCommand = map["helpCommand"] as? String ?: "",
                    workingDir = map["workingDir"] as? String?,
                    requireWorkdir = (map["requireWorkdir"] as? Boolean) ?: false,
                    env = stringEnv
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 权限选项
 */
data class PermissionOption(
    val index: Int,
    val label: String,
    val isDefault: Boolean = false
) {
    companion object {
        fun fromMap(map: Map<*, *>): PermissionOption? {
            return try {
                PermissionOption(
                    index = (map["index"] as? Number)?.toInt() ?: return null,
                    label = map["label"] as? String ?: return null,
                    isDefault = (map["isDefault"] as? Boolean) ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 权限提示
 */
data class PermissionPrompt(
    val requestId: String,
    val description: String,
    val options: List<PermissionOption>
) {
    companion object {
        fun fromMap(map: Map<*, *>): PermissionPrompt? {
            return try {
                val optionsList = (map["options"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { PermissionOption.fromMap(it) }
                } ?: emptyList()

                PermissionPrompt(
                    requestId = map["request_id"] as? String ?: return null,
                    description = map["description"] as? String ?: "",
                    options = optionsList
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 发送消息结果
 */
sealed class SendMessageResult {
    data class Response(val content: String) : SendMessageResult()
    data class PermissionNeeded(val prompt: PermissionPrompt) : SendMessageResult()
    data class SessionNotFound(val message: String) : SendMessageResult()
}

/**
 * 发现的 Agent 信息
 */
data class DiscoveredAgent(
    val id: String,
    val name: String,
    val agentType: String,
    val version: String?,
    val description: String?,
    val configPath: String,
    val projectDir: String,
    val available: Boolean,
    val error: String?,
    val registered: Boolean
) {
    companion object {
        fun fromMap(map: Map<*, *>): DiscoveredAgent? {
            return try {
                DiscoveredAgent(
                    id = map["id"] as? String ?: return null,
                    name = map["name"] as? String ?: map["id"] as String,
                    agentType = map["agentType"] as? String ?: "unknown",
                    version = map["version"] as? String,
                    description = map["description"] as? String,
                    configPath = map["configPath"] as? String ?: "",
                    projectDir = map["projectDir"] as? String ?: "",
                    available = map["available"] as? Boolean ?: false,
                    error = map["error"] as? String,
                    registered = map["registered"] as? Boolean ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 扫描发现结果
 */
data class DiscoverResult(
    val agents: List<DiscoveredAgent>,
    val scanPath: String
)

data class LLMConfigResult(
    val configured: Boolean,
    val provider: String,
    val apiKey: String,
    val endpoint: String,
    val format: String,
    val model: String,
    val modality: String
)
