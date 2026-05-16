package dev.opencode.mobile.ui

data class ServerConnection(
    val serverUrl: String,
    val token: String,
)

data class ActiveSession(
    val id: String,
    val projectId: String?,
    val directory: String?,
    val title: String?,
)
