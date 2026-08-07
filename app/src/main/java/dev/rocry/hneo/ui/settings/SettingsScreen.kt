package dev.rocry.hneo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.BuildConfig
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.SettingsStore
import dev.rocry.hneo.data.ThemeMode
import dev.rocry.hneo.data.update.UpdateState
import dev.rocry.hneo.di.LocalAppContainer
import dev.rocry.hneo.ui.components.LoadingIndicator
import dev.rocry.hneo.ui.components.einkClickable
import dev.rocry.hneo.ui.theme.BuiltInFont
import dev.rocry.hneo.ui.theme.FontInfo
import dev.rocry.hneo.ui.theme.FontManager
import kotlinx.coroutines.launch

private val UPDATE_INTERVALS = listOf(
    6 to "6h",
    12 to "12h",
    24 to "1 day",
    72 to "3 days",
    168 to "7 days",
)

private const val MIN_COMMENTS = 10
private const val MAX_COMMENTS = 500

/**
 * An ordinary observer of the settings flow, like every other consumer. It holds
 * no copy of the settings record, so no write can leave the UI showing something
 * the store disagrees with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val settingsStore = container.settings
    val settings by settingsStore.settings.collectAsState(initial = null)

    var availableFonts by remember { mutableStateOf<List<FontInfo>>(emptyList()) }
    LaunchedEffect(Unit) { availableFonts = FontManager.listAvailableFonts(context) }

    // Written on the application scope: a save must not be cancelled by the user
    // navigating away the moment they finish typing.
    fun update(transform: (AppSettings) -> AppSettings) {
        container.applicationScope.launch { settingsStore.update(transform) }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val imported = FontManager.importFont(context, uri) ?: return@rememberLauncherForActivityResult
        availableFonts = FontManager.listAvailableFonts(context)
        update { it.copy(fontChoice = imported.name) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = settings
        if (current == null) {
            LoadingIndicator(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Theme")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        selected = current.themeMode == mode,
                        onClick = { update { it.copy(themeMode = mode) } },
                    ) {
                        Text(mode.label)
                    }
                }
            }

            HorizontalDivider()

            SettingsSection("Font") {
                FilledTonalButton(
                    onClick = { fontPickerLauncher.launch(arrayOf("font/*", "application/octet-stream")) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableFonts.forEach { font ->
                    FontRow(
                        font = font,
                        selected = current.fontChoice == font.name,
                        onSelect = { update { it.copy(fontChoice = font.name) } },
                        onDelete = {
                            FontManager.deleteFont(context, font)
                            availableFonts = FontManager.listAvailableFonts(context)
                            if (current.fontChoice == font.name) {
                                update { it.copy(fontChoice = BuiltInFont.SYSTEM.displayName) }
                            }
                        },
                    )
                }
            }

            Text(
                text = "Import .ttf/.otf font files to use custom fonts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            SettingsSection("Browser")

            SettingsSwitchRow(
                title = "Open links in external browser",
                subtitle = "Use default browser instead of in-app webview",
                checked = current.openLinksInBrowser,
                onCheckedChange = { enabled -> update { it.copy(openLinksInBrowser = enabled) } },
            )

            HorizontalDivider()

            SettingsSection("AI Summary")

            SettingsTextField(
                value = current.llmApiUrl,
                label = "API URL",
                onCommit = { value -> update { it.copy(llmApiUrl = value) } },
            )

            SettingsTextField(
                value = current.llmModel,
                label = "Model",
                onCommit = { value -> update { it.copy(llmModel = value) } },
            )

            SettingsTextField(
                value = current.llmApiKey,
                label = "API Key",
                onCommit = { value -> update { it.copy(llmApiKey = value) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            SettingsTextField(
                value = current.llmMaxComments.toString(),
                label = "Max Comments ($MIN_COMMENTS-$MAX_COMMENTS)",
                onCommit = { value ->
                    value.toIntOrNull()?.coerceIn(MIN_COMMENTS, MAX_COMMENTS)?.let { clamped ->
                        update { it.copy(llmMaxComments = clamped) }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            HorizontalDivider()

            SettingsSection("Prompts")

            SettingsTextField(
                value = current.llmSystemPrompt,
                label = "System Prompt",
                onCommit = { value -> update { it.copy(llmSystemPrompt = value) } },
                singleLine = false,
                minLines = 3,
            )

            SettingsTextField(
                value = current.llmExplainPrompt,
                label = "Explain Prompt",
                onCommit = { value -> update { it.copy(llmExplainPrompt = value) } },
                singleLine = false,
                minLines = 3,
            )

            SettingsTextField(
                value = current.llmWebpageSummaryPrompt,
                label = "Webpage Summary Prompt",
                onCommit = { value -> update { it.copy(llmWebpageSummaryPrompt = value) } },
                singleLine = false,
                minLines = 3,
            )

            HorizontalDivider()

            SettingsSection("About")

            SettingsSwitchRow(
                title = "Auto check for updates",
                subtitle = "Check on app launch at the chosen interval",
                checked = current.autoUpdateEnabled,
                onCheckedChange = { enabled -> update { it.copy(autoUpdateEnabled = enabled) } },
            )

            if (current.autoUpdateEnabled) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    UPDATE_INTERVALS.forEachIndexed { index, (hours, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, UPDATE_INTERVALS.size),
                            selected = current.updateCheckIntervalHours == hours,
                            onClick = { update { it.copy(updateCheckIntervalHours = hours) } },
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            UpdateSection()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FontRow(
    font: FontInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val isCustom = font.path.isNotBlank()
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .einkClickable(onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = font.name, style = MaterialTheme.typography.bodyLarge)
            if (isCustom) {
                Text(
                    text = font.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (isCustom) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete font",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** The settings-screen face of the AppUpdater — the same state machine, no second copy. */
@Composable
private fun UpdateSection() {
    val updater = LocalAppContainer.current.appUpdater
    val scope = rememberCoroutineScope()
    val state by updater.state.collectAsState()

    when (val current = state) {
        is UpdateState.Checking -> LoadingIndicator(caption = "Checking...", compact = true)

        is UpdateState.UpToDate -> {
            Text(
                text = "You're up to date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { scope.launch { updater.checkNow() } }) {
                Text("Check Again")
            }
        }

        is UpdateState.Available -> {
            Text(
                text = "${current.release.versionName} is available",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (current.release.changelog.isNotBlank()) {
                Text(
                    text = current.release.changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { updater.download(current.release) }) {
                Text("Download and install")
            }
        }

        is UpdateState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Downloading... ${(current.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { current.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is UpdateState.Failed -> {
            Text(
                text = current.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            FilledTonalButton(onClick = { scope.launch { updater.checkNow() } }) {
                Text("Retry")
            }
        }

        is UpdateState.Idle -> {
            FilledTonalButton(onClick = { scope.launch { updater.checkNow() } }) {
                Text("Check for Updates")
            }
        }
    }
}
