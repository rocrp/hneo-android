package dev.rocry.hneo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.rocry.hneo.ui.theme.LocalEinkMode

/** clickable that suppresses the ripple/highlight in e-ink mode. */
@Composable
fun Modifier.einkClickable(onClick: () -> Unit): Modifier {
    val eink = LocalEinkMode.current
    return if (eink) {
        this.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        this.clickable(onClick = onClick)
    }
}
