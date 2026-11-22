// Lo que hace la web mientras la API arranca o si no contesta.
//
// La web insiste durante 90 segundos antes de rendirse. Para no esperarlos de verdad, el
// reloj del navegador es falso y la prueba lo adelanta a saltos. Van a saltos y no de una
// vez porque entre comprobación y comprobación hay una petición de red, y esa sí tarda lo
// que tarda.
import { test, expect } from "@playwright/test";
import { simularApi, despertarApi, FOTOS } from "./api-simulada.js";

const adelantar = async (page, milisegundos, salto = 2_000) => {
  for (let hecho = 0; hecho < milisegundos; hecho += salto) {
    await page.clock.runFor(salto);
  }
};

test("con la API caída, la subida se apaga, se dice por qué y el feed sigue", async ({ page }) => {
  await page.clock.install();
  const api = await simularApi(page, { viva: false });
  await page.goto("/");

  await adelantar(page, 95_000);

  const estado = page.locator("#subidaEstado");
  await expect(estado).toHaveText("La API no ha respondido en 90 segundos. El feed sigue funcionando.");
  await expect(page.getByRole("button", { name: "Probar con una de ejemplo" })).toBeDisabled();
  await expect(page.locator("#archivo")).toBeDisabled();
  await expect(page.locator("#tarjetas .tarjeta")).toHaveCount(FOTOS.length);
  expect(api.comprobaciones).toBeGreaterThan(1);

  // Si la API vuelve, «Volver a comprobar» enciende la subida sin recargar la página.
  api.viva = true;
  await page.getByRole("button", { name: "Volver a comprobar" }).click();
  await expect(page.getByRole("button", { name: "Probar con una de ejemplo" })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Volver a comprobar" })).toHaveCount(0);
});

test("si la API tarda, avisa de que se está despertando y luego de que ya está", async ({ page }) => {
  await page.clock.install();
  const api = await simularApi(page, { viva: "colgada" });
  await page.goto("/");

  await adelantar(page, 3_000, 1_000);
  await expect(page.locator("#subidaEstado")).toContainText("La API se está despertando");
  await expect(page.getByRole("button", { name: "Probar con una de ejemplo" })).toBeDisabled();

  await despertarApi(api);

  await expect(page.locator("#subidaEstado")).toHaveText("La API ya está despierta.");
  await expect(page.getByRole("button", { name: "Probar con una de ejemplo" })).toBeEnabled();
});
