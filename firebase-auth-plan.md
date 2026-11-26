# Plan Detallado: Firebase Authentication en GRANEX

## Fase 1: Configuración de Firebase

### 1.1 Dependencias
**Archivo:** `gradle/libs.versions.toml`
- Agregar versión: `firebase = "33.2.0"` (BOM)
- Agregar versión: `firebaseAuth = "23.1.0"`

**Archivo:** `app/build.gradle.kts`
```kotlin
dependencies {
    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:${libs.versions.firebase.get()}"))
    implementation("com.google.firebase:firebase-auth-ktx")
}
```

### 1.2 Configuración de Firebase
1. Ir a [Firebase Console](https://console.firebase.google.com)
2. Crear proyecto (o usar existente): "GRANEX Agtech"
3. Registrar app Android:
   - Package name: `com.aistudio.granex.agtech`
   - SHA-1: Obtener con `gradle :app:signingReport` o Android Studio
   - Descargar `google-services.json` → `app/`
4. En Firebase Console → Authentication → Enable Email/Password provider

### 1.3 Gradle Plugin
**Archivo:** `app/build.gradle.kts`
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2"
}
```

---

## Fase 2: Backend de Autenticación

### 2.1 Modelo de Datos
**Archivo:** `data/remote/model/AuthModels.kt` (nuevo)

```kotlin
// Representa el usuario autenticado
data class UsuarioAutenticado(
    val uid: String,              // ID único de Firebase
    val email: String,
    val displayName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// Estados posibles en AuthViewModel
sealed interface AuthUiState {
    object Loading : AuthUiState
    data class Authenticated(val usuario: UsuarioAutenticado) : AuthUiState
    object Unauthenticated : AuthUiState
    data class Error(val mensaje: String) : AuthUiState
}

// Respuesta de login/signup
sealed class AuthResult {
    data class Success(val usuario: UsuarioAutenticado) : AuthResult()
    data class Failure(val error: String) : AuthResult()
}
```

### 2.2 Repository de Autenticación
**Archivo:** `data/repository/AuthRepository.kt` (nuevo)

```kotlin
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    // Verificar si hay sesión activa
    fun obtenerUsuarioActual(): UsuarioAutenticado? {
        return firebaseAuth.currentUser?.let { user ->
            UsuarioAutenticado(
                uid = user.uid,
                email = user.email ?: "",
                displayName = user.displayName
            )
        }
    }
    
    // Crear cuenta con email/contraseña
    suspend fun registrarse(
        email: String,
        contrasena: String,
        nombreUsuario: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val task = firebaseAuth.createUserWithEmailAndPassword(email, contrasena)
                .await()
            
            val user = task.user ?: return@withContext 
                AuthResult.Failure("No se pudo crear el usuario")
            
            // Actualizar nombre en el perfil
            user.updateProfile(
                userProfileChangeRequest {
                    displayName = nombreUsuario
                }
            ).await()
            
            AuthResult.Success(
                UsuarioAutenticado(user.uid, email, nombreUsuario)
            )
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Failure("El email ya está registrado")
        } catch (e: FirebaseAuthWeakPasswordException) {
            AuthResult.Failure("La contraseña debe tener al menos 6 caracteres")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Failure("Email inválido")
        } catch (e: Exception) {
            AuthResult.Failure("Error: ${e.message}")
        }
    }
    
    // Iniciar sesión
    suspend fun iniciarSesion(
        email: String,
        contrasena: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val task = firebaseAuth.signInWithEmailAndPassword(email, contrasena)
                .await()
            
            val user = task.user ?: return@withContext 
                AuthResult.Failure("No se pudo iniciar sesión")
            
            AuthResult.Success(
                UsuarioAutenticado(user.uid, user.email ?: "", user.displayName)
            )
        } catch (e: FirebaseAuthInvalidUserException) {
            AuthResult.Failure("Usuario no encontrado")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Failure("Email o contraseña incorrectos")
        } catch (e: Exception) {
            AuthResult.Failure("Error: ${e.message}")
        }
    }
    
    // Cerrar sesión
    fun cerrarSesion() {
        firebaseAuth.signOut()
    }
    
    // Observar cambios de autenticación
    fun obtenerEstadoAuth(): Flow<UsuarioAutenticado?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(
                auth.currentUser?.let { user ->
                    UsuarioAutenticado(
                        uid = user.uid,
                        email = user.email ?: "",
                        displayName = user.displayName
                    )
                }
            )
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }
}
```

### 2.3 Agregar AuthRepository a AppContainer
**Archivo:** `GranexApplication.kt` (modificar)

```kotlin
class GranexApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}

