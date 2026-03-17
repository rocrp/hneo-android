package dev.rocry.hneo.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    var hasStoragePermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasStoragePermission = granted
        if (granted) availableFonts = FontManager.listAvailableFonts()
    }

    LaunchedEffect(Unit) {
        settings = settingsFlow(context).first()
        loaded = true
        availableFonts = FontManager.listAvailableFonts()
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
            Text(
                text = "Font",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (!hasStoragePermission && Build.VERSION.SDK_INT < 33) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant storage access to load custom fonts from /sdcard/Fonts/")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableFonts.forEach { font ->
                    val selected = settings.fontChoice == font.name
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
                        Column {
                            Text(
                                text = font.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (font.path.isNotBlank()) {
                                Text(
                                    text = font.path.substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (availableFonts.none { it.path.isNotBlank() }) {
                Text(
                    text = "Place .ttf/.otf files in /sdcard/Fonts/ to add custom fonts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
