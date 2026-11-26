package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.model.EstadoRofex
import com.example.data.remote.model.FuturoRofexContract
import com.example.ui.components.Grano
import com.example.ui.components.IconoGrano
import com.example.ui.rediseno.TarjetaR
import com.example.ui.rediseno.TileDato
import com.example.ui.rediseno.colorSecundario
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.EstadoPreciosInicio
import java.util.Locale

private val AR_M = Locale("es", "AR")

/** "Mayo 2027" → "may 27". */
private fun posicionCorta(p: String): String {
    val partes = p.trim().split(" ")
    if (partes.size != 2) return p
    return partes[0].take(3).lowercase() + " " + partes[1].takeLast(2)
}

/**
 * Rediseño: la pizarra de hoy de cada grano, con sus tres posiciones de futuros de A3 en la misma
 * tarjeta (antes los futuros estaban en Finanzas y la pizarra en Mercado).
 */
@Composable
fun PreciosDeHoyMercado(precios: EstadoPreciosInicio, rofex: EstadoRofex?, onReintentar: () -> Unit) {
    val futuros: Map<String, List<FuturoRofexContract>> =
        (rofex as? EstadoRofex.Ok)?.contratos.orEmpty().groupBy { it.cultivo.lowercase() }
    val rueda = (rofex as? EstadoRofex.Ok)?.contratos?.mapNotNull { it.fechaAjuste }?.maxOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("precios_hoy_mercado")) {
        val fecha = (precios as? EstadoPreciosInicio.Listo)?.fecha
        Text(
            buildString {
                append("Pizarra Rosario (Cámara Arbitral)")
                fecha?.split("-")?.takeIf { it.size == 3 }?.let { append(" del ${it[2]}/${it[1]}") }
                append(" · futuros: precio de ajuste de A3 Mercados")
                rueda?.split("-")?.takeIf { it.size == 3 }?.let { append(", rueda del ${it[2]}/${it[1]}") }
            },
            fontSize = 12.sp, color = colorSecundario, modifier = Modifier.fillMaxWidth()
        )
        when (precios) {
            is EstadoPreciosInicio.Listo -> precios.precios.forEach { p ->
                val contratos = futuros[p.grano.lowercase()].orEmpty().take(3)
                TarjetaR(padding = 14.dp, espacio = 10.dp, modifier = Modifier.testTag("precio_${p.grano.lowercase()}")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconoGrano(Grano.de(p.grano), tamano = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(p.grano, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            p.arsTn?.let { Text("$ " + String.format(AR_M, "%,.0f", it) + " /t", fontSize = 12.sp, color = colorSecundario) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("US$ ${p.usdTn.toInt()}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            val v = p.variacionPct
                            Text(
                                when {
                                    v == null -> "sin variación"
                                    v > 0.05 -> "▲ " + String.format(AR_M, "%.1f %%", v)
                                    v < -0.05 -> "▼ " + String.format(AR_M, "%.1f %%", -v)
                                    else -> "= sin cambio"
                                },
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = when {
                                    v == null || kotlin.math.abs(v) <= 0.05 -> colorSecundario
                                    v > 0 -> AgroGreenPrimary
                                    else -> Color(0xFFB45309)
                                }
                            )
                        }
                    }
                    if (contratos.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            contratos.forEach { c ->
                                TileDato(
                                    "Futuro ${posicionCorta(c.posVencimiento)}",
                                    "US$ " + String.format(AR_M, "%.1f", c.precioAjusteUSD).removeSuffix(",0"),
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            is EstadoPreciosInicio.Error -> TarjetaR {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(precios.mensaje, fontSize = 13.sp, color = colorSecundario, modifier = Modifier.weight(1f))
                    TextButton(onClick = onReintentar) { Text("Reintentar", color = AgroGreenPrimary) }
                }
            }
            EstadoPreciosInicio.Cargando -> repeat(3) {
                TarjetaR { Row(Modifier.fillMaxWidth().height(48.dp)) {} }
            }
        }
    }
}
