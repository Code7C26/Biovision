package com.example

import com.example.data.remote.supabase.DatosEstacion
import com.example.data.remote.supabase.ErrorEstaciones
import com.example.data.remote.supabase.EstacionDeCuenta
import com.example.data.remote.supabase.EstacionMeteoRemota
import com.example.data.remote.supabase.LecturaEstacion
import com.example.data.remote.supabase.ResultadoAgregarEstacion
import com.example.data.remote.supabase.ServicioEstaciones
import com.example.ui.viewmodel.EstadoAgregarEstacion
import com.example.ui.viewmodel.EstadoDatosEstacion
import com.example.ui.viewmodel.EstadoEstaciones
import com.example.ui.viewmodel.EstacionesViewModel
import com.example.utils.MarcaEstacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Estaciones propias: lista, selección recordada, alta con elección de estación y baja. */
@OptIn(ExperimentalCoroutinesApi::class)
class EstacionesViewModelTest {

    private class Servidor : ServicioEstaciones {
        val estaciones = mutableListOf<EstacionMeteoRemota>()
        var pedidosDatos = 0
        val enviados = mutableListOf<Pair<Map<String, String>, String?>>()
        var errorAlAgregar: String? = null

        override suspend fun listar() = estaciones.toList()

        override suspend fun agregar(
            marca: MarcaEstacion,
            nombre: String,
            credenciales: Map<String, String>,
            identificador: String?
        ): ResultadoAgregarEstacion {
            enviados += credenciales to identificador
            errorAlAgregar?.let { throw ErrorEstaciones(it) }
            if (marca == MarcaEstacion.DAVIS && identificador == null) {
                return ResultadoAgregarEstacion.Elegir(listOf(EstacionDeCuenta("1", "Casa"), EstacionDeCuenta("2", "Lote 4")))
            }
            val nueva = EstacionMeteoRemota("id${estaciones.size + 1}", nombre, marca.clave, identificador)
            estaciones += nueva
            return ResultadoAgregarEstacion.Agregada(nueva, if (marca == MarcaEstacion.ENVIO) "codigo-secreto" else null)
        }

        override suspend fun datos(id: String): DatosEstacion {
            pedidosDatos++
            return DatosEstacion(actual = LecturaEstacion("2026-11-16T13:00:00Z", temperaturaC = if (id == "a") 20.0 else 15.0))
        }

        override suspend fun eliminar(id: String) {
            estaciones.removeAll { it.id == id }
        }
    }

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test
    fun sinBackendNoHayEstaciones() {
        val vm = EstacionesViewModel(null)
        assertEquals(EstadoEstaciones.SinBackend, vm.estaciones.value)
    }

    @Test
    fun recuerdaLaEstacionElegidaYNoRepideLoQueYaTiene() {
        val s = Servidor().apply {
            estaciones += EstacionMeteoRemota("a", "Casa", "ecowitt")
            estaciones += EstacionMeteoRemota("b", "Lote 4", "davis")
        }
        var guardada: String? = "b"
        val vm = EstacionesViewModel(s, leerSeleccion = { guardada }, guardarSeleccion = { guardada = it }, ahora = { 1_000L })

        assertEquals("b", vm.seleccionada.value)
        assertEquals(15.0, (vm.datos.value as EstadoDatosEstacion.Listo).datos.actual!!.temperaturaC)

        vm.seleccionar("a")
        assertEquals("a", guardada)
        assertEquals(20.0, (vm.datos.value as EstadoDatosEstacion.Listo).datos.actual!!.temperaturaC)
        assertEquals(2, s.pedidosDatos)

        // Volver a la primera usa lo que ya estaba cargado
        vm.seleccionar("b")
        assertEquals(15.0, (vm.datos.value as EstadoDatosEstacion.Listo).datos.actual!!.temperaturaC)
        assertEquals(2, s.pedidosDatos)

        // Actualizar sí vuelve a pedir
        vm.actualizar()
        assertEquals(3, s.pedidosDatos)
    }

    @Test
    fun conVariasEstacionesEnLaCuentaPideElegirYRepiteConLasMismasClaves() {
        val s = Servidor()
        val vm = EstacionesViewModel(s)
        assertEquals(EstadoEstaciones.Lista(emptyList()), vm.estaciones.value)

        vm.conectar(MarcaEstacion.DAVIS, "Campo", mapOf("apiKey" to "k", "apiSecret" to "s"))
        val elegir = vm.agregar.value as EstadoAgregarEstacion.Elegir
        assertEquals(2, elegir.opciones.size)

        vm.elegir(elegir.opciones[1])
        val listo = vm.agregar.value as EstadoAgregarEstacion.Listo
        assertEquals("2", listo.estacion.identificador)
        assertNull(listo.token)
        assertEquals(mapOf("apiKey" to "k", "apiSecret" to "s") to "2", s.enviados.last())
        assertEquals(listOf(listo.estacion), (vm.estaciones.value as EstadoEstaciones.Lista).estaciones)
        assertEquals(listo.estacion.id, vm.seleccionada.value)

        vm.terminarAgregar()
        assertEquals(EstadoAgregarEstacion.Inactivo, vm.agregar.value)
        // Después de terminar ya no queda nada para reenviar
        vm.elegir(elegir.opciones[0])
        assertEquals(EstadoAgregarEstacion.Inactivo, vm.agregar.value)
    }

    @Test
    fun elEnvioDirectoDevuelveElCodigoUnaVez() {
        val s = Servidor()
        val vm = EstacionesViewModel(s)
        vm.conectar(MarcaEstacion.ENVIO, "Galpón", emptyMap())
        assertEquals("codigo-secreto", (vm.agregar.value as EstadoAgregarEstacion.Listo).token)
    }

    @Test
    fun losErroresDelServidorSeMuestranTalCual() {
        val s = Servidor().apply { errorAlAgregar = "La API Key de Ecowitt no es válida." }
        val vm = EstacionesViewModel(s)
        vm.conectar(MarcaEstacion.ECOWITT, "Campo", mapOf("applicationKey" to "a", "apiKey" to "b"))
        assertEquals("La API Key de Ecowitt no es válida.", (vm.agregar.value as EstadoAgregarEstacion.Error).mensaje)
        vm.descartarError()
        assertEquals(EstadoAgregarEstacion.Inactivo, vm.agregar.value)
    }

    @Test
    fun alDesconectarPasaALaSiguiente() {
        val s = Servidor().apply {
            estaciones += EstacionMeteoRemota("a", "Casa", "ecowitt")
            estaciones += EstacionMeteoRemota("b", "Lote 4", "davis")
        }
        val vm = EstacionesViewModel(s)
        assertEquals("a", vm.seleccionada.value)
        vm.eliminar("a")
        assertEquals(listOf("b"), (vm.estaciones.value as EstadoEstaciones.Lista).estaciones.map { it.id })
        assertEquals("b", vm.seleccionada.value)
        vm.eliminar("b")
        assertNull(vm.seleccionada.value)
        assertTrue(vm.datos.value is EstadoDatosEstacion.Ninguno)
    }
}
