package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.data.repository.ClasificadorNoticias
import com.example.data.repository.FUENTES_NOTICIAS
import com.example.data.repository.FuenteNoticias
import com.example.data.repository.LectorRss
import com.example.data.repository.NewsArticle
import com.example.data.repository.RubroFuente
import com.example.data.repository.TemaNoticia
import com.example.ui.components.urlNavegable
import com.example.ui.screens.FiltrosTema
import com.example.ui.screens.NovedadAgroCard
import com.example.ui.theme.GRANEXTheme
import com.example.ui.viewmodel.NovedadesViewModel
import com.example.ui.viewmodel.ResumenNovedades
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h1200dp-xhdpi", sdk = [36])
class NoticiasTest {

    @get:Rule val compose = createComposeRule()

    private fun fuente(dominio: String, rubro: RubroFuente = RubroFuente.AGRO) =
        FuenteNoticias("Medio", "https://www.$dominio/rss", dominio, rubro)

    // --- Lector RSS (fragmentos reales de cada formato) ---

    @Test
    fun imagenDesdeEnclosureMediaContentOHtml() {
        val clarin = """<rss><channel><item><title><![CDATA[Polémica en Buenos Aires por los caminos rurales]]></title>
            <link>https://www.clarin.com/rural/buenos-aires-ley-caminos_0_abc.html</link>
            <description><![CDATA[<ul><li>La ley permite usar la tasa vial</li></ul>]]></description>
            <pubDate>Wed, 21 Oct 2026 12:06:41 +0000</pubDate>
            <enclosure url="https://www.clarin.com/img/2026/10/06/foto_1200x630.jpg" length="0" type="image/jpg"/></item></channel></rss>"""
        val c = LectorRss.leer(clarin, fuente("clarin.com")).single()
        assertEquals("https://www.clarin.com/img/2026/10/06/foto_1200x630.jpg", c.imagen)
        assertEquals("La ley permite usar la tasa vial", c.descripcion)
        assertEquals(1792584401000L, c.timestamp)

        val agrofy = """<rss><channel><item><title>Quieren usar fondos de caminos rurales para otras obras</title>
            <link>https://news.agrofy.com.ar/noticia/216837/quieren-usar-fondos</link>
            <category>Actualidad</category><pubDate>Wed, 21 Oct 2026 14:33:00 GMT</pubDate>
            <media:content url="https://news.agrofystatic.com/portadas/obra.jpg" medium="image" type="image/jpeg" /></item></channel></rss>"""
        val a = LectorRss.leer(agrofy, fuente("agrofy.com.ar")).single()
        assertEquals("https://news.agrofystatic.com/portadas/obra.jpg", a.imagen)
        assertEquals(listOf("Actualidad"), a.categorias)

        // WordPress (Bichos de Campo): la foto solo está dentro del HTML de content:encoded
        val bichos = """<rss><channel><item><title>Los biológicos no van a reemplazar a los agroquímicos</title>
            <link>https://bichosdecampo.com/los-biologicos-no-van/</link>
            <description><![CDATA[Daniel Germinara explicó…]]></description>
            <content:encoded><![CDATA[<p><img src="https://bichosdecampo.com/wp-content/uploads/2026/10/bioinsumos.jpg" /></p><p>Texto</p>]]></content:encoded>
            <pubDate>Wed, 21 Oct 2026 14:44:55 +0000</pubDate></item></channel></rss>"""
        assertEquals(
            "https://bichosdecampo.com/wp-content/uploads/2026/10/bioinsumos.jpg",
            LectorRss.leer(bichos, fuente("bichosdecampo.com")).single().imagen
        )

        // La Voz publica la imagen por http: se pide por https (la app bloquea HTTP en claro)
        val lavoz = """<rss><channel><item><title>De prestar servicios a vender drones en Río Cuarto</title>
            <link>https://www.lavoz.com.ar/agro/drones_0_x.html</link>
            <enclosure url="http://www.lavoz.com.ar/images/2026/10/20/foto.jpg" length="0" type="image/jpg"/>
            <pubDate><![CDATA[Wed, 21 Oct 2026 14:08:00 GMT]]></pubDate></item></channel></rss>"""
        val v = LectorRss.leer(lavoz, fuente("lavoz.com.ar")).single()
        assertEquals("https://www.lavoz.com.ar/images/2026/10/20/foto.jpg", v.imagen)
        assertTrue(v.timestamp > 0)
    }

