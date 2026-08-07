package dev.rocry.hneo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.ui.theme.LocalEinkMode

/**
 * "Am I waiting?" — spinner normally, words on e-ink, where an animation ghosts.
 * The choice is made here so no screen has to make it again.
 */
@Composable
fun LoadingIndicator(
    caption: String = "Loading...",
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val einkMode = LocalEinkMode.current

    Box(
        modifier = if (compact) modifier.fillMaxWidth().padding(16.dp) else modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (einkMode) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        if (compact) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            return@Box
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * E-ink has no pull-to-refresh — dragging is a page turn — so it gets a button
 * instead. Renders nothing in normal mode.
 */
@Composable
fun EinkRefreshAction(onRefresh: () -> Unit) {
    if (!LocalEinkMode.current) return
    IconButton(onClick = onRefresh) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
    }
}
