# GRANEX web: migración a Kotlin Multiplatform

Guía para llevar GRANEX al navegador (PWA con Compose Multiplatform, Wasm + JS) reutilizando la
arquitectura de la app Android. Estado: **fase 1 hecha** (estructura, lógica común, PWA instalable,
despliegue). Cómo publicar: [DESPLIEGUE.md](DESPLIEGUE.md).

## 1. Estructura

```
:app     Android (Compose 1.7, Room, Retrofit, WebViews…)  ──depende de──┐
                                                                         ▼
:shared  Kotlin Multiplatform: android + js + wasmJs — lógica pura, Ktor, preferencias
                                                                         ▲
:webApp  Compose Multiplatform 1.10: js + wasmJs → PWA  ──depende de────┘
```

- `:shared` **no tiene Compose a propósito**: Compose Multiplatform subiría la app Android de
  Compose 1.7 (BOM 2024.09) a 1.10. Eso es la fase 2, con su propia verificación visual.
- `:webApp` tiene su UI propia por ahora (tema "Verde Campo" copiado, pantallas nuevas responsivas).
  En la fase 2 la UI pasa a `:shared` y ambas apps comparten pantallas.
- Con AGP 9 un módulo KMP no puede usar `com.android.library`: `:shared` usa
  `com.android.kotlin.multiplatform.library` (`kotlin { android { … } }`).
- Kotlin pasó de 2.2.10 a **2.2.21** (mismo lenguaje 2.2): es el mínimo de Compose Multiplatform 1.10
  y del source set `webMain` (código común a JS y Wasm). Todas las librerías nuevas están compiladas
  con Kotlin ≤ 2.2.21; subir Compose a 1.11+ o Ktor a 3.4+ exige subir Kotlin (y revisar KSP/AGP).

### Qué hay en cada módulo

| Módulo | Contenido |
|---|---|
| `shared/commonMain/.../utils` | `MargenAgricola`, `GeometriaLote`, `EscaneoLote`, `AgregacionPrecios`, `vra/Zonificacion`, `vra/Prescripcion`, `vra/ImagenIndices`, `Comun.kt` (reemplazos de `Math`, `Calendar`, `String.format`) |
| `shared/.../data/repository` | `Localidades.kt` (lector de la geocodificación de Open-Meteo, `TipoLocalidad`), `PreferenciasUsuario` (multiplatform-settings) |
| `shared/.../data/remote` | `ktor/` (cliente Ktor + dólar, pronóstico, pizarra, localidades), `model/ClimaComun.kt` (códigos WMO, UV), `CotizacionHistorica` |
| `shared/commonTest` | Tests de geometría, análisis satelital, agregación de precios y reemplazos JVM; corren en JVM, Node/JS y Node/Wasm |
| `webApp/src/webMain` | App web: `GranexWeb` (navegación adaptable), `pantallas/`, `viewmodel/`, `ui/` |
| `webApp/src/{wasmJsMain,jsMain}` | `Navegador.*.kt`: DOM (iframes, rutas `#/…`, `localStorage`); el DOM tiene tipos distintos en Wasm y JS |
| `webApp/pwa` | Plantilla del service worker y `vercel.json` |

Los archivos movidos conservan el paquete (`com.example.utils`…): la app Android no cambió imports.

## 2. Análisis de compatibilidad

