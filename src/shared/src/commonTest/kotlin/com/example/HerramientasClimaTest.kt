package com.example

import com.example.data.remote.ktor.DiarioBalanceOpenMeteo
import com.example.data.remote.ktor.HorarioAgroOpenMeteo
import com.example.data.remote.ktor.RespuestaAgroOpenMeteo
import com.example.utils.HerramientasClima
import com.example.utils.NivelHelada
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HerramientasClimaTest {
    private val desfase = -3 * 3600

    // 23/11/2026 00:00 en Argentina
    private val medianoche = 1_795_402_800L

    @Test
    fun armaPulverizacionYHeladaDesdeLasHoras() {
        val n = 72
        val horario = RespuestaAgroOpenMeteo(
            desfase = desfase,
            hourly = HorarioAgroOpenMeteo(
                time = List(n) { medianoche + it * 3600L },
                temperatura = List(n) { if (it == 30) 1.5 else 18.0 },
                humedad = List(n) { 55.0 },
                precipitation = List(n) { 0.0 },
                viento = List(n) { 9.0 },
                rafagas = List(n) { 15.0 },
                esDeDia = List(n) { if ((it % 24) in 7..19) 1 else 0 }
            )
        )
        val r = HerramientasClima.calcular(horario, null, medianoche + 3600)
        val p = assertNotNull(r.pulverizacion, "con horas hay ventana de pulverización")
        assertEquals(medianoche + 3600, p.horas.first().epochS, "empieza en la hora actual")
        val h = assertNotNull(r.helada, "con temperaturas hay pronóstico de helada")
        assertEquals(1.5, h.minimaC, "la mínima es la de la hora 30")
        assertEquals(NivelHelada.RIESGO, h.nivel, "1,5 °C es riesgo, no helada")
        assertNull(r.balance, "sin el pedido del balance no hay balance")
    }

    @Test
    fun armaElBalanceConLosDiasPasadosYProximos() {
        val dias = 37
        val inicio = medianoche - 30 * 86_400L
        val balance = RespuestaAgroOpenMeteo(
            desfase = desfase,
            daily = DiarioBalanceOpenMeteo(
                time = List(dias) { inicio + it * 86_400L },
                lluvia = List(dias) { if (it < 30) 2.0 else 0.0 },
                et0 = List(dias) { 4.0 }
            )
        )
        val b = assertNotNull(HerramientasClima.calcular(null, balance, medianoche + 10 * 3600).balance, "hay balance")
        assertEquals(60.0, b.lluviaPasadaMm, 1e-9, "30 días de 2 mm")
        assertEquals(120.0, b.et0PasadaMm, 1e-9, "30 días de 4 mm")
        assertNull(HerramientasClima.calcular(null, null, medianoche).pulverizacion, "sin horas no hay ventana")
    }
}
