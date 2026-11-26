package com.example.data.remote.model

// Interpretación de datos de Open-Meteo, común a Android y la web.

/** Códigos WMO de Open-Meteo: 51-67 llovizna/lluvia, 80-82 chubascos, 95-99 tormenta. */
fun esCodigoLluvia(codigo: Int): Boolean = codigo in 51..67 || codigo in 80..82 || codigo >= 95

fun describirCodigoClima(codigo: Int): String = when (codigo) {
    0 -> "Despejado"
    1 -> "Mayormente despejado"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    51, 53, 55 -> "Llovizna"
    56, 57 -> "Llovizna helada"
    61 -> "Lluvia débil"
    63 -> "Lluvia"
    65 -> "Lluvia intensa"
    66, 67 -> "Lluvia helada"
    71, 73, 75, 77 -> "Nieve"
    80, 81 -> "Chubascos"
    82 -> "Chubascos fuertes"
    85, 86 -> "Chubascos de nieve"
    95 -> "Tormenta"
    96, 99 -> "Tormenta con granizo"
    else -> "Sin datos"
}

/** Categoría del índice UV según la escala de la OMS, con recomendación para personas y pulverización. */
enum class NivelUv(val etiqueta: String, val desde: Double, val proteccion: String, val pulverizacion: String) {
    BAJO("Bajo", 0.0, "Sin protección especial.", "Sin restricciones por radiación."),
    MODERADO("Moderado", 3.0, "Usar gorra y protector solar al mediodía.", "Preferir aplicar temprano o al atardecer."),
    ALTO("Alto", 6.0, "Protección obligatoria: ropa larga, protector y anteojos.", "Precaución en pulverización: más evaporación y degradación de algunos activos."),
    MUY_ALTO("Muy alto", 8.0, "Evitar la exposición directa entre las 11 y las 16 h.", "Evitar pulverizar al mediodía; aplicar temprano o al atardecer."),
    EXTREMO("Extremo", 11.0, "Evitar la exposición directa; trabajar a la sombra.", "No pulverizar en horas centrales del día.");

    companion object {
        fun de(uv: Double): NivelUv = entries.last { uv >= it.desde }
    }
}

/**
 * Riesgo de incendio de rastrojos y pastizales con temperatura (°C), humedad (%) y viento (km/h):
 * la misma regla que WeatherRepository.calcularRiesgoFuego en Android ("Bajo", "Moderado", "Alto" o "Extremo").
 */
fun riesgoIncendio(temperatura: Double, humedad: Double, vientoKmH: Double): String = when {
    temperatura > 30.0 && humedad < 30 && vientoKmH > 25.0 -> "Extremo"
    temperatura > 25.0 && humedad < 45 && vientoKmH > 15.0 -> "Alto"
    temperatura > 20.0 && humedad < 60 -> "Moderado"
    else -> "Bajo"
}
