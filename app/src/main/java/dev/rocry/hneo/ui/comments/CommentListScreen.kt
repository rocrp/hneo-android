package dev.rocry.hneo.ui.comments

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.ui.components.EinkPaginatedList
import dev.rocry.hneo.ui.theme.LocalEinkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentListScreen(
    story: Story,
    viewModel: CommentListViewModel,
    onBack: () -> Unit,
    onSummaryClick: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onExplain: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val einkMode = LocalEinkMode.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSummaryClick) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary")
                    }
                    story.url?.let { url ->
                        IconButton(onClick = { onOpenUrl(url) }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser")
                        }
                    }
                    IconButton(onClick = {
                        val shareUrl = story.url ?: "https://news.ycombinator.com/item?id=${story.id}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${story.title}\n$shareUrl")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (state.isLoading && state.comments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (einkMode) Text("Loading...") else CircularProgressIndicator()
                }
            } else if (state.error != null && state.comments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error ?: "Error loading comments",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (einkMode) {
                // E-ink: paginated lazy list — handles variable-height comments
                EinkPaginatedList(
                    modifier = Modifier.fillMaxSize(),
                    totalItemCount = state.comments.size + 1, // +1 for header
                ) {
                    item(key = "header") {
                        StoryHeader(
                            story = story,
                            onTitleClick = story.url?.let { url -> { onOpenUrl(url) } },
                        )
                    }
                    items(
                        items = state.comments,
                        key = { it.id },
                    ) { comment ->
                        CommentItem(
                            comment = comment,
                            isCollapsed = comment.id in state.collapsedIds,
                            onClick = { viewModel.toggleCollapse(comment.id) },
                            onExplain = { text -> onExplain(text) },
                        )
                    }
                    if (state.comments.isEmpty() && !state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("No comments yet", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            } else {
                // Normal: scrollable list
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        StoryHeader(
                            story = story,
                            onTitleClick = story.url?.let { url -> { onOpenUrl(url) } },
                        )
                    }

                    items(
                        items = state.comments,
                        key = { it.id },
                    ) { comment ->
                        CommentItem(
                            comment = comment,
                            isCollapsed = comment.id in state.collapsedIds,
                            onClick = { viewModel.toggleCollapse(comment.id) },
                            onExplain = { text -> onExplain(text) },
                        )
                    }

                    if (state.comments.isEmpty() && !state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No comments yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
