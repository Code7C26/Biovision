package com.example.data.remote.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/*
 * Clientes HTTP comunes (Ktor). En la web el motor es fetch (ktor-client-js): solo se puede llamar a
 * APIs que respondan con CORS. Las que no (RSS, futuros de A3 desde la web, Copernicus)
 * necesitan un backend propio: ver docs/web/MIGRACION_KMP.md.
 */

internal val JsonGranex = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Cliente para las APIs públicas. Cada plataforma aporta el motor (fetch en el navegador). */
fun crearClienteHttp(): HttpClient = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) { json(JsonGranex) }
    // En el navegador solo aplica el tiempo total del pedido
    install(HttpTimeout) { requestTimeoutMillis = 20_000 }
}
