package com.dataloom.checklist.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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
    surfaceContainerLowest = Neutral99,
    surfaceContainerLow = SurfaceContainerLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerLight,
    surfaceContainerHighest = SurfaceContainerLight,
    error = Error40,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
    scrim = ScrimLight,
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
    surfaceContainerLowest = Neutral6,
    surfaceContainerLow = SurfaceContainerDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerDark,
    surfaceContainerHighest = SurfaceContainerDark,
    error = Error80,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    scrim = ScrimDark,
)

// High contrast: pure white or black background, near-black or white text, 7:1 or better for every
// text pair and 3:1 or better for every border. The brand green stays, only darker or lighter.
internal val HighContrastLightColors = lightColorScheme(
    primary = HcLightPrimary,
    onPrimary = HcWhite,
    primaryContainer = HcLightPrimaryContainer,
    onPrimaryContainer = HcLightOnPrimaryContainer,
    background = HcWhite,
    onBackground = HcBlack,
    surface = HcWhite,
    onSurface = HcBlack,
    surfaceVariant = HcLightSurfaceVariant,
    onSurfaceVariant = HcLightOnSurfaceVariant,
    outline = HcLightOutline,
    outlineVariant = HcLightOutlineVariant,
    surfaceContainerLowest = HcWhite,
    surfaceContainerLow = HcLightSurfaceContainer,
    surfaceContainer = HcLightSurfaceContainer,
    surfaceContainerHigh = HcLightSurfaceContainer,
    surfaceContainerHighest = HcLightSurfaceContainer,
    error = HcLightError,
    onError = HcWhite,
    errorContainer = HcLightErrorContainer,
    onErrorContainer = HcLightOnErrorContainer,
    inverseSurface = HcBlack,
    inverseOnSurface = HcWhite,
    inversePrimary = HcDarkPrimary,
    scrim = ScrimDark,
)

internal val HighContrastDarkColors = darkColorScheme(
    primary = HcDarkPrimary,
    onPrimary = HcDarkOnPrimary,
    primaryContainer = HcDarkPrimaryContainer,
    onPrimaryContainer = HcWhite,
    background = HcBlack,
    onBackground = HcWhite,
    surface = HcBlack,
    onSurface = HcWhite,
    surfaceVariant = HcDarkSurfaceVariant,
    onSurfaceVariant = HcDarkOnSurfaceVariant,
    outline = HcDarkOutline,
    outlineVariant = HcDarkOutlineVariant,
    surfaceContainerLowest = HcBlack,
    surfaceContainerLow = HcDarkSurfaceContainer,
    surfaceContainer = HcDarkSurfaceContainer,
    surfaceContainerHigh = HcDarkSurfaceContainer,
    surfaceContainerHighest = HcDarkSurfaceContainer,
    error = HcDarkError,
    onError = HcDarkOnError,
    errorContainer = HcDarkErrorContainer,
    onErrorContainer = HcWhite,
    inverseSurface = HcWhite,
    inverseOnSurface = HcBlack,
    inversePrimary = HcLightPrimary,
    scrim = ScrimDark,
)

/** Colours the Material scheme has no role for. Read them through [MaterialTheme.extendedColors]. */
@Immutable
data class ExtendedColors(
    /** The tinted primary background of a banner (UI-SPEC section 3): #E3EFE4 light, #1E2B1F dark. */
    val bannerContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(bannerContainer = BannerLight)
internal val DarkExtendedColors = ExtendedColors(bannerContainer = BannerDark)
internal val HighContrastLightExtendedColors = ExtendedColors(bannerContainer = HcLightBanner)
internal val HighContrastDarkExtendedColors = ExtendedColors(bannerContainer = HcDarkBanner)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** The tinted primary background of a banner (UI-SPEC section 3). Name kept from the onboarding track. */
val ColorScheme.bannerContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current.bannerContainer

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

internal fun checkListColorScheme(darkTheme: Boolean, highContrast: Boolean): ColorScheme = when {
    highContrast && darkTheme -> HighContrastDarkColors
    highContrast -> HighContrastLightColors
    darkTheme -> DarkColors
    else -> LightColors
}

internal fun checkListExtendedColors(darkTheme: Boolean, highContrast: Boolean): ExtendedColors = when {
    highContrast && darkTheme -> HighContrastDarkExtendedColors
    highContrast -> HighContrastLightExtendedColors
    darkTheme -> DarkExtendedColors
    else -> LightExtendedColors
}

/**
 * The app theme.
 *
 * @param highContrast the 7:1 palette from Settings.
 * @param textScale the user's in-app text size step (1.0 normal), applied on top of the system font scale.
 */
@Composable
fun CheckListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaled = if (textScale == 1f) density else Density(density.density, density.fontScale * textScale)
    CompositionLocalProvider(
        LocalExtendedColors provides checkListExtendedColors(darkTheme, highContrast),
        LocalDensity provides scaled,
    ) {
        MaterialTheme(
            colorScheme = checkListColorScheme(darkTheme, highContrast),
            typography = CheckListTypography,
            shapes = CheckListShapes,
            content = content,
        )
    }
}
