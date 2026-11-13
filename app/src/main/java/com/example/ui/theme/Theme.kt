package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = DeepPurple,
    onPrimary = PureWhite,
    primaryContainer = DeepPurpleContainer,
    onPrimaryContainer = OnDeepPurpleContainer,
    secondary = CobaltBlue,
    onSecondary = PureWhite,
    secondaryContainer = CobaltBlueContainer,
    onSecondaryContainer = OnCobaltBlueContainer,
    tertiary = SoftLavender,
    onTertiary = SlateInk,
    background = WarmOffWhite,
    onBackground = SlateInk,
    surface = PureWhite,
    onSurface = SlateInk,
    surfaceVariant = SoftLavenderSurface,
    onSurfaceVariant = SlateMuted,
    outline = OutlineSubtle,
    error = CoralAlert,
    errorContainer = CoralContainer,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DeepPurpleContainer,
    secondary = DarkSecondary,
    onSecondary = OnCobaltBlueContainer,
    secondaryContainer = OnCobaltBlueContainer,
    onSecondaryContainer = CobaltBlueContainer,
    tertiary = SoftLavender,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSurfaceVariant,
    error = CoralAlert,
  )

val FriendsHubShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
  )

@Composable
fun FriendsHubTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = FriendsHubTypography,
    shapes = FriendsHubShapes,
    content = content,
  )
}
