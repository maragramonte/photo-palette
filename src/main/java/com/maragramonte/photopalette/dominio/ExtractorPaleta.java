package com.maragramonte.photopalette.dominio;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Agrupa los píxeles de una foto con k-means y devuelve la paleta.
 *
 * <p>El algoritmo es siempre el mismo: k-means++ sobre el espacio RGB, con semilla fija
 * para que la misma foto dé siempre la misma paleta. Lo que cambia entre modos es qué
 * píxeles entran y cómo se ordenan los grupos que salen.
 *
 * <ul>
 *   <li><b>presentes</b>: {@code k = n}, orden por número de píxeles. Es fiel a la foto.</li>
 *   <li><b>vivos</b>: {@code k = 3n} para que los colores minoritarios tengan grupo propio;
 *       se ignoran los píxeles casi negros, se descartan los grupos con menos del 0,5 % de
 *       los píxeles con luz, se ordena por saturación y se descartan los tonos parecidos a
 *       uno ya elegido.</li>
 * </ul>
 */
@Component
public class ExtractorPaleta {

    public static final int MIN_COLORES = 2;
    public static final int MAX_COLORES = 12;

    /** Por debajo de este brillo, el píxel es sombra: en modo vivos no cuenta. */
    static final float BRILLO_MINIMO_VIVOS = 0.15f;

    /** Un grupo con menos de este porcentaje de píxeles con luz es ruido. */
    static final double MIN_PORCENTAJE_VIVOS = 0.5;

    /** Dos colores con menos de estos grados de diferencia de tono comparten tono. */
    static final float TONO_MINIMO_GRADOS = 25f;

    /**
     * Dos colores del mismo tono siguen siendo dos colores si su brillo se separa esto.
     *
     * <p>Sin esta segunda condicion, una foto calida entera se queda en una sola muestra:
     * el marron de la sombra y el naranja del sol comparten tono. Para quien va a copiar la
     * paleta a una web, ese par es justo lo util, porque uno sirve de fondo y el otro de
     * acento. Lo que sobra son los cuatro azules casi iguales de un cielo nocturno, y esos
     * siguen colapsando porque comparten tono Y brillo.
     */
    static final float BRILLO_MINIMO_ENTRE_TONOS = 0.25f;

    /** Por debajo de esta saturación el tono no significa nada: es un gris. */
    static final float SATURACION_GRIS = 0.12f;

    /** Dos grises se distinguen solo si su brillo se separa esto. */
    static final float BRILLO_MINIMO_ENTRE_GRISES = 0.25f;

    private static final int MAX_ITERACIONES = 30;
    private static final long SEMILLA = 42L;

    public List<ColorPaleta> extraer(int[] pixeles, int n, Modo modo) {
        if (n < MIN_COLORES || n > MAX_COLORES) {
            throw new NumeroColoresInvalidoException(n);
        }
        if (pixeles == null || pixeles.length == 0) {
            throw new ImagenIlegibleException("La imagen no tiene píxeles que analizar.");
        }
        List<Grupo> grupos = modo == Modo.VIVOS ? gruposVivos(pixeles, n) : gruposPresentes(pixeles, n);
        return aPaleta(grupos);
    }

    // --- modos -------------------------------------------------------------

    private List<Grupo> gruposPresentes(int[] pixeles, int n) {
        List<Grupo> grupos = agrupar(pixeles, n);
        grupos.sort(Comparator.comparingInt(Grupo::cuantos).reversed());
        return grupos.subList(0, Math.min(n, grupos.size()));
    }

