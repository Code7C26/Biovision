/*
 * Service worker de GRANEX web. Plantilla: la tarea Gradle distribucionPwa reemplaza __VERSION__ y la
 * lista de precarga con los archivos reales del build (no editar el sw.js generado en build/dist).
 *
 * - App (mismo origen): precargada en la instalación (lo común + la variante Wasm o JS de este
 *   navegador) y servida desde la caché → abre sin conexión.
 *   Las navegaciones van primero a la red para tomar la versión nueva de index.html.
 * - APIs públicas (Open-Meteo, dolarapi, granos.ar): red primero; sin conexión, la última respuesta.
 * - Todo lo demás (teselas del mapa, Windy, sitios externos) pasa sin tocar. Las teselas NO se
 *   interceptan: al volar a otra ciudad Leaflet pide cientos y cancela casi todas; el SW no cancelaba
 *   sus fetch y además recorría la caché en cada guardado, así que las teselas del destino quedaban
 *   en cola y el mapa se veía negro. La caché HTTP del navegador ya las guarda.
 */
'use strict';

const VERSION = '__VERSION__';
const CACHE_APP = 'granex-app-' + VERSION;
const CACHE_DATOS = 'granex-datos-v1';
/** Cachés de versiones anteriores que ya no se usan. */
const CACHES_VIEJAS = ['granex-teselas-v1'];
const MAX_DATOS = 80;

// Cada navegador usa una sola variante (Wasm o JS de respaldo, ~14 MB cada una): se precarga lo común
// más esa variante. Los archivos que no figuran en ninguna lista se guardan recién si se piden.
const PRECARGA_COMUN = ['./', '__PRECARGA_COMUN__'];
const PRECARGA_WASM = ['__PRECARGA_WASM__'];
const PRECARGA_JS = ['__PRECARGA_JS__'];

/**
 * Misma prueba que el webApp.js que genera Compose para elegir variante: un módulo mínimo que usa
 * WasmGC. Si dejaran de coincidir, solo se precargaría la variante equivocada (la app sigue andando
 * y la correcta entra a la caché al usarse).
 */
function soportaWasmGc() {
    try {
        return typeof WebAssembly === 'object' && typeof WebAssembly.validate === 'function' &&
            WebAssembly.validate(new Uint8Array([
                0, 97, 115, 109, 1, 0, 0, 0, 1, 8, 2, 95, 1, 120, 0, 96, 0, 0, 3, 3, 2, 1, 1, 10,
                14, 2, 6, 0, 6, 64, 25, 11, 11, 5, 0, 208, 112, 26, 11, 0, 45, 4, 110, 97, 109, 101,
                1, 15, 2, 0, 5, 102, 117, 110, 99, 48, 1, 5, 102, 117, 110, 99, 49, 4, 8, 1, 0, 5,
                116, 121, 112, 101, 48, 10, 11, 1, 0, 1, 0, 6, 102, 105, 101, 108, 100, 48
            ]));
    } catch (e) {
        return false;
    }
}
const APIS = ['api.open-meteo.com', 'geocoding-api.open-meteo.com', 'dolarapi.com', 'granosar.lfcaucino.workers.dev'];

self.addEventListener('install', function (evento) {
    // cache: 'reload' saltea la caché HTTP: webApp.js no cambia de nombre entre versiones y no debe
    // quedar uno viejo junto a los .wasm nuevos
    evento.waitUntil(
        caches.open(CACHE_APP)
            .then(function (cache) {
                const lista = PRECARGA_COMUN.concat(soportaWasmGc() ? PRECARGA_WASM : PRECARGA_JS);
                return cache.addAll(lista.map(function (ruta) { return new Request(ruta, { cache: 'reload' }); }));
            })
            .then(function () { return self.skipWaiting(); })
    );
});

self.addEventListener('activate', function (evento) {
    evento.waitUntil(
        caches.keys()
            .then(function (nombres) {
                return Promise.all(nombres
                    .filter(function (n) {
                        return (n.indexOf('granex-app-') === 0 && n !== CACHE_APP) || CACHES_VIEJAS.indexOf(n) >= 0;
                    })
                    .map(function (n) { return caches.delete(n); }));
            })
            .then(function () { return self.clients.claim(); })
    );
});

self.addEventListener('fetch', function (evento) {
    const pedido = evento.request;
    if (pedido.method !== 'GET') return;
    const url = new URL(pedido.url);

    if (url.origin === self.location.origin) {
        if (pedido.mode === 'navigate') {
            evento.respondWith(redPrimero(pedido, CACHE_APP, 0, './'));
        } else {
            evento.respondWith(cachePrimero(pedido, CACHE_APP, 0));
        }
    } else if (APIS.indexOf(url.hostname) >= 0) {
        evento.respondWith(redPrimero(pedido, CACHE_DATOS, MAX_DATOS, null));
    }
});

/** Las respuestas opacas (no-cors) ocupan varios MB de cuota cada una: no se guardan. */
function guardable(respuesta) {
    return respuesta && respuesta.ok && respuesta.type !== 'opaque';
}

function redPrimero(pedido, nombreCache, maximo, respaldo) {
    return fetch(pedido)
        .then(function (respuesta) {
            if (guardable(respuesta)) guardar(nombreCache, pedido, respuesta.clone(), maximo);
            return respuesta;
        })
        .catch(function (error) {
            return caches.match(pedido).then(function (enCache) {
                if (enCache) return enCache;
                if (respaldo) return caches.match(respaldo).then(function (r) { if (r) return r; throw error; });
                throw error;
            });
        });
}

function cachePrimero(pedido, nombreCache, maximo) {
    return caches.match(pedido).then(function (enCache) {
        if (enCache) return enCache;
        return fetch(pedido).then(function (respuesta) {
            if (guardable(respuesta)) guardar(nombreCache, pedido, respuesta.clone(), maximo);
            return respuesta;
        });
    });
}

function guardar(nombreCache, pedido, respuesta, maximo) {
    return caches.open(nombreCache).then(function (cache) {
        return cache.put(pedido, respuesta).then(function () {
            if (!maximo) return;
            return cache.keys().then(function (claves) {
                // Las claves salen en orden de inserción: se borran las más viejas
                const sobran = claves.length - maximo;
                for (let i = 0; i < sobran; i++) cache.delete(claves[i]);
            });
        });
    }).catch(function () { /* cuota llena o modo privado: se sigue sin caché */ });
}
