package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.EnlaceExterno
import com.example.ui.components.EnlacesRegistro
import com.example.ui.theme.GRANEXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnlaceExternoTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun abreLaPaginaOficialDeRegistro() {
        val abiertas = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) { abiertas += uri }
        }
        compose.setContent {
            GRANEXTheme {
                CompositionLocalProvider(LocalUriHandler provides handler) {
                    EnlaceExterno("Registrate en Copernicus", EnlacesRegistro.COPERNICUS_REGISTRO, testTag = "link")
                }
            }
        }
        compose.onNodeWithTag("link").performClick()
        assertEquals(listOf(EnlacesRegistro.COPERNICUS_REGISTRO), abiertas)
    }

    @Test
    fun sinNavegadorNoCierraLaApp() {
        val sinNavegador = object : UriHandler {
            override fun openUri(uri: String) = throw IllegalArgumentException("No hay actividad para ACTION_VIEW")
        }
        compose.setContent {
            GRANEXTheme {
                CompositionLocalProvider(LocalUriHandler provides sinNavegador) {
                    EnlaceExterno("Copernicus", EnlacesRegistro.COPERNICUS_REGISTRO, testTag = "link")
                }
            }
        }
        compose.onNodeWithTag("link").performClick()
    }

    @Test
    fun todosLosEnlacesSonHttps() {
        listOf(
            EnlacesRegistro.COPERNICUS_REGISTRO, EnlacesRegistro.COPERNICUS_CLIENTES,
            EnlacesRegistro.MAPTILER_REGISTRO, EnlacesRegistro.OPEN_METEO_PLANES
        ).forEach { assertTrue(it, it.startsWith("https://")) }
    }
}
