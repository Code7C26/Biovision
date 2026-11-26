package com.example

import com.example.utils.esUrlExternaSegura
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Enlaces que la web abre en otra pestaña (Navegador.abrirEnPestana). */
class UrlSeguraTest {

    @Test
    fun aceptaHttpsConDominio() {
        assertTrue(esUrlExternaSegura("https://www.clarin.com/rural/"))
        assertTrue(esUrlExternaSegura("https://open-meteo.com"))
        assertTrue(esUrlExternaSegura("https://www.windy.com/?wind,-32.94682,-60.63932,9"))
        assertTrue(esUrlExternaSegura("https://news.agrofy.com.ar:443/nota#arriba"))
    }

    @Test
    fun rechazaEsquemasPeligrososOInseguros() {
        assertFalse(esUrlExternaSegura("javascript:alert(1)"), "javascript:")
        assertFalse(esUrlExternaSegura("data:text/html,<script>alert(1)</script>"), "data:")
        assertFalse(esUrlExternaSegura("http://www.clarin.com/"), "http sin cifrar")
        assertFalse(esUrlExternaSegura("HTTPS://www.clarin.com/"), "mayúsculas: no se normaliza, se rechaza")
        assertFalse(esUrlExternaSegura(" https://www.clarin.com/"), "espacio inicial")
        assertFalse(esUrlExternaSegura("//www.clarin.com/"), "relativa al protocolo")
    }

    @Test
    fun rechazaHostsEngañososOIncompletos() {
        assertFalse(esUrlExternaSegura("https://"), "sin host")
        assertFalse(esUrlExternaSegura("https://localhost/"), "sin dominio")
        assertFalse(esUrlExternaSegura("https://banco.com@malo.example/"), "credenciales que esconden el destino")
        assertFalse(esUrlExternaSegura("https://www.clarin.com\\@malo.example/"), "barra invertida")
        assertFalse(esUrlExternaSegura("https://mal o.com/"), "espacio en el host")
        assertFalse(esUrlExternaSegura("https://.com/"), "host que empieza con punto")
        assertFalse(esUrlExternaSegura("https://ejemplo.com%2F@malo/"), "caracteres codificados en el host")
    }
}
