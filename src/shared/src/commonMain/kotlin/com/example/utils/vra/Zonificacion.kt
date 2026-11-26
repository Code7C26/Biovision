package com.example.utils.vra

import com.example.utils.GeometriaLote
import com.example.utils.radianes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

enum class MetodoZonificacion(val nombre: String) {
    KMEANS("Cortes naturales (k-means)"),
    CUANTILES("Superficies iguales (cuantiles)")
}

data class ConfigZonificacion(
    val zonas: Int = 3,
    /** Lado de la celda; con [anchoLaborM], largo de la celda a lo largo de la pasada. */
    val celdaM: Double = 20.0,
    /** Ancho de labor de la máquina: las celdas son pasadas de este ancho. null = celdas cuadradas al norte. */
    val anchoLaborM: Double? = null,
    /** Sentido de siembra (0 = norte-sur, 90 = este-oeste); solo con [anchoLaborM]. */
    val rumboGrados: Double? = null,
    val areaMinimaHa: Double = 0.5,
    /** Máximo de polígonos que lee el monitor (null = sin límite). */
    val maxPoligonos: Int? = null,
    val metodo: MetodoZonificacion = MetodoZonificacion.KMEANS
)

data class ResumenZona(
    val zona: Int,
    val celdas: Int,
    val areaHa: Double,
    val indicePromedio: Double,
    val indiceMin: Double,
    val indiceMax: Double
)

/** Polígono de una zona en (lat, lon): el primer anillo es el exterior (horario), el resto son huecos (antihorarios). Anillos cerrados. */
data class PoligonoZona(val zona: Int, val anillos: List<List<Pair<Double, Double>>>, val areaHa: Double)

class Zonificacion(
    /** Grilla norte-sur para ISO-XML: la misma de las zonas o, con grilla de máquina, una más fina. */
    val grilla: GrillaGeografica,
    /** Zona por celda de [grilla] (1 = índice más bajo); 0 = fuera del lote. */
    val etiquetas: IntArray,
    val zonas: List<ResumenZona>,
    val poligonos: List<PoligonoZona>,
    val areaCeldaHa: Double,
    /** Área mínima de parche realmente aplicada (puede subir para respetar el límite de polígonos). */
    val areaMinimaAplicadaHa: Double,
    /** true si el límite de polígonos del monitor obligó a subir el área mínima. */
    val areaSubioPorLimite: Boolean = false,
    /** Celdas con que se calcularon las zonas (geográficas o de máquina). */
    val marco: MarcoCeldas = grilla,
    /** Grilla de máquina: ancho de labor y rumbo usados; null con celdas cuadradas al norte. */
    val anchoLaborM: Double? = null,
    val rumboGrados: Double? = null
)

class ErrorZonificacion(mensaje: String) : Exception(mensaje)

/**
 * Cuánta variación del índice explica cada cantidad de zonas (0 a 1) y cuántas zonas se justifican.
 * Más zonas siempre explican algo más; se recomienda la menor cantidad a partir de la cual agregar
 * otra zona casi no suma.
 */
data class RecomendacionZonas(
    val zonas: Int,
    val explicada: Map<Int, Double>,
    /** Coeficiente de variación del índice dentro del lote (desvío / media). */
    val coeficienteVariacion: Double,
    /** Lote casi parejo: dividirlo en zonas aporta poco. */
    val homogeneo: Boolean,
    /** Parte del lote con valores atípicos (agua, caminos, bordes) que no se usó para los cortes. */
    val atipicosPct: Double = 0.0
)

object Zonificador {
    const val MIN_ZONAS = 2
    const val MAX_ZONAS = 7
    /** Con esta parte de la variación explicada, más zonas ya no se justifican. */
    const val EXPLICADA_SUFICIENTE = 0.85
    /** Si otra zona suma menos que esto, no vale la pena. */
    const val GANANCIA_MINIMA = 0.04
    /** Por debajo de este coeficiente de variación el lote es parejo. */
    const val CV_HOMOGENEO = 0.03
    /**
     * Distancia mínima a los cuartiles para considerar atípico un valor (en unidades del índice o del
     * potencial relativo): en un lote muy parejo el criterio de Tukey solo marcaría las colas normales.
     */
    const val ATIPICO_MINIMO = 0.10

