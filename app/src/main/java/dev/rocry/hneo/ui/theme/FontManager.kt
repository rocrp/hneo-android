package dev.rocry.hneo.ui.theme

import android.content.Context
import android.graphics.Typeface as PlatformTypeface
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import dev.rocry.hneo.ui.webview.ReaderFont
import java.io.File

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalTypeface = staticCompositionLocalOf<PlatformTypeface> { PlatformTypeface.DEFAULT }

data class FontInfo(val name: String, val path: String)

private val FONT_EXTENSIONS = setOf("ttf", "otf")

object FontManager {
    private val sdcardFontsDir = File(Environment.getExternalStorageDirectory(), "Fonts")

    /** App-private fonts directory — no permissions needed */
    fun getAppFontsDir(context: Context): File = File(context.filesDir, "fonts").also { it.mkdirs() }

    /**
     * Import a font file from a content URI (SAF) into app-private storage.
     * Returns the FontInfo for the imported font, or null on failure.
     */
    fun importFont(context: Context, uri: Uri): FontInfo? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val displayName = cursor?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        } ?: uri.lastPathSegment ?: return null

        if (displayName.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) return null

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
        val builtIn = BuiltInFont.entries.map { FontInfo(it.displayName, "") }
        val appFonts = listFontsIn(getAppFontsDir(context))

        // Legacy /sdcard/Fonts/ — works on older Android or with permission
        val sdcardFonts = try {
            listFontsIn(sdcardFontsDir).filter { sd -> appFonts.none { it.name == sd.name } }
        } catch (_: Exception) {
            emptyList()
        }

        return builtIn + appFonts + sdcardFonts
    }

    /**
     * The one place that turns a font choice into a file. Everything that needs a
     * custom font — the app typeface, the Compose family, the reader stylesheet —
     * resolves through here.
     */
    fun customFontFile(fontChoice: String, context: Context): File? {
        if (BuiltInFont.forChoice(fontChoice) != null) return null
        val info = listAvailableFonts(context).find { it.name == fontChoice } ?: return null
        if (info.path.isBlank()) return null
        return File(info.path).takeIf { it.exists() }
    }

    fun loadTypeface(fontChoice: String, context: Context): PlatformTypeface {
        BuiltInFont.forChoice(fontChoice)?.let { return it.platformTypeface() }
        val file = customFontFile(fontChoice, context) ?: return PlatformTypeface.DEFAULT
        return try {
            PlatformTypeface.createFromFile(file)
        } catch (_: Exception) {
            PlatformTypeface.DEFAULT
        }
    }

    fun loadFontFamily(fontChoice: String, context: Context): FontFamily {
        BuiltInFont.forChoice(fontChoice)?.let { return it.fontFamily() }
        val file = customFontFile(fontChoice, context) ?: return FontFamily.Default
        return try {
            FontFamily(PlatformTypeface.createFromFile(file))
        } catch (_: Exception) {
            FontFamily.Default
        }
    }

    /**
     * The same choice, expressed for a WebView. An imported font has to travel as
     * bytes: the WebView cannot open a file inside app-private storage.
     */
    fun loadReaderFont(fontChoice: String, context: Context): ReaderFont {
        BuiltInFont.forChoice(fontChoice)?.let { return ReaderFont.builtIn(it) }
        val file = customFontFile(fontChoice, context) ?: return ReaderFont.SYSTEM
        return try {
            ReaderFont.embedded(file.name, file.readBytes())
        } catch (_: Exception) {
            ReaderFont.SYSTEM
        }
    }

    private fun listFontsIn(dir: File): List<FontInfo> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.lowercase() in FONT_EXTENSIONS }
            ?.sortedBy { it.nameWithoutExtension.lowercase() }
            ?.map { FontInfo(it.nameWithoutExtension, it.absolutePath) }
            ?: emptyList()
    }

    private fun BuiltInFont.platformTypeface(): PlatformTypeface = when (this) {
        BuiltInFont.SYSTEM -> PlatformTypeface.DEFAULT
        BuiltInFont.SERIF -> PlatformTypeface.SERIF
        BuiltInFont.MONOSPACE -> PlatformTypeface.MONOSPACE
    }

    private fun BuiltInFont.fontFamily(): FontFamily = when (this) {
        BuiltInFont.SYSTEM -> FontFamily.Default
        BuiltInFont.SERIF -> FontFamily.Serif
        BuiltInFont.MONOSPACE -> FontFamily.Monospace
    }
}
