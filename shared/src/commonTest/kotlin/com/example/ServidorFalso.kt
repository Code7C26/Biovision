package com.example

import com.example.data.remote.supabase.LoteRemoto
import com.example.data.remote.supabase.RemotoLotes
import com.example.data.sync.RegistroLote
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Servidor en memoria con las mismas reglas que supabase/migrations: gana el mayor modificado_en. */
@OptIn(ExperimentalTime::class)
internal class ServidorFalso : RemotoLotes {
    val filas = mutableMapOf<String, LoteRemoto>()
    var reloj = 1_761_400_000L
    var sinRed = false
    var subidas = 0
    /** Como RLS: si no es null, solo se ven las filas de ese usuario. */
    var verSolo: String? = null

    private fun marca() = Instant.fromEpochSeconds(reloj++).toString()

    override suspend fun subir(lotes: List<LoteRemoto>) {
        if (sinRed) throw IllegalStateException("sin red")
        subidas++
        lotes.forEach { nuevo ->
            val viejo = filas[nuevo.id]
            if (viejo == null || nuevo.modificadoEn >= viejo.modificadoEn) filas[nuevo.id] = nuevo.copy(actualizadoEn = marca())
        }
    }

    override suspend fun cambiosDesde(desde: String?): List<LoteRemoto> {
        if (sinRed) throw IllegalStateException("sin red")
        return filas.values
            .filter { verSolo == null || it.usuarioId == verSolo }
            .filter { desde == null || Instant.parse(it.actualizadoEn!!) >= Instant.parse(desde) }
            .sortedBy { it.actualizadoEn }
    }

    /** Edición hecha desde otro dispositivo de la misma cuenta. */
    fun editarDesdeOtroDispositivo(fila: LoteRemoto) {
        filas[fila.id] = fila.copy(actualizadoEn = marca())
    }
}

internal fun lote(id: String, nombre: String, modificado: Long) = RegistroLote(
    id = id, nombre = nombre, provincia = "Córdoba", superficieHa = 10.0,
    geojson = """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[]}}""",
    creadoEn = 1_000, modificadoEn = modificado
)