    @Test
    fun descartaEnlacesAjenosPublicidadYTitulosVacios() {
        val xml = """<rss><channel>
            <item><title>Nota válida del medio sobre la cosecha</title><link>https://www.infocampo.com.ar/nota-valida/</link></item>
            <item><title>Redirección a otro sitio con premios</title><link>https://bit.ly/premio-gratis</link></item>
            <item><title>Contenido patrocinado: el mejor seguro agrícola</title><link>https://www.infocampo.com.ar/seguro/</link></item>
            <item><title>Nota de marca</title><link>https://www.infocampo.com.ar/brandstudio/nota/</link></item>
            <item><title></title><link>https://www.infocampo.com.ar/sin-titulo/</link></item>
            <item><title>Enlace con esquema peligroso</title><link>javascript:alert(1)</link></item>
        </channel></rss>"""
        val items = LectorRss.leer(xml, fuente("infocampo.com.ar"))
        assertEquals(listOf("https://www.infocampo.com.ar/nota-valida/"), items.map { it.enlace })
        assertNull(LectorRss.enlaceSeguro("https://infocampo.com.ar.evil.com/x", "infocampo.com.ar"))
        assertEquals("https://news.agrofy.com.ar/n/1", LectorRss.enlaceSeguro("https://news.agrofy.com.ar/n/1", "agrofy.com.ar"))
    }

    @Test
    fun todasLasFuentesSonHttpsYDeSuDominio() {
        assertEquals(8, FUENTES_NOTICIAS.size)
        FUENTES_NOTICIAS.forEach { f ->
            assertTrue(f.url, f.url.startsWith("https://") && java.net.URI(f.url).host.endsWith(f.dominio))
        }
        assertEquals(setOf("El Cronista"),
            FUENTES_NOTICIAS.filter { it.rubro == RubroFuente.ECONOMIA }.map { it.nombre }.toSet())
    }

    // --- Clasificación ---

    @Test
    fun clasificaPorTemaYCategoria() {
        fun temas(t: String, rubro: RubroFuente = RubroFuente.AGRO) = ClasificadorNoticias.temas(t, "", emptyList(), rubro)
        assertEquals(setOf(TemaNoticia.MAQUINARIA_TECNOLOGIA), temas("John Deere lanzó una cosechadora con piloto automático"))
        assertEquals(setOf(TemaNoticia.INSUMOS_MERCADOS), temas("Campaña 2026/27: los números de la fertilización cierran"))
        assertEquals(setOf(TemaNoticia.ECONOMIA_FINANZAS), temas("Dólar blue hoy: a cuánto cotiza"))
        assertTrue(TemaNoticia.ECONOMIA_FINANZAS in temas("Yerba mate: lanzan un programa", RubroFuente.ECONOMIA))
        // Coincidencia por inicio de palabra: "padrón" no es "dron", "participación" no es "IPC"
        assertEquals(emptySet<TemaNoticia>(), temas("Actualizan el padrón de participación de productores"))

        assertEquals(ClasificadorNoticias.CATEGORIA_MAQUINARIA, ClasificadorNoticias.categoria("Llega una nueva generación de camiones", "", RubroFuente.AGRO))
        assertEquals(ClasificadorNoticias.CATEGORIA_CLIMA, ClasificadorNoticias.categoria("Alerta por heladas tardías en el sur", "", RubroFuente.AGRO))
        assertEquals(ClasificadorNoticias.CATEGORIA_ECONOMIA, ClasificadorNoticias.categoria("Privatización del Belgrano Cargas", "", RubroFuente.ECONOMIA))
        assertEquals(ClasificadorNoticias.CATEGORIA_AGRICULTURA, ClasificadorNoticias.categoria("Avanza la siembra de maíz", "", RubroFuente.AGRO))
    }

