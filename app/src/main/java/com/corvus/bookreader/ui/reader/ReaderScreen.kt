package com.corvus.bookreader.ui.reader

import android.annotation.SuppressLint
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Inject content after WebView is ready and content is loaded
    LaunchedEffect(state.content, webView) {
        val wv = webView ?: return@LaunchedEffect
        if (state.content.isEmpty()) return@LaunchedEffect
        val payload = buildPayloadJson(state, uri)
        wv.evaluateJavascript("loadContent($payload)", null)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true   // needed to load reader.css/js from assets
                        allowContentAccess = false
                        setSupportZoom(false)
                        displayZoomControls = false
                        builtInZoomControls = false
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    addJavascriptInterface(
                        ReaderBridge(
                            onSaveProgress = { scrollTop, percent -> vm.saveProgress(scrollTop, percent) },
                            onSavePrefs = { prefs -> vm.savePrefs(prefs) },
                        ),
                        "AndroidBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            webView = view
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
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }
    }
}

private fun buildPayloadJson(state: ReaderUiState, uri: String): String {
    return JSONObject().apply {
        put("text", state.content)
        put("title", state.title)
        put("savedProgress", state.scrollTop)
        put("uriKey", uri)
        put("prefs", JSONObject().apply {
            put("fontSize", state.prefs.fontSize)
            put("lineHeight", state.prefs.lineHeight)
            put("fontFamily", state.prefs.fontFamily)
            put("theme", state.prefs.theme)
        })
    }.toString()
}

class ReaderBridge(
    private val onSaveProgress: (scrollTop: Int, percent: Int) -> Unit,
    private val onSavePrefs: (ReaderPrefs) -> Unit,
) {
    @JavascriptInterface
    fun saveProgress(scrollTop: Int, percent: Int) = onSaveProgress(scrollTop, percent)

    @JavascriptInterface
    fun savePrefs(prefsJson: String) {
        try {
            val obj = JSONObject(prefsJson)
            onSavePrefs(
                ReaderPrefs(
                    fontSize   = obj.optInt("fontSize", 18),
                    lineHeight = obj.optDouble("lineHeight", 2.1).toFloat(),
                    fontFamily = obj.optString("fontFamily", "serif"),
                    theme      = obj.optString("theme", "dark"),
                )
            )
        } catch (_: Exception) {}
    }
}
