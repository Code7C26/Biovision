# Publicar GRANEX web (PWA)

La web es una carpeta estática: se arma con Gradle y se sube a cualquier hosting con HTTPS.
Arquitectura y plan de migración: [MIGRACION_KMP.md](MIGRACION_KMP.md).

## 0. Antes de publicar (lista de control)

Lo que el código ya resuelve está marcado como hecho; el resto depende de decisiones o cuentas tuyas.

**Datos de publicación** (variables de entorno o `local.properties`; `distribucionPwa` avisa con
`AVISO para publicar` si falta alguno):

| Dato | Variable / propiedad | Para qué |
|---|---|---|
| Correo de soporte | `GRANEX_EMAIL_SOPORTE` / `granex.emailSoporte` (por defecto supportgranex@gmail.com) | Contacto de las páginas legales; la Ley 25.326 exige un canal para pedir acceso o borrado de datos |
| URL pública | `GRANEX_URL_PUBLICA` / `granex.urlPublica` | `canonical` y vista previa al compartir (`og:url`, `og:image`) |
| Supabase | `SUPABASE_URL`, `SUPABASE_ANON_KEY` | Cuentas, sincronización y escaneo; sin ellos la web funciona solo local |

Sin un dato, sus líneas no se publican: nunca queda un marcador a la vista.

**Supabase (panel del proyecto):**
- [x] *Authentication → URL Configuration*: poner la URL pública como **Site URL** y sumarla en
  **Redirect URLs**. Si no, los enlaces de confirmación de cuenta llevan a otra dirección.
- [ ] Migraciones de `supabase/migrations/` aplicadas (RLS en `lotes`, `perfiles` y `uso_sentinel`).
- [x] Edge Function `sentinel` desplegada, con los secretos `CDSE_CLIENT_ID` y `CDSE_CLIENT_SECRET`. Ya
  acepta pedidos del navegador (CORS) y rechaza los que no tienen sesión.
- [x] Edge Function `cuenta` desplegada (borrado de cuenta desde la app).
- [x] Edge Function `publico` desplegada (`supabase functions deploy publico`): futuros de A3 y noticias en
  la web. No pide sesión ni secretos; sin ella, Finanzas y Novedades muestran un error con «Reintentar».
- [x] Revisar el límite de correos del servicio de Auth: el que trae Supabase de fábrica es bajo para
  confirmar cuentas en producción. Conviene un SMTP propio (*Authentication → Emails*).

**Legal y contenido:**
- [x] Páginas `legal/privacidad.html` y `legal/terminos.html` dentro de la PWA, enlazadas desde
  "Acerca de" y desde "Crear cuenta". La política cubre cuentas, lotes, almacenamiento del navegador y
  servicios externos, e incluye el aviso de la AAIP.
- [ ] Que las revise un abogado. Registrar la base de datos ante la AAIP si corresponde (hay datos
  personales: correos).
- [x] La política de la landing (`web/privacidad.html`) quedó al día con las cuentas y la web. El
  correo de contacto de la landing sale de `CONFIG.emailSoporte` en `web/js/main.js`.
- [x] Borrado de cuenta desde la app ("Eliminar cuenta", Edge Function `cuenta`) y
  `web/eliminar-cuenta.html` para pedirlo sin la app.

**Servicios de terceros** (sección 6): Open-Meteo gratis es solo para uso no comercial; las teselas
de OpenStreetMap no admiten uso intensivo.

**Hosting:**
- [x] Cabeceras de seguridad (HSTS, `frame-ancestors`, COOP, CORP, `Permissions-Policy`,
  `upgrade-insecure-requests`) y caché de un año para los archivos con hash, en `vercel.json` y
  `firebase.json`. GitHub Pages no permite cabeceras: preferir Firebase o Vercel.
