package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.supabase.Autenticacion
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.PerfilRemoto
import com.example.data.remote.supabase.ResultadoRegistro
import com.example.data.remote.supabase.ValidacionPerfil
import com.example.ui.viewmodel.EstadoPerfil
import com.example.ui.viewmodel.PerfilViewModel
import com.example.ui.viewmodel.PerfilesRemotos
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Perfil del productor: se crea solo con los datos del registro, se pide si falta y no queda al salir. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PerfilViewModelTest {

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class Cuenta(var deLaCuenta: PerfilNuevo?) : Autenticacion {
        override val configurada = true
        override val estado = MutableStateFlow<EstadoCuenta>(EstadoCuenta.SinSesion)
        override suspend fun ingresar(correo: String, clave: String) = Result.success(Unit)
        override suspend fun registrarse(correo: String, clave: String) = Result.success(ResultadoRegistro.SESION_INICIADA)
        override suspend fun salir() = Result.success(Unit)
        override suspend fun eliminarCuenta() = Result.success(Unit)
        override fun perfilDeLaCuenta() = deLaCuenta
    }

    private class Tabla : PerfilesRemotos {
        var fila: PerfilRemoto? = null
        override suspend fun miPerfil() = fila
        override suspend fun guardar(perfil: PerfilRemoto) {
            fila = perfil
        }
    }

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test
    fun conLosDatosDelRegistroElPerfilSeCreaSolo() {
        val tabla = Tabla()
        val cuenta = Cuenta(PerfilNuevo("Ana Pérez", "La Esperanza", "Córdoba", "Alta Gracia", 320.0))
        val estado = MutableStateFlow<EstadoCuenta>(EstadoCuenta.SinSesion)
        val vm = PerfilViewModel(cuenta, tabla, estado, contexto)

        estado.value = EstadoCuenta.Conectado("u1", "ana@campo.com")
        val listo = vm.estado.value as EstadoPerfil.Listo
        assertEquals("La Esperanza", listo.perfil.establecimiento)
        assertEquals("ana@campo.com", listo.correo)
        assertEquals("Alta Gracia, Córdoba", tabla.fila?.ubicacionPrincipal)
        assertEquals("Ana Pérez", WeatherNotificationService.getUserName(contexto))

        // Al salir, el teléfono no muestra los datos de esa cuenta
        estado.value = EstadoCuenta.SinSesion
        assertEquals(EstadoPerfil.SinSesion, vm.estado.value)
        assertEquals("", WeatherNotificationService.getUserName(contexto))
    }

    @Test
    fun conGoogleSePideCompletarYSeGuarda() {
        val tabla = Tabla()
        // Google trae solo el nombre
        val cuenta = Cuenta(PerfilNuevo("Juan Gómez", "", ""))
        val estado = MutableStateFlow<EstadoCuenta>(EstadoCuenta.Conectado("u2", "juan@gmail.com"))
        val vm = PerfilViewModel(cuenta, tabla, estado, contexto)

        val incompleto = vm.estado.value as EstadoPerfil.Incompleto
        assertEquals("Juan Gómez", incompleto.sugerido.nombre)

        var guardado = false
        vm.guardar(PerfilNuevo("Juan Gómez", "Don Juan SA", "Santa Fe")) { guardado = true }
        assertTrue(guardado)
        assertTrue(vm.estado.value is EstadoPerfil.Listo)
        assertEquals("Don Juan SA", tabla.fila?.establecimiento)
    }

    @Test
    fun validacionYUbicacion() {
        val e = ValidacionPerfil.validar("", "x", "", "abc")
        assertTrue(e.nombre != null && e.provincia != null && e.hectareas != null && e.establecimiento == null)
        assertEquals(1234.5, ValidacionPerfil.hectareas("1.234,5")!!, 1e-9)
        val p = PerfilNuevo.desdeRemoto(PerfilRemoto("u", "Ana", "Est", "Alta Gracia, Córdoba", 10.0))
        assertEquals("Córdoba", p.provincia)
        assertEquals("Alta Gracia", p.localidad)
    }
}
