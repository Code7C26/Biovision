# Publicar GRANEX en Google Play

Guía paso a paso con los textos listos para copiar en Play Console. Correo de soporte: **supportgranex@gmail.com**.

## 1. Antes de empezar

| Qué | Dónde | Estado |
|---|---|---|
| Cuenta de desarrollador de Google Play (US$ 25, pago único) | play.google.com/console | Pendiente |
| Política de privacidad publicada en una URL https | `web/privacidad.html` | Falta publicar la web |
| Página para eliminar la cuenta | `web/eliminar-cuenta.html` | Falta publicar la web |
| Borrar la cuenta desde la app | Ajustes → Eliminar cuenta | Hecho (falta desplegar la función `cuenta` en Supabase) |
| Clave de subida (firma) | ver punto 2 | Pendiente |
| Licencia comercial de Open-Meteo, si la app se cobra | open-meteo.com/en/pricing | A decidir |

La web (`web/`) se puede publicar gratis en Vercel, Netlify o GitHub Pages; ver `docs/web/DESPLIEGUE.md`.

## 2. Firma (clave de subida)

Google firma la app final (Play App Signing). Vos firmás cada versión con una **clave de subida**. Si la perdés, Google la puede reemplazar, pero tarda días: guardala en dos lugares (por ejemplo, un pendrive y un gestor de contraseñas).

Crearla una sola vez (PowerShell, en una carpeta **fuera** del proyecto):

```powershell
& "D:\USUARIO\dev\jdk-21.0.12.1+1\bin\keytool.exe" -genkeypair -v `
  -keystore D:\USUARIO\claves\granex-subida.jks -alias upload `
  -keyalg RSA -keysize 4096 -validity 10000
```

Pide una contraseña (elegila vos, mínimo 12 caracteres) y tus datos. Nunca la pongas en el repositorio.

Armar el bundle firmado para cada versión:

```powershell
$env:KEYSTORE_PATH = "D:\USUARIO\claves\granex-subida.jks"
$env:STORE_PASSWORD = Read-Host "Contraseña de la clave"
$env:KEY_PASSWORD = $env:STORE_PASSWORD
$env:KEY_ALIAS = "upload"
./gradlew :app:bundleRelease
```

- Resultado: `app/build/outputs/bundle/release/app-release.aab`. Ese archivo es el que se sube a Play.
- Subí también `app/build/outputs/mapping/release/mapping.txt` (Play Console → la versión → "Archivos de desofuscación").
- En cada versión nueva subí `versionCode` (y `versionName`) en `app/build.gradle.kts`.

## 3. Prueba cerrada obligatoria

Las cuentas personales nuevas tienen que hacer una **prueba cerrada con al menos 12 personas durante 14 días seguidos** antes de poder publicar. Conviene empezar cuanto antes:

1. Play Console → Pruebas → Prueba cerrada → crear pista.
2. Agregar los correos de Gmail de los testers (familia, productores conocidos, el agrónomo).
3. Subir el `.aab` y compartir el enlace de suscripción.
4. Pedirles que la instalen y la usen. Después de 14 días se habilita "Solicitar acceso a producción".

## 4. Ficha de la tienda

**Nombre** (máx. 30): `GRANEX: agro, clima y mercado`

**Descripción breve** (máx. 80):
`Cotizaciones, pizarra Rosario, clima agro, monitoreo satelital y dosis variable.`

**Descripción completa**:

```
GRANEX reúne en una sola app la información que un productor agropecuario consulta todos los días.

MERCADO
• Precios de pizarra de la Cámara Arbitral de Rosario: soja, maíz, trigo, girasol y sorgo, en pesos y dólares.
• Historial con gráficos por semana, mes y año.
• Calculadora de margen bruto y neto por hectárea, con derechos de exportación, impuestos provinciales y rinde de indiferencia, en toneladas o quintales.
• Dólar oficial, mayorista, MEP, CCL y blue.

CLIMA AGRO
• Pronóstico por hora y a 7 días con el modelo europeo ECMWF.
• Mapa de radar, lluvia, viento y temperatura.
• Alertas de helada, lluvia y viento para tu zona.

MONITOREO SATELITAL
• Dibujá tus lotes o detectalos con un toque sobre el mapa.
• Índices de vegetación NDVI, NDRE y GNDVI con imágenes Sentinel-2 de 10 m, de las últimas fechas sin nubes.
• Foto del día en color real, comparación entre fechas y relieve del lote.

DOSIS VARIABLE
• Zonas de manejo a partir de varias campañas, del relieve o de tus mapas de rinde.
• Análisis de suelo por zona y dosis sugeridas, siempre editables.
• Exportación para monitores Cuffia, FGS, Altina, ISO-XML, Shapefile y GeoJSON.

NOTICIAS
• Las últimas notas de los principales medios agropecuarios del país, filtradas por tema.

Ingresá con tu correo o con tu cuenta de Google: tus lotes, análisis de suelo y mapas de rinde se guardan en tu cuenta y se sincronizan entre tus dispositivos.

Las dosis sugeridas son orientativas: verificalas con tu ingeniero agrónomo. Verificá que el archivo de prescripción funcione en tu monitor antes de aplicar.
```

