package dev.rocry.hneo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(AppSettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = settingsFlow(context).first()
        loaded = true
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
