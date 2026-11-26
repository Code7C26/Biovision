package com.example

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.AgroSatRepository
import com.example.data.repository.DetectorLotes
import com.example.data.repository.FuenteSentinel
import com.example.data.repository.LoteSatelital
import com.example.ui.viewmodel.DeteccionLote
import com.example.ui.viewmodel.SateliteViewModel
import com.example.utils.GeometriaLote
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import kotlin.math.abs

/** "Detectar lote": tocar el mapa, bajar dos fechas alrededor del toque y abrir el contorno en el editor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeteccionLoteTest {

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * Imágenes inventadas sobre el recuadro que pide el detector: un lote de 600 × 400 m (24 ha)
     * centrado en el toque, rodeado de otro cultivo. Hace un mes el lote recién emergía.
     */
    private inner class FuenteFalsa(private val configurada: Boolean = true) : FuenteSentinel {
        val pedidas: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun configurada() = configurada
        override suspend fun escenas(lote: LoteSatelital, dias: Int) = Result.success(
            listOf(
                EscenaSentinel("2026-10-30", 2.0, 0.7, 0.4, 0.6, 1000),
                EscenaSentinel("2026-10-25", 60.0, 0.5, 0.3, 0.4, 400), // nublada: no se usa
                EscenaSentinel("2026-09-28", 1.0, 0.4, 0.2, 0.3, 1000),
                // Campaña anterior: se pide la más verde (el pico), no la otra
                EscenaSentinel("2026-02-10", 3.0, 0.85, 0.5, 0.7, 1000),
                EscenaSentinel("2026-01-12", 4.0, 0.6, 0.35, 0.5, 1000)
            )
        )

        override suspend fun imagen(lote: LoteSatelital, fecha: String): Result<ImagenIndices> {
            pedidas += fecha
            val lats = lote.coordenadas.map { it.first }
            val lons = lote.coordenadas.map { it.second }
            val norte = lats.max()
            val oeste = lons.min()
            val columnas = 240
            val filas = 240
            val grilla = GrillaGeografica(oeste, norte, (lons.max() - oeste) / columnas, (norte - lats.min()) / filas, columnas, filas)
            val reciente = fecha == "2026-10-30"
            val v = FloatArray(grilla.total) { i ->
                val col = i % columnas
                val fila = i / columnas
                val enLote = col in 90..149 && fila in 100..139
                when {
                    enLote && reciente -> 0.78f
                    enLote -> 0.30f
                    reciente -> 0.55f
                    else -> 0.62f
                } + ((i * 7919) % 11 - 5) * 0.004f
            }
            return Result.success(ImagenIndices(grilla, fecha, v, v.copyOf(), v.copyOf()))
        }

        override suspend fun campana(lote: LoteSatelital, campana: Campana): Result<CampanaNdvi> =
            Result.failure(UnsupportedOperationException())
    }

    private fun esperar(vm: SateliteViewModel, condicion: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicion()) return
            Thread.sleep(20)
        }
        throw AssertionError("Estado no alcanzado: ${vm.deteccionLote.value} / ${vm.edicion.value}")
    }

    @Test
    fun tocarDentroDelLoteAbreSuContornoEnElEditor() {
        val repo = AgroSatRepository(AppDatabase.getDatabase(contexto).agroDao())
        val fuente = FuenteFalsa()
        val vm = SateliteViewModel(repo, maptilerDisponible = false, detector = DetectorLotes(fuente))

        vm.iniciarDeteccion()
        assertEquals(DeteccionLote.EsperandoToque, vm.deteccionLote.value)
        vm.detectarEn(-33.1, -61.2)
        esperar(vm) { vm.edicion.value != null }

        val e = vm.edicion.value!!
        assertTrue("detectado", e.detectado)
        assertEquals(DeteccionLote.Inactiva, vm.deteccionLote.value)
        // 60 × 40 píxeles de 10 m = 24 ha, con pocos vértices editables
        assertTrue("superficie ${e.areaHa}", abs(e.areaHa - 24.0) < 3.0)
        assertTrue("vértices ${e.vertices.size}", e.vertices.size in 4..10)
        val centro = GeometriaLote.centroide(e.vertices)!!
        assertTrue("centrado en el toque: $centro", abs(centro.first + 33.1) < 0.002 && abs(centro.second + 61.2) < 0.002)
        // La fecha más reciente sin nubes, otra de al menos 25 días antes y el pico de la campaña
        // anterior; la nublada no
        assertEquals(listOf("2026-10-30", "2026-09-28", "2026-02-10"), fuente.pedidas.toList())

        // Se edita como cualquier lote: mover un vértice y deshacer
        val (lat, lon) = e.vertices[0]
        vm.moverVertice(0, lat + 0.0005, lon)
        assertTrue(vm.edicion.value!!.puedeDeshacer)
    }

    @Test
    fun sinSesionExplicaPorQueNoSePuede() {
        val repo = AgroSatRepository(AppDatabase.getDatabase(contexto).agroDao())
        val vm = SateliteViewModel(repo, maptilerDisponible = false, detector = DetectorLotes(FuenteFalsa(configurada = false)))
        vm.iniciarDeteccion()
        assertTrue(vm.deteccionLote.value is DeteccionLote.Fallo)
    }
}
