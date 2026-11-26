package com.example.ui.viewmodel

import com.example.data.repository.SugerenciaLocalidad

/** Lista desplegable de los buscadores de localidades (Clima y Satélite). Vacía (sin [cargando] ni [error]) oculta el menú. */
data class SugerenciasState(
    val items: List<SugerenciaLocalidad> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null
) {
    val visible: Boolean get() = items.isNotEmpty() || cargando || error != null
}
