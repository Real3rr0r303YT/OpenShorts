package org.openshorts.ui.feed

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** TikTok's official embed player for one video. */
fun tikTokEmbedUrl(videoId: String): String = "https://www.tiktok.com/embed/v2/$videoId"

/**
 * Plays a TikTok video in TikTok's official embed player, styled as a full-screen short:
 * - hardware-accelerated WebView, autoplay with sound, looping,
 * - only the `<video>` element is shown (object-fit: cover, black background),
 * - known analytics/ad hosts are blocked, third-party cookies are off,
 * - [onError] fires when the page fails or no playable video appears within a few seconds,
 *   so the feed can skip to the next video.
 *
 * Touches are handled by the feed (an overlay above this view), not by the web page;
 * play/pause is controlled through [playing].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokEmbedView(
    videoId: String,
    playing: Boolean,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
) {
    val currentOnError by rememberUpdatedState(onError)
    val currentOnReady by rememberUpdatedState(onReady)
    // The WebView of this embed, for play/pause from Compose.
    val webView = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true // the embed player is a JavaScript app
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    cacheMode = WebSettings.LOAD_DEFAULT
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webChromeClient = WebChromeClient()
                webViewClient = EmbedClient { currentOnError() }
                tag = EmbedState(videoId)
                loadUrl(tikTokEmbedUrl(videoId))
                startWatchdog(this, onReady = { currentOnReady() }, onError = { currentOnError() })
                webView.value = this
            }
        },
        update = { view ->
            val state = view.tag as EmbedState
            if (state.videoId != videoId) {
                state.videoId = videoId
                view.loadUrl(tikTokEmbedUrl(videoId))
                startWatchdog(view, onReady = { currentOnReady() }, onError = { currentOnError() })
            }
        },
        onRelease = { view ->
            webView.value = null
            (view.tag as? EmbedState)?.released = true
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )

    // Play/pause the page's <video> when the feed toggles playback (harmless before it exists).
    LaunchedEffect(playing, webView.value) {
        webView.value?.evaluateJavascript(if (playing) PLAY_SCRIPT else PAUSE_SCRIPT, null)
    }
}

/** Remembers which video is loaded and whether the view was released. */
private class EmbedState(var videoId: String, var released: Boolean = false, var ready: Boolean = false)

private class EmbedClient(private val onError: () -> Unit) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val host = request?.url?.host.orEmpty()
        return if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) {
            // Analytics/ads are answered with an empty response instead of reaching the network.
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            null
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        // Stay on the embed: links ("Watch on TikTok", profile, …) are not opened inside the feed.
        request?.url?.toString()?.startsWith("https://www.tiktok.com/embed/") != true

    override fun onPageFinished(view: WebView?, url: String?) {
        if (url?.startsWith("https://www.tiktok.com/embed/") == true) view?.evaluateJavascript(FULLSCREEN_SCRIPT, null)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) onError()
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) onError()
    }
}

/**
 * Checks once a second whether the page shows a playable `<video>`; reports an error after
 * [WATCHDOG_SECONDS] (deleted/private videos show an error page with HTTP 200).
 */
private fun startWatchdog(view: WebView, onReady: () -> Unit, onError: () -> Unit) {
    val state = view.tag as EmbedState
    state.ready = false
    val handler = Handler(Looper.getMainLooper())
    val loadedId = state.videoId
    var seconds = 0
    val check = object : Runnable {
        override fun run() {
            if (state.released || state.videoId != loadedId || state.ready) return
            view.evaluateJavascript(READY_SCRIPT) { result ->
                if (state.released || state.videoId != loadedId) return@evaluateJavascript
                if (result == "true") {
                    state.ready = true
                    view.evaluateJavascript(FULLSCREEN_SCRIPT, null)
                    onReady()
                } else if (++seconds >= WATCHDOG_SECONDS) {
                    onError()
                } else {
                    handler.postDelayed(this, 1_000)
                }
            }
        }
    }
    handler.postDelayed(check, 1_000)
}

private const val WATCHDOG_SECONDS = 10

/** Hosts the embed loads only for analytics, monitoring or ads. */
private val BLOCKED_HOSTS = listOf(
    "analytics.tiktok.com",
    "analytics-sg.tiktok.com",
    "mon.tiktokv.com",
    "mon-va.tiktokv.com",
    "mcs.tiktokv.com",
    "mcs-va.tiktokv.com",
    "log.tiktokv.com",
    "ads.tiktok.com",
    "google-analytics.com",
    "googletagmanager.com",
    "doubleclick.net",
)

private const val READY_SCRIPT =
    "(function(){var v=document.querySelector('video');return !!(v&&v.readyState>=2);})()"

private const val PLAY_SCRIPT =
    "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.loop=true;v.play();}})()"

private const val PAUSE_SCRIPT =
    "(function(){var v=document.querySelector('video');if(v){v.pause();}})()"

/** Shows only the video, full screen like a native short; keeps it looping with sound. */
private const val FULLSCREEN_SCRIPT = """
(function(){
  if(!document.getElementById('openshorts-style')){
    var s=document.createElement('style');
    s.id='openshorts-style';
    s.textContent='html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important}'+
      'video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;'+
      'object-fit:cover!important;z-index:2147483646!important;background:#000!important}';
    (document.head||document.documentElement).appendChild(s);
  }
  var v=document.querySelector('video');
  if(v){v.loop=true;v.muted=false;v.playsInline=true;var p=v.play();if(p&&p.catch){p.catch(function(){v.muted=true;v.play();});}}
})()
"""
