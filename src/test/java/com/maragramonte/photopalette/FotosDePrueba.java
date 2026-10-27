package com.maragramonte.photopalette;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * Fotos sintéticas con proporciones conocidas, para poder afirmar cosas sobre la paleta.
 *
 * <p>Las fotos reales del feed no sirven como material de test: si mañana se cambia una, los
 * tests fallarían sin que nada esté roto. Aquí cada imagen se construye con bandas de
 * porcentaje exacto, así que lo que se afirma sobre la paleta es comprobable a mano.
 */
public final class FotosDePrueba {

    private FotosDePrueba() {}

    /** Una banda horizontal de color plano que ocupa {@code fraccion} de la altura. */
    public record Banda(int rgb, double fraccion) {}

    /**
     * Un atardecer, con las proporciones del caso real: los dos tonos más oscuros suman el
     * 72 % y el naranja del sol apenas el 6 %. Es la foto que justifica el modo vivos.
     */
    public static BufferedImage atardecer() {
        return bandas(
                200,
                200,
                new Banda(0x0a0a14, 0.40), // casi negro: en vivos no cuenta
                new Banda(0x1f3850, 0.32), // azul acero del cielo
                new Banda(0x3f7a4a, 0.16), // verde de los pinos
                new Banda(0x8a2a4a, 0.06), // vino de las nubes
                new Banda(0xff8c2a, 0.06)); // el naranja del sol
    }

    /** Dónde está el sol del {@link #atardecer()}. */
    public static final String NARANJA_DEL_SOL = "#ff8c2a";

    /**
     * Un cielo estrellado: cuatro azules casi iguales y unas pocas estrellas blancas. En
     * modo vivos tiene que dar muy pocos colores, porque los cuatro azules son el mismo tono.
     */
    public static BufferedImage cieloEstrellado() {
        return bandas(
                200,
                200,
                new Banda(0x141c3c, 0.25),
                new Banda(0x18224a, 0.25),
                new Banda(0x1c2858, 0.245),
                new Banda(0x202e66, 0.245),
                new Banda(0xffffff, 0.01)); // las estrellas
    }

    /** Una foto de un solo color plano. */
    public static BufferedImage unColor(int rgb) {
        return bandas(100, 100, new Banda(rgb, 1.0));
    }

    /** Casi todo negro, con una mancha roja pequeña pero muy saturada. */
    public static BufferedImage nocheConUnaLuz() {
        return bandas(100, 100, new Banda(0x050508, 0.97), new Banda(0xe01020, 0.03));
    }

    /** Solo grises: ningún tono, solo brillos distintos. */
    public static BufferedImage grises() {
        return bandas(
                100,
                100,
                new Banda(0x303030, 0.25),
                new Banda(0x606060, 0.25),
                new Banda(0x989898, 0.25),
                new Banda(0xd8d8d8, 0.25));
    }

    /** Un PNG con mitad transparente y mitad de un color plano. */
    public static BufferedImage mitadTransparente(int rgb) {
        BufferedImage imagen = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        for (int y = 50; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                imagen.setRGB(x, y, 0xff000000 | rgb);
            }
        }
        return imagen;
    }

    /** Un PNG entero transparente: no aporta ni un color. */
    public static BufferedImage todoTransparente() {
        return new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    }

    /** Una foto grande de verdad, para comprobar el submuestreo. */
    public static BufferedImage deTamano(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < alto; y++) {
            int fila = Color.HSBtoRGB((float) y / alto, 0.7f, 0.8f);
            for (int x = 0; x < ancho; x++) {
                imagen.setRGB(x, y, fila);
            }
        }
        return imagen;
    }

    public static BufferedImage bandas(int ancho, int alto, Banda... bandas) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        int y = 0;
        for (int i = 0; i < bandas.length; i++) {
            int hasta = i == bandas.length - 1 ? alto : y + (int) Math.round(alto * bandas[i].fraccion());
            for (; y < Math.min(hasta, alto); y++) {
                for (int x = 0; x < ancho; x++) {
                    imagen.setRGB(x, y, bandas[i].rgb());
                }
            }
        }
        return imagen;
    }

    public static byte[] png(BufferedImage imagen) {
        return escribir(imagen, "png");
    }

    public static byte[] jpg(BufferedImage imagen) {
        return escribir(imagen, "jpeg");
    }

    private static byte[] escribir(BufferedImage imagen, String formato) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            if (!ImageIO.write(imagen, formato, salida)) {
                throw new IllegalStateException("Este JDK no sabe escribir " + formato);
            }
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un archivo de texto con nombre de foto: el caso del .txt renombrado a .jpg. */
    public static byte[] textoDisfrazadoDeFoto() {
        return "Esto no es una foto, es un txt al que alguien le cambió la extensión."
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Un PNG con la cabecera IHDR de una foto enorme y sin datos de imagen.
     *
     * <p>Sirve justo para lo que hace falta: comprobar que las dimensiones se rechazan
     * leyendo solo la cabecera, antes de decodificar un píxel. Un PNG de verdad de 48
     * megapíxeles ocuparía 192 MB en memoria solo para construirlo.
     */
    public static byte[] pngSoloCabecera(int ancho, int alto) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        byte[] ihdr = new byte[] {
            'I', 'H', 'D', 'R',
            (byte) (ancho >>> 24), (byte) (ancho >>> 16), (byte) (ancho >>> 8), (byte) ancho,
            (byte) (alto >>> 24), (byte) (alto >>> 16), (byte) (alto >>> 8), (byte) alto,
            8, // bits por componente
            2, // color verdadero, sin alfa
            0, 0, 0
        };
        escribirTrozo(salida, ihdr);
        escribirTrozo(salida, new byte[] {'I', 'E', 'N', 'D'});
        return salida.toByteArray();
    }

    private static void escribirTrozo(ByteArrayOutputStream salida, byte[] tipoYDatos) {
        int longitud = tipoYDatos.length - 4;
        salida.write(longitud >>> 24);
        salida.write(longitud >>> 16);
        salida.write(longitud >>> 8);
        salida.write(longitud);
        salida.writeBytes(tipoYDatos);
        CRC32 crc = new CRC32();
        crc.update(tipoYDatos);
        long valor = crc.getValue();
        salida.write((int) (valor >>> 24));
        salida.write((int) (valor >>> 16));
        salida.write((int) (valor >>> 8));
        salida.write((int) valor);
    }
}
