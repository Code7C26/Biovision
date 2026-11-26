"""Despliega en Supabase lo que la app necesita, con la API de administración.

Uso:  python -I supabase/desplegar.py [--migracion archivo.sql] [--secreto-aleatorio NOMBRE]
                                      [--funcion nombre[:sin-jwt]] ...

Lee SUPABASE_TOKEN de D:\\USUARIO\\claves\\granex-config.txt (nunca lo imprime). El proyecto sale
de supabase.url en local.properties.
- --migracion: ejecuta el SQL del archivo (debe poder correrse dos veces: "if not exists").
- --secreto-aleatorio: crea el secreto de Edge Functions con 32 bytes al azar en base64, solo si
  todavía no existe (cambiarlo dejaría ilegible lo que ya se cifró con él). El valor no se muestra.
- --funcion: sube supabase/functions/<nombre>/*.ts; ":sin-jwt" la despliega con verify_jwt = false.
"""
import base64
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.request
import uuid

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG = r"D:\USUARIO\claves\granex-config.txt"
API = "https://api.supabase.com/v1"


def leer_config():
    valores = {}
    with open(CONFIG, encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if linea and not linea.startswith("#") and "=" in linea:
                k, v = linea.split("=", 1)
                valores[k.strip()] = v.strip()
    return valores


def proyecto():
    with open(os.path.join(RAIZ, "local.properties"), encoding="utf-8") as f:
        for linea in f:
            m = re.match(r"\s*supabase\.url\s*=\s*https?\\?://([a-z0-9]+)\.supabase\.co", linea)
            if m:
                return m.group(1)
    sys.exit("No encontré supabase.url en local.properties")


def pedir(token, metodo, ruta, cuerpo=None, tipo="application/json"):
    datos = None
    if cuerpo is not None:
        datos = cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo).encode("utf-8")
    req = urllib.request.Request(API + ruta, data=datos, method=metodo, headers={
        "Authorization": "Bearer " + token,
        "Content-Type": tipo,
        "User-Agent": "granex-desplegar/1.0",
    })
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            texto = r.read().decode("utf-8")
            return r.status, (json.loads(texto) if texto.strip().startswith(("{", "[")) else texto)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:500]


def main():
    token = leer_config().get("SUPABASE_TOKEN", "")
    if not token.startswith("sbp_"):
        sys.exit("Falta SUPABASE_TOKEN en el archivo de configuración")
    ref = proyecto()
    args = sys.argv[1:]
    error = False
    i = 0
    while i < len(args):
        opcion, valor = args[i], args[i + 1]
        i += 2
        if opcion == "--migracion":
            with open(os.path.join(RAIZ, valor), encoding="utf-8") as f:
                sql = f.read()
            estado, r = pedir(token, "POST", f"/projects/{ref}/database/query", {"query": sql})
            print(f"migración {os.path.basename(valor)}: {estado}", "" if estado < 300 else r)
            error |= estado >= 300
        elif opcion == "--secreto-aleatorio":
            estado, lista = pedir(token, "GET", f"/projects/{ref}/secrets")
            if estado >= 300:
                print(f"secretos: no se pudieron listar ({estado})", lista)
                error = True
                continue
            if any(s.get("name") == valor for s in lista):
                print(f"secreto {valor}: ya existía, no se cambia")
                continue
            clave = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
            estado, r = pedir(token, "POST", f"/projects/{ref}/secrets", [{"name": valor, "value": clave}])
            del clave
            print(f"secreto {valor}: {estado}", "" if estado < 300 else r)
            error |= estado >= 300
        elif opcion == "--funcion":
            nombre, _, modo = valor.partition(":")
            carpeta = os.path.join(RAIZ, "supabase", "functions", nombre)
            archivos = sorted(a for a in os.listdir(carpeta) if a.endswith(".ts"))
            meta = {"entrypoint_path": "index.ts", "name": nombre, "verify_jwt": modo != "sin-jwt"}
            limite = "----granex" + uuid.uuid4().hex
            partes = [
                f"--{limite}\r\nContent-Disposition: form-data; name=\"metadata\"\r\n"
                f"Content-Type: application/json\r\n\r\n{json.dumps(meta)}\r\n".encode("utf-8")
            ]
            for a in archivos:
                with open(os.path.join(carpeta, a), "rb") as f:
                    contenido = f.read()
                partes.append(
                    f"--{limite}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{a}\"\r\n"
                    f"Content-Type: application/typescript\r\n\r\n".encode("utf-8") + contenido + b"\r\n"
                )
            partes.append(f"--{limite}--\r\n".encode("utf-8"))
            estado, r = pedir(token, "POST", f"/projects/{ref}/functions/deploy?slug={nombre}", b"".join(partes),
                              tipo=f"multipart/form-data; boundary={limite}")
            detalle = f"versión {r.get('version')}, verify_jwt={r.get('verify_jwt')}" if isinstance(r, dict) else r
            print(f"función {nombre} ({', '.join(archivos)}): {estado}", detalle)
            error |= estado >= 300
        else:
            sys.exit("Opción desconocida: " + opcion)
    sys.exit(1 if error else 0)


if __name__ == "__main__":
    main()
