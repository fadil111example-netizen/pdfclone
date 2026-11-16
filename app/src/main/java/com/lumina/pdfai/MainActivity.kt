package com.lumina.pdfai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.view.KeyEvent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lumina.pdfai.di.Services
import com.lumina.pdfai.ui.LuminaApp
import com.lumina.pdfai.ui.theme.LuminaTheme
import com.lumina.pdfai.util.VolumeKeyBus

class MainActivity : ComponentActivity() {

    /** A uri that arrived through an external intent and must be opened once. */
    private var pendingOpenUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Services.init(this)

        pendingOpenUri = uriFromIntent(intent)

        setContent {
            LuminaTheme {
                LuminaApp(
                    incomingDocument = pendingOpenUri,
                    onIncomingHandled = { pendingOpenUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        uriFromIntent(intent)?.let { pendingOpenUri = it }
    }

    /**
     * Gives the visible screen a chance to turn the volume rocker into page
     * turns (see [VolumeKeyBus]). Returning the result of `super` keeps the
     * normal volume behaviour when no reader is on screen.
     */
    override fun onDestroy() {
        super.onDestroy()
        VolumeKeyBus.clear()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val consumed = VolumeKeyBus.handler?.invoke(keyCode) ?: false
        return if (consumed) true else super.onKeyDown(keyCode, event)
    }

    private fun uriFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        val resolved = uri ?: return null
        // Persist the read grant so the library entry keeps working next launch.
        runCatching {
            contentResolver.takePersistableUriPermission(
                resolved,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return resolved.toString()
    }
}
