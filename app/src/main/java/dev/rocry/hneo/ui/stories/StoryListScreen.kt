package dev.rocry.hneo.ui.stories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.ui.components.EinkPaginatedList
import dev.rocry.hneo.ui.theme.LocalEinkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryListScreen(
    viewModel: StoryListViewModel,
    onStoryClick: (Story) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val einkMode = LocalEinkMode.current
    val listState = rememberLazyListState()

    // Prefetch comments for visible items (normal mode only)
    if (!einkMode) {
        val visibleItems by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                info.visibleItemsInfo.mapNotNull { item ->
                    state.stories.getOrNull(item.index)?.id
                }
            }
        }

        LaunchedEffect(visibleItems) {
            if (visibleItems.isNotEmpty()) {
                viewModel.prefetchComments(visibleItems)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("hneo") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Feed selector tabs
            ScrollableTabRow(
                selectedTabIndex = FeedKind.entries.indexOf(state.currentFeed),
                edgePadding = 16.dp,
                divider = {},
            ) {
                FeedKind.entries.forEach { feed ->
                    Tab(
                        selected = feed == state.currentFeed,
                        onClick = { viewModel.switchFeed(feed) },
                        text = { Text(feed.label) },
                    )
                }
            }

            if (state.error != null && state.stories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (state.isLoading && state.stories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (einkMode) Text("Loading...") else CircularProgressIndicator()
                }
            } else if (einkMode) {
                // E-ink: paginated lazy list
                EinkPaginatedList(
                    modifier = Modifier.fillMaxSize(),
                    totalItemCount = state.stories.size,
                ) {
                    itemsIndexed(
                        items = state.stories,
                        key = { _, story -> story.id },
                    ) { index, story ->
                        StoryCard(
                            story = story,
                            onClick = { onStoryClick(story) },
                        )
                        if (index < state.stories.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                            )
                        }
                        // Load more when near end
                        if (index >= state.stories.size - 5) {
                            LaunchedEffect(state.currentPage) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            } else {
                // Normal: scrollable list with pull-to-refresh
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = state.stories,
                            key = { _, story -> story.id },
                        ) { index, story ->
                            StoryCard(
                                story = story,
                                onClick = { onStoryClick(story) },
                            )

                            if (index < state.stories.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }

                            // Load more when near end
                            if (index >= state.stories.size - 5) {
                                LaunchedEffect(state.currentPage) {
                                    viewModel.loadMore()
                                }
                            }
                        }

                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
