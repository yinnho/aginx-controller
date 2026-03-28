package net.aginx.controller.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aginx")
data class AginxEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val token: String,
    val lastConnected: Long?,
    val isOnline: Boolean = false,
    val isFavorite: Boolean = false
)

@Entity(tableName = "agents", primaryKeys = ["id", "aginxId"])
data class AgentEntity(
    val id: String,
    val numericId: Long,
    val localId: String,
    val aginxId: String,
    val nickname: String,
    val avatar: String?,
    val description: String?,
    val personality: String?,
    val capabilities: String,
    val workdir: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String?,
    val senderName: String?,
    val senderAvatar: String?,
    val content: String,
    val timestamp: Long,
    val isFromUser: Boolean
)

@Entity(tableName = "conversations", primaryKeys = ["id", "aginxId"])
data class ConversationEntity(
    val id: String,
    val aginxId: String,
    val agentId: String,
    val workdir: String?,
    val title: String?,
    val sessionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String?,
    val lastMessageTime: Long?
)
