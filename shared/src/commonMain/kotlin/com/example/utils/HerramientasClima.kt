package com.example.utils

import com.example.data.remote.ktor.RespuestaAgroOpenMeteo

/** Las tres herramientas agro del pronóstico; cada una null si no hay datos para calcularla. */
data class HerramientasAgro(
    val pulverizacion: PronosticoPulverizacion?,
    val helada: PronosticoHelada?,
    val balance: BalanceHidrico?
)

/** Respuestas de Open-Meteo (unixtime) → [PronosticoAgro], igual que WeatherRepository en Android. */
object HerramientasClima {

    fun calcular(horario: RespuestaAgroOpenMeteo?, balance: RespuestaAgroOpenMeteo?, ahoraS: Long): HerramientasAgro {
        val horas = horario?.hourly?.let { h ->
            h.time.indices.map { i ->
                HoraPronosticoAgro(
                    epochS = h.time[i],
                    temperaturaC = h.temperatura.getOrNull(i),
                    humedadPct = h.humedad.getOrNull(i),
                    vientoKmh = h.viento.getOrNull(i),
                    rafagaKmh = h.rafagas.getOrNull(i),
                    lluviaHoraPreviaMm = h.precipitation.getOrNull(i),
                    esDeDia = h.esDeDia.getOrNull(i)?.let { it == 1 }
                )
            }
        }.orEmpty()
        val desfase = horario?.desfase ?: 0
        val pulverizacion = if (horas.isEmpty()) null
        else PronosticoAgro.pulverizacion(horas, ahoraS, desfase).takeIf { it.horas.isNotEmpty() }
        val helada = if (horas.isEmpty()) null else PronosticoAgro.helada(horas, ahoraS, desfase)
        val hidrico = balance?.daily?.let { d ->
            val dias = d.time.indices.map { i -> DiaBalance(d.time[i], d.lluvia.getOrNull(i), d.et0.getOrNull(i)) }
            if (dias.isEmpty()) null else PronosticoAgro.balance(dias, ahoraS, balance.desfase)
        }
        return HerramientasAgro(pulverizacion, helada, hidrico)
    }
}
