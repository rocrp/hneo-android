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
import dev.rocry.hneo.data.*
import dev.rocry.hneo.ui.components.einkClickable
import dev.rocry.hneo.ui.theme.FontInfo
import dev.rocry.hneo.ui.theme.FontManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
