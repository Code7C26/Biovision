package com.example

import com.example.data.repository.AccionAgronomia
import com.example.data.repository.AlmacenAgronomia
import com.example.data.repository.ArchivosRemotos
import com.example.data.repository.MapaRindeLote
import com.example.data.repository.PlanAgronomia
import com.example.data.repository.SincronizadorAgronomia
import com.example.data.repository.SueloLote
import com.example.utils.vra.CultivoVra
import com.example.utils.vra.MuestraSuelo
import com.example.utils.vra.PuntoRinde
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Suelo, rinde y dosis por lote en la nube: plan de la pasada y sincronización entre dos teléfonos. */
@RunWith(RobolectricTestRunner::class)
class SincronizacionAgronomiaTest {

    @get:Rule val carpeta = TemporaryFolder()

    /** Carpeta del usuario en Storage, en memoria. */
    private class NubeFalsa : ArchivosRemotos {
        val archivos = linkedMapOf<String, ByteArray>()
        var falla = false
        override suspend fun listar(prefijo: String) = archivos.keys.filter { it.startsWith(prefijo) }
        override suspend fun subir(nombre: String, contenido: ByteArray) {
            check(!falla) { "sin red" }
            archivos[nombre] = contenido
        }
        override suspend fun bajar(nombre: String) = archivos.getValue(nombre)
        override suspend fun borrar(nombre: String) {
            archivos.remove(nombre)
        }
    }

    private fun mapa() = MapaRindeLote(
        id = "1700000000000",
        etiqueta = "Maíz 2024/25",
        cultivo = CultivoVra.entries.first(),
        puntos = listOf(PuntoRinde(-33.0, -61.0, 9.5), PuntoRinde(-33.001, -61.001, 8.2)),
        descartados = 3,
        archivo = "rinde.csv"
    )

    @Test
    fun nombresYPlan() {
        assertEquals("u-1" to 42L, PlanAgronomia.leer(PlanAgronomia.nombre("u-1", 42)))
        assertNull(PlanAgronomia.leer("otro.zip"))

        val remotos = listOf(PlanAgronomia.nombre("a", 10), PlanAgronomia.nombre("a", 20), PlanAgronomia.nombre("b", 5), PlanAgronomia.nombre("z", 1))
        val plan = PlanAgronomia.planificar(
            lotes = mapOf("db_1" to "a", "db_2" to "b"),
            pendientes = setOf("db_2"),
            borrados = setOf("z"),
            versionLocal = { if (it == "a") 10L else 0L },
            remotos = remotos
        )
        // "a" tiene una versión más nueva en la nube: se baja y se borra la vieja
        assertTrue(AccionAgronomia.Bajar("db_1", "a", PlanAgronomia.nombre("a", 20), 20) in plan)
        assertTrue(AccionAgronomia.BorrarRemoto(PlanAgronomia.nombre("a", 10)) in plan)
        // "b" tiene cambios locales: se sube y la de la nube se reemplaza
        assertTrue(AccionAgronomia.Subir("db_2", "b") in plan)
        assertTrue(AccionAgronomia.BorrarRemoto(PlanAgronomia.nombre("b", 5)) in plan)
        // "z" es de un lote borrado
        assertTrue(AccionAgronomia.BorrarRemoto(PlanAgronomia.nombre("z", 1)) in plan)
        assertEquals(5, plan.size)
    }

    @Test
    fun losDatosPasanDeUnTelefonoAOtro() = runBlocking {
        val nube = NubeFalsa()
        var reloj = 1_000L
        val telefonoA = AlmacenAgronomia(carpeta.newFolder("a"))
        val telefonoB = AlmacenAgronomia(carpeta.newFolder("b"))
        // El mismo lote tiene otro id local en cada teléfono; el uuid es el de la nube
        val syncA = SincronizadorAgronomia(telefonoA, nube, { mapOf("db_7" to "lote-uuid") }) { reloj }
        val syncB = SincronizadorAgronomia(telefonoB, nube, { mapOf("db_3" to "lote-uuid") }) { reloj }

        telefonoA.guardarRinde("db_7", mapa())
        telefonoA.guardarSuelo("db_7", SueloLote(manual = mapOf(1 to MuestraSuelo(12.0, 40.0, 2.5, 6.1)), firmaManual = "f1"))
        assertEquals(setOf("db_7"), telefonoA.pendientes())
        assertNull(syncA.sincronizar())
        assertTrue(telefonoA.pendientes().isEmpty())
        assertEquals(listOf(PlanAgronomia.nombre("lote-uuid", 1_000)), nube.archivos.keys.toList())

        assertNull(syncB.sincronizar())
        assertEquals(1, telefonoB.rindes("db_3").size)
        assertEquals(2, telefonoB.rindes("db_3").first().puntos.size)
        assertEquals(12.0, telefonoB.suelo("db_3").manual[1]!!.pBrayPpm!!, 1e-9)

        // B cambia el suelo: A lo recibe y en la nube queda una sola versión
        reloj = 2_000L
        telefonoB.guardarSuelo("db_3", SueloLote(manual = mapOf(1 to MuestraSuelo(18.0, null, null, null)), firmaManual = "f1"))
        assertNull(syncB.sincronizar())
        assertNull(syncA.sincronizar())
        assertEquals(18.0, telefonoA.suelo("db_7").manual[1]!!.pBrayPpm!!, 1e-9)
        assertEquals(listOf(PlanAgronomia.nombre("lote-uuid", 2_000)), nube.archivos.keys.toList())
    }

    @Test
    fun siLaSubidaFallaNoSeBorraLaVersionDeLaNube() = runBlocking {
        val nube = NubeFalsa()
        var reloj = 1_000L
        val telefono = AlmacenAgronomia(carpeta.newFolder("a"))
        val sync = SincronizadorAgronomia(telefono, nube, { mapOf("db_1" to "u") }) { reloj }
        telefono.guardarRinde("db_1", mapa())
        sync.sincronizar()

        reloj = 2_000L
        telefono.borrarRinde("db_1", mapa().id)
        nube.falla = true
        assertTrue(sync.sincronizar() != null)
        assertEquals(listOf(PlanAgronomia.nombre("u", 1_000)), nube.archivos.keys.toList())
        assertEquals(setOf("db_1"), telefono.pendientes())
    }

    @Test
    fun borrarElLoteBorraSusDatosDeLaNube() = runBlocking {
        val nube = NubeFalsa()
        val telefono = AlmacenAgronomia(carpeta.newFolder("a"))
        var lotes = mapOf("db_1" to "u")
        val sync = SincronizadorAgronomia(telefono, nube, { lotes }) { 1_000L }
        telefono.guardarRinde("db_1", mapa())
        sync.sincronizar()
        assertEquals(1, nube.archivos.size)

        telefono.borrarLote("db_1", "u")
        lotes = emptyMap()
        assertNull(sync.sincronizar())
        assertTrue(nube.archivos.isEmpty())
        assertTrue(telefono.borrados().isEmpty())
        assertTrue(telefono.rindes("db_1").isEmpty())
    }

    @Test
    fun unZipConRutasRarasNoEscribeFueraDeLaCarpeta() {
        val telefono = AlmacenAgronomia(carpeta.newFolder("a"))
        val salida = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(salida).use { zip ->
            for (nombre in listOf("../fuera.json", "suelo.json")) {
                zip.putNextEntry(java.util.zip.ZipEntry(nombre))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }
        telefono.desempaquetar("db_1", salida.toByteArray())
        assertTrue(!java.io.File(carpeta.root, "fuera.json").exists())
        assertTrue(telefono.tieneDatos("db_1"))
    }
}
