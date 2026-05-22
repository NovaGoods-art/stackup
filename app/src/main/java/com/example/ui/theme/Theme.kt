package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldGreen,
  secondary = GoldenStreak,
  tertiary = SparkleGold,
  background = Slate900,
  surface = Slate800,
  onPrimary = Slate900,
  onSecondary = Slate900,
  onBackground = SoftIce,
  onSurface = SoftIce,
  surfaceVariant = Slate700,
  error = NudgeRose
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldGreen,
  secondary = GoldenStreak,
  tertiary = SparkleGold,
  background = Color(0xFFF8FAFC), // Very soft Slate light
  surface = Color.White,
  onPrimary = Color.White,
  onSecondary = Slate900,
  onBackground = Slate900,
  onSurface = Slate900,
  surfaceVariant = Color(0xFFE2E8F0),
  error = NudgeRose
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
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
