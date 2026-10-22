package app.patootie.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.patootie.R

object Kawaii {
    val Lavender = Color(0xFFC8B6F2)
    val LavenderDeep = Color(0xFF8E72D9)
    val LavenderInk = Color(0xFF5B4A86)
    val LavenderMist = Color(0xFFF1EBFF)
    val Mint = Color(0xFFB5EAD7)
    val MintDeep = Color(0xFF4FB892)
    val MintMist = Color(0xFFE6F8F0)
    val Pink = Color(0xFFFFB7D5)
    val PinkDeep = Color(0xFFFF7EB3)
    val Cream = Color(0xFFFBF7FF)
    val Lemon = Color(0xFFFFE9A8)

    val NightBg = Color(0xFF221C33)
    val NightCard = Color(0xFF30284A)
}

/** Colors the cute components use that Material doesn't have a slot for. */
@Immutable
data class KawaiiColors(
    val sparkle: Color,
    val heart: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val live: Color,
    val ink: Color,
)

private val LightKawaii =
    KawaiiColors(Kawaii.Lemon, Kawaii.PinkDeep, Color.White, Kawaii.LavenderMist, Kawaii.MintDeep, Kawaii.LavenderInk)
private val DarkKawaii =
    KawaiiColors(Kawaii.Lemon, Kawaii.Pink, Kawaii.NightCard, Color(0xFF3A3157), Kawaii.Mint, Kawaii.LavenderMist)

val LocalKawaii = staticCompositionLocalOf { LightKawaii }

private val LightColors = lightColorScheme(
    primary = Kawaii.LavenderDeep,
    onPrimary = Color.White,
    primaryContainer = Kawaii.Lavender,
    onPrimaryContainer = Kawaii.LavenderInk,
    secondary = Kawaii.MintDeep,
    onSecondary = Color.White,
    secondaryContainer = Kawaii.Mint,
    onSecondaryContainer = Color(0xFF1F5A45),
    tertiary = Kawaii.PinkDeep,
    tertiaryContainer = Kawaii.Pink,
    background = Kawaii.Cream,
    onBackground = Kawaii.LavenderInk,
    surface = Color.White,
    onSurface = Kawaii.LavenderInk,
    surfaceVariant = Kawaii.LavenderMist,
    onSurfaceVariant = Color(0xFF7B6CA3),
    outline = Kawaii.Lavender,
    error = Color(0xFFE5677F),
)

private val DarkColors = darkColorScheme(
    primary = Kawaii.Lavender,
    onPrimary = Kawaii.LavenderInk,
    primaryContainer = Color(0xFF4A3D73),
    onPrimaryContainer = Kawaii.LavenderMist,
    secondary = Kawaii.Mint,
    onSecondary = Color(0xFF1F5A45),
    secondaryContainer = Color(0xFF2E5C4D),
    onSecondaryContainer = Kawaii.MintMist,
    tertiary = Kawaii.Pink,
    tertiaryContainer = Color(0xFF6E3553),
    background = Kawaii.NightBg,
    onBackground = Kawaii.LavenderMist,
    surface = Kawaii.NightCard,
    onSurface = Kawaii.LavenderMist,
    surfaceVariant = Color(0xFF3A3157),
    onSurfaceVariant = Color(0xFFCBBEF0),
    outline = Color(0xFF6C5D99),
    error = Color(0xFFFF9AAE),
)

@OptIn(ExperimentalTextApi::class)
private fun fredoka(weight: Int) = Font(
    R.font.fredoka,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Fredoka = FontFamily(fredoka(400), fredoka(500), fredoka(600), fredoka(700))

private val CuteType = Typography().let { base ->
    fun TextStyle.cute(weight: Int = 500) = copy(fontFamily = Fredoka, fontWeight = FontWeight(weight))
    base.copy(
        displaySmall = base.displaySmall.cute(700),
        headlineLarge = base.headlineLarge.cute(700),
        headlineMedium = base.headlineMedium.cute(700),
        headlineSmall = base.headlineSmall.cute(600),
        titleLarge = base.titleLarge.cute(600).copy(fontSize = 22.sp),
        titleMedium = base.titleMedium.cute(600),
        titleSmall = base.titleSmall.cute(600),
        bodyLarge = base.bodyLarge.cute(400),
        bodyMedium = base.bodyMedium.cute(400),
        bodySmall = base.bodySmall.cute(400),
        labelLarge = base.labelLarge.cute(600).copy(fontSize = 16.sp),
        labelMedium = base.labelMedium.cute(500),
        labelSmall = base.labelSmall.cute(500),
    )
}

private val RoundShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun PatootieTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKawaii provides if (dark) DarkKawaii else LightKawaii) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = CuteType,
            shapes = RoundShapes,
            content = content,
        )
    }
}
