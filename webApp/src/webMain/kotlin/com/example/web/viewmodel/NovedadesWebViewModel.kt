package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ktor.NotaPublica
import com.example.data.remote.ktor.ServicioNoticiasGranex
import com.example.data.repository.ClasificadorNoticias
import com.example.data.repository.RubroFuente
import com.example.data.repository.TemaNoticia
import com.example.web.ui.horaActual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Nota con la categoría y los temas calculados igual que en Android (ClasificadorNoticias, en :shared). */
data class NotaWeb(val nota: NotaPublica, val categoria: String, val temas: Set<TemaNoticia>)

sealed interface EstadoNoticias {
    data object Cargando : EstadoNoticias
    /** Build sin backend: los RSS no se pueden leer desde el navegador. */
    data object SinServidor : EstadoNoticias
    data class Ok(val notas: List<NotaWeb>, val fallidas: List<String>, val hora: String) : EstadoNoticias
    data class Error(val mensaje: String) : EstadoNoticias
}

/**
 * Novedades de la web: las notas que la función "publico" ya leyó y filtró (mismas reglas que
 * LectorRss). Nunca hay notas de ejemplo: sin servidor o sin respuesta, la pantalla lo dice.
 */
class NovedadesWebViewModel(private val servicio: ServicioNoticiasGranex?) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoNoticias>(if (servicio == null) EstadoNoticias.SinServidor else EstadoNoticias.Cargando)
    val estado: StateFlow<EstadoNoticias> = _estado.asStateFlow()

    /** Filtro de tema, como los chips de Android; null = todas. */
    private val _tema = MutableStateFlow<TemaNoticia?>(null)
    val tema: StateFlow<TemaNoticia?> = _tema.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        val s = servicio ?: return
        viewModelScope.launch {
            if (_estado.value !is EstadoNoticias.Ok) _estado.value = EstadoNoticias.Cargando
            _estado.value = try {
                val r = conReintentos { s.noticias() }
                EstadoNoticias.Ok(r.notas.map(::clasificar), r.fallidas, horaActual())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _estado.value as? EstadoNoticias.Ok
                    ?: EstadoNoticias.Error("No se pudieron leer las noticias. Revisá la conexión y probá de nuevo.")
            }
        }
    }

    fun elegirTema(tema: TemaNoticia?) {
        _tema.value = tema
    }

    private fun clasificar(n: NotaPublica): NotaWeb {
        val rubro = if (n.rubro == "ECONOMIA") RubroFuente.ECONOMIA else RubroFuente.AGRO
        return NotaWeb(
            nota = n,
            categoria = ClasificadorNoticias.categoria(n.titulo, n.resumen, rubro),
            temas = ClasificadorNoticias.temas(n.titulo, n.resumen, n.categorias, rubro)
        )
    }
}
