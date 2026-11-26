package com.example.data.remote.api

/**
 * Acceso centralizado a las claves inyectadas en BuildConfig desde `.env`.
 * Devuelve null si la clave falta o es un valor de ejemplo, para que el llamador
 * use datos de respaldo en lugar de enviar una clave inválida.
 */
object ApiKeys {

    val maptiler: String?
        get() = real(com.example.BuildConfig.MAPTILER_API_KEY)

    /**
     * Proyecto Supabase (backend de cuentas y sincronización de lotes); null sin configurar. La clave
     * es la pública: los datos los protegen las políticas RLS (docs/backend/SUPABASE.md).
     */
    val supabase: com.example.data.remote.supabase.ConfiguracionSupabase?
        get() = com.example.data.remote.supabase.ConfiguracionSupabase.desde(
            com.example.BuildConfig.SUPABASE_URL,
            com.example.BuildConfig.SUPABASE_ANON_KEY
        )

    // Las credenciales de usuario (Copernicus) nunca van en BuildConfig: cualquier
    // valor compilado se extrae del APK. Se cargan en Ajustes y se guardan cifradas.

    private fun real(value: String?): String? {
        val key = value?.trim()
        if (key.isNullOrEmpty()) return null
        // Valores de ejemplo usados en .env.example y en plantillas de AI Studio
        if (key.startsWith("TU_") || key.startsWith("YOUR_") || key.startsWith("MY_") ||
            key.endsWith("_FALLBACK_DEMO")
        ) return null
        return key
    }
}
