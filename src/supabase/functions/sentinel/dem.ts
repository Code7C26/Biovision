// Copernicus DEM GLO-30 Public, leído directo de AWS Open Data (Cloud Optimized GeoTIFF, sin clave).
//
// En Copernicus Data Space el GLO-30 está reservado a usuarios CCM, y el GLO-90 abierto tiene un
// píxel cada 90 m: en un lote de 50 ha no distingue un bajo de una loma.
//
// Cada tesela cubre 1° × 1° y lleva el nombre de su esquina sudoeste. Son alturas Float32 en metros
// sobre el geoide EGM2008, en bloques comprimidos con Deflate. Se pide la cabecera y después solo
// los bloques que tocan el lote, con pedidos HTTP con Range.

const BASE_DEM = "https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com";
const BYTES_CABECERA = 65_536;

interface Tesela {
  url: string;
  ancho: number;
  alto: number;
  bloqueAncho: number;
  bloqueAlto: number;
  desplazamientos: number[];
  tamanos: number[];
  predictor: number;
  nodata: number | null;
  /** Centro del píxel (0, 0) y paso entre píxeles, en grados. */
  lon0: number;
  lat0: number;
  pasoLon: number;
  pasoLat: number;
}

function nombreTesela(lat: number, lon: number): string {
  const la = Math.floor(lat);
  const lo = Math.floor(lon);
  const ns = `${la < 0 ? "S" : "N"}${String(Math.abs(la)).padStart(2, "0")}_00`;
  const eo = `${lo < 0 ? "W" : "E"}${String(Math.abs(lo)).padStart(3, "0")}_00`;
  return `Copernicus_DSM_COG_10_${ns}_${eo}_DEM`;
}

async function rango(url: string, desde: number, hasta: number): Promise<Uint8Array | null> {
  const r = await fetch(url, { headers: { Range: `bytes=${desde}-${hasta - 1}` } });
  // Sin tesela (mar o país sin GLO-30 público): esos píxeles quedan sin dato
  if (r.status === 404 || r.status === 403) {
    await r.body?.cancel();
    return null;
  }
  if (r.status !== 206 && r.status !== 200) throw new Error(`DEM: HTTP ${r.status}`);
  const datos = new Uint8Array(await r.arrayBuffer());
  return r.status === 200 ? datos.slice(desde, hasta) : datos;
}

// Bytes por valor según el tipo de campo TIFF: BYTE, ASCII, SHORT, LONG, RATIONAL, ..., DOUBLE
const TAMANO_TIPO: Record<number, number> = { 1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 11: 4, 12: 8 };

function leerCabecera(url: string, cab: Uint8Array): Tesela {
  const dv = new DataView(cab.buffer, cab.byteOffset, cab.byteLength);
  if (dv.getUint16(0) !== 0x4949) throw new Error("DEM: el TIFF no es little-endian");
  if (dv.getUint16(2, true) !== 42) throw new Error("DEM: BigTIFF no soportado");
  const ifd = dv.getUint32(4, true);
  const numeros = new Map<number, number[]>();
  const textos = new Map<number, string>();
  const n = dv.getUint16(ifd, true);
  for (let i = 0; i < n; i++) {
    const e = ifd + 2 + i * 12;
    const etiqueta = dv.getUint16(e, true);
    const tipo = dv.getUint16(e + 2, true);
    const cuenta = dv.getUint32(e + 4, true);
    const tam = TAMANO_TIPO[tipo];
    if (!tam) continue;
    const pos = cuenta * tam <= 4 ? e + 8 : dv.getUint32(e + 8, true);
    if (pos + cuenta * tam > cab.length) throw new Error(`DEM: la etiqueta ${etiqueta} está fuera de la cabecera`);
    if (tipo === 2) {
      textos.set(etiqueta, new TextDecoder().decode(cab.subarray(pos, pos + cuenta)).replace(/\0.*$/s, ""));
      continue;
    }
    const v: number[] = [];
    for (let k = 0; k < cuenta; k++) {
      const p = pos + k * tam;
      v.push(
        tipo === 1 ? dv.getUint8(p)
        : tipo === 3 ? dv.getUint16(p, true)
        : tipo === 4 ? dv.getUint32(p, true)
        : tipo === 11 ? dv.getFloat32(p, true)
        : tipo === 12 ? dv.getFloat64(p, true)
        : dv.getUint32(p, true) / dv.getUint32(p + 4, true),
      );
    }
    numeros.set(etiqueta, v);
  }
  const uno = (etiqueta: number, porDefecto?: number): number => {
    const v = numeros.get(etiqueta)?.[0] ?? porDefecto;
    if (v === undefined) throw new Error(`DEM: falta la etiqueta TIFF ${etiqueta}`);
    return v;
  };
  if (uno(258) !== 32 || uno(339, 1) !== 3) throw new Error("DEM: se esperaban alturas Float32");
  if (uno(277, 1) !== 1) throw new Error("DEM: se esperaba una sola banda");
  const compresion = uno(259, 1);
  if (compresion !== 8 && compresion !== 32946) throw new Error(`DEM: compresión ${compresion} no soportada`);
  const predictor = uno(317, 1);
  if (predictor !== 1 && predictor !== 3) throw new Error(`DEM: predictor ${predictor} no soportado`);

  const escala = numeros.get(33550);
  const ancla = numeros.get(33922);
  if (!escala || !ancla) throw new Error("DEM: la tesela no está georreferenciada");
  // GTRasterTypeGeoKey (1025): 1 = el ancla es la esquina del píxel, 2 = su centro
  const claves = numeros.get(34735) ?? [];
  let esquina = true;
  for (let k = 4; k + 3 < claves.length; k += 4) {
    if (claves[k] === 1025 && claves[k + 1] === 0) esquina = claves[k + 3] !== 2;
  }
  const medio = esquina ? 0.5 : 0;
  const nodata = textos.get(42113)?.trim();
  return {
    url,
    ancho: uno(256),
    alto: uno(257),
    bloqueAncho: uno(322),
    bloqueAlto: uno(323),
    desplazamientos: numeros.get(324) ?? [],
    tamanos: numeros.get(325) ?? [],
    predictor,
    nodata: nodata ? Number(nodata) : null,
    lon0: ancla[3] + (medio - ancla[0]) * escala[0],
    lat0: ancla[4] - (medio - ancla[1]) * escala[1],
    pasoLon: escala[0],
    pasoLat: escala[1],
  };
}