class AppContainer(context: Context) {
    // ... repositorios existentes ...
    
    val authRepository: AuthRepository by lazy {
        AuthRepository()
    }
}
```

---

## Fase 3: ViewModel de Autenticación

### 3.1 AuthViewModel
**Archivo:** `ui/viewmodel/AuthViewModel.kt` (nuevo)

```kotlin
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    // Estado actual de autenticación
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()
    
    // Campos del formulario
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()
    
    private val _contrasena = MutableStateFlow("")
    val contrasena: StateFlow<String> = _contrasena.asStateFlow()
    
    private val _nombreUsuario = MutableStateFlow("")
    val nombreUsuario: StateFlow<String> = _nombreUsuario.asStateFlow()
    
    // Modo: "login" o "registro"
    private val _modo = MutableStateFlow("login")
    val modo: StateFlow<String> = _modo.asStateFlow()
    
    init {
        // Verificar si hay sesión activa al iniciar
        verificarSesionActiva()
    }
    
    private fun verificarSesionActiva() {
        viewModelScope.launch {
            authRepository.obtenerEstadoAuth().collect { usuario ->
                _authState.value = if (usuario != null) {
                    AuthUiState.Authenticated(usuario)
                } else {
                    AuthUiState.Unauthenticated
                }
            }
        }
    }
    
    fun actualizarEmail(nuevoEmail: String) {
        _email.value = nuevoEmail
    }
    
    fun actualizarContrasena(nuevaContrasena: String) {
        _contrasena.value = nuevaContrasena
    }
    
    fun actualizarNombre(nuevoNombre: String) {
        _nombreUsuario.value = nuevoNombre
    }
    
    fun cambiarModo(nuevoModo: String) {
        _modo.value = nuevoModo
        // Limpiar errores al cambiar de modo
        _email.value = ""
        _contrasena.value = ""
        _nombreUsuario.value = ""
    }
    
    fun iniciarSesion() {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            
            val result = authRepository.iniciarSesion(
                email = _email.value.trim(),
                contrasena = _contrasena.value
            )
            
            when (result) {
                is AuthResult.Success -> {
                    _authState.value = AuthUiState.Authenticated(result.usuario)
                    _email.value = ""
                    _contrasena.value = ""
                }
                is AuthResult.Failure -> {
                    _authState.value = AuthUiState.Error(result.error)
                }
            }
        }
    }
    
    fun registrarse() {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            
            val result = authRepository.registrarse(
                email = _email.value.trim(),
                contrasena = _contrasena.value,
                nombreUsuario = _nombreUsuario.value.trim()
            )
            
            when (result) {
                is AuthResult.Success -> {
                    _authState.value = AuthUiState.Authenticated(result.usuario)
                    _email.value = ""
                    _contrasena.value = ""
                    _nombreUsuario.value = ""
                }
                is AuthResult.Failure -> {
                    _authState.value = AuthUiState.Error(result.error)
                }
            }
        }
    }
    
    fun cerrarSesion() {
        authRepository.cerrarSesion()
        _authState.value = AuthUiState.Unauthenticated
    }
    
    fun limpiarError() {
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Unauthenticated
        }
    }
}
```

---

## Fase 4: UI

### 4.1 Pantalla de Login/Registro
**Archivo:** `ui/screens/LoginScreen.kt` (nuevo)

Pantalla con dos pestañas:
- **Iniciar Sesión**: campos email, contraseña, botón login
- **Crear Cuenta**: campos nombre, email, contraseña, botón registro

Estados:
- Loading: mostrar ProgressBar
- Unauthenticated: mostrar formulario
- Authenticated: no debería verse (MainActivity navega al NavHost)
- Error: mostrar banner rojo con mensaje

### 4.2 Modificar MainActivity
**Archivo:** `MainActivity.kt` (modificar)

```kotlin
@Composable
fun GRANEXMainApp() {
    val app = LocalContext.current.applicationContext as GranexApplication
    val container = app.container
    
    val authViewModel = viewModel { 
        AuthViewModel(container.authRepository) 
    }
    
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    
    when (authState) {
        is AuthUiState.Loading -> {
            // Pantalla de splash/loading
            SplashScreen()
        }
        is AuthUiState.Authenticated -> {
            // Mostrar la app normal
            GRANEXApp(container)
        }
        is AuthUiState.Unauthenticated -> {
            // Mostrar login
            LoginScreen(viewModel = authViewModel)
        }
        is AuthUiState.Error -> {
            // Mostrar error y opción de reintentar
            LoginScreen(viewModel = authViewModel)
        }
    }
}

