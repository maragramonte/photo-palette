package com.maragramonte.photopalette.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Modo y bloque CSS")
class ContratoDeSalidaTest {

    @ParameterizedTest
    @CsvSource({"presentes,PRESENTES", "vivos,VIVOS", "VIVOS,VIVOS", " Presentes ,PRESENTES"})
    void traduceElParametroModo(String entrada, Modo esperado) {
        assertThat(Modo.desdeParametro(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("sin modo, el valor por defecto es presentes")
    void elModoPorDefectoEsPresentes(String entrada) {
        assertThat(Modo.desdeParametro(entrada)).isEqualTo(Modo.PRESENTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"apagados", "vivo", "vívos", "1"})
    void rechazaUnModoQueNoExiste(String entrada) {
        assertThatThrownBy(() -> Modo.desdeParametro(entrada))
                .isInstanceOf(ModoInvalidoException.class)
                .hasMessageContaining("presentes")
                .hasMessageContaining("vivos");
    }

    @Test
    void elBloqueCssTieneUnaLineaPorColor() {
        List<ColorPaleta> colores = List.of(
                ColorPaleta.de(209, 141, 103, 5.7, 1), ColorPaleta.de(11, 13, 26, 64.0, 2));

        RespuestaPaleta respuesta = RespuestaPaleta.de(Modo.VIVOS, colores);

        assertThat(respuesta.modo()).isEqualTo("vivos");
        assertThat(respuesta.css())
                .isEqualTo(":root {\n  --color-1: #d18d67;\n  --color-2: #0b0d1a;\n}");
    }

    @Test
    void elPorcentajeSeRedondeaAUnDecimal() {
        assertThat(ColorPaleta.de(1, 2, 3, 5.6789, 1).porcentaje()).isEqualTo(5.7);
    }
}
