// axe-core con las reglas de WCAG 2.1 A y AA, en la portada y con el panel abierto.
//
// axe no lo ve todo: no sabe si un texto alternativo describe bien la foto ni si el orden
// del foco tiene sentido. Eso sigue siendo revisión a mano. Lo que hace es que un cambio
// no pueda estropear el contraste o los nombres de los botones sin que se note.
import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { simularApi } from "./api-simulada.js";

const infracciones = async (page, ambito) => {
  // Las muestras entran con una animación de opacidad. A medio camino, axe mide un texto
  // medio transparente y da un contraste que el visitante no llega a ver.
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== "running"));
  let analisis = new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]);
  if (ambito) {
    analisis = analisis.include(ambito);
  }
  const { violations } = await analisis.analyze();
  // Una línea por infracción, para que el fallo se lea en el log del CI.
  return violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`);
};

test.beforeEach(async ({ page }) => {
  await simularApi(page);
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Probar con una de ejemplo" })).toBeEnabled();
});

test("la portada, sin infracciones de WCAG 2.1 A y AA", async ({ page }) => {
  expect(await infracciones(page)).toEqual([]);
});

test("el feed en modo vivos, sin infracciones", async ({ page }) => {
  await page.getByRole("radio", { name: "Vivos" }).click();
  expect(await infracciones(page)).toEqual([]);
});

test("el panel de detalle, sin infracciones", async ({ page }) => {
  await page.locator("#tarjetas .tarjeta").first().click();
  await expect(page.getByRole("dialog")).toBeVisible();
  expect(await infracciones(page, "#detalle")).toEqual([]);
});
