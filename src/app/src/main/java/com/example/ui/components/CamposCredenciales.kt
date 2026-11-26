package com.example.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.AgroGreenPrimary

/**
 * Campo para contraseñas y claves: oculto por defecto, con un botón de ojo para mostrarlo.
 * El estado "visible" usa remember (no rememberSaveable) a propósito: tras rotar o volver a la
 * pantalla el secreto queda oculto otra vez.
 *
 * @param error mensaje debajo del campo (lo marca en rojo); null = sin error.
 * @param alConfirmar acción de la tecla Done/Go del teclado.
 */
@Composable
fun CampoSecreto(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    error: String? = null,
    alConfirmar: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        // Teclado de contraseña: sin corrección, sin sugerencias ni aprendizaje de lo que se escribe
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = alConfirmar?.let { accion -> { accion() } }),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier.testTag("${testTag ?: "campo_secreto"}_ver")
            ) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Ocultar $etiqueta" else "Mostrar $etiqueta"
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AgroGreenPrimary,
            focusedLabelColor = AgroGreenPrimary
        ),
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    )
}

/** Campo para usuarios e IDs de cliente: sin autocorrección ni mayúscula inicial. */
@Composable
fun CampoIdentificador(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AgroGreenPrimary,
            focusedLabelColor = AgroGreenPrimary
        ),
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    )
}

/** Campo de correo: teclado de email, sin autocorrección ni mayúscula inicial. */
@Composable
fun CampoCorreo(
    valor: String,
    onValorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String = "Correo electrónico",
    error: String? = null,
    testTag: String? = null
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AgroGreenPrimary,
            focusedLabelColor = AgroGreenPrimary
        ),
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    )
}
