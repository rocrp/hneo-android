package dev.rocry.hneo.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rocry.hneo.data.StoryRepository
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.model.flattenComments
import dev.rocry.hneo.model.toStory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommentListState(
    val story: Story? = null,
    val comments: List<FlatComment> = emptyList(),
    val collapsedIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class CommentListViewModel(private val repository: StoryRepository) : ViewModel() {

    private val _state = MutableStateFlow(CommentListState())
    val state = _state.asStateFlow()

    private var allComments: List<FlatComment> = emptyList()
    private var storyId: Int? = null

    fun load(storyId: Int) {
        if (this.storyId == storyId) return
        this.storyId = storyId

        val cached = repository.cachedDetail(storyId)
        allComments = cached?.let { flattenComments(it.comments) }.orEmpty()
        _state.value = CommentListState(
            story = cached?.toStory() ?: repository.knownStory(storyId),
            comments = allComments,
            isLoading = cached == null,
        )

        refresh()
    }

    fun refresh() {
        val id = storyId ?: return
        viewModelScope.launch {
            try {
                val detail = repository.fetchDetail(id)
                allComments = flattenComments(detail.comments)
                _state.value = _state.value.copy(
                    story = detail.toStory(),
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
}
