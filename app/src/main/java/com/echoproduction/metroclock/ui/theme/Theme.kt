package com.echoproduction.metroclock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PrecisionDarkScheme = darkColorScheme(
    primary      = McOrange,
    background   = Color(0xFF07070A),
    surface      = Color(0xFF0D0D13),
    onPrimary    = Color.White,
    onBackground = Color.White,
    onSurface    = Color.White,
)

private val PrecisionLightScheme = lightColorScheme(
    primary      = McOrange,
    background   = Color(0xFFF2F2F7),
    surface      = Color.White,
    onPrimary    = Color.White,
    onBackground = Color(0xFF1C1C1E),
    onSurface    = Color(0xFF1C1C1E),
)

@Composable
fun MetroClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val mcColors = if (darkTheme) darkMcColors else lightMcColors
    val materialScheme = if (darkTheme) PrecisionDarkScheme else PrecisionLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = mcColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalMcColors provides mcColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography  = Typography,
            content     = content
        )
    }
}
