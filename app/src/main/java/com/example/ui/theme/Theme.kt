package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = DeepSaffron,
    onPrimary = PureWhite,
    secondary = GoldenYellow,
    tertiary = MintFresh,
    background = BlackPrimary,
    surface = BlackSecondary,
    onBackground = PureWhite,
    onSurface = PureWhite,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = DeepSaffron,
    onPrimary = PureWhite,
    secondary = GoldenYellow,
    tertiary = MintFresh,
    background = WarmCream,
    surface = CardSurface,
    onBackground = TextDark,
    onSurface = TextDark,
  )

/** Warm cream-to-off-white gradient for onboarding page backgrounds. */
val WarmPageGradient: Brush =
  Brush.verticalGradient(
    colors = listOf(WarmCream, Color(0xFFEDE8DF)),
  )

/** Soft cream radial gradient for Slide 1 illustration backdrop. */
val SaffronRadialGradient: Brush =
  Brush.radialGradient(
    colors = listOf(Color(0xFFF5F0E8), Color(0xFFFAF7F2)),
  )

/** Soft cream radial gradient for Slide 2 illustration backdrop. */
val PurpleRadialGradient: Brush =
  Brush.radialGradient(
    colors = listOf(Color(0xFFF5F0E8), Color(0xFFFAF7F2)),
  )

/** Soft cream radial gradient for Slide 3 illustration backdrop. */
val MintRadialGradient: Brush =
  Brush.radialGradient(
    colors = listOf(Color(0xFFF5F0E8), Color(0xFFFAF7F2)),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Keep warm light theme as default
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
