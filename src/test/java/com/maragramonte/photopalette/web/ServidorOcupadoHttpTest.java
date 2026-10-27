package com.maragramonte.photopalette.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maragramonte.photopalette.FotosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El 503 visto desde HTTP, con el cupo de analisis puesto a cero.
 *
 * <p>Que el semaforo rechaza cuando no hay hueco se comprueba en ServicioPaletasTest con
 * dos hilos. Aqui solo interesa que ese rechazo salga como 503 y con el ProblemDetail que
 * el frontend necesita para decidir si reintenta.
 */
@SpringBootTest(properties = "photopalette.analisis-simultaneos=0")
@AutoConfigureMockMvc
class ServidorOcupadoHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("503 cuando no queda hueco para otro analisis")
    void respondeQueEstaOcupado() throws Exception {
        MockMultipartFile foto = new MockMultipartFile(
                "imagen", "atardecer.png", "image/png", FotosDePrueba.png(FotosDePrueba.atardecer()));

        mockMvc.perform(multipart("/api/paletas").file(foto))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("El servidor está ocupado"))
                .andExpect(jsonPath("$.detail", containsString("unos segundos")))
                .andExpect(jsonPath("$.type", containsString("servidor-ocupado")));
    }
}
