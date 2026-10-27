package com.maragramonte.photopalette.dominio;

import java.util.List;

/**
 * La respuesta de {@code POST /api/paletas}.
 *
 * @param modo    el modo que se ha aplicado, ya resuelto (nunca nulo)
 * @param colores los colores encontrados; en modo vivos pueden ser menos de los pedidos
 * @param css     un bloque {@code :root} listo para pegar en una hoja de estilos
 */
public record RespuestaPaleta(String modo, List<ColorPaleta> colores, String css) {

    public static RespuestaPaleta de(Modo modo, List<ColorPaleta> colores) {
        return new RespuestaPaleta(modo.parametro(), colores, bloqueCss(colores));
    }

    private static String bloqueCss(List<ColorPaleta> colores) {
        StringBuilder css = new StringBuilder(":root {\n");
        for (ColorPaleta color : colores) {
            css.append("  ").append(color.variable()).append(": ").append(color.hex()).append(";\n");
        }
        return css.append("}").toString();
    }
}
