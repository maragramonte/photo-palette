// Configuración de las pruebas E2E de la web.
//
// Se sirve web/ con un servidor estático y la API se simula dentro del navegador
// (tests/api-simulada.js). Así las pruebas no necesitan Java ni Docker y dan lo mismo en
// local que en el CI. La web se abre en localhost, y config.js entiende entonces que la
// API está en localhost:8080: esa es la dirección que intercepta la simulación.
import { defineConfig, devices } from "@playwright/test";

const PUERTO = 4322;
const enCI = !!process.env.CI;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: enCI,
  retries: enCI ? 1 : 0,
  reporter: enCI ? [["github"], ["html", { open: "never" }]] : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `http://localhost:${PUERTO}`,
    locale: "es-ES",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "escritorio", use: { ...devices["Desktop Chrome"] } },
    { name: "movil", use: { ...devices["Pixel 7"] } },
  ],
  webServer: {
    command: `npx http-server ../web -p ${PUERTO} -c-1 --silent`,
    url: `http://localhost:${PUERTO}/index.html`,
    reuseExistingServer: !enCI,
    timeout: 30_000,
  },
});
