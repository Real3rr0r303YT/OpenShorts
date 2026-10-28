package org.openshorts.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** Which real mobile browser the WebView identifies as. */
enum class BrowserIdentity { CHROME_MOBILE, FIREFOX_MOBILE }

/**
 * A WebView that behaves like a privacy-focused mobile browser with an ad blocker:
 * - drops ad, tracker and cookie-consent requests ([AdBlocker]) in [AdBlockWebViewClient],
 * - hides remaining cookie banners and ad overlays after every page load (CSS + MutationObserver),
 * - sends a real mobile browser User-Agent (the device's Chrome version without the "wv" WebView marker),
 * - hardware accelerated, no file/content access, no geolocation, no pop-up windows,
 *   no third-party cookies.
 *
 * Set a client derived from [AdBlockWebViewClient] to keep the blocking when customizing behaviour.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
open class AdBlockWebView(
    context: Context,
    identity: BrowserIdentity = BrowserIdentity.CHROME_MOBILE,
) : WebView(context) {

    /** Number of requests blocked on the current page (for debugging / a counter in the UI). */
    var blockedRequests: Int = 0
        internal set

    /** Called on the main thread whenever a request was blocked (with the new page total). */
    var onBlockedRequest: ((Int) -> Unit)? = null

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.BLACK)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = true // TikTok/YouTube players are JavaScript apps
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
            userAgentString = userAgentFor(context, identity)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = WebChromeClient()
        webViewClient = AdBlockWebViewClient()
    }

    override fun loadUrl(url: String) {
        resetBlockedCount()
        super.loadUrl(url)
    }

    /** Starts counting blocked requests from zero (new page). */
    fun resetBlockedCount() {
        blockedRequests = 0
        onBlockedRequest?.invoke(0)
    }

    companion object {
        /**
         * A real mobile browser User-Agent. For Chrome, the device's own WebView UA is used with the
         * WebView markers removed, so the Chrome version always matches the actual engine.
         */
        fun userAgentFor(context: Context, identity: BrowserIdentity): String = when (identity) {
            BrowserIdentity.CHROME_MOBILE -> chromeUserAgent(WebSettings.getDefaultUserAgent(context))
            BrowserIdentity.FIREFOX_MOBILE ->
                "Mozilla/5.0 (Android 14; Mobile; rv:$FIREFOX_VERSION) Gecko/$FIREFOX_VERSION Firefox/$FIREFOX_VERSION"
        }

        private const val FIREFOX_VERSION = "131.0"
    }
}

/** "…; wv) … Version/4.0 Chrome/130…" → "…) … Chrome/130…" (looks like the Chrome app). */
fun chromeUserAgent(webViewUserAgent: String): String =
    webViewUserAgent
        .replace("; wv)", ")")
        .replace(Regex("""Version/\d+(\.\d+)* """), "")

/**
 * Blocks requests via [AdBlocker] and injects the banner/overlay hider after each page load.
 * Subclasses must call `super` in [onPageFinished] and [shouldInterceptRequest] to keep blocking.
 */
open class AdBlockWebViewClient : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (!AdBlocker.shouldBlock(url)) return null
        (view as? AdBlockWebView)?.let { web ->
            web.post {
                web.blockedRequests++
                web.onBlockedRequest?.invoke(web.blockedRequests)
            }
        }
        // An empty "204 No Content" instead of the ad/tracker/consent script.
        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(BANNER_REMOVER_SCRIPT, null)
    }
}

/**
 * Hides cookie banners and ad overlays (display:none) – also ones added later – and releases the
 * scroll lock such dialogs put on the page. Nothing is clicked, so no consent is given.
 */
val BANNER_REMOVER_SCRIPT: String = run {
    val selectors = CONSENT_AND_AD_SELECTORS.joinToString(",").replace("'", "\\'")
    """
    (function(){
      var sel='$selectors';
      function hide(root){
        try{
          root.querySelectorAll(sel).forEach(function(e){e.style.setProperty('display','none','important');});
          root.querySelectorAll('*').forEach(function(e){ if(e.shadowRoot){ hide(e.shadowRoot); } });
        }catch(err){}
      }
      if(!document.getElementById('openshorts-adblock')){
        var s=document.createElement('style');
        s.id='openshorts-adblock';
        s.textContent=sel+'{display:none!important}';
        (document.head||document.documentElement).appendChild(s);
      }
      hide(document);
      ['modal-open','no-scroll','overflow-hidden'].forEach(function(c){document.documentElement.classList.remove(c);if(document.body){document.body.classList.remove(c);}});
      if(!window.__openshortsObserver){
        var pending=false;
        window.__openshortsObserver=new MutationObserver(function(){
          if(pending){return;}
          pending=true;
          setTimeout(function(){pending=false;hide(document);},500);
        });
        window.__openshortsObserver.observe(document.documentElement,{childList:true,subtree:true});
      }
    })();
    """.trimIndent()
}

/** Compose wrapper: an [AdBlockWebView] showing [url]. */
@Composable
fun AdBlockBrowser(url: String, modifier: Modifier = Modifier, identity: BrowserIdentity = BrowserIdentity.CHROME_MOBILE) {
    AndroidView(
        modifier = modifier,
        factory = { context -> AdBlockWebView(context, identity).apply { loadUrl(url) } },
        update = { view -> if (view.url != url && view.originalUrl != url) view.loadUrl(url) },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
    )
}
