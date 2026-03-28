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
import dev.rocry.hneo.data.*
import dev.rocry.hneo.ui.components.einkClickable
import dev.rocry.hneo.ui.theme.FontInfo
import dev.rocry.hneo.ui.theme.FontManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(AppSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var availableFonts by remember { mutableStateOf<List<FontInfo>>(emptyList()) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val imported = FontManager.importFont(context, uri) ?: return@rememberLauncherForActivityResult
        availableFonts = FontManager.listAvailableFonts(context)
        // Auto-select the imported font
        settings = settings.copy(fontChoice = imported.name)
        scope.launch { updateSetting(context, SettingsKeys.FONT_CHOICE, imported.name) }
    }

    LaunchedEffect(Unit) {
        settings = settingsFlow(context).first()
        loaded = true
        availableFonts = FontManager.listAvailableFonts(context)
    }

    if (!loaded) return

    fun save(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        scope.launch { updateSetting(context, key, value) }
    }

    fun saveInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        scope.launch { updateSetting(context, key, value) }
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Theme section
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        selected = settings.themeMode == mode,
                        onClick = {
                            settings = settings.copy(themeMode = mode)
                            save(SettingsKeys.THEME_MODE, mode.name)
                        },
                    ) {
                        Text(mode.label)
                    }
                }
            }

            HorizontalDivider()

            // Font section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Font",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                FilledTonalButton(
                    onClick = {
                        fontPickerLauncher.launch(arrayOf("font/*", "application/octet-stream"))
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableFonts.forEach { font ->
                    val selected = settings.fontChoice == font.name
                    val isCustom = font.path.isNotBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (selected) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp),
                                    )
                                },
                            )
                            .einkClickable {
                                settings = settings.copy(fontChoice = font.name)
                                save(SettingsKeys.FONT_CHOICE, font.name)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = font.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
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
                                IconButton(
                                    onClick = {
                                        FontManager.deleteFont(context, font)
                                        availableFonts = FontManager.listAvailableFonts(context)
                                        if (settings.fontChoice == font.name) {
                                            settings = settings.copy(fontChoice = "System")
                                            save(SettingsKeys.FONT_CHOICE, "System")
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
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
            }

            Text(
                text = "Import .ttf/.otf font files to use custom fonts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // Browser section
            Text(
                text = "Browser",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .einkClickable {
                        val newValue = !settings.openLinksInBrowser
                        settings = settings.copy(openLinksInBrowser = newValue)
                        scope.launch { updateSetting(context, SettingsKeys.OPEN_LINKS_IN_BROWSER, newValue) }
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open links in external browser",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Use default browser instead of in-app webview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.openLinksInBrowser,
                    onCheckedChange = { newValue ->
                        settings = settings.copy(openLinksInBrowser = newValue)
                        scope.launch { updateSetting(context, SettingsKeys.OPEN_LINKS_IN_BROWSER, newValue) }
                    },
                )
            }

            HorizontalDivider()

            // AI section
            Text(
                text = "AI Summary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = settings.llmApiUrl,
                onValueChange = {
                    settings = settings.copy(llmApiUrl = it)
                    save(SettingsKeys.LLM_API_URL, it)
                },
                label = { Text("API URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = settings.llmModel,
                onValueChange = {
                    settings = settings.copy(llmModel = it)
                    save(SettingsKeys.LLM_MODEL, it)
                },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = settings.llmApiKey,
                onValueChange = {
                    settings = settings.copy(llmApiKey = it)
                    save(SettingsKeys.LLM_API_KEY, it)
                },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            OutlinedTextField(
                value = settings.llmMaxComments.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { v ->
                        val clamped = v.coerceIn(10, 500)
                        settings = settings.copy(llmMaxComments = clamped)
                        saveInt(SettingsKeys.LLM_MAX_COMMENTS, clamped)
                    }
                },
                label = { Text("Max Comments (10-500)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            HorizontalDivider()

            Text(
                text = "Prompts",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = settings.llmSystemPrompt,
                onValueChange = {
                    settings = settings.copy(llmSystemPrompt = it)
                    save(SettingsKeys.LLM_SYSTEM_PROMPT, it)
                },
                label = { Text("System Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            OutlinedTextField(
                value = settings.llmExplainPrompt,
                onValueChange = {
                    settings = settings.copy(llmExplainPrompt = it)
                    save(SettingsKeys.LLM_EXPLAIN_PROMPT, it)
                },
                label = { Text("Explain Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            OutlinedTextField(
                value = settings.llmWebpageSummaryPrompt,
                onValueChange = {
                    settings = settings.copy(llmWebpageSummaryPrompt = it)
                    save(SettingsKeys.LLM_WEBPAGE_SUMMARY_PROMPT, it)
                },
                label = { Text("Webpage Summary Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            HorizontalDivider()

            // About section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .einkClickable {
                        val newValue = !settings.autoUpdateEnabled
                        settings = settings.copy(autoUpdateEnabled = newValue)
                        scope.launch { updateSetting(context, SettingsKeys.AUTO_UPDATE_ENABLED, newValue) }
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto check for updates",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Check on app launch at the chosen interval",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.autoUpdateEnabled,
                    onCheckedChange = { newValue ->
                        settings = settings.copy(autoUpdateEnabled = newValue)
                        scope.launch { updateSetting(context, SettingsKeys.AUTO_UPDATE_ENABLED, newValue) }
                    },
                )
            }

            if (settings.autoUpdateEnabled) {
                val intervalOptions = listOf(6, 12, 24, 72, 168) // hours
                val intervalLabels = listOf("6h", "12h", "1 day", "3 days", "7 days")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    intervalOptions.forEachIndexed { index, hours ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, intervalOptions.size),
                            selected = settings.updateCheckIntervalHours == hours,
                            onClick = {
                                settings = settings.copy(updateCheckIntervalHours = hours)
                                saveInt(SettingsKeys.UPDATE_CHECK_INTERVAL_HOURS, hours)
                            },
                        ) {
                            Text(intervalLabels[index], style = MaterialTheme.typography.labelSmall)
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

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object NoUpdate : UpdateState
    data class Available(val release: UpdateService.ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var showDialog by remember { mutableStateOf(false) }

    when (val s = state) {
        is UpdateState.Idle -> {
            FilledTonalButton(
                onClick = {
                    state = UpdateState.Checking
                    scope.launch {
                        state = try {
                            val release = UpdateService.checkForUpdate(BuildConfig.VERSION_CODE)
                            if (release != null) {
                                showDialog = true
                                UpdateState.Available(release)
                            } else {
                                UpdateState.NoUpdate
                            }
                        } catch (e: Exception) {
                            UpdateState.Error(e.message ?: "Unknown error")
                        }
                    }
                },
            ) {
                Text("Check for Updates")
            }
        }

        is UpdateState.Checking -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Checking...", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is UpdateState.NoUpdate -> {
            Text(
                text = "You're up to date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { state = UpdateState.Idle }) {
                Text("Check Again")
            }
        }

        is UpdateState.Available -> {
            FilledTonalButton(onClick = { showDialog = true }) {
                Text("Update available: ${s.release.versionName}")
            }
        }

        is UpdateState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Downloading... ${(s.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is UpdateState.Error -> {
            Text(
                text = s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            FilledTonalButton(onClick = { state = UpdateState.Idle }) {
                Text("Retry")
            }
        }
    }

    if (showDialog && state is UpdateState.Available) {
        val release = (state as UpdateState.Available).release
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(release.versionName) },
            text = {
                Text(
                    text = release.changelog.ifBlank { "No changelog available" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    state = UpdateState.Downloading(0f)
                    scope.launch {
                        try {
                            val file = UpdateService.downloadApk(
                                context = context,
                                url = release.downloadUrl,
                                fileName = "hneo-${release.versionName}.apk",
                                onProgress = { state = UpdateState.Downloading(it) },
                            )
                            UpdateService.installApk(context, file)
                            state = UpdateState.Idle
                        } catch (e: Exception) {
                            state = UpdateState.Error("Download failed: ${e.message}")
                        }
                    }
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}


