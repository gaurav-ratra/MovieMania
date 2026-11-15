package app.sitewrap

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Full-screen WebView with:
 *  - user-agent "emulation" (Mobile / Desktop / Smart TV)
 *  - optional on-screen cursor driven by the D-pad (for sites that need a mouse, on a TV remote)
 *  - long-press BACK or MENU = options (reload, cursor on/off, switch mode, close)
 */
class SiteActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var dot: Dot
    private var fullscreenView: View? = null
    private var fullscreenCb: WebChromeClient.CustomViewCallback? = null
    private var mode = 0
    private var cursorOn = false
    private var cx = 0f
    private var cy = 0f

    private inner class Dot(c: Context) : View(c) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
        override fun onDraw(c: Canvas) { c.drawCircle(cx, cy, 14f, fill); c.drawCircle(cx, cy, 14f, edge) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mode = intent.getIntExtra("mode", 0)
        cursorOn = intent.getBooleanExtra("cursor", false)

        web = WebView(this)
        dot = Dot(this).apply { visibility = if (cursorOn) View.VISIBLE else View.GONE }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(web, MATCH_PARENT, MATCH_PARENT)
            addView(dot, MATCH_PARENT, MATCH_PARENT)
            post { cx = width / 2f; cy = height / 2f; dot.invalidate() }
        }
        setContentView(root)

        val defaultUa = web.settings.userAgentString
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        }
        web.tag = defaultUa // keep the stock UA so we can switch modes later
        applyMode()
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            // stay on http(s); block intent://, market:// etc. that would jump out of the app
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) =
                r.url.scheme != "http" && r.url.scheme != "https"
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                fullscreenView = v; fullscreenCb = cb
                web.visibility = View.GONE
                root.addView(v, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
            override fun onHideCustomView() = hideFullscreen()
        }
        if (b == null) web.loadUrl(intent.getStringExtra("url") ?: "about:blank") else web.restoreState(b)
    }

    private fun hideFullscreen() {
        fullscreenView?.let { root.removeView(it) }
        fullscreenView = null
        fullscreenCb?.onCustomViewHidden(); fullscreenCb = null
        web.visibility = View.VISIBLE
    }

    private fun applyMode() {
        web.settings.userAgentString = userAgent(mode, web.tag as String)
    }

    // ---- keys -------------------------------------------------------------------------------

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (cursorOn) {
            val (dx, dy) = when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
                KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
                KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
                else -> 0 to 0
            }
            if (dx != 0 || dy != 0) { if (e.action == KeyEvent.ACTION_DOWN) moveCursor(dx, dy, e.repeatCount); return true }
            if (e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (e.repeatCount == 0) tap(e.action == KeyEvent.ACTION_DOWN)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun moveCursor(dx: Int, dy: Int, repeat: Int) {
        val step = (10 + minOf(repeat, 40) * 2) * resources.displayMetrics.density // accelerates while held
        val nx = (cx + dx * step).coerceIn(0f, root.width - 1f)
        val ny = (cy + dy * step).coerceIn(0f, root.height - 1f)
        if (nx == cx && dx != 0 || ny == cy && dy != 0) web.scrollBy((dx * step * 2).toInt(), (dy * step * 2).toInt()) // pinned to an edge => scroll
        cx = nx; cy = ny
        dot.invalidate()
    }

    private fun tap(down: Boolean) {
        val t = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(t, t, if (down) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP, cx, cy, 0)
        web.dispatchTouchEvent(ev); ev.recycle()
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean = when (k) {
        KeyEvent.KEYCODE_BACK -> { e.startTracking(); true }
        KeyEvent.KEYCODE_MENU -> { options(); true }
        else -> super.onKeyDown(k, e)
    }

    override fun onKeyLongPress(k: Int, e: KeyEvent): Boolean =
        if (k == KeyEvent.KEYCODE_BACK) { options(); true } else super.onKeyLongPress(k, e)

    override fun onKeyUp(k: Int, e: KeyEvent): Boolean {
        if (k == KeyEvent.KEYCODE_BACK && e.isTracking && !e.isCanceled) {
            when {
                fullscreenView != null -> hideFullscreen()
                web.canGoBack() -> web.goBack()
                else -> finish()
            }
            return true
        }
        return super.onKeyUp(k, e)
    }

    private fun options() {
        val items = arrayOf("Reload", "Remote cursor: ${if (cursorOn) "ON" else "OFF"}", "Mode: ${MODES[mode]}  (tap to switch)", "Close site")
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> web.reload()
                1 -> { cursorOn = !cursorOn; dot.visibility = if (cursorOn) View.VISIBLE else View.GONE }
                2 -> { mode = (mode + 1) % MODES.size; applyMode(); web.reload() }
                3 -> finish()
            }
        }.show()
    }

    // ---- lifecycle --------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); web.saveState(o) }
    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { root.removeAllViews(); web.destroy(); super.onDestroy() }
}
