package com.example.data.repository

import com.example.utils.vra.CultivoVra
import com.example.utils.vra.EstrategiaFosforo
import com.example.utils.vra.MuestraSuelo
import com.example.utils.vra.ParametrosDosis
import com.example.utils.vra.ProductoFertilizante
import com.example.utils.vra.PuntoRinde
import com.example.utils.vra.PuntoSuelo
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Un mapa de rinde del lote, ya limpio (puntos en t/ha dentro del lote). */
data class MapaRindeLote(
    val id: String,
    /** P. ej. "Maíz 2024/25". */
    val etiqueta: String,
    val cultivo: CultivoVra,
    val puntos: List<PuntoRinde>,
    /** Puntos que no entraron: fuera del lote, sin rinde o atípicos. */
    val descartados: Int,
    val archivo: String
) {
    val medianaTHa: Double by lazy {
        val v = puntos.map { it.rindeTHa }.sorted()
        if (v.isEmpty()) 0.0 else v[v.size / 2]
    }
}

/**
 * Análisis de suelo del lote: puntos georreferenciados y valores cargados a mano por zona. Los de a
 * mano valen para una zonificación: [firmaManual] la identifica y, si las zonas cambian, se descartan.
 */
data class SueloLote(
    val puntos: List<PuntoSuelo> = emptyList(),
    val manual: Map<Int, MuestraSuelo> = emptyMap(),
    val firmaManual: String? = null
)

/**
 * Guarda en el teléfono, por lote, los mapas de rinde, el análisis de suelo y los parámetros de la
 * dosis sugerida (filesDir/agronomia). Con cuenta, [SincronizadorAgronomia] sube cada lote como un
 * ZIP a Storage: cada cambio marca el lote como pendiente y avisa con [alCambiar].
 */
class AlmacenAgronomia(private val raiz: File) {

    /** Se llama después de cada cambio (la app programa la sincronización). */
    var alCambiar: () -> Unit = {}

    /** Borra todo (al eliminar la cuenta). */
    fun vaciar() {
        raiz.deleteRecursively()
    }

    // ---------- Sincronización con la cuenta ----------

    private val archivoPendientes get() = File(raiz, "pendientes.txt")
    private val archivoBorrados get() = File(raiz, "borrados.txt")
    private val archivoVersiones get() = File(raiz, "versiones.json")

    private fun leerLineas(f: File): Set<String> =
        if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()

    private fun escribirLineas(f: File, lineas: Set<String>) {
        raiz.mkdirs()
        f.writeText(lineas.joinToString("\n"))
    }

    /** Lotes (id local) con cambios sin subir. */
    @Synchronized
    fun pendientes(): Set<String> = leerLineas(archivoPendientes)

    @Synchronized
    private fun marcarPendiente(loteId: String) {
        escribirLineas(archivoPendientes, pendientes() + loteId)
    }

    private fun cambio(loteId: String) {
        marcarPendiente(loteId)
        alCambiar()
    }

    @Synchronized
    fun marcarSubido(loteId: String) {
        escribirLineas(archivoPendientes, pendientes() - loteId)
    }

    /** Lotes (uuid) borrados cuyos datos todavía hay que borrar de la nube. */
    @Synchronized
    fun borrados(): Set<String> = leerLineas(archivoBorrados)

    @Synchronized
    fun olvidarBorrado(uuid: String) {
        escribirLineas(archivoBorrados, borrados() - uuid)
    }

    /** Versión del ZIP de la nube que tiene este teléfono (0 = ninguna). */
    @Synchronized
    fun version(uuid: String): Long =
        runCatching { JSONObject(archivoVersiones.readText()).optLong(uuid, 0L) }.getOrDefault(0L)

    @Synchronized
    fun guardarVersion(uuid: String, version: Long) {
        val o = runCatching { JSONObject(archivoVersiones.readText()) }.getOrDefault(JSONObject())
        raiz.mkdirs()
        archivoVersiones.writeText(o.put(uuid, version).toString())
    }

    /** Al borrar el lote: sus datos se van del teléfono y, con cuenta, de la nube. */
    @Synchronized
    fun borrarLote(loteId: String, uuid: String) {
        carpeta(loteId).deleteRecursively()
        marcarSubido(loteId)
        escribirLineas(archivoBorrados, borrados() + uuid)
        alCambiar()
    }