| Pieza Android | ¿Corre en web? | Estrategia | Estado |
|---|---|---|---|
| Lógica pura (márgenes, geometría, escaneo Sentinel-2, VRA, agregación) | Sí, con cambios menores | `Math.toRadians` → `radianes()`, `SimpleDateFormat`/`Calendar` → kotlinx-datetime (`FechasIso`), `String.format` → `formatoDecimal` | ✅ Fase 1 |
| Retrofit + Moshi + OkHttp | No (JVM) | Ktor + kotlinx.serialization en `shared/.../ktor`; motor `fetch` en el navegador | ✅ Web (4 servicios). Android sigue con Retrofit: el motor OkHttp de Ktor 3 trae OkHttp 5 (fase 2) |
| `org.json` | No | `JsonElement` de kotlinx.serialization | ✅ Lector de localidades |
| Jsoup (pizarra CAC, RSS) | No | Backend que entrega JSON (y resuelve CORS) | Fase 3 |
| SharedPreferences | No | multiplatform-settings: `SharedPreferencesSettings` / `StorageSettings` | ✅ `PreferenciasUsuario` (Android ya la usa). `WeatherNotificationService` pendiente |
| `CredentialCipher` (Keystore AES-GCM) | No hay equivalente seguro | **La web no guarda secretos.** reMarkets y Copernicus van por un backend con OAuth del lado servidor | Fase 3 |
| Room (`lotes_satelitales`, precios, favoritas) | Room no tiene target web | Interfaz común `RepositorioLotes`: Room en Android, IndexedDB/`localStorage` (o KStore) en la web; o sincronización con backend | Fase 2 |
| Mapa GIS (WebView + `WebViewAssetLoader` + `TeselasMapa`) | Sí, distinto | iframe del mismo origen con la **misma** página `mapa_gis.js`; comandos y eventos por `postMessage`; teselas con la caché HTTP del navegador (el service worker no las intercepta) | ✅ Mapa base, búsqueda, centro. Lotes/índices en fase 2 |
| Windy (WebView + `scriptVolar`) | Sí | iframe de `embed.windy.com`; cambiar de lugar recarga el embed (no se puede mover un iframe de otro origen) | ✅ |
| Ubicación (play-services-location) | Sí, distinto | `navigator.geolocation` en `Navegador` (expect/actual) | Fase 2 |
| WorkManager + notificaciones | No | Web Push desde el backend (el navegador no corre tareas periódicas confiables) | Fase 3 |
| Exportar prescripciones (SAF, `EscritorShapefile` con `java.nio`) | Parcial | `ByteArray` común + descarga con Blob en la web | Fase 2 |
| Custom Tabs | Sí | `window.open(url, "_blank", "noopener")` | ✅ |
| Coil (portadas de noticias) | Sí | Coil 3 es multiplataforma | Fase 3 (con el RSS) |
| Pantallas Compose (Clima ~1,8k líneas, Satélite, Mercado…) | Sí, al pasar a Compose Multiplatform | Subir BOM, mover `ui/theme` y componentes a `shared`, luego cada pantalla | Fase 2 |

### APIs: qué se puede llamar desde el navegador (CORS, probado el 24/10/2026)

| Servicio | CORS | En la web |
|---|---|---|
| dolarapi.com, api.argentinadatos.com | ✅ `*` | Directo |
| Open-Meteo (pronóstico y geocodificación) | ✅ `*` | Directo |
| granos.ar (pizarra del día e historial de 365 ruedas) | ✅ `*` | Directo (el historial reemplaza a la CAC en la web) |
| Nominatim, Photon | ✅ `*` | Directo |
| Sentinel-2 (Edge Function `sentinel`) | ✅ | Por el backend de GRANEX con la sesión del usuario (`SentinelGranex`, en `:shared`) |
| Teselas Esri, OpenStreetMap, MapTiler | ✅ `*` | Directo |
| CAC/BCR (historial de pizarra) | ❌ | No hace falta: la web usa el historial de granos.ar |
| A3 Mercados, API CEM (ajustes de futuros, pública y sin clave) | ❌ | Edge Function `publico` (Android la llama directo) |
| RSS de los medios | ❌ | Edge Function `publico` (ya leídos y filtrados, en JSON) |
| Copernicus (token OAuth) | ❌ | Backend (además el secreto no puede ir al navegador) |

## 3. Fases

### Fase 1 — hecha

