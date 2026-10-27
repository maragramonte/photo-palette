package com.maragramonte.photopalette.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/salud}: responde 200 sin cuerpo.
 *
 * <p>La web lo consulta al cargar y, si falla, desactiva la subida con un aviso. El feed
 * sigue funcionando porque se sirve de {@code paletas.json}, que es estático.
 *
 * <p>Queda fuera del límite por IP a propósito: si el navegador recibiese un 429 al
 * comprobar la salud, la web desactivaría la subida sin motivo.
 */
@RestController
public class ControladorSalud {

    @GetMapping("/api/salud")
    @ResponseStatus(HttpStatus.OK)
    public void salud() {
        // Sin cuerpo: el código 200 es toda la respuesta.
    }
}
