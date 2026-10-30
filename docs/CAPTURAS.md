# Capturas

La web pantalla a pantalla, para quien prefiera no pulsar nada. Todas son de la aplicación
real, con las paletas que calcula la API sobre las seis fotos del feed.

← [Volver al README](../README.md)

---

## La portada

El titular, la zona para soltar una foto con sus dos botones y, debajo, el arranque del feed
con el interruptor entre los dos modos.

![Portada de Photo Palette: sobre fondo azul noche, el titular «Cada foto esconde una paleta. Esta API la encuentra» junto a una zona para soltar una foto con los botones «Elegir una foto» y «Probar con una de ejemplo», y debajo el feed con el interruptor entre «Más presentes» y «Vivos»](capturas/01-portada.jpg)

---

## Los dos modos, con la misma foto

A la izquierda, `presentes`: la paleta empieza por el negro, que ocupa el 70 % del eclipse.
A la derecha, `vivos`: empieza por el naranja del sol, se olvida de las sombras y se queda en
cinco colores. Por qué existen los dos está en
[DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md#1-dos-modos-porque-contar-píxeles-da-paletas-apagadas).

![La tarjeta del eclipse parcial dos veces. En «Más presentes» sus seis muestras empiezan por un negro casi puro; en «Vivos» son cinco y empiezan por el naranja del sol](capturas/02-dos-modos.jpg)

---

## El feed en modo vivos

Cada tarjeta enseña la foto, su nombre, cuántos colores salen y las muestras. El eclipse da
cinco y el ala vista desde la ventanilla, seis.

![El feed en modo Vivos: dos tarjetas con su foto y sus muestras de color debajo. La del eclipse da cinco colores encabezados por el naranja del sol; la del ala vista desde la ventanilla da seis, empezando por el azul del cielo](capturas/04-feed-vivos.jpg)

---

## El detalle de una paleta

Al pulsar una tarjeta se abre el panel: las muestras grandes, que copian su HEX con un clic;
el bloque `:root` listo para pegar, y una tarjeta de ejemplo pintada solo con esa paleta, con
el contraste calculado para que se vea que cumple WCAG AA.

![Panel de detalle del ala desde la ventanilla en modo vivos: seis muestras con su HEX y su porcentaje, el bloque :root con los seis colores y el botón para copiarlo, y debajo una tarjeta de ejemplo pintada con esa paleta y la razón de contraste del texto](capturas/03-detalle.jpg)

---

## La API, en vivo

«Probar con una de ejemplo» coge una foto del feed y se la manda a la API. El panel avisa de
que esa paleta se acaba de calcular y no viene de `paletas.json`, y sale idéntica a la del
feed: el k-means es determinista.

![Panel de detalle con el aviso «Calculada ahora mismo por la API, no leída de paletas.json» sobre las seis muestras del ala desde la ventanilla en modo más presentes y su bloque :root](capturas/05-api-en-vivo.jpg)

---

## En el móvil

El feed pasa a una columna, el interruptor se coloca bajo el título y las tarjetas mantienen
su recorte 4:5.

![La web en un móvil: el título «El feed», el interruptor entre «Más presentes» y «Vivos» y la tarjeta del eclipse a todo el ancho con sus seis muestras](capturas/06-movil.jpg)
