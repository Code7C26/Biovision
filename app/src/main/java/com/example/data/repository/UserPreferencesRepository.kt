package com.example.data.repository

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Envoltorio Android de [PreferenciasUsuario] (código común con la web): lee y escribe fuera del hilo principal. */
class UserPreferencesRepository(private val context: Context) {

    private val preferencias by lazy {
        PreferenciasUsuario(
            SharedPreferencesSettings(context.getSharedPreferences(PreferenciasUsuario.ARCHIVO_ANDROID, Context.MODE_PRIVATE))
        )
    }

    suspend fun saveLastSelectedCity(name: String, lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        preferencias.guardarUltimaCiudad(name, lat, lon)
    }

    suspend fun getLastSelectedCity(): LastSelectedCity? = withContext(Dispatchers.IO) {
        preferencias.ultimaCiudad()
    }

    /** Zoom del mapa de Clima con que se mostró la localidad al buscarla (según su tamaño). */
    suspend fun guardarZoomLocalidad(nombre: String, zoom: Int) = withContext(Dispatchers.IO) {
        preferencias.guardarZoomLocalidad(nombre, zoom)
    }

    suspend fun getZoomLocalidad(nombre: String): Int? = withContext(Dispatchers.IO) {
        preferencias.zoomLocalidad(nombre)
    }
}
