package com.example.utils.vra

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Alambrados, caminos y cortadas: líneas rectas largas donde la imagen cambia de golpe. Sirven para
 * cerrar el lote "entre alambrados" alrededor de la región que encontró [DetectorContorno]: así
 * entra la franja de bajo o la laguna que el cultivo no deja juntar, y queda afuera lo que la región
 * se comió del casco o de un vecino. Puro, probado en DetectorContornoTest.
 *
 * Coordenadas en píxeles de la grilla: x = columna, y = fila hacia el sur; el píxel i ocupa [i, i+1],
 * con centro en i + 0,5 (las mismas esquinas que traza el contorno).
 */
internal object DetectorAlambrados {
    /** Una línea recta encontrada: n·p = rho (n = (cos, sin)) y los píxeles del tramo continuo más largo. */
    class Segmento(val cos: Double, val sin: Double, val rho: Double, val pixeles: List<Int>)

    /** Tramo continuo mínimo para ser alambrado o camino (15 píxeles = 150 m). */
    private const val LARGO_MINIMO = 15
    private const val HUECO_MAXIMO = 3
    private const val MAX_LINEAS = 40
    /** Una línea sirve para cerrar el lote si corre a menos de esto de la región (25 píxeles = 250 m). */
    private const val DISTANCIA_MAXIMA = 25
    /** Cuánto del lote "entre alambrados" puede faltar de la región, y cuánto más grande puede ser. */
    private const val COBERTURA_MINIMA = 0.75
    private const val AGRANDADO_MAXIMO = 1.7
    /**
     * Si todos los lados de la celda corren sobre un borde marcado (alambrado o camino de verdad) se
     * admite que sea bastante más grande que la región: la región solo junta lo que se parece al toque
     * y deja afuera franjas de otra labranza o de bajo del mismo lote. Solo si ningún alambrado cruza
     * la celda por adentro: eso serían dos lotes.
     */
    private const val AGRANDADO_CON_CERCO_NITIDO = 2.4
    private const val APOYO_NITIDO = 0.8
    /** Un tramo paralelo a un lado de la celda, adentro y más largo que esto (fracción del lado típico), es un alambrado interno. */
    private const val ALAMBRADO_INTERNO = 0.4
    private const val APOYO_MINIMO = 0.6
    /** cos 10°: dos líneas son paralelas si difieren menos que eso. */
    private const val COS_PARALELO = 0.985
    /** Píxeles hacia adentro de cada lado: lo que está más cerca es el propio alambrado. */
    private const val MARGEN_INTERNO = 3.0
    private const val OTRO_LADO_PARECIDO = 0.4

