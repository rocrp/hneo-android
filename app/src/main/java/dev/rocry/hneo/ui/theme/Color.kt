package dev.rocry.hneo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Catppuccin Frappe palette
object CatppuccinFrappe {
    val Rosewater = Color(0xFFF2D5CF)
    val Flamingo = Color(0xFFEEBEBE)
    val Pink = Color(0xFFF4B8E4)
    val Mauve = Color(0xFFCA9EE6)
    val Red = Color(0xFFE78284)
    val Maroon = Color(0xFFEA999C)
    val Peach = Color(0xFFEF9F76)
    val Yellow = Color(0xFFE5C890)
    val Green = Color(0xFFA6D189)
    val Teal = Color(0xFF81C8BE)
    val Sky = Color(0xFF99D1DB)
    val Sapphire = Color(0xFF85C1DC)
    val Blue = Color(0xFF8CAAEE)
    val Lavender = Color(0xFFBABBF1)

    val Text = Color(0xFFC6D0F5)
    val Subtext1 = Color(0xFFB5BFE2)
    val Subtext0 = Color(0xFFA5ADCE)
    val Overlay2 = Color(0xFF949CBB)
    val Overlay1 = Color(0xFF838BA7)
    val Overlay0 = Color(0xFF737994)
    val Surface2 = Color(0xFF626880)
    val Surface1 = Color(0xFF51576D)
    val Surface0 = Color(0xFF414559)
    val Base = Color(0xFF303446)
    val Mantle = Color(0xFF292C3C)
    val Crust = Color(0xFF232634)
}

// Rainbow colors for depth indicators and accent bars (normal mode)
val RainbowColors = listOf(
    CatppuccinFrappe.Blue,
    CatppuccinFrappe.Sapphire,
    CatppuccinFrappe.Sky,
    CatppuccinFrappe.Teal,
    CatppuccinFrappe.Green,
    CatppuccinFrappe.Yellow,
    CatppuccinFrappe.Peach,
    CatppuccinFrappe.Red,
    CatppuccinFrappe.Mauve,
    CatppuccinFrappe.Pink,
)

@Composable
fun depthColor(depth: Int): Color {
    if (depth <= 0) return Color.Transparent
    val eink = LocalEinkMode.current
    return if (eink) Color.Black else RainbowColors[(depth - 1) % RainbowColors.size]
}

@Composable
fun storyAccentColor(score: Int?, comments: Int): Color {
    val eink = LocalEinkMode.current
    if (eink) return Color.Black

    val scoreImportance = ((score ?: 0).toFloat() / 500f).coerceIn(0f, 1f)
    val commentImportance = (comments.toFloat() / 200f).coerceIn(0f, 1f)
    val combined = scoreImportance * 0.7f + commentImportance * 0.3f
    val index = (combined * (RainbowColors.size - 1)).toInt().coerceIn(0, RainbowColors.size - 1)
    return RainbowColors[index]
}