    private List<Grupo> gruposVivos(int[] pixeles, int n) {
        int[] conLuz = soloConLuz(pixeles);
        // Una foto entera en sombra no tiene colores vivos; se analiza tal cual
        // antes que devolver una paleta vacía.
        if (conLuz.length == 0) {
            conLuz = pixeles;
        }

        List<Grupo> grupos = agrupar(conLuz, n * 3);
        List<Grupo> conPeso = new ArrayList<>();
        for (Grupo grupo : grupos) {
            if (grupo.porcentaje() >= MIN_PORCENTAJE_VIVOS) {
                conPeso.add(grupo);
            }
        }
        // Si todos los grupos son diminutos, al menos se devuelve el mayor.
        if (conPeso.isEmpty()) {
            grupos.sort(Comparator.comparingInt(Grupo::cuantos).reversed());
            conPeso.add(grupos.get(0));
        }

        conPeso.sort(Comparator.comparingDouble((Grupo grupo) -> grupo.hsb()[1])
                .thenComparingInt(Grupo::cuantos)
                .reversed());

        List<Grupo> elegidos = new ArrayList<>();
        for (Grupo candidato : conPeso) {
            if (elegidos.size() == n) {
                break;
            }
            boolean repetido = false;
            for (Grupo elegido : elegidos) {
                if (mismoColor(elegido.hsb(), candidato.hsb())) {
                    repetido = true;
                    break;
                }
            }
            if (!repetido) {
                elegidos.add(candidato);
            }
        }
        return elegidos;
    }

    private static int[] soloConLuz(int[] pixeles) {
        int[] filtrados = new int[pixeles.length];
        int cuantos = 0;
        float[] hsb = new float[3];
        for (int pixel : pixeles) {
            Color.RGBtoHSB((pixel >> 16) & 0xff, (pixel >> 8) & 0xff, pixel & 0xff, hsb);
            if (hsb[2] >= BRILLO_MINIMO_VIVOS) {
                filtrados[cuantos++] = pixel;
            }
        }
        return Arrays.copyOf(filtrados, cuantos);
    }

    /** Dos colores son el mismo si comparten tono, o si los dos son grises de brillo parecido. */
    static boolean mismoColor(float[] a, float[] b) {
        boolean grisA = a[1] < SATURACION_GRIS;
        boolean grisB = b[1] < SATURACION_GRIS;
        if (grisA != grisB) {
            return false;
        }
        if (grisA) {
            return Math.abs(a[2] - b[2]) < BRILLO_MINIMO_ENTRE_GRISES;
        }
        return distanciaTono(a[0], b[0]) < TONO_MINIMO_GRADOS
                && Math.abs(a[2] - b[2]) < BRILLO_MINIMO_ENTRE_TONOS;
    }

    /** El tono es un círculo: 350 y 10 grados están a 20 de distancia, no a 340. */
    static float distanciaTono(float tonoA, float tonoB) {
        float diferencia = Math.abs(tonoA - tonoB) * 360f;
        return Math.min(diferencia, 360f - diferencia);
    }

    private static List<ColorPaleta> aPaleta(List<Grupo> grupos) {
        List<ColorPaleta> paleta = new ArrayList<>(grupos.size());
        for (int i = 0; i < grupos.size(); i++) {
            Grupo grupo = grupos.get(i);
            paleta.add(ColorPaleta.de(grupo.r(), grupo.g(), grupo.b(), grupo.porcentaje(), i + 1));
        }
        return paleta;
    }

    // --- k-means -----------------------------------------------------------

    /**
     * k-means++ sobre RGB. Devuelve solo los grupos con píxeles: pedir más grupos que
     * colores distintos tiene la imagen es normal, y simplemente salen menos.
     */
    private List<Grupo> agrupar(int[] pixeles, int k) {
        int cuantosGrupos = Math.min(k, pixeles.length);
        double[][] centros = sembrar(pixeles, cuantosGrupos);
        int[] asignado = new int[pixeles.length];
        Arrays.fill(asignado, -1);

        for (int iteracion = 0; iteracion < MAX_ITERACIONES; iteracion++) {
            boolean cambio = asignar(pixeles, centros, asignado);
            recalcular(pixeles, asignado, centros);
            if (!cambio) {
                break;
            }
        }
        return recuento(pixeles, asignado, centros);
    }

