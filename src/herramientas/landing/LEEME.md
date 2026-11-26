# Estilos de la landing (`web/`)

`web/css/tailwind.css` se genera con Tailwind 3.4.16 a partir de las clases que usan `web/*.html` y
`web/js/*.js`. Antes se generaba en el navegador con el CDN de pruebas de Tailwind, que no es para
producción y metía un script de terceros en la página.

Después de cambiar clases en la landing, regenerarlo desde la raíz del repo:

```bash
PATH="/d/USUARIO/dev/gradle-home/nodejs/node-v22.0.0-win-x64:$PATH" npm_config_cache="D:/USUARIO/dev/npm-cache" \
  npx.cmd tailwindcss@3.4.16 -c herramientas/landing/tailwind.config.cjs -i herramientas/landing/entrada.css -o web/css/tailwind.css --minify
```
