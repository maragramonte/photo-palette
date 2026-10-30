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

`mvn verify` además mide la cobertura con JaCoCo y deja el informe en
`target/site/jacoco/index.html`.

---

## Qué hay

**98 pruebas** en dos niveles:

| Nivel | Pruebas | Qué cubre |
| --- | --- | --- |
| Unitarias | 74 | `ExtractorPaleta` (29), `LectorImagen` (11), el contrato del JSON de salida (12), `ServicioPaletas` (3), la lista de proxies de confianza (15) y la purga del límite por IP (4) |
| HTTP | 24 | La aplicación entera levantada con `@SpringBootTest` y MockMvc: el controlador, los errores en ProblemDetail, el límite por IP, el tope de subidas, CORS y el servidor ocupado |

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
- Al pasar de 10.000 IP, el límite olvida las que llevan diez minutos sin volver, pero no a
  las activas: una IP con su 429 no lo pierde porque otras manden peticiones. Se prueba con
  un tope de 3 y el reloj movido a mano.

---

## Cobertura

| | Líneas | Ramas |
| --- | --- | --- |
| `dominio` | 97,9 % | 93,8 % |
| `web` | 97,9 % | 80,8 % |
| **La API entera** | **97,4 %** | **91,4 %** |
| Contando el generador de `paletas.json` | 80,9 % | 78,9 % |

`mvn verify` falla si la API baja del 90 % de líneas o del 85 % de ramas. El suelo está por
debajo de lo medido a propósito: tiene que avisar cuando se deja de probar algo, no cuando
un refactor añade un `if`.

El generador de `paletas.json` (`cli/`) no tiene tests. Se ejecuta a mano con
`mvn compile exec:java@paletas` y su resultado se mira en la web antes de publicarlo. Sale en
el informe con su 0 %, pero no cuenta para el suelo.

Las ramas de `web` son las más flojas, pero lo que queda fuera son caminos poco probables,
como un `IOException` al leer la subida en el controlador.

---

## En cada push

El workflow [`tests.yml`](../.github/workflows/tests.yml) ejecuta `mvn verify` y, si pasa,
construye la imagen Docker. Un `Dockerfile` que solo funciona en un portátil no sirve de nada
el día del despliegue. El badge del README mira a este workflow. La cobertura sale en el
resumen de cada run, y el informe HTML completo como artefacto `jacoco`.

El de la web, [`web.yml`](../.github/workflows/web.yml), se niega a publicar si falta
`web/paletas.json`.

---

## Prueba de carga (I03)

El criterio del backlog: 20 subidas de 10 MB a la vez contra el mismo `docker-compose` que
en el VPS (512 MB, 1 CPU). El contenedor no pasa de 512 MB, responde 503 o 429 en vez de
caerse, y el vecino sigue respondiendo.

```bash
docker compose up -d --build
python carga/i03.py --fotos <carpeta con JPG de ~10 MB> --vecino http://127.0.0.1:18081/
```

El script solo usa la biblioteca estándar. Lanza tres escenarios: 20 a la vez desde una IP,
20 a la vez desde 20 IP (con `X-Forwarded-For`, que Tomcat acepta porque la conexión llega
por la pasarela de Docker) y 20 sin parar durante un minuto, cada petición con una IP nueva
para que el límite por IP no alivie nada. La memoria se lee del cgroup, separando la caché de
disco, que el kernel recupera, de la que no se puede recuperar.

Con dos fotos de 9,5 MB, una de 24 MP y otra de 39,6 MP, en Docker Desktop:

| Un minuto sin parar | Sin `FiltroSubidasEnCurso` | Con él |
| --- | --- | --- |
| Memoria sin caché de disco | 491 MB | **341 MB** |
| `memory.peak` del cgroup | 545 MB, contra el techo | 482 MB |
| Análisis terminados | 6 | **21** |
| La petición más lenta | 41 s | 12 s |
| Fallos del vecino | 0 | 0 |

La primera pasada cumplía el criterio, pero a 21 MB del límite. El motivo: el semáforo de
`ServicioPaletas` se consulta después de que el controlador copie la foto al heap, así que
las 18 subidas que iban a recibir un 503 ocupaban antes 10 MB cada una. Ahora un filtro corta
a partir de la cuarta subida en curso sin leer el cuerpo.

En ningún escenario hubo OOM, reinicios ni otra respuesta que 200, 429 o 503.

---

## Lo que no está cubierto

Dicho sin adornos:

- **El frontend no tiene pruebas automatizadas** ni hay pruebas E2E. La web se prueba a mano
  en el navegador.
- **La prueba de carga no corre en el CI.** Se lanza a mano con `carga/i03.py`, y el vecino
  fue un nginx en Docker Desktop, no el Dr. Agramonte en el VPS.
- **Lighthouse está pendiente** (I02): el objetivo es 90 o más en móvil.
