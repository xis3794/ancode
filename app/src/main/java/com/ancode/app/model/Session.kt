package com.ancode.app.model

import kotlinx.serialization.Serializable

/**
 * A persisted coding session. Mirrors the way OpenCode/Claude Code keep
 * conversations resumable: messages + todo list + working directory.
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val workingDir: String = "/root/projects",
    /** Each session owns a workspace (host files/workspaces/<workspaceId>). */
    val workspaceId: String = "default"
) {
    fun withUpdatedAt(): Session = copy(updatedAt = System.currentTimeMillis())
}

/** Lightweight list entry for the session screen. */
data class SessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val preview: String,
    val messageCount: Int,
    val todoCount: Int
)