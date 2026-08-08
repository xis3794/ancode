package com.ancode.app.session

import android.content.Context
import com.ancode.app.model.Session
import com.ancode.app.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Persists sessions as JSON files under files/sessions/<id>.json
 * (same spirit as OpenCode's session store).
 */
class SessionStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "sessions")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        dir.mkdirs()
    }

    suspend fun newSession(title: String = "新会话"): Session {
        val id = UUID.randomUUID().toString()
        val s = Session(
            id = id,
            title = title,
            // each session gets its own workspace dir (host files/workspaces/<id>)
            workspaceId = id.take(8)
        )
        save(s)
        return s
    }

    suspend fun load(id: String): Session? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString(Session.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(dir, "${session.id}.json")
            f.writeText(json.encodeToString(Session.serializer(), session.withUpdatedAt()))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }

    suspend fun list(): List<SessionSummary> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching {
                val s = json.decodeFromString(Session.serializer(), f.readText())
                SessionSummary(
                    id = s.id,
                    title = s.title,
                    updatedAt = s.updatedAt,
                    preview = s.messages.lastOrNull { it.role != com.ancode.app.model.Role.TOOL }?.content?.take(60) ?: "（空会话）",
                    messageCount = s.messages.size,
                    todoCount = s.todos.count { !it.done }
                )
            }.getOrNull()
        }?.sortedByDescending { it.updatedAt } ?: emptyList()
    }

    suspend fun rename(id: String, title: String) {
        load(id)?.let { save(it.copy(title = title)) }
    }
}