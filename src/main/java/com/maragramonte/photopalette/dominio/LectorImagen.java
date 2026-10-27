package com.maragramonte.photopalette.dominio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Convierte los bytes que llegan por HTTP en una muestra de píxeles pequeña y acotada.
 *
 * <p>La imagen nunca se decodifica entera. Primero se leen solo las dimensiones de la
 * cabecera y se rechaza la foto si pasa de {@link #MAX_MEGAPIXELES}; después se decodifica
 * saltando píxeles ({@code setSourceSubsampling}) hasta dejar unos {@link #PIXELES_OBJETIVO}.
 * Así una foto de 12 MP ocupa unos cientos de kilobytes en memoria, no 48 MB.
 *
 * <p>No se admite WebP: el {@code ImageIO} del JDK no lo lee.
 */
@Component
public class LectorImagen {

    /** Más de esto se rechaza con 422 antes de decodificar un solo píxel. */
    public static final long MAX_MEGAPIXELES = 40;

    /** Tamaño de la muestra con la que trabaja el k-means. */
    public static final int PIXELES_OBJETIVO = 40_000;

    private static final Set<String> FORMATOS = Set.of("jpeg", "jpg", "png");

    /**
     * @param datos el archivo subido, completo
     * @return los píxeles de la muestra, empaquetados como {@code 0xRRGGBB}; sin los
     *         totalmente transparentes
     */
    public int[] muestrear(byte[] datos) {
        if (datos == null || datos.length == 0) {
            throw new ImagenIlegibleException("El archivo está vacío.");
        }
        try (ImageInputStream flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            if (flujo == null) {
                throw new ImagenIlegibleException("No se ha podido abrir el archivo.");
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                throw new ImagenIlegibleException(
                        "El archivo no es un JPG ni un PNG. Comprueba que la extensión coincide con el contenido.");
            }
            ImageReader lector = lectores.next();
            try {
                return muestrearCon(lector, flujo);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new ImagenIlegibleException("El archivo está dañado o incompleto.");
        }
    }

    private int[] muestrearCon(ImageReader lector, ImageInputStream flujo) throws IOException {
        lector.setInput(flujo, true, true);

        String formato = lector.getFormatName().toLowerCase();
        if (!FORMATOS.contains(formato)) {
            throw new ImagenIlegibleException(
                    "El formato " + formato.toUpperCase() + " no se admite. Sube un JPG o un PNG.");
        }

        int ancho = lector.getWidth(0);
        int alto = lector.getHeight(0);
        long megapixeles = (long) ancho * alto / 1_000_000L;
        if (megapixeles > MAX_MEGAPIXELES) {
            throw new ImagenDemasiadoGrandeException(megapixeles, MAX_MEGAPIXELES);
        }

        ImageReadParam parametros = lector.getDefaultReadParam();
        int paso = calcularPaso(ancho, alto);
        parametros.setSourceSubsampling(paso, paso, 0, 0);

        BufferedImage imagen = lector.read(0, parametros);
        return extraerPixeles(imagen);
    }

    /** Cada cuántos píxeles se toma uno, para que la muestra ronde {@link #PIXELES_OBJETIVO}. */
    static int calcularPaso(int ancho, int alto) {
        double total = (double) ancho * alto;
        if (total <= PIXELES_OBJETIVO) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(Math.sqrt(total / PIXELES_OBJETIVO)));
    }

    private int[] extraerPixeles(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int[] argb = imagen.getRGB(0, 0, ancho, alto, null, 0, ancho);

        int[] pixeles = new int[argb.length];
        int cuantos = 0;
        for (int valor : argb) {
            // Un PNG transparente no aporta color: el fondo lo pone la web, no la foto.
            if (((valor >>> 24) & 0xff) < 16) {
                continue;
            }
            pixeles[cuantos++] = valor & 0xffffff;
        }
        if (cuantos == 0) {
            throw new ImagenIlegibleException("La imagen es completamente transparente.");
        }
        int[] ajustado = new int[cuantos];
        System.arraycopy(pixeles, 0, ajustado, 0, cuantos);
        return ajustado;
    }
}
