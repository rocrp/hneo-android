package dev.rocry.hneo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.ThemeMode
import dev.rocry.hneo.data.settingsFlow
import dev.rocry.hneo.ui.navigation.HneoNavGraph
import dev.rocry.hneo.ui.theme.FontManager
import dev.rocry.hneo.ui.theme.HneoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsFlow(this).collectAsState(initial = AppSettings())
            val einkMode = settings.themeMode == ThemeMode.EINK
            val fontFamily = remember(settings.fontChoice) {
                FontManager.loadFontFamily(settings.fontChoice)
            }

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
