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
