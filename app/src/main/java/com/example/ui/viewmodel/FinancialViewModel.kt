package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.model.FinancialDashboardData
import com.example.data.repository.FinancialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FinancialUiState {
    object Loading : FinancialUiState
    data class Success(val data: FinancialDashboardData) : FinancialUiState
    data class Error(val message: String) : FinancialUiState
}

class FinancialViewModel(
    private val repository: FinancialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FinancialUiState>(FinancialUiState.Loading)
    val uiState: StateFlow<FinancialUiState> = _uiState.asStateFlow()

    fun cargarDatosFinancieros(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value !is FinancialUiState.Success) {
                _uiState.value = FinancialUiState.Loading
            }
            repository.getFinancialAndRofexData(forceRefresh)
                .onSuccess { data ->
                    _uiState.value = FinancialUiState.Success(data)
                }
                .onFailure { error ->
                    val currentState = _uiState.value
                    if (currentState !is FinancialUiState.Success) {
                        _uiState.value = FinancialUiState.Error(error.localizedMessage ?: "Error al cargar cotizaciones")
                    }
                }
        }
    }
}