- [x] `404.html`, `robots.txt` y aviso en la pantalla de carga si la app no termina de cargar.
- [ ] Dominio propio con HTTPS. Después de publicar, revisar con
  [securityheaders.com](https://securityheaders.com) y abrir la consola del navegador: no tiene que
  haber bloqueos de CSP.

## 1. Generar la carpeta

```bash
./gradlew :webApp:distribucionPwa        # → webApp/build/dist/pwa       (Wasm + JS de respaldo)
./gradlew :webApp:distribucionPwaWasm    # → webApp/build/dist/pwa-wasm  (solo Wasm, compila más rápido)
```

- `distribucionPwa` usa el modo compatibilidad de Compose (`composeCompatibilityBrowserDistribution`):
  el navegador carga Wasm si soporta WasmGC (Chrome/Edge 119+, Firefox 120+, Safari/iOS 18.2+) y,
  si no, la versión JS. Es la que conviene publicar.
- El enlazado de producción (Wasm y JS) pide un heap de ~3 GB y varios GB de memoria libre. En la
  PC de desarrollo anduvo recién con el archivo de paginación en D: (límite de ~25 GB) y tarda más de
  una hora. Si la memoria no alcanza, generá solo `distribucionPwaWasm` y dejá que el workflow de
  GitHub Actions arme la versión completa.
- Después de compilar, la tarea copia la salida, agrega `vercel.json` y genera `sw.js` con una versión
  (hash del contenido; cada build nuevo invalida la caché vieja) y tres listas de precarga: lo común,
  la variante Wasm y la variante JS. El service worker precarga lo común y la variante que el navegador
  usa (~14 MB en vez de los ~29 MB de la carpeta). Los scripts que no pide ninguna variante (por
  ejemplo, una copia de `skiko.wasm`) se publican igual, pero sin precarga.

### Probar en la PC

```bash
python webApp/pwa/servir.py        # mismas cabeceras de seguridad que producción (lee vercel.json)
```

Abrir http://localhost:8080 (solo accesible desde la misma PC). `localhost` cuenta como sitio seguro: el service worker y el botón
"Instalar" funcionan igual que en producción. Para desarrollar con recarga automática:
`./gradlew :webApp:wasmJsBrowserDevelopmentRun` (sin service worker).

En producción el sitio **tiene que ser HTTPS** (Firebase, Vercel y GitHub Pages lo son).

## 2. Firebase Hosting

**Publicado (18/11/2026):** proyecto `granex-4a7db`. `firebase.json` publica `web/`: la landing queda en
https://granex-4a7db.web.app y la PWA en https://granex-4a7db.web.app/app/ (copia de
`webApp/build/dist/pwa` en `web/app`). `granex.urlPublica` y la *Site URL* de Supabase = `…/app/`.
No usar `"trailingSlash": false`: redirige `/app/` a `/app` y los archivos relativos de la app dan 404.

```bash
./gradlew :webApp:distribucionPwa
rm -rf web/app && cp -r webApp/build/dist/pwa web/app
npx firebase-tools deploy --only hosting
```

Primera vez:

`firebase.json` (raíz del repo) ya apunta a `webApp/build/dist/pwa` y define cabeceras de seguridad
y de caché.

```bash
npm install -g firebase-tools
firebase login
firebase use --add                 # elige o crea el proyecto; guarda .firebaserc
./gradlew :webApp:distribucionPwa
firebase hosting:channel:deploy prueba   # URL de prueba temporal
firebase deploy --only hosting           # producción: https://<proyecto>.web.app
```

## 3. Vercel

El entorno de build de Vercel no trae JDK ni Android SDK, así que se sube la carpeta ya compilada
(el `vercel.json` de las cabeceras viaja dentro de ella):

```bash
./gradlew :webApp:distribucionPwa
npx vercel deploy webApp/build/dist/pwa          # vista previa (la primera vez crea el proyecto)
npx vercel deploy webApp/build/dist/pwa --prod   # producción
```

## 4. GitHub Pages

El workflow `.github/workflows/web-pwa.yml` corre los tests de `:shared`, arma la PWA y la publica.