async function procesar(datos: Uint8Array, transformacion: CompressionStream | DecompressionStream): Promise<Uint8Array> {
  const flujo = new Blob([datos]).stream().pipeThrough(transformacion);
  return new Uint8Array(await new Response(flujo).arrayBuffer());
}

async function leerBloque(t: Tesela, indice: number): Promise<Float32Array> {
  const desde = t.desplazamientos[indice];
  const comprimido = await rango(t.url, desde, desde + t.tamanos[indice]);
  if (!comprimido) throw new Error("DEM: falta un bloque de la tesela");
  const datos = await procesar(comprimido, new DecompressionStream("deflate"));
  const w = t.bloqueAncho;
  const n = w * t.bloqueAlto;
  if (datos.length < n * 4) throw new Error("DEM: bloque incompleto");
  const salida = new Float32Array(n);
  if (t.predictor === 1) {
    const dv = new DataView(datos.buffer, datos.byteOffset, n * 4);
    for (let i = 0; i < n; i++) salida[i] = dv.getFloat32(i * 4, true);
    return salida;
  }
  // Predictor de punto flotante (3): cada fila guarda los bytes de los valores en planos (primero
  // todos los más significativos) y como diferencias con el byte anterior
  const fila = w * 4;
  const be = new Uint8Array(n * 4);
  for (let y = 0; y < t.bloqueAlto; y++) {
    const base = y * fila;
    for (let i = 1; i < fila; i++) datos[base + i] = datos[base + i] + datos[base + i - 1];
    for (let x = 0; x < w; x++) {
      const o = base + x * 4;
      be[o] = datos[base + x];
      be[o + 1] = datos[base + w + x];
      be[o + 2] = datos[base + 2 * w + x];
      be[o + 3] = datos[base + 3 * w + x];
    }
  }
  const dv = new DataView(be.buffer);
  for (let i = 0; i < n; i++) salida[i] = dv.getFloat32(i * 4, false);
  return salida;
}

/**
 * Alturas del terreno (m) en los centros de una grilla de [ancho] × [alto] píxeles sobre la caja
 * dada, fila 0 al norte, interpoladas en forma bilineal. NaN donde no hay dato.
 */
