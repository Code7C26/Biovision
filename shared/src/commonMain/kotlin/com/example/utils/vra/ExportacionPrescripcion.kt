package com.example.utils.vra

import com.example.utils.GeometriaLote
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/** Archivo listo para escribir; [ruta] puede incluir carpetas ("TASKDATA/TASKDATA.XML"). */
class ArchivoExportado(val ruta: String, val bytes: ByteArray)

/**
 * Archivos de la prescripción para cada monitor. En :shared (sin java.util.zip ni org.json) para que
 * la web descargue exactamente lo mismo que guarda la app Android.
 */
object ExportacionPrescripcion {

    /** Colores de zona de menor a mayor índice (rojo → verde), como en el mapa. */
    fun colorZona(zona: Int, zonas: Int): String {
        // RdYlGn de 7 clases: con 6 o 7 zonas cada una sigue teniendo su color
        val paleta = listOf("#d73027", "#fc8d59", "#fee08b", "#ffffbf", "#d9ef8b", "#91cf60", "#1a9850")
        if (zonas <= 1) return paleta.last()
        val i = ((zona - 1) * (paleta.size - 1).toDouble() / (zonas - 1)).roundToLong().toInt()
        return paleta[i.coerceIn(0, paleta.lastIndex)]
    }

    /** Nombre de archivo para Cuffia: hasta 8 caracteres, solo letras y números. */
    fun nombreCorto(nombreLote: String): String {
        val base = EscritorShapefile.soloAscii(nombreLote).uppercase()
            .replace(Regex("\\bLOTE\\b"), "")
            .filter { it.isLetterOrDigit() }
        return ("RX" + base).take(8).ifEmpty { "RXLOTE" }
    }

    // --- Shapefile según el perfil del monitor ---

    fun shapefile(p: Prescripcion, perfil: PerfilMonitor): List<ArchivoExportado> {
        val anillos = p.zonificacion.poligonos.map { it.anillos }
        val (campos, registros) = when {
            perfil == PerfilMonitor.CUFFIA_AG9500 || perfil == PerfilMonitor.CUFFIA_AG_FUSION || perfil == PerfilMonitor.CUFFIA_ALTINA -> {
                // Exactamente 3 columnas enteras, en este orden (guía de carga de Cuffia)
                val campos = listOf(CampoDbf("FERT_LIN", 'N', 10), CampoDbf("FERT_COS", 'N', 10), CampoDbf("SEMILLAS", 'N', 10))
                campos to p.zonificacion.poligonos.map { pol ->
                    val d = p.dosisDe(pol.zona)
                    if (perfil == PerfilMonitor.CUFFIA_ALTINA) {
                        // Altina dosifica un solo producto en kg/ha (semilla o fertilizante)
                        listOf(d.fertilizanteKgHa ?: 0, 0, 0)
                    } else {
                        val fert = if (p.labor.usaFertilizante) d.fertilizanteKgHa ?: 0 else 0
                        val semillas = if (p.labor.usaSemillas) ConversionesDosis.semillasCada10m(d.semillasHa ?: 0, p.distanciaSurcosM) else 0
                        listOf(
                            if (p.ubicacionFertilizante == UbicacionFertilizante.LINEA) fert else 0,
                            if (p.ubicacionFertilizante == UbicacionFertilizante.COSTADO) fert else 0,
                            semillas
                        )
                    }
                }
            }
            perfil == PerfilMonitor.CUFFIA_FGS -> {
                val campos = listOf(CampoDbf("SEMILLAS", 'N', 10), CampoDbf("FERTILIZ", 'N', 10))
                campos to p.zonificacion.poligonos.map { pol ->
                    val d = p.dosisDe(pol.zona)
                    listOf(if (p.labor.usaSemillas) d.semillasHa ?: 0 else 0, if (p.labor.usaFertilizante) d.fertilizanteKgHa ?: 0 else 0)
                }
            }
            else -> {
                val campos = listOf(
                    CampoDbf("ZONA", 'N', 3),
                    CampoDbf("AREA_HA", 'N', 10, 2),
                    CampoDbf("INDICE", 'N', 7, 3),
                    CampoDbf("FERT_KGHA", 'N', 10),
                    CampoDbf("SEM_HA", 'N', 10),
                    CampoDbf("PRODUCTO", 'C', 40)
                )
                val promedios = p.zonificacion.zonas.associate { it.zona to it.indicePromedio }
                campos to p.zonificacion.poligonos.map { pol ->
                    val d = p.dosisDe(pol.zona)
                    listOf(
                        pol.zona,
                        pol.areaHa,
                        promedios[pol.zona],
                        if (p.labor.usaFertilizante) d.fertilizanteKgHa else null,
                        if (p.labor.usaSemillas) d.semillasHa else null,
                        p.producto.take(40)
                    )
                }
            }
        }
        val base = if (perfil.esCuffia) nombreCorto(p.nombreLote) else "PRESCRIP"
        return EscritorShapefile.escribir(anillos, campos, registros).map { (ext, bytes) -> ArchivoExportado("$base.$ext", bytes) }
    }

