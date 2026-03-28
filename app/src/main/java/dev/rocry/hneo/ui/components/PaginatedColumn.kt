package dev.rocry.hneo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Emits -1 for page up (volume up), +1 for page down (volume down). */
val LocalVolumePageEvents = staticCompositionLocalOf<SharedFlow<Int>> { MutableSharedFlow() }

/** Callback to enable/disable volume key interception from child composables. */
val LocalSetVolumeKeyIntercept = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * A LazyColumn that disables smooth scrolling and instead jumps by a screenful
 * on vertical swipe. Designed for e-ink displays where animation causes ghosting.
 *
 * Use like a normal LazyColumn via the [content] lambda.
 */
@Composable
fun EinkPaginatedList(
    modifier: Modifier = Modifier,
    totalItemCount: Int = 0,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleCount by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) }
    }
    val totalItems by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount.coerceAtLeast(1) }
    }

    // Jump size: overlap by 1 item so the last partially-visible item
    // becomes the first item on the next page (no content skipped).
    val jump by remember { derivedStateOf { (visibleCount - 1).coerceAtLeast(1) } }

    // Volume key page turning
    val volumeEvents = LocalVolumePageEvents.current
    LaunchedEffect(Unit) {
        volumeEvents.collect { direction ->
            val target = if (direction < 0) {
                (firstVisible - jump).coerceAtLeast(0)
            } else {
                (firstVisible + jump).coerceAtMost(totalItems - 1)
            }
            listState.scrollToItem(target)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onVerticalDrag = { _, amount -> dragAccumulator += amount },
                        onDragEnd = {
                            if (abs(dragAccumulator) > 80f) {
                                val target = if (dragAccumulator < 0) {
                                    // swipe up → next page
                                    (firstVisible + jump).coerceAtMost(totalItems - 1)
                                } else {
                                    // swipe down → prev page
                                    (firstVisible - jump).coerceAtLeast(0)
                                }
                                scope.launch { listState.scrollToItem(target) }
                            }
                            dragAccumulator = 0f
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }

        // Page indicator
        if (totalItems > visibleCount) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoRippleTextButton(
                    text = "Prev",
                    enabled = firstVisible > 0,
                    onClick = {
                        scope.launch {
                            listState.scrollToItem((firstVisible - jump).coerceAtLeast(0))
                        }
                    },
                )

                Text(
                    text = "${firstVisible + 1}–${(firstVisible + visibleCount).coerceAtMost(totalItems)} / $totalItems",
                    style = MaterialTheme.typography.bodySmall,
                )

                NoRippleTextButton(
                    text = "Next",
                    enabled = firstVisible + visibleCount < totalItems,
                    onClick = {
                        scope.launch {
                            listState.scrollToItem(
                                (firstVisible + jump).coerceAtMost(totalItems - 1),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NoRippleTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
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
