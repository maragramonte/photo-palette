package com.maragramonte.photopalette.dominio;

/** Ya hay tantos análisis en curso como el servidor admite. Se responde 503. */
public class DemasiadosAnalisisException extends RuntimeException {

    public DemasiadosAnalisisException() {
        super("Hay demasiados análisis en curso. Prueba otra vez en unos segundos.");
    }
}
