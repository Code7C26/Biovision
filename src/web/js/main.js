// Enlaces de descarga y contacto. Vacío = todavía no publicado: el botón no se muestra (nunca un
// enlace roto). APK firmado: 'descargas/granex.apk'. Play: 'https://play.google.com/store/apps/details?id=com.aistudio.granex.agtech'.
const CONFIG = {
  apkUrl: '',
  playUrl: '',
  emailSoporte: 'supportgranex@gmail.com'
};

document.addEventListener('DOMContentLoaded', () => {
  // Enlaces configurables.
  const enlace = (tipo, url) => document.querySelectorAll('[data-enlace="' + tipo + '"]').forEach((a) => {
    if (url) a.href = url;
    else a.remove();
  });
  enlace('apk', CONFIG.apkUrl);
  enlace('play', CONFIG.playUrl);
  document.querySelectorAll('[data-enlace="soporte"]').forEach((a) => {
    a.href = 'mailto:' + CONFIG.emailSoporte;
    if (a.dataset.mostrarEmail !== undefined) a.textContent = CONFIG.emailSoporte;
  });

  const anio = document.getElementById('anio');
  if (anio) anio.textContent = new Date().getFullYear();

  // Sombra del encabezado al desplazarse.
  const encabezado = document.getElementById('encabezado');
  if (encabezado) {
    const actualizar = () => encabezado.classList.toggle('desplazado', window.scrollY > 8);
    actualizar();
    window.addEventListener('scroll', actualizar, { passive: true });
  }

  // Menú móvil.
  const boton = document.getElementById('boton-menu');
  const menu = document.getElementById('menu-movil');
  if (boton && menu) {
    const cerrar = () => { menu.classList.add('hidden'); boton.setAttribute('aria-expanded', 'false'); };
    boton.addEventListener('click', () => {
      const abierto = menu.classList.toggle('hidden') === false;
      boton.setAttribute('aria-expanded', String(abierto));
    });
    menu.querySelectorAll('a').forEach((a) => a.addEventListener('click', cerrar));
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') cerrar(); });
  }

  // Aparición suave de las secciones y conteo de las cifras.
  const sinMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const elementos = document.querySelectorAll('[data-revelar], [data-contar]');
  if (!('IntersectionObserver' in window)) {
    elementos.forEach((el) => el.classList.add('visible'));
    return;
  }
  const observador = new IntersectionObserver((entradas) => {
    entradas.forEach((entrada) => {
      if (!entrada.isIntersecting) return;
      entrada.target.classList.add('visible');
      if (entrada.target.dataset.contar && !sinMovimiento) contar(entrada.target);
      observador.unobserve(entrada.target);
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
  elementos.forEach((el) => observador.observe(el));
});

// Cuenta de 0 al valor final; el HTML ya trae el valor, así funciona sin JS.
function contar(el) {
  const destino = Number(el.dataset.contar);
  const duracion = 1200;
  const inicio = performance.now();
  const paso = (ahora) => {
    const t = Math.min(1, (ahora - inicio) / duracion);
    el.textContent = Math.round(destino * (1 - Math.pow(1 - t, 3)));
    if (t < 1) requestAnimationFrame(paso);
  };
  requestAnimationFrame(paso);
}
