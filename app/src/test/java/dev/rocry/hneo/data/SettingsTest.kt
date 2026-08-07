package dev.rocry.hneo.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun `an unset store yields exactly the declared defaults`() {
        // Defaults live in AppSettings' constructor and nowhere else; if a reader
        // ever grows a second opinion, this fails.
        assertEquals(AppSettings(), emptyPreferences().toAppSettings())
    }

    @Test
    fun `every field survives a round trip through storage`() {
        val custom = AppSettings(
            llmApiUrl = "https://llm.example/v1",
            llmModel = "some-model",
            llmApiKey = "sk-secret",
            llmMaxComments = 42,
            llmSystemPrompt = "system",
            llmExplainPrompt = "explain",
            llmWebpageSummaryPrompt = "webpage",
            fontChoice = "Literata",
            themeMode = ThemeMode.EINK,
            openLinksInBrowser = true,
            autoUpdateEnabled = false,
            updateCheckIntervalHours = 6,
            lastUpdateCheck = 1_700_000_000_000L,
        )

        val stored = mutablePreferencesOf().apply { write(custom) }

        assertEquals(custom, stored.toAppSettings())
    }

    @Test
    fun `the round trip covers a value different from every default`() {
        // Guards the test above: a field left at its default would pass a round
        // trip even if its key were never written.
        val defaults = AppSettings()
        val custom = AppSettings(
            llmApiUrl = "https://llm.example/v1",
            llmModel = "some-model",
            llmApiKey = "sk-secret",
            llmMaxComments = 42,
            llmSystemPrompt = "system",
            llmExplainPrompt = "explain",
            llmWebpageSummaryPrompt = "webpage",
            fontChoice = "Literata",
            themeMode = ThemeMode.EINK,
            openLinksInBrowser = true,
            autoUpdateEnabled = false,
            updateCheckIntervalHours = 6,
            lastUpdateCheck = 1_700_000_000_000L,
        )

        AppSettings::class.java.declaredFields
            .filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                assertEquals(
                    "${field.name} must differ from its default for the round trip to prove anything",
                    false,
                    field.get(defaults) == field.get(custom),
                )
            }
    }

    @Test
    fun `an unrecognised theme falls back to the default rather than crashing`() {
        val stored = mutablePreferencesOf(stringPreferencesKey("theme_mode") to "SEPIA")

        assertEquals(AppSettings().themeMode, stored.toAppSettings().themeMode)
    }

    @Test
    fun `a partially written store keeps the declared defaults for the rest`() {
        val stored = mutablePreferencesOf(stringPreferencesKey("llm_model") to "only-this")

        val settings = stored.toAppSettings()

        assertEquals("only-this", settings.llmModel)
        assertEquals(AppSettings().llmApiUrl, settings.llmApiUrl)
        assertEquals(AppSettings().llmMaxComments, settings.llmMaxComments)
    }
}
