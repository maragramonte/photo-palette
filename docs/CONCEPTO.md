# Photo Palette: concepto, sketch y backlog

Oct 26, 2026 · @Mar

## Antes de empezar: lo que corrijo del plan anterior

El plan está bien orientado, pero tiene un riesgo de producto que no menciona: con tus fotos reales, la paleta sale apagada. El algoritmo devuelve los colores que más píxeles ocupan, no los más vistosos. En el atardecer, los dos tonos más oscuros suman el 64 % y el naranja del sol apenas un 6 %. En el cielo estrellado salen cuatro azules casi iguales. Sin un modo de **colores vivos**, el feed se verá gris.

Otros seis ajustes:

1. **El backlog ignora lo que ya está hecho.** El núcleo de B01–B05 ya existe en el proyecto `paleta`: k-means, endpoint, errores y 21 tests. Falta compilarlo con Maven en local. Backend baja de 4–6 días a 1–2.
2. **WebP no funciona tal cual.** `ImageIO` de Java no lee WebP. Opción simple: solo JPG y PNG. Si lo quieres, hace falta la dependencia TwelveMonkeys `imageio-webp`.
3. **El contrato del JSON no coincide con el código.** El documento usa `colors`, `rgb` como objeto y `n` de 2 a 10. El código usa `colores`, `rgb` como array y `n` de 2 a 12. Me quedo con el del código (sección Contrato).
4. **La paleta de ejemplo de la luna es inventada.** Tu luna real es naranja, no tiene "malva" ni "azul noche". Ningún color del diseño se escribe a mano: todos salen de la API.
5. **Las imágenes del documento son de stock.** En el producto, solo fotos tuyas. Es justo lo que lo diferencia.
6. **Despliegue partido en dos.** La página va en GitHub Pages, que no ejecuta Java. El feed lee un paletas.json generado por tu código Java, y la subida llama a la API en tu VPS, en un contenedor separado del Dr. Agramonte.

## Concepto

**Photo Palette convierte tus fotos en paletas de color listas para pegar en una web.** Las calcula una API propia en Java y se ven en un feed nocturno donde el color lo ponen las fotos.

**Frase para el README y las redes:** "Cada foto esconde una paleta. Esta API la encuentra."

**Para quién, por orden:** quien vea tus redes (tiene que entenderse en 10 segundos), un recruiter (tiene que ver backend defendible) y quien diseñe webs (tiene que poder copiar los colores y usarlos).

| Sí entra en la v1 | No entra en la v1 |
| --- | --- |
| Feed con 6 fotos tuyas y su paleta real | Cuentas, likes, comentarios, seguidores |
| Modo "más presentes" y modo "vivos" | Base de datos e historial |
| Copiar HEX con un clic | Guardar las fotos subidas |
| Vista previa: una tarjeta pintada con la paleta | Editor de diseño o filtros de foto |
| Subir tu propia foto (JPG o PNG, máx. 10 MB) | WebP, app móvil, IA |
| API documentada, tests y Docker | Login o panel de administración |

**Terminado significa:** se puede abrir la demo en el móvil, subir una foto, copiar un color y ver la tarjeta cambiar. Todo con datos reales de la API, con tests en verde y el README con capturas.

## Sketch

Todo cabe en una sola página, sin rutas. El mockup usa tus tres fotos y las paletas reales que salen del algoritmo en modo vivos. No hay ni un color inventado.

&#91;image: Mockup de escritorio de Photo Palette con las fotos de Mar\]

**De arriba abajo:**

1. **Cabecera:** logo con la luna, y enlaces a Feed, API (la sección del README) y GitHub.
2. **Hero:** la frase a la izquierda y la zona de subida a la derecha. Al soltar una foto, se añade como primera tarjeta del feed y se abre su detalle.
3. **Feed:** tarjetas 4:5 con la foto, el nombre, el número de colores y 6 muestras. El interruptor *Más presentes / Vivos* afecta a todo el feed.
4. **Detalle**, que se abre al pulsar una tarjeta: muestras grandes que se copian al clic ("Copiado ✓"), el bloque `:root` listo para copiar y la vista previa pintada con la paleta.

**En móvil:** el hero apila la frase sobre la subida, el feed pasa a 1 columna y el detalle se abre como hoja desde abajo.

**Lo que enseña el mockup:** el cielo estrellado solo da 2 colores en modo vivos, porque sus azules son casi iguales. Es correcto, y la tarjeta tiene que verse bien con menos de 6 muestras.

**Tokens base (D02):**

