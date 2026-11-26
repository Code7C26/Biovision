package com.example.utils

/**
 * Solo https con dominio y sin credenciales embebidas: lo que se puede abrir en otra pestaña sin
 * riesgo (mismo criterio que urlNavegable en Android). Descarta javascript:, data:, http:,
 * "https://usuario@sitio" (engaña sobre el destino real) y hosts sin punto ("https://localhost").
 */
fun esUrlExternaSegura(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    val autoridad = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (autoridad.isEmpty() || '@' in autoridad || autoridad.any { it.isWhitespace() || it == '\\' }) return false
    val host = autoridad.substringBefore(':')
    return '.' in host && !host.startsWith('.') && !host.endsWith('.') &&
        host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}
