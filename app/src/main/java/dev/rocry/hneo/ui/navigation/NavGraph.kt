package dev.rocry.hneo.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Routes {
    const val STORIES = "stories"
    const val COMMENTS = "comments/{storyJson}"
    const val SUMMARY = "summary/{storyJson}"
    const val EXPLAIN = "explain/{selectedText}/{storyTitle}"
    const val SETTINGS = "settings"
}

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun HneoNavGraph() {
    val navController = rememberNavController()
    val storyListViewModel: StoryListViewModel = viewModel()

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

            // Get comments from the comment view model's cache
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

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