    private class Celdas(val grilla: MarcoCeldas, val valores: DoubleArray, val dentro: BooleanArray, val areaCeldaHa: Double)

    /**
     * Las celdas de la zonificación: cuadradas de [ConfigZonificacion.celdaM] al norte, o de máquina
     * (pasadas del ancho de labor, alineadas al rumbo) si hay [ConfigZonificacion.anchoLaborM].
     */
    fun marcoPara(anillo: List<Pair<Double, Double>>, config: ConfigZonificacion): MarcoCeldas {
        val ancho = config.anchoLaborM ?: return grillaDeCeldas(anillo, config.celdaM)
        return MarcoMaquina.para(anillo, ancho, config.celdaM, config.rumboGrados ?: 0.0)
    }

    private fun prepararCeldas(imagen: ImagenIndices, indice: IndiceVegetacion, lote: List<Pair<Double, Double>>, config: ConfigZonificacion): Celdas {
        val celdaM = config.celdaM
        val anillo = GeometriaLote.anilloAbierto(lote)
        if (anillo.size < 3) throw ErrorZonificacion("El lote no tiene un polígono válido.")
        val grilla = marcoPara(anillo, config)
        val dentro = BooleanArray(grilla.total) { i ->
            val (lat, lon) = grilla.centro(i)
            GeometriaLote.dentroDePoligono(lat, lon, anillo)
        }
        if (dentro.none { it }) throw ErrorZonificacion("El lote es más chico que una celda de ${celdaM.toInt()} m.")
        val valores = remuestrear(imagen, indice, grilla, dentro)
        if (valores.indices.none { dentro[it] && !valores[it].isNaN() }) {
            throw ErrorZonificacion("La imagen no tiene píxeles sin nubes dentro del lote: elige otra fecha.")
        }
        rellenarHuecos(valores, dentro, grilla)
        val areaCeldaHa = (grilla as? MarcoMaquina)?.areaCeldaHa ?: (celdaM * celdaM / 10_000.0)
        return Celdas(grilla, valores, dentro, areaCeldaHa)
    }

    /**
     * Los valores sin los atípicos (fuera de Q1 − d … Q3 + d, con d = 1,5·RIC como en el criterio de
     * Tukey pero nunca menor a [ATIPICO_MINIMO]). Un
     * bajo con agua, un camino o una esquina con otro cultivo tienen valores tan distintos que, si
     * entran al cálculo, los cortes separan "agua" de "todo lo demás" en lugar de las zonas
     * productivas. Esos píxeles igual se asignan a la zona más cercana.
     */
    fun nucleo(ordenados: List<Double>): List<Double> {
        if (ordenados.size < 20) return ordenados
        fun q(p: Double) = ordenados[(p * (ordenados.size - 1)).toInt()]
        val q1 = q(0.25)
        val q3 = q(0.75)
        val ric = q3 - q1
        val d = maxOf(1.5 * ric, ATIPICO_MINIMO)
        val sinAtipicos = ordenados.filter { it >= q1 - d && it <= q3 + d }
        return if (sinAtipicos.size >= ordenados.size / 2) sinAtipicos else ordenados
    }

    fun recomendarZonas(imagen: ImagenIndices, indice: IndiceVegetacion, lote: List<Pair<Double, Double>>, celdaM: Double): RecomendacionZonas =
        recomendarZonas(imagen, indice, lote, ConfigZonificacion(celdaM = celdaM))

    /** Varianza explicada por k-means con 2 a [MAX_ZONAS] zonas y la cantidad recomendada. */
    fun recomendarZonas(imagen: ImagenIndices, indice: IndiceVegetacion, lote: List<Pair<Double, Double>>, config: ConfigZonificacion): RecomendacionZonas {
        val c = prepararCeldas(imagen, indice, lote, config)
        val todos = c.valores.indices.filter { c.dentro[it] }.map { c.valores[it] }.sorted()
        val enLote = nucleo(todos)
        val atipicosPct = 100.0 * (todos.size - enLote.size) / todos.size
        val media = enLote.average()
        val total = enLote.sumOf { (it - media) * (it - media) }
        val cv = if (media == 0.0) 0.0 else sqrt(total / enLote.size) / abs(media)
        val explicada = (MIN_ZONAS..MAX_ZONAS).associateWith { k ->
            if (total <= 0.0) 1.0 else {
                val cortes = cortesKMeans(enLote, k)
                val grupos = enLote.groupBy { v -> cortes.count { v > it } }
                val dentroDeGrupos = grupos.values.sumOf { g -> val m = g.average(); g.sumOf { (it - m) * (it - m) } }
                (1 - dentroDeGrupos / total).coerceIn(0.0, 1.0)
            }
        }
        val homogeneo = cv < CV_HOMOGENEO
        return RecomendacionZonas(cantidadRecomendada(explicada, homogeneo), explicada, cv, homogeneo, atipicosPct)
    }

