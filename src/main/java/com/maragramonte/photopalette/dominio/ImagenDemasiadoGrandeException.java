package com.maragramonte.photopalette.dominio;

/** La imagen supera el límite de megapíxeles que la API acepta analizar. */
public class ImagenDemasiadoGrandeException extends RuntimeException {

    public ImagenDemasiadoGrandeException(long megapixeles, long maximo) {
        super("La imagen tiene " + megapixeles + " megapíxeles y el máximo es " + maximo
                + ". Redúcela antes de subirla.");
    }
}
