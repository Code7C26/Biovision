package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ClasificadorNoticias
import com.example.data.repository.NewsArticle
import com.example.data.repository.NewsRepository
import com.example.data.repository.TemaNoticia
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NovedadesUiState {
    object LoadingSkeleton : NovedadesUiState
    data class Success(val listaNovedades: List<NewsArticle>) : NovedadesUiState
    object Empty : NovedadesUiState
    data class Error(val message: String) : NovedadesUiState
}

/** Medios consultados en la última carga y cuántas notas hay por tema (para los chips de filtro). */
data class ResumenNovedades(
    val fuentesOk: List<String> = emptyList(),
    val fuentesConError: List<String> = emptyList(),
    val total: Int = 0,
    val porTema: Map<TemaNoticia, Int> = emptyMap()
)

class NovedadesViewModel(
    private val repository: NewsRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<NovedadesUiState>(NovedadesUiState.LoadingSkeleton)
    val uiState: StateFlow<NovedadesUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** null = "Todas". */
    private val _tema = MutableStateFlow<TemaNoticia?>(null)
    val tema: StateFlow<TemaNoticia?> = _tema.asStateFlow()

    private val _resumen = MutableStateFlow(ResumenNovedades())
    val resumen: StateFlow<ResumenNovedades> = _resumen.asStateFlow()

    private var allArticles: List<NewsArticle> = emptyList()

    init {
        cargarNovedades()
    }

    fun cargarNovedades() {
        viewModelScope.launch(Dispatchers.IO) {
            // Al refrescar se siguen viendo las notas actuales; el esqueleto es solo para la primera carga
            if (allArticles.isEmpty()) _uiState.value = NovedadesUiState.LoadingSkeleton
            try {
                val resultado = repository.obtenerNoticias()
                // Lo que ya se vio en pantalla no debe llegar después como notificación
                WeatherNotificationService.notificarNoticias(context, resultado.articulos, notificar = false)
                allArticles = resultado.articulos
                _resumen.value = ResumenNovedades(
                    fuentesOk = resultado.fuentesOk,
                    fuentesConError = resultado.fuentesConError,
                    total = resultado.articulos.size,
                    porTema = TemaNoticia.entries.associateWith { t -> resultado.articulos.count { t in it.temas } }
                )
                filtrarYActualizar()
            } catch (e: Exception) {
                Log.e("NOVEDADES_ERROR", "Error al cargar noticias", e)
                if (allArticles.isEmpty()) {
                    _uiState.value = NovedadesUiState.Error(e.message ?: "No se pudieron cargar las novedades")
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        filtrarYActualizar()
    }

    fun seleccionarTema(tema: TemaNoticia?) {
        _tema.value = tema
        filtrarYActualizar()
    }

    private fun filtrarYActualizar() {
        val filtrados = filtrar(allArticles, _tema.value, _searchQuery.value)
        _uiState.value = if (filtrados.isEmpty()) NovedadesUiState.Empty else NovedadesUiState.Success(filtrados)
    }

    companion object {
        /** Tema y texto (sin distinguir acentos ni mayúsculas); el orden cronológico se conserva. */
        fun filtrar(articulos: List<NewsArticle>, tema: TemaNoticia?, texto: String): List<NewsArticle> {
            val buscado = ClasificadorNoticias.normalizar(texto.trim())
            return articulos.filter { a ->
                (tema == null || tema in a.temas) &&
                    (buscado.isEmpty() || listOf(a.title, a.description, a.category, a.sourceName)
                        .any { ClasificadorNoticias.normalizar(it).contains(buscado) })
            }
        }
    }
}
