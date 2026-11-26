package com.example.data.repository

import com.russhwolf.settings.Settings

data class LastSelectedCity(
    val name: String,
    val lat: Double,
    val lon: Double
)

/**
 * Última localidad elegida en Clima y zoom con que se mostró cada localidad. Común a Android
 * (SharedPreferences "granex_user_prefs", a través de [UserPreferencesRepository]) y la web
 * (localStorage), con multiplatform-settings. Las claves son las que ya usaba la app Android.
 */
class PreferenciasUsuario(private val settings: Settings) {

    fun guardarUltimaCiudad(nombre: String, lat: Double, lon: Double) {
        settings.putString(CLAVE_NOMBRE, nombre)
        settings.putFloat(CLAVE_LAT, lat.toFloat())
        settings.putFloat(CLAVE_LON, lon.toFloat())
    }

    fun ultimaCiudad(): LastSelectedCity? {
        val nombre = settings.getStringOrNull(CLAVE_NOMBRE) ?: return null
        return LastSelectedCity(
            name = nombre,
            lat = settings.getFloat(CLAVE_LAT, -32.9468f).toDouble(),
            lon = settings.getFloat(CLAVE_LON, -60.6393f).toDouble()
        )
    }

    fun guardarZoomLocalidad(nombre: String, zoom: Int) = settings.putInt(claveZoom(nombre), zoom)

    fun zoomLocalidad(nombre: String): Int? = settings.getIntOrNull(claveZoom(nombre))

    private fun claveZoom(nombre: String) = PREFIJO_ZOOM_LOCALIDAD + nombre.trim().lowercase()

    companion object {
        const val ARCHIVO_ANDROID = "granex_user_prefs"
        private const val CLAVE_NOMBRE = "last_city_name"
        private const val CLAVE_LAT = "last_city_lat"
        private const val CLAVE_LON = "last_city_lon"
        private const val PREFIJO_ZOOM_LOCALIDAD = "zoom_localidad_"
    }
}
