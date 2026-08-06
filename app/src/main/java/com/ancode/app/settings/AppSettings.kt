package com.ancode.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ancode.app.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ancode_settings")

/** Persistent app settings (DataStore-backed). */
class AppSettings(private val context: Context) {

    private object Keys {
        val API_BASE = stringPreferencesKey("api_base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val WORKING_DIR = stringPreferencesKey("working_dir")
        val SESSION_ID = stringPreferencesKey("active_session_id")
    }

    data class Settings(
        val baseUrl: String = "https://api.deepseek.com",
        val apiKey: String = "",
        val model: String = "deepseek-chat",
        val workingDir: String = "/root/projects"
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            baseUrl = p[Keys.API_BASE] ?: "https://api.deepseek.com",
            apiKey = p[Keys.API_KEY] ?: "",
            model = p[Keys.MODEL] ?: "deepseek-chat",
            workingDir = p[Keys.WORKING_DIR] ?: "/root/projects"
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(
        baseUrl: String? = null,
        apiKey: String? = null,
        model: String? = null,
        workingDir: String? = null
    ) {
        context.dataStore.edit { p ->
            baseUrl?.let { p[Keys.API_BASE] = it.trim() }
            apiKey?.let { p[Keys.API_KEY] = it.trim() }
            model?.let { p[Keys.MODEL] = it.trim() }
            workingDir?.let { p[Keys.WORKING_DIR] = it.trim() }
        }
    }

    suspend fun llmConfig(): LlmConfig {
        val s = current()
        return LlmConfig(
            baseUrl = s.baseUrl,
            apiKey = s.apiKey,
            model = s.model
        )
    }

    /** Active session id (resume last session on app start). */
    val activeSessionId: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_ID] }

    suspend fun setActiveSession(id: String?) {
        context.dataStore.edit { p ->
            if (id == null) p.remove(Keys.SESSION_ID) else p[Keys.SESSION_ID] = id
        }
    }
}