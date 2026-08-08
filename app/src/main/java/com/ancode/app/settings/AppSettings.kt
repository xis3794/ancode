package com.ancode.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ancode.app.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ancode_settings")

/** A single LLM provider profile (OpenAI-compatible endpoint). */
@Serializable
data class ProviderProfile(
    val id: String = "",
    val name: String = "DeepSeek",
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat"
) {
    val chatEndpoint: String
        get() = baseUrl.trimEnd('/') + "/chat/completions"
}

/** Persistent app settings (DataStore-backed). */
class AppSettings(private val context: Context) {

    private object Keys {
        val PROVIDERS = stringPreferencesKey("providers_json")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider_id")
        val WORKING_DIR = stringPreferencesKey("working_dir")
        val SESSION_ID = stringPreferencesKey("active_session_id")

        // legacy single-provider keys (migrated on first read)
        val LEGACY_API_BASE = stringPreferencesKey("api_base_url")
        val LEGACY_API_KEY = stringPreferencesKey("api_key")
        val LEGACY_MODEL = stringPreferencesKey("model")
    }

    data class Settings(
        val providers: List<ProviderProfile> = defaultProviders(),
        val activeProviderId: String = providers.firstOrNull()?.id ?: "",
        val workingDir: String = "/root/projects"
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val providers = runCatching {
            Json.decodeFromString<List<ProviderProfile>>(p[Keys.PROVIDERS] ?: "[]")
        }.getOrDefault(emptyList())
        val migrated = migrateIfNeeded(
            stored = p[Keys.PROVIDERS],
            providers = providers,
            legacyBase = p[Keys.LEGACY_API_BASE],
            legacyKey = p[Keys.LEGACY_API_KEY],
            legacyModel = p[Keys.LEGACY_MODEL]
        )
        Settings(
            providers = migrated,
            activeProviderId = p[Keys.ACTIVE_PROVIDER] ?: migrated.firstOrNull()?.id ?: "",
            workingDir = p[Keys.WORKING_DIR] ?: "/root/projects"
        )
    }

    /** If no providers stored yet, seed defaults (or migrate the legacy single config). */
    private fun migrateIfNeeded(
        stored: String?,
        providers: List<ProviderProfile>,
        legacyBase: String?,
        legacyKey: String?,
        legacyModel: String?
    ): List<ProviderProfile> {
        if (stored != null && providers.isNotEmpty()) return providers
        val defaults = defaultProviders()
        // legacy single-provider config wins as the active one
        if (legacyBase != null || legacyKey != null) {
            val legacy = ProviderProfile(
                id = "p-legacy",
                name = "自定义",
                baseUrl = legacyBase ?: "https://api.deepseek.com",
                apiKey = legacyKey ?: "",
                model = legacyModel ?: "deepseek-chat"
            )
            return listOf(legacy) + defaults.filter { it.id != legacy.id }
        }
        // nothing configured yet → user adds providers themselves in Settings
        return defaults
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(
        providers: List<ProviderProfile>? = null,
        activeProviderId: String? = null,
        workingDir: String? = null
    ) {
        context.dataStore.edit { p ->
            providers?.let { p[Keys.PROVIDERS] = Json.encodeToString(it) }
            activeProviderId?.let { p[Keys.ACTIVE_PROVIDER] = it }
            workingDir?.let { p[Keys.WORKING_DIR] = it.trim() }
        }
    }

    /** Convenience: upsert a provider and (optionally) make it active. */
    suspend fun upsertProvider(profile: ProviderProfile, makeActive: Boolean = false) {
        val s = current()
        val existing = s.providers.any { it.id == profile.id }
        val list = if (existing) {
            s.providers.map { if (it.id == profile.id) profile else it }
        } else {
            s.providers + profile
        }
        update(
            providers = list,
            activeProviderId = if (makeActive) profile.id else s.activeProviderId
        )
    }

    suspend fun deleteProvider(id: String) {
        val s = current()
        val list = s.providers.filterNot { it.id == id }
        val nextActive = if (s.activeProviderId == id) list.firstOrNull()?.id ?: "" else s.activeProviderId
        update(providers = list, activeProviderId = nextActive)
    }

    suspend fun setActiveProvider(id: String) = update(activeProviderId = id)

    suspend fun llmConfig(): LlmConfig {
        val s = current()
        val p = s.providers.firstOrNull { it.id == s.activeProviderId }
            ?: s.providers.firstOrNull()
        if (p == null) {
            // no provider configured yet — return a placeholder; AppViewModel
            // will prompt the user to add one
            return LlmConfig(
                baseUrl = "https://api.deepseek.com",
                apiKey = "",
                model = "deepseek-chat"
            )
        }
        return LlmConfig(
            baseUrl = p.baseUrl,
            apiKey = p.apiKey,
            model = p.model
        )
    }

    /** Active session id (resume last session on app start). */
    val activeSessionId: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_ID] }

    suspend fun setActiveSession(id: String?) {
        context.dataStore.edit { p ->
            if (id == null) p.remove(Keys.SESSION_ID) else p[Keys.SESSION_ID] = id
        }
    }

    companion object {
        /** Built-in provider presets (OpenAI-compatible) — user picks when adding. */
        fun defaultProviders(): List<ProviderProfile> = emptyList()

        fun newId(): String = "p-" + System.currentTimeMillis().toString(16)
    }
}