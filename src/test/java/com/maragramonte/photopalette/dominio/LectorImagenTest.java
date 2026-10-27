package com.maragramonte.photopalette.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maragramonte.photopalette.FotosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LectorImagenTest {

    private final LectorImagen lector = new LectorImagen();

    @Test
    void leeUnJpg() {
        int[] pixeles = lector.muestrear(FotosDePrueba.jpg(FotosDePrueba.atardecer()));

        assertThat(pixeles).isNotEmpty();
    }

    @Test
    void leeUnPng() {
        int[] pixeles = lector.muestrear(FotosDePrueba.png(FotosDePrueba.atardecer()));

        assertThat(pixeles).hasSize(200 * 200);
    }

    @Test
    @DisplayName("rechaza más de 40 MP leyendo solo la cabecera (B08)")
    void rechazaUnaFotoDeDemasiadosMegapixeles() {
        // 8000x6000 son 48 MP. El archivo no tiene datos de imagen a propósito: si el
        // lector intentase decodificarlo, fallaría de otra forma.
        byte[] enorme = FotosDePrueba.pngSoloCabecera(8000, 6000);

        assertThatThrownBy(() -> lector.muestrear(enorme))
                .isInstanceOf(ImagenDemasiadoGrandeException.class)
                .hasMessageContaining("48")
                .hasMessageContaining("40");
    }

    @Test
    void aceptaUnaFotoJustoPorDebajoDelLimite() {
        byte[] grande = FotosDePrueba.pngSoloCabecera(6000, 6000); // 36 MP

        // Pasa el control de dimensiones y falla al decodificar, que es otro error.
        assertThatThrownBy(() -> lector.muestrear(grande))
                .isInstanceOf(ImagenIlegibleException.class);
    }

    @Test
    @DisplayName("un .txt renombrado a .jpg no cuela")
    void rechazaUnArchivoQueNoEsImagen() {
        assertThatThrownBy(() -> lector.muestrear(FotosDePrueba.textoDisfrazadoDeFoto()))
                .isInstanceOf(ImagenIlegibleException.class);
    }

    @Test
    void rechazaUnArchivoVacio() {
        assertThatThrownBy(() -> lector.muestrear(new byte[0]))
                .isInstanceOf(ImagenIlegibleException.class);
    }

    @Test
    @DisplayName("un PNG transparente aporta solo sus píxeles opacos")
    void ignoraLosPixelesTransparentes() {
        int[] pixeles = lector.muestrear(FotosDePrueba.png(FotosDePrueba.mitadTransparente(0x2b7a4b)));

        assertThat(pixeles).hasSize(50 * 100);
        assertThat(pixeles).containsOnly(0x2b7a4b);
    }

    @Test
    void rechazaUnPngEnteroTransparente() {
        assertThatThrownBy(() -> lector.muestrear(FotosDePrueba.png(FotosDePrueba.todoTransparente())))
                .isInstanceOf(ImagenIlegibleException.class)
                .hasMessageContaining("transparente");
    }

    @Test
    @DisplayName("una foto de 12 MP se submuestrea a unos 40.000 píxeles (B08)")
    void submuestreaLasFotosGrandes() {
        byte[] doceMegapixeles = FotosDePrueba.jpg(FotosDePrueba.deTamano(4000, 3000));

        int[] pixeles = lector.muestrear(doceMegapixeles);

        // 12 MP / 40.000 da un paso de 18, o sea 223x167 = unos 37.000 píxeles.
        // Lo que importa es que no se acerque ni de lejos a los 12 millones.
        assertThat(pixeles.length).isLessThan(60_000);
        assertThat(pixeles.length).isGreaterThan(20_000);
    }

    @Test
    void noSubmuestreaLoQueYaEsPequeno() {
        assertThat(LectorImagen.calcularPaso(100, 100)).isEqualTo(1);
        assertThat(LectorImagen.calcularPaso(200, 200)).isEqualTo(1);
    }

    @Test
    void elPasoCreceConElTamano() {
        assertThat(LectorImagen.calcularPaso(400, 400)).isEqualTo(2);
        assertThat(LectorImagen.calcularPaso(4000, 3000)).isEqualTo(18);
    }
}
