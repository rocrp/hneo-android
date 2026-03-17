package dev.rocry.hneo.ui.comments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.CommentCache
import dev.rocry.hneo.data.HNClient
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.model.StoryDetail
import dev.rocry.hneo.model.flattenComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommentListState(
    val story: Story? = null,
    val storyDetail: StoryDetail? = null,
    val comments: List<FlatComment> = emptyList(),
    val collapsedIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class CommentListViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(CommentListState())
    val state = _state.asStateFlow()

    private var commentCache: CommentCache? = null
    private var allComments: List<FlatComment> = emptyList()

    fun init(story: Story, cache: CommentCache) {
        commentCache = cache
        _state.value = CommentListState(story = story, isLoading = true)

        // Check cache first
        val cached = cache.get(story.id)
        if (cached != null) {
            allComments = flattenComments(cached.comments)
            _state.value = _state.value.copy(
                storyDetail = cached,
                comments = allComments,
                isLoading = false,
            )
        }

        loadComments(story.id)
    }

    fun toggleCollapse(commentId: Int) {
        val current = _state.value.collapsedIds
        val newCollapsed = if (commentId in current) current - commentId else current + commentId
        _state.value = _state.value.copy(
            collapsedIds = newCollapsed,
            comments = filterCollapsed(allComments, newCollapsed),
        )
    }

    private fun filterCollapsed(comments: List<FlatComment>, collapsed: Set<Int>): List<FlatComment> {
        val result = mutableListOf<FlatComment>()
        var skipDepth = Int.MAX_VALUE

        for (c in comments) {
            if (c.depth > skipDepth) continue
            skipDepth = Int.MAX_VALUE

            if (c.id in collapsed) {
                skipDepth = c.depth
            }
            result += c
        }
        return result
    }

    private fun loadComments(storyId: Int) {
        viewModelScope.launch {
            try {
                val detail = HNClient.fetchStoryDetail(storyId)
                commentCache?.put(detail)
                allComments = flattenComments(detail.comments)
                _state.value = _state.value.copy(
                    storyDetail = detail,
                    comments = filterCollapsed(allComments, _state.value.collapsedIds),
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                if (_state.value.comments.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                } else {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }
}
