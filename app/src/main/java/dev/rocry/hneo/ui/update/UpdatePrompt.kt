package dev.rocry.hneo.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.data.update.AppUpdater
import dev.rocry.hneo.data.update.UpdateState

/**
 * The launch-time face of the AppUpdater. A thin adapter: it renders the state
 * machine and calls back into it — it owns no flow of its own.
 */
@Composable
fun UpdatePrompt(updater: AppUpdater) {
    val state by updater.state.collectAsState()

    // Only the download this dialog started gets a modal; a download begun from
    // Settings already reports itself there.
    var launchPromptAccepted by remember { mutableStateOf(false) }

    when (val current = state) {
        is UpdateState.Available -> {
            if (!current.promptOnLaunch) return
            AlertDialog(
                onDismissRequest = { updater.dismissLaunchPrompt() },
                title = { Text("Update Available") },
                text = {
                    Text(
                        text = "${current.release.versionName}\n\n" +
                            current.release.changelog.ifBlank { "No changelog available" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        launchPromptAccepted = true
                        updater.download(current.release)
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { updater.dismissLaunchPrompt() }) { Text("Later") }
                },
            )
        }

        is UpdateState.Downloading -> {
            if (!launchPromptAccepted) return
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading ${current.release.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${(current.progress * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { current.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
            )
        }

        else -> Unit
    }
}
