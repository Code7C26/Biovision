package com.example.utils.vra

import com.example.utils.GeometriaLote
import kotlin.math.abs
import kotlin.math.sqrt

/** Contorno de un lote encontrado a partir de un toque en el mapa, listo para revisar y editar. */
class ContornoDetectado(
    /** (lat, lon), anillo abierto. */
    val vertices: List<Pair<Double, Double>>,
    val areaHa: Double,
    /** El lote sigue más allá del recuadro analizado: el contorno quedó cortado en ese borde. */
    val cortadoPorBorde: Boolean
)

class ErrorDeteccion(mensaje: String) : Exception(mensaje)

/**
 * Detecta el lote que contiene un punto en imágenes Sentinel-2 (10 m): crece una región desde el
 * punto tocado mientras los píxeles se parezcan a lo que ya se juntó, y se corta en los cambios
 * fuertes (caminos, alambrados con monte, otro cultivo o fecha de siembra). Puro, probado en
 * DetectorContornoTest.
 *
 * Cada imagen es una capa de 3 valores (índices de una fecha, o el color real R/G/B de 0 a 1). Dos
 * píxeles son de lotes distintos si **alguna** capa los distingue: la distancia es la mayor entre
 * capas, no un promedio. Con lotes pelados después de cosecha los índices casi no varían y el color
 * del suelo (rastrojo, labranza) es lo que los separa; promediando, esa diferencia se diluía y en
 * una prueba real se juntaron tres lotes vecinos (252 ha).
 *
 * Es un punto de partida: el contorno sigue los píxeles de 10 m, así que puede quedar unos metros
 * adentro del alambrado. La pantalla lo abre en el editor para corregirlo.
 */
object DetectorContorno {
    /** Diferencia típica (RMS en unidades de índice) por debajo de la cual dos píxeles son "el mismo lote". */
    const val UMBRAL_MINIMO = 0.045
    const val UMBRAL_MAXIMO = 0.14
    /** Más chico que esto no es un lote: el toque cayó en un manchón, un camino o un monte. */
    const val AREA_MINIMA_HA = 1.0
    /** Si la región ocupa casi todo el recuadro, la zona es pareja y no se ven los bordes. */
    private const val FRACCION_MAXIMA = 0.9
    /** Vértices más cerca que esto (en píxeles de 10 m) se unen: en el editor quedaban amontonados. */
    private const val DISTANCIA_MINIMA_PX = 3.0
    /** Más vértices que esto se simplifica con más tolerancia (hasta 70 m). */
    private const val MAX_VERTICES = 16

