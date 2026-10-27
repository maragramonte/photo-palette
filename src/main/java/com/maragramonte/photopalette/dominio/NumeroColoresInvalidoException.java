package com.maragramonte.photopalette.dominio;

/** El parámetro {@code n} está fuera del rango permitido. */
public class NumeroColoresInvalidoException extends RuntimeException {

    public NumeroColoresInvalidoException(int n) {
        super("Has pedido " + n + " colores. El mínimo es " + ExtractorPaleta.MIN_COLORES
                + " y el máximo " + ExtractorPaleta.MAX_COLORES + ".");
    }
}
