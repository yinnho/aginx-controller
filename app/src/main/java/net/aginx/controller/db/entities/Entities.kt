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
