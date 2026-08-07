package dev.rocry.hneo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.ui.components.einkClickable
import kotlinx.coroutines.delay

private const val COMMIT_DELAY_MS = 500L

@Composable
fun SettingsSection(
    title: String,
    action: @Composable (() -> Unit)? = null,
) {
    if (action == null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        action()
    }
}

/**
 * Tapping the row and flipping the switch are the same action, so the toggle is
 * written once here rather than twice per row at every call site.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .einkClickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A text setting that keeps a draft while typing and commits once the user
 * pauses — a disk write per keystroke is not a save, it is a stutter.
 */
@Composable
fun SettingsTextField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var draft by remember { mutableStateOf(value) }
    var committed by remember { mutableStateOf(value) }

    // Only a change from elsewhere (an import, a reset) overwrites the draft.
    // Comparing against what we last committed — rather than against the current
    // value — keeps the echo of our own write from undoing keystrokes typed
    // while it was in flight.
    LaunchedEffect(value) {
        if (value != committed) {
            committed = value
            draft = value
        }
    }

    LaunchedEffect(draft) {
        if (draft == committed) return@LaunchedEffect
        delay(COMMIT_DELAY_MS)
        committed = draft
        onCommit(draft)
    }

    // Leaving the screen mid-edit is not a reason to lose the edit.
    val pendingDraft by rememberUpdatedState(draft)
    val lastCommitted by rememberUpdatedState(committed)
    val commit by rememberUpdatedState(onCommit)
    DisposableEffect(Unit) {
        onDispose { if (pendingDraft != lastCommitted) commit(pendingDraft) }
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
    )
}