**Categoría**: Productividad (alternativa: Herramientas). **Correo de contacto**: supportgranex@gmail.com. **Sitio web**: la URL donde publiques `web/`.

**Gráficos**: ícono 512 × 512 PNG, imagen destacada 1024 × 500 y al menos 2 capturas de teléfono (recomendado: Finanzas, Mercado, Clima, Satélite con índice, Prescripción). Las capturas se pueden sacar del Pixel con el modo demo.

## 5. Contenido de la app (Play Console → Política → Contenido de la app)

- **Política de privacidad**: URL de `privacidad.html`.
- **Anuncios**: No contiene anuncios.
- **Acceso a la app**: hace falta una cuenta para entrar. Elegir "Todas o algunas funciones están restringidas" y dar una cuenta de prueba (correo y contraseña) (crear una con un correo propio, por ejemplo `supportgranex+revision@gmail.com`, y cargar un lote de ejemplo).
- **Clasificación de contenido**: cuestionario IARC → categoría "Utilidad, productividad, comunicación u otra". Sin violencia, sexo, apuestas, ni compras. Interacción entre usuarios: no. Comparte ubicación con otros usuarios: no. Resultado esperado: apta para todo público.
- **Público objetivo**: 18 años o más (no está pensada para menores).
- **App de noticias**: No (las noticias son una sección, no la función principal). Si Play insiste, declararla y completar los datos del editor.
- **Apps gubernamentales / financieras / salud**: No.
- **Eliminación de la cuenta**: URL de `eliminar-cuenta.html`.

## 6. Seguridad de los datos (formulario)

Respuestas generales:
- ¿Recopila o comparte datos del usuario? **Sí**.
- ¿Los datos se encriptan en tránsito? **Sí** (todo es HTTPS).
- ¿Los usuarios pueden pedir que se borren? **Sí** (en la app y en `eliminar-cuenta.html`).

| Tipo de dato | ¿Se recopila? | ¿Se comparte? | Opcional | Para qué |
|---|---|---|---|---|
| Ubicación aproximada | Sí | No | Sí (permiso) | Funcionalidad: clima de tu zona y centrar el mapa |
| Ubicación precisa | Sí | No | Sí (permiso) | Funcionalidad: marcar vértices de un lote con el GPS |
| Dirección de correo | Sí | No | No (hace falta para entrar) | Administración de la cuenta |
| Nombre | Sí | No | No (perfil) | Administración de la cuenta; personalización |
| ID de usuario | Sí | No | No | Administración de la cuenta; funcionalidad (sincronizar lotes) |
| Otra información (establecimiento, provincia, localidad, hectáreas) | Sí | No | Localidad y hectáreas sí | Personalización |
| Otro contenido generado por el usuario (lotes: nombre y contorno) | Sí | No | Sí | Funcionalidad: monitoreo satelital y sincronización |
| Otra información (claves de la cuenta de la estación meteorológica: Davis, Ecowitt o Pessl) | Sí | No | Sí | Funcionalidad; quedan cifradas en el servidor y solo se usan para leer esa estación |
| Otro contenido generado por el usuario (estación propia: nombre, ubicación y lecturas) | Sí | No | Sí | Funcionalidad: mostrar los datos de su estación |

Notas para no equivocarse:
- La ubicación se envía a Open-Meteo para pedir el pronóstico y no se guarda en el servidor de GRANEX: es "recopilada" para funcionalidad, procesada de forma efímera.
- Los análisis de suelo, mapas de rinde y dosis se guardan en la cuenta: declararlos como "Otro contenido generado por el usuario" (ya incluido en la fila de lotes).
- No hay analítica, publicidad, ni registros de fallos de terceros (Firebase está desactivado).
- Los proveedores que procesan datos en nombre de GRANEX (Supabase, Copernicus) son "proveedores de servicios", y eso **no** cuenta como compartir.

## 7. Permisos

- **Ubicación (solo en primer plano)**: no requiere formulario especial. Si Play pregunta, explicar: "clima de la ubicación actual y marcar vértices de un lote con el GPS".
- **Notificaciones**: alertas de helada, lluvia, viento, mercado y noticias que el usuario activa en Ajustes.
- No se usa ubicación en segundo plano, ni servicios en primer plano, ni lectura de archivos (los archivos se eligen con el selector del sistema).

## 8. Antes de producción

- [ ] Probar un archivo de prescripción en un monitor real (Cuffia, FGS, Altina o ISO-XML).
- [ ] Desplegar la función `cuenta` de Supabase y probar "Eliminar cuenta" con una cuenta de prueba.
- [ ] Resolver la licencia comercial de Open-Meteo y de los datos de A3 Mercados si la app se cobra.
- [ ] Revisar la política de privacidad y los términos con un abogado.
