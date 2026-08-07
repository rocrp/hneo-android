package dev.rocry.hneo.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rocry.hneo.BuildConfig
import dev.rocry.hneo.data.CommentCache
import dev.rocry.hneo.data.DataStoreSettings
import dev.rocry.hneo.data.HNClient
import dev.rocry.hneo.data.OpenGraphService
import dev.rocry.hneo.data.PageTextCache
import dev.rocry.hneo.data.PasteService
import dev.rocry.hneo.data.SettingsStore
import dev.rocry.hneo.data.StoryCache
import dev.rocry.hneo.data.StoryRepository
import dev.rocry.hneo.data.SummaryCache
import dev.rocry.hneo.data.UpdateChecker
import dev.rocry.hneo.data.UpdateService
import dev.rocry.hneo.data.http.HttpEngine
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.data.http.OkHttpEngine
import dev.rocry.hneo.data.llm.LlmClient
import dev.rocry.hneo.data.llm.OpenAiLlmClient
import dev.rocry.hneo.data.settingsDataStore
import dev.rocry.hneo.ui.comments.CommentListViewModel
import dev.rocry.hneo.ui.llmdocument.LlmDocumentViewModel
import dev.rocry.hneo.ui.stories.StoryListViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Injectable so tests can run every module on a deterministic scheduler. */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

/**
 * The composition root: the one place that constructs modules and wires them
 * together. Nothing below here reaches for a singleton or builds its own
 * dependencies — they are handed everything they need.
 */
class AppContainer(
    context: Context,
    val dispatchers: AppDispatchers = AppDispatchers(),
) {
    private val appContext: Context = context.applicationContext

    /** One JSON configuration for the whole app. */
    val json: Json = Json { ignoreUnknownKeys = true }

    /** One connection pool for the whole app. */
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(dev.rocry.hneo.data.http.HttpRequest.DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val httpEngine: HttpEngine = OkHttpEngine(okHttpClient, dispatchers.io)

    private val jsonHttp = JsonHttp(httpEngine, json, dispatchers.io)

    val settings: SettingsStore = DataStoreSettings(appContext.settingsDataStore)

    val hnClient = HNClient(jsonHttp)
    val llmClient: LlmClient = OpenAiLlmClient(httpEngine, json, settings, dispatchers.io)
    val openGraphService = OpenGraphService(jsonHttp)
    val pasteService = PasteService(jsonHttp)
    val updateService = UpdateService(jsonHttp, httpEngine, dispatchers.io)

    private val storyCache = StoryCache(File(appContext.cacheDir, "stories"), json, dispatchers.io)
    private val commentCache = CommentCache()

    val storyRepository = StoryRepository(hnClient, storyCache, commentCache)
    val pageTextCache = PageTextCache()
    val summaryCache = SummaryCache(File(appContext.cacheDir, "summaries.json"), json, dispatchers.io)

    val updatesDir: File = File(appContext.cacheDir, "updates")
    val updateChecker = UpdateChecker(settings, updateService, BuildConfig.VERSION_CODE)

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { StoryListViewModel(storyRepository) }
        initializer { CommentListViewModel(storyRepository) }
        initializer { LlmDocumentViewModel(llmClient, storyRepository, summaryCache, pageTextCache) }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided — MainActivity must supply one")
}
