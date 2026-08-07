package dev.rocry.hneo.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.di.LocalAppContainer
import dev.rocry.hneo.ui.comments.CommentListScreen
import dev.rocry.hneo.ui.comments.CommentListViewModel
import dev.rocry.hneo.ui.llmdocument.LlmDocumentScreen
import dev.rocry.hneo.ui.llmdocument.LlmDocumentViewModel
import dev.rocry.hneo.ui.settings.SettingsScreen
import dev.rocry.hneo.ui.stories.StoryListScreen
import dev.rocry.hneo.ui.stories.StoryListViewModel
import dev.rocry.hneo.ui.update.UpdatePrompt
import dev.rocry.hneo.ui.webview.WebViewScreen

@Composable
fun HneoNavGraph() {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val storyListViewModel: StoryListViewModel = viewModel(factory = container.viewModelFactory)
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsState(initial = AppSettings())

    LaunchedEffect(Unit) { container.appUpdater.checkOnLaunch() }

    UpdatePrompt(updater = container.appUpdater)

    fun openUrl(url: String) {
        if (settings.openLinksInBrowser) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } else {
            navController.navigate(Routes.webView(url))
        }
    }

    NavHost(navController = navController, startDestination = Routes.STORIES) {
        composable(Routes.STORIES) {
            StoryListScreen(
                viewModel = storyListViewModel,
                onStoryClick = { story -> navController.navigate(Routes.comments(story.id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            Routes.COMMENTS,
            arguments = listOf(navArgument(Routes.ARG_STORY_ID) { type = NavType.IntType }),
        ) { entry ->
            val storyId = entry.intArg(Routes.ARG_STORY_ID)
            val commentViewModel: CommentListViewModel = viewModel(
                key = "comments_$storyId",
                factory = container.viewModelFactory,
            )

            LaunchedEffect(storyId) { commentViewModel.load(storyId) }

            CommentListScreen(
                viewModel = commentViewModel,
                onBack = { navController.popBackStack() },
                onSummaryClick = { navController.navigate(Routes.summary(storyId)) },
                onOpenUrl = ::openUrl,
                onExplain = { selectedText, storyTitle ->
                    navController.navigate(Routes.explain(selectedText, storyTitle))
                },
            )
        }

        composable(
            Routes.SUMMARY,
            arguments = listOf(navArgument(Routes.ARG_STORY_ID) { type = NavType.IntType }),
        ) { entry ->
            val storyId = entry.intArg(Routes.ARG_STORY_ID)
            val documentViewModel: LlmDocumentViewModel = viewModel(
                key = "summary_$storyId",
                factory = container.viewModelFactory,
            )

            LaunchedEffect(storyId) { documentViewModel.summarizeStory(storyId) }

            LlmDocumentScreen(
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.EXPLAIN,
            arguments = listOf(
                navArgument(Routes.ARG_SELECTED_TEXT) { type = NavType.StringType },
                navArgument(Routes.ARG_STORY_TITLE) { type = NavType.StringType },
            ),
        ) { entry ->
            val selectedText = entry.stringArg(Routes.ARG_SELECTED_TEXT)
            val storyTitle = entry.stringArg(Routes.ARG_STORY_TITLE)
            val documentViewModel: LlmDocumentViewModel = viewModel(factory = container.viewModelFactory)

            LaunchedEffect(selectedText) { documentViewModel.explain(selectedText, storyTitle) }

            LlmDocumentScreen(
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.WEBVIEW,
            arguments = listOf(navArgument(Routes.ARG_URL) { type = NavType.StringType }),
        ) { entry ->
            WebViewScreen(
                url = entry.stringArg(Routes.ARG_URL),
                onClose = { navController.popBackStack() },
                onSummary = { pageTitle, pageContent, pageUrl ->
                    container.pageTextCache.put(pageUrl, pageContent)
                    navController.navigate(Routes.pageSummary(pageUrl, pageTitle))
                },
            )
        }

        composable(
            Routes.PAGE_SUMMARY,
            arguments = listOf(
                navArgument(Routes.ARG_URL) { type = NavType.StringType },
                navArgument(Routes.ARG_TITLE) { type = NavType.StringType },
            ),
        ) { entry ->
            val pageUrl = entry.stringArg(Routes.ARG_URL)
            val pageTitle = entry.stringArg(Routes.ARG_TITLE)
            val documentViewModel: LlmDocumentViewModel = viewModel(factory = container.viewModelFactory)

            LaunchedEffect(pageUrl) { documentViewModel.summarizePage(pageTitle, pageUrl) }

            LlmDocumentScreen(
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavBackStackEntry.intArg(name: String): Int =
    checkNotNull(arguments?.getInt(name)) { "route argument '$name' is missing" }

private fun NavBackStackEntry.stringArg(name: String): String =
    arguments?.getString(name).orEmpty()
