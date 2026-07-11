package com.xiaomi.mimoclaw.core.theme

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.annotation.RequiresApi

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB27B),
    onPrimary = Color(0xFF4D2000),
    primaryContainer = Color(0xFF713400),
    onPrimaryContainer = Color(0xFFFFDCC5),
    secondary = Color(0xFFD8C3B5),
    onSecondary = Color(0xFF3B2B22),
    tertiary = Color(0xFFB8C7B4),
    onTertiary = Color(0xFF253722),
    background = Color(0xFF15120F),
    onBackground = Color(0xFFF5EFE9),
    surface = Color(0xFF1D1915),
    onSurface = Color(0xFFF5EFE9),
    surfaceVariant = Color(0xFF2A241F),
    onSurfaceVariant = Color(0xFFD0C3B9),
    outline = Color(0xFF9D8F84),
    outlineVariant = Color(0xFF463C35),
    inverseSurface = Color(0xFFF5EFE9),
    inverseOnSurface = Color(0xFF201B16),
    error = Color(0xFFFFB4AB),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC84D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC5),
    onPrimaryContainer = Color(0xFF4A1A00),
    secondary = Color(0xFF6C5A4E),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF456343),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF211A15),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF211A15),
    surfaceVariant = Color(0xFFF1E9E2),
    onSurfaceVariant = Color(0xFF70635B),
    outline = Color(0xFF8D7E74),
    outlineVariant = Color(0xFFE2D8D0),
    inverseSurface = Color(0xFF2A211B),
    inverseOnSurface = Color(0xFFFFF7F1),
    error = Color(0xFFBA1A1A),
)

private val BaseTypography = Typography()
private val MiMoTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 21.sp
    )
)

private val MiMoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MiMoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicColorScheme(darkTheme)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx: Context = view.context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) {
                    val window = ctx.window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                    break
                }
                ctx = (ctx as ContextWrapper).baseContext
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiMoTypography,
        shapes = MiMoShapes,
        content = content
    )
}

@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("NewApi") // Guarded by SDK_INT >= S at the only call site.
@Composable
private fun dynamicColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
