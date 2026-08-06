package com.ancode.app.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** OpenAI-compatible chat completion request body. */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<JsonObject>,
    val tools: List<JsonObject>? = null,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/** SSE chunk from a streaming response. */
@Serializable
data class ChatChunk(
    val id: String? = null,
    val choices: List<ChunkChoice> = emptyList()
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChunkToolCall>? = null
)

@Serializable
data class ChunkToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ChunkFunction? = null
)

@Serializable
data class ChunkFunction(
    val name: String? = null,
    val arguments: String? = null
)

/** Non-streaming response (used as fallback). */
@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ResponseChoice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class ResponseChoice(
    val index: Int = 0,
    val message: ResponseMessage = ResponseMessage(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ResponseToolCall>? = null
)

@Serializable
data class ResponseToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: ResponseFunction? = null
)

@Serializable
data class ResponseFunction(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/** Result of one assistant turn. */
data class TurnResult(
    val content: String,
    val toolCalls: List<AssistantToolCall> = emptyList(),
    val finishReason: String? = null
)

data class AssistantToolCall(
    val id: String,
    val name: String,
    val arguments: String
)