    /** k-means++: el primer centro al azar y cada siguiente lejos de los ya elegidos. */
    private double[][] sembrar(int[] pixeles, int k) {
        Random azar = new Random(SEMILLA);
        double[][] centros = new double[k][3];
        centros[0] = aComponentes(pixeles[azar.nextInt(pixeles.length)]);

        double[] distancias = new double[pixeles.length];
        Arrays.fill(distancias, Double.MAX_VALUE);

        for (int c = 1; c < k; c++) {
            double suma = 0;
            for (int i = 0; i < pixeles.length; i++) {
                distancias[i] = Math.min(distancias[i], distancia(pixeles[i], centros[c - 1]));
                suma += distancias[i];
            }
            if (suma == 0) { // no queda ningún píxel distinto de los centros ya elegidos
                centros[c] = centros[c - 1].clone();
                continue;
            }
            double objetivo = azar.nextDouble() * suma;
            double acumulado = 0;
            int elegido = pixeles.length - 1;
            for (int i = 0; i < pixeles.length; i++) {
                acumulado += distancias[i];
                if (acumulado >= objetivo) {
                    elegido = i;
                    break;
                }
            }
            centros[c] = aComponentes(pixeles[elegido]);
        }
        return centros;
    }

    private boolean asignar(int[] pixeles, double[][] centros, int[] asignado) {
        boolean cambio = false;
        for (int i = 0; i < pixeles.length; i++) {
            int mejor = 0;
            double mejorDistancia = Double.MAX_VALUE;
            for (int c = 0; c < centros.length; c++) {
                double d = distancia(pixeles[i], centros[c]);
                if (d < mejorDistancia) {
                    mejorDistancia = d;
                    mejor = c;
                }
            }
            if (asignado[i] != mejor) {
                asignado[i] = mejor;
                cambio = true;
            }
        }
        return cambio;
    }

    private void recalcular(int[] pixeles, int[] asignado, double[][] centros) {
        double[][] sumas = new double[centros.length][3];
        int[] cuantos = new int[centros.length];
        for (int i = 0; i < pixeles.length; i++) {
            int c = asignado[i];
            sumas[c][0] += (pixeles[i] >> 16) & 0xff;
            sumas[c][1] += (pixeles[i] >> 8) & 0xff;
            sumas[c][2] += pixeles[i] & 0xff;
            cuantos[c]++;
        }
        for (int c = 0; c < centros.length; c++) {
            if (cuantos[c] == 0) {
                continue; // grupo vacío: se queda donde está y se descarta al contar
            }
            centros[c][0] = sumas[c][0] / cuantos[c];
            centros[c][1] = sumas[c][1] / cuantos[c];
            centros[c][2] = sumas[c][2] / cuantos[c];
        }
    }

    private List<Grupo> recuento(int[] pixeles, int[] asignado, double[][] centros) {
        int[] cuantos = new int[centros.length];
        for (int c : asignado) {
            cuantos[c]++;
        }
        List<Grupo> grupos = new ArrayList<>();
        for (int c = 0; c < centros.length; c++) {
            if (cuantos[c] > 0) {
                grupos.add(Grupo.de(centros[c], cuantos[c], pixeles.length));
            }
        }
        return grupos;
    }

    private static double distancia(int pixel, double[] centro) {
        double dr = ((pixel >> 16) & 0xff) - centro[0];
        double dg = ((pixel >> 8) & 0xff) - centro[1];
        double db = (pixel & 0xff) - centro[2];
        return dr * dr + dg * dg + db * db;
    }

    private static double[] aComponentes(int pixel) {
        return new double[] {(pixel >> 16) & 0xff, (pixel >> 8) & 0xff, pixel & 0xff};
    }

    /** Un grupo del k-means, ya redondeado a color entero. */
    private record Grupo(int r, int g, int b, int cuantos, double porcentaje, float[] hsb) {

        static Grupo de(double[] centro, int cuantos, int total) {
            int r = acotar(centro[0]);
            int g = acotar(centro[1]);
            int b = acotar(centro[2]);
            return new Grupo(r, g, b, cuantos, 100.0 * cuantos / total, Color.RGBtoHSB(r, g, b, null));
        }

        private static int acotar(double valor) {
            return Math.max(0, Math.min(255, (int) Math.round(valor)));
        }
    }
}
