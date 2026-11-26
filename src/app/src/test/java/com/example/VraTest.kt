package com.example

import com.example.utils.vra.ConfigZonificacion
import com.example.utils.vra.ConversionesDosis
import com.example.utils.vra.DosisZona
import com.example.utils.vra.ExportacionPrescripcion
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.Labor
import com.example.utils.vra.MetodoZonificacion
import com.example.utils.vra.PerfilMonitor
import com.example.utils.vra.Prescripcion
import com.example.utils.vra.UbicacionFertilizante
import com.example.utils.vra.Zonificador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VraTest {

    // Lote de ejemplo de Pergamino (~52 ha)
    private val lote = listOf(-33.8912 to -60.5732, -33.8850 to -60.5732, -33.8850 to -60.5650, -33.8912 to -60.5650)

    /** Imagen de 10 m con NDVI que crece de oeste a este, más ruido "sal y pimienta". */
    private fun imagen(ruido: Double = 0.0): ImagenIndices {
        val oeste = -60.5740; val este = -60.5640; val norte = -33.8845; val sur = -33.8920
        val ancho = 92; val alto = 83
        val g = GrillaGeografica(oeste, norte, (este - oeste) / ancho, (norte - sur) / alto, ancho, alto)
        val r = Random(7)
        val ndvi = FloatArray(g.total) { i ->
            val c = i % ancho
            val base = 0.3 + 0.5 * c / ancho
            (base + if (r.nextDouble() < ruido) r.nextDouble(-0.4, 0.4) else 0.0).toFloat()
        }
        return ImagenIndices(g, "2026-10-10", ndvi, ndvi.copyOf(), ndvi.copyOf())
    }

    @Test
    fun zonasOrdenadasDeMenorAMayorIndice() {
        val z = Zonificador.zonificar(imagen(), IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 3, metodo = MetodoZonificacion.CUANTILES))
        assertEquals(3, z.zonas.size)
        assertTrue(z.zonas[0].indicePromedio < z.zonas[1].indicePromedio && z.zonas[1].indicePromedio < z.zonas[2].indicePromedio)
        // La suma de las zonas se aproxima a la superficie del lote (52,1 ha) dentro de lo que mide el borde de celdas
        assertEquals(52.1, z.zonas.sumOf { it.areaHa }, 2.0)
        // Gradiente limpio: un polígono por zona
        assertEquals(3, z.poligonos.size)
    }

    @Test
    fun respetaElLimiteDePoligonosDelMonitor() {
        val config = ConfigZonificacion(zonas = 4, celdaM = 10.0, areaMinimaHa = 0.0)
        val sinLimite = Zonificador.zonificar(imagen(ruido = 0.5), IndiceVegetacion.NDVI, lote, config)
        val n = sinLimite.poligonos.size
        assertTrue("hace falta un caso fragmentado: $n polígonos", n >= 10)
        val limite = n / 2
        val conLimite = Zonificador.zonificar(imagen(ruido = 0.5), IndiceVegetacion.NDVI, lote, config.copy(maxPoligonos = limite))
        assertTrue("con límite $limite: ${conLimite.poligonos.size}", conLimite.poligonos.size <= limite)
        assertEquals(sinLimite.zonas.sumOf { it.celdas }, conLimite.zonas.sumOf { it.celdas })
    }

    @Test
    fun cadaPoligonoTieneExteriorHorarioYAreaCoherente() {
        val z = Zonificador.zonificar(imagen(ruido = 0.05), IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 3))
        z.poligonos.forEach { p ->
            val exterior = p.anillos.first()
            assertEquals(exterior.first(), exterior.last())
            assertTrue("exterior horario", areaFirmada(exterior) < 0)
            p.anillos.drop(1).forEach { assertTrue("hueco antihorario", areaFirmada(it) > 0) }
            val m2 = p.anillos.sumOf { areaFirmada(it) } * -1 * 111_000.0 * 92_400.0
            assertEquals(p.areaHa, m2 / 10_000, p.areaHa * 0.03 + 0.05)
        }
    }

    // x = lon, y = lat: horario < 0
    private fun areaFirmada(r: List<Pair<Double, Double>>) =
        r.zipWithNext().sumOf { (a, b) -> a.second * b.first - b.second * a.first } / 2

    @Test
    fun conversionesDeDosis() {
        assertEquals(42, ConversionesDosis.semillasCada10m(80_000, 0.525)) // ejemplo de la guía de Cuffia
        assertEquals(12_000L, ConversionesDosis.kgHaAMgM2(120))
        assertEquals(8_000L, ConversionesDosis.semillasHaADdi11(80_000))
    }

    private fun prescripcion(perfil: PerfilMonitor, labor: Labor = Labor.SIEMBRA_Y_FERTILIZACION): Prescripcion {
        val config = ConfigZonificacion(zonas = 3, maxPoligonos = perfil.maxPoligonos)
        val z = Zonificador.zonificar(imagen(ruido = 0.05), IndiceVegetacion.NDVI, lote, config)
        return Prescripcion(
            nombreLote = "Lote El Trébol (Pergamino)", lote = lote, fechaImagen = "2026-10-10", indice = IndiceVegetacion.NDVI,
            zonificacion = z, labor = labor, ubicacionFertilizante = UbicacionFertilizante.COSTADO, distanciaSurcosM = 0.525,
            dosis = z.zonas.map { DosisZona(it.zona, fertilizanteKgHa = 60 + 20 * it.zona, semillasHa = 70_000 + 5_000 * it.zona) },
            producto = "Fosfato diamónico"
        )
    }

    @Test
    fun shapefileCuffiaAg9500() {
        val p = prescripcion(PerfilMonitor.CUFFIA_AG9500)
        assertTrue(p.problemas(PerfilMonitor.CUFFIA_AG9500).isEmpty())
        val archivos = ExportacionPrescripcion.shapefile(p, PerfilMonitor.CUFFIA_AG9500)
        assertEquals(setOf("RXELTREB.shp", "RXELTREB.shx", "RXELTREB.dbf", "RXELTREB.prj"), archivos.map { it.ruta }.toSet())
        val shp = archivos.first { it.ruta.endsWith(".shp") }.bytes
        val shx = archivos.first { it.ruta.endsWith(".shx") }.bytes
        val dbf = archivos.first { it.ruta.endsWith(".dbf") }.bytes
        assertEquals(9994, ByteBuffer.wrap(shp).order(ByteOrder.BIG_ENDIAN).getInt(0))
        assertEquals(shp.size / 2, ByteBuffer.wrap(shp).order(ByteOrder.BIG_ENDIAN).getInt(24))
        assertEquals(5, ByteBuffer.wrap(shp).order(ByteOrder.LITTLE_ENDIAN).getInt(32))
        assertEquals(100 + 8 * p.zonificacion.poligonos.size, shx.size)
        val registros = ByteBuffer.wrap(dbf).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
        val campos = (ByteBuffer.wrap(dbf).order(ByteOrder.LITTLE_ENDIAN).getShort(8) - 33) / 32
        assertEquals(p.zonificacion.poligonos.size, registros)
        assertEquals(3, campos) // la guía de Cuffia exige exactamente 3 columnas

        // Muestras para validar con herramientas GIS externas
        val dir = File("build/vra-muestra").apply { mkdirs() }
        archivos.forEach { File(dir, it.ruta).writeBytes(it.bytes) }
        ExportacionPrescripcion.shapefile(p, PerfilMonitor.GENERICO).forEach { File(dir, it.ruta).writeBytes(it.bytes) }
        ExportacionPrescripcion.isoXml(p).forEach { File(dir, it.ruta).apply { parentFile?.mkdirs() }.writeBytes(it.bytes) }
        File(dir, "prescripcion.geojson").writeText(ExportacionPrescripcion.geojson(p))
        File(dir, "dosis_esperadas.txt").writeText(
            p.zonificacion.zonas.joinToString("\n") { z ->
                val d = p.dosisDe(z.zona)
                "${z.zona};${d.fertilizanteKgHa};${ConversionesDosis.semillasCada10m(d.semillasHa!!, 0.525)};${"%.4f".format(java.util.Locale.US, z.areaHa)}"
            }
        )
    }

    @Test
    fun isoXmlConGrillaDeZonas() {
        val p = prescripcion(PerfilMonitor.ISOBUS)
        val archivos = ExportacionPrescripcion.isoXml(p)
        val xml = String(archivos.first { it.ruta == "TASKDATA/TASKDATA.XML" }.bytes)
        val bin = archivos.first { it.ruta == "TASKDATA/GRD00001.BIN" }.bytes
        val g = p.zonificacion.grilla
        assertEquals(g.columnas * g.filas, bin.size)
        assertTrue(xml.contains("<PDV A=\"0006\" B=\"${(60 + 20) * 100}\""))
        assertTrue(xml.contains("<PDV A=\"000B\" B=\"7500\""))
        assertTrue(xml.contains("I=\"1\"/>"))
        // La primera fila del binario es la del sur
        val primeraFila = bin.copyOfRange(0, g.columnas).map { it.toInt() }
        val filaSurEsperada = (0 until g.columnas).map { p.zonificacion.etiquetas[(g.filas - 1) * g.columnas + it] }
        assertEquals(filaSurEsperada, primeraFila)
    }

    @Test
    fun faltanDosisImpideExportar() {
        val p = prescripcion(PerfilMonitor.CUFFIA_AG_FUSION).let { it.copy(dosis = it.dosis.drop(1)) }
        assertTrue(p.problemas(PerfilMonitor.CUFFIA_AG_FUSION).any { it.contains("zona 1") })
    }

    @Test
    fun geojsonConExteriorAntihorario() {
        val p = prescripcion(PerfilMonitor.GENERICO, Labor.FERTILIZACION)
        val json = org.json.JSONObject(ExportacionPrescripcion.geojson(p))
        val features = json.getJSONArray("features")
        assertEquals(p.zonificacion.poligonos.size, features.length())
        val anillo = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0)
        var doble = 0.0
        for (k in 0 until anillo.length() - 1) {
            val a = anillo.getJSONArray(k); val b = anillo.getJSONArray(k + 1)
            doble += a.getDouble(0) * b.getDouble(1) - b.getDouble(0) * a.getDouble(1)
        }
        assertTrue("RFC 7946 pide exterior antihorario", doble > 0)
        assertTrue(abs(features.getJSONObject(0).getJSONObject("properties").getDouble("area_ha")) > 0)
    }
}