    /**
     * La menor cantidad de zonas que explica [EXPLICADA_SUFICIENTE] de la variación, o después de la
     * cual otra zona suma menos de [GANANCIA_MINIMA]; 2 si el lote es parejo.
     */
    fun cantidadRecomendada(explicada: Map<Int, Double>, homogeneo: Boolean): Int =
        if (homogeneo) MIN_ZONAS else (MIN_ZONAS until MAX_ZONAS).firstOrNull { k ->
            explicada.getValue(k) >= EXPLICADA_SUFICIENTE || explicada.getValue(k + 1) - explicada.getValue(k) < GANANCIA_MINIMA
        } ?: MAX_ZONAS

    fun zonificar(
        imagen: ImagenIndices,
        indice: IndiceVegetacion,
        lote: List<Pair<Double, Double>>,
        config: ConfigZonificacion
    ): Zonificacion {
        require(config.zonas in MIN_ZONAS..MAX_ZONAS) { "Se admiten de $MIN_ZONAS a $MAX_ZONAS zonas" }
        val celdas = prepararCeldas(imagen, indice, lote, config)
        val grilla = celdas.grilla
        val dentro = celdas.dentro
        val valores = celdas.valores

        val etiquetas = clasificar(valores, dentro, config)
        suavizar(etiquetas, grilla)

        val areaCeldaHa = celdas.areaCeldaHa
        var minCeldas = max(1, ceil(config.areaMinimaHa / areaCeldaHa).toInt())
        eliminarParches(etiquetas, grilla, minCeldas)
        val minCeldasPedidas = minCeldas
        val limite = config.maxPoligonos
        if (limite != null) {
            var intentos = 0
            while (componentes(etiquetas, grilla).size > limite && intentos++ < 40) {
                minCeldas = ceil(minCeldas * 1.5).toInt() + 1
                eliminarParches(etiquetas, grilla, minCeldas)
            }
        }
        compactarEtiquetas(etiquetas)

        val zonas = resumir(etiquetas, valores, areaCeldaHa)
        val poligonos = componentes(etiquetas, grilla)
            .sortedWith(compareBy({ it.zona }, { -it.celdas.size }))
            .map { comp -> PoligonoZona(comp.zona, vectorizar(comp.celdas, grilla), comp.celdas.size * areaCeldaHa) }
        // ISO-XML pide una grilla norte-sur: con grilla de máquina se vuelve a muestrear más fino
        val (grillaIso, etiquetasIso) = if (grilla is GrillaGeografica) grilla to etiquetas
            else rasterizar(grilla, etiquetas, GeometriaLote.anilloAbierto(lote))
        return Zonificacion(
            grillaIso, etiquetasIso, zonas, poligonos, areaCeldaHa, minCeldas * areaCeldaHa, minCeldas > minCeldasPedidas,
            marco = grilla,
            anchoLaborM = (grilla as? MarcoMaquina)?.anchoM,
            rumboGrados = (grilla as? MarcoMaquina)?.rumboGrados
        )
    }

    /** Celdas cuadradas de [celdaM] metros alineadas a la esquina noroeste del lote. */
    fun grillaDeCeldas(anillo: List<Pair<Double, Double>>, celdaM: Double): GrillaGeografica {
        val norte = anillo.maxOf { it.first }
        val sur = anillo.minOf { it.first }
        val este = anillo.maxOf { it.second }
        val oeste = anillo.minOf { it.second }
        val lat = radianes((norte + sur) / 2)
        val metrosLat = 111132.92 - 559.82 * cos(2 * lat) + 1.175 * cos(4 * lat)
        val metrosLon = 111412.84 * cos(lat) - 93.5 * cos(3 * lat)
        val pasoLat = celdaM / metrosLat
        val pasoLon = celdaM / metrosLon
        val columnas = max(1, ceil((este - oeste) / pasoLon - 1e-9).toInt())
        val filas = max(1, ceil((norte - sur) / pasoLat - 1e-9).toInt())
        return GrillaGeografica(oeste, norte, pasoLon, pasoLat, columnas, filas)
    }

