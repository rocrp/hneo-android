package dev.rocry.hneo

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.ThemeMode
import dev.rocry.hneo.data.settingsFlow
import dev.rocry.hneo.ui.components.LocalSetVolumeKeyIntercept
import dev.rocry.hneo.ui.components.LocalVolumePageEvents
import dev.rocry.hneo.ui.navigation.HneoNavGraph
import dev.rocry.hneo.ui.theme.FontManager
import dev.rocry.hneo.ui.theme.HneoTheme
import dev.rocry.hneo.ui.theme.LocalTypeface
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val volumePageEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private var isEinkMode = false
    private var shouldInterceptVolumeKeys = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isEinkMode || shouldInterceptVolumeKeys) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { volumePageEvents.tryEmit(-1); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { volumePageEvents.tryEmit(1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsFlow(this).collectAsState(initial = AppSettings())
            val einkMode = settings.themeMode == ThemeMode.EINK
            isEinkMode = einkMode
            val fontFamily = remember(settings.fontChoice) {
                FontManager.loadFontFamily(settings.fontChoice, this)
            }
            val typeface = remember(settings.fontChoice) {
                FontManager.loadTypeface(settings.fontChoice, this)
            }

            CompositionLocalProvider(
                LocalVolumePageEvents provides volumePageEvents,
                LocalSetVolumeKeyIntercept provides { shouldInterceptVolumeKeys = it },
                LocalTypeface provides typeface,
            ) {
                HneoTheme(
                    einkMode = einkMode,
                    fontFamily = fontFamily,
                    dynamicColor = !einkMode,
                ) {
                    HneoNavGraph()
                }
            }
        }
    }
}
