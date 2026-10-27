package com.flowstudio.gitagent

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

private val LightC = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0E8), onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFD3E8E4), onSecondaryContainer = Color(0xFF0B1F1C),
    surface = Color(0xFFF6FAF9), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerHigh = Color(0xFFE4EBE9), surfaceContainer = Color(0xFFEAF1EF),
    background = Color(0xFFF6FAF9), onBackground = Color(0xFF161D1C),
    outline = Color(0xFF6F7977),
)

private val DarkC = darkColorScheme(
    primary = Color(0xFF5CD6C8), onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFB7F0E8),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFD3E8E4),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE5E3),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerHigh = Color(0xFF242B2A), surfaceContainer = Color(0xFF1A2120),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDDE5E3),
    outline = Color(0xFF899390),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkC
        else -> LightC
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}
