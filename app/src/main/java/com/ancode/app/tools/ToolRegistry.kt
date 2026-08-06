package com.ancode.app.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Holds all built-in tools and exposes them in the OpenAI tools wire format.
 * Future MCP servers / Skills register here as additional [Tool] instances.
 */
class ToolRegistry(
    tools: List<Tool>
) {
    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    fun get(name: String): Tool? = byName[name]

    fun all(): List<Tool> = byName.values.toList()

    /** OpenAI "tools" array for the chat request. */
    fun toApiTools(): List<JsonObject> = byName.values.map { tool ->
        buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.toJsonSchema())
            })
        }
    }
}