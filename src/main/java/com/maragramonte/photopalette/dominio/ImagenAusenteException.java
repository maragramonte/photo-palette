package com.maragramonte.photopalette.dominio;

/** No ha llegado el campo imagen, o ha llegado vacío. Se responde 400. */
public class ImagenAusenteException extends RuntimeException {

    public ImagenAusenteException(String detalle) {
        super(detalle);
    }
}
