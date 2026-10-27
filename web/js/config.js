/*
 * Lo único que cambia entre tu portátil y GitHub Pages (tarea P03).
 *
 * `api` es la URL base de la API, sin barra final. Mientras esté vacía, el feed funciona
 * igual —sus paletas están en paletas.json, que es estático— y la subida aparece desactivada
 * con un aviso, en vez de fallar en silencio.
 *
 * En local no hay que tocar nada: si la página se abre en localhost, se asume que la API
 * está arrancada en el 8080 con el perfil dev, que es el que permite ese origen.
 */
(() => {
  const EN_LOCAL = ["localhost", "127.0.0.1", ""].includes(location.hostname);

  window.PHOTO_PALETTE = {
    // Publicado: pon aquí la URL de tu API en el VPS, por ejemplo "https://api.tudominio.com".
    api: EN_LOCAL ? "http://localhost:8080" : "",
    repositorio: "https://github.com/maragramonte/photo-palette",
  };
})();
