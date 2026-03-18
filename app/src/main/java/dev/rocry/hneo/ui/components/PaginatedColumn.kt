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
import kotlinx.coroutines.launch
import kotlin.math.abs

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
                                    (firstVisible + visibleCount).coerceAtMost(totalItems - 1)
                                } else {
                                    // swipe down → prev page
                                    (firstVisible - visibleCount).coerceAtLeast(0)
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
                            listState.scrollToItem((firstVisible - visibleCount).coerceAtLeast(0))
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
                                (firstVisible + visibleCount).coerceAtMost(totalItems - 1),
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
