// Subir una foto y la foto de ejemplo, contra la API simulada.
import { test, expect } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { simularApi, paletaSimulada } from "./api-simulada.js";

const LUNA = fileURLToPath(new URL("../../web/fotos/luna-llena.jpg", import.meta.url));

// La subida se enciende cuando la API contesta a la comprobación del arranque.
const esperarSubida = (page) => expect(page.getByRole("button", { name: "Probar con una de ejemplo" })).toBeEnabled();

test("sube una foto, la pone la primera del feed y abre su paleta", async ({ page }) => {
  const api = await simularApi(page);
  await page.goto("/");
  await esperarSubida(page);

  await page.locator("#archivo").setInputFiles(LUNA);

  const detalle = page.getByRole("dialog", { name: "luna-llena" });
  await expect(detalle).toBeVisible();
  await expect(detalle.locator("pre.codigo")).toHaveText(paletaSimulada("presentes").css);
  expect(api.analisis).toEqual([{ modo: "presentes", n: "6" }]);

  await page.keyboard.press("Escape");
  const tuya = page.locator("#tarjetas .tarjeta").first();
  await expect(tuya).toContainText("Tu foto");
  await expect(page.locator("#subidaEstado")).toHaveText(
    `Listo: ${paletaSimulada("presentes").colores.length} colores en modo presentes.`
  );
});

test("al cambiar de modo, pide a la API la paleta que le falta a tu foto", async ({ page }) => {
  const api = await simularApi(page);
  await page.goto("/");
  await esperarSubida(page);
  await page.locator("#archivo").setInputFiles(LUNA);
  // Primero que se abra el panel: un Escape antes de tiempo no cierra nada, y el panel que
  // se abre después deja el feed inerte.
  await expect(page.getByRole("dialog", { name: "luna-llena" })).toBeVisible();
  await page.keyboard.press("Escape");

  await page.getByRole("radio", { name: "Vivos" }).click();

  await expect.poll(() => api.analisis).toEqual([
    { modo: "presentes", n: "6" },
    { modo: "vivos", n: "6" },
  ]);
  const n = paletaSimulada("vivos").colores.length;
  await expect(page.locator("#tarjetas .tarjeta").first()).toContainText(`${n} colores`);
});

test("rechaza lo que no es JPG ni PNG sin llamar a la API", async ({ page }) => {
  const api = await simularApi(page);
  await page.goto("/");
  await esperarSubida(page);

  await page.locator("#archivo").setInputFiles({ name: "foto.webp", mimeType: "image/webp", buffer: Buffer.from("RIFF") });

  await expect(page.locator("#subidaEstado")).toHaveText("Solo JPG y PNG. El WebP todavía no se puede analizar.");
  expect(api.analisis).toEqual([]);
});

test("enseña el error de la API tal cual y deja reintentar", async ({ page }) => {
  const detalleDelError = "La imagen está dañada o no es una imagen.";
  // La primera petición falla con un 422; el reintento sale bien.
  await simularApi(page, { error: (numero) => (numero === 1 ? { status: 422, detail: detalleDelError } : null) });
  await page.goto("/");
  await esperarSubida(page);

  await page.locator("#archivo").setInputFiles(LUNA);

  await expect(page.locator("#subidaEstado")).toHaveText(detalleDelError);
  await page.getByRole("button", { name: "Reintentar" }).click();

  await expect(page.getByRole("dialog", { name: "luna-llena" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Reintentar" })).toHaveCount(0);
});

test("«Probar con una de ejemplo» abre una paleta calculada en vivo", async ({ page }) => {
  const api = await simularApi(page);
  await page.goto("/");
  await esperarSubida(page);

  await page.getByRole("button", { name: "Probar con una de ejemplo" }).click();

  const detalle = page.getByRole("dialog");
  await expect(detalle).toBeVisible();
  await expect(detalle).toContainText("Calculada ahora mismo, en directo. Sale igual que la guardada porque el mismo cálculo siempre da el mismo resultado.");
  expect(api.analisis).toHaveLength(1);
});

test("en el móvil, «Prueba con tu foto» lleva a la caja de subida; en escritorio no hace falta", async ({ page, isMobile }) => {
  await simularApi(page);
  await page.goto("/");
  const accion = page.getByRole("link", { name: "Prueba con tu foto" });

  if (!isMobile) {
    await expect(accion).toBeHidden();
    return;
  }

  await expect(page.locator("#formularioSubida")).not.toBeInViewport();
  await accion.click();
  await expect(page).toHaveURL(/#subida$/);
  await expect(page.locator("#formularioSubida")).toBeInViewport();
});
