package com.maragramonte.photopalette.dominio;

/** El parámetro {@code modo} no es {@code presentes} ni {@code vivos}. */
public class ModoInvalidoException extends RuntimeException {

    public ModoInvalidoException(String valor) {
        super("El modo '" + valor + "' no existe. Usa 'presentes' o 'vivos'.");
    }
}
