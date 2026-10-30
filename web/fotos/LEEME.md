# Las fotos del feed

Son seis fotos reales, hechas con el móvil: un eclipse, un ala de avión, una piscina de
bolas, la luna y dos cielos de noche. Están reducidas a 1600 px y sin metadatos EXIF.

Cada una lleva mi símbolo (el de `favicon.svg`, solo la M en crema) como marca de agua en la
esquina inferior derecha, dentro del recorte 4:5 de las tarjetas para que no se corte.

## La marca de agua no entra en la paleta

Si se generan las paletas con estas fotos, la crema de la marca sale como un color más: en
los cielos de noche llega a ser uno de los «vivos». Por eso `paletas.json` se ha calculado
con copias **sin marca** de las mismas fotos, y después se ha puesto en `peso` el tamaño de
las que se publican. La subida y el botón de foto de ejemplo, en cambio, mandan a la API la
foto con marca, igual que haría cualquiera que la descargase.

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

   Si las fotos llevan marca de agua, pon las copias sin marca y `fotos.json` en otra
   carpeta que también se llame `fotos` y pásasela al generador:

   ```bash
   mvn compile exec:java -Dexec.mainClass=com.maragramonte.photopalette.cli.GeneradorPaletasJson      -Dexec.args="/ruta/a/limpias/fotos web/paletas.json"
   ```

   Luego corrige el `peso` de cada foto con el tamaño del archivo de `web/fotos`.

Si borras `fotos.json`, el generador coge todas las imágenes de la carpeta, usa el nombre
del archivo y deja el `alt` vacío. Sirve para probar, no para publicar.
