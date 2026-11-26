package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colores de marca usados directamente en las pantallas. Cada uno lee el tema activo de
 * AppThemeState (estado observable de Compose): al cambiar de tema en Ajustes, todo lo que los usa
 * se recompone o redibuja en el momento, sin reiniciar la app.
 */
val AgroGreenPrimary: Color get() = AppThemeState.marca.primario
val AgroGreenDark: Color get() = AppThemeState.marca.primarioOscuro
val AgroGreenLight: Color get() = AppThemeState.marca.primarioClaro
val AgroGoldAccent: Color get() = AppThemeState.marca.acento
val AgroGoldLight: Color get() = AppThemeState.marca.acentoClaro
val AgroSkyBlue: Color get() = AppThemeState.marca.info
