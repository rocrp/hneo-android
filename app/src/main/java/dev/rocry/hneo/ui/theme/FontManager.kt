package dev.rocry.hneo.ui.theme

import android.content.Context
import android.graphics.Typeface as PlatformTypeface
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import java.io.File

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalTypeface = staticCompositionLocalOf<PlatformTypeface> { PlatformTypeface.DEFAULT }

data class FontInfo(val name: String, val path: String)

object FontManager {
    private val sdcardFontsDir = File(Environment.getExternalStorageDirectory(), "Fonts")

    /** App-private fonts directory — no permissions needed */
    fun getAppFontsDir(context: Context): File {
        return File(context.filesDir, "fonts").also { it.mkdirs() }
    }

    /**
     * Import a font file from a content URI (SAF) into app-private storage.
     * Returns the FontInfo for the imported font, or null on failure.
     */
    fun importFont(context: Context, uri: Uri): FontInfo? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val displayName = cursor?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        } ?: uri.lastPathSegment ?: return null

        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext !in listOf("ttf", "otf")) return null

        val destFile = File(getAppFontsDir(context), displayName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            // Validate the font can be loaded
            PlatformTypeface.createFromFile(destFile)
        } catch (_: Exception) {
            destFile.delete()
            return null
        }
        return FontInfo(destFile.nameWithoutExtension, destFile.absolutePath)
    }

    /** Delete a custom font from app-private storage */
    fun deleteFont(context: Context, fontInfo: FontInfo): Boolean {
        if (fontInfo.path.isBlank()) return false
        val file = File(fontInfo.path)
        if (!file.absolutePath.startsWith(getAppFontsDir(context).absolutePath)) return false
        return file.delete()
    }

    fun listAvailableFonts(context: Context): List<FontInfo> {
        val builtIn = listOf(
            FontInfo("System", ""),
            FontInfo("Serif", ""),
            FontInfo("Monospace", ""),
        )

        // App-private fonts (always accessible, no permission needed)
        val appFonts = listFontsIn(getAppFontsDir(context))

        // Legacy /sdcard/Fonts/ — works on older Android or with permission
        val sdcardFonts = try {
            listFontsIn(sdcardFontsDir)
                .filter { sd -> appFonts.none { it.name == sd.name } } // deduplicate
        } catch (_: Exception) {
            emptyList()
        }

        return builtIn + appFonts + sdcardFonts
    }

    private fun listFontsIn(dir: File): List<FontInfo> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.lowercase() in listOf("ttf", "otf") }
            ?.sortedBy { it.nameWithoutExtension.lowercase() }
            ?.map { FontInfo(it.nameWithoutExtension, it.absolutePath) }
            ?: emptyList()
    }

    fun loadTypeface(fontChoice: String, context: Context): PlatformTypeface {
        return when (fontChoice) {
            "System", "" -> PlatformTypeface.DEFAULT
            "Serif" -> PlatformTypeface.SERIF
            "Monospace" -> PlatformTypeface.MONOSPACE
            else -> {
                val fonts = listAvailableFonts(context)
                val info = fonts.find { it.name == fontChoice } ?: return PlatformTypeface.DEFAULT
                if (info.path.isBlank()) return PlatformTypeface.DEFAULT
                try {
                    PlatformTypeface.createFromFile(info.path)
                } catch (_: Exception) {
                    PlatformTypeface.DEFAULT
                }
            }
        }
    }

    fun loadFontFamily(fontChoice: String, context: Context): FontFamily {
        return when (fontChoice) {
            "System", "" -> FontFamily.Default
            "Serif" -> FontFamily.Serif
            "Monospace" -> FontFamily.Monospace
            else -> {
                val fonts = listAvailableFonts(context)
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
