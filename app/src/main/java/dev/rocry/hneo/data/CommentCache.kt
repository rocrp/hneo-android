package dev.rocry.hneo.data

import dev.rocry.hneo.model.StoryDetail

class CommentCache(private val maxSize: Int = 20) {
    private val cache = LinkedHashMap<Int, StoryDetail>(maxSize, 0.75f, true)

    @Synchronized
    fun get(id: Int): StoryDetail? = cache[id]

    @Synchronized
    fun put(detail: StoryDetail) {
        cache[detail.id] = detail
        while (cache.size > maxSize) {
            val oldest = cache.keys.first()
            cache.remove(oldest)
        }
    }

    @Synchronized
    fun evictFarFrom(visibleIds: Set<Int>) {
        if (cache.size <= maxSize / 2) return
        val toRemove = cache.keys.filter { it !in visibleIds }
        val removeCount = (cache.size - maxSize / 2).coerceAtMost(toRemove.size)
        toRemove.take(removeCount).forEach { cache.remove(it) }
    }
}
