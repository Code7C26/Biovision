package com.example.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/** Resultado del selector de cuentas de Google. */
sealed interface ResultadoGoogle {
    /** [nonce] es el valor sin cifrar: Supabase lo compara con el hash que va dentro del token. */
    data class Token(val idToken: String, val nonce: String) : ResultadoGoogle
    data object Cancelado : ResultadoGoogle
    data class Error(val mensaje: String) : ResultadoGoogle
}

/**
 * "Continuar con Google" con Credential Manager: el teléfono muestra sus cuentas de Google y devuelve
 * un ID token firmado por Google que Supabase valida (`signInWith(IDToken)`), sin navegador.
 *
 * Necesita el client ID **web** de Google Cloud en `GOOGLE_WEB_CLIENT_ID` (local.properties
 * `google.webClientId` o variable de entorno) y, en Google Cloud, un client ID de Android con el
 * paquete y la huella SHA-1 de la firma (debug y la de Play). Ver docs/backend/SUPABASE.md.
 */
object AccesoGoogle {
    val disponible: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** [contexto] tiene que ser la Activity: el selector se abre sobre ella. */
    suspend fun pedirToken(contexto: Context): ResultadoGoogle {
        if (!disponible) return ResultadoGoogle.Error("El ingreso con Google todavía no está configurado en esta versión.")
        val nonce = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val nonceCifrado = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val opcion = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonceCifrado)
            .build()
        val pedido = GetCredentialRequest.Builder().addCredentialOption(opcion).build()
        return try {
            val credencial = CredentialManager.create(contexto).getCredential(contexto, pedido).credential
            if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                ResultadoGoogle.Token(GoogleIdTokenCredential.createFrom(credencial.data).idToken, nonce)
            } else {
                ResultadoGoogle.Error("Google devolvió una credencial que no se reconoce.")
            }
        } catch (e: GetCredentialCancellationException) {
            ResultadoGoogle.Cancelado
        } catch (e: NoCredentialException) {
            ResultadoGoogle.Error("No hay una cuenta de Google en este teléfono. Agregala en Ajustes de Android o ingresá con tu correo.")
        } catch (e: GetCredentialException) {
            ResultadoGoogle.Error("No se pudo ingresar con Google (${e.type.substringAfterLast('.')}). Probá de nuevo o ingresá con tu correo.")
        }
    }
}
