package dev.rocry.hneo.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.model.FlatComment
import dev.rocry.hneo.ui.components.einkClickable
import dev.rocry.hneo.ui.theme.depthColor

@Composable
fun CommentItem(
    comment: FlatComment,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indent = 16.dp + (comment.depth * 12).dp.coerceAtMost(96.dp)
    val barColor = depthColor(comment.depth)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .einkClickable(onClick)
            .padding(start = indent, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (comment.depth > 0) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            // Header
            val header = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(comment.user)
                }
                append("  ·  ")
                append(comment.timeAgo)
                if (isCollapsed && comment.childCount > 0) {
                    append("  [+${comment.childCount}]")
                }
            }

            Text(
                text = header,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isCollapsed) {
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = comment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
