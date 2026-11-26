package com.example.utils

/**
 * Polígono que se está dibujando (vértices (lat, lon) WGS84) con sus medidas ya calculadas.
 * [revision] sube con cada cambio confirmado: es lo que dispara el reenvío de vértices a la página
 * del mapa (los arrastres solo actualizan la superficie provisoria, sin redibujar).
 */
data class EstadoEdicionPoligono(
    val vertices: List<Pair<Double, Double>> = emptyList(),
    val seleccionado: Int? = null,
    val revision: Int = 0,
    val areaHa: Double = 0.0,
    val perimetroM: Double = 0.0,
    val autointerseccion: Boolean = false,
    val puedeDeshacer: Boolean = false,
    val puedeRehacer: Boolean = false
) {
    val puedeGuardar: Boolean get() = vertices.size >= 3 && !autointerseccion

    /** Tocar el primer vértice con el polígono completo (y ninguno elegido) lo cierra: se pasa a guardar. */
    fun cierraAlTocar(indice: Int): Boolean = indice == 0 && seleccionado == null && puedeGuardar
}

/**
 * Edición de un lote con deshacer / rehacer, la misma lógica que SateliteViewModel en Android
 * (pura, para la web y los tests). Las coordenadas fuera de rango o no finitas se ignoran.
 */
class EditorPoligono(inicial: List<Pair<Double, Double>> = emptyList()) {
    private val pilaDeshacer = ArrayDeque<List<Pair<Double, Double>>>()
    private val pilaRehacer = ArrayDeque<List<Pair<Double, Double>>>()

    var estado: EstadoEdicionPoligono = calcular(GeometriaLote.anilloAbierto(inicial), null, 0)
        private set

    fun agregar(lat: Double, lon: Double): EstadoEdicionPoligono {
        if (!valida(lat, lon)) return estado
        return aplicar(estado.vertices + (lat to lon), seleccion = null)
    }

    fun mover(indice: Int, lat: Double, lon: Double): EstadoEdicionPoligono {
        if (indice !in estado.vertices.indices || !valida(lat, lon)) return estado
        return aplicar(estado.vertices.toMutableList().also { it[indice] = lat to lon }, seleccion = indice)
    }

    /** Inserta un vértice en [indice] (el que estaba en esa posición pasa a la siguiente). */
    fun insertar(indice: Int, lat: Double, lon: Double): EstadoEdicionPoligono {
        if (indice !in 0..estado.vertices.size || !valida(lat, lon)) return estado
        return aplicar(estado.vertices.toMutableList().also { it.add(indice, lat to lon) }, seleccion = indice)
    }

    fun eliminarSeleccionado(): EstadoEdicionPoligono {
        val i = estado.seleccionado ?: return estado
        if (i !in estado.vertices.indices) return estado
        return aplicar(estado.vertices.toMutableList().also { it.removeAt(i) }, seleccion = null)
    }

    /** Tocar un vértice lo elige; tocar el ya elegido lo suelta. No entra en el historial. */
    fun seleccionar(indice: Int): EstadoEdicionPoligono {
        if (indice !in estado.vertices.indices) return estado
        estado = estado.copy(seleccionado = if (estado.seleccionado == indice) null else indice, revision = estado.revision + 1)
        return estado
    }

    fun limpiar(): EstadoEdicionPoligono =
        if (estado.vertices.isEmpty()) estado else aplicar(emptyList(), seleccion = null)

    fun deshacer(): EstadoEdicionPoligono {
        val anterior = pilaDeshacer.removeLastOrNull() ?: return estado
        pilaRehacer.addLast(estado.vertices)
        estado = calcular(anterior, null, estado.revision + 1)
        return estado
    }

    fun rehacer(): EstadoEdicionPoligono {
        val siguiente = pilaRehacer.removeLastOrNull() ?: return estado
        pilaDeshacer.addLast(estado.vertices)
        estado = calcular(siguiente, null, estado.revision + 1)
        return estado
    }

    /** Superficie mientras se arrastra un vértice ([insercion] = un punto intermedio); null si no aplica. */
    fun areaProvisoria(indice: Int, lat: Double, lon: Double, insercion: Boolean): Double? {
        if (!valida(lat, lon)) return null
        val temporal = estado.vertices.toMutableList()
        when {
            insercion && indice in 0..temporal.size -> temporal.add(indice, lat to lon)
            !insercion && indice in temporal.indices -> temporal[indice] = lat to lon
            else -> return null
        }
        return GeometriaLote.areaHectareas(temporal)
    }

    private fun aplicar(nuevos: List<Pair<Double, Double>>, seleccion: Int?): EstadoEdicionPoligono {
        pilaDeshacer.addLast(estado.vertices)
        if (pilaDeshacer.size > MAX_HISTORIAL) pilaDeshacer.removeFirst()
        pilaRehacer.clear()
        estado = calcular(nuevos, seleccion, estado.revision + 1)
        return estado
    }

    private fun calcular(vertices: List<Pair<Double, Double>>, seleccion: Int?, revision: Int) = EstadoEdicionPoligono(
        vertices = vertices,
        seleccionado = seleccion?.takeIf { it in vertices.indices },
        revision = revision,
        areaHa = GeometriaLote.areaHectareas(vertices),
        perimetroM = GeometriaLote.perimetroMetros(vertices),
        autointerseccion = GeometriaLote.tieneAutointerseccion(vertices),
        puedeDeshacer = pilaDeshacer.isNotEmpty(),
        puedeRehacer = pilaRehacer.isNotEmpty()
    )

    private fun valida(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    companion object {
        const val MAX_HISTORIAL = 100
    }
}
