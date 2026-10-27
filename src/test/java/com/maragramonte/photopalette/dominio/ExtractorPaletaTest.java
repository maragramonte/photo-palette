package com.maragramonte.photopalette.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maragramonte.photopalette.FotosDePrueba;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractorPaletaTest {

    private final ExtractorPaleta extractor = new ExtractorPaleta();

    private static int[] pixelesDe(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int[] argb = imagen.getRGB(0, 0, ancho, alto, null, 0, ancho);
        int[] pixeles = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            pixeles[i] = argb[i] & 0xffffff;
        }
        return pixeles;
    }

    private static float tono(ColorPaleta color) {
        return Color.RGBtoHSB(color.rgb()[0], color.rgb()[1], color.rgb()[2], null)[0];
    }

    @Nested
    @DisplayName("Validación de n")
    class ValidacionDeN {

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 1, 13, 100})
        void rechazaUnNumeroDeColoresFueraDeRango(int n) {
            int[] pixeles = pixelesDe(FotosDePrueba.atardecer());
            assertThatThrownBy(() -> extractor.extraer(pixeles, n, Modo.PRESENTES))
                    .isInstanceOf(NumeroColoresInvalidoException.class)
                    .hasMessageContaining("2")
                    .hasMessageContaining("12");
        }

        @ParameterizedTest
        @ValueSource(ints = {2, 6, 12})
        void aceptaTodoElRangoDelContrato(int n) {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.deTamano(120, 120)), n, Modo.PRESENTES);
            assertThat(paleta).hasSize(n);
        }

        @Test
        void rechazaUnaImagenSinPixeles() {
            assertThatThrownBy(() -> extractor.extraer(new int[0], 6, Modo.PRESENTES))
                    .isInstanceOf(ImagenIlegibleException.class);
        }
    }

    @Nested
    @DisplayName("Modo presentes")
    class ModoPresentes {

        @Test
        void ordenaDeMayorAMenorPorcentaje() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 5, Modo.PRESENTES);

            assertThat(paleta).extracting(ColorPaleta::porcentaje).isSortedAccordingTo(
                    (a, b) -> Double.compare(b, a));
        }

        @Test
        void losPorcentajesSonSobreLaFotoEntera() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 5, Modo.PRESENTES);

            double suma = paleta.stream().mapToDouble(ColorPaleta::porcentaje).sum();
            assertThat(suma).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1.0));
        }

        @Test
        @DisplayName("el naranja del sol NO entra: es el problema que resuelve el modo vivos")
        void dejaFueraElColorMasVistosoDelAtardecer() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 6, Modo.PRESENTES);

            assertThat(paleta.subList(0, 3)).extracting(ColorPaleta::hex)
                    .doesNotContain(FotosDePrueba.NARANJA_DEL_SOL);
        }

        @Test
        void unaFotoDeUnSoloColorDevuelveUnSoloColor() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.unColor(0x2b7a4b)), 6, Modo.PRESENTES);

            assertThat(paleta).hasSize(1);
            assertThat(paleta.get(0).hex()).isEqualTo("#2b7a4b");
            assertThat(paleta.get(0).porcentaje()).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("Modo vivos")
    class ModoVivos {

        @Test
        @DisplayName("el naranja del sol sale entre los 3 primeros colores (B03)")
        void saleElColorMasVistosoDelAtardecer() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 6, Modo.VIVOS);

            assertThat(paleta).isNotEmpty();
            assertThat(paleta.subList(0, Math.min(3, paleta.size()))).extracting(ColorPaleta::hex)
                    .contains(FotosDePrueba.NARANJA_DEL_SOL);
        }

        @Test
        void ordenaDeMasAMenosSaturado() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 6, Modo.VIVOS);

            List<Float> saturaciones = paleta.stream()
                    .map(color -> Color.RGBtoHSB(color.rgb()[0], color.rgb()[1], color.rgb()[2], null)[1])
                    .toList();
            assertThat(saturaciones).isSortedAccordingTo((a, b) -> Float.compare(b, a));
        }

        @Test
        @DisplayName("ignora los casi negros: la mancha roja del 3 % manda")
        void ignoraLosPixelesCasiNegros() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.nocheConUnaLuz()), 6, Modo.VIVOS);

            assertThat(paleta.get(0).rgb()[0]).isGreaterThan(180);
            // El porcentaje va sobre los píxeles con luz, no sobre la foto entera.
            assertThat(paleta.get(0).porcentaje()).isGreaterThan(50.0);
        }

        @Test
        @DisplayName("no repite tonos parecidos: el cielo estrellado da muy pocos colores")
        void noRepiteTonosParecidos() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.cieloEstrellado()), 6, Modo.VIVOS);

            assertThat(paleta).hasSizeLessThan(6);
            for (int i = 0; i < paleta.size(); i++) {
                for (int j = i + 1; j < paleta.size(); j++) {
                    assertThat(ExtractorPaleta.mismoColor(hsb(paleta.get(i)), hsb(paleta.get(j))))
                            .as("%s y %s son el mismo color", paleta.get(i).hex(), paleta.get(j).hex())
                            .isFalse();
                }
            }
        }

        private float[] hsb(ColorPaleta color) {
            return Color.RGBtoHSB(color.rgb()[0], color.rgb()[1], color.rgb()[2], null);
        }

        @Test
        @DisplayName("un tono repetido con otro brillo sí cuenta: la sombra y el sol son dos")
        void elMismoTonoConOtroBrilloSonDosColores() {
            BufferedImage calida = FotosDePrueba.bandas(
                    200,
                    200,
                    new FotosDePrueba.Banda(0x6b4a2a, 0.70), // marrón de la sombra
                    new FotosDePrueba.Banda(0xffb060, 0.30)); // naranja claro del sol

            List<ColorPaleta> paleta = extractor.extraer(pixelesDe(calida), 6, Modo.VIVOS);

            assertThat(paleta).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("puede devolver menos de n, y el contrato lo admite")
        void puedeDevolverMenosColoresDeLosPedidos() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.cieloEstrellado()), 12, Modo.VIVOS);

            assertThat(paleta).isNotEmpty().hasSizeLessThan(12);
        }

        @Test
        @DisplayName("una foto entera en sombra devuelve su paleta en vez de quedarse vacía")
        void unaFotoEnteraOscuraNoSeQuedaSinPaleta() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.unColor(0x050507)), 6, Modo.VIVOS);

            assertThat(paleta).isNotEmpty();
        }

        @Test
        @DisplayName("los grises se distinguen por brillo, porque su tono no significa nada")
        void distingueLosGrisesPorBrillo() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.grises()), 6, Modo.VIVOS);

            assertThat(paleta).hasSizeGreaterThan(1);
            assertThat(paleta).allSatisfy(color ->
                    assertThat(color.rgb()[0]).isEqualTo(color.rgb()[1]).isEqualTo(color.rgb()[2]));
        }

        @Test
        void descartaLosGruposPorDebajoDeMedioPorciento() {
            // Una mota de color que ocupa el 0,1 % no es un color de la foto, es ruido.
            BufferedImage conMota = FotosDePrueba.bandas(
                    1000,
                    1000,
                    new FotosDePrueba.Banda(0x3366cc, 0.999),
                    new FotosDePrueba.Banda(0xff0000, 0.001));

            List<ColorPaleta> paleta = extractor.extraer(pixelesDe(conMota), 6, Modo.VIVOS);

            assertThat(paleta).extracting(ColorPaleta::hex).doesNotContain("#ff0000");
        }
    }

    @Nested
    @DisplayName("Forma de la respuesta")
    class FormaDeLaRespuesta {

        @Test
        void numeraLasVariablesCssEnOrden() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 4, Modo.PRESENTES);

            assertThat(paleta).extracting(ColorPaleta::variable)
                    .containsExactly("--color-1", "--color-2", "--color-3", "--color-4");
        }

        @Test
        void elHexYElRgbDicenLoMismo() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.atardecer()), 5, Modo.VIVOS);

            assertThat(paleta).allSatisfy(color -> assertThat(color.hex())
                    .isEqualTo(String.format("#%02x%02x%02x", color.rgb()[0], color.rgb()[1], color.rgb()[2])));
        }

        @Test
        void elPorcentajeTieneUnSoloDecimal() {
            List<ColorPaleta> paleta =
                    extractor.extraer(pixelesDe(FotosDePrueba.deTamano(173, 97)), 6, Modo.PRESENTES);

            assertThat(paleta).allSatisfy(color ->
                    assertThat(color.porcentaje() * 10).isCloseTo(
                            Math.round(color.porcentaje() * 10), org.assertj.core.data.Offset.offset(1e-9)));
        }
    }

    @Test
    @DisplayName("la misma foto da siempre la misma paleta")
    void esDeterminista() {
        int[] pixeles = pixelesDe(FotosDePrueba.deTamano(200, 150));

        List<ColorPaleta> primera = extractor.extraer(pixeles, 6, Modo.VIVOS);
        List<ColorPaleta> segunda = new ExtractorPaleta().extraer(pixeles, 6, Modo.VIVOS);

        assertThat(segunda).extracting(ColorPaleta::hex)
                .isEqualTo(primera.stream().map(ColorPaleta::hex).toList());
    }

    @Nested
    @DisplayName("Distancia entre tonos")
    class DistanciaEntreTonos {

        @Test
        void elTonoEsUnCirculo() {
            assertThat(ExtractorPaleta.distanciaTono(350f / 360f, 10f / 360f)).isCloseTo(
                    20f, org.assertj.core.data.Offset.offset(0.01f));
        }

        @Test
        void unGrisYUnColorNuncaSonElMismo() {
            float[] gris = Color.RGBtoHSB(128, 128, 128, null);
            float[] rojo = Color.RGBtoHSB(220, 30, 30, null);

            assertThat(ExtractorPaleta.mismoColor(gris, rojo)).isFalse();
        }

        @Test
        void dosGrisesDeBrilloParecidoSonElMismo() {
            float[] claro = Color.RGBtoHSB(200, 200, 200, null);
            float[] casiIgual = Color.RGBtoHSB(210, 210, 210, null);

            assertThat(ExtractorPaleta.mismoColor(claro, casiIgual)).isTrue();
        }
    }
}
