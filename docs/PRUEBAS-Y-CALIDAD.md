# Pruebas y calidad

Qué se comprueba, cómo, y qué queda sin cubrir.

← [Volver al README](../README.md)

---

## Cómo se ejecutan

```bash
mvn test
```

No hace falta Docker ni base de datos: todo corre en la JVM, y las fotos de prueba se generan
en memoria dentro de los propios tests.

---

## Qué hay

**92 pruebas** en dos niveles:

| Nivel | Pruebas | Qué cubre |
| --- | --- | --- |
| Unitarias | 70 | `ExtractorPaleta` (29), `LectorImagen` (11), el contrato del JSON de salida (12), `ServicioPaletas` (3) y la lista de proxies de confianza (15) |
| HTTP | 22 | La aplicación entera levantada con `@SpringBootTest` y MockMvc: el controlador, los errores en ProblemDetail, el límite por IP, CORS y el servidor ocupado |

Las que importan no comprueban que el código corre, sino que las decisiones de producto se
cumplen:

- El naranja del sol entra entre los tres primeros colores en `vivos` y **no** entra en
  `presentes`. Es el test que justifica que el modo exista.
- Un cielo de cuatro azules casi iguales devuelve menos de 6 colores.
- La misma foto da siempre la misma paleta: sin esto, el feed cambiaría de color en cada
  despliegue.
- Un `.txt` renombrado a `.jpg` da 422, no un 500.
- Una cabecera de PNG de 48 MP se rechaza **sin decodificar un píxel**.
- Dos IP distintas detrás del proxy tienen cupos separados, y la que se pasa recibe un 429
  con su `Retry-After`.
- Ese 429 lleva cabeceras CORS, para que la web pueda enseñar el motivo en lugar de un error
  de red.
- Si alguien recorta la IPv6 privada de la lista de proxies de confianza, falla un test. Sin
  él no fallaría nada: el servicio respondería igual, pero todas las visitas compartirían un
  único cupo.

---

## En cada push

El workflow [`tests.yml`](../.github/workflows/tests.yml) ejecuta `mvn verify` y, si pasa,
construye la imagen Docker. Un `Dockerfile` que solo funciona en un portátil no sirve de nada
el día del despliegue. El badge del README mira a este workflow.

El de la web, [`web.yml`](../.github/workflows/web.yml), se niega a publicar si falta
`web/paletas.json`.

---

## Lo que no está cubierto

Dicho sin adornos:

- **La cobertura no está medida.** No hay JaCoCo, así que no hay porcentaje.
- **El frontend no tiene pruebas automatizadas** ni hay pruebas E2E. La web se prueba a mano
  en el navegador.
- **No hay prueba de carga** (I03). Los límites están puestos, pero nadie ha comprobado aún
  cuántas subidas de 10 MB a la vez aguantan los 512 MB del contenedor.
- **Lighthouse está pendiente** (I02): el objetivo es 90 o más en móvil.
