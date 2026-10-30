# La API

Un solo endpoint que hace el trabajo y otro que dice si está vivo. La foto se analiza en
memoria y no se guarda en ningún sitio.

← [Volver al README](../README.md)

---

## `POST /api/paletas`

`multipart/form-data`, con la foto en el campo `imagen`.

```bash
curl -X POST "https://photo-palette.onrender.com/api/paletas?n=6&modo=vivos" -F imagen=@foto.jpg
```

> **En PowerShell, `curl` no es curl**: es un alias de `Invoke-WebRequest`, que no entiende
> `-X` ni `-F` y falla antes de salir de tu máquina. Llama al de verdad por su nombre
> completo, y entrecomilla el `-F` para que PowerShell no parta el argumento por el `@`:
>
> ```powershell
> curl.exe -X POST "https://photo-palette.onrender.com/api/paletas?n=6&modo=vivos" -F "imagen=@foto.jpg"
> ```

La respuesta trae cada color con su HEX, su RGB, el porcentaje de la foto que ocupa y el
nombre de variable CSS que le toca, más el bloque `:root` ya montado:

```json
{
  "modo": "vivos",
  "colores": [
    { "hex": "#fe8402", "rgb": [254, 132, 2], "porcentaje": 15.9, "variable": "--color-1" }
  ],
  "css": ":root {\n  --color-1: #fe8402;\n}"
}
```

| Parámetro | Valores | Por defecto | Qué hace |
| --- | --- | --- | --- |
| `imagen` | JPG o PNG, máx. 10 MB | obligatorio | La foto. En memoria, sin guardarse |
| `n` | de 2 a 12 | 6 | Cuántos colores devolver |
| `modo` | `presentes` · `vivos` | `presentes` | Cómo se eligen y se ordenan |

| Código | Cuándo |
| --- | --- |
| `200` | Paleta calculada. En `vivos` pueden venir menos de `n` colores |
| `400` | Falta la imagen, o `n` o `modo` no son válidos |
| `413` | La imagen pasa de 10 MB |
| `422` | No es un JPG o PNG legible, o pasa de 40 megapíxeles |
| `429` | Esa IP ha superado 10 análisis por minuto |
| `503` | Ya hay 2 análisis en curso. Reintenta en unos segundos |

Por qué existe cada límite está en
[DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md#3-límites-pensados-para-compartir-servidor).

---

## Los errores

Van en **ProblemDetail (RFC 9457)**, con `title` y `detail` en español escritos para que los
lea una persona, no un log. La web pinta ese texto tal cual, así que el mensaje que ve quien
sube la foto lo escribió el backend para ella.

```json
{
  "type": "https://maragramonte.github.io/photo-palette/errores/imagen-ilegible",
  "title": "No se puede leer la imagen",
  "status": 422,
  "detail": "El archivo no es un JPG ni un PNG. Comprueba que la extensión coincide con el contenido.",
  "instance": "/api/paletas"
}
```

El `429` sale con su `Retry-After` y con las cabeceras CORS puestas, para que la web pueda
enseñar el motivo en lugar de un error de red genérico.

---

## `GET /api/salud`

Responde `200` sin cuerpo y queda fuera del límite por IP. La web lo consulta al cargar: si
falla, apaga la subida con un aviso y el feed sigue funcionando, porque sus paletas están en
un JSON estático.

En Render la instancia gratuita se duerme a los quince minutos sin tráfico. Mientras
despierta, la web enseña «Comprobando que la API responde…» en lugar de dejar la zona de
subida muda, y si no llega a responder ofrece un botón para volver a comprobarlo.

---

## Lo que no admite

**WebP.** El `ImageIO` del JDK no lo lee, y añadir la dependencia no compensa para la v1.