1. Crear el repositorio en GitHub y subir el proyecto (hoy la carpeta no es un repo git):
   `git init`, `git add .`, `git commit`, `git remote add origin …`, `git push -u origin main`.
   Revisar antes que `.gitignore` deje afuera `.env`, `debug.keystore` y `local.properties` (ya lo hace).
2. En GitHub: *Settings → Pages → Source: GitHub Actions*.
3. Cada push a `main` que toque `shared/`, `webApp/` o el mapa publica en
   `https://<usuario>.github.io/<repo>/`. También se puede lanzar a mano (*Actions → Web PWA → Run workflow*).

La app usa rutas relativas (`./`, `#/clima`), así que funciona bajo `/<repo>/`. GitHub Pages no
permite cabeceras propias: se pierden las de `firebase.json`/`vercel.json` (HSTS, `frame-ancestors`,
COOP, `Permissions-Policy`). La política de contenido (CSP) principal sí se mantiene, porque va en el
`<meta>` de `index.html`. Para producción conviene Firebase o Vercel.

### Seguridad: qué protege cada capa

| Dónde | Qué hace |
|---|---|
| `index.html` (CSP en `<meta>`) | Solo scripts propios, sin scripts en línea ni `eval`; red solo hacia dolarapi, Open-Meteo (pronóstico y geocodificación) y granos.ar; iframes solo del propio sitio y de Windy |
| `mapa/mapa_gis.html` (CSP propia) | El mapa solo carga sus scripts y teselas de Esri, OpenStreetMap y MapTiler |
| Cabeceras del hosting | HSTS, `frame-ancestors 'self'` + `X-Frame-Options` (nadie puede incrustar GRANEX), COOP, `nosniff`, `Permissions-Policy` sin cámara, micrófono ni ubicación |
| Código | Iframe de Windy con `sandbox` (no puede navegar ni redirigir la app); mensajes aceptados solo del iframe del mapa; enlaces externos solo https (`esUrlExternaSegura`) |
| Datos | Sin cookies, cuentas ni analítica; en `localStorage` solo la última localidad y el tema; ninguna contraseña en la web |

Si se agrega un servicio externo, sumarlo al `connect-src` (o `frame-src`) de `index.html`: si no,
el navegador lo bloquea. Para revisar las cabeceras después de publicar:
[securityheaders.com](https://securityheaders.com) y la pestaña *Console* de las herramientas de
desarrollo (los bloqueos de CSP aparecen ahí).

## 5. Después de publicar

- **Instalar:** Chrome/Edge en PC muestran el ícono de instalar en la barra de direcciones; Android
  (Chrome) ofrece "Instalar app"; iPhone/iPad: Safari → Compartir → "Agregar a inicio".
- **Sin conexión:** la app abre con lo último que se vio (pronóstico, dólar, pizarra y las teselas
  del mapa ya visitadas). El mapa de Windy y las búsquedas nuevas necesitan conexión.
- **Actualizaciones:** al abrir la app con conexión se descarga la versión nueva en segundo plano y
  aparece el aviso "Versión nueva disponible · Actualizar". Si se ignora, se aplica en la
  siguiente apertura. Subir `VERSION_WEB` (`ui/AcercaDe.kt`) con los cambios visibles.
- **Dominio propio:** configurarlo en el hosting.
- **Landing (`web/`):** cuando haya URL definitiva, agregar el enlace "Abrir versión web" en `CONFIG`
  de `web/js/main.js` y revisar que los textos digan lo que la web hace hoy.

## 6. Condiciones de los servicios usados por la web

Revisarlas antes de abrir la web al público:

- **Open-Meteo:** el plan gratuito es solo para uso no comercial. Si GRANEX se comercializa hace falta
  el plan pago (clave y dominio `customer-api.open-meteo.com`). Mantener "Datos: Open-Meteo.com".
- **Teselas de OpenStreetMap:** su política no admite uso intensivo; para producción conviene MapTiler
  u otro proveedor, con la clave restringida al dominio de la web.
- **Esri World Imagery, Windy:** según sus términos de uso; la atribución queda visible en el mapa.
- **granos.ar:** uso libre con atribución ("Fuente: granos.ar").
