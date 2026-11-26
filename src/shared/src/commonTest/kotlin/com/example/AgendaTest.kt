package com.example

import com.example.data.agenda.Agenda
import com.example.data.agenda.Aplicacion
import com.example.data.agenda.CatalogoProductos
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.DatosAgenda
import com.example.data.agenda.EstadoPago
import com.example.data.agenda.Gasto
import com.example.data.agenda.IconoProducto
import com.example.data.agenda.LoteAgenda
import com.example.data.agenda.Moneda
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cálculos y catálogo de la Agenda. */
class AgendaTest {

    private fun gasto(
        id: String, fecha: String = "2026-11-21", categoria: CategoriaGasto = CategoriaGasto.SEMILLA,
        producto: String = "Semilla de soja", cantidad: Double = 40.0, unidad: String = "bolsas",
        precio: Double = 42.0, moneda: Moneda = Moneda.USD, tc: Double = 1200.0,
        lote: String? = "a", pago: EstadoPago = EstadoPago.PAGADO, vence: String? = null
    ) = Gasto(id, fecha, categoria, producto, cantidad, unidad, precio, moneda, tc, lote, lote?.let { "Lote $it" },
        estadoPago = pago, vencimiento = vence)

    private fun cerca(esperado: Double, real: Double) = assertTrue(abs(esperado - real) < 1e-6, "esperado $esperado, fue $real")

    @Test
    fun campanaDeJulioAJunio() {
        assertEquals("2026/27", Agenda.campanaDe("2026-11-21"))
        assertEquals("2026/27", Agenda.campanaDe("2026-07-01"))
        assertEquals("2025/26", Agenda.campanaDe("2026-06-30"))
        assertEquals("2099/00", Agenda.campanaDe("2099-08-01"))
    }

    @Test
    fun losGastosEnPesosSePasanADolaresConElDolarDeEseDia() {
        val g = gasto("1", producto = "Glifosato", cantidad = 120.0, unidad = "L", precio = 4500.0, moneda = Moneda.ARS, tc = 1200.0)
        cerca(540_000.0, g.total)
        cerca(450.0, g.totalUsd)
        cerca(540_000.0, g.totalPesos)
        cerca(1680.0, gasto("2").totalUsd)
        cerca(1680.0 * 1200, gasto("2").totalPesos)
    }

    @Test
    fun resumenConLoQueQuedaAPagar() {
        val r = Agenda.resumen(listOf(
            gasto("1"),
            gasto("2", categoria = CategoriaGasto.LABOR, producto = "Siembra", cantidad = 85.0, unidad = "ha", precio = 40.0,
                pago = EstadoPago.A_PAGAR, vence = "2026-12-05"),
            gasto("3", categoria = CategoriaGasto.FITOSANITARIO, producto = "Glifosato", cantidad = 120.0, unidad = "L",
                precio = 3.75, pago = EstadoPago.A_PAGAR, vence = "2026-11-28")
        ))
        cerca(1680.0 + 3400.0 + 450.0, r.totalUsd)
        cerca(3850.0, r.aPagarUsd)
        assertEquals(2, r.facturasAPagar)
        assertEquals("2026-11-28", r.proximoVencimiento)
        cerca(3400.0, r.porCategoria.getValue(CategoriaGasto.LABOR))
        assertFalse(CategoriaGasto.COMBUSTIBLE in r.porCategoria)
    }

    @Test
    fun stockSumaComprasDelMismoProductoYRestaLoAplicado() {
        val datos = DatosAgenda(
            gastos = listOf(
                gasto("1", cantidad = 80.0, precio = 40.0),
                gasto("2", producto = " semilla de SOJA ", cantidad = 40.0, precio = 46.0),
                // Una labor no va al stock
                gasto("3", categoria = CategoriaGasto.LABOR, producto = "Siembra", unidad = "ha", precio = 40.0)
            ),
            aplicaciones = listOf(Aplicacion("x", "2026-11-25", "Semilla de soja", "bolsas", 110.0, "a", "Lote a"))
        )
        val soja = Agenda.stock(datos).single()
        cerca(120.0, soja.comprado)
        cerca(110.0, soja.aplicado)
        cerca(10.0, soja.restante)
        assertTrue(soja.poco, "queda menos del 15 %")
        cerca((80 * 40.0 + 40 * 46.0) / 120, soja.usdPorUnidad)
    }

    @Test
    fun costoPorLoteYGeneralesPorHectarea() {
        val lotes = listOf(LoteAgenda("a", "La Loma", 62.0), LoteAgenda("b", "El Bajo", 38.0))
        val c = Agenda.costosPorLote(
            listOf(
                gasto("1", lote = "a", cantidad = 1.0, precio = 6200.0),
                gasto("2", lote = "b", cantidad = 1.0, precio = 3800.0),
                gasto("3", lote = null, cantidad = 1.0, precio = 1000.0),
                // De un lote que ya no existe: cuenta como general
                gasto("4", lote = "borrado", cantidad = 1.0, precio = 500.0)
            ),
            lotes
        )
        cerca(100.0, c.lotes.first { it.lote.id == "a" }.usdPorHa)
        cerca(100.0, c.lotes.first { it.lote.id == "b" }.usdPorHa)
        cerca(1500.0, c.generalesUsd)
        cerca(15.0, c.generalesPorHa)
        cerca(115.0, Agenda.usdPorHaConGenerales(c, "a")!!)
        assertNull(Agenda.usdPorHaConGenerales(c, "z"))
    }

