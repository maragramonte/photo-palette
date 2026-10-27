package com.maragramonte.photopalette.web;

import com.maragramonte.photopalette.dominio.ImagenAusenteException;
import com.maragramonte.photopalette.dominio.ImagenIlegibleException;
import com.maragramonte.photopalette.dominio.Modo;
import com.maragramonte.photopalette.dominio.RespuestaPaleta;
import com.maragramonte.photopalette.dominio.ServicioPaletas;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El único endpoint de análisis: {@code POST /api/paletas?n=6&modo=vivos}.
 *
 * <p>La foto se analiza en memoria y no se guarda en ningún momento.
 */
@RestController
public class ControladorPaletas {

    private final ServicioPaletas servicio;

    public ControladorPaletas(ServicioPaletas servicio) {
        this.servicio = servicio;
    }

    @PostMapping(path = "/api/paletas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RespuestaPaleta analizar(
            @RequestParam("imagen") MultipartFile imagen,
            @RequestParam(name = "n", defaultValue = "6") int n,
            @RequestParam(name = "modo", required = false) String modo) {

        if (imagen == null || imagen.isEmpty()) {
            throw new ImagenAusenteException("No has adjuntado ninguna imagen en el campo 'imagen'.");
        }
        try {
            return servicio.analizar(imagen.getBytes(), n, Modo.desdeParametro(modo));
        } catch (IOException e) {
            throw new ImagenIlegibleException("No se ha podido leer el archivo subido.");
        }
    }
}