| Token | Valor | Uso |
| --- | --- | --- |
| `--bg` | #0b0d1a | Fondo de página |
| `--surface` | #12152b | Tarjetas y paneles |
| `--border` | #1e2240 | Bordes de 1 px |
| `--accent` | #7c5cff | Botones e interruptor activo |
| `--accent-soft` | #a78bfa | Etiquetas, seleccionado y logo |
| `--text` / `--text-2` | #e8ebff / #aab0d6 | Texto principal y secundario |
| Tipografía | Inter 800 / 600 / 400 | Titulares, nombres y cuerpo; código en monoespaciada |
| Radio | 20 px tarjetas, 12 px botones | — |

## Contrato de la API

Un solo endpoint. Es el que ya existe en el código, más el parámetro `modo`.

`POST /api/paletas?n=6&modo=vivos` · multipart, campo `imagen`

| Parámetro | Valores | Por defecto | Qué hace |
| --- | --- | --- | --- |
| `imagen` | JPG o PNG, máx. 10 MB | — | La foto a analizar, en memoria, sin guardarla |
| `n` | 2–12 | 6 | Cuántos colores devolver |
| `modo` | `presentes` · `vivos` | `presentes` | `presentes`: orden por píxeles. `vivos`: agrupa en 3×n, ignora los casi negros, descarta los grupos con menos del 0,5 % de los píxeles con luz y ordena por saturación sin repetir tonos parecidos. Puede devolver menos de n |

```json
{
  "modo": "vivos",
  "colores": [
    { "hex": "#d18d67", "rgb": [209, 141, 103], "porcentaje": 5.7, "variable": "--color-1" }
  ],
  "css": ":root {\n  --color-1: #d18d67;\n}"
}
```

| Código | Cuándo |
| --- | --- |
| `400` | Falta la imagen, o `n` o `modo` no son válidos |
| `413` | La imagen pasa de 10 MB |
| `422` | No es un JPG o PNG legible, o pasa de 40 megapíxeles |
| `429` | Esa IP ha superado 10 análisis por minuto |
| `503` | Ya hay 2 análisis en curso; el frontend reintenta en unos segundos |

Los errores van en formato ProblemDetail (RFC 9457), con `title` y `detail` en español. El frontend muestra `detail` tal cual.

Cambios sobre el código actual: añadir `modo` en la respuesta y `variable` en cada color.

**CORS:** la API solo acepta peticiones desde `https://maragramonte.github.io`. Cualquier otro origen recibe un rechazo del navegador.

**Salud:** `GET /api/salud` responde `200` sin cuerpo. La web lo consulta al cargar y, si falla, desactiva la subida con un aviso. El feed sigue funcionando con `paletas.json`.

CORS solo decide qué webs pueden llamar a la API desde el navegador. No protege contra abuso: eso lo hacen los límites de tamaño, píxeles, tasa y concurrencia.

## Backlog inicial

Son 26 tareas en 14–17 días a tiempo parcial. Es menos que el plan anterior porque el backend ya está casi hecho. He cambiado el orden: primero se cierra el backend, para diseñar con paletas reales y no con colores inventados. Cada tarea lleva su criterio de terminado.

### 01 · Cerrar backend (1–2 días)

- [ ] **B01** Compilar `paleta` en local y dejar los 21 tests en verde: *`mvn test` pasa sin tocar nada a mano.*
- [ ] **B02** Renombrar a `photo-palette` (repo, `artifactId`, README): *el nombre es el mismo en GitHub, la web y las redes.*
- [ ] **B03** Modo `vivos` en `ExtractorPaleta`: *con el atardecer, el naranja del sol sale entre los 3 primeros colores.*
- [ ] **B04** Añadir `modo` y `variable` al JSON: *la respuesta coincide con la sección Contrato.*
- [ ] **B05** Tests del modo `vivos` y de `modo` inválido: *un test por regla nueva y el 400 cubierto.*

* [ ] **B06** Comando Java que genera `paletas.json` con las fotos del feed en los dos modos: *un `mvn` y el JSON queda en la carpeta de la web.*
* [ ] **B07** CORS solo para `https://maragramonte.github.io`: *un test que compruebe que otro origen se rechaza.*

