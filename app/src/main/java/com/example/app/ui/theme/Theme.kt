
package com.example.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RoadTripLightColorScheme = lightColorScheme(
    primary = RoadTripPrimary,
    onPrimary = RoadTripOnPrimary,

    primaryContainer = RoadTripSurfaceVariant,
    onPrimaryContainer = RoadTripText,

    secondary = RoadTripSecondary,
    onSecondary = RoadTripOnPrimary,

    tertiary = RoadTripSuccess,
    onTertiary = RoadTripOnPrimary,

    background = RoadTripBackground,
    onBackground = RoadTripText,

    surface = RoadTripSurface,
    onSurface = RoadTripText,

    surfaceVariant = RoadTripSurfaceVariant,
    onSurfaceVariant = RoadTripTextSecondary,

    outline = RoadTripOutline,

    error = RoadTripError,
    onError = RoadTripOnPrimary
)

@Composable
fun APPTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoadTripLightColorScheme,
        typography = Typography,
        content = content
    )
}
