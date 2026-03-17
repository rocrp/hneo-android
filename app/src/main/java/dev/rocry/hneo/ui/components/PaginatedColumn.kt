package dev.rocry.hneo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun <T> PaginatedColumn(
    items: List<T>,
    itemsPerPage: Int = 10,
    modifier: Modifier = Modifier,
    onNearEnd: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val totalPages = if (items.isEmpty()) 1 else ((items.size + itemsPerPage - 1) / itemsPerPage)
    var currentPage by remember { mutableIntStateOf(0) }
    // Reset page when items change significantly
    LaunchedEffect(items.size) {
        if (currentPage >= totalPages) currentPage = (totalPages - 1).coerceAtLeast(0)
    }

    val pageStart = currentPage * itemsPerPage
    val pageEnd = (pageStart + itemsPerPage).coerceAtMost(items.size)
    val pageItems = if (items.isNotEmpty()) items.subList(pageStart, pageEnd) else emptyList()

    // Trigger load more when on last page
    if (currentPage >= totalPages - 1) {
        LaunchedEffect(currentPage) { onNearEnd?.invoke() }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(totalPages, currentPage) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        if (abs(dragAccumulator) > 100f) {
                            if (dragAccumulator < 0 && currentPage < totalPages - 1) {
                                currentPage++
                            } else if (dragAccumulator > 0 && currentPage > 0) {
                                currentPage--
                            }
                        }
                        dragAccumulator = 0f
                    },
                )
            },
    ) {
        if (currentPage == 0 && header != null) {
            header()
        }

        Column(modifier = Modifier.weight(1f)) {
            for (item in pageItems) {
                itemContent(item)
            }
        }

        // Page controls — no ripple (this component is e-ink only)
        if (totalPages > 1) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoRippleIconButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }

                Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = MaterialTheme.typography.bodyMedium,
                )

                NoRippleIconButton(
                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                    enabled = currentPage < totalPages - 1,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
private fun NoRippleIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
