# Supabase en GRANEX

Supabase es el backend de cuentas, base de datos y archivos de GRANEX, para Android y la web. La
integración usa **supabase-kt 3.2.6** en el módulo `:shared` (Postgrest, Auth y Storage).

**Sin proyecto configurado la app funciona igual que antes**, solo con datos locales: no hay
cuentas ni sincronización.

## 1. Crear el proyecto (una vez)

1. Crear un proyecto en [supabase.com](https://supabase.com/dashboard). Elegir la región **South
   America (São Paulo)**, que es la más cercana a Argentina.
2. Aplicar el esquema `supabase/migrations/20261026120000_lotes_perfiles.sql`, de una de estas dos formas:
   - Con la CLI: `supabase link --project-ref <ref>` y después `supabase db push`.
   - Sin la CLI: SQL Editor → pegar el archivo → Run.
3. Authentication → Sign In / Providers → Email: dejar activa la **confirmación de correo**.
   Authentication → URL Configuration: cargar el dominio de la web en *Site URL* y en *Redirect URLs*.
4. Project Settings → API Keys: copiar la **URL** del proyecto y la clave **pública** (`anon` o
   `sb_publishable_…`).

> Nunca uses la clave `service_role` / `sb_secret_…` en la app ni en la web: saltea RLS y da acceso
> a todos los datos. Si alguien la configura por error, el build se corta (`gradle/supabase.gradle.kts`)
> y la app tampoco la acepta (`ConfiguracionSupabase.esClaveSecreta`).

## 2. Configurar los builds

### Android y desarrollo local

En `local.properties`, en la raíz del proyecto. El archivo no se versiona:

```properties
supabase.url=https://<ref>.supabase.co
supabase.anonKey=<clave pública>
```

Los valores llegan a `BuildConfig.SUPABASE_URL` y `BuildConfig.SUPABASE_ANON_KEY`, y se leen con
`ApiKeys.supabase`.

### Web y CI

Con las variables de entorno `SUPABASE_URL` y `SUPABASE_ANON_KEY`, que tienen prioridad sobre
`local.properties`. Por ejemplo, en Vercel: Settings → Environment Variables.

`:webApp` genera `ConfiguracionSupabaseWeb.kt` al compilar y agrega el dominio del proyecto al
`connect-src` de la CSP de `index.html` (marcador `__ORIGEN_SUPABASE__`).

## 3. Qué hay en la base

| Tabla o bucket | Contenido | Acceso (RLS) |
|---|---|---|
| `perfiles` | Datos del productor, uno por cuenta | Cada usuario lee y edita solo el suyo |
| `lotes` | Lotes: GeoJSON, `geom` PostGIS (calculada por trigger), superficie, provincia | Cada usuario lee, crea y edita solo los suyos. Sin DELETE: los borrados son lógicos |
| Bucket `lotes` (privado) | Archivos: GeoJSON, Shapefile, prescripciones; carpeta `{usuario_id}/` | Cada usuario solo usa su carpeta |

La columna `geom` (`geometry(Polygon, 4326)` con índice GiST) permite consultas espaciales en el
servidor, por ejemplo `ST_Intersects` o `ST_Area(geom::geography) / 10000` en hectáreas, sin que
la app maneje WKB.

## 4. Cómo funciona la sincronización offline

Primero se guarda en el dispositivo y después se sincroniza:

- **Dónde se guarda:** Room en Android (`lotes_satelitales`, versión 6) y `localStorage` en la web
  (`AlmacenLotesAjustes`).
- **Cada cambio queda pendiente:** alta, edición y borrado lógico, con `modificadoEn` = reloj del
  dispositivo.
- **`SincronizadorLotes`** (en `:shared`, con tests en JVM, JS y Wasm):
  1. Sube lo pendiente.
  2. En el servidor, el trigger `lotes_antes_de_guardar` conserva la versión con mayor
     `modificado_en`: **gana el último que editó**, aunque su cambio llegue después.
  3. Baja lo que cambió desde el último cursor (`actualizado_en`, reloj del servidor), con 2 minutos
     de margen para no perder transacciones que confirmaron tarde.
- **Cuándo corre:**
  - En Android, con `SincronizacionLotesWorker` (WorkManager, solo con conexión: si no hay señal,
    espera a que vuelva). Se programa al abrir la app y después de cada cambio.
  - En la web, al arrancar y cuando vuelve la conexión.
- **Lotes y cuentas:**
  - Los lotes creados antes de tener cuenta se suben a la primera cuenta que inicia sesión.
  - `cerrarSesion` sube lo pendiente, vacía los lotes del dispositivo y recién ahí cierra la
    sesión. Si quedó algo sin subir (sin red), devuelve `ResultadoCierreSesion.Bloqueada`: no cierra
    la sesión ni borra nada, para no perderlo.
  - Si el dispositivo tiene lotes de otra cuenta (su sesión venció sin cerrarla): los ya
    sincronizados se quitan al ingresar la nueva cuenta; los que quedaron sin subir no se mezclan
    (`ResultadoSincronizacion.OtraCuenta`) y cerrar la sesión nueva no los borra.

## 4.1 Pantalla de cuenta

- **Lógica compartida:** `ControladorCuenta` (`:shared`, `data/sync`), con tests en `CuentaTest`.
  Cada plataforma le pasa el scope de su ViewModel. Hace una operación a la vez y valida los campos
  con `ValidacionCuenta`: correo con formato válido; al crear la cuenta, 8 caracteres como mínimo,
  con letras y números, y la contraseña repetida igual.
- **Al ingresar** (o al crear la cuenta, si el proyecto no pide confirmar el correo) sincroniza
  enseguida: baja los lotes de la nube a Room y sube los creados sin cuenta. Si falla, programa el
  reintento: en Android, `SincronizacionLotesWorker`, que espera a que haya red.
- **Con confirmación de correo,** el registro avisa que se abra el enlace y pasa a "Iniciar sesión".
  No se maneja el regreso del enlace a la app: la cuenta se confirma en el navegador y después se
  ingresa con la contraseña.
- **Al cerrar sesión** pide confirmación. Si quedan cambios sin subir, muestra la alerta "No se
  puede cerrar sesión…" con "Reintentar".
- **Android:** `CuentaViewModel` y `ui/screens/AjustesCuenta.kt` (`TarjetaCuenta`, con tests en
  `TarjetaCuentaTest`), arriba de todo en Ajustes. Muestra cuántos cambios faltan subir
  (`AgroDao.contarLotesPendientes`).
- **Tokens:** Postgrest y Storage usan el token de la sesión del mismo cliente; sin sesión, la clave
  pública, que con RLS no ve filas. Lo prueba `CuentaTest.lasConsultasVanConElTokenDeLaSesion`.
- **Web:** sección Cuenta (`#/cuenta`, `pantallas/Cuenta.kt`), abierta desde el menú lateral, el
  riel o el menú ⋮ del teléfono. Usa el mismo `ControladorCuenta` mediante `CuentaWebViewModel`, con
  los mismos textos que Android ("este navegador" en lugar de "este teléfono"). Sin red no programa
  reintentos: la web sincroniza sola al volver la conexión. Los campos de Compose dibujan en un canvas,
  así que el gestor de contraseñas del navegador no los autocompleta.

**Límites conocidos:**
- La resolución de conflictos usa el reloj del dispositivo: un teléfono con la hora muy atrasada
  puede perder ediciones.
- Las filas borradas quedan en la base como marca (`eliminado = true`). Para purgar las viejas,
  usar una tarea programada con la clave secreta, del lado del servidor.

## 4.1a Acceso: Google y recuperar la contraseña

La app Android pide una cuenta para entrar (pantalla de acceso). Para que funcionen todas las
opciones hay que configurar, una sola vez:

**Ingresar con Google**
1. En Google Cloud Console (console.cloud.google.com), crear un proyecto "GRANEX" y configurar la
   pantalla de consentimiento OAuth (tipo Externo, nombre GRANEX, correo supportgranex@gmail.com).
2. Credenciales → Crear ID de cliente OAuth → **Aplicación web**. Copiar el *ID de cliente* y el
   *secreto*.
3. Credenciales → Crear ID de cliente OAuth → **Android**: paquete `com.aistudio.granex.agtech` y la
   huella SHA-1 de la firma. La de depuración sale con
   `keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android`.
   Después de publicar, crear otro con la SHA-1 de "Firma de apps" de Play Console.
4. Supabase → Authentication → Sign In / Providers → **Google**: activarlo, pegar el ID de cliente
   web y el secreto. En "Client IDs" (Authorized Client IDs) poner el ID de cliente web.
5. En `local.properties` de la PC que compila: `google.webClientId=<ID de cliente web>` (es público:
   va dentro del APK). Sin ese dato el botón "Continuar con Google" avisa que no está configurado.

**Correos de GRANEX (remitente y plantillas)**

El correo de prueba de Supabase sale de "Supabase Auth", solo entrega a pocos destinatarios y con un
límite muy bajo por hora. Para que los correos salgan como GRANEX:

1. *Contraseña de aplicación de Gmail* (en la cuenta supportgranex@gmail.com): activar la
   verificación en dos pasos (myaccount.google.com → Seguridad) y después crear una contraseña de
   aplicación en myaccount.google.com/apppasswords (nombre "Supabase"). Son 16 letras: no se
   comparten con nadie ni se pegan en el chat.
2. Supabase → Authentication → Emails → **SMTP Settings** → Enable custom SMTP:
   - Sender email: `supportgranex@gmail.com`
   - Sender name: `GRANEX`
   - Host: `smtp.gmail.com`, Port: `465`
   - Username: `supportgranex@gmail.com`, Password: la contraseña de aplicación del paso 1
   - Minimum interval: 30 segundos
   Gmail permite unos 500 correos por día. Con un dominio propio (granex.com.ar) conviene pasar a
   Resend o Brevo, que entregan mejor y no caen en "no deseado".
3. Authentication → Emails → **Templates**. La app no abre enlaces: pide un código de 6 dígitos.
   - **Confirm signup**: asunto `Tu código para activar GRANEX: {{ .Token }}` y como mensaje el
     contenido de `supabase/plantillas/confirmar-cuenta.html`.
   - **Reset Password**: asunto `Tu código para cambiar la contraseña de GRANEX` y el contenido de
     `supabase/plantillas/cambiar-contrasena.html`.
4. Authentication → Sign In / Providers → **Email**: "Confirm email" activado y "Email OTP Length"
   en 6.

Con la confirmación activa, al crear la cuenta la app pide el código del correo (también si se
intenta ingresar con una cuenta sin activar). Los datos del registro (nombre, establecimiento,
provincia…) viajan en los metadatos de la cuenta y la fila de `perfiles` se crea sola al activarla.

## 4.1b Eliminar la cuenta (Edge Function `cuenta`)

- **Qué hace:** `POST {accion: "eliminar"}` con la sesión del usuario. Borra su carpeta del bucket
  `lotes` y el usuario de Auth. Las filas de `perfiles`, `lotes` y `uso_sentinel` se borran solas
  (`on delete cascade`). Código en `supabase/functions/cuenta/index.ts`.
- **Clave:** usa `SUPABASE_SERVICE_ROLE_KEY`, que Supabase inyecta en todas las funciones. No hay
  que cargar ningún secreto.
- **Desplegar:** `supabase functions deploy cuenta` (o desde el panel, Edge Functions → Deploy).
  Sin desplegarla, el botón «Eliminar cuenta» muestra un error y no borra nada.

## 4.1c Datos públicos para la web (Edge Function `publico`)

- **Qué hace:** le pasa a la web dos fuentes que el navegador no puede pedir directo, porque no
  responden con CORS. Código en `supabase/functions/publico/` (`index.ts` y `rss.ts`).
  - `GET /publico/noticias`: lee los RSS oficiales de los 8 medios de Novedades y devuelve las notas
    filtradas en JSON, con las mismas reglas que la app Android.
  - `GET /publico/a3/api/v2/closing-prices?…`: los precios de ajuste de los futuros de soja, maíz
    y trigo, desde la API pública del CEM de A3 Mercados.
- **Sin sesión ni secretos:** alcanza la clave pública del proyecto, que la web ya tiene. No guarda
  nada en la base.
- **Protección:** no es un proxy abierto. Solo lee la lista fija de medios y, de A3, solo un pedido
  de ajustes de esos tres productos, con los parámetros validados. Tiene caché (15 min las noticias,
  30 min los futuros) y un límite de 60 pedidos por minuto por IP.
- **Desplegar:** `supabase functions deploy publico` (o desde el panel, Edge Functions → Deploy,
  con los dos archivos). Sin desplegarla, Finanzas y Novedades de la web muestran un error con
  «Reintentar»; Android no la usa.
- **Si cambia la lista de medios** en `FuentesNoticias.kt`, actualizar `FUENTES` en `index.ts`.

## 4.2 Imágenes satelitales (Edge Function `sentinel`)

- **Qué hace:** pide a Sentinel Hub (Copernicus Data Space) los datos de un lote. Código en
  `supabase/functions/sentinel/index.ts`; la tabla `uso_sentinel` se crea en la migración
  `20261028150000_uso_sentinel.sql`. Tiene cinco acciones:
  - `escenas`: las fechas con imagen sobre el lote y el % sin dato medido sobre el lote.
  - `imagen`: NDVI, NDRE y GNDVI a 10 m de una fecha.
  - `campana`: el pico de vigor de una campaña (p90 de NDVI y NDRE, y la cantidad de pasadas).
  - `color`: la foto en color real de una fecha, con un 20 % de margen alrededor del lote.
  - `relieve`: la altura del terreno (Copernicus DEM GLO-30, remuestreado a 10 m). No pasa por
    Sentinel Hub, donde el GLO-30 está reservado a usuarios CCM: `dem.ts` lee las teselas públicas
    de AWS Open Data (`copernicus-dem-30m`) con pedidos por rango, sin clave y sin gastar cuota.
    La licencia del GLO-30 exige mostrar su aviso (`AnalisisRelieve.AVISO_DEM`) donde se muestra o
    se exporta el relieve.
  Cada acción cuenta como un pedido. La app guarda en el teléfono las campañas y el relieve,
  porque no cambian.
- **Cuenta de Copernicus:** una sola, la de GRANEX. Se carga en el panel de Supabase, en Edge
  Functions → Secrets, como `CDSE_CLIENT_ID` y `CDSE_CLIENT_SECRET`. Nunca va en la app ni en la web.
- **Quién puede usarla:** solo usuarios con sesión. La función rechaza la clave pública sola y suma
  cada pedido en `uso_sentinel`, con un límite de 200 por usuario por día (`LIMITE_DIARIO`).
- **Cuota gratuita de Copernicus:** 10.000 unidades de procesamiento y 50.000 pedidos por mes,
  con 2 pedidos a la vez. Una imagen de un lote de 70 ha gasta alrededor de 0,01 a 0,05 unidades.
  Si GRANEX crece, hace falta un plan pago o pedir créditos.
- **Aviso esperado del linter:** "Signed-In Users Can Execute SECURITY DEFINER Function", por
  `registrar_pedido_sentinel()`. Es intencional: solo suma al contador del propio usuario y la
  tabla no tiene políticas de escritura, así que nadie puede bajarlo.

## 4.3 Estaciones meteorológicas propias (Edge Function `estaciones`)

- **Qué hace:** conecta la estación del productor y devuelve sus datos en unidades métricas. Código
  en `supabase/functions/estaciones/` (`index.ts` y `marcas.ts`); las tablas se crean en la migración
  `20261116120000_estaciones.sql`. Marcas:
  - **Davis (WeatherLink v2):** API Key + API Secret. El gráfico de 24 h necesita WeatherLink Pro.
  - **Ecowitt / Misol (API v3):** Application Key + API Key; la estación se elige por su MAC.
  - **Pessl Metos (FieldClimate v2):** clave pública y privada HMAC.
  - **Envío directo:** la estación manda sus datos (protocolo Ecowitt o Wunderground) a
    `https://<proyecto>.supabase.co/functions/v1/estaciones/subir/<código>`. El código se muestra una
    sola vez al crearla; se guarda solo su hash.
- **Acciones con sesión:** `listar`, `agregar` (si la cuenta de la marca tiene varias estaciones,
  devuelve `elegir` y la app repite el pedido con la elegida), `datos` y `eliminar`. Hasta 5
  estaciones por usuario.
- **Claves de las marcas:** se prueban contra la marca antes de guardarlas y quedan cifradas con
  AES-256-GCM en `credenciales_estacion`, una tabla sin políticas (solo la función la lee). Nunca
  vuelven a la app.
- **Secreto:** `CLAVE_ESTACIONES`, 32 bytes al azar en base64, en Edge Functions → Secrets. Si se
  pierde o se cambia, las estaciones de marcas con nube hay que volver a conectarlas.
- **Datos del envío directo:** `lecturas_estacion` guarda 10 días (como mucho una lectura por minuto)
  y `diario_estacion` guarda para siempre la lluvia y las temperaturas extremas de cada día.
- **Desplegar:** aplicar la migración, cargar el secreto y desplegar con
  `supabase functions deploy estaciones --no-verify-jwt`. Sin `--no-verify-jwt` las estaciones con
  envío directo reciben 401, porque no mandan JWT. La función valida la sesión ella misma en todo
  lo demás.
- **Falta probar con equipos reales:** el historial diario de Ecowitt, el formato de FieldClimate y
  si cada modelo puede enviar por HTTPS (algunos gateways de Ecowitt solo envían por HTTP, puerto 80).

## 5. Seguridad

- **Clave pública:** es visible en el APK y en la web por diseño. Las políticas RLS de cada tabla y
  de Storage son las que protegen los datos.
- **Sesión en Android:** `SesionEnAjustes` guarda los tokens en `granex_sesion.xml`, cifrados con
  `CredentialCipher` (AES-GCM, clave del Keystore) y excluidos del backup.
- **Sesión en la web:** va en `localStorage`, como en supabase-js. La protege la CSP estricta de
  `index.html` (sin scripts inline ni de terceros).
- **Auth:** usa PKCE, así los enlaces de confirmación y de recuperación no llevan tokens en la URL.
- **Credenciales de terceros:** reMarkets y Copernicus siguen sin guardarse en la web. Pasarlas al
  backend (Edge Functions) es el próximo paso.

## 6. Pendiente

1. ~~Dibujo y lista de lotes en la web, con la pantalla de cuenta~~ (hecho el 02/11/2026: ver
   `docs/web/MIGRACION_KMP.md`). El 03/11/2026 se sumaron la provincia automática al guardar y el
   escaneo Sentinel-2 del lote (índices, foto del día y relieve por la misma Edge Function). En la web
   faltan la comparación entre fechas, la serie del índice y las prescripciones.
2. Edición del perfil (`RepositorioPerfil`) y subida de prescripciones a Storage (`ArchivosLotes`).
3. Edge Functions: proxy de la pizarra CAC y de los RSS para la web, y credenciales de reMarkets y
   Copernicus del lado servidor.
4. Para pasar a supabase-kt 3.3 o posterior hay que subir Kotlin a 2.3 o 2.4, y con él KSP y
   Compose Multiplatform.
