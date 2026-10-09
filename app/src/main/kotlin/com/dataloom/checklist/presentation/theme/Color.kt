package com.dataloom.checklist.presentation.theme

import androidx.compose.ui.graphics.Color

// Fixed brand palette instead of dynamic colour so contrast stays predictable (WCAG AA, section 10).
// Follows the green of the app logo (UI-SPEC section 2): primary #00704A, dark primary #73FF9A,
// containers #B8F5CF and #005234, background #F7FCF8 and #0F1511. The neutrals are tinted to match.
internal val Green40 = Color(0xFF00704A)
internal val Green90 = Color(0xFFB8F5CF)
internal val Green80 = Color(0xFF73FF9A)
internal val Green20 = Color(0xFF002114)
internal val GreenOnDark = Color(0xFF00391E)
internal val GreenContainerDark = Color(0xFF005234)
internal val Neutral10 = Color(0xFF171D19)
internal val Neutral95 = Color(0xFFEBF2EC)
internal val Neutral99 = Color(0xFFF7FCF8)
internal val Neutral6 = Color(0xFF0F1511)
internal val Neutral90 = Color(0xFFDFE4DE)
internal val Error40 = Color(0xFFB3261E)
internal val Error80 = Color(0xFFF2B8B5)

// UI-SPEC section 2 additions (CL-250): borders, dividers, sheets and secondary text.
internal val Outline40 = Color(0xFF6A756D)
internal val Outline70 = Color(0xFF89938B)
internal val OutlineVariant80 = Color(0xFFBFC9C1)
internal val OutlineVariant30 = Color(0xFF3F4943)
internal val SurfaceContainerLight = Color(0xFFE8F0E9)
internal val SurfaceContainerDark = Color(0xFF1B211D)

// Tinted primary background of the Banner (UI-SPEC section 3); not a Material colour role.
internal val BannerLight = Color(0xFFE1F3E7)
internal val BannerDark = Color(0xFF17301F)

// Error, inverse (snackbar) and scrim roles from the mockup stylesheet (source/style.css).
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val OnErrorDark = Color(0xFF601410)
internal val ErrorContainerLight = Color(0xFFF9DEDC)
internal val OnErrorContainerLight = Color(0xFF410E0B)
internal val ErrorContainerDark = Color(0xFF8C1D18)
internal val OnErrorContainerDark = Color(0xFFF9DEDC)
internal val InverseSurfaceLight = Color(0xFF2C322E)
internal val InverseOnSurfaceLight = Color(0xFFEDF2EC)
internal val InversePrimaryLight = Color(0xFF73FF9A)
internal val InverseSurfaceDark = Color(0xFFDFE4DE)
internal val InverseOnSurfaceDark = Color(0xFF2C322E)
internal val InversePrimaryDark = Color(0xFF00704A)
internal val ScrimLight = Color(0x6B000000)
internal val ScrimDark = Color(0x99000000)

// High-contrast palette (Settings > High contrast). Every text pair is at least 7:1 (WCAG AAA) and
// every border at least 3:1 against its background; ContrastTest checks the numbers.
internal val HcBlack = Color(0xFF000000)
internal val HcWhite = Color(0xFFFFFFFF)

internal val HcLightPrimary = Color(0xFF003D0F)
internal val HcLightPrimaryContainer = Color(0xFFC4EFCB)
internal val HcLightOnPrimaryContainer = Color(0xFF00210A)
internal val HcLightSurfaceVariant = Color(0xFFEEEFEA)
internal val HcLightOnSurfaceVariant = Color(0xFF1A1E19)
internal val HcLightSurfaceContainer = Color(0xFFEAECE4)
internal val HcLightOutline = Color(0xFF3B4238)
internal val HcLightOutlineVariant = Color(0xFF5F665B)
internal val HcLightError = Color(0xFF8A0008)
internal val HcLightErrorContainer = Color(0xFFFFE0DC)
internal val HcLightOnErrorContainer = Color(0xFF3F0003)
internal val HcLightBanner = Color(0xFFDCEBDD)

internal val HcDarkPrimary = Color(0xFFB8F5CF)
internal val HcDarkOnPrimary = Color(0xFF00210A)
internal val HcDarkPrimaryContainer = Color(0xFF1B5128)
internal val HcDarkSurfaceVariant = Color(0xFF1B1E1A)
internal val HcDarkOnSurfaceVariant = Color(0xFFEDF1E8)
internal val HcDarkSurfaceContainer = Color(0xFF1F231E)
internal val HcDarkOutline = Color(0xFFC2C9BD)
internal val HcDarkOutlineVariant = Color(0xFF99A095)
internal val HcDarkError = Color(0xFFFFD2CF)
internal val HcDarkOnError = Color(0xFF3F0003)
internal val HcDarkErrorContainer = Color(0xFF7A1410)
internal val HcDarkBanner = Color(0xFF1F3322)
