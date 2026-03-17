package dev.rocry.hneo.ui.theme

import android.graphics.Typeface as PlatformTypeface
import android.os.Environment
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import java.io.File

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

data class FontInfo(val name: String, val path: String)

object FontManager {
    private val fontsDir = File(Environment.getExternalStorageDirectory(), "Fonts")

    fun listAvailableFonts(): List<FontInfo> {
        val builtIn = listOf(
            FontInfo("System", ""),
            FontInfo("Serif", ""),
            FontInfo("Monospace", ""),
        )

        val custom = if (fontsDir.exists() && fontsDir.isDirectory) {
            fontsDir.listFiles()
                ?.filter { it.extension.lowercase() in listOf("ttf", "otf") }
                ?.sortedBy { it.nameWithoutExtension.lowercase() }
                ?.map { FontInfo(it.nameWithoutExtension, it.absolutePath) }
                ?: emptyList()
        } else {
            emptyList()
        }

        return builtIn + custom
    }

    fun loadFontFamily(fontChoice: String): FontFamily {
        return when (fontChoice) {
            "System", "" -> FontFamily.Default
            "Serif" -> FontFamily.Serif
            "Monospace" -> FontFamily.Monospace
            else -> {
                // Try loading from /sdcard/Fonts/
                val fonts = listAvailableFonts()
                val info = fonts.find { it.name == fontChoice } ?: return FontFamily.Default
                if (info.path.isBlank()) return FontFamily.Default
                try {
                    val typeface = PlatformTypeface.createFromFile(info.path)
                    FontFamily(typeface)
                } catch (_: Exception) {
                    FontFamily.Default
                }
            }
        }
    }
}
