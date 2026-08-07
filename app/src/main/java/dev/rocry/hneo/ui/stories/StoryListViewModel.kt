package dev.rocry.hneo.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.CommentCache
import dev.rocry.hneo.data.HNClient
import dev.rocry.hneo.data.StoryCache
import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoryListState(
    val stories: List<Story> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentFeed: FeedKind = FeedKind.TOP,
    val currentPage: Int = 1,
    val error: String? = null,
    val canLoadMore: Boolean = true,
)

class StoryListViewModel(
    private val hnClient: HNClient,
    private val storyCache: StoryCache,
    private val commentCache: CommentCache,
) : ViewModel() {

    private val _state = MutableStateFlow(StoryListState())
    val state = _state.asStateFlow()

    init {
        loadStories()
    }

    fun switchFeed(feed: FeedKind) {
        if (feed == _state.value.currentFeed) return
        _state.value = StoryListState(currentFeed = feed, isLoading = true)
        loadStories()
    }

    fun refresh() {
        _state.value = _state.value.copy(currentPage = 1, isLoading = true, error = null, canLoadMore = true)
        loadStories()
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isLoadingMore || !snapshot.canLoadMore) return
        _state.value = snapshot.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val nextPage = snapshot.currentPage + 1
                val more = hnClient.fetchStories(snapshot.currentFeed, nextPage)
                if (more.isEmpty()) {
                    _state.value = _state.value.copy(isLoadingMore = false, canLoadMore = false)
                } else {
                    val combined = _state.value.stories + more
                    _state.value = _state.value.copy(
                        stories = combined,
                        currentPage = nextPage,
                        isLoadingMore = false,
                    )
                    storyCache.save(snapshot.currentFeed, combined)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun prefetchComments(visibleStoryIds: List<Int>) {
        viewModelScope.launch {
            val toPrefetch = visibleStoryIds
                .filter { commentCache.get(it) == null }
                .take(PREFETCH_LIMIT)
            for (id in toPrefetch) {
                try {
                    commentCache.put(hnClient.fetchStoryDetail(id))
                } catch (_: Exception) {
                    // prefetch failure is non-critical
                }
            }
        }
    }

    private fun loadStories() {
        viewModelScope.launch {
            val feed = _state.value.currentFeed
            val cached = storyCache.load(feed)
            if (cached != null && _state.value.stories.isEmpty()) {
                _state.value = _state.value.copy(stories = cached, isLoading = true)
            }
            try {
                val stories = hnClient.fetchStories(feed, 1)
                _state.value = _state.value.copy(
                    stories = stories,
                    isLoading = false,
                    currentPage = 1,
                    error = null,
                )
                storyCache.save(feed, stories)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = if (_state.value.stories.isEmpty()) e.message else null,
                )
            }
        }
    }

    private companion object {
        const val PREFETCH_LIMIT = 5
    }
}
