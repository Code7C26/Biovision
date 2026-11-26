"""Sirve la PWA en la PC con las MISMAS cabeceras que producción (las lee de hosting/vercel.json).

    python webApp/pwa/servir.py                    # webApp/build/dist/pwa en http://localhost:8080
    python webApp/pwa/servir.py --puerto 9000 --carpeta webApp/build/dist/pwa-wasm

Solo escucha en 127.0.0.1 (no se ve desde otros equipos). `localhost` cuenta como sitio seguro, así
que el service worker y "Instalar app" funcionan igual que publicados. HSTS no aplica en http.
"""
import argparse
import functools
import http.server
import json
import pathlib
import re

AQUI = pathlib.Path(__file__).resolve().parent
RAIZ_REPO = AQUI.parent.parent

TIPOS_EXTRA = {".wasm": "application/wasm", ".mjs": "text/javascript", ".webmanifest": "application/manifest+json"}


def leer_reglas():
    """[(regex, [(cabecera, valor)])] en el orden de vercel.json (las posteriores pisan a las anteriores)."""
    config = json.loads((AQUI / "hosting" / "vercel.json").read_text(encoding="utf-8"))
    reglas = []
    for regla in config.get("headers", []):
        # Los "source" de este proyecto son expresiones regulares simples sobre la ruta
        reglas.append((re.compile("^" + regla["source"] + "$"), [(h["key"], h["value"]) for h in regla["headers"]]))
    return reglas


class Manejador(http.server.SimpleHTTPRequestHandler):
    reglas = []
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, **TIPOS_EXTRA}

    def send_error(self, code, message=None, explain=None):
        """Como Firebase, Vercel y GitHub Pages: una ruta que no existe responde con 404.html."""
        pagina = pathlib.Path(self.directory) / "404.html"
        if code == 404 and pagina.is_file():
            cuerpo = pagina.read_bytes()
            self.send_response(404)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(cuerpo)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(cuerpo)
            return
        super().send_error(code, message, explain)

    def end_headers(self):
        ruta = self.path.split("?", 1)[0].split("#", 1)[0]
        cabeceras = {}
        for patron, lista in self.reglas:
            if patron.match(ruta):
                cabeceras.update(lista)
        for nombre, valor in cabeceras.items():
            if nombre != "Strict-Transport-Security":
                self.send_header(nombre, valor)
        super().end_headers()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--puerto", type=int, default=8080)
    p.add_argument("--carpeta", default=str(RAIZ_REPO / "webApp" / "build" / "dist" / "pwa"))
    a = p.parse_args()
    carpeta = pathlib.Path(a.carpeta).resolve()
    if not (carpeta / "index.html").is_file():
        raise SystemExit(f"No hay index.html en {carpeta}: primero ./gradlew :webApp:distribucionPwa")
    Manejador.reglas = leer_reglas()
    servidor = http.server.ThreadingHTTPServer(("127.0.0.1", a.puerto), functools.partial(Manejador, directory=str(carpeta)))
    print(f"GRANEX web en http://localhost:{a.puerto}  (carpeta {carpeta}; Ctrl+C para cortar)", flush=True)
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
