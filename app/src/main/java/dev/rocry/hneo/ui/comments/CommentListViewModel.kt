package dev.rocry.hneo.ui.comments

import androidx.lifecycle.ViewModel
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

class CommentListViewModel(
    private val hnClient: HNClient,
    private val commentCache: CommentCache,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentListState())
    val state = _state.asStateFlow()

    private var allComments: List<FlatComment> = emptyList()

    fun init(story: Story) {
        _state.value = CommentListState(story = story, isLoading = true)

        commentCache.get(story.id)?.let { cached ->
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
        val collapsed = if (commentId in current) current - commentId else current + commentId
        _state.value = _state.value.copy(
            collapsedIds = collapsed,
            comments = filterCollapsed(allComments, collapsed),
        )
    }

    private fun filterCollapsed(comments: List<FlatComment>, collapsed: Set<Int>): List<FlatComment> {
        val result = mutableListOf<FlatComment>()
        var skipDepth = Int.MAX_VALUE

        for (comment in comments) {
            if (comment.depth > skipDepth) continue
            skipDepth = Int.MAX_VALUE
            if (comment.id in collapsed) skipDepth = comment.depth
            result += comment
        }
        return result
    }

    private fun loadComments(storyId: Int) {
        viewModelScope.launch {
            try {
                val detail = hnClient.fetchStoryDetail(storyId)
                commentCache.put(detail)
                allComments = flattenComments(detail.comments)
                _state.value = _state.value.copy(
                    storyDetail = detail,
                    comments = filterCollapsed(allComments, _state.value.collapsedIds),
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = if (_state.value.comments.isEmpty()) e.message else null,
                )
            }
        }
    }
}