    fun detectar(imagenes: List<ImagenIndices>, lat: Double, lon: Double): ContornoDetectado {
        if (imagenes.isEmpty()) throw ErrorDeteccion("No hay imágenes de la zona.")
        val grilla = imagenes.first().grilla
        val w = grilla.columnas
        val h = grilla.filas
        val usadas = imagenes.filter { it.grilla.columnas == w && it.grilla.filas == h }
        val dims = usadas.size * 3
        val rasgos = Array(grilla.total) { i ->
            FloatArray(dims) { d ->
                val img = usadas[d / 3]
                when (d % 3) {
                    0 -> img.ndvi[i]
                    1 -> img.ndre[i]
                    else -> img.gndvi[i]
                }
            }
        }
        fun valido(i: Int) = rasgos[i].count { !it.isNaN() } >= 3

        val semillaIdx = grilla.celdaDe(lat, lon) ?: throw ErrorDeteccion("El punto quedó fuera de la imagen.")
        val sx = semillaIdx % w
        val sy = semillaIdx / w
        val cercanos = vecinos(sx, sy, 1, w, h).filter(::valido)
        if (cercanos.size < 5) {
            throw ErrorDeteccion("Tocaste sobre una zona sin dato (nube, agua, camino o monte). Tocá en el medio del cultivo.")
        }
        val semilla = FloatArray(dims) { d -> mediana(cercanos.mapNotNull { rasgos[it][d].takeIf { v -> !v.isNaN() }?.toDouble() }).toFloat() }

        // Tolerancia según lo parejo que es el lote alrededor del toque
        val locales = vecinos(sx, sy, 3, w, h).filter(::valido).map { distancia(rasgos[it], semilla) }.filter { it.isFinite() }
        val umbral = (3.0 * mediana(locales)).coerceIn(UMBRAL_MINIMO, UMBRAL_MAXIMO)

        // Crecimiento de la región (4 vecinos)
        val enRegion = BooleanArray(grilla.total)
        val suma = DoubleArray(dims)
        val cuenta = IntArray(dims)
        fun sumar(i: Int) = rasgos[i].forEachIndexed { d, v -> if (!v.isNaN()) { suma[d] += v; cuenta[d]++ } }
        val media = FloatArray(dims)
        fun actualizarMedia() { for (d in 0 until dims) media[d] = if (cuenta[d] > 0) (suma[d] / cuenta[d]).toFloat() else semilla[d] }
        val cola = ArrayDeque<Int>()
        enRegion[semillaIdx] = true
        cercanos.forEach { enRegion[it] = true; sumar(it); cola.addLast(it) }
        if (semillaIdx !in cercanos) cola.addLast(semillaIdx)
        actualizarMedia()
        var agregados = 0
        while (cola.isNotEmpty()) {
            val i = cola.removeFirst()
            val x = i % w
            val y = i / w
            for ((nx, ny) in arrayOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (enRegion[j] || !valido(j)) continue
                val r = rasgos[j]
                // Se parece a lo juntado, no se aleja de lo tocado y no hay un salto brusco con el vecino
                if (distancia(r, media) < umbral && distancia(r, semilla) < 1.5 * umbral &&
                    (!valido(i) || distancia(r, rasgos[i]) < umbral)
                ) {
                    enRegion[j] = true
                    sumar(j)
                    cola.addLast(j)
                    if (++agregados % 64 == 0) actualizarMedia()
                }
            }
        }

        // Apertura de 2 píxeles (2 erosiones + 2 dilataciones): corta los pasos de hasta ~40 m (caminos,
        // cabeceras parecidas) por donde la región se escapa a un lote vecino. Con 1 píxel, un lote
        // pelado de 120 ha se juntó con el de al lado.
        var mascara = dilatar(dilatar(erosionar(erosionar(enRegion, w, h), w, h), w, h), w, h).let { abierta ->
            BooleanArray(grilla.total) { abierta[it] && enRegion[it] }
        }
        mascara = componenteDe(mascara, semillaIdx, w, h)
            ?: throw ErrorDeteccion("No se distingue un lote alrededor del punto: probá tocando más cerca del centro, o dibujalo a mano.")
        mascara = rellenarHuecos(mascara, w, h)
        // Cierre de 2 píxeles: rellena entrantes chicos del borde (surcos, manchones pegados al alambrado)
        mascara = rellenarHuecos(erosionar(erosionar(dilatar(dilatar(mascara, w, h), w, h), w, h), w, h), w, h)
            .let { cerrada -> componenteDe(cerrada, semillaIdx, w, h) ?: mascara }
        // Mayoría 3 × 3: saca los dientes de un píxel del borde, que dejaban vértices amontonados
        mascara = componenteDe(mayoria(mascara, w, h), semillaIdx, w, h)?.let { rellenarHuecos(it, w, h) } ?: mascara

        val pixeles = mascara.count { it }
        if (pixeles > FRACCION_MAXIMA * grilla.total) {
            throw ErrorDeteccion("La zona es muy pareja y no se distinguen los bordes del lote. Dibujalo a mano.")
        }
        val cortado = (0 until w).any { mascara[it] || mascara[(h - 1) * w + it] } ||
            (0 until h).any { mascara[it * w] || mascara[it * w + w - 1] }

        // Alambrados y caminos: si sus líneas rectas cierran el lote alrededor de la región, el lote es
        // lo que encierran (entra el bajo que el cultivo no juntó, sale lo que se escapó por una tranquera)
        val bordes = DetectorAlambrados.fuertesCerca(DetectorAlambrados.bordes(rasgos, w, h, ::distancia), mascara, w, h)
        val alambrados = DetectorAlambrados.segmentos(bordes, w, h)
        DetectorAlambrados.celda(alambrados, bordes, mascara, erosionar(erosionar(mascara, w, h), w, h), w, h, sx, sy) { i ->
            valido(i) && distancia(rasgos[i], media) < umbral
        }?.let { celda ->
            val vertices = unirCercanos(celda, DISTANCIA_MINIMA_PX)
                .map { (x, y) -> (grilla.norte - y * grilla.pasoLat) to (grilla.oeste + x * grilla.pasoLon) }
            val area = GeometriaLote.areaHectareas(vertices)
            if (vertices.size >= 3 && area >= AREA_MINIMA_HA) return ContornoDetectado(vertices, area, cortadoPorBorde = false)
        }

        // El borde de 10 m es una escalera con dientes de 1–2 píxeles (píxeles mezclados): se suaviza,
        // se simplifica y se unen los vértices muy cercanos, que en el editor quedaban amontonados.
        // Si el resultado se cruza, se prueba antes con más tolerancia que con menos.
        val anillo = suavizar(trazarContorno(mascara, w, h))
        val vertices: List<Pair<Double, Double>>
        // Un borde muy irregular se simplifica más hasta [MAX_VERTICES]: es más fácil corregir a mano un
        // polígono de 12 vértices que uno con 50 amontonados
        var elegido: List<Pair<Double, Double>>? = null
        for (tolerancia in doubleArrayOf(2.5, 3.5, 5.0, 7.0, 1.5, 1.0)) {
            val simple = unirCercanos(simplificar(anillo, tolerancia), DISTANCIA_MINIMA_PX)
                .map { (x, y) -> (grilla.norte - y * grilla.pasoLat) to (grilla.oeste + x * grilla.pasoLon) }
            if (simple.size < 3 || GeometriaLote.tieneAutointerseccion(simple)) continue
            if (elegido == null) elegido = simple
            if (simple.size <= MAX_VERTICES) {
                elegido = simple
                break
            }
        }
        vertices = elegido ?: emptyList()
        val area = GeometriaLote.areaHectareas(vertices)
        if (vertices.size < 3 || area < AREA_MINIMA_HA) {
            throw ErrorDeteccion("No se distingue un lote alrededor del punto: probá tocando más cerca del centro, o dibujalo a mano.")
        }
        return ContornoDetectado(vertices, area, cortado)
    }

