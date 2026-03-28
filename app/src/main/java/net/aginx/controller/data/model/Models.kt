package net.aginx.controller.data.model

data class Aginx(
    val id: String,
    val name: String,
    val url: String,
    val token: String,
    val lastConnected: Long? = null,
    val isOnline: Boolean = false,
    val isFavorite: Boolean = false
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String?,
    val senderName: String?,
    val senderAvatar: String?,
    val content: String,
    val timestamp: Long,
    val isFromUser: Boolean
)

data class Conversation(
    val id: String,
    val title: String?,
    val agentId: String,
    val aginxId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null
)
