# GRANEX (Biovision)

> Plataforma agrotecnológica integral diseñada para el productor agrícola: cotizaciones y futuros, pizarra de Rosario, clima agro, monitoreo satelital de lotes, prescripción de dosis variable, Agenda de gastos e insumos y noticias del campo.

**Usala en: https://granex-4a7db.web.app/**

---

## Estado Técnico Actual

GRANEX está en desarrollo activo. La versión web (PWA) y la aplicación Android comparten la misma lógica y el mismo diseño, y se sincronizan con una cuenta de usuario.

La app tiene cinco pestañas principales:

1. **Inicio:** resumen del día con el clima, si se puede pulverizar, los precios de la pizarra, tus lotes, la Agenda y las últimas noticias.
2. **Clima:** clima de ahora, pronóstico por hora y a 7 días, mapa de radar de lluvia y viento, ventanas de pulverización, riesgo de helada y balance hídrico. Si tenés una estación meteorológica propia, también la muestra.
3. **Campo:** dibujá tus lotes o detectalos con un toque sobre el mapa. Mirá el vigor con imágenes Sentinel-2 de 10 m (NDVI, NDRE, GNDVI), el relieve del lote y la comparación entre fechas. Desde ahí también se arma la prescripción de dosis variable para los monitores Cuffia, FGS, Altina e ISO-XML.
4. **Mercado:** pizarra de Rosario con el historial de precios, la calculadora de margen y, en la misma pestaña, el dólar (oficial, mayorista, MEP, CCL, blue, cripto y tarjeta) y los futuros de A3 Mercados (MATba ROFEX).
5. **Agenda:** gastos de la campaña, insumos y stock, con el costo por lote en dólares por hectárea. Se sincroniza con tu cuenta.

Además, **Noticias** reúne las notas de los principales medios agropecuarios del país, filtradas por tema.

---

## Necesidad a cubrir

Dar al productor una herramienta centralizada que resuelva dos cosas: el seguimiento de sus lotes (vigor, relieve, prescripción de insumos) y la toma de decisiones diarias (clima, precios de granos, cotización de divisas, costos y noticias del sector), para ahorrar tiempo y mejorar la eficiencia en el campo.

---

## Características Principales

* **Clima agro:** pronóstico, ventanas de pulverización, riesgo de helada y balance hídrico, con el modelo ECMWF y datos de Open-Meteo.
* **Monitoreo satelital:** lotes dibujados a mano o detectados automáticamente, índices de vegetación Sentinel-2, foto del día y relieve del terreno (Copernicus DEM).
* **Dosis variable:** zonas de manejo a partir de varias campañas, del relieve o de mapas de rinde; análisis de suelo por zona y dosis sugeridas, siempre editables, con exportación para los monitores.
* **Mercado y finanzas:**
  * Pizarra de Rosario y su historial, con gráficos por semana, mes, trimestre y año.
  * Calculadora de margen bruto y neto por hectárea, con derechos de exportación, impuestos provinciales y rinde de indiferencia.
  * Cotizaciones del dólar y futuros de A3 Mercados (cierres de rueda, no tiempo real).
* **Agenda de campaña:** gastos con su estado de pago, stock de insumos y costo por lote, con copia en la nube.
* **Estaciones propias:** conexión de estaciones de Davis, Ecowitt y Pessl, o envío directo desde la estación.
* **Noticias:** notas de los medios agropecuarios, leídas de sus feeds RSS oficiales.

---

## Público Objetivo

Productores agropecuarios, ingenieros agrónomos y profesionales del sector agroindustrial dedicados a la gestión de campos y cultivos.

---

## Tecnologías Utilizadas

* **Android:** Kotlin, Jetpack Compose, Material 3, Room y WorkManager.
* **Web:** Compose Multiplatform (Wasm y JS) como PWA.
* **Lógica compartida:** Kotlin Multiplatform (`shared`), sin interfaz, con los cálculos, los clientes HTTP y el backend.
* **Backend:** Supabase (cuentas, sincronización de lotes y Agenda, almacenamiento) con funciones de Edge para imágenes Sentinel-2, estaciones y noticias.
* **APIs externas:** Open-Meteo (clima), Windy (mapa de radar), dolarapi, granos.ar y Cámara Arbitral de Cereales (pizarra), A3 Mercados (futuros), Copernicus Data Space (imágenes satelitales y relieve) y Photon / Nominatim (búsqueda de localidades).

---

## Desarrollo

El código de la aplicación está en la carpeta `src/`. Para compilar, desde `src/`:

```bash
./gradlew :app:assembleDebug        # app Android (APK de prueba)
./gradlew :app:testDebugUnitTest    # tests de la app
./gradlew :shared:allTests          # tests de la lógica compartida
./gradlew :webApp:distribucionPwa   # versión web
```

Requisitos: JDK 21 y Android SDK (API 36). La conexión al backend se configura en `local.properties` con `supabase.url` y `supabase.anonKey`. Sin esos valores, la app funciona en modo local. Las claves secretas nunca van en el repositorio.

La documentación técnica está en `src/docs/`.

---

## Privacidad

- [Política de privacidad](https://granex-4a7db.web.app/privacidad.html)
- [Términos y condiciones](https://granex-4a7db.web.app/terminos.html)
- [Eliminar la cuenta](https://granex-4a7db.web.app/eliminar-cuenta.html)

Contacto: supportgranex@gmail.com