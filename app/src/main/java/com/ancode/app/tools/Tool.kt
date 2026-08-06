package com.ancode.app.tools

import kotlinx.serialization.json.JsonObject

/**
 * A tool the agent can invoke. JSON Schema for parameters is generated from
 * [parametersSpec] so the model sees exactly what [execute] expects.
 *
 * Extension point: MCP servers and Skills will be exposed as additional
 * Tool implementations in a later milestone.
 */
interface Tool {
    val name: String
    val description: String

    /** JSON Schema "properties" map. */
    val parametersSpec: Map<String, JsonObject>

    /** Required parameter names (JSON Schema "required"). */
    val requiredParams: List<String>

    /** Execute with parsed arguments; returns a short text result for the model. */
    suspend fun execute(args: Map<String, Any?>): String
}

data class ToolResult(
    val ok: Boolean,
    val text: String,
    val durationMs: Long = 0
)

fun Tool.toJsonSchema(): JsonObject {
    val props = kotlinx.serialization.json.buildJsonObject {
        parametersSpec.forEach { (k, v) -> put(k, v) }
    }
    return kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", props)
        if (requiredParams.isNotEmpty()) {
            put("required", kotlinx.serialization.json.buildJsonArray {
                requiredParams.forEach { add(it) }
            })
        }
    }
}

/** Common JSON Schema builders for tool parameters. */
object Schema {
    fun string(description: String, enum: List<String>? = null) =
        kotlinx.serialization.json.buildJsonObject {
            put("type", "string")
            put("description", description)
            enum?.let {
                put("enum", kotlinx.serialization.json.buildJsonArray { it.forEach { e -> add(e) } })
            }
        }

    fun integer(description: String) = kotlinx.serialization.json.buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    fun boolean(description: String) = kotlinx.serialization.json.buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    fun number(description: String) = kotlinx.serialization.json.buildJsonObject {
        put("type", "number")
        put("description", description)
    }
}