    @Test
    fun campanasIncluyenLaActualAunqueNoHayaDatos() {
        val datos = DatosAgenda(gastos = listOf(gasto("1", fecha = "2025-10-01")))
        assertEquals(listOf("2026/27", "2025/26"), Agenda.campanas(datos, "2026-11-21"))
        assertEquals(1, Agenda.deCampana(datos, "2025/26").gastos.size)
        assertTrue(Agenda.deCampana(datos, "2026/27").gastos.isEmpty())
    }

    @Test
    fun numerosConComaOPunto() {
        assertEquals(1.5, Agenda.leerNumero("1,5"))
        assertEquals(1.5, Agenda.leerNumero(" 1.5 "))
        assertNull(Agenda.leerNumero("abc"))
        assertEquals("21 nov", Agenda.fechaCorta("2026-11-21"))
    }

    @Test
    fun elCatalogoTraeLasOpcionesDeLaCategoriaConSuIcono() {
        val semillas = CatalogoProductos.sugerencias(CategoriaGasto.SEMILLA, "")
        assertTrue(semillas.any { it.nombre == "Semilla de soja" && it.icono == IconoProducto.SOJA })
        assertTrue(semillas.none { it.icono == IconoProducto.HERBICIDA }, "solo las de la categoría")
        // Todas las categorías tienen opciones, y cada una su unidad
        CategoriaGasto.entries.forEach { c ->
            val opciones = CatalogoProductos.porCategoria.getValue(c)
            assertTrue(opciones.isNotEmpty(), "sin opciones: $c")
            assertTrue(opciones.all { it.unidad in Agenda.UNIDADES }, "unidad desconocida en $c")
        }
    }

    @Test
    fun buscarIgnoraAcentosYMayusculasYNoRepiteLoEscrito() {
        val r = CatalogoProductos.sugerencias(CategoriaGasto.SEMILLA, "MAIZ")
        assertEquals(listOf("Semilla de maíz"), r.map { it.nombre })
        assertTrue(CatalogoProductos.sugerencias(CategoriaGasto.COMBUSTIBLE, "Gasoil").none { it.nombre == "Gasoil" })
        assertTrue(CatalogoProductos.sugerencias(CategoriaGasto.FITOSANITARIO, "xyz").isEmpty())
    }

    @Test
    fun loYaCargadoApareceArribaConSuIcono() {
        val usados = listOf(
            CategoriaGasto.FITOSANITARIO to "Glifosato premium 66 %",
            CategoriaGasto.FITOSANITARIO to "glifosato", // ya está en el catálogo: no se repite
            CategoriaGasto.SEMILLA to "Soja DM 46i20"   // otra categoría: no aparece
        )
        val r = CatalogoProductos.sugerencias(CategoriaGasto.FITOSANITARIO, "glif", usados)
        assertEquals(listOf("Glifosato premium 66 %", "Glifosato"), r.map { it.nombre })
        assertEquals(IconoProducto.USADO, r.first().icono)
    }

    @Test
    fun cadaLaborTieneSuMaquina() {
        val labores = CatalogoProductos.porCategoria.getValue(CategoriaGasto.LABOR).associate { it.nombre to it.icono }
        assertEquals(IconoProducto.PULVERIZADORA, labores["Pulverización terrestre"])
        assertEquals(IconoProducto.AVION, labores["Pulverización aérea"])
        assertEquals(IconoProducto.COSECHADORA, labores["Cosecha"])
        assertEquals(IconoProducto.EMBOLSADORA, labores["Embolsado (silo bolsa)"])
        assertEquals(IconoProducto.EXTRACTORA, labores["Extracción de silo bolsa"])
        assertEquals(IconoProducto.SEMBRADORA, labores["Siembra"])
        // Cada máquina tiene su dibujo, con trazos que empiezan en un punto (M)
        listOf(
            IconoProducto.PULVERIZADORA, IconoProducto.AVION, IconoProducto.COSECHADORA, IconoProducto.EMBOLSADORA,
            IconoProducto.EXTRACTORA, IconoProducto.SEMBRADORA, IconoProducto.FERTILIZADORA, IconoProducto.RASTRA,
            IconoProducto.ROLO, IconoProducto.CAMION, IconoProducto.MONITOREO
        ).forEach { i ->
            val trazos = com.example.data.agenda.TrazosIconos.de(i)
            assertTrue(!trazos.isNullOrEmpty() && trazos.all { it.trimStart().startsWith("M") }, "sin dibujo: $i")
        }
    }
}
