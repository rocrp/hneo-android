package dev.rocry.hneo.ui.eink

enum class PageDirection { PREVIOUS, NEXT }

/**
 * Page turning, in one implementation.
 *
 * A page turn advances by a screenful *minus an overlap*, so the content that was
 * half-visible at the bottom is what you read first on the next page. Both the
 * item-indexed adapter (lists) and the pixel-indexed adapter (continuous text and
 * the web view) measure in their own units and share this arithmetic.
 */
object PageArithmetic {
    /** One item of a list, or a tenth of a screen of text, stays on screen across a turn. */
    const val TEXT_OVERLAP_DIVISOR = 10
    private const val LIST_OVERLAP_ITEMS = 1

    /** How far one page turn moves. Never zero — a page turn must always make progress. */
    fun jump(pageSize: Int, overlap: Int): Int = (pageSize - overlap).coerceAtLeast(1)

    fun target(current: Int, pageSize: Int, overlap: Int, max: Int, direction: PageDirection): Int {
        val distance = jump(pageSize, overlap)
        val moved = when (direction) {
            PageDirection.NEXT -> current + distance
            PageDirection.PREVIOUS -> current - distance
        }
        return moved.coerceIn(0, max.coerceAtLeast(0))
    }

    /** Item-indexed paging: [current] is the first visible item. */
    fun listTarget(
        firstVisibleItem: Int,
        visibleItemCount: Int,
        totalItems: Int,
        direction: PageDirection,
    ): Int = target(
        current = firstVisibleItem,
        pageSize = visibleItemCount,
        overlap = LIST_OVERLAP_ITEMS,
        max = totalItems - 1,
        direction = direction,
    )

    /** Pixel-indexed paging: [current] is the scroll offset. */
    fun scrollTarget(
        currentOffset: Int,
        viewportHeight: Int,
        maxOffset: Int,
        direction: PageDirection,
    ): Int = target(
        current = currentOffset,
        pageSize = viewportHeight,
        overlap = viewportHeight / TEXT_OVERLAP_DIVISOR,
        max = maxOffset,
        direction = direction,
    )

    /** How far a pixel-indexed surface scrolls in one turn, signed by [direction]. */
    fun scrollDelta(viewportHeight: Int, direction: PageDirection): Int {
        val distance = jump(viewportHeight, viewportHeight / TEXT_OVERLAP_DIVISOR)
        return if (direction == PageDirection.NEXT) distance else -distance
    }

    /** "3 / 12" — which screenful of the whole you are looking at. */
    fun pageLabel(current: Int, pageSize: Int, overlap: Int, max: Int): String {
        val distance = jump(pageSize, overlap)
        val page = current / distance + 1
        val total = (max / distance + 1).coerceAtLeast(page)
        return "$page / $total"
    }

    fun listPageLabel(firstVisibleItem: Int, visibleItemCount: Int, totalItems: Int): String {
        val last = (firstVisibleItem + visibleItemCount).coerceAtMost(totalItems)
        return "${firstVisibleItem + 1}–$last / $totalItems"
    }

    fun scrollPageLabel(currentOffset: Int, viewportHeight: Int, maxOffset: Int): String =
        pageLabel(
            current = currentOffset,
            pageSize = viewportHeight,
            overlap = viewportHeight / TEXT_OVERLAP_DIVISOR,
            max = maxOffset,
        )
}
