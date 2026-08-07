package dev.rocry.hneo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.rocry.hneo.ui.theme.BuiltInFont
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) {
    NORMAL("Normal"),
    EINK("E-Ink");

    companion object {
        fun fromString(s: String): ThemeMode = entries.find { it.name == s } ?: NORMAL
    }
}

/**
 * Every user preference, with its default declared exactly once — here, in the
 * primary constructor. Readers and writers both go through this record, so a
 * default can never disagree with itself.
 */
data class AppSettings(
    val llmApiUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    val llmModel: String = "gemini-flash-lite-latest",
    val llmApiKey: String = "",
    val llmMaxComments: Int = 200,
    val llmSystemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val llmExplainPrompt: String = DEFAULT_EXPLAIN_PROMPT,
    val llmWebpageSummaryPrompt: String = DEFAULT_WEBPAGE_SUMMARY_PROMPT,
    val fontChoice: String = BuiltInFont.SYSTEM.displayName,
    val themeMode: ThemeMode = ThemeMode.NORMAL,
    val openLinksInBrowser: Boolean = false,
    val autoUpdateEnabled: Boolean = true,
    val updateCheckIntervalHours: Int = 24,
    val lastUpdateCheck: Long = 0L,
)

const val DEFAULT_SYSTEM_PROMPT =
    "You are a helpful assistant that summarizes Hacker News discussions. " +
        "Provide a concise summary highlighting key arguments, insights, and consensus. " +
        "Use the reader's language (detected from the request). " +
        "Format with markdown."

const val DEFAULT_EXPLAIN_PROMPT =
    "You are a helpful assistant. Explain the selected text in context of the discussion. " +
        "Be concise and informative. Use the reader's language. Format with markdown."

const val DEFAULT_WEBPAGE_SUMMARY_PROMPT =
    "Summarize this webpage concisely. Highlight key points, main arguments, and important details. " +
        "Use the reader's language. Use markdown formatting."

/**
 * Settings as a module: observe the record, or transform it. Callers never see
 * storage keys, and never write one field at a time.
 */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class DataStoreSettings(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override val settings: Flow<AppSettings> =
        dataStore.data.map { it.toAppSettings() }.distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toAppSettings())) }
    }
}

private object Keys {
    val LLM_API_URL = stringPreferencesKey("llm_api_url")
    val LLM_MODEL = stringPreferencesKey("llm_model")
    val LLM_API_KEY = stringPreferencesKey("llm_api_key")
    val LLM_MAX_COMMENTS = intPreferencesKey("llm_max_comments")
    val LLM_SYSTEM_PROMPT = stringPreferencesKey("llm_system_prompt")
    val LLM_EXPLAIN_PROMPT = stringPreferencesKey("llm_explain_prompt")
    val LLM_WEBPAGE_SUMMARY_PROMPT = stringPreferencesKey("llm_webpage_summary_prompt")
    val FONT_CHOICE = stringPreferencesKey("font_choice")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val OPEN_LINKS_IN_BROWSER = booleanPreferencesKey("open_links_in_browser")
    val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
    val UPDATE_CHECK_INTERVAL_HOURS = intPreferencesKey("update_check_interval_hours")
    val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
}

/** Visible for testing: an unset preference must fall back to the record's own default. */
internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        llmApiUrl = this[Keys.LLM_API_URL] ?: defaults.llmApiUrl,
        llmModel = this[Keys.LLM_MODEL] ?: defaults.llmModel,
        llmApiKey = this[Keys.LLM_API_KEY] ?: defaults.llmApiKey,
        llmMaxComments = this[Keys.LLM_MAX_COMMENTS] ?: defaults.llmMaxComments,
        llmSystemPrompt = this[Keys.LLM_SYSTEM_PROMPT] ?: defaults.llmSystemPrompt,
        llmExplainPrompt = this[Keys.LLM_EXPLAIN_PROMPT] ?: defaults.llmExplainPrompt,
        llmWebpageSummaryPrompt = this[Keys.LLM_WEBPAGE_SUMMARY_PROMPT] ?: defaults.llmWebpageSummaryPrompt,
        fontChoice = this[Keys.FONT_CHOICE] ?: defaults.fontChoice,
        themeMode = this[Keys.THEME_MODE]?.let(ThemeMode::fromString) ?: defaults.themeMode,
        openLinksInBrowser = this[Keys.OPEN_LINKS_IN_BROWSER] ?: defaults.openLinksInBrowser,
        autoUpdateEnabled = this[Keys.AUTO_UPDATE_ENABLED] ?: defaults.autoUpdateEnabled,
        updateCheckIntervalHours = this[Keys.UPDATE_CHECK_INTERVAL_HOURS] ?: defaults.updateCheckIntervalHours,
        lastUpdateCheck = this[Keys.LAST_UPDATE_CHECK] ?: defaults.lastUpdateCheck,
    )
}

internal fun MutablePreferences.write(settings: AppSettings) {
    this[Keys.LLM_API_URL] = settings.llmApiUrl
    this[Keys.LLM_MODEL] = settings.llmModel
    this[Keys.LLM_API_KEY] = settings.llmApiKey
    this[Keys.LLM_MAX_COMMENTS] = settings.llmMaxComments
    this[Keys.LLM_SYSTEM_PROMPT] = settings.llmSystemPrompt
    this[Keys.LLM_EXPLAIN_PROMPT] = settings.llmExplainPrompt
    this[Keys.LLM_WEBPAGE_SUMMARY_PROMPT] = settings.llmWebpageSummaryPrompt
    this[Keys.FONT_CHOICE] = settings.fontChoice
    this[Keys.THEME_MODE] = settings.themeMode.name
    this[Keys.OPEN_LINKS_IN_BROWSER] = settings.openLinksInBrowser
    this[Keys.AUTO_UPDATE_ENABLED] = settings.autoUpdateEnabled
    this[Keys.UPDATE_CHECK_INTERVAL_HOURS] = settings.updateCheckIntervalHours
    this[Keys.LAST_UPDATE_CHECK] = settings.lastUpdateCheck
}
