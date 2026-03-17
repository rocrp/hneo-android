package dev.rocry.hneo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
