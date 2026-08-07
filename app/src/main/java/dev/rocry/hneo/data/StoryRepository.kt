package dev.rocry.hneo.data

import dev.rocry.hneo.model.FeedKind
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.model.StoryDetail
import dev.rocry.hneo.model.toStory
import java.util.concurrent.ConcurrentHashMap

/**
 * The single home for story data — feeds, story detail, and the comment tree —
 * composing the HN client with the on-disk and in-memory caches.
 *
 * Everything is keyed by story id. Destinations pass ids and read through here,
 * so two screens looking at the same story genuinely see the same story.
 */
class StoryRepository(
    private val hnClient: HNClient,
    private val storyCache: StoryCache,
    private val commentCache: CommentCache,
) {
    /** Every story this session has seen, so a detail screen can render before the network answers. */
    private val storiesById = ConcurrentHashMap<Int, Story>()

    suspend fun cachedStories(feed: FeedKind): List<Story>? =
        storyCache.load(feed)?.also(::remember)

    suspend fun fetchStories(feed: FeedKind, page: Int = 1): List<Story> =
        hnClient.fetchStories(feed, page).also(::remember)

    /** Persists the list the user is actually looking at, pages and all. */
    suspend fun cacheStories(feed: FeedKind, stories: List<Story>) {
        remember(stories)
        storyCache.save(feed, stories)
    }

    fun knownStory(id: Int): Story? = storiesById[id] ?: commentCache.get(id)?.toStory()

    fun cachedDetail(id: Int): StoryDetail? = commentCache.get(id)

    /** The cached detail if there is one, otherwise a fresh fetch. */
    suspend fun detail(id: Int): StoryDetail = cachedDetail(id) ?: fetchDetail(id)

    suspend fun fetchDetail(id: Int): StoryDetail =
        hnClient.fetchStoryDetail(id).also {
            commentCache.put(it)
            storiesById[it.id] = it.toStory()
        }

    private fun remember(stories: List<Story>) {
        stories.forEach { storiesById[it.id] = it }
    }
}
