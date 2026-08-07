package dev.rocry.hneo.data

import dev.rocry.hneo.model.StoryDetail

/** Recently-read story detail, least-recently-used first out. */
class CommentCache(private val maxSize: Int = DEFAULT_MAX_SIZE) {
    private val cache = object : LinkedHashMap<Int, StoryDetail>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, StoryDetail>?): Boolean =
            size > maxSize
    }

    @Synchronized
    fun get(id: Int): StoryDetail? = cache[id]

    @Synchronized
    fun put(detail: StoryDetail) {
        cache[detail.id] = detail
    }

    private companion object {
        const val DEFAULT_MAX_SIZE = 20
    }
}
