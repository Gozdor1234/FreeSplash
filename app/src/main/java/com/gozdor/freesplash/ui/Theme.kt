package com.gozdor.freesplash.ui

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
import com.gozdor.freesplash.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1F5A7A),
    secondary = Color(0xFFB8860B),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8EC5E6),
    secondary = Color(0xFFFFC857),
    background = Color(0xFF0E1216),
    surface = Color(0xFF151B20),
)

@Composable
fun FreeSplashTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(ctx)
        useDynamic -> dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
