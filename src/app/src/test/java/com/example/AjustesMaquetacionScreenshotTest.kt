package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ajustes en un teléfono angosto (360 dp) y alto, para ver todas las filas de notificaciones. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2600dp-xhdpi", sdk = [36])
class AjustesMaquetacionScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun filasDeNotificacionesSinSuperposicion() {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        compose.setContent { GRANEXTheme { SettingsScreen(onBack = {}) } }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/ajustes_360dp.png")
    }

    /** Con la letra del sistema agrandada (1,3×): la app la ignora y escala según la pantalla. */
    @Test
    @Config(qualifiers = "w360dp-h3400dp-xhdpi", fontScale = 1.3f)
    fun conLetraGrande() {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        compose.setContent { GRANEXTheme { SettingsScreen(onBack = {}) } }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/ajustes_360dp_letra130.png")
    }
}
