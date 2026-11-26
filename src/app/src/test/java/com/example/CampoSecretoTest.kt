package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.CampoSecreto
import com.example.ui.theme.GRANEXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CampoSecretoTest {

    @get:Rule val compose = createComposeRule()

    private val oculto = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
    private val visible = SemanticsMatcher.keyNotDefined(SemanticsProperties.Password)

    @Test
    fun arrancaOcultoYElOjoAlternaLaVisibilidad() {
        compose.setContent {
            GRANEXTheme {
                var valor by remember { mutableStateOf("clave-secreta") }
                CampoSecreto(valor = valor, onValorChange = { valor = it }, etiqueta = "Contraseña", testTag = "pass")
            }
        }
        compose.onNodeWithTag("pass").assert(oculto)

        compose.onNodeWithContentDescription("Mostrar Contraseña").performClick()
        compose.onNodeWithTag("pass").assert(visible)

        compose.onNodeWithContentDescription("Ocultar Contraseña").performClick()
        compose.onNodeWithTag("pass").assert(oculto)
    }
}
