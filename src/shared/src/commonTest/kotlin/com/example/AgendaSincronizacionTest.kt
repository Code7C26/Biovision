package com.example

import com.example.data.agenda.AlmacenAgenda
import com.example.data.agenda.Aplicacion
import com.example.data.agenda.ArchivosAgenda
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.DatosAgenda
import com.example.data.agenda.Gasto
import com.example.data.agenda.Moneda
import com.example.data.agenda.PersistenciaAgenda
import com.example.data.agenda.SincronizadorAgenda
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guardado de la Agenda y su copia en la nube entre dos dispositivos. */
class AgendaSincronizacionTest {

    private class Memoria : PersistenciaAgenda {
        var texto: String? = null
        override suspend fun leer() = texto
        override suspend fun escribir(texto: String) { this.texto = texto }
        override suspend fun borrar() { texto = null }
    }

    /** La carpeta del usuario en Storage. */
    private class Nube : ArchivosAgenda {
        val archivos = linkedMapOf<String, ByteArray>()
        override suspend fun listar(prefijo: String) = archivos.keys.filter { it.startsWith(prefijo) }
        override suspend fun subir(nombre: String, contenido: ByteArray) { archivos[nombre] = contenido }
        override suspend fun bajar(nombre: String) = archivos.getValue(nombre)
        override suspend fun borrar(nombre: String) { archivos.remove(nombre) }
    }

    private class Reloj(var ms: Long = 1_000) { fun avanzar(): Long = ++ms }

    private fun gasto(id: String, producto: String = "Gasoil", modificado: Long = 0) = Gasto(
        id, "2026-11-21", CategoriaGasto.COMBUSTIBLE, producto, 100.0, "L", 1.2, Moneda.USD, 1200.0, modificadoEn = modificado
    )

    @Test
    fun elDocumentoSeGuardaYSeLeeIgual() = runTest {
        val memoria = Memoria()
        val reloj = Reloj()
        val a = AlmacenAgenda(memoria, reloj::avanzar)
        a.guardarGasto(gasto("1").copy(proveedor = "Agro; \"Norte\"", notas = "con\nsalto"))
        a.guardarAplicacion(Aplicacion("x", "2026-11-25", "Gasoil", "L", 10.0))
        val otro = AlmacenAgenda(memoria, reloj::avanzar)
        otro.cargar()
        assertEquals(a.datos.value, otro.datos.value)
        assertTrue(otro.pendiente.value, "lo no subido sigue pendiente al reabrir")
    }

    @Test
    fun unDocumentoIlegibleNoImpideAbrir() = runTest {
        val memoria = Memoria().apply { texto = "{esto no es json" }
        val a = AlmacenAgenda(memoria, { 1L })
        a.cargar()
        assertEquals(DatosAgenda(), a.datos.value)
    }

    @Test
    fun cadaCambioAvisaParaSincronizar() = runTest {
        var avisos = 0
        val a = AlmacenAgenda(Memoria(), { 1L }) { avisos++ }
        a.guardarGasto(gasto("1"))
        a.borrarGasto("1")
        assertEquals(2, avisos)
    }

    @Test
    fun loDeUnTelefonoLlegaAlOtro() = runTest {
        val nube = Nube()
        val reloj = Reloj()
        val telefono = AlmacenAgenda(Memoria(), reloj::avanzar)
        val web = AlmacenAgenda(Memoria(), reloj::avanzar)
        telefono.guardarGasto(gasto("1"))
        SincronizadorAgenda(telefono, nube, reloj::avanzar).sincronizar()
        assertFalse(telefono.pendiente.value)
        assertEquals(1, nube.archivos.size)

        SincronizadorAgenda(web, nube, reloj::avanzar).sincronizar()
        assertEquals(listOf("1"), web.datos.value.gastos.map { it.id })
        assertFalse(web.pendiente.value, "lo que bajó no hay que volver a subirlo")
    }

    @Test
    fun dosDispositivosSinConexionNoSePisan() = runTest {
        val nube = Nube()
        val reloj = Reloj()
        val telefono = AlmacenAgenda(Memoria(), reloj::avanzar)
        val web = AlmacenAgenda(Memoria(), reloj::avanzar)
        // Los dos parten de lo mismo
        telefono.guardarGasto(gasto("compartido"))
        SincronizadorAgenda(telefono, nube, reloj::avanzar).sincronizar()
        SincronizadorAgenda(web, nube, reloj::avanzar).sincronizar()

        // Sin conexión: cada uno anota algo y el teléfono borra el compartido
        telefono.guardarGasto(gasto("del_telefono"))
        telefono.borrarGasto("compartido")
        web.guardarGasto(gasto("de_la_web"))

        SincronizadorAgenda(telefono, nube, reloj::avanzar).sincronizar()
        SincronizadorAgenda(web, nube, reloj::avanzar).sincronizar()
        SincronizadorAgenda(telefono, nube, reloj::avanzar).sincronizar()

        val esperado = setOf("del_telefono", "de_la_web")
        assertEquals(esperado, telefono.datos.value.gastos.map { it.id }.toSet())
        assertEquals(esperado, web.datos.value.gastos.map { it.id }.toSet())
        assertEquals(1, nube.archivos.size, "las versiones viejas se borran")
    }

    @Test
    fun alJuntarGanaLaEdicionMasNueva() {
        val local = DatosAgenda(gastos = listOf(gasto("1", "Gasoil viejo", modificado = 5), gasto("2", "Local", modificado = 9)))
        val nube = DatosAgenda(gastos = listOf(gasto("1", "Gasoil nuevo", modificado = 7), gasto("2", "Nube", modificado = 9)))
        val r = SincronizadorAgenda.fusionar(local, nube).gastos.associate { it.id to it.producto }
        assertEquals("Gasoil nuevo", r["1"])
        assertEquals("Local", r["2"], "a igual hora gana la de este dispositivo")
    }

    @Test
    fun unCambioDuranteLaSubidaQuedaPendiente() = runTest {
        val reloj = Reloj()
        lateinit var almacen: AlmacenAgenda
        var anotarDurante = true
        val nube = object : ArchivosAgenda by Nube() {
            override suspend fun subir(nombre: String, contenido: ByteArray) {
                // Mientras se sube, el usuario anota otro gasto
                if (anotarDurante) { anotarDurante = false; almacen.guardarGasto(gasto("durante")) }
            }
        }
        almacen = AlmacenAgenda(Memoria(), reloj::avanzar)
        almacen.guardarGasto(gasto("antes"))
        SincronizadorAgenda(almacen, nube, reloj::avanzar).sincronizar()
        assertTrue(almacen.pendiente.value, "lo anotado durante la subida todavía no está en la nube")
    }

    @Test
    fun vaciarBorraTodoDelDispositivo() = runTest {
        val memoria = Memoria()
        val a = AlmacenAgenda(memoria, { 1L })
        a.guardarGasto(gasto("1"))
        a.vaciar()
        assertEquals(DatosAgenda(), a.datos.value)
        assertFalse(a.pendiente.value)
        assertEquals(null, memoria.texto)
    }
}