- [ ] **B08** Lectura segura: leer primero las dimensiones con `ImageReader` y rechazar más de 40 MP; después decodificar con `setSourceSubsampling`: *una foto de 12 MP no pasa de 10 MB de memoria al analizarse.*
- [ ] **B09** Límites de uso: `Semaphore(2)` con 503 y 10 peticiones por minuto por IP con 429 (Bucket4j, con server.forward-headers-strategy=native para leer la IP real de X-Forwarded-For solo si viene de la red interna de Docker, y el puerto 8080 sin publicar fuera del VPS): *un test para cada código, y otro que compruebe que dos IP distintas detrás del proxy tienen límites separados.*
- [ ] **B10** `GET /api/salud`: *responde 200 y está fuera del límite por IP.*

### 02 · Diseño (2 días)

- [ ] **D01** Cerrar el sketch de este documento, *una sola página con: cabecera, hero con subida, feed y panel de detalle.*
- [ ] **D02** Tokens en CSS: colores, tipografía (Inter), espaciado de 8 en 8, radio 16: *un `tokens.css` y ningún valor suelto en el resto.*
- [ ] **D03** Elegir 6 fotos, exportarlas en JPG a 1600 px (la API no lee WebP): *cada una pesa menos de 300 KB y tiene un texto `alt`.*

### 03 · Frontend (4–5 días)

- [ ] **F01** Cabecera, hero y feed responsive: *1 columna en móvil y 2 en escritorio, sin scroll horizontal a 360 px.*
- [ ] **F02** El feed lee paletas.json (generado en B06) al cargar: *ningún color escrito a mano en el HTML.*
- [ ] **F03** Subida por arrastre o botón, con estados de cargando, error, API no disponible y reintentar: *el error muestra el `detail` de la API.*
- [ ] **F04** Clic en un color copia el HEX, con aviso "Copiado": *funciona con teclado (Enter) además de con ratón.*
- [ ] **F05** Vista previa: tarjeta con fondo, texto y botón de la paleta: *el texto elige blanco o negro según el contraste (WCAG AA).*
- [ ] **F06** Interruptor presentes / vivos: *cambia la paleta sin recargar la página.*

### 04 · Integración y pruebas (2 días)

- [ ] **I01** Probar fotos de 10 MB, un .txt renombrado a .jpg, un PNG transparente y una foto de un solo color: *ninguna rompe la página.*
- [ ] **I02** Accesibilidad y rendimiento: *Lighthouse ≥ 90 en accesibilidad y rendimiento en móvil.*

* [ ] **I03** Prueba de carga en local, con el mismo `docker-compose` y los mismos límites que en el VPS: 20 subidas de 10 MB a la vez: *el contenedor no pasa de 512 MB, responde 503 o 429 en vez de caerse, y el Dr. Agramonte sigue respondiendo. Sin esto, la subida no se publica.*

### 05 · Publicación (2–3 días)

- [ ] **P01** Dockerfile probado y GitHub Actions con `mvn test` en cada push: *el badge del README está en verde.*
- [ ] **P02** Desplegar la API en tu VPS, solo después de I03: contenedor propio con mem\_limit 512m, cpus 1.0 y restart: unless-stopped, detrás de Caddy con HTTPS. Primero a mano, sin automatizar: *`curl` a `/api/paletas` desde fuera devuelve 200.*
- [ ] **P03** Publicar la web en GitHub Pages con un workflow de Actions; la URL de la API va en un `config.js`: *la demo abre en `maragramonte.github.io/photo-palette` y la subida funciona desde el móvil.*
- [ ] **P04** README con GIF del flujo, contrato y explicación del k-means: *alguien lo arranca sin preguntarte nada.*

**Pregunta abierta:** ¿qué dominio propio usas para la API? No la colgaría de dragramonte.com. Decide antes de P02. Lo demás ya está decidido: la web en GitHub Pages y la API en tu VPS.

## Contenido para redes

Cada post sale de una tarea terminada, así no hay que inventar nada. Los vídeos son verticales: el mismo archivo va a Reels y a la cuenta dev de TikTok.

| Tras | Post | Formato | Gancho |
| --- | --- | --- | --- |
| B03 | El atardecer y sus dos paletas: presentes y vivos | Carrusel, 4 slides | "Mi algoritmo dice que un atardecer es casi negro. Tiene razón." |
| B05 | Cómo funciona el k-means, explicado con colores | Vídeo de pantalla, 30 s | "40.000 píxeles, 6 colores. Así los elige." |
| F05 | Subir foto, copiar un color y ver la tarjeta cambiar | Reel/TikTok, 15 s | "De mi foto a una web en 10 segundos." |
| P02 | Lanzamiento con enlace a la demo | Carrusel + historia con enlace | "Prueba con tu foto. Te leo en comentarios." |
