# Las fotos del feed

Son dieciséis fotos reales, hechas con el móvil: flores, el mar, Londres, un mirlo, la luna,
un ala de avión y una piscina de bolas. Están a 1600 px como mucho y sin metadatos EXIF. Las
nuevas están recortadas a 4:5, el formato de las tarjetas, y sin la firma de la cámara.

Cada una lleva dos marcas:

- **Mi símbolo** (el de `favicon.svg`, solo la M en crema) en la esquina inferior derecha,
  dentro del recorte 4:5 de las tarjetas para que no se corte. Si esa esquina es clara, lleva
  una sombra suave debajo, porque la crema sobre grava blanca o cielo desaparecía.
- **La firma «m.agramonte»** en el centro, translúcida. No impide descargar la foto, nada lo
  hace en una web: lo que consigue es que no se pueda usar sin que se vea de quién es, porque
  para quitarla habría que retocar el centro de la imagen.

Las dos las pone [`herramientas/marcar_fotos.py`](../../herramientas/marcar_fotos.py), que
también recorta, reduce y guarda la foto por debajo de 300 KB.

## La marca de agua entra en la paleta, y es a propósito

`paletas.json` se calcula con estas mismas fotos, marca incluida. Así el botón «Probar con
una de ejemplo», que manda a la API la foto publicada, devuelve exactamente la paleta del
feed: es la prueba de que el k-means es determinista y de que el feed no miente.

El precio es que en las fotos oscuras la crema de la marca sale como un color claro más
(en «Estrellas y tejado», un `#ccc8c2` en modo presentes). Es un color que de verdad está en la
foto que ves.

## Cambiarlas por otras

1. Borra las que sobren.
2. Prepara cada foto nueva con su recorte (en píxeles de la original) y déjala aquí:

   ```bash
   python herramientas/marcar_fotos.py original.jpg web/fotos/nombre.jpg --recorte X,Y,ANCHO,ALTO
   ```

   Sale en JPG, a 1600 px de lado mayor como mucho, por debajo de 300 KB y con las dos
   marcas. Para una foto que ya tenga el símbolo, `--sin-simbolo`.
3. Edita `fotos.json` con un objeto por foto:

   ```json
   [
     {
       "archivo": "atardecer-cala-figuera.jpg",
       "nombre": "Atardecer en Cala Figuera",
       "alt": "El sol poniéndose detrás de las barcas, con el cielo naranja."
     }
   ]
   ```

   El `alt` no es opcional: es lo que oye quien no ve la foto, y cuenta para el
   Lighthouse de accesibilidad (I02).

   La `nota` sí es opcional: una línea que sale bajo la paleta en el feed. Es para la foto
   cuya paleta parece un error sin serlo. En «Piscina de bolas», las bolas se ven verde
   menta y la paleta sale gris, pero los píxeles de las bolas son un gris apenas azulado (de
   media, `#727b7d`; las zonas iluminadas, gris neutro): es la luz cálida de la sala la
   que hace que el ojo las vea verdes.

4. Súbelo a `main` con un PR. Al mergearlo, el despliegue calcula las paletas con el código
   de la API, publica la web y abre otro PR con el `paletas.json` nuevo para guardarlo en el
   repositorio. Todo esto se puede hacer desde la
   web de GitHub, sin tener Java instalado.

   Para verlo antes en local, regenera las paletas desde la raíz del repo:

   ```bash
   mvn compile exec:java@paletas
   ```

Si borras `fotos.json`, el generador coge todas las imágenes de la carpeta, usa el nombre
del archivo y deja el `alt` vacío. Sirve para probar, no para publicar.
