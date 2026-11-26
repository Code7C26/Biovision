// La app estuvo publicada en la raíz hasta el 18/11/2026 y su service worker quedó registrado acá.
// Ahora la raíz es la landing y la app vive en /app/ (con su propio sw.js): este reemplazo se da de
// baja y recarga las pestañas, así los navegadores que guardaron la app vieja vuelven a ver la landing.
self.addEventListener('install', function () {
  self.skipWaiting();
});

self.addEventListener('activate', function (evento) {
  evento.waitUntil(
    self.registration.unregister()
      .then(function () { return self.clients.matchAll({ type: 'window' }); })
      .then(function (pestanas) {
        pestanas.forEach(function (p) {
          // Solo las de la raíz: /app/ tiene su propio service worker
          if (new URL(p.url).pathname.indexOf('/app/') !== 0) p.navigate(p.url);
        });
      })
  );
});
