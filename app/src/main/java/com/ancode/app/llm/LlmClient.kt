package com.ancode.app.llm

import com.ancode.app.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Streaming client for OpenAI-compatible /chat/completions endpoints.
 * Used by the agent loop; emits text deltas and accumulated tool-call fragments.
 */
class LlmClient(
    private val config: LlmConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val MAX_TOOL_ARG_LENGTH = 64 * 1024
    }

    sealed interface Delta {
        data class Text(val text: String) : Delta
        data class ToolCallFragment(val index: Int, val id: String?, val name: String?, val argumentsDelta: String?) : Delta
        data class Done(val finishReason: String?) : Delta
        data class Error(val message: String) : Delta
    }

    /** Stream one assistant turn. [messages] must include system + history + tool results. */
    fun chat(
        messages: List<ChatMessage>,
        toolsJson: List<JsonObject>? = null
    ): Flow<Delta> = flow {
        val payload = buildJsonObject {
            put("model", config.model)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    add(
                        buildJsonObject {
                            put("role", msg.role.name.lowercase())
                            when (msg.role) {
                                com.ancode.app.model.Role.USER,
                                com.ancode.app.model.Role.SYSTEM -> put("content", msg.content ?: "")
                                com.ancode.app.model.Role.ASSISTANT -> {
                                    put("content", msg.content ?: "")
                                    if (msg.toolCalls.isNotEmpty()) {
                                        putJsonArray("tool_calls") {
                                            msg.toolCalls.forEach { tc ->
                                                add(
                                                    buildJsonObject {
                                                        put("id", tc.id)
                                                        put("type", "function")
                                                        put("function", buildJsonObject {
                                                            put("name", tc.name)
                                                            put("arguments", tc.arguments)
                                                        })
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                com.ancode.app.model.Role.TOOL -> {
                                    put("tool_call_id", msg.toolCallId ?: "")
                                    put("content", msg.content ?: "")
                                }
                            }
                        }
                    )
                }
            }
            if (toolsJson != null && toolsJson.isNotEmpty()) {
                putJsonArray("tools") { toolsJson.forEach { add(it) } }
            }
            put("stream", true)
            config.temperature?.let { put("temperature", it) }
            config.maxTokens?.let { put("max_tokens", it) }
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(config.chatEndpoint)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            emit(Delta.Error(e.message ?: "Network error"))
            return@flow
        }

        if (!response.isSuccessful) {
            val errBody = response.body?.string().orEmpty()
            emit(Delta.Error(parseApiError(response.code, errBody)))
            response.close()
            return@flow
        }

        val source = response.body?.source()
        if (source == null) {
            emit(Delta.Error("Empty response body"))
            response.close()
            return@flow
        }

        // Accumulate tool-call fragments by index (streamed arguments arrive in chunks).
        val toolIds = HashMap<Int, String>()
        val toolNames = HashMap<Int, String>()

        try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") {
                    emit(Delta.Done(null))
                    break
                }
                try {
                    val chunk = json.decodeFromString(ChatChunk.serializer(), data)
                    for (choice in chunk.choices) {
                        val delta = choice.delta
                        if (delta.content != null) {
                            emit(Delta.Text(delta.content))
                        }
                        val tcs = delta.toolCalls
                        if (tcs != null) {
                            for (tc in tcs) {
                                val idx = tc.index
                                if (tc.id != null) toolIds[idx] = tc.id
                                if (tc.function?.name != null) toolNames[idx] = tc.function!!.name
                                emit(Delta.ToolCallFragment(idx, toolIds[idx], toolNames[idx], tc.function?.arguments))
                            }
                        }
                        if (choice.finishReason != null) {
                            emit(Delta.Done(choice.finishReason))
                        }
                    }
                } catch (e: Exception) {
                    // malformed chunk: skip
                }
            }
        } catch (e: Exception) {
            emit(Delta.Error(e.message ?: "Stream error"))
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseApiError(code: Int, body: String): String {
        val friendly = when (code) {
            401 -> "401 Unauthorized — 请检查 API Key"
            403 -> "403 Forbidden — 无权限访问该模型"
            404 -> "404 Not Found — 请检查 baseUrl / 模型名"
            429 -> "429 Rate Limited — 请求过于频繁或额度不足"
            else -> null
        }
        if (friendly != null) return friendly
        return runCatching {
            val err = json.decodeFromString(ChatResponse.serializer(), body).error
            err?.message ?: "HTTP $code"
        }.getOrDefault("HTTP $code")
    }
}