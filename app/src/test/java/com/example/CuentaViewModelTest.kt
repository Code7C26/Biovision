package com.example

import com.example.data.remote.supabase.Autenticacion
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.ResultadoRegistro
import com.example.data.sync.TipoAviso
import com.example.ui.viewmodel.CuentaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuentaViewModelTest {

    private class CuentaSinRed : Autenticacion {
        override val configurada = true
        override val estado = MutableStateFlow<EstadoCuenta>(EstadoCuenta.Conectado("u1", "productor@campo.com"))
        override suspend fun ingresar(correo: String, clave: String) = Result.success(Unit)
        override suspend fun registrarse(correo: String, clave: String) = Result.success(ResultadoRegistro.SESION_INICIADA)
        override suspend fun salir(): Result<Unit> {
            estado.value = EstadoCuenta.SinSesion
            return Result.success(Unit)
        }
        override suspend fun eliminarCuenta() = salir()
    }

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test
    fun unCambioNuevoQuitaElAvisoDeExitoViejo() {
        val pendientes = MutableStateFlow(0)
        val vm = CuentaViewModel(CuentaSinRed(), sincronizador = null, pendientes, programarReintento = {})
        vm.cerrarSesion()
        assertEquals(TipoAviso.EXITO, vm.aviso.value?.tipo)

        // Se crea un lote sin cuenta: "Sesión cerrada…" ya no describe el estado
        pendientes.value = 1
        assertNull(vm.aviso.value)
    }
}
