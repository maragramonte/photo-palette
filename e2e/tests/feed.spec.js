// El feed y el interruptor de modo. Todo lo que se ve aquí sale de paletas.json, así que
// estas pruebas comprueban que la web pinta exactamente lo que calculó la API.
import { test, expect } from "@playwright/test";
import { simularApi, FOTOS } from "./api-simulada.js";

const hexARgb = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`;
};

const coloresDeTarjeta = (tarjeta) =>
  tarjeta.locator(".muestras__color").evaluateAll((trozos) => trozos.map((t) => t.style.background));

test.beforeEach(async ({ page }) => {
  await simularApi(page);
  await page.goto("/");
});

test("pinta una tarjeta por cada foto de paletas.json", async ({ page }) => {
  await expect(page.locator("#tarjetas .tarjeta")).toHaveCount(FOTOS.length);
  await expect(page.locator("#feedResumen")).toHaveText(
    `${FOTOS.length} fotos mías y los colores que esconde cada una. Toca una para ver su paleta y copiarla.`
  );
});

test("cada tarjeta lleva el nombre y los colores de su paleta", async ({ page }) => {
  const primera = FOTOS[0];
  const tarjeta = page.getByRole("button", { name: primera.nombre, exact: false }).first();

  await expect(tarjeta).toContainText(primera.nombre);
  await expect(tarjeta).toContainText(`${primera.paletas.presentes.colores.length} colores`);
  expect(await coloresDeTarjeta(tarjeta)).toEqual(primera.paletas.presentes.colores.map((c) => hexARgb(c.hex)));
});

test("el interruptor pasa el feed entero a vivos sin recargar", async ({ page }) => {
  const vivos = page.getByRole("radio", { name: "Vivos" });
  await vivos.click();

  await expect(vivos).toHaveAttribute("aria-checked", "true");
  await expect(page.getByRole("radio", { name: "Más presentes" })).toHaveAttribute("aria-checked", "false");
  await expect(page.locator("#explicacionModo")).toContainText("Vivos: los colores que se te quedan en la memoria");

  // Todas las tarjetas, no solo la primera: el modo afecta al feed entero.
  const cuantos = await page.locator(".tarjeta__cuantos").allTextContents();
  expect(cuantos).toEqual(
    FOTOS.map(({ paletas }) => {
      const n = paletas.vivos.colores.length;
      return n + (n === 1 ? " color" : " colores");
    })
  );

  const tarjeta = page.locator("#tarjetas .tarjeta").first();
  expect(await coloresDeTarjeta(tarjeta)).toEqual(FOTOS[0].paletas.vivos.colores.map((c) => hexARgb(c.hex)));
});

test("la portada enseña una foto del feed y «Otra foto» la cambia", async ({ page }) => {
  const nombre = page.locator("#muestraNombre");
  await expect(page.locator("#muestra")).toBeVisible();
  await expect(nombre).not.toBeEmpty();

  const antes = await nombre.textContent();
  expect(FOTOS.map((foto) => foto.nombre)).toContain(antes);

  await page.getByRole("button", { name: "Otra foto" }).click();
  await expect(nombre).not.toHaveText(antes);
});
