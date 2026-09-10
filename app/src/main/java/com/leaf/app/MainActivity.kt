package com.leaf.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.leaf.app.ui.OpenRequests
import com.leaf.app.ui.QuireApp
import com.leaf.app.ui.VolumeKeys

/**
 * Single activity. AppCompatActivity rather than ComponentActivity because the
 * reader hosts PdfViewerFragment, which needs a FragmentActivity with an AppCompat theme.
 */
class MainActivity : AppCompatActivity() {

    private val openRequests = OpenRequests()
    private val volumeKeys = VolumeKeys()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only the first creation acts on the launch intent; a rotated activity must not reopen it.
        if (savedInstanceState == null) openRequests.offer(intent)
        setContent {
            QuireApp(container = appContainer, openRequests = openRequests, volumeKeys = volumeKeys)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequests.offer(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            KeyEvent.KEYCODE_VOLUME_UP -> -1
            else -> return super.onKeyDown(keyCode, event)
        }
        if (event.repeatCount == 0 && volumeKeys.offer(delta)) return true
        return if (volumeKeys.consume) true else super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeys.consume && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }
}
