package com.example.web.viewmodel

import com.example.data.remote.ktor.ServicioLocalidades
import com.example.ui.viewmodel.SugerenciasState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Sugerencias de localidades mientras se escribe, con las mismas reglas que Clima y Satélite en
 * Android: 350 ms de pausa, desde 3 letras, y solo cuenta la última consulta (flatMapLatest).
 * [cerca] da el punto que ordena por proximidad (la ciudad o el centro del mapa en pantalla).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BuscadorLocalidades(
    private val servicio: ServicioLocalidades,
    scope: CoroutineScope,
    private val cerca: () -> Pair<Double, Double>?
) {
    val texto = MutableStateFlow("")

    val sugerencias: StateFlow<SugerenciasState> = texto
        .debounce(350)
        .flatMapLatest { escrito ->
            if (escrito.trim().length < 3) flowOf(SugerenciasState())
            else flow {
                emit(SugerenciasState(cargando = true))
                emit(
                    try {
                        val items = servicio.sugerir(escrito, cerca())
                        if (items.isEmpty()) SugerenciasState(error = "No se encontraron localidades con ese nombre")
                        else SugerenciasState(items = items)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        SugerenciasState(error = "Sin conexión para sugerir localidades")
                    }
                )
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SugerenciasState())

    fun limpiar() {
        texto.value = ""
    }
}
