package com.metaai.studio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var noInternetView: LinearLayout
    private lateinit var retryButton: Button
    
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var closeSearchButton: Button
    private lateinit var searchFab: Button

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Pending download while waiting for permission
    private var pendingUrl: String? = null
    private var pendingDisposition: String? = null
    private var pendingMime: String? = null

    companion object {
        private const val HOME_URL = "https://www.meta.ai/"
        private const val FILE_CHOOSER_CODE = 1001
        private const val STORAGE_PERMISSION_CODE = 1002
        private const val CAMERA_PERMISSION_CODE = 1003

        private val ALLOWED_HOSTS = listOf(
            "meta.ai", "www.meta.ai",
            "facebook.com", "www.facebook.com", "m.facebook.com",
            "login.facebook.com", "graph.facebook.com",
            "meta.com", "account.meta.com", "auth.meta.com", "www.meta.com",
            "messenger.com", "www.messenger.com",
            "instagram.com", "www.instagram.com",
            "fbcdn.net", "fbsbx.com",
            "accounts.google.com"
        )

        private val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.6099.230 Mobile Safari/537.36"

        fun getBlobAsBase64Js(url: String): String {
            return "javascript:(function() {" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '$url', true);" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function() {" +
                    "    if (this.status == 200) {" +
                    "      var blob = this.response;" +
                    "      var reader = new FileReader();" +
                    "      reader.readAsDataURL(blob);" +
                    "      reader.onloadend = function() {" +
                    "        var base64data = reader.result.split(',')[1];" +
                    "        BlobDownloader.onBlobDataReceived(base64data, blob.type);" +
                    "      };" +
                    "    }" +
                    "  };" +
                    "  xhr.send();" +
                    "})();"
        }
    }

    // ─────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupFullscreen()
        setupSwipeRefresh()
        setupWebView()
        loadInitialPage()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    // ─────────────────────────────────────────────────────────
    // Initialisation helpers
    // ─────────────────────────────────────────────────────────

    private fun bindViews() {
        swipeRefresh   = findViewById(R.id.swipeRefresh)
        webView        = findViewById(R.id.webView)
        progressBar    = findViewById(R.id.progressBar)
        noInternetView = findViewById(R.id.noInternetView)
        retryButton    = findViewById(R.id.retryButton)
        
        searchContainer   = findViewById(R.id.searchContainer)
        searchEditText    = findViewById(R.id.searchEditText)
        closeSearchButton = findViewById(R.id.closeSearchButton)
        searchFab         = findViewById(R.id.searchFab)

        retryButton.setOnClickListener { loadInitialPage() }
        
        searchFab.setOnClickListener {
            searchContainer.visibility = View.VISIBLE
            searchEditText.requestFocus()
            showKeyboard()
        }

        closeSearchButton.setOnClickListener {
            searchContainer.visibility = View.GONE
            webView.clearMatches()
            hideKeyboard()
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                webView.findAllAsync(searchEditText.text.toString())
                hideKeyboard()
                true
            } else false
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.accent_blue),
            ContextCompat.getColor(this, R.color.accent_purple)
        )
        swipeRefresh.setOnRefreshListener {
            if (isOnline()) webView.reload()
            else {
                swipeRefresh.isRefreshing = false
                showNoInternet()
            }
        }

        // Fix 1: Limit refresh to top of screen only
        webView.viewTreeObserver.addOnScrollChangedListener {
            swipeRefresh.isEnabled = webView.scrollY == 0
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(webView.settings) {
            // Core
            javaScriptEnabled          = true
            domStorageEnabled          = true
            databaseEnabled            = true
            javaScriptCanOpenWindowsAutomatically = true

            // Media
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess            = false
            allowContentAccess         = true

            // Zoom
            setSupportZoom(true)
            builtInZoomControls        = true
            displayZoomControls        = false

            // Cache
            cacheMode                  = WebSettings.LOAD_DEFAULT

            // User-agent: mobile Chrome
            userAgentString            = MOBILE_UA

            // Mixed content – only allow HTTPS
            mixedContentMode           = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Smooth rendering
            useWideViewPort            = true
            loadWithOverviewMode       = true
        }

        // Third-party cookies (required for Meta / FB login)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isScrollbarFadingEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        webView.webViewClient   = MetaWebViewClient()
        webView.webChromeClient = MetaWebChromeClient()
        
        // Fix 3: Enable JavaScript Interface for Blob downloads
        webView.addJavascriptInterface(BlobDownloader(), "BlobDownloader")

        // Long-click for Video Download (Fix 3 continued)
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            if (result.type == WebView.HitTestResult.VIDEO_TYPE || result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                val url = result.extra
                if (url != null && (url.contains(".mp4") || url.contains(".mkv") || url.contains(".webm"))) {
                    AlertDialog.Builder(this)
                        .setTitle("Download Video")
                        .setMessage("Do you want to download this video?")
                        .setPositiveButton("Download") { _, _ -> handleDownload(url, "", "video/mp4") }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                } else false
            } else false
        }

        // Download listener
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            handleDownload(url, contentDisposition, mimeType)
        }
    }

    private fun loadInitialPage() {
        if (isOnline()) {
            showWebView()
            webView.loadUrl(HOME_URL)
        } else {
            showNoInternet()
        }
    }

    // ─────────────────────────────────────────────────────────
    // WebViewClient
    // ─────────────────────────────────────────────────────────

    private inner class MetaWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true && !isOnline()) {
                showNoInternet()
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url  = request.url?.toString() ?: return false
            val host = request.url?.host      ?: return false

            // 1. Handle special Intent URIs (intent://)
            if (url.startsWith("intent://")) {
                return try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    if (packageManager.resolveActivity(intent, 0) != null) {
                        startActivity(intent)
                    }
                    true
                } catch (e: Exception) { true }
            }

            // 2. Handle direct app schemes (fb://, fb-messenger://, mailto:, etc.)
            if (!url.startsWith("http")) {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    if (packageManager.resolveActivity(intent, 0) != null) {
                        startActivity(intent)
                    }
                    true
                } catch (e: Exception) { true }
            }

            // 3. Logic to keep login and app functionality INSIDE the WebView
            val isWhitelisted = ALLOWED_HOSTS.any { host.endsWith(it, ignoreCase = true) }
            val loginKeywords = listOf("login", "auth", "oauth", "account", "checkpoint", "reg", "confirm")
            val isLoginFlow = loginKeywords.any { url.contains(it, ignoreCase = true) }

            return if (isWhitelisted || isLoginFlow) {
                false // Keep inside the app (WebView)
            } else {
                // Truly external URLs (privacy policies, external links) open in device browser
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "No browser found", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // WebChromeClient
    // ─────────────────────────────────────────────────────────

    private inner class MetaWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val androidPerms = mutableListOf<String>()
            request.resources.forEach { res ->
                when (res) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> androidPerms.add(Manifest.permission.CAMERA)
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> androidPerms.add(Manifest.permission.RECORD_AUDIO)
                }
            }

            val notGranted = androidPerms.filter {
                ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
            }

            if (notGranted.isEmpty()) {
                request.grant(request.resources)
            } else {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    notGranted.toTypedArray(),
                    CAMERA_PERMISSION_CODE
                )
                // Decline for now. The user will need to tap the button again after granting system permissions.
                request.deny()
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            // Cancel any existing callback
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = filePathCallback

            return try {
                val intent = fileChooserParams.createIntent()
                startActivityForResult(intent, FILE_CHOOSER_CODE)
                true
            } catch (e: ActivityNotFoundException) {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                Toast.makeText(this@MainActivity, "No file picker available", Toast.LENGTH_SHORT).show()
                false
            }
        }

        override fun onConsoleMessage(msg: ConsoleMessage?) = true

        override fun onJsAlert(
            view: WebView?, url: String?, message: String?,
            result: JsResult?
        ): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> result?.confirm() }
                .setCancelable(false)
                .show()
            return true
        }

        override fun onJsConfirm(
            view: WebView?, url: String?, message: String?,
            result: JsResult?
        ): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton("OK")     { _, _ -> result?.confirm() }
                .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                .setCancelable(false)
                .show()
            return true
        }
    }

    // ─────────────────────────────────────────────────────────
    // Download handling
    // ─────────────────────────────────────────────────────────

    private fun handleDownload(url: String, contentDisposition: String, mimeType: String) {
        // Fix 2: Handle blob: and data: URLs
        if (url.startsWith("blob:")) {
            webView.loadUrl(getBlobAsBase64Js(url))
            return
        }

        if (url.startsWith("data:")) {
            handleDataUri(url)
            return
        }

        // Standard HTTP/HTTPS
        if (!url.startsWith("http")) {
            Toast.makeText(this, "Cannot download: Invalid URL type", Toast.LENGTH_SHORT).show()
            return
        }

        // On Android < Q we need WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingUrl         = url
            pendingDisposition = contentDisposition
            pendingMime        = mimeType
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
            return
        }
        startDownload(url, contentDisposition, mimeType)
    }

    private fun handleDataUri(dataUri: String) {
        try {
            val base64Data = dataUri.substringAfter(",")
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val fileName = "MetaAI_Export_${System.currentTimeMillis()}.png"
            saveBytesToFile(bytes, fileName, "image/png")
        } catch (e: Exception) {
            Toast.makeText(this, "Data URL download failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBytesToFile(bytes: ByteArray, fileName: String, mimeType: String) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MetaAI")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            
            // Notify system about the new file
            DownloadManager.Request(Uri.fromFile(file))
            Toast.makeText(this, "Saved to Downloads/MetaAI: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Saving failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownload(url: String, contentDisposition: String, mimeType: String) {
        try {
            // Ensure folder exists
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MetaAI"
            )
            if (!dir.exists()) dir.mkdirs()

            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                // Pass cookies so authenticated downloads work
                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrEmpty()) addRequestHeader("Cookie", cookie)
                addRequestHeader("User-Agent", MOBILE_UA)
                setTitle(fileName)
                setDescription("Downloading via Meta AI Studio…")
                setDestinationInExternalPublicDir("${Environment.DIRECTORY_DOWNLOADS}/MetaAI", fileName)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(
                this,
                "⬇️ Downloading: $fileName\nSaved to Downloads/MetaAI",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Back Button
    // ─────────────────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Exit Meta AI Studio")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Exit")  { _, _ -> finish() }
                .setNegativeButton("Stay")  { d, _ -> d.dismiss() }
                .show()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Activity Results
    // ─────────────────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_CODE) {
            val results = if (resultCode == Activity.RESULT_OK)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            else null
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    pendingUrl?.let { startDownload(it, pendingDisposition ?: "", pendingMime ?: "") }
                } else {
                    Toast.makeText(this,
                        "Storage permission is needed to download files",
                        Toast.LENGTH_LONG).show()
                }
                pendingUrl = null; pendingDisposition = null; pendingMime = null
            }
            CAMERA_PERMISSION_CODE -> {
                // Camera/mic granted — user will need to retry on the site
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this,
                        "Camera/mic ready. Please retry on the page.",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // JavaScript Interface for Blob Handling
    // ─────────────────────────────────────────────────────────

    inner class BlobDownloader {
        @JavascriptInterface
        fun onBlobDataReceived(base64: String, mimeType: String) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
            val fileName = "MetaAI_Blob_${System.currentTimeMillis()}.$ext"
            runOnUiThread {
                saveBytesToFile(bytes, fileName, mimeType)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // UI state helpers
    // ─────────────────────────────────────────────────────────

    private fun showWebView() {
        noInternetView.visibility = View.GONE
        swipeRefresh.visibility   = View.VISIBLE
    }

    private fun showNoInternet() {
        swipeRefresh.visibility   = View.GONE
        noInternetView.visibility = View.VISIBLE
        progressBar.visibility    = View.GONE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net  = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }
}
