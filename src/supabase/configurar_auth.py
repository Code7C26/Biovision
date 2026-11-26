"""Configura la autenticación del proyecto Supabase de GRANEX con la API de administración.

Aplica, según los datos que encuentre:
- correo de GRANEX por SMTP (Gmail si hay GMAIL_APP_PASSWORD, si no Resend);
- plantillas con código de 6 dígitos (supabase/plantillas/*.html) para confirmar la cuenta y
  cambiar la contraseña (Supabase solo permite tocarlas si hay un SMTP propio configurado);
- ingreso con Google (ID de cliente web y secreto de Google Cloud).

Los secretos se leen de un archivo FUERA del repositorio (nunca se imprimen ni se guardan acá):
    D:\\USUARIO\\claves\\granex-config.txt
con líneas CLAVE=valor:
    SUPABASE_TOKEN=sbp_...             (supabase.com/dashboard/account/tokens)
    GMAIL_EMAIL=supportgranex@gmail.com
    GMAIL_APP_PASSWORD=abcdefghijklmnop (myaccount.google.com/apppasswords, sin espacios)
    RESEND_API_KEY=re_...              (alternativa a Gmail: resend.com → API Keys)
    GOOGLE_WEB_CLIENT_ID=....apps.googleusercontent.com
    GOOGLE_WEB_CLIENT_SECRET=GOCSPX-...

Gmail (sin restricción de destinatario) tiene prioridad sobre Resend (solo manda a su propia
cuenta sin verificar un dominio) cuando los dos están completos.

Uso:  python -I supabase/configurar_auth.py [ruta del archivo]
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request

PROYECTO = "bsrkohhxkbzwhoogryhm"
# Remitente de prueba de Resend: envía sin verificar un dominio propio, y solo a esa misma cuenta.
REMITENTE_RESEND = "onboarding@resend.dev"
RAIZ = pathlib.Path(__file__).resolve().parent.parent


def leer_config(ruta: pathlib.Path) -> dict:
    datos = {}
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, valor = linea.split("=", 1)
        datos[clave.strip()] = valor.strip()
    return datos


def main() -> int:
    ruta = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else r"D:\USUARIO\claves\granex-config.txt")
    if not ruta.is_file():
        print(f"No encuentro {ruta}")
        return 1
    c = leer_config(ruta)
    token = c.get("SUPABASE_TOKEN", "").replace(" ", "")
    if not token:
        print("Falta SUPABASE_TOKEN")
        return 1

    cambios = {}
    hecho = []
    correo_gmail = c.get("GMAIL_EMAIL", "").replace(" ", "")
    clave_gmail = c.get("GMAIL_APP_PASSWORD", "").replace(" ", "")
    clave_resend = c.get("RESEND_API_KEY", "").replace(" ", "")

    if correo_gmail and clave_gmail:
        cambios.update({
            "smtp_admin_email": correo_gmail,
            "smtp_sender_name": "GRANEX",
            "smtp_host": "smtp.gmail.com",
            "smtp_port": "465",
            "smtp_user": correo_gmail,
            "smtp_pass": clave_gmail,
            "smtp_max_frequency": 30,
            # Con SMTP propio el límite sube de unos pocos por hora a 100
            "rate_limit_email_sent": 100,
        })
        hecho.append(f"correo de GRANEX (SMTP Gmail, {correo_gmail})")
    elif clave_resend:
        cambios.update({
            "smtp_admin_email": REMITENTE_RESEND,
            "smtp_sender_name": "GRANEX",
            "smtp_host": "smtp.resend.com",
            "smtp_port": "465",
            "smtp_user": "resend",
            "smtp_pass": clave_resend,
            "smtp_max_frequency": 5,
            "rate_limit_email_sent": 100,
        })
        hecho.append("correo de GRANEX (SMTP Resend: solo a su propia cuenta sin dominio verificado)")
    else:
        print("Sin GMAIL_APP_PASSWORD ni RESEND_API_KEY: no se puede tocar el correo ni las plantillas todavía (Supabase lo exige).")

    if cambios.get("smtp_host"):
        # Supabase rechaza tocar asunto/plantilla si no hay SMTP propio ya configurado: van juntos
        plantillas = RAIZ / "supabase" / "plantillas"
        cambios.update({
            "mailer_autoconfirm": False,
            "mailer_otp_length": 6,
            "mailer_subjects_confirmation": "Tu código para activar GRANEX: {{ .Token }}",
            "mailer_templates_confirmation_content": (plantillas / "confirmar-cuenta.html").read_text(encoding="utf-8"),
            "mailer_subjects_recovery": "Tu código para cambiar la contraseña de GRANEX",
            "mailer_templates_recovery_content": (plantillas / "cambiar-contrasena.html").read_text(encoding="utf-8"),
        })
        hecho.append("plantillas con código")

    if c.get("GOOGLE_WEB_CLIENT_ID") and c.get("GOOGLE_WEB_CLIENT_SECRET"):
        cambios.update({
            "external_google_enabled": True,
            "external_google_client_id": c["GOOGLE_WEB_CLIENT_ID"].replace(" ", ""),
            "external_google_secret": c["GOOGLE_WEB_CLIENT_SECRET"].replace(" ", ""),
        })
        hecho.append("ingreso con Google")

    if not cambios:
        print("Nada para aplicar todavía: completá al menos la contraseña de Gmail/Resend o los datos de Google.")
        return 1

    pedido = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{PROYECTO}/config/auth",
        data=json.dumps(cambios).encode("utf-8"),
        method="PATCH",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json", "User-Agent": "granex-config"},
    )
    try:
        with urllib.request.urlopen(pedido, timeout=60) as r:
            print(f"Supabase respondió {r.status}: aplicado {', '.join(hecho)}")
    except urllib.error.HTTPError as e:
        # El cuerpo del error no trae secretos (es el mensaje de validación de la API)
        print(f"Supabase respondió {e.code}: {e.read().decode('utf-8', 'replace')[:400]}")
        return 1

    # El ID de cliente web es público (va dentro del APK): queda en local.properties para el build
    if c.get("GOOGLE_WEB_CLIENT_ID"):
        props = RAIZ / "local.properties"
        lineas = [l for l in props.read_text(encoding="utf-8").splitlines() if not l.startswith("google.webClientId=")]
        lineas.append(f"google.webClientId={c['GOOGLE_WEB_CLIENT_ID'].replace(' ', '')}")
        props.write_text("\n".join(lineas) + "\n", encoding="utf-8")
        print("google.webClientId agregado a local.properties")
    return 0


if __name__ == "__main__":
    sys.exit(main())
