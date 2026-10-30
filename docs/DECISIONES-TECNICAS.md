# Decisiones técnicas

Por qué Photo Palette está hecho así y no de otra forma. Cada decisión dice qué se eligió,
por qué, qué se descartó y qué cuesta.

← [Volver al README](../README.md)

1. [Dos modos, porque contar píxeles da paletas apagadas](#1-dos-modos-porque-contar-píxeles-da-paletas-apagadas)
2. [El k-means, escrito a mano y con semilla fija](#2-el-k-means-escrito-a-mano-y-con-semilla-fija)
3. [Límites pensados para compartir servidor](#3-límites-pensados-para-compartir-servidor)
4. [El feed no necesita servidor](#4-el-feed-no-necesita-servidor)
5. [La marca de agua entra en la paleta](#5-la-marca-de-agua-entra-en-la-paleta)
6. [Un despliegue que no se ata al proveedor](#6-un-despliegue-que-no-se-ata-al-proveedor)

---

## 1. Dos modos, porque contar píxeles da paletas apagadas

**El problema.** Un extractor de paletas corriente devuelve los colores que más píxeles
ocupan. Es exacto y casi siempre inútil. En el feed se ve con las fotos reales: la luna llena
es un 89,4 % de negro, y en el cielo con tejado salen cinco azules casi iguales mientras el
naranja de la teja ni aparece. La paleta es correcta y le falta justo el color por el que
hiciste la foto.

**La decisión.** Dos modos. `presentes` agrupa los píxeles y los ordena por cuántos hay.
`vivos` hace cinco cosas más:

1. Agrupa con `k = 3n` en vez de `k = n`, para que un color minoritario tenga grupo propio
   en lugar de disolverse dentro del grupo grande.
2. Ignora los píxeles por debajo de 0,15 de brillo. Una sombra no es un color.
3. Descarta los grupos con menos del 0,5 % de los píxeles con luz. Eso es ruido del sensor o
   una mota, no un color de la foto.
4. Ordena por saturación, no por tamaño.
5. Descarta el color que repite **el tono y el brillo** de uno ya elegido. Los azules casi
   iguales de un cielo nocturno colapsan en uno; el marrón de la sombra y el naranja del sol,
   que comparten tono pero no brillo, sobreviven los dos, porque para quien va a pintar una
   web ese par es justo el útil.

En «Estrellas y tejado», `vivos` devuelve dos colores: el `#623321` de la teja primero y un
solo azul `#1e252b` para todo el cielo.

**Lo que cuesta.** `vivos` **puede devolver menos colores de los que pides**, y uno solo si
la foto es monocroma. No es un fallo: está en el contrato, la tarjeta del feed se pinta bien
con menos de seis muestras y hay tests que lo exigen.

**Lo descartado.** Un solo modo «inteligente». Habría escondido la diferencia, que es
justo lo que enseña el interruptor del feed.

---

## 2. El k-means, escrito a mano y con semilla fija

`ExtractorPaleta` agrupa los píxeles en el espacio RGB sin librerías de color de terceros.

1. **La foto no se decodifica entera.** `LectorImagen` lee primero solo las dimensiones de
   la cabecera y rechaza más de 40 MP; después decodifica con `setSourceSubsampling`
   saltando píxeles hasta dejar unos 40.000. Una foto de 12 MP, que serían 48 MB en memoria,
   se queda en unos cientos de kilobytes.
2. **Los centros iniciales se eligen con k-means++**: el primero al azar y cada siguiente con
   probabilidad proporcional a su distancia al centro más cercano ya elegido. Sembrar del
   todo al azar da paletas distintas en cada ejecución, y a veces absurdas.
3. **Se itera** asignando cada píxel a su centro más cercano y recolocando cada centro en la
   media de los suyos, hasta que nadie cambia de grupo o se llega a 30 vueltas.
4. **La semilla es fija.** La misma foto da siempre la misma paleta. Es lo que permite
   escribir tests y lo que evita que el feed cambie de color entre despliegues.
5. Los grupos vacíos se descartan. Pedirle 6 colores a una foto que solo tiene 3 devuelve 3.

**Lo que cuesta.** La distancia es euclídea sobre RGB, la que peor se parece a cómo vemos los
colores. CIELAB sería más fiel, pero la euclídea se entiende de un vistazo y es la que hace
necesario el modo `vivos`. Cambiarla es una mejora futura, no una corrección pendiente.

---

## 3. Límites pensados para compartir servidor

La API está pensada para vivir en un VPS junto a otro proyecto. Los límites existen para que
un pico de uso no se lleve por delante al vecino:

| Límite | Valor | Qué pasa si no está |
| --- | --- | --- |
| Tamaño del archivo | 10 MB | Un POST de 500 MB llena el disco del multipart |
| Megapíxeles | 40 MP | Una foto de 200 MP reserva 800 MB antes de hacer nada |
| Muestra del k-means | ~40.000 píxeles | El análisis pasa de milisegundos a segundos |
| Subidas en curso | 4 (`FiltroSubidasEnCurso`) | Veinte fotos de 10 MB en el heap antes de saber si hay hueco |
| Análisis simultáneos | 2 (`Semaphore`) | Diez a la vez agotan los 512 MB del contenedor |
| Peticiones por IP | 10 por minuto (Bucket4j) | Un script sin navegador tumba el VPS gratis |
| Memoria del contenedor | 512 MB (`mem_limit`) | El OOM killer elige a quién matar, y puede no ser este |

**CORS no es ninguno de estos.** Solo decide qué webs pueden llamar a la API desde el
navegador; un script sin navegador lo ignora. Está para que nadie monte una copia de esta web
sobre mi servidor, no para protegerlo del abuso.

**La IP real, solo de un proxy de confianza.** Detrás de Caddy o de Render, la IP del
visitante llega en `X-Forwarded-For`. La aplicación arranca con
`server.forward-headers-strategy=native`, así que Tomcat la lee, pero **solo** cuando la
conexión viene de un proxy de la red interna. La lista de proxies de confianza añade la IPv6
privada (`fc00::/7`), que es desde donde conecta el proxy de casi cualquier host gestionado.
Si alguien la recortara no se rompería nada visible: todas las visitas pasarían a contar como
una sola IP y compartirían diez peticiones por minuto. Por eso hay un test que la vigila.

**Cortar antes de leer.** La prueba de carga (I03) pasó a la primera, pero a 21 MB del
límite. El semáforo de análisis llegaba tarde: cuando decía que no, la foto ya estaba copiada
en el heap. `FiltroSubidasEnCurso` corre antes que Spring lea el multipart y rechaza con el
mismo 503 a partir de la cuarta subida, sin leer el cuerpo. Con él, el mismo minuto de carga
se queda en 341 MB y termina el triple de análisis. Los números están en
[PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md#prueba-de-carga-i03).

---

## 4. El feed no necesita servidor

GitHub Pages no ejecuta Java, así que el feed no puede llamar a la API al cargar. En su
lugar, un comando analiza las fotos con el mismo código que la API y deja el resultado en un
JSON estático:

```bash
mvn compile exec:java@paletas     # web/fotos/*.jpg → web/paletas.json
```

**Lo que se gana.** Ningún color de la web está escrito a mano, y el feed funciona igual con
la API dormida o caída. El workflow de Pages se niega a publicar si falta `paletas.json`.

**Lo que cuesta.** Cambiar una foto obliga a regenerar el JSON antes del push. Está explicado
en [`web/fotos/LEEME.md`](../web/fotos/LEEME.md).

---

## 5. La marca de agua entra en la paleta

Las seis fotos del feed son mías y llevan mi símbolo en una esquina, dentro del recorte 4:5
de las tarjetas para que no se corte.

**La decisión.** `paletas.json` se calcula con las fotos tal como se publican, marca incluida.

**Por qué.** Al principio se calculaba con copias sin marca, para que la crema del símbolo no
saliera como color. Pero el botón «Probar con una de ejemplo» manda a la API la foto
publicada, y bastaba ese 0,5 % de píxeles distintos para que el k-means agrupara de otra
forma: la paleta en vivo dejaba de coincidir con la del feed. Esa coincidencia es la prueba
visible de que el algoritmo es determinista y de que el feed no miente, y vale más que una
paleta un poco más limpia.

**Lo que cuesta.** En las fotos oscuras la crema de la marca aparece como un color claro más,
por ejemplo un `#ccc6bd` en «Cielo estrellado». Es un color que de verdad está en la foto que
se ve.

---

## 6. Un despliegue que no se ata al proveedor

**La web** va a GitHub Pages: el workflow [`web.yml`](../.github/workflows/web.yml) publica
la carpeta `web/` en cada push que la toque.

**La API** está hoy en Render, en un contenedor construido con el `Dockerfile` de este
repositorio. No hay un `render.yaml` ni nada específico del proveedor: el puerto se lee de
`PORT`, que es lo único que pide un host gestionado, y por eso el mismo árbol vale para un
servidor propio sin tocar una línea. Allí es un `docker compose` con Caddy delante, y el
contenedor solo escucha en el loopback del servidor.

```bash
docker compose up -d --build
curl -fsS http://localhost:8080/api/salud   # 200 sin cuerpo
```

Sea cual sea el host, su URL va a [`web/js/config.js`](../web/js/config.js) y con un
`git push` Pages se republica solo.

**Lo que cuesta.** La instancia gratuita de Render se duerme a los quince minutos sin
tráfico y la primera llamada tarda unos treinta segundos. El servidor propio sigue siendo el
destino cuando haya dominio que decidir.
