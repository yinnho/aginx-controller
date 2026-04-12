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
