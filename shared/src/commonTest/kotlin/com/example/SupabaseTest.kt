package com.example

import com.example.data.remote.supabase.ArchivosLotes
import com.example.data.remote.supabase.ConfiguracionSupabase
import com.example.data.remote.supabase.JsonSupabase
import com.example.data.remote.supabase.LoteRemoto
import com.example.data.remote.supabase.RemotoLotes
import com.example.data.sync.AlmacenLotesAjustes
import com.example.data.sync.MarcasSincronizacion
import com.example.data.sync.RegistroLote
import com.example.data.sync.ResultadoCierreSesion
import com.example.data.sync.ResultadoSincronizacion
import com.example.data.sync.SincronizadorLotes
import com.example.data.sync.aRegistro
import com.example.data.sync.aRemoto
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SupabaseTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun jwt(rol: String): String {
        fun b64(s: String) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(s.encodeToByteArray())
        return b64("""{"alg":"HS256","typ":"JWT"}""") + "." +
            b64("""{"iss":"supabase","ref":"abcdefgh","role":"$rol","iat":1761400000,"exp":2076976000}""") + ".firma"
    }

    @Test
    fun configuracionSoloConClavePublicaYHttps() {
        val url = "https://abcdefgh.supabase.co/"
        assertEquals("https://abcdefgh.supabase.co", ConfiguracionSupabase.desde(url, jwt("anon"))?.url)
        assertNotNull(ConfiguracionSupabase.desde(url, "sb_publishable_123abc"))
        // La clave secreta saltea RLS: nunca se acepta en un cliente
        assertNull(ConfiguracionSupabase.desde(url, jwt("service_role")), "service_role")
        assertNull(ConfiguracionSupabase.desde(url, "sb_secret_123abc"), "sb_secret_")
        assertTrue(ConfiguracionSupabase.esClaveSecreta(jwt("service_role")))
        assertFalse(ConfiguracionSupabase.esClaveSecreta(jwt("anon")))
        // Sin configurar, valores de ejemplo o sin https: la app sigue solo con datos locales
        assertNull(ConfiguracionSupabase.desde("", ""))
        assertNull(ConfiguracionSupabase.desde(null, null))
        assertNull(ConfiguracionSupabase.desde("http://abcdefgh.supabase.co", jwt("anon")))
        assertNull(ConfiguracionSupabase.desde("https://TU-PROYECTO.supabase.co", "TU_CLAVE_ANON"))
        // La clave no aparece en logs
        assertFalse(jwt("anon") in ConfiguracionSupabase.desde(url, jwt("anon")).toString())
    }

    @Test
    fun filaEnSnakeCaseSinNulos() {
        val registro = RegistroLote(
            id = "6f1c0a52-8f4e-4c1b-9b0a-1d2e3f405162", nombre = "Lote Norte", provincia = "Santa Fe", superficieHa = 71.5,
            geojson = """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[-61,-33],[-60.99,-33],[-60.99,-33.01],[-61,-33]]]}}""",
            creadoEn = 1_761_400_000_000, modificadoEn = 1_761_400_500_000
        )
        val json = JsonSupabase.encodeToString(LoteRemoto.serializer(), registro.aRemoto("usuario-1"))
        val obj = JsonSupabase.parseToJsonElement(json).jsonObject
        assertEquals("usuario-1", obj["usuario_id"]!!.jsonPrimitive.content)
        assertEquals("71.5", obj["superficie_ha"]!!.jsonPrimitive.content)
        assertEquals("1761400500000", obj["modificado_en"]!!.jsonPrimitive.content)
        assertEquals("false", obj["eliminado"]!!.jsonPrimitive.content)
        assertEquals("Polygon", obj["geojson"]!!.jsonObject["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        // actualizado_en lo completa la base: un null no se envía
        assertFalse("actualizado_en" in obj)
        // Ida y vuelta sin perder datos
        val vuelta = JsonSupabase.decodeFromString(LoteRemoto.serializer(), json).aRegistro()
        assertEquals(registro.copy(pendiente = false), vuelta.copy(geojson = registro.geojson))
    }

    @Test
    fun rutaDeArchivoDentroDeLaCarpetaDelUsuario() {
        assertEquals("u1/lote.geojson", ArchivosLotes.rutaArchivo("u1", " lote.geojson "))
        assertFailsWith<IllegalArgumentException> { ArchivosLotes.rutaArchivo("u1", "../u2/lote.geojson") }
        assertFailsWith<IllegalArgumentException> { ArchivosLotes.rutaArchivo("u1", "..") }
        assertFailsWith<IllegalArgumentException> { ArchivosLotes.rutaArchivo("u1", "") }
    }

    // --- Sincronización ---

    @Test
    fun subeLoPendienteYBajaLoNuevo() = runTest {
        val servidor = ServidorFalso()
        val almacen = AlmacenLotesAjustes(MapSettings())
        var usuario: String? = null
        val sync = SincronizadorLotes(almacen, servidor, MarcasSincronizacion(MapSettings()), { usuario })

        // Sin cuenta: los lotes quedan en el dispositivo
        almacen.guardarCambio(lote("a", "Lote A", 100))
        assertEquals(ResultadoSincronizacion.SinSesion, sync.sincronizar())
        assertEquals(1, almacen.pendientes().size)

        // Al iniciar sesión se suben a esa cuenta
        usuario = "u1"
        assertEquals(ResultadoSincronizacion.Lista(subidos = 1, recibidos = 0, borrados = 0), sync.sincronizar())
        assertEquals("u1", servidor.filas["a"]!!.usuarioId)
        assertTrue(almacen.pendientes().isEmpty())

        // Otro dispositivo agrega un lote: llega en la próxima pasada
        servidor.editarDesdeOtroDispositivo(lote("b", "Lote B", 200).aRemoto("u1"))
        val r = sync.sincronizar()
        assertIs<ResultadoSincronizacion.Lista>(r)
        assertEquals(1, r.recibidos)
        assertEquals(listOf("Lote B", "Lote A"), almacen.activos().sortedBy { it.nombre }.reversed().map { it.nombre })

        // Una pasada más no cambia nada (idempotente)
        assertEquals(ResultadoSincronizacion.Lista(0, 0, 0), sync.sincronizar())
    }

    @Test
    fun conflictoGanaLaUltimaEdicionYLosBorradosViajan() = runTest {
        val servidor = ServidorFalso()
        val almacen = AlmacenLotesAjustes(MapSettings())
        val sync = SincronizadorLotes(almacen, servidor, MarcasSincronizacion(MapSettings()), { "u1" })
        almacen.guardarCambio(lote("a", "Original", 100))
        sync.sincronizar()

        // Sin red se edita en el teléfono (t=300) y en la nube ya había una edición más vieja (t=200)
        servidor.editarDesdeOtroDispositivo(lote("a", "Editado en la web", 200).aRemoto("u1"))
        almacen.guardarCambio(lote("a", "Editado en el campo", 300))
        sync.sincronizar()
        assertEquals("Editado en el campo", servidor.filas["a"]!!.nombre)
        assertEquals("Editado en el campo", almacen.porId("a")!!.nombre)

        // Al revés: la nube tiene la edición más nueva y se impone sobre la local vieja
        servidor.editarDesdeOtroDispositivo(lote("a", "Nuevo de la web", 500).aRemoto("u1"))
        almacen.guardarCambio(lote("a", "Viejo del teléfono", 400))
        sync.sincronizar()
        assertEquals("Nuevo de la web", servidor.filas["a"]!!.nombre)
        assertEquals("Nuevo de la web", almacen.porId("a")!!.nombre)

        // Borrado local: viaja como borrado lógico y después se purga del dispositivo
        almacen.marcarEliminado("a", ahora = 600)
        sync.sincronizar()
        assertTrue(servidor.filas["a"]!!.eliminado)
        assertNull(almacen.porId("a"))

        // Borrado en otro dispositivo
        almacen.guardarCambio(lote("c", "Lote C", 700))
        sync.sincronizar()
        servidor.editarDesdeOtroDispositivo(lote("c", "Lote C", 800).copy(eliminado = true).aRemoto("u1"))
        val r = sync.sincronizar()
        assertIs<ResultadoSincronizacion.Lista>(r)
        assertEquals(1, r.borrados)
        assertTrue(almacen.activos().isEmpty())
    }

    @Test
    fun sinRedNoSePierdeNadaYOtraCuentaNoSeMezcla() = runTest {
        val servidor = ServidorFalso()
        val almacen = AlmacenLotesAjustes(MapSettings())
        var usuario: String? = "u1"
        val marcas = MarcasSincronizacion(MapSettings())
        val sync = SincronizadorLotes(almacen, servidor, marcas, { usuario })

        servidor.sinRed = true
        almacen.guardarCambio(lote("a", "Lote A", 100))
        val error = sync.sincronizar()
        assertIs<ResultadoSincronizacion.Error>(error)
        assertEquals(1, error.pendientes)
        // Cerrar sesión con cambios sin subir no borra nada ni cierra la sesión
        var salio = false
        val bloqueada = sync.cerrarSesion { salio = true }
        assertIs<ResultadoCierreSesion.Bloqueada>(bloqueada)
        assertEquals(1, bloqueada.pendientes)
        assertNotNull(bloqueada.causa)
        assertFalse(salio)
        assertEquals(1, almacen.activos().size)

        // Vuelve la señal: se sube y recién ahí se puede cerrar sesión, que vacía el dispositivo
        servidor.sinRed = false
        assertEquals(ResultadoCierreSesion.Cerrada, sync.cerrarSesion { salio = true })
        assertTrue(salio)
        assertTrue(almacen.activos().isEmpty())
        assertNull(marcas.usuario)
        assertEquals(1, servidor.filas.size)

        // Si el dispositivo quedó con cambios sin subir de otra cuenta (su sesión venció), no se suben a la nueva
        usuario = "u1"
        sync.sincronizar()
        almacen.guardarCambio(lote("d", "De u1 sin subir", 900))
        usuario = "u2"
        val subidasAntes = servidor.subidas
        assertEquals(ResultadoSincronizacion.OtraCuenta, sync.sincronizar())
        assertEquals(subidasAntes, servidor.subidas)
        // Y cerrar la sesión de u2 no los borra: se suben cuando u1 vuelva a ingresar
        assertEquals(ResultadoCierreSesion.Cerrada, sync.cerrarSesion { })
        assertEquals("De u1 sin subir", almacen.porId("d")?.nombre)
        usuario = "u1"
        assertIs<ResultadoSincronizacion.Lista>(sync.sincronizar())
        assertEquals("u1", servidor.filas["d"]!!.usuarioId)
    }

    @Test
    fun lotesYaSincronizadosDeOtraCuentaSeQuitanAlIngresarOtra() = runTest {
        val servidor = ServidorFalso()
        val almacen = AlmacenLotesAjustes(MapSettings())
        var usuario: String? = "u1"
        val sync = SincronizadorLotes(almacen, servidor, MarcasSincronizacion(MapSettings()), { usuario })
        almacen.guardarCambio(lote("a", "De u1", 100))
        sync.sincronizar()

        // La sesión de u1 venció sin cerrarla e ingresa u2: no ve los lotes de u1 (siguen en su nube)
        usuario = "u2"
        servidor.verSolo = "u2"
        assertIs<ResultadoSincronizacion.Lista>(sync.sincronizar())
        assertTrue(almacen.activos().isEmpty())
        assertEquals("u1", servidor.filas["a"]!!.usuarioId)
    }

    @Test
    fun almacenLocalSobreviveDatosCorruptos() = runTest {
        val ajustes = MapSettings()
        ajustes.putString(AlmacenLotesAjustes.CLAVE, "{esto no es json")
        val almacen = AlmacenLotesAjustes(ajustes)
        assertTrue(almacen.activos().isEmpty())
        almacen.guardarCambio(lote("a", "Lote A", 1))
        assertEquals(1, almacen.activos().size)
    }
}
