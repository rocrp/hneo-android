package dev.rocry.hneo.ui.components

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView

private const val MENU_ID_EXPLAIN = 1001

/**
 * Text composable that supports native text selection with a custom "Explain" action.
 * Uses AndroidView + TextView to get proper ActionMode support.
 */
@Composable
fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    onExplain: ((String) -> Unit)? = null,
) {
    val resolvedColor = if (color == Color.Unspecified) {
        LocalContentColor.current
    } else {
        color
    }
    val density = LocalDensity.current
    val currentOnExplain = rememberUpdatedState(onExplain)

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)

                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        if (currentOnExplain.value != null) {
                            menu.add(0, MENU_ID_EXPLAIN, 10, "Explain")
                        }
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        if (item.itemId == MENU_ID_EXPLAIN) {
                            val start = selectionStart
                            val end = selectionEnd
                            if (start >= 0 && end >= 0 && start != end) {
                                val selected = getText().toString().substring(
                                    minOf(start, end),
                                    maxOf(start, end),
                                )
                                currentOnExplain.value?.invoke(selected)
                            }
                            mode.finish()
                            return true
                        }
                        return false
                    }

                    override fun onDestroyActionMode(mode: ActionMode) {}
                }
            }
        },
        update = { tv ->
            tv.text = text
            tv.setTextColor(resolvedColor.toArgb())
            // Convert Compose sp fontSize to Android textSize (in sp)
            tv.textSize = style.fontSize.value
            val lineHeightPx = with(density) { style.lineHeight.toPx() }
            val fontSizePx = with(density) { style.fontSize.toPx() }
            if (lineHeightPx > 0 && fontSizePx > 0) {
                tv.setLineSpacing(lineHeightPx - fontSizePx, 1f)
            }
        },
        modifier = modifier,
    )
}
