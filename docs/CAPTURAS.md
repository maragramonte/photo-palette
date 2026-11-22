# Capturas

La web pantalla a pantalla, para quien prefiera no pulsar nada. Todas son de la aplicación
real, con las paletas que calcula la API sobre las fotos del feed. Se hicieron con la web del
repositorio y la API simulada dentro del navegador, igual que en las pruebas E2E: sus
respuestas salen de `paletas.json`, que genera el mismo código Java.

← [Volver al README](../README.md)

---

## La portada

El titular y, debajo, dos frases: una para cualquiera, que dice qué hace la web, y otra más
pequeña para quien programa. Después, la zona para soltar una foto y una foto del feed con sus
dos paletas a la vez. La
regla pegada a la foto mide lo que ocupa cada color «más presente»; debajo van los «vivos».
Verlas juntas es la idea del proyecto: la paleta fiel no es la paleta útil. «Otra foto» pasa
a la siguiente.

![Portada de Photo Palette: sobre fondo azul noche, el titular «Cada foto esconde una paleta.», la frase «Haces una foto por un color. Esta web lo encuentra…», otra más pequeña sobre la API en Java y la zona para soltar una foto con sus dos botones. A la derecha, la foto de los tulipanes con una regla de color pegada a su borde, donde el verde casi negro de las hojas ocupa el 24 % y el ocre de las flores el 20 %, y debajo sus seis colores vivos con su HEX](capturas/01-portada.jpg)

---

## Los dos modos, con la misma foto

A la izquierda, `presentes`: la paleta empieza por el verde casi negro de las hojas, que ocupa
el 24 % de la foto. A la derecha, `vivos`: empieza por el ocre de las flores amarillas y deja
entrar el rojo de los tulipanes, que en `presentes` ni aparece. Por qué existen los dos está en
[DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md#1-dos-modos-porque-contar-píxeles-da-paletas-apagadas).

![La tarjeta de los tulipanes y banderas dos veces. En «Más presentes» sus seis muestras empiezan por un verde casi negro y no hay ningún rojo; en «Vivos» empiezan por el ocre de las flores, y la tercera es el rojo de los tulipanes](capturas/02-dos-modos.jpg)

---

## El feed en modo vivos

Cada tarjeta enseña la foto, su nombre, cuántos colores salen y las muestras. Los tulipanes y
las rosas arcoíris dan seis cada una; las fotos más apagadas, menos.

![El feed en modo Vivos: dos tarjetas con su foto y sus muestras de color debajo. La de los tulipanes da seis colores encabezados por el ocre de las flores; la de las rosas arcoíris da seis, empezando por un azul intenso](capturas/04-feed-vivos.jpg)

---

## El detalle de una paleta

Al pulsar una tarjeta se abre el panel: las muestras grandes, que copian su HEX con un clic;
el bloque `:root` listo para pegar, y una tarjeta de ejemplo pintada solo con esa paleta, con
el contraste calculado para que se vea que cumple WCAG AA.

![Panel de detalle del ala desde la ventanilla en modo vivos: seis muestras con su HEX y su porcentaje, el bloque :root con los seis colores y el botón para copiarlo, y debajo una tarjeta de ejemplo pintada con esa paleta y la razón de contraste del texto](capturas/03-detalle.jpg)

---

## La API, en vivo

«Probar con una de ejemplo» coge una foto del feed y se la manda a la API. El panel avisa de
que esa paleta se acaba de calcular, y explica por qué sale idéntica a la guardada: el
k-means es determinista.

![Panel de detalle con el aviso «Calculada ahora mismo, en directo. Sale igual que la guardada porque el mismo cálculo siempre da el mismo resultado» sobre las seis muestras de los tulipanes y banderas en modo más presentes y su bloque :root](capturas/05-api-en-vivo.jpg)

---

## En el móvil

El feed pasa a una columna, el interruptor se coloca bajo el título y las tarjetas mantienen
su recorte 4:5.

![La web en un móvil: el título «Mis fotos», el resumen que invita a tocar una foto, el interruptor entre «Más presentes» y «Vivos» y la tarjeta de los tulipanes a todo el ancho con sus seis muestras](capturas/06-movil.jpg)
