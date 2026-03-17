package dev.rocry.hneo.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onClose: () -> Unit,
    onSummary: (pageTitle: String, pageContent: String, pageUrl: String) -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageFinished by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url) }
    var currentTitle by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(
                        onClick = {
                            webView?.evaluateJavascript("document.body.innerText") { result ->
                                val text = result
                                    ?.removeSurrounding("\"")
                                    ?.replace("\\n", "\n")
                                    ?.replace("\\t", "\t")
                                    ?.replace("\\\"", "\"")
                                    ?.trim()
                                if (!text.isNullOrBlank()) {
                                    onSummary(currentTitle, text, currentUrl)
                                }
                            }
                        },
                        enabled = pageFinished,
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$currentTitle\n$currentUrl")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (progress < 1f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                pageFinished = false
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                pageFinished = true
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                currentUrl = url
                                currentTitle = view.title ?: ""
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = false
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }

                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