    fun tieneDatos(loteId: String): Boolean = carpeta(loteId).listFiles()?.isNotEmpty() == true

    /** Los archivos del lote en un ZIP; null si no tiene datos. */
    fun empaquetar(loteId: String): ByteArray? {
        val archivos = carpeta(loteId).listFiles()?.filter { it.isFile }.orEmpty()
        if (archivos.isEmpty()) return null
        val salida = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(salida).use { zip ->
            archivos.forEach { f ->
                zip.putNextEntry(java.util.zip.ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return salida.toByteArray()
    }

    /**
     * Reemplaza los datos del lote por los del ZIP (bajado de la nube). Solo acepta nombres planos
     * conocidos: un ZIP armado a mano no puede escribir fuera de la carpeta del lote.
     */
    @Synchronized
    fun desempaquetar(loteId: String, zip: ByteArray) {
        val temporal = File(raiz, "tmp_" + carpeta(loteId).name).apply { deleteRecursively(); mkdirs() }
        java.util.zip.ZipInputStream(zip.inputStream()).use { entrada ->
            var total = 0L
            while (true) {
                val e = entrada.nextEntry ?: break
                if (!e.isDirectory && NOMBRE_VALIDO.matches(e.name)) {
                    File(temporal, e.name).outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = entrada.read(buffer)
                            if (n < 0) break
                            total += n
                            check(total <= MAX_DESEMPAQUETADO) { "ZIP demasiado grande" }
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
        }
        val destino = carpeta(loteId)
        destino.deleteRecursively()
        check(temporal.renameTo(destino)) { "No se pudo guardar ${destino.name}" }
    }

    private fun carpeta(loteId: String): File =
        File(raiz, "lote_" + loteId.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { loteId.hashCode().toUInt().toString() })

    // ---------- Mapas de rinde ----------

    fun rindes(loteId: String): List<MapaRindeLote> {
        val indice = File(carpeta(loteId), "rindes.json").takeIf { it.exists() } ?: return emptyList()
        return runCatching {
            val lista = JSONArray(indice.readText())
            (0 until lista.length()).mapNotNull { i ->
                val o = lista.getJSONObject(i)
                val id = o.getString("id")
                val puntos = leerPuntos(File(carpeta(loteId), "rinde_$id.bin")) ?: return@mapNotNull null
                MapaRindeLote(
                    id = id,
                    etiqueta = o.getString("etiqueta"),
                    cultivo = runCatching { CultivoVra.valueOf(o.getString("cultivo")) }.getOrDefault(CultivoVra.MAIZ),
                    puntos = puntos,
                    descartados = o.optInt("descartados"),
                    archivo = o.optString("archivo")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun guardarRinde(loteId: String, mapa: MapaRindeLote) {
        val dir = carpeta(loteId).apply { mkdirs() }
        DataOutputStream(File(dir, "rinde_${mapa.id}.bin").outputStream().buffered()).use { out ->
            out.writeInt(mapa.puntos.size)
            mapa.puntos.forEach {
                out.writeDouble(it.lat)
                out.writeDouble(it.lon)
                out.writeFloat(it.rindeTHa.toFloat())
            }
        }
        escribirIndice(loteId, rindes(loteId).filter { it.id != mapa.id } + mapa)
        cambio(loteId)
    }

    fun borrarRinde(loteId: String, id: String) {
        File(carpeta(loteId), "rinde_$id.bin").delete()
        escribirIndice(loteId, rindes(loteId).filter { it.id != id })
        cambio(loteId)
    }

    private fun escribirIndice(loteId: String, mapas: List<MapaRindeLote>) {
        val lista = JSONArray()
        mapas.forEach { m ->
            lista.put(
                JSONObject().put("id", m.id).put("etiqueta", m.etiqueta).put("cultivo", m.cultivo.name)
                    .put("descartados", m.descartados).put("archivo", m.archivo)
            )
        }
        File(carpeta(loteId).apply { mkdirs() }, "rindes.json").writeText(lista.toString())
    }

    private fun leerPuntos(archivo: File): List<PuntoRinde>? = runCatching {
        DataInputStream(archivo.inputStream().buffered()).use { inp ->
            val n = inp.readInt()
            List(n) { PuntoRinde(inp.readDouble(), inp.readDouble(), inp.readFloat().toDouble()) }
        }
    }.getOrNull()

    // ---------- Suelo ----------

    fun suelo(loteId: String): SueloLote {
        val f = File(carpeta(loteId), "suelo.json").takeIf { it.exists() } ?: return SueloLote()
        return runCatching {
            val o = JSONObject(f.readText())
            val puntos = o.optJSONArray("puntos")?.let { a ->
                (0 until a.length()).map { i ->
                    val p = a.getJSONArray(i)
                    PuntoSuelo(p.getDouble(0), p.getDouble(1), muestra(p, 2))
                }
            }.orEmpty()
            val manual = o.optJSONObject("manual")?.let { m ->
                m.keys().asSequence().associate { k -> k.toInt() to muestra(m.getJSONArray(k), 0) }
            }.orEmpty()
            SueloLote(puntos, manual, o.optString("firma").ifEmpty { null })
        }.getOrDefault(SueloLote())
    }

    fun guardarSuelo(loteId: String, suelo: SueloLote) {
        val puntos = JSONArray()
        suelo.puntos.forEach { p -> puntos.put(valores(JSONArray().put(p.lat).put(p.lon), p.muestra)) }
        val manual = JSONObject()
        suelo.manual.forEach { (zona, m) -> if (!m.vacia) manual.put(zona.toString(), valores(JSONArray(), m)) }
        val o = JSONObject().put("puntos", puntos).put("manual", manual)
        suelo.firmaManual?.let { o.put("firma", it) }
        File(carpeta(loteId).apply { mkdirs() }, "suelo.json").writeText(o.toString())
        cambio(loteId)
    }

    private fun valores(a: JSONArray, m: MuestraSuelo): JSONArray =
        listOf(m.pBrayPpm, m.nNitratosKgHa, m.materiaOrganicaPct, m.ph).fold(a) { acc, v -> acc.put(v ?: JSONObject.NULL) }

    private fun muestra(a: JSONArray, desde: Int): MuestraSuelo {
        fun v(i: Int): Double? = if (a.length() <= desde + i || a.isNull(desde + i)) null else a.getDouble(desde + i)
        return MuestraSuelo(v(0), v(1), v(2), v(3))
    }

    // ---------- Parámetros de la dosis sugerida ----------

    fun parametros(loteId: String): ParametrosDosis? {
        val f = File(carpeta(loteId), "dosis.json").takeIf { it.exists() } ?: return null
        return runCatching {
            val o = JSONObject(f.readText())
            ParametrosDosis(
                cultivo = CultivoVra.valueOf(o.getString("cultivo")),
                rindeObjetivoTHa = o.getDouble("rinde"),
                nPorToneladaKg = o.getDouble("nPorT"),
                umbralPBrayPpm = o.getDouble("umbralP"),
                kgPPorPpm = o.getDouble("kgPPorPpm"),
                extraccionPKgT = o.getDouble("extraccionP"),
                estrategiaP = EstrategiaFosforo.valueOf(o.getString("estrategiaP")),
                producto = ProductoFertilizante.valueOf(o.getString("producto")),
                nProductoPct = o.getDouble("nPct"),
                p2o5ProductoPct = o.getDouble("p2o5Pct")
            )
        }.getOrNull()
    }

    fun guardarParametros(loteId: String, p: ParametrosDosis) {
        val o = JSONObject()
            .put("cultivo", p.cultivo.name).put("rinde", p.rindeObjetivoTHa).put("nPorT", p.nPorToneladaKg)
            .put("umbralP", p.umbralPBrayPpm).put("kgPPorPpm", p.kgPPorPpm).put("extraccionP", p.extraccionPKgT)
            .put("estrategiaP", p.estrategiaP.name).put("producto", p.producto.name)
            .put("nPct", p.nProductoPct).put("p2o5Pct", p.p2o5ProductoPct)
        File(carpeta(loteId).apply { mkdirs() }, "dosis.json").writeText(o.toString())
        cambio(loteId)
    }

    private companion object {
        val NOMBRE_VALIDO = Regex("^(rindes|suelo|dosis)\\.json$|^rinde_[A-Za-z0-9_-]{1,64}\\.bin$")
        const val MAX_DESEMPAQUETADO = 200L * 1024 * 1024
    }
}
