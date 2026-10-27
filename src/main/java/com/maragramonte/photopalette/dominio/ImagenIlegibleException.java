package com.maragramonte.photopalette.dominio;

/** El archivo no es un JPG ni un PNG que se pueda decodificar. */
public class ImagenIlegibleException extends RuntimeException {

    public ImagenIlegibleException(String detalle) {
        super(detalle);
    }
}
