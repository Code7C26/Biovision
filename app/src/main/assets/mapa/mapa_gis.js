(function () {
    'use strict';

    // ---------- Comunicación con la app ----------
    // Android: WebView con el puente GranexPuente. Web (webApp): iframe del mismo origen que la app,
    // que se comunica por postMessage. Con GranexPuente presente la página se comporta como siempre.
    var EMBEBIDO_WEB = !window.GranexPuente && window.parent !== window;

    function enviar(mensaje) {
        try {
            if (window.GranexPuente) window.GranexPuente.postMessage(JSON.stringify(mensaje));
            else if (EMBEBIDO_WEB) window.parent.postMessage(JSON.stringify(mensaje), location.origin);
        } catch (e) { /* la app no está escuchando */ }
    }

    var ELEVACION_PX = 44;        // al arrastrar con el dedo, el vértice queda por encima para no taparlo
    var UMBRAL_ARRASTRE_PX = 6;
    var INTERVALO_ENVIO_MS = 80;  // ~12 actualizaciones de superficie por segundo durante el arrastre
    var RESOLUCION_M = 10;        // tamaño de píxel de Sentinel-2
    var MAX_PIXELES = 512;

    var contenedor = document.getElementById('map');
    var map = L.map(contenedor, {
        zoomControl: false,
        attributionControl: true,
        minZoom: 3,
        maxZoom: 20,
        bounceAtZoomLimits: false
    }).setView([-33.89, -60.57], 13);
    map.attributionControl.setPrefix(false);

    // Orden de capas: imagen (200) < espectral < etiquetas < contornos (400) < edición
    function crearPanel(nombre, z) {
        var panel = map.createPane(nombre);
        panel.style.zIndex = z;
        panel.style.pointerEvents = 'none';
    }
    crearPanel('fondo', 250);
    crearPanel('espectral', 350);
    crearPanel('etiquetas', 380);
    crearPanel('edicion', 650);

    // ---------- Mapa base ----------
    var ATRIBUCION = {
        maptiler: '© MapTiler © colaboradores de OpenStreetMap',
        esri: 'Imágenes © Esri, Maxar, Earthstar Geographics',
        esriEtiquetas: 'Etiquetas © Esri',
        osm: '© colaboradores de OpenStreetMap'
    };
    var ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services/';
    var capaBase = null, capaEtiquetas = null, idBase = null;

    function setMapaBase(proveedor, clave, etiquetas) {
        if (proveedor === 'maptiler' && !clave) proveedor = 'esri';
        var id = proveedor + '|' + (etiquetas ? 1 : 0);
        if (id === idBase) return;
        idBase = id;
        if (capaBase) map.removeLayer(capaBase);
        if (capaEtiquetas) map.removeLayer(capaEtiquetas);
        capaEtiquetas = null;

        // updateWhenZooming: false evita pedir teselas intermedias durante el pellizco
        // En Android las sirve TeselasMapa con su propia caché; en la web, la caché HTTP del navegador
        // (el service worker no las intercepta: ver webApp/pwa/sw.js)
        var comunes = { maxZoom: 20, updateWhenZooming: false, keepBuffer: 3 };
        if (proveedor === 'maptiler') {
            // MapTiler entrega teselas de 512 px: con zoomOffset -1 se piden 4 veces menos imágenes
            capaBase = L.tileLayer('https://api.maptiler.com/maps/' + (etiquetas ? 'hybrid' : 'satellite') +
                '/{z}/{x}/{y}.jpg?key=' + encodeURIComponent(clave),
                L.extend({}, comunes, { tileSize: 512, zoomOffset: -1, minZoom: 1, maxNativeZoom: 19, attribution: ATRIBUCION.maptiler }));
        } else if (proveedor === 'osm') {
            capaBase = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                L.extend({}, comunes, { maxNativeZoom: 19, attribution: ATRIBUCION.osm }));
        } else {
            // En zonas rurales la imagen de Esri llega a zoom 18 (0,5 m); del 19 en adelante devuelve un
            // cartel de "sin datos", así que más cerca se amplía la de 18. En pantallas de alta densidad
            // (detectRetina) se pide un nivel más que el del mapa: con teselas de 256 px a 2,6 puntos de
            // pantalla por píxel la imagen se veía borrosa. maxNativeZoom 17 + 1 = nunca pasa de 18.
            capaBase = L.tileLayer(ESRI + 'World_Imagery/MapServer/tile/{z}/{y}/{x}',
                L.extend({}, comunes, {
                    detectRetina: true,
                    maxNativeZoom: L.Browser.retina ? 17 : 18,
                    attribution: ATRIBUCION.esri
                }));
            if (etiquetas) {
                var opciones = L.extend({}, comunes, { pane: 'etiquetas', maxNativeZoom: 18 });
                capaEtiquetas = L.layerGroup([
                    L.tileLayer(ESRI + 'Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}',
                        L.extend({}, opciones, { attribution: ATRIBUCION.esriEtiquetas })),
                    L.tileLayer(ESRI + 'Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', opciones)
                ]).addTo(map);
            }
        }
        capaBase.addTo(map);
        avisarPintado();
    }

    // ---------- Lotes ----------
    var grupoLotes = L.layerGroup().addTo(map);
    var lotes = [], seleccionadoId = null, editandoId = null, ultimoEncuadre = null;

    function loteSeleccionado() {
        for (var i = 0; i < lotes.length; i++) if (lotes[i].id === seleccionadoId) return lotes[i];
        return null;
    }

    function encuadrar(coords) {
        if (coords.length < 2) return;
        map.fitBounds(L.latLngBounds(coords), { padding: [40, 40], maxZoom: 17 });
    }

    function setLotes(json) {
        var datos = JSON.parse(json);
        lotes = datos.lotes || [];
        seleccionadoId = datos.seleccionado;
        editandoId = datos.editando || null;
        dibujarLotes();
        var sel = loteSeleccionado();
        // Solo se reencuadra al cambiar de lote: volver a la pestaña conserva la vista del usuario
        if (sel && sel.id !== ultimoEncuadre && !edicion) {
            ultimoEncuadre = sel.id;
            encuadrar(sel.coords);
        }
        actualizarEspectral();
        avisarPintado();
    }

    function dibujarLotes() {
        grupoLotes.clearLayers();
        lotes.forEach(function (lote) {
            if (lote.id === editandoId && edicion) return;
            var sel = lote.id === seleccionadoId;
            var poligono = L.polygon(lote.coords, {
                color: sel ? '#00e676' : '#ffffff',
                weight: sel ? 2.5 : 1.5,
                opacity: sel ? 1 : 0.75,
                dashArray: sel ? null : '5 5',
                fill: true,
                fillOpacity: 0,
                interactive: !edicion
            });
            poligono.on('click', function (e) {
                L.DomEvent.stopPropagation(e);
                if (edicion) return;
                // Detección: tocar sobre un lote ya guardado también busca el contorno de ahí
                if (deteccion) {
                    enviar({ t: 'deteccion', lat: e.latlng.lat, lon: e.latlng.lng });
                    return;
                }
                // Tocar otro lote lo elige; tocar el elegido muestra el valor del píxel del escaneo
                if (lote.id !== seleccionadoId) enviar({ t: 'loteTocado', id: lote.id });
                else mostrarValor(e.latlng);
            });
            grupoLotes.addLayer(poligono);
            if (sel && !edicion) {
                // textContent: el nombre lo escribe el usuario y no debe interpretarse como HTML
                var etiqueta = document.createElement('div');
                etiqueta.textContent = lote.nombre;
                var area = document.createElement('span');
                area.textContent = lote.area;
                etiqueta.appendChild(area);
                grupoLotes.addLayer(L.marker(poligono.getBounds().getCenter(), {
                    icon: L.divIcon({ className: 'etiqueta-lote', html: etiqueta, iconSize: null }),
                    interactive: false,
                    keyboard: false
                }));
            }
        });
    }

    // ---------- Capa de índices (Sentinel-2, 10 m) ----------
    // La app envía la grilla del escaneo elegido (en grados, fila 0 = norte) con el valor de cada píxel.
    // Se dibuja una sola vez como imagen: al mover o hacer zoom el navegador solo la escala.
    // Sentinel-2 (10 m): escalas continuas, de [escala.min] a [escala.max] (la app las elige)
    var RAMPAS = {
        // Rojo (bajo) → amarillo → verde (alto), como RdYlGn
        indice: ['#a50026', '#d73027', '#f46d43', '#fdae61', '#fee08b', '#d9ef8b', '#a6d96a', '#66bd63', '#1a9850', '#006837'],
        // Cambio entre fechas: rojo (bajó) → blanco (igual) → verde (subió)
        cambio: ['#b2182b', '#ef8a62', '#fddbc7', '#f7f7f7', '#d9f0d3', '#7fbf7b', '#1b7837'],
        // Relieve respecto de la altura típica: azul (bajo, junta agua) → blanco → marrón (loma)
        relieve: ['#2166ac', '#4393c3', '#92c5de', '#d1e5f0', '#f7f7f7', '#f6e8c3', '#dfc27d', '#bf812d', '#8c510a']
    };
    Object.keys(RAMPAS).forEach(function (k) {
        RAMPAS[k] = RAMPAS[k].map(function (c) {
            var h = parseInt(c.slice(1), 16);
            return [(h >> 16) & 255, (h >> 8) & 255, h & 255];
        });
    });

    function colorContinuo(rampa, escala, valor) {
        var t = (valor - escala.min) / (escala.max - escala.min);
        t = Math.max(0, Math.min(1, isFinite(t) ? t : 0.5)) * (rampa.length - 1);
        var i = Math.min(rampa.length - 2, Math.floor(t)), f = t - i;
        var a = rampa[i], b = rampa[i + 1];
        return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, 255];
    }

    var datosCapa = null, opacidad = 0.85;
    var espectral = null, espectralUrl = null, claveEspectral = null, tokenEspectral = 0;

    function setCapa(json) {
        datosCapa = JSON.parse(json);
        // El valor tocado era de la capa anterior (otra fecha, índice o la comparación)
        map.closePopup();
        actualizarEspectral();
    }

    function setOpacidad(valor) {
        opacidad = valor;
        if (espectral) espectral.setOpacity(valor);
        avisarPintado();
    }

    function quitarEspectral() {
        tokenEspectral++;
        claveEspectral = null;
        if (espectral) map.removeLayer(espectral);
        if (espectralUrl) URL.revokeObjectURL(espectralUrl);
        espectral = null;
        espectralUrl = null;
    }

    function actualizarEspectral() {
        var lote = loteSeleccionado();
        if (!lote || edicion || zonasActivas || !datosCapa || datosCapa.sinDatos || !(datosCapa.geo || datosCapa.imagen)) {
            quitarEspectral();
            avisarPintado();
            return;
        }
        var escala = datosCapa.escala || {};
        var clave = lote.id + '|' + datosCapa.id + '|' + escala.min + '|' + escala.max + '|' + JSON.stringify(lote.coords);
        if (clave === claveEspectral) return;
        claveEspectral = clave;
        var token = ++tokenEspectral;
        obtenerImagen(datosCapa, function (url, limites) {
            if (token !== tokenEspectral) {
                URL.revokeObjectURL(url);
                return;
            }
            var nueva = L.imageOverlay(url, limites, {
                pane: 'espectral',
                opacity: opacidad,
                className: 'capa-espectral',
                interactive: false
            });
            // La imagen anterior se quita recién cuando la nueva cargó: sin parpadeo ni hueco
            nueva.once('load', function () {
                if (token !== tokenEspectral) {
                    map.removeLayer(nueva);
                    URL.revokeObjectURL(url);
                    return;
                }
                var anterior = espectral, urlAnterior = espectralUrl;
                espectral = nueva;
                espectralUrl = url;
                if (anterior) map.removeLayer(anterior);
                if (urlAnterior) URL.revokeObjectURL(urlAnterior);
                avisarPintado();
            });
            nueva.addTo(map);
        });
    }

    // Fondo: la foto Sentinel de la fecha elegida tapa el mapa base alrededor del lote. El mosaico del
    // proveedor puede tener más de un año y no coincidía con la fecha del escaneo
    var fondo = null, claveFondo = null;

    function setFondo(json) {
        var datos = json ? JSON.parse(json) : null;
        var clave = datos ? datos.id + '|' + JSON.stringify(datos.limites) : null;
        if (clave === claveFondo) return;
        claveFondo = clave;
        if (!datos) {
            if (fondo) map.removeLayer(fondo);
            fondo = null;
            avisarPintado();
            return;
        }
        var l = datos.limites;
        var nuevo = L.imageOverlay(datos.imagen, [[l.sur, l.oeste], [l.norte, l.este]], {
            pane: 'fondo',
            interactive: false
        });
        nuevo.once('load', function () {
            if (clave !== claveFondo) {
                map.removeLayer(nuevo);
                return;
            }
            var anterior = fondo;
            fondo = nuevo;
            if (anterior) map.removeLayer(anterior);
            avisarPintado();
        });
        nuevo.addTo(map);
    }

    // La foto del día llega ya hecha (PNG en data:, con sus límites); el resto es una grilla de valores
    function obtenerImagen(datos, listo) {
        if (datos.imagen) {
            var l = datos.limites;
            listo(datos.imagen, [[l.sur, l.oeste], [l.norte, l.este]]);
        } else {
            generarImagenGeo(datos, listo);
        }
    }

    // Grilla Sentinel-2 en grados (fila 0 = norte), un valor por píxel del satélite (10 m). Los valores
    // fuera del lote o con nubes llegan como null. Se dibuja suavizada (interpolación bilineal) a
    // varios subpíxeles por píxel y recortada con el contorno del lote: sin escalones de 10 m ni borde
    // serruchado. Es solo el dibujo: tocar el lote muestra el valor del píxel real, y las hectáreas se
    // cuentan sobre los píxeles reales.
    function generarImagenGeo(datos, listo) {
        var g = datos.geo;
        var rampa = RAMPAS[datos.rampa] || RAMPAS.indice;
        var lote = loteSeleccionado();
        var contorno = lote ? lote.coords : null;
        var n = g.columnas * g.filas;
        var colores = new Array(n);
        for (var i = 0; i < n; i++) {
            var v = datos.valores[i];
            colores[i] = (v === null || v === undefined) ? null : colorContinuo(rampa, datos.escala, v);
        }
        if (contorno) extenderFueraDelLote(colores, g, contorno);

        var base = document.createElement('canvas');
        base.width = g.columnas; base.height = g.filas;
        var ctx = base.getContext('2d');
        var imagen = ctx.createImageData(g.columnas, g.filas);
        var px = imagen.data;
        for (var j = 0; j < n; j++) {
            var c = colores[j];
            if (!c) continue;
            var k = j * 4;
            px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = 255;
        }
        ctx.putImageData(imagen, 0, 0);

        // Hasta 8 subpíxeles por píxel (1,25 m), sin pasar de 2048 px por lado en lotes grandes
        var factor = Math.max(1, Math.min(8, Math.floor(2048 / Math.max(g.columnas, g.filas))));
        var lienzo = document.createElement('canvas');
        lienzo.width = g.columnas * factor; lienzo.height = g.filas * factor;
        var cg = lienzo.getContext('2d');
        if (contorno) {
            cg.beginPath();
            contorno.forEach(function (p, idx) {
                var x = (p[1] - g.oeste) / g.pasoLon * factor, y = (g.norte - p[0]) / g.pasoLat * factor;
                if (idx === 0) cg.moveTo(x, y); else cg.lineTo(x, y);
            });
            cg.closePath();
            cg.clip();
        }
        cg.imageSmoothingEnabled = true;
        cg.imageSmoothingQuality = 'high';
        cg.drawImage(base, 0, 0, lienzo.width, lienzo.height);

        var sur = g.norte - g.filas * g.pasoLat, este = g.oeste + g.columnas * g.pasoLon;
        lienzo.toBlob(function (blob) {
            if (blob) listo(URL.createObjectURL(blob), [[sur, g.oeste], [g.norte, este]]);
        }, 'image/png');
    }

    // Los píxeles sin dato con el centro fuera del lote toman el color de sus vecinos (dos vueltas):
    // así el suavizado no se desvanece en el borde, que después recorta el contorno. Los huecos de
    // nubes adentro del lote quedan sin color.
    function extenderFueraDelLote(colores, g, contorno) {
        var afuera = new Array(colores.length);
        for (var f = 0; f < g.filas; f++) {
            for (var c = 0; c < g.columnas; c++) {
                var lat = g.norte - (f + 0.5) * g.pasoLat, lon = g.oeste + (c + 0.5) * g.pasoLon;
                afuera[f * g.columnas + c] = !puntoEnPoligono(lat, lon, contorno);
            }
        }
        for (var vuelta = 0; vuelta < 2; vuelta++) {
            var nuevos = [];
            for (var i = 0; i < colores.length; i++) {
                if (colores[i] || !afuera[i]) continue;
                var fila = Math.floor(i / g.columnas), col = i % g.columnas;
                var suma = [0, 0, 0], cuenta = 0;
                for (var df = -1; df <= 1; df++) {
                    for (var dc = -1; dc <= 1; dc++) {
                        var ff = fila + df, cc = col + dc;
                        if (ff < 0 || ff >= g.filas || cc < 0 || cc >= g.columnas) continue;
                        var vecino = colores[ff * g.columnas + cc];
                        if (!vecino) continue;
                        suma[0] += vecino[0]; suma[1] += vecino[1]; suma[2] += vecino[2];
                        cuenta++;
                    }
                }
                if (cuenta) nuevos.push([i, [suma[0] / cuenta, suma[1] / cuenta, suma[2] / cuenta]]);
            }
            nuevos.forEach(function (par) { colores[par[0]] = par[1]; });
        }
    }

    // Punto en polígono (lat/lon, contorno [[lat, lon], ...]) por cruce de rayos
    function puntoEnPoligono(lat, lon, contorno) {
        var dentro = false;
        for (var i = 0, j = contorno.length - 1; i < contorno.length; j = i++) {
            var yi = contorno[i][0], xi = contorno[i][1], yj = contorno[j][0], xj = contorno[j][1];
            if ((yi > lat) !== (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) dentro = !dentro;
        }
        return dentro;
    }

    // Valor del píxel tocado (solo grilla Sentinel-2)
    function valorEn(lat, lon) {
        if (!datosCapa || !datosCapa.geo) return null;
        var g = datosCapa.geo;
        var fila = Math.floor((g.norte - lat) / g.pasoLat), col = Math.floor((lon - g.oeste) / g.pasoLon);
        if (fila < 0 || fila >= g.filas || col < 0 || col >= g.columnas) return null;
        var v = datosCapa.valores[fila * g.columnas + col];
        return v === null || v === undefined ? null : v;
    }

    function mostrarValor(latlng) {
        var v = valorEn(latlng.lat, latlng.lng);
        if (v === null) return false;
        var decimales = datosCapa.decimales != null ? datosCapa.decimales : 2;
        var texto = (datosCapa.signo && v > 0 ? '+' : '') + v.toFixed(decimales).replace('.', ',') +
            (datosCapa.unidad ? ' ' + datosCapa.unidad : '');
        var caja = document.createElement('div');
        caja.className = 'valor-tocado';
        var titulo = document.createElement('b');
        titulo.textContent = datosCapa.etiqueta + ': ' + texto;
        var pie = document.createElement('div');
        pie.textContent = datosCapa.fecha || '';
        caja.appendChild(titulo);
        caja.appendChild(pie);
        L.popup({ closeButton: false, autoPan: false, className: 'popup-valor' }).setLatLng(latlng).setContent(caja).openOn(map);
        avisarPintado();
        return true;
    }

    // Avisa a la app cuando el cambio ya está en pantalla (dos cuadros después) para que fuerce
    // el redibujado de la WebView aunque nadie toque el mapa
    var avisoPendiente = false;
    function avisarPintado() {
        if (avisoPendiente) return;
        avisoPendiente = true;
        requestAnimationFrame(function () {
            requestAnimationFrame(function () {
                avisoPendiente = false;
                enviar({ t: 'pintado' });
            });
        });
    }

    // ---------- Edición de lotes ----------
    var edicion = null; // { vertices: [[lat, lon]], seleccionado: índice o null, mira: bool }
    var grupoEdicion = L.layerGroup().addTo(map);
    var poligonoEdicion = null;
    var ESTILO_POLIGONO = { pane: 'edicion', color: '#ff9800', weight: 2.5, fillColor: '#00e676', fillOpacity: 0.22, interactive: false };
    var ESTILO_LINEA = { pane: 'edicion', color: '#ff9800', weight: 3, interactive: false };

    function setEdicion(json) {
        var datos = json ? JSON.parse(json) : null;
        var empieza = !edicion && datos;
        var termina = edicion && !datos;
        edicion = datos;
        contenedor.classList.remove('arrastrando');
        if (empieza || termina) {
            // La barra de edición tapa la parte inferior del mapa: la atribución sube mientras se edita
            map.attributionControl.setPosition(datos ? 'topleft' : 'bottomright');
            // El doble toque haría zoom y además agregaría dos vértices
            if (datos) map.doubleClickZoom.disable(); else map.doubleClickZoom.enable();
            dibujarLotes();
            actualizarEspectral();
        }
        if (empieza) {
            if (datos.vertices.length >= 2) encuadrar(datos.vertices);
            else if (map.getZoom() < 16) map.setZoom(17);
        }
        dibujarEdicion();
        avisarPintado();
    }

    function dibujarEdicion() {
        grupoEdicion.clearLayers();
        poligonoEdicion = null;
        if (!edicion) return;
        var v = edicion.vertices, n = v.length;
        if (n >= 3) poligonoEdicion = L.polygon(v, ESTILO_POLIGONO);
        else if (n === 2) poligonoEdicion = L.polyline(v, ESTILO_LINEA);
        if (poligonoEdicion) grupoEdicion.addLayer(poligonoEdicion);

        // Punto intermedio de cada lado: arrastrarlo inserta un vértice en ese lado
        if (n >= 2) {
            var lados = n >= 3 ? n : 1;
            for (var i = 0; i < lados; i++) {
                var a = v[i], b = v[(i + 1) % n];
                agregarManija([(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], 'medio', i + 1, true);
            }
        }
        v.forEach(function (p, i) {
            var clases = 'vertice';
            if (i === 0) clases += ' primero';
            if (i === 0 && n >= 3 && edicion.seleccionado == null && !edicion.mira) clases += ' cerrable';
            if (i === edicion.seleccionado) clases += ' seleccionado';
            agregarManija(p, clases, i, false);
        });
    }

    function agregarManija(latlng, clases, indice, insercion) {
        var marcador = L.marker(latlng, {
            pane: 'edicion',
            icon: L.divIcon({ className: clases, html: '<span></span>', iconSize: [40, 40] }),
            interactive: false,
            keyboard: false
        });
        grupoEdicion.addLayer(marcador);
        habilitarArrastre(marcador, indice, insercion);
    }

    function vistaPrevia(indice, insercion, latlng) {
        var v = edicion.vertices.slice();
        var p = [latlng.lat, latlng.lng];
        if (insercion) v.splice(indice, 0, p); else v[indice] = p;
        if (v.length >= 3 && !(poligonoEdicion instanceof L.Polygon)) {
            if (poligonoEdicion) grupoEdicion.removeLayer(poligonoEdicion);
            poligonoEdicion = L.polygon(v, ESTILO_POLIGONO);
            grupoEdicion.addLayer(poligonoEdicion);
        } else if (poligonoEdicion) {
            poligonoEdicion.setLatLngs(v);
        }
    }

    function habilitarArrastre(marcador, indice, insercion) {
        var el = marcador.getElement();
        if (!el) return;
        L.DomEvent.disableClickPropagation(el);
        el.addEventListener('pointerdown', function (inicio) {
            if (!edicion || (inicio.button !== undefined && inicio.button > 0)) return;
            inicio.preventDefault();
            inicio.stopPropagation();
            var movido = false, ultimo = null, ultimoEnvio = 0;
            var elevacion = inicio.pointerType === 'touch' ? ELEVACION_PX : 0;
            try { el.setPointerCapture(inicio.pointerId); } catch (e) { /* sin captura */ }
            map.dragging.disable();
            el.classList.add('activo');

            function mover(e) {
                if (e.pointerId !== inicio.pointerId) return;
                if (!movido && Math.abs(e.clientX - inicio.clientX) + Math.abs(e.clientY - inicio.clientY) < UMBRAL_ARRASTRE_PX) return;
                if (!movido) {
                    movido = true;
                    contenedor.classList.add('arrastrando');
                }
                ultimo = map.containerPointToLatLng(map.mouseEventToContainerPoint(e).subtract([0, elevacion]));
                marcador.setLatLng(ultimo);
                vistaPrevia(indice, insercion, ultimo);
                var ahora = Date.now();
                if (ahora - ultimoEnvio > INTERVALO_ENVIO_MS) {
                    ultimoEnvio = ahora;
                    enviar({ t: 'arrastrando', i: indice, lat: ultimo.lat, lon: ultimo.lng, ins: insercion });
                }
            }

            function soltar(e) {
                if (e.pointerId !== inicio.pointerId) return;
                el.removeEventListener('pointermove', mover);
                el.removeEventListener('pointerup', soltar);
                el.removeEventListener('pointercancel', soltar);
                map.dragging.enable();
                el.classList.remove('activo');
                if (movido && ultimo && e.type === 'pointerup') {
                    enviar({ t: 'verticeSoltado', i: indice, lat: ultimo.lat, lon: ultimo.lng, ins: insercion });
                } else if (movido) {
                    dibujarEdicion(); // arrastre cancelado: vuelve a la posición confirmada
                } else if (!insercion) {
                    enviar({ t: 'verticeTocado', i: indice });
                }
            }

            el.addEventListener('pointermove', mover);
            el.addEventListener('pointerup', soltar);
            el.addEventListener('pointercancel', soltar);
        });
    }

    map.on('click', function (e) {
        if (deteccion && !edicion) enviar({ t: 'deteccion', lat: e.latlng.lat, lon: e.latlng.lng });
        else if (edicion && !edicion.mira) enviar({ t: 'toque', lat: e.latlng.lat, lon: e.latlng.lng });
        else if (!edicion) mostrarValor(e.latlng);
    });

    // ---------- Detección automática de lotes ----------
    // activo: el próximo toque se envía como 'deteccion'. Con lat/lon se marca el punto buscado.
    var deteccion = false, marcaDeteccion = null;
    function setDeteccion(activo, lat, lon) {
        deteccion = !!activo;
        if (marcaDeteccion) {
            map.removeLayer(marcaDeteccion);
            marcaDeteccion = null;
        }
        if (typeof lat === 'number' && typeof lon === 'number') {
            marcaDeteccion = L.circleMarker([lat, lon], {
                radius: 9, color: '#ffffff', weight: 3, fillColor: '#00e676', fillOpacity: 0.9, interactive: false
            }).addTo(map);
        }
        map.getContainer().style.cursor = deteccion ? 'crosshair' : '';
        avisarPintado();
    }

    function informarCentro() {
        var c = map.getCenter();
        enviar({ t: 'centro', lat: c.lat, lon: c.lng });
    }
    map.on('moveend', informarCentro);
    map.on('moveend zoomend', avisarPintado);

    function irA(lat, lon) {
        map.flyTo([lat, lon], Math.max(map.getZoom(), 15), { duration: 1.2 });
    }

    // ---------- Zonas de manejo (prescripción) ----------
    var capaZonas = null, zonasActivas = false;

    function setZonas(json) {
        var datos = json ? JSON.parse(json) : null;
        if (capaZonas) map.removeLayer(capaZonas);
        capaZonas = null;
        var antes = zonasActivas;
        zonasActivas = !!(datos && datos.features && datos.features.length);
        if (zonasActivas) {
            capaZonas = L.geoJSON(datos, {
                interactive: false,
                style: function (f) {
                    return { color: '#111614', weight: 0.8, opacity: 0.9, fillColor: f.properties.color, fillOpacity: 0.62 };
                }
            }).addTo(map);
            if (!antes) map.fitBounds(capaZonas.getBounds(), { padding: [16, 16], maxZoom: 17 });
        }
        if (antes !== zonasActivas) {
            actualizarEspectral();
            dibujarLotes();
        }
        avisarPintado();
    }

    // Un error en una función no debe dejar al resto del mapa sin responder
    function seguro(fn) {
        return function () {
            try {
                return fn.apply(null, arguments);
            } catch (e) {
                console.error('GRANEX mapa:', e);
            }
        };
    }

    window.GRANEX = {
        setMapaBase: seguro(setMapaBase),
        setLotes: seguro(setLotes),
        setCapa: seguro(setCapa),
        setFondo: seguro(setFondo),
        setOpacidad: seguro(setOpacidad),
        setEdicion: seguro(setEdicion),
        setZonas: seguro(setZonas),
        setDeteccion: seguro(setDeteccion),
        irA: seguro(irA)
    };

    // Web: la app llama a GRANEX.<metodo>(...args) con postMessage({metodo, args}). Solo se aceptan
    // mensajes de la página que contiene al iframe y del mismo origen.
    if (EMBEBIDO_WEB) {
        window.addEventListener('message', function (e) {
            if (e.source !== window.parent || e.origin !== location.origin || typeof e.data !== 'string') return;
            try {
                var m = JSON.parse(e.data);
                if (Object.prototype.hasOwnProperty.call(window.GRANEX, m.metodo)) {
                    window.GRANEX[m.metodo].apply(null, Array.isArray(m.args) ? m.args : []);
                }
            } catch (err) {
                console.error('GRANEX mapa:', err);
            }
        });
    }

    informarCentro();
    enviar({ t: 'listo' });
})();
