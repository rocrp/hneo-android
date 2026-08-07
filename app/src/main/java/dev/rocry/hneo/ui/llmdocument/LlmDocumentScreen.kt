package dev.rocry.hneo.ui.llmdocument

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import dev.rocry.hneo.di.LocalAppContainer
import dev.rocry.hneo.ui.components.LoadingIndicator
import dev.rocry.hneo.ui.eink.EinkPagedText
import dev.rocry.hneo.ui.theme.LocalEinkMode
import kotlinx.coroutines.launch

/**
 * The one screen for every LLM Document. What used to be three near-identical
 * screens that had to be patched three times per feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmDocumentScreen(
    viewModel: LlmDocumentViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val einkMode = LocalEinkMode.current
    val pasteService = LocalAppContainer.current.pasteService

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.kind.title)
                        if (state.model.isNotBlank()) {
                            Text(
                                text = state.model + if (state.isCached) " (cached)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(state.kind.title, viewModel.copyableText()),
                            )
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        enabled = state.hasContent,
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val url = pasteService.createPaste(viewModel.copyableText())
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, url)
                                            },
                                            "Share ${state.kind.title}",
                                        ),
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Share failed: ${e.message}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        enabled = state.hasContent && !state.isStreaming,
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.isStreaming,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val error = state.error
            when {
                error != null && !state.hasContent -> DocumentError(
                    message = error,
                    onRetry = { viewModel.refresh() },
                )

                !state.hasContent && state.isStreaming ->
                    LoadingIndicator(caption = state.kind.loadingCaption)

                else -> DocumentBody(
                    text = state.text,
                    isStreaming = state.isStreaming,
                    einkMode = einkMode,
                )
            }
        }
    }
}

@Composable
private fun DocumentError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun BoxScope.DocumentBody(text: String, isStreaming: Boolean, einkMode: Boolean) {
    if (einkMode) {
        // Continuous text, paged by pixels. Handing this to the list adapter as a
        // single item computed a page turn of zero: the document could not be read.
        EinkPagedText(modifier = Modifier.fillMaxSize()) {
            Markdown(
                content = text,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        return
    }

    Markdown(
        content = text,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    )

    if (isStreaming) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        )
    }
}
