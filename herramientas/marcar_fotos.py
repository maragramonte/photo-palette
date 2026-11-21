#!/usr/bin/env python3
"""Prepara una foto para el feed: recorte, tamaño, símbolo y firma.

    python herramientas/marcar_fotos.py ENTRADA SALIDA [--recorte X,Y,ANCHO,ALTO] [--sin-simbolo]

- Recorta la zona indicada (en píxeles de la foto original) y la deja como mucho a 1600 px
  de lado mayor. Sin --recorte se usa la foto entera.
- Pone el símbolo (la M de favicon.svg, en crema) abajo a la derecha, con una sombra suave
  si esa esquina es clara. --sin-simbolo es para las fotos que ya lo llevan.
- Escribe «m.agramonte» en el centro, translúcido. No impide descargar la foto, pero sí
  usarla sin que se vea de quién es: para quitarlo habría que retocar el centro de la imagen.
- Guarda un JPG sin metadatos EXIF por debajo de 300 KB.

Necesita Pillow, rsvg-convert y la fuente Montserrat (variable) instalada en el sistema.
"""
import argparse
import io
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageStat

LADO_MAXIMO = 1600
PESO_MAXIMO = 300 * 1024
CREMA = (247, 241, 232)
FIRMA = "m.agramonte"

# Medidas de las fotos que ya están en el feed, en proporción al ancho.
ANCHO_SIMBOLO = 0.075
MARGEN_SIMBOLO = 0.036
ANCHO_FIRMA = 0.46
OPACIDAD_FIRMA = 0.5

FUENTE = Path("/usr/share/fonts/truetype/google-fonts/ofl/montserrat/Montserrat[wght].ttf")

# Solo los dos trazos de la M de favicon.svg, sin el cuadrado naranja de fondo.
SIMBOLO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 88 50">
  <g fill="#F7F1E8">
    <path d="M1.2,38.3 L13,8.3 C15.6,1.5 24.2,0.65 28.8,6.7 L46.5,30 C49.2,33.5 54.2,36.1 62,35.9 C66.2,35.8 67.2,39.6 63.6,41.2 C59.6,43.2 56.5,46.9 50.5,46.7 C46.5,46.5 43.6,44.7 41.5,41.7 L20.2,11.5 Q19,10 18,11.5 L9.8,36 C8.2,40.6 4.6,42.6 2.2,42 C0.6,41.6 0.4,40.3 1.2,38.3 Z"/>
    <path d="M44.8,22.7 L56,6 C59.2,1.3 64.9,1 67.4,6.5 L85.4,46 C87.2,50 78.6,51.3 77,48.3 L58.8,14.5 C57.5,12.1 55.7,12 54.5,13.5 L46.2,24.3 C45.5,25 44.2,24 44.8,22.7 Z"/>
  </g>
</svg>"""


def simbolo(ancho):
    png = subprocess.run(
        ["rsvg-convert", "-w", str(ancho), "-f", "png"],
        input=SIMBOLO_SVG.encode(),
        capture_output=True,
        check=True,
    ).stdout
    return Image.open(io.BytesIO(png)).convert("RGBA")


def con_sombra(capa, radio, opacidad):
    """La capa con una sombra difusa debajo, para que la crema no se pierda sobre lo claro."""
    alfa = capa.getchannel("A").filter(ImageFilter.GaussianBlur(radio))
    sombra = Image.new("RGBA", capa.size, (0, 0, 0, 0))
    sombra.putalpha(alfa.point(lambda a: int(a * opacidad)))
    return Image.alpha_composite(sombra, capa)


def poner_simbolo(foto):
    ancho, alto = foto.size
    marca = simbolo(round(ancho * ANCHO_SIMBOLO))
    margen = round(ancho * MARGEN_SIMBOLO)
    x, y = ancho - marca.width - margen, alto - marca.height - margen

    # Mismo margen alrededor para que la sombra no se corte.
    hueco = margen // 2
    lienzo = Image.new("RGBA", (marca.width + 2 * hueco, marca.height + 2 * hueco), (0, 0, 0, 0))
    lienzo.paste(marca, (hueco, hueco))
    esquina = foto.crop((x, y, x + marca.width, y + marca.height)).convert("L")
    if ImageStat.Stat(esquina).mean[0] > 150:
        lienzo = con_sombra(lienzo, radio=max(2, marca.width // 18), opacidad=0.55)
    foto.alpha_composite(lienzo, (x - hueco, y - hueco))


def poner_firma(foto):
    ancho, alto = foto.size
    fuente = ImageFont.truetype(str(FUENTE), 100)
    fuente.set_variation_by_name("SemiBold")
    caja = fuente.getbbox(FIRMA)
    tamano = round(100 * ancho * ANCHO_FIRMA / (caja[2] - caja[0]))
    fuente = ImageFont.truetype(str(FUENTE), tamano)
    fuente.set_variation_by_name("SemiBold")

    capa = Image.new("RGBA", foto.size, (0, 0, 0, 0))
    ImageDraw.Draw(capa).text(
        (ancho / 2, alto / 2), FIRMA, font=fuente, anchor="mm",
        fill=CREMA + (round(255 * OPACIDAD_FIRMA),),
    )
    # Una sombra muy suave: sin ella la firma desaparece sobre cielo o nubes.
    foto.alpha_composite(con_sombra(capa, radio=max(2, tamano // 14), opacidad=0.45))


def guardar(foto, destino):
    """Baja la calidad hasta 76 y, si aún pesa demasiado, reduce la foto un 10 % y vuelve a
    empezar: en fotos con mucho detalle (flores, hojas) bajar más la calidad se nota antes
    que perder algo de tamaño."""
    rgb = foto.convert("RGB")
    while True:
        for calidad in range(88, 75, -4):
            buffer = io.BytesIO()
            rgb.save(buffer, "JPEG", quality=calidad, optimize=True, progressive=True)
            if buffer.tell() <= PESO_MAXIMO:
                Path(destino).write_bytes(buffer.getvalue())
                return rgb.size, calidad, buffer.tell()
        rgb = rgb.resize((round(rgb.width * 0.9), round(rgb.height * 0.9)), Image.LANCZOS)


def main():
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("entrada")
    argumentos.add_argument("salida")
    argumentos.add_argument("--recorte", help="X,Y,ANCHO,ALTO en píxeles de la original")
    argumentos.add_argument("--sin-simbolo", action="store_true")
    opciones = argumentos.parse_args()

    foto = Image.open(opciones.entrada).convert("RGBA")
    if opciones.recorte:
        x, y, w, h = (int(v) for v in opciones.recorte.split(","))
        foto = foto.crop((x, y, x + w, y + h))
    foto.thumbnail((LADO_MAXIMO, LADO_MAXIMO), Image.LANCZOS)

    if not opciones.sin_simbolo:
        poner_simbolo(foto)
    poner_firma(foto)
    (ancho, alto), calidad, peso = guardar(foto, opciones.salida)
    print(f"{opciones.salida}: {ancho}x{alto}, calidad {calidad}, {peso // 1024} KB")


if __name__ == "__main__":
    main()
