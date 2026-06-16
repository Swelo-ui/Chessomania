package com.chessomania.app.ui

import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.chessomania.app.R
import com.chessomania.app.SettingsManager

class GameReviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var movesBase64: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_review)

        // Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Get moves from intent
        val movesList = intent.getStringArrayListExtra("moves")
        if (movesList != null) {
            val movesJoined = movesList.joinToString(",")
            movesBase64 = Base64.encodeToString(movesJoined.toByteArray(), Base64.NO_WRAP)
        }

        webView = findViewById(R.id.review_webview)
        setupWebView()
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        
        // Disable dangerous file access APIs in favor of WebViewAssetLoader
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        // WebViewAssetLoader secures asset loading and handles Web Workers properly
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Intercept asset URLs and route them to local asset loader
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // When page finishes, start the review by passing Base64 moves, piece theme, and board colors
                if (movesBase64.isNotEmpty()) {
                    val pieceTheme = SettingsManager.getPieceTheme(this@GameReviewActivity)
                    val boardTheme = SettingsManager.getBoardTheme(this@GameReviewActivity)
                    val colors = SettingsManager.getBoardColors(boardTheme)
                    webView.evaluateJavascript(
                        "startReview('$movesBase64', '$pieceTheme', '${colors.light}', '${colors.dark}')",
                        null
                    )
                }
            }
        }

        // JS-to-Kotlin Bridge
        webView.addJavascriptInterface(AndroidReviewInterface(), "Android")

        // Load page securely over HTTPS using WebViewAssetLoader
        webView.loadUrl("https://appassets.androidplatform.net/assets/review.html")
    }

    override fun onDestroy() {
        // Clear WebView memory to prevent leaks
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        super.onDestroy()
    }

    inner class AndroidReviewInterface {
        @JavascriptInterface
        fun onError(msg: String) {
            runOnUiThread {
                Toast.makeText(this@GameReviewActivity, "Review Error: $msg", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onAnalysisComplete() {
            runOnUiThread {
                Toast.makeText(this@GameReviewActivity, "Analysis complete!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onReviewLoaded() {
            // Callback confirming the WebView UI was initialized successfully
        }
    }
}
