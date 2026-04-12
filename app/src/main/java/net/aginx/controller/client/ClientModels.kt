package net.aginx.controller.client

/**
 * 客户端数据模型
 *
 * 从旧 AgentClient.kt 中提取的活跃数据类。
 * AgentClient TCP 客户端已废弃，由 AginxiumAdapter（Rust FFI）替代。
 */

// ========== 连接状态 ==========

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

// ========== Agent ==========

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

// ========== 设备绑定 ==========

data class BindResult(
    val success: Boolean = false,
    val deviceId: String? = null,
    val token: String? = null,
    val error: String? = null
)

// ========== 会话 ==========

data class SessionResult(
    val success: Boolean = false,
    val sessionId: String? = null,
    val error: String? = null
)

sealed class SendMessageResult {
    data class Response(val content: String) : SendMessageResult()
    data class PermissionNeeded(val message: String) : SendMessageResult()
    data class SessionNotFound(val message: String) : SendMessageResult()
}

// ========== 对话 ==========

data class ServerConversation(
    val sessionId: String,
    val agentId: String,
    val workdir: String?,
    val title: String?,
    val lastMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ConversationMessage(
    val isFromUser: Boolean,
    val content: String
)

// ========== 权限 ==========

sealed class PermissionResponseResult {
    data class Completed(val stopReason: String, val response: String?) : PermissionResponseResult()
    data class PermissionNeeded(val request: net.aginx.controller.data.model.RequestPermissionNotification) : PermissionResponseResult()
    data class Error(val message: String) : PermissionResponseResult()
}
