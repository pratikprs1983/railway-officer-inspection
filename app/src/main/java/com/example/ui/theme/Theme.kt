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

// Clean official Sonpur Connect Indian Railways Orange & White Theme
private val SonpurConnectColorScheme =
  lightColorScheme(
    primary = PrimaryOrange,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryAmber,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryGreen,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRedDark,
    background = BackgroundLight,        // #F8FAFC Clean administrative light
    onBackground = OnSurfaceLight,      // #0F172A Deep dark slate (100% visible)
    surface = SurfaceLight,             // #FFFFFF Pure crisp white
    onSurface = OnSurfaceLight,         // #0F172A Deep dark slate (100% visible)
    surfaceVariant = SurfaceVariantLight, // #FFF7ED Soft warm cream/orange tint
    onSurfaceVariant = OnSurfaceVariantLight, // #475569 Medium slate
    outline = OutlineLight,             // #CBD5E1 Clean subtle border
    outlineVariant = OutlineVariantLight // #E2E8F0 Soft divider border
  )

@Composable
fun MyApplicationTheme(
  // Enforce the official Sonpur Connect Orange & White brand identity
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = SonpurConnectColorScheme,
    typography = Typography,
    content = content
  )
}
