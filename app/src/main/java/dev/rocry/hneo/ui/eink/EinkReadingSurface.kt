package dev.rocry.hneo.ui.eink

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SWIPE_THRESHOLD_PX = 80f

/**
 * The E-Ink Reading Surface: content rendered as discrete pages, because e-ink
 * ghosts under smooth scrolling.
 *
 * Two adapters over the same paging behaviour — [EinkPagedList] for list content,
 * [EinkPagedText] for continuous text. Both turn pages by button, volume key and
 * swipe, and both share the page chrome below.
 */
@Composable
fun EinkPagedList(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleCount by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) }
    }
    val totalItems by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount.coerceAtLeast(1) }
    }

    fun turn(direction: PageDirection) {
        scope.launch {
            listState.scrollToItem(
                PageArithmetic.listTarget(firstVisible, visibleCount, totalItems, direction),
            )
        }
    }

    PagedSurface(
        modifier = modifier,
        onTurn = ::turn,
        chrome = {
            if (totalItems > visibleCount) {
                PageChrome(
                    label = PageArithmetic.listPageLabel(firstVisible, visibleCount, totalItems),
                    canGoPrevious = firstVisible > 0,
                    canGoNext = firstVisible + visibleCount < totalItems,
                    onTurn = ::turn,
                )
            }
        },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

/**
 * Continuous content — a rendered markdown document, say — paged by pixels.
 *
 * A long document is a *single* composable, so it can only be paged by scroll
 * offset. Feeding it to the list adapter computes a page turn of zero and leaves
 * the reader stuck on page one.
 */
@Composable
fun EinkPagedText(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewportHeight by remember { mutableIntStateOf(0) }

    val offset by remember { derivedStateOf { scrollState.value } }
    val maxOffset by remember { derivedStateOf { scrollState.maxValue } }

    fun turn(direction: PageDirection) {
        if (viewportHeight <= 0) return
        scope.launch {
            scrollState.scrollTo(
                PageArithmetic.scrollTarget(offset, viewportHeight, maxOffset, direction),
            )
        }
    }

    PagedSurface(
        modifier = modifier,
        onTurn = ::turn,
        chrome = {
            if (maxOffset > 0 && viewportHeight > 0) {
                PageChrome(
                    label = PageArithmetic.scrollPageLabel(offset, viewportHeight, maxOffset),
                    canGoPrevious = offset > 0,
                    canGoNext = offset < maxOffset,
                    onTurn = ::turn,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(scrollState, enabled = false),
        ) {
            content()
        }
    }
}

/** Swipe and volume-key transport, plus room for the chrome. Shared by both adapters. */
@Composable
private fun PagedSurface(
    modifier: Modifier,
    onTurn: (PageDirection) -> Unit,
    chrome: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    VolumeKeyPaging(onPage = onTurn)

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onVerticalDrag = { _, amount -> dragAccumulator += amount },
                        onDragEnd = {
                            if (abs(dragAccumulator) > SWIPE_THRESHOLD_PX) {
                                // Swiping up reveals what is below: the next page.
                                onTurn(
                                    if (dragAccumulator < 0) PageDirection.NEXT else PageDirection.PREVIOUS,
                                )
                            }
                            dragAccumulator = 0f
                        },
                    )
                },
            content = content,
        )
        chrome()
    }
}

@Composable
private fun PageChrome(
    label: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onTurn: (PageDirection) -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageButton("Prev", canGoPrevious) { onTurn(PageDirection.PREVIOUS) }
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        PageButton("Next", canGoNext) { onTurn(PageDirection.NEXT) }
    }
}

@Composable
private fun PageButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
