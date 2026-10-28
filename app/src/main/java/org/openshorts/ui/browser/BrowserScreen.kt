package org.openshorts.ui.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.delay
import org.openshorts.R
import org.openshorts.data.tiktok.videoIdFrom
import org.openshorts.web.AdBlockWebView
import org.openshorts.web.AdBlockWebViewClient
import org.openshorts.web.BROWSER_HOME
import org.openshorts.web.YOUTUBE_SHORTS_WEB
import org.openshorts.web.browserTarget
import org.openshorts.web.isYouTubeUrl
import org.openshorts.web.displayUrl

/**
 * Full in-app browser on top of [AdBlockWebView] (ad/tracker/consent blocking, banner remover,
 * real mobile browser User-Agent): address & search bar, back/forward, reload, pull-to-refresh.
 * Built for browsing TikTok, but any HTTPS site works.
 */
@Composable
fun BrowserScreen(
    startUrl: String,
    onClose: () -> Unit,
    /** Adds the TikTok video currently shown to the TikTok feed. */
    onAddTikTok: (String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // "Home" goes back to where the browser was opened: YouTube Shorts or TikTok.
    val homeUrl = if (isYouTubeUrl(startUrl)) YOUTUBE_SHORTS_WEB else BROWSER_HOME

    var webView by remember { mutableStateOf<AdBlockWebView?>(null) }
    var swipe by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blocked by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(TextFieldValue(displayUrl(startUrl))) }
    // Scroll position inside the page (TikTok scrolls inner containers, not the WebView itself).
    var pageScrollTop by remember { mutableIntStateOf(0) }

    fun navigate(input: String) {
        val target = browserTarget(input) ?: return
        focusManager.clearFocus()
        webView?.loadUrl(target)
    }

    // Back goes back in the page history first, then closes the browser.
    BackHandler {
        val web = webView
        if (web != null && web.canGoBack()) web.goBack() else onClose()
    }

    // Pause JavaScript/media while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keeps the page's inner scroll position up to date for pull-to-refresh.
    LaunchedEffect(webView) {
        val web = webView ?: return@LaunchedEffect
        while (true) {
            web.evaluateJavascript(SCROLL_TOP_SCRIPT) { value -> pageScrollTop = value?.toDoubleOrNull()?.toInt() ?: 0 }
            delay(300)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding(),
    ) {
        // --- Address & search bar -----------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.browser_close))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    if (editing) Icons.Filled.Search else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (address.text.isEmpty()) {
                        Text(
                            stringResource(R.string.browser_address_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate(address.text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                editing = focus.isFocused
                                address = if (focus.isFocused) {
                                    // Full URL, all selected – typing replaces it.
                                    TextFieldValue(currentUrl, selection = TextRange(0, currentUrl.length))
                                } else {
                                    TextFieldValue(displayUrl(currentUrl))
                                }
                            },
                    )
                }
                if (editing && address.text.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { address = TextFieldValue("") },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Ad blocker badge: number of blocked requests on this page.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = stringResource(R.string.browser_blocked, blocked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    blocked.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (loading) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // --- Page with pull-to-refresh ------------------------------------------------------------
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                val web = AdBlockWebView(ctx).apply {
                    webViewClient = object : AdBlockWebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return true
                            return when (url.scheme?.lowercase()) {
                                "https" -> false
                                "http" -> {
                                    view?.loadUrl(url.buildUpon().scheme("https").build().toString())
                                    true
                                }
                                // intent://, tiktok://, market://: stay in the in-app browser.
                                else -> true
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            (view as? AdBlockWebView)?.resetBlockedCount()
                            loading = true
                            url?.let { currentUrl = it }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url) // banner remover
                            view?.evaluateJavascript(SCROLL_TRACKER_SCRIPT, null)
                            loading = false
                            swipe?.isRefreshing = false
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let { currentUrl = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                            if (newProgress >= 100) loading = false
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.deny() // no camera, microphone, …
                        }
                    }
                    onBlockedRequest = { blocked = it }
                    loadUrl(startUrl)
                }
                SwipeRefreshLayout(ctx).apply {
                    addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    setOnRefreshListener { web.reload() }
                    // Only refresh when the page is really scrolled to the top.
                    setOnChildScrollUpCallback { _, _ -> web.canScrollVertically(-1) || pageScrollTop > 0 }
                    webView = web
                    swipe = this
                }
            },
            onRelease = {
                webView?.apply {
                    stopLoading()
                    destroy()
                }
                webView = null
                swipe = null
            },
        )

        // --- Navigation bar -----------------------------------------------------------------------
        Surface(tonalElevation = 3.dp) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 2.dp),
            ) {
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browser_back))
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.browser_forward))
                }
                IconButton(onClick = { if (loading) webView?.stopLoading() else webView?.reload() }) {
                    Icon(
                        if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = stringResource(if (loading) R.string.browser_stop else R.string.browser_reload),
                    )
                }
                IconButton(onClick = { webView?.loadUrl(homeUrl) }) {
                    Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.browser_home))
                }
                // A TikTok video page can be added to the TikTok feed directly.
                IconButton(onClick = { onAddTikTok(currentUrl) }, enabled = videoIdFrom(currentUrl) != null) {
                    Icon(Icons.Filled.AddCircle, contentDescription = stringResource(R.string.browser_add_to_feed))
                }
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                }) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = stringResource(R.string.browser_open_external))
                }
            }
        }
    }
}

/** Remembers the scroll position of whichever element the page scrolls (TikTok uses inner containers). */
private const val SCROLL_TRACKER_SCRIPT = """
(function(){
  if(window.__openshortsScroll){return;}
  window.__openshortsScroll=true;
  window.__openshortsTop=0;
  document.addEventListener('scroll',function(e){
    var t=e.target;
    var top=(t&&t.scrollTop!==undefined)?t.scrollTop:(document.scrollingElement?document.scrollingElement.scrollTop:0);
    window.__openshortsTop=top||0;
  },true);
})()
"""

private const val SCROLL_TOP_SCRIPT =
    "(function(){var a=window.__openshortsTop||0;var b=document.scrollingElement?document.scrollingElement.scrollTop:0;return Math.max(a,b);})()"