export async function alturasDem(
  oeste: number, sur: number, este: number, norte: number, ancho: number, alto: number,
): Promise<Float32Array> {
  const pasoLon = (este - oeste) / ancho;
  const pasoLat = (norte - sur) / alto;
  const lon = (x: number) => oeste + (x + 0.5) * pasoLon;
  const lat = (y: number) => norte - (y + 0.5) * pasoLat;

  // Teselas que tocan el lote (casi siempre una)
  const clave = (la: number, lo: number) => Math.floor(la) * 1000 + Math.floor(lo);
  const esquinas: [number, number][] = [];
  for (let la = Math.floor(lat(alto - 1)); la <= Math.floor(lat(0)); la++) {
    for (let lo = Math.floor(lon(0)); lo <= Math.floor(lon(ancho - 1)); lo++) esquinas.push([la, lo]);
  }
  const teselas = new Map(await Promise.all(esquinas.map(async ([la, lo]) => {
    const nombre = nombreTesela(la, lo);
    const url = `${BASE_DEM}/${nombre}/${nombre}.tif`;
    const cab = await rango(url, 0, BYTES_CABECERA);
    return [clave(la, lo), cab ? leerCabecera(url, cab) : null] as const;
  })));
  const teselaDe = (y: number, x: number) => teselas.get(clave(lat(y), lon(x))) ?? null;

  // Los cuatro píxeles del DEM que rodean cada punto, y los bloques que hay que bajar
  const pedidos = new Map<Tesela, Set<number>>();
  const vecinos = (t: Tesela, la: number, lo: number) => {
    const c = Math.min(Math.max((lo - t.lon0) / t.pasoLon, 0), t.ancho - 1);
    const f = Math.min(Math.max((t.lat0 - la) / t.pasoLat, 0), t.alto - 1);
    const c0 = Math.floor(c), f0 = Math.floor(f);
    return { c0, f0, c1: Math.min(c0 + 1, t.ancho - 1), f1: Math.min(f0 + 1, t.alto - 1), dc: c - c0, df: f - f0 };
  };
  const indiceBloque = (t: Tesela, c: number, f: number) =>
    Math.floor(f / t.bloqueAlto) * Math.ceil(t.ancho / t.bloqueAncho) + Math.floor(c / t.bloqueAncho);
  for (let y = 0; y < alto; y++) {
    for (let x = 0; x < ancho; x++) {
      const t = teselaDe(y, x);
      if (!t) continue;
      let deTesela = pedidos.get(t);
      if (!deTesela) pedidos.set(t, deTesela = new Set());
      const v = vecinos(t, lat(y), lon(x));
      for (const [c, f] of [[v.c0, v.f0], [v.c1, v.f0], [v.c0, v.f1], [v.c1, v.f1]]) deTesela.add(indiceBloque(t, c, f));
    }
  }
  const listos = new Map<Tesela, Map<number, Float32Array>>();
  await Promise.all([...pedidos].flatMap(([t, indices]) => {
    const deTesela = new Map<number, Float32Array>();
    listos.set(t, deTesela);
    return [...indices].map(async (i) => deTesela.set(i, await leerBloque(t, i)));
  }));

  const salida = new Float32Array(ancho * alto).fill(NaN);
  for (let y = 0; y < alto; y++) {
    for (let x = 0; x < ancho; x++) {
      const t = teselaDe(y, x);
      if (!t) continue;
      const deTesela = listos.get(t)!;
      const valor = (c: number, f: number) => {
        const b = deTesela.get(indiceBloque(t, c, f))!;
        const h = b[(f % t.bloqueAlto) * t.bloqueAncho + (c % t.bloqueAncho)];
        return h === t.nodata ? NaN : h;
      };
      const v = vecinos(t, lat(y), lon(x));
      const arriba = valor(v.c0, v.f0) * (1 - v.dc) + valor(v.c1, v.f0) * v.dc;
      const abajo = valor(v.c0, v.f1) * (1 - v.dc) + valor(v.c1, v.f1) * v.dc;
      salida[y * ancho + x] = arriba * (1 - v.df) + abajo * v.df;
    }
  }
  return salida;
}

// ---------- PNG ----------

const TABLA_CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(datos: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < datos.length; i++) c = TABLA_CRC[(c ^ datos[i]) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function bloquePng(tipo: string, datos: Uint8Array): Uint8Array {
  const salida = new Uint8Array(12 + datos.length);
  const dv = new DataView(salida.buffer);
  dv.setUint32(0, datos.length);
  salida.set(new TextEncoder().encode(tipo), 4);
  salida.set(datos, 8);
  dv.setUint32(8 + datos.length, crc32(salida.subarray(4, 8 + datos.length)));
  return salida;
}

/**
 * PNG RGB de 8 bits con la misma codificación que usaba el evalscript de Sentinel Hub: altura en
 * decímetros + 5000 (de -500 a 6053 m) partida en R y G; B = 255 con dato, 0 sin dato.
 */
export async function pngRelieve(alturas: Float32Array, ancho: number, alto: number): Promise<Uint8Array> {
  const fila = 1 + ancho * 3; // cada fila empieza con el filtro 0 (sin filtro)
  const crudo = new Uint8Array(fila * alto);
  for (let y = 0; y < alto; y++) {
    for (let x = 0; x < ancho; x++) {
      const h = alturas[y * ancho + x];
      if (!Number.isFinite(h)) continue;
      const v = Math.max(0, Math.min(65535, Math.round((h + 500) * 10)));
      const o = y * fila + 1 + x * 3;
      crudo[o] = v >> 8;
      crudo[o + 1] = v & 255;
      crudo[o + 2] = 255;
    }
  }
  const cabecera = new Uint8Array(13);
  const dv = new DataView(cabecera.buffer);
  dv.setUint32(0, ancho);
  dv.setUint32(4, alto);
  cabecera.set([8, 2, 0, 0, 0], 8); // 8 bits, RGB, deflate, sin filtro adaptativo, sin entrelazado
  const partes = [
    new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]),
    bloquePng("IHDR", cabecera),
    bloquePng("IDAT", await procesar(crudo, new CompressionStream("deflate"))),
    bloquePng("IEND", new Uint8Array(0)),
  ];
  const png = new Uint8Array(partes.reduce((s, p) => s + p.length, 0));
  let o = 0;
  for (const p of partes) {
    png.set(p, o);
    o += p.length;
  }
  return png;
}
