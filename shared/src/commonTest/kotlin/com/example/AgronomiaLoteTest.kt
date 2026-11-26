package com.example

import com.example.utils.radianes
import com.example.utils.vra.AnalisisSuelo
import com.example.utils.vra.CalculoPotencial
import com.example.utils.vra.CapaRinde
import com.example.utils.vra.ConfigZonificacion
import com.example.utils.vra.CultivoVra
import com.example.utils.vra.DosisSugerida
import com.example.utils.vra.ErrorArchivoCampo
import com.example.utils.vra.EstrategiaFosforo
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.LectorCsv
import com.example.utils.vra.LectorShapefile
import com.example.utils.vra.MapaRinde
import com.example.utils.vra.MuestraSuelo
import com.example.utils.vra.ParametrosDosis
import com.example.utils.vra.ProductoFertilizante
import com.example.utils.vra.PuntoRinde
import com.example.utils.vra.PuntoSuelo
import com.example.utils.vra.SueloZona
import com.example.utils.vra.UnidadRinde
import com.example.utils.vra.Utm
import com.example.utils.vra.Zonificador
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Análisis de suelo, mapas de rinde y dosis sugerida (etapa 4 de la prescripción). */
class AgronomiaLoteTest {

    /** Lote de 400 × 400 m (16 ha) en el sur de Córdoba. */
    private val grilla = GrillaGeografica(-63.0, -33.0, 10 / 93_370.0, 10 / 110_950.0, 40, 40)
    private val lote = listOf(
        grilla.norte to grilla.oeste, grilla.norte to grilla.este, grilla.sur to grilla.este, grilla.sur to grilla.oeste
    )

    private fun cerca(esperado: Double, real: Double, tol: Double, mensaje: String) =
        assertTrue(abs(esperado - real) <= tol, "$mensaje: esperado $esperado, real $real")

    /** Centro de la columna [col] (0 a 39) a la latitud del centro del lote. */
    private fun lonDeColumna(col: Int) = grilla.oeste + (col + 0.5) * grilla.pasoLon
    private val latMedia = (grilla.norte + grilla.sur) / 2

    // ---------- Archivos ----------

    @Test
    fun csvConPuntoYComaYComaDecimal() {
        val t = LectorCsv.leer(
            "﻿Latitud;Longitud;\"Rinde (t/ha)\";Humedad;Cultivo\n" +
                "-33,0012;-62,9988;9,5;14,2;Maíz\n" +
                "-33.0013;-62.9987;10,25;13,9;Maíz\n" +
                ";;;;\n"
        )
        assertEquals(2, t.filas, "filas con coordenadas")
        cerca(-33.0013, t.lat[1], 1e-9, "latitud con punto decimal aunque el separador sea ;")
        assertEquals(setOf("Rinde (t/ha)", "Humedad"), t.columnas.keys, "solo las columnas numéricas")
        cerca(10.25, t.columnas.getValue("Rinde (t/ha)")[1], 1e-9, "coma decimal")
        cerca(9.875, t.mediana("Rinde (t/ha)")!!, 1.0, "mediana")
    }

    @Test
    fun csvConComaYSinCoordenadas() {
        val t = LectorCsv.leer("lon,lat,yld_mass\n-63.0,-33.0,8.1\n-63.001,-33.001,7.9\n")
        cerca(-63.001, t.lon[1], 1e-9, "longitud")
        assertEquals("yld_mass", t.buscar("yld"), "columna de rinde")
        assertFailsWith<ErrorArchivoCampo> { LectorCsv.leer("este;norte;rinde\n1;2;3\n") }
    }

    /** Shapefile mínimo de puntos con su tabla de atributos, escrito byte por byte. */
    private fun shapefile(puntos: List<Pair<Double, Double>>, rindes: List<String>): Pair<ByteArray, ByteArray> {
        val shp = mutableListOf<Byte>()
        fun be(v: Int) = (3 downTo 0).forEach { shp += (v shr (8 * it)).toByte() }
        fun le(v: Int) = (0..3).forEach { shp += (v shr (8 * it)).toByte() }
        fun leD(d: Double) = d.toBits().let { b -> (0..7).forEach { shp += (b shr (8 * it)).toByte() } }
        be(9994)
        repeat(5) { be(0) }
        be((100 + puntos.size * 28) / 2)
        le(1000)
        le(1)
        repeat(8) { leD(0.0) }
        puntos.forEachIndexed { i, (x, y) ->
            be(i + 1)
            be(10)
            le(1)
            leD(x)
            leD(y)
        }
        val dbf = mutableListOf<Byte>()
        fun leCorto(v: Int) { dbf += v.toByte(); dbf += (v shr 8).toByte() }
        dbf += 3
        repeat(3) { dbf += 0 }
        (0..3).forEach { dbf += (rindes.size shr (8 * it)).toByte() }
        leCorto(32 + 32 + 1)
        leCorto(1 + 8)
        repeat(20) { dbf += 0 }
        val nombre = "Yld_Mass_D".encodeToByteArray()
        repeat(11) { dbf += nombre.getOrElse(it) { 0 } }
        dbf += 'N'.code.toByte()
        repeat(4) { dbf += 0 }
        dbf += 8
        dbf += 2
        repeat(14) { dbf += 0 }
        dbf += 0x0D
        rindes.forEach { r ->
            dbf += ' '.code.toByte()
            r.padStart(8).encodeToByteArray().forEach { dbf += it }
        }
        return shp.toByteArray() to dbf.toByteArray()
    }

