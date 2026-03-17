package dev.rocry.hneo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

val LocalEinkMode = staticCompositionLocalOf { false }

// E-ink: pure black & white, no grays
private val EinkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.White,
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    error = Color.Black,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = CatppuccinFrappe.Blue,
    onPrimary = CatppuccinFrappe.Base,
    primaryContainer = CatppuccinFrappe.Blue.copy(alpha = 0.3f),
    onPrimaryContainer = CatppuccinFrappe.Text,
    secondary = CatppuccinFrappe.Mauve,
    onSecondary = CatppuccinFrappe.Base,
    secondaryContainer = CatppuccinFrappe.Mauve.copy(alpha = 0.3f),
    onSecondaryContainer = CatppuccinFrappe.Text,
    tertiary = CatppuccinFrappe.Teal,
    onTertiary = CatppuccinFrappe.Base,
    background = CatppuccinFrappe.Base,
    onBackground = CatppuccinFrappe.Text,
    surface = CatppuccinFrappe.Base,
    onSurface = CatppuccinFrappe.Text,
    surfaceVariant = CatppuccinFrappe.Surface0,
    onSurfaceVariant = CatppuccinFrappe.Subtext1,
    outline = CatppuccinFrappe.Overlay0,
    outlineVariant = CatppuccinFrappe.Surface2,
    surfaceContainerLowest = CatppuccinFrappe.Crust,
    surfaceContainerLow = CatppuccinFrappe.Mantle,
    surfaceContainer = CatppuccinFrappe.Surface0,
    surfaceContainerHigh = CatppuccinFrappe.Surface1,
    surfaceContainerHighest = CatppuccinFrappe.Surface2,
)

private val LightColorScheme = lightColorScheme(
    primary = CatppuccinFrappe.Blue,
    onPrimary = CatppuccinFrappe.Base,
    secondary = CatppuccinFrappe.Mauve,
    tertiary = CatppuccinFrappe.Teal,
)

@Composable
fun HneoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    einkMode: Boolean = false,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        einkMode -> EinkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = if (fontFamily != FontFamily.Default) {
        val default = Typography()
        Typography(
            displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = default.labelSmall.copy(fontFamily = fontFamily),
        )
    } else {
        Typography()
    }

    CompositionLocalProvider(
        LocalEinkMode provides einkMode,
        LocalFontFamily provides fontFamily,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
