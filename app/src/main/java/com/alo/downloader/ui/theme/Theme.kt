package com.alo.downloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alo.downloader.data.settings.AppSettings
import com.alo.downloader.data.settings.ThemeMode

/** Fallback palettes used when dynamic color is off or unavailable (Android < 12). */
data class SeedPalette(val name: String, val light: ColorScheme, val dark: ColorScheme, val swatch: Color)

val seedPalettes = listOf(
    SeedPalette(
        "Blue",
        lightColorScheme(
            primary = Color(0xFF1E5BD8), onPrimary = Color.White, primaryContainer = Color(0xFFDBE1FF),
            onPrimaryContainer = Color(0xFF00174B), secondary = Color(0xFF585E71), secondaryContainer = Color(0xFFDCE2F9),
            tertiary = Color(0xFF735471), tertiaryContainer = Color(0xFFFED7F9), surface = Color(0xFFFAF8FF),
            surfaceContainer = Color(0xFFEEEDF4), surfaceContainerHigh = Color(0xFFE8E7EF),
            surfaceContainerLow = Color(0xFFF4F3FA), surfaceContainerHighest = Color(0xFFE2E2E9),
        ),
        darkColorScheme(
            primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF002A78), primaryContainer = Color(0xFF0041A8),
            onPrimaryContainer = Color(0xFFDBE1FF), secondary = Color(0xFFC0C6DC), secondaryContainer = Color(0xFF404659),
            tertiary = Color(0xFFE1BBDC), tertiaryContainer = Color(0xFF5A3D58), surface = Color(0xFF121318),
            surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F),
            surfaceContainerLow = Color(0xFF1A1B21), surfaceContainerHighest = Color(0xFF33343A),
        ),
        Color(0xFF1E5BD8),
    ),
    SeedPalette(
        "Green",
        lightColorScheme(
            primary = Color(0xFF2B6A46), onPrimary = Color.White, primaryContainer = Color(0xFFB0F1C3),
            onPrimaryContainer = Color(0xFF00210F), secondary = Color(0xFF4F6354), secondaryContainer = Color(0xFFD2E8D5),
            tertiary = Color(0xFF3B6470), tertiaryContainer = Color(0xFFBFE9F8), surface = Color(0xFFF6FBF4),
            surfaceContainer = Color(0xFFEAEFE9),
        ),
        darkColorScheme(
            primary = Color(0xFF95D5A8), onPrimary = Color(0xFF00391D), primaryContainer = Color(0xFF0E5130),
            onPrimaryContainer = Color(0xFFB0F1C3), secondary = Color(0xFFB6CCB9), secondaryContainer = Color(0xFF384B3D),
            tertiary = Color(0xFFA3CDDB), tertiaryContainer = Color(0xFF214C57), surface = Color(0xFF101510),
            surfaceContainer = Color(0xFF1C211C),
        ),
        Color(0xFF2B6A46),
    ),
    SeedPalette(
        "Violet",
        lightColorScheme(
            primary = Color(0xFF6750A4), onPrimary = Color.White, primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D), secondary = Color(0xFF625B71), secondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFF7D5260), tertiaryContainer = Color(0xFFFFD8E4),
        ),
        darkColorScheme(
            primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF), secondary = Color(0xFFCCC2DC), secondaryContainer = Color(0xFF4A4458),
            tertiary = Color(0xFFEFB8C8), tertiaryContainer = Color(0xFF633B48),
        ),
        Color(0xFF6750A4),
    ),
    SeedPalette(
        "Amber",
        lightColorScheme(
            primary = Color(0xFF8A5100), onPrimary = Color.White, primaryContainer = Color(0xFFFFDCBE),
            onPrimaryContainer = Color(0xFF2C1600), secondary = Color(0xFF725A42), secondaryContainer = Color(0xFFFFDCBE),
            tertiary = Color(0xFF58633A), tertiaryContainer = Color(0xFFDCE8B4), surface = Color(0xFFFFF8F5),
        ),
        darkColorScheme(
            primary = Color(0xFFFFB871), onPrimary = Color(0xFF4A2800), primaryContainer = Color(0xFF693C00),
            onPrimaryContainer = Color(0xFFFFDCBE), secondary = Color(0xFFE1C1A4), secondaryContainer = Color(0xFF594430),
            tertiary = Color(0xFFC0CC9A), tertiaryContainer = Color(0xFF414B24), surface = Color(0xFF19120C),
        ),
        Color(0xFF8A5100),
    ),
    SeedPalette(
        "Rose",
        lightColorScheme(
            primary = Color(0xFFA4384A), onPrimary = Color.White, primaryContainer = Color(0xFFFFDADB),
            onPrimaryContainer = Color(0xFF40000F), secondary = Color(0xFF775657), secondaryContainer = Color(0xFFFFDADB),
            tertiary = Color(0xFF755A2F), tertiaryContainer = Color(0xFFFFDDB1), surface = Color(0xFFFFF8F7),
        ),
        darkColorScheme(
            primary = Color(0xFFFFB2B7), onPrimary = Color(0xFF650720), primaryContainer = Color(0xFF842134),
            onPrimaryContainer = Color(0xFFFFDADB), secondary = Color(0xFFE6BDBD), secondaryContainer = Color(0xFF5D3F40),
            tertiary = Color(0xFFE5C18D), tertiaryContainer = Color(0xFF5B4319), surface = Color(0xFF1A1112),
        ),
        Color(0xFFA4384A),
    ),
)

private val AloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AloTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Normal),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
)

val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AloTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && dynamicColorAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> seedPalettes.getOrElse(settings.seedColor) { seedPalettes.first() }.let { if (dark) it.dark else it.light }
    }
    if (dark && settings.pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0C),
            surfaceContainer = Color(0xFF111114),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AloTypography, shapes = AloShapes, content = content)
}

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
