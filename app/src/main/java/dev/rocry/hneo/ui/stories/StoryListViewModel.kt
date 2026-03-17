package dev.rocry.hneo.ui.stories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

class StoryListViewModel(application: Application) : AndroidViewModel(application) {
    private val storyCache = StoryCache(application)
    val commentCache = CommentCache()

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
        val s = _state.value
        if (s.isLoadingMore || !s.canLoadMore) return
        _state.value = s.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val nextPage = s.currentPage + 1
                val more = HNClient.fetchStories(s.currentFeed, nextPage)
                if (more.isEmpty()) {
                    _state.value = _state.value.copy(isLoadingMore = false, canLoadMore = false)
                } else {
                    val combined = _state.value.stories + more
                    _state.value = _state.value.copy(
                        stories = combined,
                        currentPage = nextPage,
                        isLoadingMore = false,
                    )
                    storyCache.save(s.currentFeed, combined)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun prefetchComments(visibleStoryIds: List<Int>) {
        viewModelScope.launch {
            val toPrefetch = visibleStoryIds
                .filter { commentCache.get(it) == null }
                .take(5)
            for (id in toPrefetch) {
                try {
                    val detail = HNClient.fetchStoryDetail(id)
                    commentCache.put(detail)
                } catch (_: Exception) {
                    // prefetch failure is non-critical
                }
            }
        }
    }

    private fun loadStories() {
        viewModelScope.launch {
            val feed = _state.value.currentFeed
            // Show cached first
            val cached = storyCache.load(feed)
            if (cached != null && _state.value.stories.isEmpty()) {
                _state.value = _state.value.copy(stories = cached, isLoading = true)
            }
            try {
                val stories = HNClient.fetchStories(feed, 1)
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
}
