package ravens.scroll.ui.reader

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.*
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
    uri: String,
    isDrive: Boolean,
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(uri) { vm.load(uri, isDrive) }

    LaunchedEffect(state.loadToken, webView) {
        val wv = webView ?: return@LaunchedEffect
        if (state.loadToken == 0L) return@LaunchedEffect
        val hasContent = if (state.mode == "epub") state.html.isNotEmpty() else state.content.isNotEmpty()
        if (!hasContent) return@LaunchedEffect
        val payload = buildPayloadJson(state, uri)
        Log.d("ReaderScreen", "Injecting content: mode=${state.mode}")
        wv.evaluateJavascript(
            "if(typeof loadContent==='function'){loadContent($payload);}else{window._pendingPayload=$payload;}",
            null
        )
    }

    LaunchedEffect(state.nextBook, webView) {
        val wv = webView ?: return@LaunchedEffect
        val next = state.nextBook ?: return@LaunchedEffect
        val escapedTitle = next.title.replace("\\", "\\\\").replace("'", "\\'")
        val escapedUri = next.uri.replace("\\", "\\\\").replace("'", "\\'")
        wv.evaluateJavascript("showNextBook('$escapedTitle', '$escapedUri')", null)
    }

    // windowInsetsPadding 讓 WebView 從狀態列下方開始，不需要 CSS 補償
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = false
                        setSupportZoom(false)
                        displayZoomControls = false
                        builtInZoomControls = false
                        // 停用 Android 的強制深色 / Algorithmic Darkening（舊 API）
                        @Suppress("DEPRECATION")
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            setForceDark(android.webkit.WebSettings.FORCE_DARK_OFF)
                        }
                    }
                    setBackgroundColor(android.graphics.Color.BLACK)

                    // API 33+ 的正式方式：停用 Algorithmic Darkening
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }

                    addJavascriptInterface(
                        ReaderBridge(
                            onSaveProgress = { scrollTop, percent -> vm.saveProgress(scrollTop, percent) },
                            onSavePrefs = { prefs -> vm.savePrefs(prefs) },
                            onOpenNextBook = { key -> vm.load(key, isDrive) },
                        ),
                        "AndroidBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d("ReaderScreen", "onPageFinished: $url")
                            webView = view
                        }

                        // The reader page only needs local assets (file://) and inlined
                        // data: URIs. EPUB content is untrusted, so block every network
                        // request — the WebView equivalent of the VS Code CSP sandbox.
                        // Prevents any phone-home / tracking via images, styles, etc.
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val scheme = request?.url?.scheme?.lowercase()
                            if (scheme == "http" || scheme == "https") {
                                return WebResourceResponse("text/plain", "utf-8", null)
                            }
                            return null
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            val level = msg.messageLevel()
                            val text = "${msg.message()} — ${msg.sourceId()}:${msg.lineNumber()}"
                            when (level) {
                                ConsoleMessage.MessageLevel.ERROR -> Log.e("ReaderJS", text)
                                ConsoleMessage.MessageLevel.WARNING -> Log.w("ReaderJS", text)
                                else -> Log.d("ReaderJS", text)
                            }
                            return true
                        }
                    }

                    loadUrl("file:///android_asset/reader.html")
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        state.error?.let { err ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBack) { Text("返回書庫") }
                }
            }
        }
    }
}

private fun buildPayloadJson(state: ReaderUiState, uri: String): String {
    return JSONObject().apply {
        put("mode", state.mode)
        put("text", state.content)
        put("html", state.html)
        put("chapters", org.json.JSONArray().apply {
            state.chapters.forEach { ch ->
                put(JSONObject().apply {
                    put("title", ch.title)
                    put("anchor", ch.anchor)
                })
            }
        })
        put("title", state.title)
        put("savedProgress", state.scrollTop)
        put("savedPercent", state.percent)
        put("uriKey", uri)
        put("prefs", JSONObject().apply {
            put("fontSize", state.prefs.fontSize)
            put("lineHeight", state.prefs.lineHeight)
            put("fontFamily", state.prefs.fontFamily)
            put("theme", state.prefs.theme)
        })
        state.nextBook?.let { next ->
            put("nextBook", JSONObject().apply {
                put("title", next.title)
                put("uri", next.uri)
            })
        }
    }.toString()
}

class ReaderBridge(
    private val onSaveProgress: (scrollTop: Int, percent: Int) -> Unit,
    private val onSavePrefs: (ReaderPrefs) -> Unit,
    private val onOpenNextBook: (uriKey: String) -> Unit,
) {
    @JavascriptInterface
    fun saveProgress(scrollTop: Int, percent: Int) = onSaveProgress(scrollTop, percent)

    @JavascriptInterface
    fun savePrefs(prefsJson: String) {
        try {
            val obj = JSONObject(prefsJson)
            onSavePrefs(
                ReaderPrefs(
                    fontSize   = obj.optInt("fontSize", 14),
                    lineHeight = obj.optDouble("lineHeight", 1.3).toFloat(),
                    fontFamily = obj.optString("fontFamily", "lxgw"),
                    theme      = obj.optString("theme", "dark"),
                )
            )
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun openNextBook(uriKey: String) = onOpenNextBook(uriKey)
}
