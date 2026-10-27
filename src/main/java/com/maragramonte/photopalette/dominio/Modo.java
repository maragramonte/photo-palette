package com.maragramonte.photopalette.dominio;

/**
 * Cómo se eligen y se ordenan los colores de la paleta.
 *
 * <p>{@link #PRESENTES} devuelve los colores que más píxeles ocupan. Es fiel a la foto,
 * pero en una foto oscura la paleta sale apagada: en un atardecer, los dos tonos más
 * oscuros pueden sumar el 64 % y el naranja del sol apenas el 6 %.
 *
 * <p>{@link #VIVOS} busca los colores que se recuerdan de la foto, no los que la rellenan.
 */
public enum Modo {

    PRESENTES,
    VIVOS;

    /** Convierte el valor del parámetro {@code modo} de la API. */
    public static Modo desdeParametro(String valor) {
        if (valor == null || valor.isBlank()) {
            return PRESENTES;
        }
        return switch (valor.trim().toLowerCase()) {
            case "presentes" -> PRESENTES;
            case "vivos" -> VIVOS;
            default -> throw new ModoInvalidoException(valor);
        };
    }

    /** El valor tal y como viaja en el JSON. */
    public String parametro() {
        return name().toLowerCase();
    }
}
