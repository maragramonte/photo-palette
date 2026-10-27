package com.maragramonte.photopalette.dominio;

/**
 * Un color de la paleta, tal y como viaja en el JSON.
 *
 * @param hex        el color en notación CSS, por ejemplo {@code #d18d67}
 * @param rgb        las tres componentes de 0 a 255, en este orden
 * @param porcentaje cuánto de la foto ocupa, de 0 a 100, con un decimal
 * @param variable   el nombre de la variable CSS que le toca, por ejemplo {@code --color-1}
 */
public record ColorPaleta(String hex, int[] rgb, double porcentaje, String variable) {

    public static ColorPaleta de(int r, int g, int b, double porcentaje, int posicion) {
        return new ColorPaleta(
                String.format("#%02x%02x%02x", r, g, b),
                new int[] {r, g, b},
                Math.round(porcentaje * 10.0) / 10.0,
                "--color-" + posicion);
    }
}