    /**
     * Zonas de la grilla de máquina sobre una grilla norte-sur de ~5 m (más gruesa en lotes enormes,
     * para no pasar de ~250.000 celdas), tomando la zona de la celda de máquina que cubre cada centro.
     */
    private fun rasterizar(marco: MarcoCeldas, etiquetas: IntArray, anillo: List<Pair<Double, Double>>): Pair<GrillaGeografica, IntArray> {
        val areaM2 = GeometriaLote.areaHectareas(anillo) * 10_000
        val resolucion = maxOf(5.0, kotlin.math.sqrt(areaM2 / 250_000.0))
        val g = grillaDeCeldas(anillo, resolucion)
        val salida = IntArray(g.total) { i ->
            val (lat, lon) = g.centro(i)
            if (!GeometriaLote.dentroDePoligono(lat, lon, anillo)) 0 else marco.celdaDe(lat, lon)?.let { etiquetas[it] } ?: 0
        }
        return g to salida
    }

    private fun remuestrear(imagen: ImagenIndices, indice: IndiceVegetacion, grilla: MarcoCeldas, dentro: BooleanArray): DoubleArray {
        val suma = DoubleArray(grilla.total)
        val cuenta = IntArray(grilla.total)
        val origen = imagen.grilla
        val v = imagen.valores(indice)
        for (p in 0 until origen.total) {
            val valor = v[p]
            if (valor.isNaN()) continue
            val (lat, lon) = origen.centro(p)
            val i = grilla.celdaDe(lat, lon) ?: continue
            if (!dentro[i]) continue
            suma[i] += valor
            cuenta[i]++
        }
        return DoubleArray(grilla.total) { if (dentro[it] && cuenta[it] > 0) suma[it] / cuenta[it] else Double.NaN }
    }

    /** Celdas del lote sin dato (nubes pequeñas, bordes) toman el promedio de sus vecinas. */
    private fun rellenarHuecos(valores: DoubleArray, dentro: BooleanArray, g: MarcoCeldas) {
        for (pasada in 0 until g.columnas + g.filas) {
            val faltan = valores.indices.filter { dentro[it] && valores[it].isNaN() }
            if (faltan.isEmpty()) return
            val nuevos = faltan.associateWith { i ->
                vecinos8(i, g).map { valores[it] }.filter { !it.isNaN() }.takeIf { it.isNotEmpty() }?.average()
            }
            var cambio = false
            nuevos.forEach { (i, v) -> if (v != null) { valores[i] = v; cambio = true } }
            if (!cambio) break
        }
        val promedio = valores.filter { !it.isNaN() }.average()
        valores.indices.forEach { if (dentro[it] && valores[it].isNaN()) valores[it] = promedio }
    }

    private fun clasificar(valores: DoubleArray, dentro: BooleanArray, config: ConfigZonificacion): IntArray {
        // Los cortes salen del núcleo del lote: los atípicos no los corren (igual se clasifican)
        val enLote = nucleo(valores.indices.filter { dentro[it] }.map { valores[it] }.sorted())
        val n = config.zonas
        val cortes: List<Double> = when (config.metodo) {
            MetodoZonificacion.CUANTILES -> (1 until n).map { k -> enLote[(k * enLote.size / n).coerceAtMost(enLote.lastIndex)] }
            MetodoZonificacion.KMEANS -> cortesKMeans(enLote, n)
        }
        return IntArray(valores.size) { i -> if (!dentro[i]) 0 else 1 + cortes.count { valores[i] > it } }
    }