    /**
     * Fuerza del borde en cada píxel: la mayor diferencia (entre capas) entre los vecinos de los dos
     * lados, en horizontal o en vertical. Un camino de un píxel da borde en sus dos costados.
     */
    fun bordes(rasgos: Array<FloatArray>, w: Int, h: Int, distancia: (FloatArray, FloatArray) -> Double): FloatArray =
        FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            var m = 0.0
            if (x in 1 until w - 1) distancia(rasgos[i - 1], rasgos[i + 1]).takeIf { it.isFinite() }?.let { m = maxOf(m, it) }
            if (y in 1 until h - 1) distancia(rasgos[i - w], rasgos[i + w]).takeIf { it.isFinite() }?.let { m = maxOf(m, it) }
            m.toFloat()
        }

    /** Los bordes fuertes: el 15 % más marcado, y nunca una diferencia menor a 0,06 (ruido del cultivo). */
    fun fuertes(fuerza: FloatArray): BooleanArray {
        val umbral = umbral(fuerza.filter { it > 0f }) ?: return BooleanArray(fuerza.size)
        return BooleanArray(fuerza.size) { fuerza[it] >= umbral }
    }

    /**
     * Los bordes fuertes alrededor de la región (su recuadro más [DISTANCIA_MAXIMA]): solo ahí puede
     * haber un alambrado que la cierre. El 15 % más marcado se mide en esa zona y también en todo el
     * recuadro, y vale el más bajo: un río, un monte o un casco en otra parte del recuadro dan bordes
     * mucho más fuertes que un alambrado entre dos lotes, y en un caso real (umbral 0,26 contra
     * alambrados de 0,10–0,15) el lote se quedó sin ninguna línea y perdió las cabeceras.
     */
    fun fuertesCerca(fuerza: FloatArray, region: BooleanArray, w: Int, h: Int): BooleanArray {
        var x0 = w
        var x1 = -1
        var y0 = h
        var y1 = -1
        for (i in region.indices) if (region[i]) {
            val x = i % w
            val y = i / w
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        if (x1 < 0) return BooleanArray(fuerza.size)
        val margen = DISTANCIA_MAXIMA + 2
        x0 = maxOf(0, x0 - margen)
        x1 = minOf(w - 1, x1 + margen)
        y0 = maxOf(0, y0 - margen)
        y1 = minOf(h - 1, y1 + margen)
        fun enZona(i: Int) = i % w in x0..x1 && i / w in y0..y1
        val global = umbral(fuerza.filter { it > 0f }) ?: return BooleanArray(fuerza.size)
        val local = umbral(fuerza.indices.filter { enZona(it) && fuerza[it] > 0f }.map { fuerza[it] }) ?: global
        val u = minOf(global, local)
        return BooleanArray(fuerza.size) { enZona(it) && fuerza[it] >= u }
    }

    private fun umbral(positivos: List<Float>): Float? {
        if (positivos.isEmpty()) return null
        val o = positivos.sorted()
        return maxOf(0.06f, o[(o.size * 0.85).toInt().coerceAtMost(o.size - 1)])
    }

    /**
     * Transformada de Hough sobre los bordes fuertes y, por cada pico, su tramo continuo más largo. Un
     * tramo que corre casi todo sobre el de una línea más votada se descarta: una línea inclinada unos
     * grados que cruza la franja de borde de un alambrado juntaba un tramo largo y le cortaba una cuña
     * al lote.
     */
    fun segmentos(esBorde: BooleanArray, w: Int, h: Int): List<Segmento> {

        val angulos = 180
        val cosenos = DoubleArray(angulos) { cos(it * PI / angulos) }
        val senos = DoubleArray(angulos) { sin(it * PI / angulos) }
        val diagonal = ceil(hypot(w.toDouble(), h.toDouble())).toInt()
        val filasRho = 2 * diagonal + 1
        val votos = IntArray(angulos * filasRho)
        for (i in esBorde.indices) {
            if (!esBorde[i]) continue
            val px = i % w + 0.5
            val py = i / w + 0.5
            for (a in 0 until angulos) {
                val r = (px * cosenos[a] + py * senos[a]).let { kotlin.math.round(it).toInt() } + diagonal
                votos[a * filasRho + r]++
            }
        }

        // Picos de mayor a menor, sin repetir líneas casi iguales
        val orden = votos.indices.filter { votos[it] >= LARGO_MINIMO }.sortedByDescending { votos[it] }
        val elegidos = mutableListOf<Pair<Int, Int>>()
        for (k in orden) {
            if (elegidos.size >= MAX_LINEAS * 3) break
            val a = k / filasRho
            val r = k % filasRho
            val repetido = elegidos.any { (a2, r2) ->
                val da = minOf(abs(a - a2), angulos - abs(a - a2))
                da <= 4 && abs(r - r2) <= 4
            }
            if (!repetido) elegidos += a to r
        }

        val salida = mutableListOf<Segmento>()
        val usado = BooleanArray(esBorde.size)
        for ((a, r) in elegidos) {
            val c = cosenos[a]
            val s = senos[a]
            val rho = (r - diagonal).toDouble()
            // Recorrer la línea: p(t) = rho·n + t·(−s, c)
            var mejor: List<Int> = emptyList()
            var actual = mutableListOf<Int>()
            var hueco = 0
            for (t in -diagonal..diagonal) {
                val x = rho * c - t * s
                val y = rho * s + t * c
                val indice = pixelEn(x, y, w, h) ?: continue
                val toca = esBorde[indice] ||
                    pixelEn(x + c, y + s, w, h)?.let { esBorde[it] } == true ||
                    pixelEn(x - c, y - s, w, h)?.let { esBorde[it] } == true
                if (toca) {
                    actual.add(indice)
                    hueco = 0
                } else if (actual.isNotEmpty() && ++hueco > HUECO_MAXIMO) {
                    if (actual.size > mejor.size) mejor = actual
                    actual = mutableListOf()
                    hueco = 0
                }
            }
            if (actual.size > mejor.size) mejor = actual
            if (mejor.size < LARGO_MINIMO || mejor.count { usado[it] } > mejor.size / 2) continue
            salida += Segmento(c, s, rho, mejor)
            // La franja del tramo (±2 píxeles a lo ancho) queda tomada por esta línea
            for (indice in mejor) {
                val x = indice % w + 0.5
                val y = indice / w + 0.5
                for (k in -2..2) pixelEn(x + k * c, y + k * s, w, h)?.let { usado[it] = true }
            }
            if (salida.size >= MAX_LINEAS) break
        }
        return salida
    }

    private fun pixelEn(x: Double, y: Double, w: Int, h: Int): Int? {
        val col = floor(x).toInt()
        val fila = floor(y).toInt()
        return if (col in 0 until w && fila in 0 until h) fila * w + col else null
    }

    /**
     * El lote entre alambrados: el polígono convexo que dejan, del lado de la semilla, las líneas que
     * corren junto a la región sin atravesarla. null si algún lado no tiene alambrado (queda el borde
     * del recuadro) o si el resultado no se parece a la región.
     */
    fun celda(
        segmentos: List<Segmento>,
        esBorde: BooleanArray,
        region: BooleanArray,
        nucleo: BooleanArray,
        w: Int,
        h: Int,
        semillaX: Int,
        semillaY: Int,
        /** El píxel tiene dato y se parece a lo que juntó la región. */
        pareceLote: (Int) -> Boolean
    ): List<Pair<Double, Double>>? {
        val total = region.count { it }
        if (total == 0) return null
        val distancia = distanciaARegion(region, w, h)
        val sx = semillaX + 0.5
        val sy = semillaY + 0.5

        class Semiplano(val c: Double, val s: Double, val rho: Double, val lado: Double)
        val semiplanos = segmentos.mapNotNullTo(mutableListOf()) { seg ->
            val cerca = seg.pixeles.count { distancia[it] <= DISTANCIA_MAXIMA }
            val adentro = seg.pixeles.count { nucleo[it] }
            val valor = seg.cos * sx + seg.sin * sy - seg.rho
            val lado = if (valor > 0) 1.0 else -1.0
            // Atraviesa el lote (cañada, surco, cambio de cultivo adentro), pasa por la semilla o del otro
            // lado sigue el mismo lote (un canal o una huella adentro): no cierra nada
            if (cerca < 12 || adentro > 0.15 * seg.pixeles.size || abs(valor) < 1.5 ||
                otroLadoEsLote(seg, lado, pareceLote, w, h)
            ) null
            else Semiplano(seg.cos, seg.sin, seg.rho, lado)
        }
        fun dentro(p: Semiplano, x: Double, y: Double) = p.lado * (p.c * x + p.s * y - p.rho) >= 0

        // Una línea que deja afuera mucho de la región está mal elegida: se saca la peor hasta que entre
        val pixelesRegion = region.indices.filter { region[it] }
        while (semiplanos.isNotEmpty()) {
            val afuera = semiplanos.map { p -> pixelesRegion.count { i -> !dentro(p, i % w + 0.5, i / w + 0.5) } }
            val cubiertos = pixelesRegion.count { i -> semiplanos.all { p -> dentro(p, i % w + 0.5, i / w + 0.5) } }
            if (cubiertos >= COBERTURA_MINIMA * total) break
            semiplanos.removeAt(afuera.indices.maxBy { afuera[it] })
        }
        // Cada lado tiene que correr sobre un borde de verdad (con alguna tranquera o hueco). Un tramo
        // recto corto del borde ondulado de un bajo arma una línea que corta la celda en diagonal: su
        // lado queda sin apoyo y se saca esa línea
        var poligono: List<Pair<Double, Double>>
        var apoyoMinimo = 1.0
        while (true) {
            if (semiplanos.size < 3) return null
            poligono = listOf(0.0 to 0.0, w.toDouble() to 0.0, w.toDouble() to h.toDouble(), 0.0 to h.toDouble())
            for (p in semiplanos) {
                poligono = recortar(poligono) { (x, y) -> p.lado * (p.c * x + p.s * y - p.rho) }
                if (poligono.size < 3) return null
            }
            var sinApoyo: Semiplano? = null
            apoyoMinimo = 1.0
            for (i in poligono.indices) {
                val (x1, y1) = poligono[i]
                val (x2, y2) = poligono[(i + 1) % poligono.size]
                // El lado sale de una línea si sus dos puntas están sobre ella; si no, es el borde del
                // recuadro: ahí no hay alambrado y la celda no está cerrada
                val linea = semiplanos.firstOrNull { p ->
                    abs(p.c * x1 + p.s * y1 - p.rho) < 1e-6 && abs(p.c * x2 + p.s * y2 - p.rho) < 1e-6
                } ?: if (hypot(x2 - x1, y2 - y1) < 1e-6) continue else return null
                val apoyo = apoyo(x1, y1, x2, y2, esBorde, w, h)
                if (apoyo < APOYO_MINIMO) {
                    sinApoyo = linea
                    break
                }
                apoyoMinimo = minOf(apoyoMinimo, apoyo)
            }
            if (sinApoyo == null) break
            semiplanos.remove(sinApoyo)
        }
        // Mucho más grande que la región: las líneas encierran también otro lote o un casco
        val celdas = (0 until w * h).count { i -> semiplanos.all { p -> dentro(p, i % w + 0.5, i / w + 0.5) } }
        if (celdas > AGRANDADO_MAXIMO * total) {
            if (celdas > AGRANDADO_CON_CERCO_NITIDO * total || apoyoMinimo < APOYO_NITIDO) return null
            // Algún alambrado parejo a los lados corre por adentro de la celda: encierra dos lotes
            val ladoTipico = kotlin.math.sqrt(celdas.toDouble())
            val interno = segmentos.any { seg ->
                val paralelo = semiplanos.any { p -> abs(p.c * seg.cos + p.s * seg.sin) >= COS_PARALELO }
                paralelo && seg.pixeles.count { i ->
                    val x = i % w + 0.5
                    val y = i / w + 0.5
                    semiplanos.all { p -> p.lado * (p.c * x + p.s * y - p.rho) >= MARGEN_INTERNO }
                } >= ALAMBRADO_INTERNO * ladoTipico
            }
            if (interno) return null
        }
        return poligono
    }

    /**
     * Del otro lado de la línea (de 3 a 8 píxeles, a lo largo de su tramo) la mayoría se parece al lote:
     * es un canal o una huella que corre adentro. En un lote real pelado, el canal del bajo era una recta
     * de 1 km y cortaba la franja de abajo, del mismo suelo.
     */
    private fun otroLadoEsLote(seg: Segmento, lado: Double, pareceLote: (Int) -> Boolean, w: Int, h: Int): Boolean =
        fraccionOtroLado(seg, lado, pareceLote, w, h) >= OTRO_LADO_PARECIDO

    internal fun fraccionOtroLado(seg: Segmento, lado: Double, pareceLote: (Int) -> Boolean, w: Int, h: Int): Double {
        var muestras = 0
        var parecidas = 0
        for (indice in seg.pixeles) {
            val x = indice % w + 0.5
            val y = indice / w + 0.5
            for (k in 3..8) {
                val j = pixelEn(x - lado * k * seg.cos, y - lado * k * seg.sin, w, h) ?: continue
                muestras++
                if (pareceLote(j)) parecidas++
            }
        }
        return if (muestras == 0) 0.0 else parecidas.toDouble() / muestras
    }

    /** Fracción del lado (sin las puntas) que cae sobre un borde fuerte, o a un píxel de él. */
    private fun apoyo(x1: Double, y1: Double, x2: Double, y2: Double, esBorde: BooleanArray, w: Int, h: Int): Double {
        val largo = hypot(x2 - x1, y2 - y1)
        if (largo < 6) return 1.0
        val ux = (x2 - x1) / largo
        val uy = (y2 - y1) / largo
        var muestras = 0
        var apoyos = 0
        var t = 2.0
        while (t <= largo - 2) {
            val x = x1 + ux * t
            val y = y1 + uy * t
            muestras++
            if ((-1..1).any { k -> pixelEn(x - k * uy, y + k * ux, w, h)?.let { esBorde[it] } == true }) apoyos++
            t += 0.5
        }
        return if (muestras == 0) 1.0 else apoyos.toDouble() / muestras
    }

    /** Sutherland–Hodgman: deja la parte del polígono donde [lado] ≥ 0. */
    private fun recortar(poligono: List<Pair<Double, Double>>, lado: (Pair<Double, Double>) -> Double): List<Pair<Double, Double>> {
        val salida = mutableListOf<Pair<Double, Double>>()
        for (i in poligono.indices) {
            val a = poligono[i]
            val b = poligono[(i + 1) % poligono.size]
            val la = lado(a)
            val lb = lado(b)
            if (la >= 0) salida += a
            if ((la >= 0) != (lb >= 0)) {
                val t = la / (la - lb)
                salida += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        }
        return salida
    }

    /** Distancia (en pasos de 4 vecinos) de cada píxel a la región; 0 adentro. */
    private fun distanciaARegion(region: BooleanArray, w: Int, h: Int): IntArray {
        val d = IntArray(region.size) { if (region[it]) 0 else Int.MAX_VALUE }
        val cola = ArrayDeque<Int>()
        region.indices.filter { region[it] }.forEach { cola.addLast(it) }
        while (cola.isNotEmpty()) {
            val i = cola.removeFirst()
            if (d[i] >= DISTANCIA_MAXIMA + 1) continue
            val x = i % w
            val y = i / w
            for ((nx, ny) in arrayOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (d[j] > d[i] + 1) {
                    d[j] = d[i] + 1
                    cola.addLast(j)
                }
            }
        }
        return d
    }
}