    @Test
    fun shapefileDePuntosEnGrados() {
        val (shp, dbf) = shapefile(listOf(-63.0 to -33.0, -62.999 to -33.001), listOf("9.50", "*****"))
        val t = LectorShapefile.leer(shp, dbf, null)
        assertEquals(2, t.filas, "dos puntos")
        cerca(-62.999, t.lon[1], 1e-9, "longitud del segundo punto")
        cerca(9.5, t.columnas.getValue("Yld_Mass_D")[0], 1e-9, "rinde del primero")
        assertTrue(t.columnas.getValue("Yld_Mass_D")[1].isNaN(), "celda vacía del monitor")
    }

    /** Fórmulas directas de Snyder (lat/lon → UTM), para comprobar la inversa. */
    private fun aUtm(lat: Double, lon: Double, zona: Int): Pair<Double, Double> {
        val a = 6378137.0
        val f = 1 / 298.257223563
        val e2 = f * (2 - f)
        val ep2 = e2 / (1 - e2)
        val k0 = 0.9996
        val phi = radianes(lat)
        val lon0 = radianes((zona - 1) * 6 - 180 + 3.0)
        val n = a / sqrt(1 - e2 * sin(phi).pow(2))
        val t = tan(phi).pow(2)
        val c = ep2 * cos(phi).pow(2)
        val aa = cos(phi) * (radianes(lon) - lon0)
        val m = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2.pow(3) / 256) * phi -
            (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2.pow(3) / 1024) * sin(2 * phi) +
            (15 * e2 * e2 / 256 + 45 * e2.pow(3) / 1024) * sin(4 * phi) -
            (35 * e2.pow(3) / 3072) * sin(6 * phi))
        val x = k0 * n * (aa + (1 - t + c) * aa.pow(3) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * aa.pow(5) / 120) + 500_000
        val y = k0 * (m + n * tan(phi) * (aa * aa / 2 + (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * ep2) * aa.pow(6) / 720)) + 10_000_000
        return x to y
    }

    @Test
    fun utmALatitudLongitud() {
        val (lat0, lon0) = Utm.aLatLon(500_000.0, 10_000_000.0, 20, sur = true)
        cerca(0.0, lat0, 1e-9, "ecuador")
        cerca(-63.0, lon0, 1e-9, "meridiano central de la zona 20")
        val (x, y) = aUtm(-31.5886, -64.2787, 20)
        val (lat, lon) = Utm.aLatLon(x, y, 20, sur = true)
        cerca(-31.5886, lat, 1e-7, "ida y vuelta, latitud")
        cerca(-64.2787, lon, 1e-7, "ida y vuelta, longitud")
    }

    @Test
    fun shapefileEnUtmConPrj() {
        val (x, y) = aUtm(-33.0, -63.0, 20)
        val (shp, dbf) = shapefile(listOf(x to y), listOf("8.00"))
        val prj = "PROJCS[\"WGS_1984_UTM_Zone_20S\",GEOGCS[\"GCS_WGS_1984\"],PARAMETER[\"False_Northing\",10000000.0]]"
        val t = LectorShapefile.leer(shp, dbf, prj)
        cerca(-33.0, t.lat[0], 1e-6, "latitud desde UTM")
        cerca(-63.0, t.lon[0], 1e-6, "longitud desde UTM")
        assertFailsWith<ErrorArchivoCampo> { LectorShapefile.leer(shp, dbf, "PROJCS[\"POSGAR_2007_Argentina_3\"]") }
    }

    // ---------- Mapa de rinde ----------

    @Test
    fun limpiaFueraDelLoteCerosYAtipicos() {
        val adentro = (0 until 200).map { PuntoRinde(latMedia, lonDeColumna(it % 40), 9.0 + (it % 5) * 0.1) }
        val puntos = adentro +
            PuntoRinde(latMedia, grilla.este + 0.01, 9.0) + // fuera del lote
            PuntoRinde(latMedia, lonDeColumna(3), 0.0) + // sin rinde
            PuntoRinde(latMedia, lonDeColumna(4), 30.0) // cabezal lleno: atípico
        val l = MapaRinde.limpiar(puntos, lote)
        assertEquals(200, l.puntos.size, "quedan los buenos")
        assertEquals(1, l.fueraDelLote, "fuera del lote")
        assertEquals(1, l.sinRinde, "sin rinde")
        assertEquals(1, l.atipicos, "atípico")
        assertEquals(UnidadRinde.QQ_HA, UnidadRinde.sugerida(45.0), "45 es soja en quintales")
        assertEquals(UnidadRinde.KG_HA, UnidadRinde.sugerida(9500.0), "miles son kg/ha")
        assertEquals(UnidadRinde.T_HA, UnidadRinde.sugerida(9.5), "t/ha")
    }

    @Test
    fun grillaDelLoteCoincideConLaDeLasImagenes() {
        val g = MapaRinde.grillaDelLote(lote)
        assertEquals(grilla.oeste, g.oeste, "oeste")
        assertEquals(grilla.norte, g.norte, "norte")
        assertEquals(40, g.filas, "40 filas de 10 m")
        assertTrue(g.columnas in 39..41, "unas 40 columnas de 10 m: ${g.columnas}")
    }

    /** Pasadas norte-sur cada 20 m (columnas pares): el rinde sube de oeste a este. */
    private fun pasadas(): List<PuntoRinde> = (0 until 40 step 2).flatMap { col ->
        (0 until 40).map { fila ->
            PuntoRinde(grilla.norte - (fila + 0.5) * grilla.pasoLat, lonDeColumna(col), 6.0 + col * 0.1)
        }
    }

    @Test
    fun rasterizaYRellenaLosHuecosEntrePasadas() {
        val v = MapaRinde.rasterizar(pasadas(), grilla, lote)
        assertTrue(v.none { it.isNaN() }, "las columnas impares, sin pasada, se rellenan con las vecinas")
        cerca(6.0, v[0].toDouble(), 1e-6, "columna 0")
        cerca(6.1, v[1].toDouble(), 1e-6, "columna 1: promedio de 6,0 y 6,2")
        val rel = MapaRinde.relativo(v)
        cerca(1.0, rel.filter { !it.isNaN() }.sorted()[rel.size / 2].toDouble(), 0.02, "la mediana relativa es 1")
    }

    @Test
    fun rindePorZonaYPotencialConRinde() {
        // Zonas por un índice que sube de oeste a este; el rinde también sube: las zonas coinciden
        val imagen = ImagenIndices(grilla, "x", FloatArray(1600) { 0.3f + (it % 40) * 0.01f }, FloatArray(1600), FloatArray(1600))
        val z = Zonificador.zonificar(imagen, IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 3, celdaM = 10.0, areaMinimaHa = 0.25))
        val porZona = MapaRinde.rindePorZona(pasadas(), z)
        assertEquals(listOf(1, 2, 3), porZona.map { it.zona }, "las tres zonas tienen puntos")
        assertTrue(MapaRinde.zonasOrdenadas(porZona), "el rinde sube con la zona: $porZona")
        assertFalse(MapaRinde.zonasOrdenadas(porZona.reversed().mapIndexed { i, r -> r.copy(zona = i + 1) }), "al revés no coinciden")

        val relativo = MapaRinde.relativo(MapaRinde.rasterizar(pasadas(), grilla, lote))
        val p = CalculoPotencial.combinar(emptyList(), lote, rindes = listOf(CapaRinde("Maíz 2024/25", relativo)), grilla = grilla)
        assertEquals("Rinde relativo: mapa de rinde Maíz 2024/25", p.descripcion, "descripción")
        assertTrue(p.indice[39] > p.indice[0], "el potencial sigue al rinde")
    }

    // ---------- Suelo y dosis ----------

    @Test
    fun analisisDeSueloPorZona() {
        val t = LectorCsv.leer(
            "Lat;Long;Muestra;P Bray (ppm);N-NO3 0-60 (kg/ha);MO %;pH\n" +
                "$latMedia;${lonDeColumna(2)};1;8;40;2,1;5,9\n" +
                "$latMedia;${lonDeColumna(5)};2;10;;2,3;6,1\n" +
                "$latMedia;${lonDeColumna(37)};3;22;70;3,0;6,4\n"
        )
        val col = AnalisisSuelo.columnasSugeridas(t)
        assertEquals("P Bray (ppm)", col.pBray, "fósforo")
        assertEquals("N-NO3 0-60 (kg/ha)", col.nNitratos, "nitratos")
        assertEquals("MO %", col.materiaOrganica, "materia orgánica")
        assertEquals("pH", col.ph, "pH, no confundido con el fósforo")

        val imagen = ImagenIndices(grilla, "x", FloatArray(1600) { 0.3f + (it % 40) * 0.01f }, FloatArray(1600), FloatArray(1600))
        val z = Zonificador.zonificar(imagen, IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 3, celdaM = 10.0, areaMinimaHa = 0.25))
        val porZona = AnalisisSuelo.porZona(AnalisisSuelo.puntos(t, col), z)
        val baja = porZona.getValue(1)
        assertEquals(2, baja.puntos, "dos puntos en la zona baja")
        cerca(9.0, baja.muestra.pBrayPpm!!, 1e-9, "promedio del Bray")
        cerca(40.0, baja.muestra.nNitratosKgHa!!, 1e-9, "nitratos solo del punto que los tiene")
        assertNull(porZona[2], "la zona del medio no tiene puntos")

        // Lo cargado a mano gana, valor por valor
        val manual = mapOf(1 to MuestraSuelo(pBrayPpm = 7.0))
        val m = AnalisisSuelo.deZona(1, manual, porZona)!!
        cerca(7.0, m.pBrayPpm!!, 1e-9, "Bray a mano")
        cerca(40.0, m.nNitratosKgHa!!, 1e-9, "nitratos de los puntos")
        assertEquals(MuestraSuelo(materiaOrganicaPct = 2.5), AnalisisSuelo.deZona(2, mapOf(2 to MuestraSuelo(materiaOrganicaPct = 2.5)), emptyMap<Int, SueloZona>()), "solo a mano")
        assertEquals(1, AnalisisSuelo.puntos(t, col).count { it.muestra.ph == 6.4 }, "pH con coma decimal")
        assertTrue(PuntoSuelo(0.0, 0.0, MuestraSuelo()).muestra.vacia, "muestra vacía")
    }

    @Test
    fun dosisSugeridaDeNitrogenoYFosforo() {
        val maiz = ParametrosDosis(cultivo = CultivoVra.MAIZ)
        val suelo = mapOf(1 to MuestraSuelo(pBrayPpm = 10.0, nNitratosKgHa = 60.0), 2 to MuestraSuelo(pBrayPpm = 25.0))
        val urea = DosisSugerida.calcular(maiz, listOf(1, 2), mapOf(1 to 1.0, 2 to 1.2)) { suelo[it] }
        cerca(9 * 17.0 - 60, urea[0].nKgHa!!, 1e-9, "N = rinde × 17 − nitratos")
        assertEquals(202, urea[0].productoKgHa, "93 kg de N son 202 kg de urea")
        cerca(10.8, urea[1].rindeObjetivoTHa, 1e-9, "la zona buena apunta a más rinde")
        assertNull(urea[1].productoKgHa, "sin nitratos no hay dosis de urea")
        assertEquals("Faltan los nitratos de la zona 2.", urea[1].faltante, "aviso")

        val map = maiz.conProducto(ProductoFertilizante.MAP)
        val conMap = DosisSugerida.calcular(map, listOf(1, 2), emptyMap()) { suelo[it] }
        cerca((16 - 10) * 3.0, conMap[0].pKgHa!!, 1e-9, "suficiencia: (umbral − Bray) × 3")
        assertEquals(79, conMap[0].productoKgHa, "18 kg de P son 79 kg de MAP")
        assertEquals(0, conMap[1].productoKgHa, "Bray sobre el umbral: no hace falta P")

        val reposicion = DosisSugerida.calcular(map.copy(estrategiaP = EstrategiaFosforo.REPOSICION), listOf(2), emptyMap()) { suelo[it] }
        cerca(9 * 3.0, reposicion[0].pKgHa!!, 1e-9, "reposición: lo que se lleva el grano")

        val soja = DosisSugerida.calcular(ParametrosDosis().conCultivo(CultivoVra.SOJA), listOf(1), emptyMap()) { null }
        assertEquals(0.0, soja[0].nKgHa, "la soja no lleva N")
        assertEquals(0, soja[0].productoKgHa, "ni urea")
        val otro = DosisSugerida.calcular(ParametrosDosis().conProducto(ProductoFertilizante.OTRO).copy(nProductoPct = 0.0), listOf(1), emptyMap()) { suelo[it] }
        assertEquals("Cargá el contenido de N y P₂O₅ del producto.", otro[0].faltante, "producto sin composición")
    }
}
