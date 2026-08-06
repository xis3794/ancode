package com.ancode.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Role of a message inside a session. */
@Serializable
enum class Role {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL
}

/** A tool call requested by the model (OpenAI tool_calls shape). */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String = "{}",
    // UI state (not sent to the model)
    val status: String = "pending",      // pending | running | success | error
    val result: String? = null,
    val durationMs: Long? = null,
    val error: String? = null
)

/**
 * One persisted chat message. Content is nullable for assistant messages that
 * only carry tool calls.
 */
@Serializable
data class ChatMessage(
    val role: Role,
    val content: String? = null,
    val toolCallId: String? = null,       // for ROLE.TOOL
    val toolName: String? = null,         // for ROLE.TOOL (UI label)
    val toolCalls: List<ToolCall> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Convert to the wire format used by the OpenAI-compatible API. */
    fun toApiMap(): Map<String, Any?> = when (role) {
        Role.USER -> mapOf("role" to "user", "content" to (content ?: ""))
        Role.ASSISTANT -> {
            if (toolCalls.isNotEmpty()) {
                mapOf(
                    "role" to "assistant",
                    "content" to (content ?: ""),
                    "tool_calls" to toolCalls.map { tc ->
                        mapOf(
                            "id" to tc.id,
                            "type" to "function",
                            "function" to mapOf("name" to tc.name, "arguments" to tc.arguments)
                        )
                    }
                )
            } else {
                mapOf("role" to "assistant", "content" to (content ?: ""))
            }
        }
        Role.TOOL -> mapOf(
            "role" to "tool",
            "tool_call_id" to (toolCallId ?: ""),
            "content" to (content ?: "")
        )
        Role.SYSTEM -> mapOf("role" to "system", "content" to (content ?: ""))
    }
}