package lol.hypixel.lidlapp.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import lol.hypixel.lidlapp.auth.AuthManager
import lol.hypixel.lidlapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // PKCE pair generated once per login attempt
    private var pkcePair: AuthManager.PKCEPair? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupClickListeners()
        setupObservers()

        if (AuthManager.isLoggedIn(this)) {
            showPinScreen()
            viewModel.onBearerAvailable(AuthManager.getAccessToken(this)!!)
        } else {
            showLoginScreen()
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(AuthManager.REDIRECT_URI)) {
                    val code = Uri.parse(url).getQueryParameter("code")
                    if (code != null) handleAuthCode(code)
                }
                return false
            }
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener { startOAuth() }

        binding.btnGenerate.setOnClickListener { triggerQrGeneration() }

        binding.etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { triggerQrGeneration(); true } else false
        }

        binding.btnBack.setOnClickListener {
            viewModel.reset()
            showPinScreen()
        }

        binding.btnLogout.setOnClickListener {
            AuthManager.clearTokens(this)
            viewModel.reset()
            showLoginScreen()
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.btnGenerate.isEnabled = true
                }
                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.btnGenerate.isEnabled = false
                }
                is UiState.QrReady -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerate.isEnabled = true
                    showQrScreen(state.qrData + "1") //the + "1" part is for digital receipts; to turn them off set to "0"
                }
                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerate.isEnabled = true
                    showError(state.message)
                }
            }
        }

        viewModel.paymentMethods.observe(this) { methods ->
            if (methods.isNotEmpty()) {
                val m = methods[viewModel.selectedMethodIndex]
                val label = if (m.last4 != null) "···· ${m.last4}" else m.type
                binding.tvCardLabel.text = label
                binding.tvCardLabel.visibility = View.VISIBLE
            }
        }
    }

    // ── OAuth ─────────────────────────────────────────────────────────────────

    private fun startOAuth() {
        pkcePair = AuthManager.generatePKCE()
        val url = AuthManager.buildAuthUrl(pkcePair!!)
        showWebView()
        binding.webView.loadUrl(url)
    }

    private fun handleAuthCode(code: String) {
        val pkce = pkcePair ?: return
        showLoading("Signing in…")

        lifecycleScope.launch {
            try {
                val bearer = withContext(Dispatchers.IO) {
                    AuthManager.exchangeCode(this@MainActivity, code, pkce)
                }
                showPinScreen()
                viewModel.onBearerAvailable(bearer)
            } catch (e: Exception) {
                showLoginScreen()
                showError(e.message ?: "Login failed")
            }
        }
    }

    // ── QR generation ─────────────────────────────────────────────────────────

    private fun triggerQrGeneration() {
        val pin = binding.etPin.text?.toString()?.trim() ?: ""
        if (pin.length < 4) { showError("Enter your 4-digit PIN"); return }

        val bearer = AuthManager.getAccessToken(this) ?: run {
            showError("Session expired — please log in again")
            showLoginScreen()
            return
        }
        viewModel.generateQr(bearer, pin)
    }

    private fun renderQrBitmap(data: String) {
        val size = resources.displayMetrics.widthPixels - 128
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        binding.ivQrCode.setImageBitmap(bmp)
    }

    // ── Screen navigation ─────────────────────────────────────────────────────

    private fun showLoginScreen() {
        binding.screenLogin.visibility = View.VISIBLE
        binding.screenWebView.visibility = View.GONE
        binding.screenPin.visibility = View.GONE
        binding.screenQr.visibility = View.GONE
        setNormalBrightness()
    }

    private fun showWebView() {
        binding.screenLogin.visibility = View.GONE
        binding.screenWebView.visibility = View.VISIBLE
        binding.screenPin.visibility = View.GONE
        binding.screenQr.visibility = View.GONE
    }

    private fun showLoading(message: String) {
        binding.screenLogin.visibility = View.VISIBLE
        binding.screenWebView.visibility = View.GONE
        binding.screenPin.visibility = View.GONE
        binding.screenQr.visibility = View.GONE
        // reuse login screen area to show a spinner — simplest approach
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun showPinScreen() {
        binding.screenLogin.visibility = View.GONE
        binding.screenWebView.visibility = View.GONE
        binding.screenPin.visibility = View.VISIBLE
        binding.screenQr.visibility = View.GONE
        binding.etPin.text?.clear()
        binding.tvError.visibility = View.GONE
        setNormalBrightness()
    }

    private fun showQrScreen(qrData: String) {
        binding.screenPin.visibility = View.GONE
        binding.screenQr.visibility = View.VISIBLE
        renderQrBitmap(qrData)
        setFullBrightness()
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    // ── Brightness ────────────────────────────────────────────────────────────

    private fun setFullBrightness() {
        window.attributes = window.attributes.also { it.screenBrightness = 1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setNormalBrightness() {
        window.attributes = window.attributes.also { it.screenBrightness = -1f }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
