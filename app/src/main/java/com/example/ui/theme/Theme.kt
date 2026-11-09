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
    primary = DeliveryOrange,
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
    primary = DeliveryOrange,
    onPrimary = PureWhite,
    secondary = GoldenYellow,
    tertiary = MintFresh,
    background = WarmCream,
    surface = CardSurface,
    onBackground = TextDark,
    onSurface = TextDark,
  )

/** Clean off-white gradient for onboarding & screen backgrounds. */
val WarmPageGradient: Brush =
  Brush.verticalGradient(
    colors = listOf(Color(0xFFFFFFFF), WarmCream),
  )

/** Soft amber radial gradient for Slide 1 backdrop. */
val SaffronRadialGradient: Brush =
  Brush.radialGradient(
    colors = listOf(Color(0xFFFFF7ED), Color(0xFFF8FAFC)),
  )

/** Soft indigo-violet radial gradient for Slide 2 backdrop. */
val PurpleRadialGradient: Brush =
  Brush.radialGradient(
    colors = listOf(Color(0xFFF5F3FF), Color(0xFFF8FAFC)),
  )

/** Soft emerald radial gradient for Slide 3 backdrop. */
val MintRadialGradient: Brush =
  Brush.radialGradient(
    colors = listOf(Color(0xFFECFDF5), Color(0xFFF8FAFC)),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Keep clean modern light theme as default
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