@Composable
fun GRANEXApp(container: AppContainer) {
    // El NavHost original, sin cambios
    // ... código existente ...
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = AgroGreenPrimary
        )
    }
}
```

---

## Fase 5: Integración

### 5.1 Agregar cierre de sesión a SettingsScreen
**Archivo:** `ui/screens/SettingsScreen.kt` (modificar)

- Pasar `authViewModel: AuthViewModel` como parámetro
- Agregar sección "Cuenta" con:
  - Mostrar email del usuario autenticado
  - Botón "Cerrar Sesión"
  - Confirmación antes de cerrar

### 5.2 Actualizar MainActivity para pasar AuthViewModel
**Archivo:** `MainActivity.kt` (modificar)

```kotlin
val authViewModel = viewModel { 
    AuthViewModel(container.authRepository) 
}

// Pasar a SettingsScreen
SettingsScreen(
    onBack = { navController.popBackStack() },
    authViewModel = authViewModel  // Nuevo
)
```

### 5.3 Persistencia de Datos por Usuario (Opcional)
Si quieres aislar datos por usuario:

**Cambios en Room:**
- Agregar `userId: String` a entidades relevantes (favorites, etc.)
- Agregar índice compuesto `(userId, ...)` en DAOs

**En Repositorios:**
- Pasar `userId` actual a queries
- Filtrar resultados por usuario autenticado

---

## Orden de Implementación

1. ✅ **Dependencias** → gradle sync
2. ✅ **Firebase Console** → descargar `google-services.json`
3. ✅ **AuthModels.kt** → definir DTOs
4. ✅ **AuthRepository.kt** → lógica de Firebase
5. ✅ **AppContainer** → agregar authRepository
6. ✅ **AuthViewModel.kt** → manejo de estado
7. ✅ **LoginScreen.kt** → UI
8. ✅ **MainActivity.kt** → integración de rutas
9. ✅ **SettingsScreen.kt** → cierre de sesión
10. ✅ **Pruebas** → crear cuenta, iniciar sesión, cerrar

---

## Notas Importantes

- **Seguridad**: Firebase maneja hashing y almacenamiento seguro
- **Offline**: Firebase cachea el token, la app funciona offline (si ya inició sesión)
- **Email Verification**: Opcional (agregar después si es necesario)
- **Recovery**: Opción "Olvide contraseña" (agregar después)
- **Tests**: Robolectric + Firebase emulator (más adelante)

---

## Archivos Nuevos a Crear

```
app/src/main/java/com/example/
├── data/
│   ├── remote/model/
│   │   └── AuthModels.kt
│   └── repository/
│       └── AuthRepository.kt
├── ui/
│   ├── screens/
│   │   └── LoginScreen.kt
│   └── viewmodel/
│       └── AuthViewModel.kt
├── GranexApplication.kt (modificar)
└── MainActivity.kt (modificar)
```

---

## Archivos Existentes a Modificar

- `gradle/libs.versions.toml` → agregar Firebase
- `app/build.gradle.kts` → agregar dependencias y plugin
- `app/google-services.json` → copiar desde Firebase Console
- `GranexApplication.kt` → agregar authRepository
- `MainActivity.kt` → redirigir a login si no autenticado
- `SettingsScreen.kt` → agregar cierre de sesión