    /** k-means en una dimensión, iniciado en los cuantiles; devuelve los puntos medios entre centros. */
    fun cortesKMeans(ordenados: List<Double>, n: Int): List<Double> {
        var centros = (0 until n).map { k -> ordenados[((2 * k + 1) * ordenados.size / (2 * n)).coerceAtMost(ordenados.lastIndex)] }
        for (iteracion in 0 until 60) {
            val grupos = List(n) { mutableListOf<Double>() }
            ordenados.forEach { v -> grupos[centros.indices.minBy { abs(centros[it] - v) }] += v }
            val nuevos = centros.indices.map { k -> grupos[k].takeIf { it.isNotEmpty() }?.average() ?: centros[k] }.sorted()
            if (nuevos == centros) break
            centros = nuevos
        }
        return (0 until n - 1).map { (centros[it] + centros[it + 1]) / 2 }
    }

    /** Filtro de mayoría 3×3: quita celdas sueltas sin mover los bordes del lote. */
    private fun suavizar(etiquetas: IntArray, g: MarcoCeldas) {
        val original = etiquetas.copyOf()
        for (i in etiquetas.indices) {
            if (original[i] == 0) continue
            val conteo = HashMap<Int, Int>()
            (vecinos8(i, g) + i).forEach { j -> if (original[j] != 0) conteo.sumarUno(original[j]) }
            val maximo = conteo.values.max()
            if ((conteo[original[i]] ?: 0) < maximo) etiquetas[i] = conteo.filterValues { it == maximo }.keys.min()
        }
    }

    class Componente(val zona: Int, val celdas: List<Int>)

    /** Componentes conexos (4 vecinos) de cada zona. */
    fun componentes(etiquetas: IntArray, g: MarcoCeldas): List<Componente> {
        val visto = BooleanArray(etiquetas.size)
        val resultado = mutableListOf<Componente>()
        for (inicio in etiquetas.indices) {
            if (visto[inicio] || etiquetas[inicio] == 0) continue
            val zona = etiquetas[inicio]
            val celdas = mutableListOf<Int>()
            val pila = ArrayDeque<Int>().apply { add(inicio) }
            visto[inicio] = true
            while (pila.isNotEmpty()) {
                val i = pila.removeLast()
                celdas += i
                vecinos4(i, g).forEach { j ->
                    if (!visto[j] && etiquetas[j] == zona) {
                        visto[j] = true
                        pila.add(j)
                    }
                }
            }
            resultado += Componente(zona, celdas)
        }
        return resultado
    }

    /** Parches menores a [minCeldas] pasan a la zona vecina con la que comparten más borde. */
    private fun eliminarParches(etiquetas: IntArray, g: MarcoCeldas, minCeldas: Int) {
        repeat(200) {
            val chicos = componentes(etiquetas, g).filter { it.celdas.size < minCeldas }.sortedBy { it.celdas.size }
            var cambio = false
            for (comp in chicos) {
                // Se reevalúa con las etiquetas actuales: un parche vecino pudo haberse fusionado antes
                val zonaActual = etiquetas[comp.celdas.first()]
                if (comp.celdas.any { etiquetas[it] != zonaActual }) continue
                val borde = HashMap<Int, Int>()
                comp.celdas.forEach { i ->
                    vecinos4(i, g).forEach { j ->
                        val z = etiquetas[j]
                        if (z != 0 && z != zonaActual) borde.sumarUno(z)
                    }
                }
                val destino = borde.maxByOrNull { it.value }?.key ?: continue
                comp.celdas.forEach { etiquetas[it] = destino }
                cambio = true
            }
            if (!cambio) return
        }
    }

    private fun compactarEtiquetas(etiquetas: IntArray) {
        val presentes = etiquetas.filter { it != 0 }.distinct().sorted()
        val nueva = presentes.withIndex().associate { (i, z) -> z to i + 1 }
        for (i in etiquetas.indices) if (etiquetas[i] != 0) etiquetas[i] = nueva.getValue(etiquetas[i])
    }

    private fun resumir(etiquetas: IntArray, valores: DoubleArray, areaCeldaHa: Double): List<ResumenZona> =
        etiquetas.indices.filter { etiquetas[it] != 0 }.groupBy { etiquetas[it] }.entries.sortedBy { it.key }.map { (zona, celdas) ->
            val v = celdas.map { valores[it] }
            ResumenZona(zona, celdas.size, celdas.size * areaCeldaHa, v.average(), v.min(), v.max())
        }

    /** Conteo por clave (MutableMap.merge es de la JVM). */
    private fun MutableMap<Int, Int>.sumarUno(clave: Int) {
        this[clave] = (this[clave] ?: 0) + 1
    }

