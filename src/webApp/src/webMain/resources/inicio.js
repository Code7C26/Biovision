/*
 * Arranque de GRANEX web, cargado en <head> antes de pintar. Es un archivo aparte (no un <script> en
 * línea) para que la política de seguridad (CSP de index.html) pueda prohibir scripts en línea.
 *
 * 1. Tema: si el usuario eligió claro u oscuro en la app (clave "web_tema" de localStorage, la misma
 *    que usa ContenedorWeb.modoTema), la pantalla de carga ya sale con ese tema.
 * 2. Service worker: se registra al terminar de cargar. Si ya había uno controlando la página y llega
 *    otro, es una versión nueva publicada: se ofrece recargar.
 */
'use strict';

(function () {
    try {
        var tema = window.localStorage.getItem('web_tema');
        if (tema === 'claro' || tema === 'oscuro') document.documentElement.setAttribute('data-tema', tema);
    } catch (e) {
        /* localStorage bloqueado: se usa el tema del sistema */
    }

    // Si la app no termina de cargar (red muy lenta, navegador viejo, archivo que no llegó) la pantalla
    // de carga deja de girar sola y ofrece recargar, en lugar de quedar así para siempre
    var cargaFallida = false;
    function avisarCarga(texto) {
        var cargando = document.getElementById('cargando');
        var aviso = document.getElementById('carga-demorada');
        if (!cargando || !aviso || cargando.classList.contains('oculto')) return;
        if (texto) document.getElementById('carga-demorada-texto').textContent = texto;
        aviso.hidden = false;
    }
    document.addEventListener('DOMContentLoaded', function () {
        var recargar = document.getElementById('carga-recargar');
        if (recargar) recargar.addEventListener('click', function () { window.location.reload(); });
        setTimeout(function () { if (!cargaFallida) avisarCarga(null); }, 45000);
    });
    // Un script propio que no cargó (error de red) llega como evento de error con su elemento
    window.addEventListener('error', function (e) {
        var origen = e.target && e.target.tagName === 'SCRIPT' && e.target.src;
        if (origen && origen.indexOf(window.location.origin) === 0) {
            cargaFallida = true;
            avisarCarga('No se pudo cargar GRANEX. Revisá la conexión y recargá la página.');
        }
    }, true);

    if (!('serviceWorker' in navigator)) return;

    var habiaVersion = !!navigator.serviceWorker.controller;
    navigator.serviceWorker.addEventListener('controllerchange', function () {
        if (habiaVersion) mostrarAvisoVersion();
        habiaVersion = true;
    });
    window.addEventListener('load', function () {
        // En el servidor de desarrollo no hay sw.js: no se registra nada
        navigator.serviceWorker.register('sw.js').catch(function () {});
    });

    function mostrarAvisoVersion() {
        if (document.getElementById('aviso-version')) return;
        var aviso = document.createElement('div');
        aviso.id = 'aviso-version';
        aviso.setAttribute('role', 'status');

        var texto = document.createElement('span');
        texto.textContent = 'Versión nueva disponible';
        var actualizar = boton('Actualizar', function () { window.location.reload(); });
        var despues = boton('Después', function () { aviso.remove(); });

        aviso.append(texto, actualizar, despues);
        document.body.appendChild(aviso);
    }

    function boton(etiqueta, alTocar) {
        var b = document.createElement('button');
        b.type = 'button';
        b.textContent = etiqueta;
        b.addEventListener('click', alTocar);
        return b;
    }
})();
