package com.example

import com.example.data.remote.supabase.ArchivosLotes
import com.example.data.remote.supabase.Autenticacion
import com.example.data.remote.supabase.ConfiguracionSupabase
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.RemotoLotesSupabase
import com.example.data.remote.supabase.ResultadoRegistro
import com.example.data.remote.supabase.ServicioCuenta
import com.example.data.remote.supabase.SesionEnAjustes
import com.example.data.remote.supabase.ValidacionCuenta
import com.example.data.remote.supabase.crearClienteSupabase
import com.example.data.sync.AlmacenLotesAjustes
import com.example.data.sync.ControladorCuenta
import com.example.data.sync.MarcasSincronizacion
import com.example.data.sync.OperacionCuenta
import com.example.data.sync.SincronizadorLotes
import com.example.data.sync.TipoAviso
import com.example.data.sync.aRemoto
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CuentaTest {

    /** Supabase Auth en memoria: una sola cuenta "u1" con contraseña "clave123". */
    private class CuentaFalsa : Autenticacion {
        override val configurada = true
        override val estado = MutableStateFlow<EstadoCuenta>(EstadoCuenta.SinSesion)
        var usuario: String? = null
        var intentos = 0
        var pideConfirmacion = false
        var salidas = 0
        /** Si no es null, ingresar espera a que se complete (simula la demora de la red). */
        var demora: CompletableDeferred<Unit>? = null

        override suspend fun ingresar(correo: String, clave: String): Result<Unit> {
            intentos++
            demora?.await()
            if (clave != "clave123") return Result.failure(Exception("Correo o contraseña incorrectos."))
            usuario = "u1"
            estado.value = EstadoCuenta.Conectado("u1", correo)
            return Result.success(Unit)
        }

        override suspend fun registrarse(correo: String, clave: String): Result<ResultadoRegistro> {
            intentos++
            if (pideConfirmacion) return Result.success(ResultadoRegistro.CONFIRMAR_CORREO)
            ingresar(correo, clave)
            return Result.success(ResultadoRegistro.SESION_INICIADA)
        }

        override suspend fun salir(): Result<Unit> {
            salidas++
            usuario = null
            estado.value = EstadoCuenta.SinSesion
            return Result.success(Unit)
        }

        override suspend fun confirmarCorreo(correo: String, codigo: String): Result<Unit> {
            if (codigo != "123456") return Result.failure(Exception("El código no es correcto o venció. Pedí uno nuevo."))
            usuario = "u1"
            estado.value = EstadoCuenta.Conectado("u1", correo)
            return Result.success(Unit)
        }

        var eliminadas = 0
        /** Si no es null, eliminar falla con este mensaje (servidor caído). */
        var falloAlEliminar: String? = null

        override suspend fun eliminarCuenta(): Result<Unit> {
            falloAlEliminar?.let { return Result.failure(Exception(it)) }
            eliminadas++
            usuario = null
            estado.value = EstadoCuenta.SinSesion
            return Result.success(Unit)
        }
    }

    @Test
    fun laCuentaNuevaSeActivaConElCodigoDelCorreo() = runTest {
        val e = Escenario(this)
        e.cuenta.pideConfirmacion = true
        e.controlador.registrarse("nuevo@campo.com.ar", "clave123", "clave123")
        advanceUntilIdle()
        assertEquals("nuevo@campo.com.ar", e.controlador.correoPorConfirmar.value)
        assertEquals(TipoAviso.INFO, e.controlador.aviso.value?.tipo)
        assertTrue("código" in e.controlador.aviso.value!!.mensaje)

        e.controlador.confirmarCorreo("000000")
        advanceUntilIdle()
        assertEquals(TipoAviso.ERROR, e.controlador.aviso.value?.tipo)
        assertEquals("nuevo@campo.com.ar", e.controlador.correoPorConfirmar.value)

        e.controlador.confirmarCorreo("123456")
        advanceUntilIdle()
        assertNull(e.controlador.correoPorConfirmar.value)
        assertIs<EstadoCuenta.Conectado>(e.controlador.estado.value)
    }

    @Test
    fun alCerrarSesionSeBorraTodoAntesDeSalirYSinSubirNoSeSale() = runTest {
        val cuenta = CuentaFalsa()
        var bloqueo: String? = "hay análisis de suelo sin subir."
        val pasos = mutableListOf<String>()
        val controlador = ControladorCuenta(
            cuenta,
            sincronizador = null,
            alcance = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            borrarDatosLocales = { pasos += "borrar (salidas=${cuenta.salidas})" },
            antesDeCerrarSesion = { bloqueo }
        )
        controlador.ingresar("productor@campo.com.ar", "clave123")
        advanceUntilIdle()

        controlador.cerrarSesion()
        advanceUntilIdle()
        assertEquals(TipoAviso.CIERRE_BLOQUEADO, controlador.aviso.value?.tipo)
        assertIs<EstadoCuenta.Conectado>(controlador.estado.value)
        assertTrue(pasos.isEmpty(), "con datos sin subir no se borra nada")

        bloqueo = null
        controlador.cerrarSesion()
        advanceUntilIdle()
        assertEquals(listOf("borrar (salidas=0)"), pasos, "se borra antes de cerrar la sesión")
        assertEquals(1, cuenta.salidas)
        assertEquals(EstadoCuenta.SinSesion, controlador.estado.value)
    }

    @Test
    fun eliminarLaCuentaBorraLosLotesDelDispositivo() = runTest {
        val e = Escenario(this)
        e.controlador.ingresar("productor@campo.com.ar", "clave123")
        advanceUntilIdle()
        e.almacen.guardarCambio(lote("a", "Sin subir", 100))

        // Si el servidor falla no se borra nada local y la sesión sigue
        e.cuenta.falloAlEliminar = "No se pudo eliminar la cuenta."
        e.controlador.eliminarCuenta()
        advanceUntilIdle()
        assertEquals(TipoAviso.ERROR, e.controlador.aviso.value?.tipo)
        assertEquals(1, e.almacen.activos().size)
        assertIs<EstadoCuenta.Conectado>(e.controlador.estado.value)

        e.cuenta.falloAlEliminar = null
        e.controlador.eliminarCuenta()
        advanceUntilIdle()
        assertEquals(1, e.cuenta.eliminadas)
        assertEquals(TipoAviso.EXITO, e.controlador.aviso.value?.tipo)
        assertTrue(e.almacen.activos().isEmpty(), "los lotes del dispositivo se borran, también los pendientes")
        assertTrue(e.almacen.pendientes().isEmpty())
        assertEquals(OperacionCuenta.NINGUNA, e.controlador.operacion.value)
    }

    private class Escenario(alcance: TestScope) {
        val cuenta = CuentaFalsa()
        val servidor = ServidorFalso()
        val almacen = AlmacenLotesAjustes(MapSettings())
        var reintentos = 0
        val controlador = ControladorCuenta(
            cuenta,
            SincronizadorLotes(almacen, servidor, MarcasSincronizacion(MapSettings())) { cuenta.usuario },
            // Sin dispatcher de prueba "en pausa": lo que no espera a la red termina en el mismo llamado
            CoroutineScope(alcance.backgroundScope.coroutineContext + UnconfinedTestDispatcher(alcance.testScheduler)),
            programarReintento = { reintentos++ }
        )
    }

    @Test
    fun alIngresarSeSincronizaEnseguida() = runTest {
        val e = Escenario(this)
        // Un lote hecho sin cuenta en el teléfono y otro que ya estaba en la nube
        e.almacen.guardarCambio(lote("a", "Hecho sin cuenta", 100))
        e.servidor.editarDesdeOtroDispositivo(lote("b", "De la nube", 200).aRemoto("u1"))

        e.controlador.ingresar("productor@campo.com.ar", "clave123")
        advanceUntilIdle()

        assertIs<EstadoCuenta.Conectado>(e.controlador.estado.value)
        assertEquals(OperacionCuenta.NINGUNA, e.controlador.operacion.value)
        assertEquals(setOf("Hecho sin cuenta", "De la nube"), e.almacen.activos().map { it.nombre }.toSet())
        assertEquals("u1", e.servidor.filas["a"]!!.usuarioId)
        val aviso = assertNotNull(e.controlador.aviso.value)
        assertEquals(TipoAviso.EXITO, aviso.tipo)
        assertTrue("1 lote recibido" in aviso.mensaje && "1 cambio subido" in aviso.mensaje, aviso.mensaje)
    }

    @Test
    fun camposInvalidosYCredencialesIncorrectas() = runTest {
        val e = Escenario(this)
        e.controlador.ingresar("sin-arroba", "clave123")
        e.controlador.registrarse("productor@campo.com", "corta1", "corta1")
        advanceUntilIdle()
        assertEquals(0, e.cuenta.intentos, "con campos inválidos no se llama a Supabase")

        e.controlador.ingresar("productor@campo.com", "otra")
        advanceUntilIdle()
        assertEquals(TipoAviso.ERROR, e.controlador.aviso.value?.tipo)
        assertEquals(EstadoCuenta.SinSesion, e.controlador.estado.value)
        assertEquals(0, e.servidor.subidas)
    }

    @Test
    fun registroConConfirmacionPorCorreoNoSincroniza() = runTest {
        val e = Escenario(this)
        e.cuenta.pideConfirmacion = true
        e.controlador.registrarse("nuevo@campo.com", "clave123", "clave123")
        advanceUntilIdle()
        val aviso = assertNotNull(e.controlador.aviso.value)
        assertEquals(TipoAviso.INFO, aviso.tipo)
        assertTrue("nuevo@campo.com" in aviso.mensaje)
        assertEquals(EstadoCuenta.SinSesion, e.controlador.estado.value)
        assertEquals(0, e.servidor.subidas)
    }

    @Test
    fun cerrarSesionSinRedSeBloqueaYConRedVaciaElDispositivo() = runTest {
        val e = Escenario(this)
        e.controlador.ingresar("productor@campo.com", "clave123")
        advanceUntilIdle()
        e.almacen.guardarCambio(lote("a", "Editado sin señal", 300))

        e.servidor.sinRed = true
        e.controlador.cerrarSesion()
        advanceUntilIdle()
        val aviso = assertNotNull(e.controlador.aviso.value)
        assertEquals(TipoAviso.CIERRE_BLOQUEADO, aviso.tipo)
        assertTrue(aviso.mensaje.startsWith("No se puede cerrar sesión: hay 1 cambio local pendiente de sincronizar."), aviso.mensaje)
        assertIs<EstadoCuenta.Conectado>(e.controlador.estado.value)
        assertEquals(0, e.cuenta.salidas)
        assertEquals(1, e.almacen.pendientes().size)
        assertTrue(e.reintentos > 0, "queda programado el reintento para cuando vuelva la red")

        e.servidor.sinRed = false
        e.controlador.cerrarSesion()
        advanceUntilIdle()
        assertEquals(TipoAviso.EXITO, e.controlador.aviso.value?.tipo)
        assertEquals(EstadoCuenta.SinSesion, e.controlador.estado.value)
        assertEquals(1, e.cuenta.salidas)
        assertTrue(e.almacen.activos().isEmpty(), "los lotes de la cuenta no quedan en el dispositivo")
        assertEquals("Editado sin señal", e.servidor.filas["a"]!!.nombre)
        assertNull(e.controlador.ultimaSincronizacion.value)
    }

    @Test
    fun unaOperacionALaVez() = runTest {
        val e = Escenario(this)
        val red = CompletableDeferred<Unit>()
        e.cuenta.demora = red
        e.controlador.ingresar("productor@campo.com", "clave123")
        assertEquals(OperacionCuenta.INGRESANDO, e.controlador.operacion.value)
        e.controlador.ingresar("productor@campo.com", "clave123")
        e.controlador.cerrarSesion()
        red.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, e.cuenta.intentos)
        assertEquals(0, e.cuenta.salidas)
        assertEquals(OperacionCuenta.NINGUNA, e.controlador.operacion.value)
    }

    @Test
    fun validacionDeCampos() {
        assertFalse(ValidacionCuenta.validar(" productor@campo.com.ar ", "x", null, creandoCuenta = false).hayErrores)
        val registro = ValidacionCuenta.validar("a@b", "abcdefgh", "abcdefgi", creandoCuenta = true)
        assertNotNull(registro.correo)
        assertNotNull(registro.clave, "sin números")
        assertNotNull(registro.confirmacion)
        assertFalse(ValidacionCuenta.validar("a@campo.com", "trigo2026", "trigo2026", creandoCuenta = true).hayErrores)
    }

    /**
     * RLS solo protege si cada consulta lleva el token del usuario. Sin sesión, Postgrest va con la clave
     * pública (y no ve filas); después de ingresar, Postgrest y Storage van con el token de la sesión.
     */
    @Test
    fun lasConsultasVanConElTokenDeLaSesion() = runTest {
        val clavePublica = "sb_publishable_PruebaDeGranex123"
        val pedidos = mutableListOf<HttpRequestData>()
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val motor = MockEngine { pedido ->
            pedidos += pedido
            val ruta = pedido.url.encodedPath
            when {
                ruta.endsWith("/auth/v1/token") -> respond(
                    """{"access_token":"token-del-usuario","token_type":"bearer","expires_in":3600,
                       "refresh_token":"renovar","user":{"id":"u-1","aud":"authenticated","email":"productor@campo.com"}}""",
                    HttpStatusCode.OK, json
                )
                ruta.startsWith("/rest/v1/") -> respond("[]", HttpStatusCode.OK, json)
                ruta.startsWith("/storage/v1/") -> respond("""{"Key":"lotes/u-1/a.geojson","Id":"1"}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
        val cliente = crearClienteSupabase(
            assertNotNull(ConfiguracionSupabase.desde("https://proyecto.supabase.co", clavePublica)),
            SesionEnAjustes(MapSettings()),
            motor,
            cicloDeVida = false
        )
        try {
            val lotes = RemotoLotesSupabase(cliente)
            lotes.cambiosDesde(null)
            assertEquals("Bearer $clavePublica", pedidos.last().headers[HttpHeaders.Authorization])

            ServicioCuenta(cliente).ingresar("productor@campo.com", "clave123").getOrThrow()
            lotes.cambiosDesde(null)
            assertEquals("Bearer token-del-usuario", pedidos.last().headers[HttpHeaders.Authorization])

            ArchivosLotes(cliente).subir("a.geojson", "{}".encodeToByteArray())
            val subida = pedidos.last()
            assertEquals("Bearer token-del-usuario", subida.headers[HttpHeaders.Authorization])
            assertTrue(subida.url.encodedPath.endsWith("/storage/v1/object/lotes/u-1/a.geojson"), subida.url.encodedPath)
        } finally {
            cliente.close()
        }
    }
}
