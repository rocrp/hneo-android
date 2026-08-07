package dev.rocry.hneo

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.ThemeMode
import dev.rocry.hneo.di.LocalAppContainer
import dev.rocry.hneo.ui.eink.LocalVolumeKeyTransport
import dev.rocry.hneo.ui.eink.VolumeKeyTransport
import dev.rocry.hneo.ui.navigation.HneoNavGraph
import dev.rocry.hneo.ui.theme.FontManager
import dev.rocry.hneo.ui.theme.HneoTheme
import dev.rocry.hneo.ui.theme.LocalTypeface

class MainActivity : ComponentActivity() {
    /** Owned by the Activity because only it sees key events; claimed by whatever can page. */
    private val volumeKeys = VolumeKeyTransport()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        volumeKeys.onKeyDown(keyCode) || super.onKeyDown(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HneoApp).container

        setContent {
            val settings by container.settings.settings.collectAsState(initial = AppSettings())
            val einkMode = settings.themeMode == ThemeMode.EINK
            val fontFamily = remember(settings.fontChoice) {
                FontManager.loadFontFamily(settings.fontChoice, this)
            }
            val typeface = remember(settings.fontChoice) {
                FontManager.loadTypeface(settings.fontChoice, this)
            }

            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalVolumeKeyTransport provides volumeKeys,
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
