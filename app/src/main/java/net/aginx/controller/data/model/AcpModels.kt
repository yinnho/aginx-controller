package net.aginx.controller.data.model

/**
 * ACP 协议相关模型
 * 用于处理来自 aginx 的 sessionUpdate 通知
 */

/**
 * 会话更新通知
 */
data class SessionUpdateNotification(
    val sessionId: String,
    val update: SessionUpdate
)

/**
 * 会话更新类型
 */
sealed class SessionUpdate {
    /**
     * Agent 消息片段
     */
    data class AgentMessageChunk(
        val content: MessageContent
    ) : SessionUpdate()

    /**
     * 工具调用开始
     */
    data class ToolCall(
        val toolCallId: String,
        val title: String,
        val status: ToolCallStatus,
        val kind: ToolKind? = null
    ) : SessionUpdate()

    /**
     * 工具调用更新
     */
    data class ToolCallUpdate(
        val toolCallId: String,
        val status: ToolCallStatus? = null,
        val content: List<ToolCallContent>? = null
    ) : SessionUpdate()

    /**
     * 可用命令更新
     */
    data class AvailableCommandsUpdate(
        val commands: List<CommandInfo>
    ) : SessionUpdate()
}

/**
 * 消息内容
 */
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()

    companion object {
        fun fromMap(map: Map<*, *>): MessageContent? {
            val type = map["type"] as? String ?: return null
            return when (type) {
                "text" -> Text(map["text"] as? String ?: return null)
                else -> null
            }
        }
    }
}

/**
 * 工具调用状态
 */
enum class ToolCallStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    companion object {
        fun fromString(value: String): ToolCallStatus {
            return when (value.lowercase()) {
                "inprogress", "in_progress" -> IN_PROGRESS
                "completed" -> COMPLETED
                "failed" -> FAILED
                else -> IN_PROGRESS
            }
        }
    }
}

/**
 * 工具类型
 */
enum class ToolKind {
    READ,
    EDIT,
    DELETE,
    MOVE,
    SEARCH,
    EXECUTE,
    FETCH,
    OTHER;

    companion object {
        fun fromString(value: String): ToolKind {
            return when (value.lowercase()) {
                "read" -> READ
                "edit" -> EDIT
                "delete" -> DELETE
                "move" -> MOVE
                "search" -> SEARCH
                "execute" -> EXECUTE
                "fetch" -> FETCH
                else -> OTHER
            }
        }
    }
}

/**
 * 工具调用内容
 */
sealed class ToolCallContent {
    data class Content(val content: MessageContent) : ToolCallContent()
    data class Location(val path: String, val line: Int? = null) : ToolCallContent()

    companion object {
        fun fromMap(map: Map<*, *>): ToolCallContent? {
            val type = map["type"] as? String ?: return null
            return when (type) {
                "content" -> {
                    val contentMap = map["content"] as? Map<*, *> ?: return null
                    Content(MessageContent.fromMap(contentMap) ?: return null)
                }
                "location" -> Location(
                    path = map["path"] as? String ?: return null,
                    line = (map["line"] as? Number)?.toInt()
                )
                else -> null
            }
        }
    }
}

/**
 * 命令信息
 */
data class CommandInfo(
    val name: String,
    val description: String? = null
)

/**
 * 权限请求通知
 */
data class RequestPermissionNotification(
    val requestId: String,
    val description: String? = null,
    val toolCall: ToolCallInfo? = null,
    val options: List<PermissionOptionInfo>
)

/**
 * 工具调用信息（权限请求中）
 */
data class ToolCallInfo(
    val toolCallId: String,
    val title: String? = null
)

/**
 * 权限选项信息
 */
data class PermissionOptionInfo(
    val optionId: String,
    val label: String,
    val kind: String? = null
)

// ========== 解析辅助函数 ==========

/**
 * 解析 SessionUpdate 通知
 */
fun parseSessionUpdate(map: Map<*, *>): SessionUpdateNotification? {
    val sessionId = map["sessionId"] as? String ?: return null
    val updateMap = map["update"] as? Map<*, *> ?: return null

    val updateType = updateMap["sessionUpdate"] as? String ?: return null

    val update = when (updateType) {
        "agent_message_chunk" -> {
            val contentMap = updateMap["content"] as? Map<*, *> ?: return null
            val content = MessageContent.fromMap(contentMap) ?: return null
            SessionUpdate.AgentMessageChunk(content)
        }
        "tool_call" -> {
            SessionUpdate.ToolCall(
                toolCallId = updateMap["toolCallId"] as? String ?: return null,
                title = updateMap["title"] as? String ?: "",
                status = (updateMap["status"] as? String)?.let { ToolCallStatus.fromString(it) } ?: ToolCallStatus.IN_PROGRESS,
                kind = (updateMap["kind"] as? String)?.let { ToolKind.fromString(it) }
            )
        }
        "tool_call_update" -> {
            SessionUpdate.ToolCallUpdate(
                toolCallId = updateMap["toolCallId"] as? String ?: return null,
                status = (updateMap["status"] as? String)?.let { ToolCallStatus.fromString(it) },
                content = (updateMap["content"] as? List<*>)?.mapNotNull {
                    (it as? Map<*, *>)?.let { m -> ToolCallContent.fromMap(m) }
                }
            )
        }
        "available_commands_update" -> {
            val commands = (updateMap["availableCommands"] as? List<*>)?.mapNotNull {
                (it as? Map<*, *>)?.let { cmd ->
                    CommandInfo(
                        name = cmd["name"] as? String ?: return@mapNotNull null,
                        description = cmd["description"] as? String
                    )
                }
            } ?: emptyList()
            SessionUpdate.AvailableCommandsUpdate(commands)
        }
        else -> return null
    }

    return SessionUpdateNotification(sessionId, update)
}

/**
 * 解析权限请求通知
 */
fun parseRequestPermission(map: Map<*, *>): RequestPermissionNotification? {
    val requestId = map["requestId"] as? String ?: return null

    val toolCall = (map["toolCall"] as? Map<*, *>)?.let {
        ToolCallInfo(
            toolCallId = it["toolCallId"] as? String ?: return null,
            title = it["title"] as? String
        )
    }

    val options = (map["options"] as? List<*>)?.mapNotNull {
        (it as? Map<*, *>)?.let { opt ->
            PermissionOptionInfo(
                optionId = opt["optionId"] as? String ?: return@mapNotNull null,
                label = opt["label"] as? String ?: return@mapNotNull null,
                kind = opt["kind"] as? String
            )
        }
    } ?: return null

    return RequestPermissionNotification(
        requestId = requestId,
        description = map["description"] as? String,
        toolCall = toolCall,
        options = options
    )
}
