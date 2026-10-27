package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// Sonpur Connect - Official Indian Railways Orange & White Theme Palette
// Clean, high-contrast, modern railway administrative interface
// ============================================================================

// Primary Brand Colors - Indian Railways Saffron / Orange & Crisp White
val PrimaryOrange = Color(0xFFEA580C)           // Official Saffron Orange (600)
val PrimaryOrangeLight = Color(0xFFF97316)      // Bright Saffron Orange (500)
val PrimaryOrangeDark = Color(0xFFC2410C)       // Deep Amber Rust (700)
val PrimaryContainerLight = Color(0xFFFFF7ED)   // Soft clean cream/orange container
val OnPrimaryContainerLight = Color(0xFF9A3412) // High-contrast deep rust orange text

// Backward compatibility aliases
val PrimaryNavy = PrimaryOrange
val PrimaryNavyLight = PrimaryOrangeLight
val PrimaryNavyDark = PrimaryOrangeDark
val PrimaryBlue = PrimaryOrange
val PrimaryLight = PrimaryOrangeLight
val PrimaryDark = PrimaryOrangeDark

// Secondary Colors - Professional Warm Amber & Terracotta
val SecondaryAmber = Color(0xFFD97706)          // Amber 600
val SecondaryLight = Color(0xFFF59E0B)          // Amber 500
val SecondaryDark = Color(0xFFB45309)           // Amber 700
val SecondaryContainerLight = Color(0xFFFEF3C7) // Very light warm amber container
val OnSecondaryContainerLight = Color(0xFF78350F)

val SecondarySteel = SecondaryAmber
val SecondaryTeal = SecondaryAmber

// Tertiary Accent - Indian Railways Railway Green & Gold
val TertiaryGreen = Color(0xFF15803D)           // Green 700
val TertiaryLight = Color(0xFF22C55E)           // Green 500
val TertiaryDark = Color(0xFF166534)            // Green 800
val TertiaryContainerLight = Color(0xFFDCFCE7)  // Soft light green container
val OnTertiaryContainerLight = Color(0xFF14532D)

val TertiaryAccent = TertiaryGreen
val TertiaryViolet = TertiaryGreen

// High-Contrast Text & Neutral Surfaces (Ensures 100% visibility on white boxes)
val BackgroundLight = Color(0xFFF8FAFC)        // Clean administrative crisp light gray
val SurfaceLight = Color(0xFFFFFFFF)           // Pure crisp white card/surface
val SurfaceVariantLight = Color(0xFFFFF7ED)    // Soft warm tint surface
val OnSurfaceLight = Color(0xFF0F172A)         // Deep dark charcoal - Maximum readability (never invisible)
val OnSurfaceVariantLight = Color(0xFF334155)  // High-contrast deep slate for metadata/captions (100% visible on white cards)
val OutlineLight = Color(0xFFCBD5E1)           // Clean subtle border
val OutlineVariantLight = Color(0xFFE2E8F0)    // Soft divider border

// Status & Semantic Colors (Clean, accessible, standard railway colors)
val SuccessGreen = Color(0xFF15803D)
val SuccessGreenLight = Color(0xFFDCFCE7)
val SuccessGreenDark = Color(0xFF14532D)

val SuccessEmerald = SuccessGreen
val SuccessEmeraldLight = SuccessGreenLight
val SuccessEmeraldDark = SuccessGreenDark

val WarningAmber = Color(0xFFD97706)
val WarningAmberLight = Color(0xFFFEF3C7)
val WarningAmberDark = Color(0xFF78350F)

val ErrorRed = Color(0xFFDC2626)
val ErrorRedLight = Color(0xFFFEE2E2)
val ErrorRedDark = Color(0xFF991B1B)

val ErrorRose = ErrorRed
val ErrorRoseLight = ErrorRedLight
val ErrorRoseDark = ErrorRedDark

val InfoBlue = Color(0xFF0284C7)
val InfoBlueLight = Color(0xFFE0F2FE)
val InfoBlueDark = Color(0xFF075985)

val InfoSky = InfoBlue
val InfoSkyLight = InfoBlueLight
val InfoSkyDark = InfoBlueDark

// Dark Theme Variants (maintains clear dark text on white textboxes and proper contrast)
val PrimaryDarkTheme = Color(0xFFFB923C)
val SecondaryDarkTheme = Color(0xFFFCD34D)
val TertiaryDarkTheme = Color(0xFF86EFAC)
val BackgroundDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155)
val OnSurfaceDark = Color(0xFFF1F5F9)
val OnSurfaceVariantDark = Color(0xFFCBD5E1)

// Clean Sonpur Connect Orange & White Gradients
val GradientHero = Brush.horizontalGradient(
    colors = listOf(Color(0xFFC2410C), Color(0xFFEA580C), Color(0xFFF97316))
)
val GradientOcean = Brush.horizontalGradient(
    colors = listOf(Color(0xFFC2410C), Color(0xFFEA580C))
)
val GradientOrange = GradientHero
val GradientPurple = GradientHero
val GradientSunset = Brush.horizontalGradient(
    colors = listOf(Color(0xFFEA580C), Color(0xFFFBBF24))
)
