package com.example.data.repository

import com.example.utils.vra.ContornoDetectado
import com.example.utils.vra.DetectorContorno
import com.example.utils.vra.ErrorDeteccion
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.remote.supabase.ImagenSentinelRemota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos

/**
 * Detecta el lote que contiene un punto: baja índices Sentinel-2 de un recuadro de [LADO_M] alrededor
 * del toque (la fecha más reciente con la zona despejada y otra de al menos [SEPARACION_DIAS] antes,
 * si la hay, la foto en color real de la reciente y el pico de cultivo de la campaña anterior) y
 * busca su contorno con [DetectorContorno]. Gasta de 3 a 5 consultas del límite diario.
 */
class DetectorLotes(
    private val fuente: FuenteSentinel,
    /** Solo en debug: guarda el toque y las capas de la última detección para reproducirla en la PC. */
    private val volcado: java.io.File? = null
) {

    val disponible: Boolean get() = fuente.configurada()
    val mensajeNoDisponible: String get() = fuente.mensajeSinConfigurar

    suspend fun detectar(lat: Double, lon: Double): Result<ContornoDetectado> = runCatching {
        val zona = recuadro(lat, lon)
        val escenas = fuente.escenas(zona, DIAS).getOrThrow()
            .filter { it.nubesPct <= NUBES_MAX && it.ndvi != null }
            .sortedByDescending { it.fecha }
        val reciente = escenas.firstOrNull()
            ?: throw ErrorDeteccion("No hay imágenes sin nubes de la zona en los últimos 10 meses. Dibujá el lote a mano.")
        val anterior = escenas.firstOrNull { diasEntre(it.fecha, reciente.fecha) >= SEPARACION_DIAS }
        val imagenes = mutableListOf<ImagenIndices>(fuente.imagen(zona, reciente.fecha).getOrThrow())
        // El color real separa lotes pelados (rastrojo, labranza), que en los índices son casi iguales
        fuente.colorReal(zona, reciente.fecha).getOrNull()?.let { color ->
            runCatching { capaColor(color, imagenes.first().grilla) }.getOrNull()?.let(imagenes::add)
        }
        anterior?.let { e -> fuente.imagen(zona, e.fecha).getOrNull()?.let(imagenes::add) }
        // El pico de la campaña anterior: en primavera los lotes pelados son todos iguales, pero en
        // verano cada uno tenía su cultivo (en una prueba real, dos lotes del mismo suelo se juntaban)
        escenas.filter { diasEntre(it.fecha, reciente.fecha) >= DIAS_CAMPANA_ANTERIOR && (it.ndvi ?: 0.0) >= NDVI_CULTIVO }
            .maxByOrNull { it.ndvi ?: 0.0 }
            ?.let { e -> fuente.imagen(zona, e.fecha).getOrNull()?.let(imagenes::add) }
        withContext(Dispatchers.Default) {
            volcado?.let { runCatching { volcar(it, imagenes, lat, lon) } }
            DetectorContorno.detectar(imagenes, lat, lon)
        }
    }

    private fun volcar(archivo: java.io.File, imagenes: List<ImagenIndices>, lat: Double, lon: Double) {
        java.io.DataOutputStream(archivo.outputStream().buffered()).use { o ->
            o.writeDouble(lat)
            o.writeDouble(lon)
            o.writeInt(imagenes.size)
            for (img in imagenes) {
                val g = img.grilla
                o.writeUTF(img.fecha)
                o.writeDouble(g.oeste); o.writeDouble(g.norte); o.writeDouble(g.pasoLon); o.writeDouble(g.pasoLat)
                o.writeInt(g.columnas); o.writeInt(g.filas)
                for (canal in listOf(img.ndvi, img.ndre, img.gndvi)) canal.forEach(o::writeFloat)
            }
        }
    }

    /**
     * La foto en color real (con margen, en su propia grilla) pasada a la grilla de los índices: R, G y
     * B de 0 a 1 en los tres canales de una ImagenIndices; NaN donde no hay dato.
     */
    private fun capaColor(r: ImagenSentinelRemota, grilla: GrillaGeografica): ImagenIndices {
        val opciones = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeByteArray(r.png, 0, r.png.size, opciones) ?: error("Foto inválida")
        val pixeles = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixeles, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val ancho = bitmap.width
        val alto = bitmap.height
        bitmap.recycle()
        val rojo = FloatArray(grilla.total) { Float.NaN }
        val verde = FloatArray(grilla.total) { Float.NaN }
        val azul = FloatArray(grilla.total) { Float.NaN }
        for (i in 0 until grilla.total) {
            val (lat, lon) = grilla.centro(i)
            val x = ((lon - r.oeste) / (r.este - r.oeste) * ancho).toInt()
            val y = ((r.norte - lat) / (r.norte - r.sur) * alto).toInt()
            if (x !in 0 until ancho || y !in 0 until alto) continue
            val p = pixeles[y * ancho + x]
            if ((p ushr 24) == 0) continue
            rojo[i] = ((p shr 16) and 0xFF) / 255f
            verde[i] = ((p shr 8) and 0xFF) / 255f
            azul[i] = (p and 0xFF) / 255f
        }
        return ImagenIndices(grilla, "color ${r.fecha}", rojo, verde, azul)
    }

    private fun recuadro(lat: Double, lon: Double): LoteSatelital {
        val medio = LADO_M / 2
        val dLat = medio / METROS_LAT
        val dLon = medio / (METROS_LON_ECUADOR * cos(Math.toRadians(lat)))
        val coords = listOf(
            lat + dLat to lon - dLon, lat + dLat to lon + dLon,
            lat - dLat to lon + dLon, lat - dLat to lon - dLon
        )
        return LoteSatelital(
            id = "deteccion",
            nombre = "Detección de lote",
            coordenadas = coords,
            geojson = "",
            areaHectareas = LADO_M * LADO_M / 10_000,
            provincia = "",
            editable = false
        )
    }

    private fun diasEntre(desde: String, hasta: String): Long {
        val formato = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return runCatching { (formato.parse(hasta)!!.time - formato.parse(desde)!!.time) / 86_400_000L }.getOrDefault(0L)
    }

    companion object {
        /** Lado del recuadro analizado: alcanza para lotes de hasta ~150 ha con el toque en el medio. */
        const val LADO_M = 2400.0
        /** Alcanza para el pico de la campaña de verano anterior. */
        private const val DIAS = 300
        private const val DIAS_CAMPANA_ANTERIOR = 120L
        private const val NDVI_CULTIVO = 0.5
        private const val NUBES_MAX = 10.0
        private const val SEPARACION_DIAS = 25L
        private const val METROS_LAT = 110_950.0
        private const val METROS_LON_ECUADOR = 111_412.84
    }
}
