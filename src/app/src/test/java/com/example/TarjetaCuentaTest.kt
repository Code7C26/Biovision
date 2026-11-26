package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.sync.AvisoCuenta
import com.example.data.sync.OperacionCuenta
import com.example.data.sync.TipoAviso
import com.example.ui.screens.AccionesCuenta
import com.example.ui.screens.TarjetaCuenta
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
class TarjetaCuentaTest {

    @get:Rule val compose = createComposeRule()

    private val llamadas = mutableListOf<String>()
    private val acciones = AccionesCuenta(
        ingresar = { correo, clave -> llamadas += "ingresar $correo $clave" },
        registrarse = { correo, clave, confirmacion -> llamadas += "registrarse $correo $clave $confirmacion" },
        sincronizar = { llamadas += "sincronizar" },
        cerrarSesion = { llamadas += "cerrar" },
        descartarAviso = { llamadas += "descartar" }
    )

    private fun mostrar(estado: EstadoCuenta, aviso: AvisoCuenta? = null, pendientes: Int = 0) {
        compose.setContent {
            // Con scroll, como dentro de Ajustes: el formulario de "Crear cuenta" no entra en la pantalla de prueba
            GRANEXTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TarjetaCuenta(estado, OperacionCuenta.NINGUNA, aviso, pendientes, acciones)
                }
            }
        }
    }

    @Test
    fun ingresarValidaLosCamposAntesDeEnviar() {
        mostrar(EstadoCuenta.SinSesion)
        compose.onNodeWithTag("btn_cuenta_enviar").performClick()
        compose.onNodeWithText("Ingresá tu correo.").assertIsDisplayed()
        compose.onNodeWithText("Ingresá la contraseña.").assertIsDisplayed()
        assertTrue(llamadas.isEmpty())

        compose.onNodeWithTag("input_cuenta_correo").performTextInput("productor@campo.com")
        compose.onNodeWithTag("input_cuenta_clave").performTextInput("trigo2026")
        compose.onNodeWithTag("btn_cuenta_enviar").performClick()
        assertEquals(listOf("ingresar productor@campo.com trigo2026"), llamadas)
    }

    @Test
    fun crearCuentaPideRepetirLaContrasena() {
        mostrar(EstadoCuenta.SinSesion)
        compose.onNodeWithTag("tab_crear_cuenta").performClick()
        compose.onNodeWithTag("input_cuenta_correo").performTextInput("nuevo@campo.com")
        compose.onNodeWithTag("input_cuenta_clave").performTextInput("trigo2026")
        compose.onNodeWithTag("input_cuenta_confirmacion").performTextInput("trigo2025")
        compose.onNodeWithTag("btn_cuenta_enviar").performScrollTo().performClick()
        compose.onNodeWithText("Las contraseñas no coinciden.").performScrollTo().assertIsDisplayed()
        assertTrue(llamadas.none { it.startsWith("registrarse") })
    }

    @Test
    fun conSesionMuestraElCorreoYConfirmaAntesDeCerrar() {
        mostrar(EstadoCuenta.Conectado("u1", "productor@campo.com"), pendientes = 2)
        compose.onNodeWithText("productor@campo.com").assertIsDisplayed()
        compose.onNodeWithText("2 cambios pendientes de subir. Se suben solos cuando hay conexión.").assertIsDisplayed()

        compose.onNodeWithTag("btn_cerrar_sesion").performClick()
        assertTrue("pide confirmación antes de cerrar", llamadas.isEmpty())
        compose.onNodeWithTag("btn_confirmar_cierre").performClick()
        assertEquals(listOf("cerrar"), llamadas)
    }

    @Test
    fun cierreBloqueadoSeMuestraComoAlerta() {
        var aviso by mutableStateOf<AvisoCuenta?>(
            AvisoCuenta("No se puede cerrar sesión: hay 1 cambio local pendiente de sincronizar.", TipoAviso.CIERRE_BLOQUEADO)
        )
        compose.setContent {
            GRANEXTheme {
                TarjetaCuenta(EstadoCuenta.Conectado("u1", "productor@campo.com"), OperacionCuenta.NINGUNA, aviso, 1, acciones)
            }
        }
        compose.onNodeWithTag("dialogo_cierre_bloqueado").assertIsDisplayed()
        compose.onNodeWithText("No se puede cerrar sesión: hay 1 cambio local pendiente de sincronizar.").assertIsDisplayed()
        compose.onNodeWithTag("btn_reintentar_cierre").performClick()
        assertEquals(listOf("cerrar"), llamadas)
        aviso = null
    }
}
