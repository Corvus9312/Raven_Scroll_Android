package ravens.scroll.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = OnPrimary,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkToolbar,
    onSurfaceVariant = DarkText,
    outline = DarkBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = OnPrimary,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightToolbar,
    onSurfaceVariant = LightText,
    outline = LightBorder,
)

@Composable
fun BookReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
