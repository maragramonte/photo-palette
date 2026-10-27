package com.maragramonte.photopalette.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maragramonte.photopalette.FotosDePrueba;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicioPaletasTest {

    private final byte[] foto = FotosDePrueba.png(FotosDePrueba.atardecer());

    @Test
    void devuelveLaPaletaConSuModoYSuCss() {
        ServicioPaletas servicio = new ServicioPaletas(new LectorImagen(), new ExtractorPaleta(), 2);

        RespuestaPaleta respuesta = servicio.analizar(foto, 4, Modo.VIVOS);

        assertThat(respuesta.modo()).isEqualTo("vivos");
        assertThat(respuesta.colores()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(respuesta.css()).startsWith(":root {").contains(respuesta.colores().get(0).hex());
    }

    @Test
    @DisplayName("con el cupo de análisis agotado, el siguiente recibe un 503 (B09)")
    void rechazaElAnalisisCuandoNoHayHueco() throws InterruptedException {
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch sueltaElPermiso = new CountDownLatch(1);

        // Un lector que se queda bloqueado mientras el test comprueba qué pasa con el
        // siguiente análisis. Así el permiso está ocupado de verdad, sin depender del reloj.
        LectorImagen lectorLento = new LectorImagen() {
            @Override
            public int[] muestrear(byte[] datos) {
                dentro.countDown();
                try {
                    sueltaElPermiso.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.muestrear(datos);
            }
        };
        ServicioPaletas servicio = new ServicioPaletas(lectorLento, new ExtractorPaleta(), 1);

        Thread ocupante = new Thread(() -> servicio.analizar(foto, 6, Modo.PRESENTES));
        ocupante.start();
        assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> servicio.analizar(foto, 6, Modo.PRESENTES))
                    .isInstanceOf(DemasiadosAnalisisException.class)
                    .hasMessageContaining("unos segundos");
        } finally {
            sueltaElPermiso.countDown();
            ocupante.join(5_000);
        }
    }

    @Test
    @DisplayName("el permiso se devuelve aunque el análisis falle")
    void devuelveElPermisoSiElAnalisisRevienta() {
        ServicioPaletas servicio = new ServicioPaletas(new LectorImagen(), new ExtractorPaleta(), 1);

        assertThatThrownBy(() -> servicio.analizar(FotosDePrueba.textoDisfrazadoDeFoto(), 6, Modo.VIVOS))
                .isInstanceOf(ImagenIlegibleException.class);

        // Si el semáforo se hubiese quedado pillado, esto daría 503 en vez de una paleta.
        assertThat(servicio.analizar(foto, 6, Modo.VIVOS).colores()).isNotEmpty();
    }
}
