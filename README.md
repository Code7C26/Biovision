# GRANEX (Biovision)

> Plataforma agrotecnológica integral diseñada para el productor agrícola, combinando inteligencia artificial (**Agro-AI**), análisis de mercado en tiempo real, monitoreo meteorológico satelital y finanzas del sector agropecuario.

---

## Estado Técnico Actual

La aplicación móvil se encuentra en fase de desarrollo activo y funcional. Integra cinco módulos principales disponibles desde el menú inferior de navegación:

1. **Finanzas & Futuros:** Monitoreo en tiempo real de divisas (Dólar Oficial, Blue, MEP, CCL, Tarjeta) mediante API integrada y cotizaciones del Mercado de Futuros Matba ROFEX (Soja, Maíz, Trigo).
2. **Clima & Agrometeorología:** Búsqueda por localidad con integración de radar y mapa satelital interactivo de precipitaciones vía Windy, junto con variables agrometeorológicas.
3. **Mercado de Granos:** Cotizaciones oficiales de granos (Soja, Maíz, Trigo), gráfico de evolución de precios (USD/Ton) e histórico de cotizaciones en la Bolsa de Comercio de Rosario.
4. **Agro-AI (Diagnóstico con IA):** Asistente inteligente y visión por computadora para el procesamiento de imágenes, identificación fitosanitaria de plagas/enfermedades en cultivos y recomendaciones de cuidado.
5. **Novedades del Sector:** Buscador por palabra clave/categoría y feed de noticias en tiempo real con fuentes agropecuarias destacadas (ej. Clarín Rural) e información climática.

---

## Necesidad a cubrir

Proveer al productor y trabajador agrícola una herramienta centralizada que resuelva tanto el cuidado de sus cultivos (detección de plagas y monitoreo ambiental) como la toma de decisiones financieras e informativas (precios de granos, cotización de divisas, clima local y noticias del sector) para optimizar el tiempo y maximizar la eficiencia en el campo.

---

## Características Principales

* **Agro-AI (Diagnóstico Fitosanitario):** Captura y análisis de fotos de plantas mediante IA para diagnosticar problemas y sugerir tratamientos.
* **Mapa Satelital y Clima:** Visualización de radar de lluvias/truenos por localidad integrando la API de Windy.
* **Panel Financiero & Mercados:**
  * Seguimiento de tipos de cambio oficiales y paralelos en Argentina.
  * Cotizaciones en tiempo real del mercado de futuros Matba ROFEX.
  * Evolución e histórico de precios en pizarra (USD/Ton y ARS/Ton) con simulador integrado.
* **Noticias & Novedades:** Buscador interactivo de artículos y noticias del ámbito agropecuario.

---

## Público Objetivo

Agricultores, productores agropecuarios, ingenieros agrónomos, jardineros y profesionales del sector agroindustrial dedicados a la gestión de campos, cultivos y huertas.

---

## Tecnologías Utilizadas

* **Lenguaje / Entorno:** JavaScript / Node.js / Python 3.10+
* **APIs Integradas:**
  * API de Clima y Radar Satelital (Windy).
  * API de Finanzas y Divisas en Tiempo Real.
  * API de Cotizaciones de Granos (granos.ar / Rosario / Matba ROFEX).
  * API de Noticias e Inteligencia Artificial (Visión por computadora / IA generativa).

---

## Instrucciones de Instalación y Uso

### Instalación
1. Clonar el repositorio localmente:
   ```bash
   git clone <URL_DE_TU_REPOSITORIO>

