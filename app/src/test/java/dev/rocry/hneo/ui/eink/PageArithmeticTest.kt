package dev.rocry.hneo.ui.eink

import dev.rocry.hneo.ui.eink.PageDirection.NEXT
import dev.rocry.hneo.ui.eink.PageDirection.PREVIOUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageArithmeticTest {

    @Test
    fun `a page turn advances by a screenful minus the overlap`() {
        assertEquals(9, PageArithmetic.jump(pageSize = 10, overlap = 1))
        assertEquals(900, PageArithmetic.jump(pageSize = 1000, overlap = 100))
    }

    @Test
    fun `a page turn always makes progress, however small the page`() {
        // A single visible item, or a viewport smaller than its own overlap,
        // must still move — this is the no-op that made long documents unreadable.
        assertEquals(1, PageArithmetic.jump(pageSize = 1, overlap = 1))
        assertEquals(1, PageArithmetic.jump(pageSize = 0, overlap = 5))
        assertTrue(PageArithmetic.jump(pageSize = -3, overlap = 2) > 0)
    }

    @Test
    fun `forward and back move by the same distance`() {
        val forward = PageArithmetic.target(current = 0, pageSize = 10, overlap = 1, max = 100, direction = NEXT)
        val back = PageArithmetic.target(current = forward, pageSize = 10, overlap = 1, max = 100, direction = PREVIOUS)

        assertEquals(9, forward)
        assertEquals(0, back)
    }

    @Test
    fun `paging never runs past either end`() {
        assertEquals(0, PageArithmetic.target(current = 3, pageSize = 10, overlap = 1, max = 100, direction = PREVIOUS))
        assertEquals(100, PageArithmetic.target(current = 95, pageSize = 10, overlap = 1, max = 100, direction = NEXT))
        assertEquals(0, PageArithmetic.target(current = 0, pageSize = 10, overlap = 1, max = 0, direction = NEXT))
    }

    @Test
    fun `list paging overlaps by one item so nothing is skipped`() {
        val target = PageArithmetic.listTarget(
            firstVisibleItem = 0,
            visibleItemCount = 7,
            totalItems = 42,
            direction = NEXT,
        )

        assertEquals("the 7th item leads the next page", 6, target)
    }

    @Test
    fun `list paging stops at the last item`() {
        assertEquals(
            41,
            PageArithmetic.listTarget(firstVisibleItem = 38, visibleItemCount = 7, totalItems = 42, direction = NEXT),
        )
    }

    @Test
    fun `a single-item list still pages through its content by pixels, not by index`() {
        // A one-item list cannot be paged by index: this is why a long LLM
        // Document rendered as one markdown item could not be scrolled at all.
        assertEquals(
            0,
            PageArithmetic.listTarget(firstVisibleItem = 0, visibleItemCount = 1, totalItems = 1, direction = NEXT),
        )

        // The pixel-indexed adapter is the one that must serve that content.
        assertEquals(
            1800,
            PageArithmetic.scrollTarget(
                currentOffset = 0,
                viewportHeight = 2000,
                maxOffset = 9000,
                direction = NEXT,
            ),
        )
    }

    @Test
    fun `scroll paging keeps a tenth of the screen as overlap`() {
        assertEquals(1800, PageArithmetic.scrollDelta(viewportHeight = 2000, direction = NEXT))
        assertEquals(-1800, PageArithmetic.scrollDelta(viewportHeight = 2000, direction = PREVIOUS))
    }

    @Test
    fun `scroll paging clamps to the end of the document`() {
        assertEquals(
            9000,
            PageArithmetic.scrollTarget(
                currentOffset = 8000,
                viewportHeight = 2000,
                maxOffset = 9000,
                direction = NEXT,
            ),
        )
    }

    @Test
    fun `the list label names the visible range`() {
        assertEquals("1–7 / 42", PageArithmetic.listPageLabel(0, 7, 42))
        assertEquals("39–42 / 42", PageArithmetic.listPageLabel(38, 7, 42))
    }

    @Test
    fun `the scroll label counts pages`() {
        assertEquals("1 / 6", PageArithmetic.scrollPageLabel(0, 2000, 9000))
        assertEquals("2 / 6", PageArithmetic.scrollPageLabel(1800, 2000, 9000))
        assertEquals("6 / 6", PageArithmetic.scrollPageLabel(9000, 2000, 9000))
    }

    @Test
    fun `the scroll label never claims a page beyond the total`() {
        assertEquals("1 / 1", PageArithmetic.scrollPageLabel(0, 2000, 0))
    }
}
