package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.agenda.CatalogoProductos
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.IconoProducto
import com.example.ui.screens.IconoProductoAgenda
import com.example.ui.screens.vectorProducto
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Los íconos del menú de productos de la Agenda (todas las categorías), para revisarlos a la vista. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2400dp-xhdpi", sdk = [36])
class IconosAgendaScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun todosLosIconosSeDibujan() {
        // Ninguno falla al armarse (un trazo mal escrito tiraría una excepción)
        IconoProducto.entries.forEach { vectorProducto(it) }
    }

    @Test
    fun menuDeCadaCategoria() {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        compose.setContent {
            GRANEXTheme {
                Column(
                    Modifier.width(412.dp).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(CategoriaGasto.LABOR, CategoriaGasto.SEMILLA, CategoriaGasto.FITOSANITARIO, CategoriaGasto.COMBUSTIBLE).forEach { c ->
                        Text(c.nombre, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                        CatalogoProductos.porCategoria.getValue(c).forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoProductoAgenda(p.icono, c)
                                Text(p.nombre, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/agenda_iconos_menu.png")
    }

    /** Las máquinas de las labores en grande, para revisar el dibujo. */
    @Test
    fun maquinasEnGrande() {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        val maquinas = CatalogoProductos.porCategoria.getValue(CategoriaGasto.LABOR).distinctBy { it.icono }
        compose.setContent {
            GRANEXTheme {
                Column(
                    Modifier.width(412.dp).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    maquinas.chunked(3).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            fila.forEach { p ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(116.dp)) {
                                    androidx.compose.material3.Icon(
                                        vectorProducto(p.icono), contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color(0xFF6D28D9),
                                        modifier = Modifier.padding(4.dp).width(96.dp).height(96.dp)
                                    )
                                    Text(p.nombre, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/agenda_iconos_maquinas.png")
    }
}
