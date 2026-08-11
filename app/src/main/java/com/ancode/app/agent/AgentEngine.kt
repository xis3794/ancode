package com.ancode.app.agent

import com.ancode.app.llm.LlmClient
import com.ancode.app.llm.TurnResult
import com.ancode.app.llm.AssistantToolCall
import com.ancode.app.model.ChatMessage
import com.ancode.app.model.Role
import com.ancode.app.model.ToolCall
import com.ancode.app.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The agent loop, modeled after OpenCode's build agent and Claude Code:
 * system + history -> model -> tool calls -> execute -> feed results back -> repeat.
 */
class AgentEngine(
    private val llm: LlmClient,
    private val registry: ToolRegistry,
    private val systemPrompt: String,
    private val maxIterations: Int = 40
) {
    sealed interface Event {
        data class TurnStarted(val turn: Int) : Event
        data class TextDelta(val text: String) : Event
        data class ToolCallStarted(val callId: String, val name: String, val args: String) : Event
        data class ToolCallFinished(
            val callId: String, val name: String,
            val ok: Boolean, val result: String, val durationMs: Long
        ) : Event
        data class TurnFinished(val content: String) : Event
        data class Finished(val reason: String) : Event
        data class Error(val message: String) : Event
    }

    private val cancelled = AtomicBoolean(false)
    private val json = Json { ignoreUnknownKeys = true }

    fun cancel() {
        cancelled.set(true)
    }

    fun resetCancel() {
        cancelled.set(false)
    }

    /**
     * Run the agent until no tool calls remain (or max iterations / cancel).
     * [messages] is mutated in place: user msg, assistant msgs, tool results appended.
     */
    suspend fun run(
        messages: MutableList<ChatMessage>,
        onSync: suspend (List<ChatMessage>) -> Unit = { _ -> },
        onEvent: suspend (Event) -> Unit
    ) {
        cancelled.set(false)
        var iteration = 0
        var lastContent = ""

        while (iteration < maxIterations && !cancelled.get()) {
            iteration++
            onEvent(Event.TurnStarted(iteration))

            // ---- 1. Request a turn ----
            val turn = requestTurn(messages, onEvent)
            if (turn == null) {
                if (cancelled.get()) {
                    onEvent(Event.Finished("已停止"))
                }
                return
            }

            lastContent = turn.content

            // ---- 2. Persist assistant message ----
            val assistantMsg = ChatMessage(
                role = Role.ASSISTANT,
                content = turn.content.ifEmpty { null },
                toolCalls = turn.toolCalls.map { tc ->
                    ToolCall(id = tc.id, name = tc.name, arguments = tc.arguments)
                }
            )
            messages.add(assistantMsg)
            val assistantIdx = messages.size - 1
            onSync(messages.toList())

            // ---- 3. No tool calls -> done ----
            if (turn.toolCalls.isEmpty()) {
                onEvent(Event.TurnFinished(turn.content))
                onEvent(Event.Finished("完成"))
                return
            }

            // ---- 4. Execute tool calls sequentially ----
            for (tc in turn.toolCalls) {
                if (cancelled.get()) {
                    onEvent(Event.Finished("已停止"))
                    return
                }
                val started = System.currentTimeMillis()
                onEvent(Event.ToolCallStarted(tc.id, tc.name, tc.arguments))
                updateToolCall(messages, assistantIdx, tc.id) { it.copy(status = "running") }
                onSync(messages.toList())

                val (ok, result) = executeTool(tc)

                val duration = System.currentTimeMillis() - started
                onEvent(Event.ToolCallFinished(tc.id, tc.name, ok, result, duration))

                updateToolCall(messages, assistantIdx, tc.id) {
                    it.copy(
                        status = if (ok) "success" else "error",
                        result = if (ok) result.take(2000) else null,
                        error = if (ok) null else result.take(1000),
                        durationMs = duration
                    )
                }

                // tool result message
                messages.add(
                    ChatMessage(
                        role = Role.TOOL,
                        content = result.take(24_000),
                        toolCallId = tc.id,
                        toolName = tc.name
                    )
                )
                onSync(messages.toList())
            }
            if (iteration >= maxIterations) {
                onEvent(Event.Finished("达到最大迭代次数 $maxIterations"))
            }
        }
    }

    /** Patch one tool call inside the assistant message at [idx] (in place). */
    private fun updateToolCall(
        messages: MutableList<ChatMessage>,
        idx: Int,
        callId: String,
        transform: (ToolCall) -> ToolCall
    ) {
        if (idx !in messages.indices) return
        val msg = messages[idx]
        messages[idx] = msg.copy(toolCalls = msg.toolCalls.map { c ->
            if (c.id == callId) transform(c) else c
        })
    }

    private suspend fun requestTurn(
        messages: List<ChatMessage>,
        onEvent: suspend (Event) -> Unit
    ): TurnResult? {
        val full = buildList {
            add(ChatMessage(role = Role.SYSTEM, content = systemPrompt))
            addAll(messages)
        }

        val sb = StringBuilder()
        val toolCallAcc = LinkedHashMap<Int, StringBuilder>() // index -> args
        val toolIdAcc = HashMap<Int, String>()
        val toolNameAcc = HashMap<Int, String>()
        var failed: String? = null

        try {
            llm.chat(full, registry.toApiTools()).collect { delta ->
                when (delta) {
                    is LlmClient.Delta.Text -> {
                        sb.append(delta.text)
                        onEvent(Event.TextDelta(delta.text))
                    }
                    is LlmClient.Delta.ToolCallFragment -> {
                        toolIdAcc[delta.index] = delta.id ?: toolIdAcc[delta.index].orEmpty()
                        delta.name?.let { toolNameAcc[delta.index] = it }
                        delta.argumentsDelta?.let {
                            toolCallAcc.getOrPut(delta.index) { StringBuilder() }.append(it)
                        }
                    }
                    is LlmClient.Delta.Done -> Unit
                    is LlmClient.Delta.Error -> {
                        failed = delta.message
                        onEvent(Event.Error(delta.message))
                    }
                }
            }
        } catch (e: Exception) {
            failed = e.message ?: "请求失败"
            onEvent(Event.Error(failed ?: "请求失败"))
        }
        if (failed != null) return null

        val toolCalls = toolCallAcc.entries.sortedBy { it.key }.map { (idx, argsSb) ->
            AssistantToolCall(
                id = toolIdAcc[idx] ?: "call_${UUID.randomUUID().toString().take(8)}",
                name = toolNameAcc[idx] ?: "unknown",
                arguments = argsSb.toString()
            )
        }
        return TurnResult(sb.toString(), toolCalls)
    }

    private suspend fun executeTool(tc: AssistantToolCall): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val tool = registry.get(tc.name)
        if (tool == null) {
            return@withContext false to "未知工具: ${tc.name}"
        }
        val args = parseArgs(tc.arguments)
        try {
            val result = tool.execute(args)
            true to result
        } catch (e: Exception) {
            false to "工具执行异常: ${e.message}"
        }
    }

    private fun parseArgs(raw: String): Map<String, Any?> {
        if (raw.isBlank()) return emptyMap()
        return try {
            json.parseToJsonElement(raw).jsonObject.toMutableMap().mapValues { (_, v) ->
                when {
                    v is JsonObject -> v.toString()
                    else -> v.jsonPrimitive.contentOrNull ?: v.toString()
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}