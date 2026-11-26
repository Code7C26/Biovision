package com.example

import com.example.data.remote.ktor.CierreA3
import com.example.utils.FuturosA3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuturosA3Test {

    private fun cierre(fecha: String, simbolo: String, ajuste: Double?, variacion: Double? = 1.0, volumen: Double = 10.0) =
        CierreA3("${fecha}T00:00:00.000Z", simbolo, settlement = ajuste, changePercent = variacion, volume = volumen, openInterest = 100.0)

    @Test
    fun tomaLaUltimaRuedaYLasTresPosicionesMasCercanas() {
        val cierres = listOf(
            cierre("2026-11-06", "SOJ.ROS/NOV26", 349.3),
            cierre("2026-11-11", "SOJ.ROS/JUL27", 355.0),
            cierre("2026-11-11", "SOJ.ROS/NOV26", 356.0, variacion = 1.92),
            cierre("2026-11-11", "SOJ.ROS/MAY27", 345.5),
            cierre("2026-11-11", "SOJ.ROS/ENE27", 361.3),
            cierre("2026-11-11", "SOJ.VISEC/NOV26", 366.0),
            cierre("2026-11-11", "SOJ.ROS/NOV26M", 356.0)
        )
        val contratos = FuturosA3.vigentes(cierres)
        assertEquals(listOf("SOJ.ROS/NOV26", "SOJ.ROS/ENE27", "SOJ.ROS/MAY27"), contratos.map { it.simbolo })
        val nov = contratos.first()
        assertEquals("2026-11-11", nov.fecha)
        assertEquals(356.0, nov.ajusteUsd)
        assertEquals(1.92, nov.variacionPct)
        assertEquals("Noviembre 2026", nov.posicion)
        assertEquals("Soja", nov.cultivo)
    }

    @Test
    fun ordenaPorCultivoYDescartaSinAjuste() {
        val cierres = listOf(
            cierre("2026-11-11", "TRI.ROS/DIC26", 196.0),
            cierre("2026-11-11", "MAI.ROS/ABR27", 183.1),
            cierre("2026-11-11", "MAI.ROS/DIC26", null),
            cierre("2026-11-11", "SOJ.ROS/NOV26", 356.0)
        )
        val contratos = FuturosA3.vigentes(cierres)
        assertEquals(listOf("Soja", "Maíz", "Trigo"), contratos.map { it.cultivo })
        assertEquals(listOf("SOJ.ROS/NOV26", "MAI.ROS/ABR27", "TRI.ROS/DIC26"), contratos.map { it.simbolo })
    }

    @Test
    fun sinCierresNoHayContratos() {
        assertTrue(FuturosA3.vigentes(emptyList()).isEmpty())
    }
}
