package com.example

import com.example.data.repository.ClasificadorNoticias
import com.example.data.repository.RubroFuente
import com.example.data.repository.TemaNoticia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El clasificador en :shared (lo usa la web): mismo resultado sin java.text.Normalizer. */
class ClasificadorNoticiasComunTest {

    @Test
    fun normalizaTildesYMayusculas() {
        assertEquals("cosechadora nina maiz pinguino", ClasificadorNoticias.normalizar("Cosechadora NIÑA Maíz Pingüino"))
    }

    @Test
    fun clasificaComoEnAndroid() {
        val temas = ClasificadorNoticias.temas("Suben los fertilizantes y el dólar", "La urea y el BCRA", emptyList(), RubroFuente.AGRO)
        assertEquals(setOf(TemaNoticia.ECONOMIA_FINANZAS, TemaNoticia.INSUMOS_MERCADOS), temas)
        assertEquals(ClasificadorNoticias.CATEGORIA_MAQUINARIA, ClasificadorNoticias.categoria("Nueva cosechadora autónoma", "", RubroFuente.AGRO))
        assertEquals(ClasificadorNoticias.CATEGORIA_CLIMA, ClasificadorNoticias.categoria("Alerta por heladas en el sur", "", RubroFuente.AGRO))
        // Inicio de palabra: "dron" no está en "padrón"
        assertTrue(TemaNoticia.MAQUINARIA_TECNOLOGIA !in ClasificadorNoticias.temas("El padrón de productores", "", emptyList(), RubroFuente.AGRO))
    }
}
