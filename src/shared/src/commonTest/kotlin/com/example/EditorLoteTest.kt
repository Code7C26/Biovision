package com.example

import com.example.utils.EditorPoligono
import com.example.utils.GeoJsonLote
import com.example.utils.GeometriaLote
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dibujo de lotes en la web (EditorPoligono) y su GeoJSON, que tiene que coincidir con el de Android. */
class EditorLoteTest {

    // Cuadrado de ~100 m de lado cerca de Pergamino: ~1 ha
    private val cuadrado = listOf(
        -33.8900 to -60.5700,
        -33.8900 to -60.5689,
        -33.8909 to -60.5689,
        -33.8909 to -60.5700
    )

    @Test
    fun dibujarCalculaMedidasYHabilitaGuardar() {
        val editor = EditorPoligono()
        cuadrado.take(2).forEach { (lat, lon) -> editor.agregar(lat, lon) }
        assertFalse(editor.estado.puedeGuardar, "con 2 vértices no es un polígono")
        cuadrado.drop(2).forEach { (lat, lon) -> editor.agregar(lat, lon) }
        val e = editor.estado
        assertEquals(4, e.vertices.size)
        assertTrue(e.puedeGuardar)
        assertTrue(abs(e.areaHa - GeometriaLote.areaHectareas(cuadrado)) < 1e-9, "área de GeometriaLote")
        assertTrue(e.areaHa in 0.9..1.2, "≈ 1 ha, fue ${e.areaHa}")
        assertTrue(e.perimetroM in 380.0..420.0, "≈ 400 m, fue ${e.perimetroM}")
        assertTrue(e.cierraAlTocar(0), "tocar el primero cierra el polígono")
        assertFalse(e.cierraAlTocar(1), "tocar otro vértice no cierra")
    }

    @Test
    fun deshacerYRehacer() {
        val editor = EditorPoligono()
        cuadrado.forEach { (lat, lon) -> editor.agregar(lat, lon) }
        editor.deshacer()
        assertEquals(3, editor.estado.vertices.size)
        assertTrue(editor.estado.puedeRehacer)
        editor.rehacer()
        assertEquals(cuadrado, editor.estado.vertices)
        editor.deshacer()
        editor.agregar(-33.8905, -60.5705)
        assertFalse(editor.estado.puedeRehacer, "un cambio nuevo borra lo que se podía rehacer")
    }

    @Test
    fun moverInsertarYEliminarVertice() {
        val editor = EditorPoligono(cuadrado)
        val revision = editor.estado.revision
        editor.insertar(1, -33.8898, -60.56945)
        assertEquals(5, editor.estado.vertices.size)
        assertEquals(1, editor.estado.seleccionado, "el insertado queda elegido")
        assertTrue(editor.estado.revision > revision, "un cambio confirmado sube la revisión")
        editor.eliminarSeleccionado()
        assertEquals(cuadrado, editor.estado.vertices)
        editor.mover(2, -33.8910, -60.5688)
        assertEquals(-33.8910 to -60.5688, editor.estado.vertices[2])
        assertFalse(editor.estado.cierraAlTocar(0), "con un vértice elegido, tocar el primero no cierra")
    }

    @Test
    fun seleccionarAlternaYNoEntraEnElHistorial() {
        val editor = EditorPoligono(cuadrado)
        editor.seleccionar(2)
        assertEquals(2, editor.estado.seleccionado)
        editor.seleccionar(2)
        assertNull(editor.estado.seleccionado)
        assertFalse(editor.estado.puedeDeshacer, "elegir un vértice no es un cambio")
    }

    @Test
    fun autointerseccionImpideGuardar() {
        // Moño: los lados 0-1 y 2-3 se cruzan
        val editor = EditorPoligono(listOf(cuadrado[0], cuadrado[2], cuadrado[1], cuadrado[3]))
        assertTrue(editor.estado.autointerseccion)
        assertFalse(editor.estado.puedeGuardar)
    }

