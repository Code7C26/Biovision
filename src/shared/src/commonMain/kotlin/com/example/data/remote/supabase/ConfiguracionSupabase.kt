package com.example.data.remote.supabase

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * URL del proyecto Supabase y su clave PÚBLICA ("anon" o "sb_publishable_…"). Viajan dentro de la
 * app por diseño de Supabase: los datos los protegen las políticas RLS de cada tabla, no la clave.
 *
 * Android las recibe de BuildConfig y la web de un archivo generado al compilar; las dos salen de
 * local.properties o de variables de entorno (gradle/supabase.gradle.kts).
 */
class ConfiguracionSupabase private constructor(val url: String, val clavePublica: String) {

    override fun toString() = "ConfiguracionSupabase(url=$url)" // la clave no va a los logs

    companion object {
        /**
         * Configuración válida o null (sin proyecto configurado, valores de ejemplo, URL sin https o
         * clave secreta). Con null la app sigue funcionando solo con datos locales.
         */
        fun desde(url: String?, clave: String?): ConfiguracionSupabase? {
            val u = url?.trim()?.trimEnd('/').orEmpty()
            val c = clave?.trim().orEmpty()
            if (u.isEmpty() || c.isEmpty()) return null
            if (!u.startsWith("https://") || u.length <= "https://".length) return null
            if (esValorDeEjemplo(u) || esValorDeEjemplo(c)) return null
            if (esClaveSecreta(c)) return null
            return ConfiguracionSupabase(u, c)
        }

        /** La clave service_role (o sb_secret_) saltea RLS: nunca puede llegar a un cliente. */
        fun esClaveSecreta(clave: String): Boolean =
            clave.startsWith("sb_secret_") || rolDeJwt(clave) == "service_role"

        private fun esValorDeEjemplo(valor: String): Boolean {
            val v = valor.uppercase()
            return v.startsWith("TU_") || v.startsWith("YOUR_") || "TU-PROYECTO" in v || "YOUR-PROJECT" in v
        }

        /** Rol que declara una clave JWT de Supabase ("anon", "service_role"); null si no es un JWT. */
        @OptIn(ExperimentalEncodingApi::class)
        internal fun rolDeJwt(clave: String): String? {
            val partes = clave.split('.')
            if (partes.size != 3) return null
            val contenido = try {
                Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(partes[1]).decodeToString()
            } catch (e: IllegalArgumentException) {
                return null
            }
            return Regex("\"role\"\\s*:\\s*\"([^\"]+)\"").find(contenido)?.groupValues?.get(1)
        }
    }
}