    private fun nota(id: String, titulo: String, temas: Set<TemaNoticia>, ts: Long, imagen: String? = null) = NewsArticle(
        id = "https://www.clarin.com/rural/$id", title = titulo, description = "", sourceName = "Clarín Rural",
        articleUrl = "https://www.clarin.com/rural/$id", category = ClasificadorNoticias.categoria(titulo, "", RubroFuente.AGRO),
        formattedTime = "Hace 2 horas", timestamp = ts, imageUrl = imagen, temas = temas
    )

    @Test
    fun filtrarPorTemaYTextoConservaElOrden() {
        val lista = listOf(
            nota("a", "Nueva sembradora neumática", setOf(TemaNoticia.MAQUINARIA_TECNOLOGIA), 3),
            nota("b", "Suben los fertilizantes y el dólar", setOf(TemaNoticia.INSUMOS_MERCADOS, TemaNoticia.ECONOMIA_FINANZAS), 2),
            nota("c", "Récord de exportaciones", setOf(TemaNoticia.ECONOMIA_FINANZAS), 1)
        )
        assertEquals(listOf("a", "b", "c"), NovedadesViewModel.filtrar(lista, null, "").map { it.id.substringAfterLast('/') })
        assertEquals(listOf("b", "c"), NovedadesViewModel.filtrar(lista, TemaNoticia.ECONOMIA_FINANZAS, "").map { it.id.substringAfterLast('/') })
        assertEquals(listOf("b"), NovedadesViewModel.filtrar(lista, TemaNoticia.ECONOMIA_FINANZAS, "DOLAR").map { it.id.substringAfterLast('/') })
    }

    // --- Apertura segura ---

    @Test
    fun soloSeAbrenUrlsHttpsConHost() {
        assertEquals("https://www.ambito.com/economia/nota", urlNavegable(" https://www.ambito.com/economia/nota "))
        assertNull(urlNavegable(null))
        assertNull(urlNavegable(""))
        assertNull(urlNavegable("http://www.ambito.com/x"))
        assertNull(urlNavegable("javascript:alert(1)"))
        assertNull(urlNavegable("intent://x#Intent;end"))
        assertNull(urlNavegable("https://"))
        assertNull(urlNavegable("https://exa mple.com/%%"))
    }

    // --- Interfaz ---

    @Test
    fun tocarLaTarjetaAbreSuNotaYLosChipsFiltran() {
        val abiertas = mutableListOf<String>()
        var tema: TemaNoticia? = TemaNoticia.ECONOMIA_FINANZAS
        val sinImagen = nota("tractor", "Lanzan un tractor eléctrico para tambos", setOf(TemaNoticia.MAQUINARIA_TECNOLOGIA), 1)
        compose.setContent {
            GRANEXTheme {
                var seleccionado by remember { mutableStateOf<TemaNoticia?>(null) }
                Column(Modifier.width(412.dp)) {
                    FiltrosTema(
                        seleccionado = seleccionado,
                        resumen = ResumenNovedades(listOf("Clarín Rural"), emptyList(), 42, mapOf(
                            TemaNoticia.ECONOMIA_FINANZAS to 18, TemaNoticia.MAQUINARIA_TECNOLOGIA to 7, TemaNoticia.INSUMOS_MERCADOS to 11
                        )),
                        onSeleccionar = { seleccionado = it; tema = it }
                    )
                    Column(Modifier.padding(16.dp)) {
                        NovedadAgroCard(novedad = sinImagen, onAbrir = { abiertas += it.articleUrl })
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/novedades_tarjeta_respaldo.png")

        compose.onNodeWithTag("card_novedad_${sinImagen.id}").assert(hasClickAction()).performClick()
        assertEquals(listOf("https://www.clarin.com/rural/tractor"), abiertas)

        compose.onNodeWithTag("chip_tema_maquinaria_tecnologia").performClick()
        compose.onNodeWithTag("chip_tema_maquinaria_tecnologia").assertIsSelected()
        assertEquals(TemaNoticia.MAQUINARIA_TECNOLOGIA, tema)
        compose.onNodeWithTag("chip_tema_todas").performClick()
        assertNull(tema)
    }
}
