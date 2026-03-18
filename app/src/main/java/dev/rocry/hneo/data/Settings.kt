package dev.rocry.hneo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
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
}

enum class ThemeMode(val label: String) {
    NORMAL("Normal"),
    EINK("E-Ink");

    companion object {
        fun fromString(s: String): ThemeMode = entries.find { it.name == s } ?: NORMAL
    }
}

data class AppSettings(
    val llmApiUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    val llmModel: String = "gemini-flash-lite-latest",
    val llmApiKey: String = "",
    val llmMaxComments: Int = 200,
    val llmSystemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val llmExplainPrompt: String = DEFAULT_EXPLAIN_PROMPT,
    val llmWebpageSummaryPrompt: String = DEFAULT_WEBPAGE_SUMMARY_PROMPT,
    val fontChoice: String = "System",
    val themeMode: ThemeMode = ThemeMode.NORMAL,
    val openLinksInBrowser: Boolean = false,
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

fun settingsFlow(context: Context): Flow<AppSettings> =
    context.dataStore.data.map { prefs ->
        AppSettings(
            llmApiUrl = prefs[SettingsKeys.LLM_API_URL] ?: AppSettings().llmApiUrl,
            llmModel = prefs[SettingsKeys.LLM_MODEL] ?: AppSettings().llmModel,
            llmApiKey = prefs[SettingsKeys.LLM_API_KEY] ?: "",
            llmMaxComments = prefs[SettingsKeys.LLM_MAX_COMMENTS] ?: 200,
            llmSystemPrompt = prefs[SettingsKeys.LLM_SYSTEM_PROMPT] ?: AppSettings().llmSystemPrompt,
            llmExplainPrompt = prefs[SettingsKeys.LLM_EXPLAIN_PROMPT] ?: AppSettings().llmExplainPrompt,
            llmWebpageSummaryPrompt = prefs[SettingsKeys.LLM_WEBPAGE_SUMMARY_PROMPT] ?: AppSettings().llmWebpageSummaryPrompt,
            fontChoice = prefs[SettingsKeys.FONT_CHOICE] ?: "System",
            themeMode = ThemeMode.fromString(prefs[SettingsKeys.THEME_MODE] ?: "NORMAL"),
            openLinksInBrowser = prefs[SettingsKeys.OPEN_LINKS_IN_BROWSER] ?: false,
        )
    }

suspend fun updateSetting(context: Context, key: Preferences.Key<String>, value: String) {
    context.dataStore.edit { it[key] = value }
}

suspend fun updateSetting(context: Context, key: Preferences.Key<Int>, value: Int) {
    context.dataStore.edit { it[key] = value }
}

suspend fun updateSetting(context: Context, key: Preferences.Key<Boolean>, value: Boolean) {
    context.dataStore.edit { it[key] = value }
}
