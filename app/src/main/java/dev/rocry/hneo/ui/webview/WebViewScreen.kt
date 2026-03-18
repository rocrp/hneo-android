package dev.rocry.hneo.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.settingsFlow
import dev.rocry.hneo.ui.components.LocalSetVolumeKeyIntercept
import dev.rocry.hneo.ui.components.LocalVolumePageEvents
import dev.rocry.hneo.ui.theme.FontManager
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onClose: () -> Unit,
    onSummary: (pageTitle: String, pageContent: String, pageUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val appSettings by settingsFlow(context).collectAsState(initial = AppSettings())
    val readerFontCss = remember(appSettings.fontChoice) {
        resolveReaderFontCss(appSettings.fontChoice, context)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageFinished by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url) }
    var currentTitle by remember { mutableStateOf("") }
    var readerMode by remember { mutableStateOf(false) }

    // Volume key page scrolling
    val setVolumeIntercept = LocalSetVolumeKeyIntercept.current
    val volumeEvents = LocalVolumePageEvents.current
    DisposableEffect(Unit) {
        setVolumeIntercept(true)
        onDispose { setVolumeIntercept(false) }
    }
    LaunchedEffect(webView) {
        val wv = webView ?: return@LaunchedEffect
        volumeEvents.collect { direction ->
            val scrollJs = if (direction < 0) {
                "window.scrollBy(0, -window.innerHeight * 0.9)"
            } else {
                "window.scrollBy(0, window.innerHeight * 0.9)"
            }
            wv.evaluateJavascript(scrollJs, null)
        }
    }

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
                            readerMode = !readerMode
                            if (readerMode) {
                                webView?.evaluateJavascript(readerModeJs(readerFontCss), null)
                            } else {
                                webView?.reload()
                            }
                        },
                        enabled = pageFinished,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Reader Mode")
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
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser")
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
                        settings.allowFileAccess = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                pageFinished = false
                                readerMode = false
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

private data class ReaderFontCss(val fontFace: String, val fontFamily: String)

private fun resolveReaderFontCss(fontChoice: String, context: android.content.Context): ReaderFontCss {
    return when (fontChoice) {
        "System", "" -> ReaderFontCss("", "system-ui,-apple-system,Roboto,sans-serif")
        "Serif" -> ReaderFontCss("", "Georgia,serif")
        "Monospace" -> ReaderFontCss("", "'Courier New',Courier,monospace")
        else -> {
            val fonts = FontManager.listAvailableFonts(context)
            val fontInfo = fonts.find { it.name == fontChoice }
            if (fontInfo != null && fontInfo.path.isNotBlank()) {
                val file = File(fontInfo.path)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val ext = file.extension.lowercase()
                    val format = if (ext == "otf") "opentype" else "truetype"
                    val fontFace = """@font-face{font-family:"CustomReaderFont";src:url("data:font/$ext;base64,$base64") format("$format")}"""
                    ReaderFontCss(fontFace, """"CustomReaderFont",sans-serif""")
                } else {
                    ReaderFontCss("", "system-ui,sans-serif")
                }
            } else {
                ReaderFontCss("", "system-ui,sans-serif")
            }
        }
    }
}

private fun readerModeJs(fontCss: ReaderFontCss): String {
    val fontFaceRule = fontCss.fontFace.replace("'", "\\'")
    val fontFamilyValue = fontCss.fontFamily.replace("'", "\\'")
    return """
(function() {
    var article = document.querySelector('article') ||
                  document.querySelector('[role="main"]') ||
                  document.querySelector('main') ||
                  document.querySelector('.post-content, .article-content, .entry-content, .content');
    var title = document.title;
    var content = article ? article.innerHTML : document.body.innerHTML;
    var temp = document.createElement('div');
    temp.innerHTML = content;
    var remove = temp.querySelectorAll('script, style, nav, footer, header, aside, iframe, ' +
        '.ad, .ads, .sidebar, .comments, .social, .share, .related, .newsletter, .popup, ' +
        '.modal, .cookie, [role="banner"], [role="navigation"], [role="complementary"]');
    for (var i = 0; i < remove.length; i++) remove[i].remove();
    document.head.innerHTML = '<meta name="viewport" content="width=device-width, initial-scale=1">' +
        '<style>' +
        '$fontFaceRule' +
        'body{max-width:680px;margin:0 auto;padding:20px 16px;font-family:$fontFamilyValue;' +
        'font-size:18px;line-height:1.8;color:#222;background:#fffff8}' +
        'img{max-width:100%;height:auto;border-radius:4px;margin:12px 0}' +
        'h1{font-size:24px;line-height:1.3;margin-bottom:16px}' +
        'a{color:#1a73e8}' +
        'pre,code{font-size:14px;background:#f5f5f5;padding:2px 6px;border-radius:3px;overflow-x:auto}' +
        'pre{padding:12px;margin:12px 0}' +
        'blockquote{border-left:3px solid #ddd;margin:12px 0;padding-left:16px;color:#555}' +
        'p{margin:0 0 16px}' +
        '</style>';
    document.body.innerHTML = '<h1>' + title + '</h1>' + temp.innerHTML;
})()
"""
}
