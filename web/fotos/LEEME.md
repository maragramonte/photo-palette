# Las fotos del feed

Son seis fotos reales, hechas con el móvil: un eclipse, un ala de avión, una piscina de
bolas, la luna y dos cielos de noche. Están reducidas a 1600 px y sin metadatos EXIF.

Cada una lleva mi símbolo (el de `favicon.svg`, solo la M en crema) como marca de agua en la
esquina inferior derecha, dentro del recorte 4:5 de las tarjetas para que no se corte.

## La marca de agua entra en la paleta, y es a propósito

`paletas.json` se calcula con estas mismas fotos, marca incluida. Así el botón «Probar con
una de ejemplo», que manda a la API la foto publicada, devuelve exactamente la paleta del
feed: es la prueba de que el k-means es determinista y de que el feed no miente.

El precio es que en las fotos oscuras la crema de la marca sale como un color claro más
(en «Cielo estrellado», un `#ccc6bd` en modo vivos). Es un color que de verdad está en la
foto que ves.

## Cambiarlas por otras

1. Borra las que sobren.
2. Copia aquí las nuevas en **JPG o PNG** (la API no lee WebP), a 1600 px de lado
   mayor y por debajo de 300 KB cada una.
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

4. Regenera las paletas desde la raíz del repo:

   ```bash
   mvn compile exec:java@paletas
   ```

Si borras `fotos.json`, el generador coge todas las imágenes de la carpeta, usa el nombre
del archivo y deja el `alt` vacío. Sirve para probar, no para publicar.
