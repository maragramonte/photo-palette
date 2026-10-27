package com.maragramonte.photopalette.dominio;

import java.util.List;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Analiza una foto y devuelve su paleta, con un tope de análisis simultáneos.
 *
 * <p>El k-means es lo único caro de esta API: con dos análisis a la vez el contenedor se
 * mantiene holgado dentro de sus 512 MB. El tercero recibe un 503 y el frontend reintenta,
 * que es mucho mejor que encolar peticiones hasta que el contenedor muera y se lleve por
 * delante a los vecinos del VPS.
 */
@Service
public class ServicioPaletas {

    private final LectorImagen lector;
    private final ExtractorPaleta extractor;
    private final Semaphore enCurso;

    public ServicioPaletas(
            LectorImagen lector,
            ExtractorPaleta extractor,
            @Value("${photopalette.analisis-simultaneos:2}") int analisisSimultaneos) {
        this.lector = lector;
        this.extractor = extractor;
        this.enCurso = new Semaphore(analisisSimultaneos);
    }

    public RespuestaPaleta analizar(byte[] imagen, int n, Modo modo) {
        if (!enCurso.tryAcquire()) {
            throw new DemasiadosAnalisisException();
        }
        try {
            int[] pixeles = lector.muestrear(imagen);
            List<ColorPaleta> colores = extractor.extraer(pixeles, n, modo);
            return RespuestaPaleta.de(modo, colores);
        } finally {
            enCurso.release();
        }
    }
}