    // ---------- Distancias y vecindades ----------

    /**
     * Diferencia entre dos píxeles: la mayor entre capas de la diferencia RMS de sus 3 valores, sobre
     * las capas que tienen ambos.
     */
    internal fun distancia(a: FloatArray, b: FloatArray): Double {
        var maxima = -1.0
        var capa = 0
        while (capa < a.size) {
            var s = 0.0
            var n = 0
            for (d in capa until minOf(capa + 3, a.size)) {
                val x = a[d]
                val y = b[d]
                if (x.isNaN() || y.isNaN()) continue
                s += (x - y).toDouble() * (x - y)
                n++
            }
            if (n > 0) maxima = maxOf(maxima, sqrt(s / n))
            capa += 3
        }
        return if (maxima < 0) Double.POSITIVE_INFINITY else maxima
    }

    private fun vecinos(x: Int, y: Int, radio: Int, w: Int, h: Int): List<Int> = buildList {
        for (dy in -radio..radio) for (dx in -radio..radio) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h) add(ny * w + nx)
        }
    }

    private fun mediana(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val o = v.sorted()
        return if (o.size % 2 == 1) o[o.size / 2] else (o[o.size / 2 - 1] + o[o.size / 2]) / 2
    }

    // ---------- Morfología ----------

    private fun erosionar(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w
        val y = i / w
        m[i] && x > 0 && x < w - 1 && y > 0 && y < h - 1 && m[i - 1] && m[i + 1] && m[i - w] && m[i + w] ||
            // En el borde del recuadro no se erosiona hacia afuera: el lote puede seguir
            m[i] && (x == 0 || x == w - 1 || y == 0 || y == h - 1) &&
            (x == 0 || m[i - 1]) && (x == w - 1 || m[i + 1]) && (y == 0 || m[i - w]) && (y == h - 1 || m[i + w])
    }

    private fun dilatar(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w
        val y = i / w
        m[i] || (x > 0 && m[i - 1]) || (x < w - 1 && m[i + 1]) || (y > 0 && m[i - w]) || (y < h - 1 && m[i + w])
    }

    /** La componente (4 vecinos) que contiene la semilla o, si la apertura la borró, la más cercana. */
    private fun componenteDe(m: BooleanArray, semilla: Int, w: Int, h: Int): BooleanArray? {
        val inicio = if (m[semilla]) semilla else {
            val sx = semilla % w
            val sy = semilla / w
            m.indices.filter { m[it] }.minByOrNull { abs(it % w - sx) + abs(it / w - sy) }
                ?.takeIf { abs(it % w - sx) + abs(it / w - sy) <= 3 } ?: return null
        }
        val salida = BooleanArray(m.size)
        val cola = ArrayDeque<Int>().apply { addLast(inicio) }
        salida[inicio] = true
        while (cola.isNotEmpty()) {
            val i = cola.removeFirst()
            val x = i % w
            val y = i / w
            for ((nx, ny) in arrayOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (m[j] && !salida[j]) {
                    salida[j] = true
                    cola.addLast(j)
                }
            }
        }
        return salida
    }

    private fun mayoria(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w
        val y = i / w
        var n = 0
        var total = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            total++
            if (m[ny * w + nx]) n++
        }
        n * 2 > total
    }

    /** Árboles, aguadas o manchones adentro del lote son parte del lote. */
    private fun rellenarHuecos(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val afuera = BooleanArray(m.size)
        val cola = ArrayDeque<Int>()
        for (i in m.indices) {
            val x = i % w
            val y = i / w
            if (!m[i] && (x == 0 || y == 0 || x == w - 1 || y == h - 1)) {
                afuera[i] = true
                cola.addLast(i)
            }
        }
        while (cola.isNotEmpty()) {
            val i = cola.removeFirst()
            val x = i % w
            val y = i / w
            for ((nx, ny) in arrayOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (!m[j] && !afuera[j]) {
                    afuera[j] = true
                    cola.addLast(j)
                }
            }
        }
        return BooleanArray(m.size) { !afuera[it] }
    }

    // ---------- Contorno ----------

    /**
     * Borde exterior de la máscara siguiendo los lados de los píxeles (x = columna, y = fila hacia el
     * sur), con el interior a la derecha. En las esquinas donde se tocan dos píxeles en diagonal se
     * dobla a la derecha, así cada parte da su propio anillo; se devuelve el de mayor superficie.
     */
    internal fun trazarContorno(m: BooleanArray, w: Int, h: Int): List<Pair<Double, Double>> {
        fun dentro(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        val salidas = HashMap<Long, MutableList<Long>>()
        fun clave(x: Int, y: Int) = y.toLong() * (w + 1) + x
        fun lado(x1: Int, y1: Int, x2: Int, y2: Int) {
            salidas.getOrPut(clave(x1, y1)) { mutableListOf() }.add(clave(x2, y2))
        }
        for (y in 0 until h) for (x in 0 until w) {
            if (!m[y * w + x]) continue
            if (!dentro(x, y - 1)) lado(x, y, x + 1, y)
            if (!dentro(x + 1, y)) lado(x + 1, y, x + 1, y + 1)
            if (!dentro(x, y + 1)) lado(x + 1, y + 1, x, y + 1)
            if (!dentro(x - 1, y)) lado(x, y + 1, x, y)
        }
        fun xy(c: Long) = (c % (w + 1)).toInt() to (c / (w + 1)).toInt()

        var mejor: List<Pair<Double, Double>> = emptyList()
        var mejorArea = 0.0
        while (true) {
            val inicio = salidas.entries.firstOrNull { it.value.isNotEmpty() }?.key ?: break
            val anillo = mutableListOf<Pair<Double, Double>>()
            var actual = inicio
            var dirX = 0
            var dirY = 0
            var pasos = 0
            do {
                val opciones = salidas[actual] ?: break
                if (opciones.isEmpty()) break
                val (ax, ay) = xy(actual)
                val siguiente = if (opciones.size == 1) opciones[0] else opciones.maxBy { c ->
                    val (bx, by) = xy(c)
                    // Producto vectorial con y hacia abajo: positivo = giro a la derecha
                    (dirX * (by - ay) - dirY * (bx - ax)).toDouble()
                }
                opciones.remove(siguiente)
                anillo += ax.toDouble() to ay.toDouble()
                val (bx, by) = xy(siguiente)
                dirX = bx - ax
                dirY = by - ay
                actual = siguiente
            } while (actual != inicio && ++pasos < 4 * m.size)
            val area = abs(areaAnillo(anillo))
            if (area > mejorArea) {
                mejorArea = area
                mejor = anillo
            }
        }
        return quitarColineales(mejor)
    }

    private fun areaAnillo(a: List<Pair<Double, Double>>): Double {
        var s = 0.0
        for (i in a.indices) {
            val (x1, y1) = a[i]
            val (x2, y2) = a[(i + 1) % a.size]
            s += x1 * y2 - x2 * y1
        }
        return s / 2
    }

    private fun quitarColineales(a: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (a.size < 4) return a
        return a.filterIndexed { i, p ->
            val (px, py) = a[(i - 1 + a.size) % a.size]
            val (nx, ny) = a[(i + 1) % a.size]
            (p.first - px) * (ny - p.second) - (p.second - py) * (nx - p.first) != 0.0
        }
    }

    /** Promedio móvil de 7 puntos sobre el anillo: convierte la escalera de píxeles en una línea. */
    internal fun suavizar(a: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (a.size < 8) return a
        // Se densifica primero (un punto por lado de píxel) para que el promedio no corte esquinas largas
        val denso = buildList {
            for (i in a.indices) {
                val (x1, y1) = a[i]
                val (x2, y2) = a[(i + 1) % a.size]
                val pasos = maxOf(1, kotlin.math.max(abs(x2 - x1), abs(y2 - y1)).toInt())
                for (k in 0 until pasos) add(x1 + (x2 - x1) * k / pasos to y1 + (y2 - y1) * k / pasos)
            }
        }
        val n = denso.size
        return List(n) { i ->
            var sx = 0.0
            var sy = 0.0
            for (d in -3..3) {
                val (x, y) = denso[(i + d + n) % n]
                sx += x
                sy += y
            }
            sx / 7 to sy / 7
        }
    }

    /** Une vértices consecutivos a menos de [minimo] píxeles (queda el punto medio). */
    internal fun unirCercanos(a: List<Pair<Double, Double>>, minimo: Double): List<Pair<Double, Double>> {
        if (a.size <= 4) return a
        val salida = mutableListOf<Pair<Double, Double>>()
        for (p in a) {
            val ultimo = salida.lastOrNull()
            if (ultimo != null && kotlin.math.hypot(p.first - ultimo.first, p.second - ultimo.second) < minimo) {
                salida[salida.lastIndex] = (p.first + ultimo.first) / 2 to (p.second + ultimo.second) / 2
            } else salida += p
        }
        if (salida.size > 3) {
            val (x0, y0) = salida.first()
            val (xn, yn) = salida.last()
            if (kotlin.math.hypot(x0 - xn, y0 - yn) < minimo) {
                salida[0] = (x0 + xn) / 2 to (y0 + yn) / 2
                salida.removeAt(salida.lastIndex)
            }
        }
        return salida
    }

    /** Douglas–Peucker sobre un anillo cerrado: se parte en el vértice más lejano al primero. */
    internal fun simplificar(anillo: List<Pair<Double, Double>>, tolerancia: Double): List<Pair<Double, Double>> {
        if (anillo.size < 4 || tolerancia <= 0.0) return anillo
        val (x0, y0) = anillo[0]
        val lejano = anillo.indices.maxBy { i -> (anillo[i].first - x0).let { it * it } + (anillo[i].second - y0).let { it * it } }
        val ida = dp(anillo.subList(0, lejano + 1), tolerancia)
        val vuelta = dp(anillo.subList(lejano, anillo.size) + listOf(anillo[0]), tolerancia)
        return ida.dropLast(1) + vuelta.dropLast(1)
    }

    private fun dp(p: List<Pair<Double, Double>>, tol: Double): List<Pair<Double, Double>> {
        if (p.size < 3) return p
        val (ax, ay) = p.first()
        val (bx, by) = p.last()
        val largo = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        var max = -1.0
        var indice = 0
        for (i in 1 until p.size - 1) {
            val (x, y) = p[i]
            val d = if (largo == 0.0) sqrt((x - ax) * (x - ax) + (y - ay) * (y - ay))
            else abs((bx - ax) * (ay - y) - (ax - x) * (by - ay)) / largo
            if (d > max) {
                max = d
                indice = i
            }
        }
        return if (max > tol) dp(p.subList(0, indice + 1), tol).dropLast(1) + dp(p.subList(indice, p.size), tol)
        else listOf(p.first(), p.last())
    }
}
