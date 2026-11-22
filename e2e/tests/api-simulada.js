// La API simulada: contesta dentro del navegador a las dos llamadas que hace la web, con
// respuestas de la misma forma que las de la API real (ver docs/API.md).
//
// Las paletas salen de web/paletas.json, que genera el mismo código Java de la API. Así
// la simulación no se inventa colores y, si cambia el formato de la respuesta, el feed y
// estas pruebas cambian a la vez.
import { readFileSync } from "node:fs";

export const API = "http://localhost:8080";

const datos = JSON.parse(readFileSync(new URL("../../web/paletas.json", import.meta.url), "utf8"));

/** Las fotos del feed, tal como las lee la web. */
export const FOTOS = datos.fotos;

/** La paleta que devuelve la API simulada para cualquier foto: la de la primera del feed. */
export const paletaSimulada = (modo) => FOTOS[0].paletas[modo];

// La web está en localhost:4322 y la API en localhost:8080: son orígenes distintos y, sin
// esta cabecera, el navegador no deja leer la respuesta.
const CORS = { "access-control-allow-origin": "*" };

/**
 * @param {import('@playwright/test').Page} page
 * @param {object} [opciones]
 * @param {boolean} [opciones.viva] si GET /api/photo-palette contesta (true) o la conexión falla
 * @param {(numero: number) => {status: number, detail: string} | null} [opciones.error]
 *        para la petición número `numero` (desde 1), el error que devuelve POST /api/paletas,
 *        o null para contestar con la paleta
 * @returns el registro de lo que ha pedido la web, que las pruebas pueden mirar
 */
export async function simularApi(page, { viva = true, error = () => null } = {}) {
  const registro = { viva, comprobaciones: 0, analisis: [], pendientes: [] };

  await page.route(`${API}/api/photo-palette`, (ruta) => {
    registro.comprobaciones++;
    if (registro.viva === "colgada") {
      // Ni contesta ni falla: así se ve el aviso de que la API se está despertando.
      registro.pendientes.push(ruta);
      return;
    }
    return registro.viva
      ? ruta.fulfill({ status: 200, headers: CORS })
      : ruta.abort("connectionrefused");
  });

  await page.route(/\/api\/paletas\?/, (ruta) => {
    const url = new URL(ruta.request().url());
    registro.analisis.push({ modo: url.searchParams.get("modo"), n: url.searchParams.get("n") });

    const fallo = error(registro.analisis.length);
    if (fallo) {
      return ruta.fulfill({
        status: fallo.status,
        headers: CORS,
        contentType: "application/problem+json",
        body: JSON.stringify({ title: "Error", status: fallo.status, detail: fallo.detail }),
      });
    }
    return ruta.fulfill({ status: 200, headers: CORS, json: paletaSimulada(url.searchParams.get("modo")) });
  });

  return registro;
}

/** Contesta de golpe a las comprobaciones que se habían quedado colgadas. */
export async function despertarApi(registro) {
  registro.viva = true;
  for (const ruta of registro.pendientes.splice(0)) {
    await ruta.fulfill({ status: 200, headers: CORS });
  }
}
