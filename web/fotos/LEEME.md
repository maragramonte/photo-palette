# Las fotos del feed

Las seis `marcador-0N.jpg` son **relleno generado**, no fotos. Están para que el feed
arranque y se pueda probar el circuito entero antes de tener las fotos de verdad.

## Cambiarlas por las tuyas (tarea D03)

1. Borra los `marcador-0N.jpg`.
2. Copia aquí tus seis fotos en **JPG o PNG** (la API no lee WebP), a 1600 px de lado
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