    // --- GeoJSON (RFC 7946: exterior antihorario) ---

    fun geojson(p: Prescripcion, conColor: Boolean = false): String {
        val zonas = p.zonificacion.zonas.size
        val promedios = p.zonificacion.zonas.associate { it.zona to it.indicePromedio }
        val features = buildJsonArray {
            p.zonificacion.poligonos.forEach { pol ->
                val d = p.dosisDe(pol.zona)
                val coords = buildJsonArray {
                    pol.anillos.forEach { anillo ->
                        add(buildJsonArray { anillo.reversed().forEach { (lat, lon) -> add(buildJsonArray { add(JsonPrimitive(redondear(lon))); add(JsonPrimitive(redondear(lat))) }) } })
                    }
                }
                add(buildJsonObject {
                    put("type", "Feature")
                    put("properties", buildJsonObject {
                        put("zona", pol.zona)
                        put("area_ha", (pol.areaHa * 100).roundToLong() / 100.0)
                        put("indice", p.nombreValor)
                        put("indice_medio", promedios[pol.zona]?.let { (it * 1000).roundToLong() / 1000.0 })
                        put("fertilizante_kg_ha", if (p.labor.usaFertilizante) JsonPrimitive(d.fertilizanteKgHa) else JsonNull)
                        put("semillas_ha", if (p.labor.usaSemillas) JsonPrimitive(d.semillasHa) else JsonNull)
                        if (conColor) put("color", colorZona(pol.zona, zonas))
                    })
                    put("geometry", buildJsonObject {
                        put("type", "Polygon")
                        put("coordinates", coords)
                    })
                })
            }
        }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("name", "Prescripcion ${p.nombreLote}")
            put("properties", buildJsonObject {
                put("fecha_imagen", p.fechaImagen)
                put("fuente", p.fuente)
                put("labor", p.labor.nombre)
                put("producto", p.producto)
            })
            put("features", features)
        }.toString()
    }

    private fun redondear(v: Double) = (v * 1e7).roundToLong() / 1e7

    // --- ISO-XML (ISO 11783-10, versión 3) con grilla de zonas (tipo 1) ---

    fun isoXml(p: Prescripcion): List<ArchivoExportado> {
        val z = p.zonificacion
        val g = z.grilla
        // Tipo 1: un byte por celda con el código de zona; la primera celda es la del sudoeste y
        // cada fila va de oeste a este, de sur a norte
        val bin = ByteArray(g.total)
        var k = 0
        for (fila in g.filas - 1 downTo 0) for (col in 0 until g.columnas) {
            bin[k++] = z.etiquetas[fila * g.columnas + col].toByte()
        }

        fun num(v: Double, decimales: Int = 9) = decimalConPunto(v, decimales)
        fun xml(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        val anillo = GeometriaLote.anilloAbierto(p.lote)
        val areaM2 = (GeometriaLote.areaHectareas(anillo) * 10_000).roundToLong()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<ISO11783_TaskData VersionMajor=\"3\" VersionMinor=\"3\" ManagementSoftwareManufacturer=\"GRANEX\" ManagementSoftwareVersion=\"1.0\" DataTransferOrigin=\"1\">\n")
        sb.append("<CTR A=\"CTR1\" B=\"GRANEX\"/>\n")
        sb.append("<FRM A=\"FRM1\" B=\"Establecimiento\" I=\"CTR1\"/>\n")
        sb.append("<PFD A=\"PFD1\" C=\"${xml(p.nombreLote)}\" D=\"$areaM2\" E=\"CTR1\" F=\"FRM1\">\n")
        sb.append("<PLN A=\"1\" C=\"$areaM2\">\n<LSG A=\"1\">\n")
        (anillo + anillo.first()).forEach { (lat, lon) -> sb.append("<PNT A=\"2\" C=\"${num(lat)}\" D=\"${num(lon)}\"/>\n") }
        sb.append("</LSG>\n</PLN>\n</PFD>\n")
        if (p.labor.usaFertilizante) sb.append("<PDT A=\"PDT1\" B=\"${xml(p.producto.ifBlank { "Fertilizante" })}\"/>\n")
        if (p.labor.usaSemillas) sb.append("<PDT A=\"PDT2\" B=\"Semilla\"/>\n")
        sb.append("<VPN A=\"VPN1\" B=\"0\" C=\"0.01\" D=\"0\" E=\"kg/ha\"/>\n")
        sb.append("<VPN A=\"VPN2\" B=\"0\" C=\"10\" D=\"0\" E=\"sem/ha\"/>\n")
        sb.append("<TSK A=\"TSK1\" B=\"${xml("Prescripcion ${p.nombreLote}")}\" C=\"CTR1\" D=\"FRM1\" E=\"PFD1\" G=\"1\" H=\"0\" I=\"$ZONA_SIN_GPS\" J=\"0\">\n")

        fun tzn(codigo: Int, nombre: String, fertKg: Int, semHa: Int) {
            sb.append("<TZN A=\"$codigo\" B=\"${xml(nombre)}\">\n")
            if (p.labor.usaFertilizante) sb.append("<PDV A=\"0006\" B=\"${ConversionesDosis.kgHaAMgM2(fertKg)}\" C=\"PDT1\" E=\"VPN1\"/>\n")
            if (p.labor.usaSemillas) sb.append("<PDV A=\"000B\" B=\"${ConversionesDosis.semillasHaADdi11(semHa)}\" C=\"PDT2\" E=\"VPN2\"/>\n")
            sb.append("</TZN>\n")
        }
        tzn(0, "Fuera del lote", 0, 0)
        z.zonas.forEach { zona ->
            val d = p.dosisDe(zona.zona)
            tzn(zona.zona, "Zona ${zona.zona}", d.fertilizanteKgHa ?: 0, d.semillasHa ?: 0)
        }
        // Si se pierde la señal GPS se aplica la dosis promedio en lugar de cortar la aplicación
        tzn(ZONA_SIN_GPS, "Sin GPS (dosis promedio)", p.promedioFertilizante(), p.promedioSemillas())
        sb.append("<GRD A=\"${num(g.sur)}\" B=\"${num(g.oeste)}\" C=\"${num(g.pasoLat, 10)}\" D=\"${num(g.pasoLon, 10)}\" E=\"${g.columnas}\" F=\"${g.filas}\" G=\"GRD00001\" H=\"${bin.size}\" I=\"1\"/>\n")
        sb.append("</TSK>\n</ISO11783_TaskData>\n")
        return listOf(
            ArchivoExportado("TASKDATA/TASKDATA.XML", sb.toString().encodeToByteArray()),
            ArchivoExportado("TASKDATA/GRD00001.BIN", bin)
        )
    }

    private const val ZONA_SIN_GPS = 254

    // --- Paquetes ---

    /** Archivos para el monitor elegido, tal como deben quedar en el pendrive. */
    fun paraMonitor(p: Prescripcion, perfil: PerfilMonitor): List<ArchivoExportado> =
        if (perfil == PerfilMonitor.ISOBUS) isoXml(p) else shapefile(p, perfil)

    /** Todos los formatos en carpetas, para compartir con el agrónomo o con otra plataforma. */
    fun todosLosFormatos(p: Prescripcion, perfil: PerfilMonitor): List<ArchivoExportado> = buildList {
        if (perfil.esCuffia) shapefile(p, perfil).forEach { add(ArchivoExportado("CUFFIA/${it.ruta}", it.bytes)) }
        shapefile(p, PerfilMonitor.GENERICO).forEach { add(ArchivoExportado("SHAPEFILE/${it.ruta}", it.bytes)) }
        addAll(isoXml(p))
        add(ArchivoExportado("GEOJSON/prescripcion.geojson", geojson(p).encodeToByteArray()))
        add(ArchivoExportado("LEEME.txt", leeme(p, perfil).encodeToByteArray()))
    }

    /** ZIP sin compresión (método STORE): lo abre cualquier sistema y no hace falta java.util.zip. */
    fun zip(archivos: List<ArchivoExportado>): ByteArray = Zip.escribir(archivos)

    fun leeme(p: Prescripcion, perfil: PerfilMonitor): String = buildString {
        appendLine("PRESCRIPCIÓN DE DOSIS VARIABLE - GRANEX")
        appendLine("Lote: ${p.nombreLote}")
        appendLine("Base de las zonas: ${p.fuente}")
        (p.zonificacion.marco as? MarcoMaquina)?.let { m ->
            appendLine("Grilla de maquina: pasadas de ${decimalConPunto(m.anchoM, 1)} m, celdas de ${decimalConPunto(m.largoM, 0)} m de largo, rumbo ${decimalConPunto(m.rumboGrados, 0)} grados")
        }
        appendLine("Labor: ${p.labor.nombre}${if (p.producto.isNotBlank()) " - ${p.producto}" else ""}")
        appendLine()
        appendLine("ZONAS")
        p.zonificacion.zonas.forEach { z ->
            val d = p.dosisDe(z.zona)
            append("Zona ${z.zona}: ${decimalConPunto(z.areaHa, 2)} ha, ${p.nombreValor} medio ${decimalConPunto(z.indicePromedio, 3)}")
            if (p.labor.usaFertilizante) append(" | fertilizante ${d.fertilizanteKgHa ?: 0} kg/ha")
            if (p.labor.usaSemillas) append(" | ${d.semillasHa ?: 0} sem/ha")
            appendLine()
        }
        if (p.notas.isNotEmpty()) {
            appendLine()
            appendLine("SUELO Y RINDE")
            p.notas.forEach(::appendLine)
        }
        appendLine()
        when {
            perfil.esCuffia -> {
                appendLine("MONITOR ${perfil.nombre.uppercase()}")
                appendLine("1. Copiá los 4 archivos (.shp .shx .dbf .prj) sueltos en la raíz del pendrive o tarjeta SD, sin carpetas.")
                appendLine("2. En el monitor: Menú Dosificador > Copiar prescripciones en SD (si usás pendrive).")
                appendLine("3. Dosificación automática > Cargar mapa de prescripción nuevo > elegí ${nombreCorto(p.nombreLote)}.")
                if (perfil.semillasCada10m) {
                    appendLine("Columnas: FERT_LIN y FERT_COS en kg/ha; SEMILLAS en semillas cada 10 m lineales (distancia entre surcos ${decimalConPunto(p.distanciaSurcosM, 3)} m).")
                }
                if (perfil == PerfilMonitor.CUFFIA_FGS) appendLine("En el monitor cargá el factor de conversión: distancia entre surcos (m) / 10000 para semillas, 1 para fertilizante.")
                perfil.maxPoligonos?.let { appendLine("Polígonos: ${p.zonificacion.poligonos.size} (el monitor lee hasta $it).") }
            }
            perfil == PerfilMonitor.ISOBUS -> appendLine("ISOBUS: copiá la carpeta TASKDATA a la raíz del pendrive e importá la tarea desde el Task Controller.")
            else -> appendLine("Shapefile en WGS84. Columnas: FERT_KGHA (kg/ha) y SEM_HA (sem/ha).")
        }
        appendLine()
        appendLine("Las zonas surgen de imágenes satelitales y son relativas dentro del lote. Las dosis deben definirlas un ingeniero agrónomo con análisis de suelo, historial y objetivos de rendimiento. Probá la prescripción en una franja antes de aplicarla en todo el lote.")
    }
}

