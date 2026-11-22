// El panel de detalle: los colores, el bloque :root, copiar y el foco.
import { test, expect } from "@playwright/test";
import { simularApi, FOTOS } from "./api-simulada.js";

const PRIMERA = FOTOS[0];
const PALETA = PRIMERA.paletas.presentes;

test.beforeEach(async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await simularApi(page);
  await page.goto("/");
});

const abrirPrimera = async (page) => {
  const tarjeta = page.locator("#tarjetas .tarjeta").first();
  await tarjeta.click();
  const detalle = page.getByRole("dialog", { name: PRIMERA.nombre });
  await expect(detalle).toBeVisible();
  return { tarjeta, detalle };
};

test("enseña cada color con su HEX y el bloque :root de la paleta", async ({ page }) => {
  const { detalle } = await abrirPrimera(page);

  for (const color of PALETA.colores) {
    const boton = detalle.getByRole("button", { name: `Copiar ${color.hex}` });
    await expect(boton).toContainText(color.hex);
    await expect(boton).toContainText(`${color.porcentaje} % · ${color.variable}`);
  }
  await expect(detalle.locator("pre.codigo")).toHaveText(PALETA.css);
  await expect(detalle.locator(".contraste")).toContainText(":1");
});

test("copiar un color lo deja en el portapapeles y lo avisa", async ({ page }) => {
  const { detalle } = await abrirPrimera(page);
  const hex = PALETA.colores[0].hex;

  await detalle.getByRole("button", { name: `Copiar ${hex}` }).click();

  await expect(page.locator("#avisoCopiado")).toHaveText(`Copiado ${hex} ✓`);
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(hex);
});

test("copiar el bloque :root copia el CSS entero", async ({ page }) => {
  const { detalle } = await abrirPrimera(page);

  await detalle.getByRole("button", { name: "Copiar el bloque :root" }).click();

  await expect(page.locator("#avisoCopiado")).toHaveText("Bloque :root copiado ✓");
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(PALETA.css);
});

test("con el panel abierto, el tabulador no llega a nada de detrás", async ({ page }) => {
  await abrirPrimera(page);
  await expect(page.getByRole("button", { name: "Cerrar el detalle" })).toBeFocused();

  // Al pasar del último botón, el foco sale a la barra del navegador (document.activeElement
  // es entonces el body) y vuelve al panel. Lo que no puede es caer en el feed de detrás.
  const enDetrasDelPanel = () =>
    page.evaluate(() => {
      const activo = document.activeElement;
      return activo !== document.body && !activo.closest("#detalle");
    });

  const visitados = new Set();
  for (let i = 0; i < 25; i++) {
    await page.keyboard.press("Tab");
    expect(await enDetrasDelPanel()).toBe(false);
    visitados.add(await page.evaluate(() => document.activeElement.textContent.trim().slice(0, 20)));
  }
  // Y el recorrido pasa de verdad por los botones del panel, no se queda quieto.
  expect(visitados.size).toBeGreaterThan(5);
});

test("Escape cierra el panel y devuelve el foco a la tarjeta", async ({ page }) => {
  const { tarjeta, detalle } = await abrirPrimera(page);

  await page.keyboard.press("Escape");

  await expect(detalle).toBeHidden();
  await expect(tarjeta).toBeFocused();
});

test("pulsar fuera del panel también lo cierra", async ({ page }) => {
  const { detalle } = await abrirPrimera(page);

  // La esquina de arriba a la izquierda es telón en escritorio (el panel va a la derecha) y
  // en móvil (el panel sube desde abajo).
  await page.locator("#telon").click({ position: { x: 5, y: 5 } });

  await expect(detalle).toBeHidden();
});