1. Módulos `:shared` y `:webApp`, catálogo de versiones, Kotlin 2.2.21.
2. Lógica pura movida a `commonMain` (sin cambiar paquetes) y sus tests a `commonTest`.
3. Ktor + kotlinx.serialization para las APIs con CORS; multiplatform-settings para preferencias.
4. App web con navegación adaptable: barra inferior (< 600 dp), riel lateral (600–1199 dp) y menú
   lateral fijo (≥ 1200 dp). Rutas `#/finanzas`, `#/clima`, `#/satelital`, `#/mercado`, `#/novedades`.
5. Secciones web:
   - **Finanzas:** cotizaciones del dólar en grilla adaptable (1 a 6 columnas) con brecha.
   - **Clima:** buscador de localidades, condiciones actuales, indicadores agro (balance hídrico, heladas) y 7 días. Mapa de Windy. Tablet: 2 columnas; escritorio: 3.
   - **Satélite:** mapa Leaflet compartido con Android, mapa base, buscador y centro. Panel lateral en tablet/escritorio.
   - **Mercado:** pizarra del día (granos.ar) y calculadora de margen con `MargenAgricola`. Escritorio: pizarra | datos | resultado.
   - **Novedades:** explica la falta del feed (CORS) y enlaza a los medios. Nunca muestra notas inventadas.
6. PWA: `manifest.webmanifest`, íconos (incluido *maskable*), service worker con precarga versionada,
   datos de APIs y teselas sin conexión.
7. Despliegue: `firebase.json`, `vercel.json`, workflow de GitHub Pages.

### Fase 2 — UI común y Satélite completo

1. **Subir Compose en Android** al BOM equivalente a Compose 1.10 y revisar las pantallas con
   `./gradlew :app:verifyRoborazziDebug` (regrabar con `recordRoborazziDebug` lo que cambie a propósito).
2. Agregar `compose-multiplatform` a `:shared`. Mover `ui/theme` (Paletas, Color con `AppThemeState`,
   Type), `GranexHeader`, `CamposCredenciales`, `IconoGrano` (los vectores `ic_grano_*.xml` pasan a
   `composeResources/drawable`). Borrar `webApp/.../ui/Tema.kt`.
3. Mover los ViewModels a `commonMain` (lifecycle-viewmodel es multiplataforma): primero
   `MercadoViewModel` y `FinancialViewModel`, que no dependen de Android salvo `Log`.
4. Repositorios: pasar `RosarioMarketRepository`, `WeatherRepository`
   a Ktor. En Android, motor OkHttp (sube OkHttp a 5: probar Retrofit y `TeselasMapa`) o migrar todo a Ktor.
5. Lotes en la web. **Hecho el 02/11/2026**:
   - Satélite lista, elige, dibuja, edita y borra lotes. Están en `localStorage` (`LotesWeb` sobre
     `AlmacenLotesAjustes`, observable) y se sincronizan con Supabase después de cada cambio.
   - El dibujo usa `utils/EditorPoligono` (`:shared`, con deshacer/rehacer, tests en
     `EditorLoteTest`) y la misma página del mapa (`setLotes` / `setEdicion` y los eventos `toque`,
     `verticeTocado`, `arrastrando`, `verticeSoltado` y `loteTocado`).
   - El GeoJSON sale de `utils/GeoJsonLote`, con el mismo formato que Android.
   - **03/11/2026:** provincia automática al guardar (`ServicioProvincias`, Nominatim) y escaneo
     Sentinel-2 (`EscaneoWebViewModel`): fechas despejadas, NDVI/NDRE/GNDVI con zonas de vigor, foto del
     día y relieve. Usa las mismas piezas compartidas que Android y la misma Edge Function, y hace falta
     sesión. Las capas del mapa salen de `utils/CapaMapaEscaneo`, y los PNG se decodifican en el
     navegador (`decodificarPng`).
   - Pendiente: la comparación entre fechas y la serie del índice, las prescripciones VRA en la web y
     que Android use `EditorPoligono` y `CapaMapaEscaneo` en lugar de sus copias.