/** Escritor ZIP mínimo (sin compresión), en Kotlin común: lo usan Android y la web. */
internal object Zip {
    private val TABLA_CRC = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun crc32(datos: ByteArray): Int {
        var c = -1
        for (b in datos) c = TABLA_CRC[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    fun escribir(archivos: List<ArchivoExportado>): ByteArray {
        val nombres = archivos.map { it.ruta.encodeToByteArray() }
        val crcs = archivos.map { crc32(it.bytes) }
        val largoLocal = archivos.indices.sumOf { 30 + nombres[it].size + archivos[it].bytes.size }
        val largoCentral = archivos.indices.sumOf { 46 + nombres[it].size }
        val b = Bytes(largoLocal + largoCentral + 22)
        val desplazamientos = IntArray(archivos.size)
        var pos = 0
        archivos.forEachIndexed { i, a ->
            desplazamientos[i] = pos
            b.intLittle(0x04034b50).shortLittle(20).shortLittle(0x0800).shortLittle(0).shortLittle(0).shortLittle(0x21)
            b.intLittle(crcs[i]).intLittle(a.bytes.size).intLittle(a.bytes.size).shortLittle(nombres[i].size).shortLittle(0)
            b.bytes(nombres[i]).bytes(a.bytes)
            pos += 30 + nombres[i].size + a.bytes.size
        }
        archivos.forEachIndexed { i, a ->
            b.intLittle(0x02014b50).shortLittle(20).shortLittle(20).shortLittle(0x0800).shortLittle(0).shortLittle(0).shortLittle(0x21)
            b.intLittle(crcs[i]).intLittle(a.bytes.size).intLittle(a.bytes.size).shortLittle(nombres[i].size)
            b.shortLittle(0).shortLittle(0).shortLittle(0).shortLittle(0).intLittle(0).intLittle(desplazamientos[i])
            b.bytes(nombres[i])
        }
        b.intLittle(0x06054b50).shortLittle(0).shortLittle(0).shortLittle(archivos.size).shortLittle(archivos.size)
        b.intLittle(largoCentral).intLittle(largoLocal).shortLittle(0)
        return b.datos
    }
}
