package com.example.ui.viewmodel

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.GranexApplication
import com.example.data.remote.supabase.PerfilRemoto
import com.example.data.remote.supabase.RepositorioPerfil
import com.example.data.sync.SincronizacionLotesWorker
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** La Activity detrás del contexto de Compose (MainActivity envuelve el contexto para la escala). */
private fun Context.actividad(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

/**
 * Cuenta de la Activity: la usan el control de acceso, Ajustes y el diálogo de perfil. Desde una
 * pantalla del NavHost, `viewModel {}` daría otra instancia (la de esa pantalla): por eso se pide
 * siempre con la Activity como dueña.
 */
@Composable
fun cuentaViewModelDeLaApp(): CuentaViewModel {
    val contexto = LocalContext.current
    val app = contexto.applicationContext as GranexApplication
    val actividad = requireNotNull(contexto.actividad()) { "Sin Activity" }
    return viewModel(viewModelStoreOwner = actividad) {
        val container = app.container
        CuentaViewModel(
            container.cuenta,
            container.sincronizadorLotes,
            container.agroDao.contarLotesPendientes(),
            programarReintento = { SincronizacionLotesWorker.programar(app) },
            borrarDatosLocales = {
                withContext(Dispatchers.IO) {
                    container.almacenAgronomia.vaciar()
                    container.almacenAgenda.vaciar()
                    WeatherNotificationService.borrarCredencialesTerceros(app)
                    // Imágenes de campañas y de la última detección: son de los lotes de esa cuenta
                    java.io.File(app.cacheDir, "campanas").deleteRecursively()
                    java.io.File(app.cacheDir, "deteccion.bin").delete()
                }
            },
            antesDeCerrarSesion = {
                // Suelo, rinde, dosis y gastos de la Agenda sin subir se perderían al borrarlos del teléfono
                val agronomia = container.sincronizadorAgronomia
                val agronomiaPendiente = if (agronomia == null || container.almacenAgronomia.pendientes().isEmpty()) false
                else {
                    agronomia.sincronizar()
                    container.almacenAgronomia.pendientes().isNotEmpty()
                }
                val agenda = container.sincronizadorAgenda
                container.almacenAgenda.cargar()
                if (agenda != null && container.almacenAgenda.pendiente.value) runCatching { agenda.sincronizar() }
                val agendaPendiente = agenda != null && container.almacenAgenda.pendiente.value
                when {
                    agronomiaPendiente -> "hay análisis de suelo o mapas de rinde sin subir. Verificá tu conexión a internet e intentá de nuevo."
                    agendaPendiente -> "hay gastos de la Agenda sin subir. Verificá tu conexión a internet e intentá de nuevo."
                    else -> null
                }
            }
        )
    }
}

@Composable
fun perfilViewModelDeLaApp(): PerfilViewModel {
    val contexto = LocalContext.current
    val app = contexto.applicationContext as GranexApplication
    val actividad = requireNotNull(contexto.actividad()) { "Sin Activity" }
    val cuenta = cuentaViewModelDeLaApp()
    return viewModel(viewModelStoreOwner = actividad) {
        val remoto = app.container.supabase?.let { cliente ->
            val repositorio = RepositorioPerfil(cliente)
            object : PerfilesRemotos {
                override suspend fun miPerfil(): PerfilRemoto? = repositorio.miPerfil()
                override suspend fun guardar(perfil: PerfilRemoto) = repositorio.guardar(perfil)
            }
        }
        PerfilViewModel(app.container.cuenta, remoto, cuenta.estado, app)
    }
}
