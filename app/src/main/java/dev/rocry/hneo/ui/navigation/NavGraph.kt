package dev.rocry.hneo.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.rocry.hneo.BuildConfig
import dev.rocry.hneo.data.*
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.ui.comments.CommentListScreen
import dev.rocry.hneo.ui.comments.CommentListViewModel
import dev.rocry.hneo.ui.explain.ExplainScreen
import dev.rocry.hneo.ui.explain.ExplainViewModel
import dev.rocry.hneo.ui.settings.SettingsScreen
import dev.rocry.hneo.ui.stories.StoryListScreen
import dev.rocry.hneo.ui.stories.StoryListViewModel
import dev.rocry.hneo.ui.summary.SummaryScreen
import dev.rocry.hneo.ui.summary.SummaryViewModel
import dev.rocry.hneo.ui.webview.WebSummaryData
import dev.rocry.hneo.ui.webview.WebSummaryScreen
import dev.rocry.hneo.ui.webview.WebSummaryViewModel
import dev.rocry.hneo.ui.webview.WebViewScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Routes {
    const val STORIES = "stories"
    const val COMMENTS = "comments/{storyJson}"
    const val SUMMARY = "summary/{storyJson}"
    const val EXPLAIN = "explain/{selectedText}/{storyTitle}"
    const val SETTINGS = "settings"
    const val WEBVIEW = "webview/{url}"
    const val WEB_SUMMARY = "web_summary"
}

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun HneoNavGraph() {
    val navController = rememberNavController()
    val storyListViewModel: StoryListViewModel = viewModel()
    val context = LocalContext.current
    val settings by settingsFlow(context).collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    var autoUpdateRelease by remember { mutableStateOf<UpdateService.ReleaseInfo?>(null) }
    var autoUpdateDownloadProgress by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        autoUpdateRelease = UpdateChecker.checkIfNeeded(context, BuildConfig.VERSION_CODE)
    }

    autoUpdateRelease?.let { release ->
        if (autoUpdateDownloadProgress != null) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading ${release.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${(autoUpdateDownloadProgress!! * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { autoUpdateDownloadProgress!! },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
            )
        } else {
            AlertDialog(
                onDismissRequest = { autoUpdateRelease = null },
                title = { Text("Update Available") },
                text = {
                    Text(
                        text = "${release.versionName}\n\n${release.changelog.ifBlank { "No changelog available" }}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        autoUpdateDownloadProgress = 0f
                        scope.launch {
                            try {
                                val file = UpdateService.downloadApk(
                                    context = context,
                                    url = release.downloadUrl,
                                    fileName = "hneo-${release.versionName}.apk",
                                    onProgress = { autoUpdateDownloadProgress = it },
                                )
                                UpdateService.installApk(context, file)
                                autoUpdateRelease = null
                                autoUpdateDownloadProgress = null
                            } catch (_: Exception) {
                                autoUpdateRelease = null
                                autoUpdateDownloadProgress = null
                            }
                        }
                    }) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { autoUpdateRelease = null }) {
                        Text("Later")
                    }
                },
            )
        }
    }

    NavHost(navController = navController, startDestination = Routes.STORIES) {
        composable(Routes.STORIES) {
            StoryListScreen(
                viewModel = storyListViewModel,
                onStoryClick = { story ->
                    val encoded = java.net.URLEncoder.encode(json.encodeToString(story), "UTF-8")
                    navController.navigate("comments/$encoded")
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(
            Routes.COMMENTS,
            arguments = listOf(navArgument("storyJson") { type = NavType.StringType }),
        ) { backStackEntry ->
            val storyJson = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("storyJson") ?: "",
                "UTF-8",
            )
            val story = json.decodeFromString<Story>(storyJson)
            val commentViewModel: CommentListViewModel = viewModel(
                key = "comments_${story.id}",
            )

            LaunchedEffect(story.id) {
                commentViewModel.init(story, storyListViewModel.commentCache)
            }

            CommentListScreen(
                story = story,
                viewModel = commentViewModel,
                onBack = { navController.popBackStack() },
                onSummaryClick = {
                    val encoded = java.net.URLEncoder.encode(json.encodeToString(story), "UTF-8")
                    navController.navigate("summary/$encoded")
                },
                onOpenUrl = { url ->
                    if (settings.openLinksInBrowser) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } else {
                        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("webview/$encoded")
                    }
                },
                onExplain = { selectedText ->
                    val encodedText = java.net.URLEncoder.encode(selectedText, "UTF-8")
                    val encodedTitle = java.net.URLEncoder.encode(story.title, "UTF-8")
                    navController.navigate("explain/$encodedText/$encodedTitle")
                },
            )
        }

        composable(
            Routes.SUMMARY,
            arguments = listOf(navArgument("storyJson") { type = NavType.StringType }),
        ) { backStackEntry ->
            val storyJson = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("storyJson") ?: "",
                "UTF-8",
            )
            val story = json.decodeFromString<Story>(storyJson)
            val summaryViewModel: SummaryViewModel = viewModel(
                key = "summary_${story.id}",
            )

            val commentViewModel: CommentListViewModel = viewModel(
                key = "comments_${story.id}",
            )
            val commentState by commentViewModel.state.collectAsState()

            LaunchedEffect(story.id) {
                summaryViewModel.startSummary(story, commentState.comments)
            }

            SummaryScreen(
                viewModel = summaryViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.EXPLAIN,
            arguments = listOf(
                navArgument("selectedText") { type = NavType.StringType },
                navArgument("storyTitle") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val selectedText = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("selectedText") ?: "",
                "UTF-8",
            )
            val storyTitle = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("storyTitle") ?: "",
                "UTF-8",
            )
            val explainViewModel: ExplainViewModel = viewModel()

            LaunchedEffect(selectedText) {
                explainViewModel.explain(selectedText, storyTitle)
            }

            ExplainScreen(
                viewModel = explainViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.WEBVIEW,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { backStackEntry ->
            val url = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("url") ?: "",
                "UTF-8",
            )

            WebViewScreen(
                url = url,
                onClose = { navController.popBackStack() },
                onSummary = { pageTitle, pageContent, pageUrl ->
                    WebSummaryData.set(pageTitle, pageContent, pageUrl)
                    navController.navigate(Routes.WEB_SUMMARY)
                },
            )
        }

        composable(Routes.WEB_SUMMARY) {
            val webSummaryViewModel: WebSummaryViewModel = viewModel()

            LaunchedEffect(Unit) {
                webSummaryViewModel.startSummary(
                    WebSummaryData.pageTitle,
                    WebSummaryData.pageContent,
                    WebSummaryData.pageUrl,
                )
            }

            WebSummaryScreen(
                viewModel = webSummaryViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
