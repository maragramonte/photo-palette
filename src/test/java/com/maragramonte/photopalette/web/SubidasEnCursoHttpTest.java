package com.maragramonte.photopalette.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maragramonte.photopalette.FotosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El tope de subidas de FiltroSubidasEnCurso, visto desde HTTP.
 *
 * <p>Interesan dos cosas: que el rechazo sea el mismo 503 que da el servicio, para que el
 * frontend reintente igual, y que el hueco se devuelva al terminar. Un release olvidado no
 * rompería nada en el primer análisis; dejaría la subida apagada para siempre a partir del
 * cuarto.
 */
class SubidasEnCursoHttpTest {

    private static MockMultipartFile foto() {
        return new MockMultipartFile(
                "imagen", "atardecer.png", "image/png", FotosDePrueba.png(FotosDePrueba.atardecer()));
    }

    @Nested
    @SpringBootTest(properties = "photopalette.subidas-simultaneas=0")
    @AutoConfigureMockMvc
    class SinHueco {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("503 con el mismo ProblemDetail que el servicio, antes de leer la foto")
        void respondeQueEstaOcupado() throws Exception {
            mockMvc.perform(multipart("/api/paletas").file(foto()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.title").value("El servidor está ocupado"))
                    .andExpect(jsonPath("$.detail", containsString("unos segundos")))
                    .andExpect(jsonPath("$.type", containsString("servidor-ocupado")))
                    .andExpect(jsonPath("$.instance").value("/api/paletas"));
        }
    }

    @Nested
    @SpringBootTest(properties = "photopalette.subidas-simultaneas=1")
    @AutoConfigureMockMvc
    class ConUnHueco {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("el hueco se devuelve al terminar: varias subidas seguidas pasan todas")
        void devuelveElHueco() throws Exception {
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(multipart("/api/paletas").file(foto()))
                        .andExpect(status().isOk());
            }
        }
    }
}
