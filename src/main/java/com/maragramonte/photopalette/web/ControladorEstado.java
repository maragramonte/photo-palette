package com.maragramonte.photopalette.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/photo-palette}: responde 200 sin cuerpo si la API está en pie.
 *
 * <p>La web lo consulta al cargar y, si falla, desactiva la subida con un aviso. El feed
 * sigue funcionando porque se sirve de {@code paletas.json}, que es estático. El
 * {@code HEALTHCHECK} del Dockerfile pregunta aquí también.
 *
 * <p>Queda fuera del límite por IP a propósito: si el navegador recibiese un 429 al
 * comprobar si la API está viva, la web desactivaría la subida sin motivo.
 */
@RestController
public class ControladorEstado {

    @GetMapping("/api/photo-palette")
    @ResponseStatus(HttpStatus.OK)
    public void estado() {
        // Sin cuerpo: el código 200 es toda la respuesta.
    }
}