    private fun vecinos4(i: Int, g: MarcoCeldas): List<Int> {
        val f = i / g.columnas
        val c = i % g.columnas
        return buildList(4) {
            if (f > 0) add(i - g.columnas)
            if (f < g.filas - 1) add(i + g.columnas)
            if (c > 0) add(i - 1)
            if (c < g.columnas - 1) add(i + 1)
        }
    }

    private fun vecinos8(i: Int, g: MarcoCeldas): List<Int> {
        val f = i / g.columnas
        val c = i % g.columnas
        return buildList(8) {
            for (df in -1..1) for (dc in -1..1) {
                if (df == 0 && dc == 0) continue
                val ff = f + df
                val cc = c + dc
                if (ff in 0 until g.filas && cc in 0 until g.columnas) add(ff * g.columnas + cc)
            }
        }
    }

    // --- Vectorización ---

    private class Arista(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        var usada = false
        val dx get() = x1 - x0
        val dy get() = y1 - y0
    }

    /**
     * Contorno de un conjunto de celdas siguiendo sus bordes. En coordenadas de grilla (y hacia el sur)
     * cada celda se recorre en sentido horario, así que el exterior queda horario y los huecos
     * antihorarios, que es lo que pide el formato shapefile. En los vértices donde dos celdas se tocan
     * en diagonal se gira a la derecha, para que cada anillo sea simple.
     */
    fun vectorizar(celdas: List<Int>, g: MarcoCeldas): List<List<Pair<Double, Double>>> {
        val miembro = BooleanArray(g.total).apply { celdas.forEach { this[it] = true } }
        val ancho = g.columnas + 1
        val salientes = HashMap<Int, MutableList<Arista>>()
        fun agregar(a: Arista) = salientes.getOrPut(a.y0 * ancho + a.x0) { mutableListOf() }.add(a)
        for (i in celdas) {
            val f = i / g.columnas
            val c = i % g.columnas
            if (f == 0 || !miembro[i - g.columnas]) agregar(Arista(c, f, c + 1, f))
            if (c == g.columnas - 1 || !miembro[i + 1]) agregar(Arista(c + 1, f, c + 1, f + 1))
            if (f == g.filas - 1 || !miembro[i + g.columnas]) agregar(Arista(c + 1, f + 1, c, f + 1))
            if (c == 0 || !miembro[i - 1]) agregar(Arista(c, f + 1, c, f))
        }

        val anillos = mutableListOf<List<Pair<Int, Int>>>()
        for (inicio in salientes.values.flatten()) {
            if (inicio.usada) continue
            val puntos = mutableListOf(inicio.x0 to inicio.y0)
            var actual = inicio
            actual.usada = true
            while (!(actual.x1 == inicio.x0 && actual.y1 == inicio.y0)) {
                puntos += actual.x1 to actual.y1
                val opciones = salientes[actual.y1 * ancho + actual.x1].orEmpty().filter { !it.usada }
                val derecha = -actual.dy to actual.dx
                val recto = actual.dx to actual.dy
                val siguiente = opciones.firstOrNull { (it.dx to it.dy) == derecha }
                    ?: opciones.firstOrNull { (it.dx to it.dy) == recto }
                    ?: opciones.firstOrNull()
                    ?: break
                siguiente.usada = true
                actual = siguiente
            }
            anillos += quitarColineales(puntos)
        }

        fun areaGrilla(r: List<Pair<Int, Int>>): Long =
            r.indices.sumOf { k -> val (x0, y0) = r[k]; val (x1, y1) = r[(k + 1) % r.size]; x0.toLong() * y1 - x1.toLong() * y0 }

        val exteriores = anillos.filter { areaGrilla(it) > 0 }.sortedByDescending { areaGrilla(it) }
        val huecos = anillos.filter { areaGrilla(it) < 0 }
        return (exteriores + huecos).map { r ->
            val geo = r.map { (x, y) -> g.vertice(x, y) }
            geo + geo.first()
        }
    }

    private fun quitarColineales(r: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (r.size < 4) return r
        return r.filterIndexed { k, p ->
            val a = r[(k - 1 + r.size) % r.size]
            val b = r[(k + 1) % r.size]
            (p.first - a.first).toLong() * (b.second - p.second) - (p.second - a.second).toLong() * (b.first - p.first) != 0L
        }
    }
}
