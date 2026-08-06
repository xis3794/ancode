package com.ancode.app.model

import kotlinx.serialization.Serializable

/** A task in the built-in Do List (Claude Code style TodoWrite). */
@Serializable
data class TodoItem(
    val id: String,
    val content: String,
    val done: Boolean = false
)

/** A TodoWrite operation requested by the agent. */
@Serializable
enum class TodoOp {
    ADD, UPDATE, MARK_DONE, CLEAR
}