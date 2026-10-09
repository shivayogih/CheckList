package com.dataloom.checklist.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Neutral99,
    primaryContainer = Green90,
    onPrimaryContainer = Green20,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = OutlineVariant30,
    outline = Outline40,
    outlineVariant = OutlineVariant80,
    surfaceContainer = SurfaceContainerLight,
    error = Error40,
)

internal val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green40,
    onPrimaryContainer = Green90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = Neutral10,
    onSurfaceVariant = OutlineVariant80,
    outline = Outline70,
    outlineVariant = OutlineVariant30,
    surfaceContainer = SurfaceContainerDark,
    error = Error80,
)

private val LocalBannerContainer = staticCompositionLocalOf { BannerLight }

/** The tinted primary background of a banner (UI-SPEC section 3): #E3EFE4 in light, #1E2B1F in dark. */
val ColorScheme.bannerContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBannerContainer.current

@Composable
fun CheckListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBannerContainer provides if (darkTheme) BannerDark else BannerLight) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CheckListTypography,
            content = content,
        )
    }
}