6. Pantallas: cada una con `LocalClaseAncho` (o `currentWindowAdaptiveInfo()` de material3-adaptive)
   para 1/2/3 columnas. Así la versión Android también aprovecha tablets y Chromebooks.

### Fase 3 — Backend de GRANEX

> **Iniciada el 26/10/2026 con Supabase** (ver `docs/backend/SUPABASE.md`): cuentas (Auth), tabla
> `lotes` con PostGIS y sincronización offline (`:shared` `data/sync`), y bucket de archivos. Lo de
> abajo (proxy de RSS y de futuros, y Web Push) queda para Edge Functions de Supabase.
>
> **11/11/2026:** el historial de pizarra ya no necesita proxy (granos.ar guarda 365 ruedas, con CORS) y
> los futuros salen de la API pública del CEM de A3, sin credenciales: reMarkets quedó afuera.

Un servicio chico (Cloudflare Workers o Cloud Functions de Firebase) que:
- haga de proxy de los RSS y de los ajustes de A3 (lista fija de destinos, respuesta JSON con CORS solo
  para el dominio de la app, caché de 15 min a 1 h, límite por IP). Con la clave pública alcanza:
  no hace falta que el usuario haya ingresado;
- use Copernicus con el cliente OAuth de GRANEX del lado servidor (ya lo hace la función `sentinel`);
- envíe Web Push para las alertas de clima, mercado y noticias (reemplazo de `AlertasWorker`).

`FuenteSentinel` ya es una interfaz pensada para esto: la web usa una implementación que llama al backend.

## 4. Cómo trabajar

Comandos (desde la raíz; en esta PC ver la memoria de entorno: JDK y Gradle en D:):

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # app web en http://localhost:8080 con recarga
./gradlew :shared:allTests                      # tests comunes en JVM, JS y Wasm
./gradlew :webApp:distribucionPwa               # PWA publicable en webApp/build/dist/pwa
./gradlew :app:assembleDebug                    # la app Android sigue igual
```

### Mover un archivo de `:app` a `:shared`

1. Buscar imports de `android.*`, `java.*`, `org.json`, `okhttp3`, `retrofit2`, `Math.`, `String.format`,
   `synchronized` (`grep -n "^import\|Math\.\|String.format" archivo.kt`).
2. Reemplazar con lo común: `radianes/grados`, `FechasIso`, `formatoDecimal`, kotlinx.serialization,
   Ktor. Lo que sea de plataforma va detrás de una interfaz o `expect/actual`.
3. Moverlo a `shared/src/commonMain/kotlin/` **con el mismo paquete**. `internal` deja de ser visible
   desde `:app` (es por módulo).
4. Si tiene test JUnit puro, moverlo a `shared/src/commonTest` (`kotlin.test`; ojo: en kotlin.test el
   mensaje va al final).
5. `./gradlew :shared:allTests :app:testDebugUnitTest :app:assembleDebug`.

### Agregar una sección web

1. ViewModel en `webApp/.../viewmodel` (StateFlow + estado sellado, como en Android), creado en
   `GranexWeb` con `viewModel { … }` a partir de `ContenedorWeb`.
2. Pantalla en `pantallas/`: `Encabezado` + `Tablero(columnas)` eligiendo el reparto con
   `LocalClaseAncho` (COMPACTA apila todo en una columna).
3. Entrada en `Seccion` (ruta e ícono).

### Reglas que siguen valiendo en la web

- Ningún secreto en el código ni en `localStorage`. Las claves de servicios con restricción por
  origen (p. ej. MapTiler) se configuran para el dominio de la web; hoy la web no usa MapTiler.
- Solo APIs con CORS; el resto, por el backend. Nunca datos inventados: estado de error o "no disponible".
- Atribuciones visibles: "Datos: Open-Meteo.com", granos.ar, Leaflet/Esri/OSM, Windy.
- Todo lo que se envía a la página del mapa va como JSON (`ComandoMapa`) y la página solo acepta
  mensajes del mismo origen y de su ventana padre.