    @Test
    fun ignoraCoordenadasInvalidas() {
        val editor = EditorPoligono()
        editor.agregar(Double.NaN, -60.0)
        editor.agregar(-95.0, -60.0)
        editor.agregar(-33.0, 200.0)
        assertTrue(editor.estado.vertices.isEmpty())
        assertNull(editor.areaProvisoria(0, -33.0, -60.0, insercion = false), "no hay vértice 0 que mover")
    }

    @Test
    fun areaProvisoriaNoCambiaElEstado() {
        val editor = EditorPoligono(cuadrado)
        val antes = editor.estado
        val provisoria = editor.areaProvisoria(2, -33.8920, -60.5689, insercion = false)
        assertTrue(provisoria != null && provisoria > antes.areaHa, "arrastrar hacia afuera agranda el lote")
        assertEquals(antes, editor.estado)
    }

    @Test
    fun geojsonCerradoEnLonLatYDeIda() {
        val texto = GeoJsonLote.generar(cuadrado)
        assertTrue("\"type\":\"Polygon\"" in texto)
        // Primer punto en [lon, lat] y el anillo cerrado (5 puntos para 4 vértices)
        assertTrue("[[[-60.57,-33.89]," in texto, texto)
        assertEquals(cuadrado, GeoJsonLote.leer(texto))
    }

    @Test
    fun leeElGeojsonDeAndroid() {
        // Lo que escribe AgroSatRepository.generarGeoJson (org.json con sangría)
        val android = """
            {
              "type": "Feature",
              "properties": {},
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[-60.57, -33.89], [-60.5689, -33.89], [-60.5689, -33.8909], [-60.57, -33.8909], [-60.57, -33.89]]]
              }
            }
        """.trimIndent()
        assertEquals(cuadrado, GeoJsonLote.leer(android))
    }

    @Test
    fun geojsonInvalidoEsNull() {
        assertNull(GeoJsonLote.leer("no es json"))
        assertNull(GeoJsonLote.leer("""{"type":"Point","coordinates":[-60.0,-33.0]}"""))
        assertNull(GeoJsonLote.leer("""{"type":"Polygon","coordinates":[[[-60.0,-33.0],[-60.1,-33.0],[-60.0,-33.0]]]}"""), "2 vértices")
        assertNull(GeoJsonLote.leer("{}"))
    }
}

/** Provincia del lote al guardarlo en la web (Nominatim /reverse, la misma lectura que Android). */
class ProvinciaLoteTest {
    @Test
    fun leeLaProvinciaDeNominatim() {
        val respuesta = """{"place_id":1,"address":{"state":"Buenos Aires","country":"Argentina","country_code":"ar"}}"""
        assertEquals("Buenos Aires", com.example.data.remote.ktor.ServicioProvincias.provinciaDeRespuesta(respuesta))
    }

    @Test
    fun sinProvinciaEsNull() {
        val leer = com.example.data.remote.ktor.ServicioProvincias::provinciaDeRespuesta
        assertNull(leer("""{"error":"Unable to geocode"}"""), "punto en el mar")
        assertNull(leer("""{"address":{"state":"  "}}"""), "vacía")
        assertNull(leer("no es json"))
    }
}

/** Riesgo de incendio de las tarjetas de Clima (misma regla que Android). */
class RiesgoIncendioTest {
    @Test
    fun categorias() {
        assertEquals("Extremo", com.example.data.remote.model.riesgoIncendio(33.0, 20.0, 30.0))
        assertEquals("Alto", com.example.data.remote.model.riesgoIncendio(27.0, 40.0, 20.0))
        assertEquals("Moderado", com.example.data.remote.model.riesgoIncendio(22.0, 55.0, 5.0))
        assertEquals("Bajo", com.example.data.remote.model.riesgoIncendio(18.0, 80.0, 40.0))
    }
}
