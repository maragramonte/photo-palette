package com.maragramonte.photopalette.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato de {@code POST /api/paletas} visto desde fuera.
 *
 * <p>El límite por IP se sube muy alto a propósito: aquí se comprueba el contrato, y en
 * {@link LimitesHttpTest} se comprueban los límites.
 */
@SpringBootTest(properties = "photopalette.peticiones-por-minuto=1000")
@AutoConfigureMockMvc
class ControladorPaletasTest {

    @Autowired
    private MockMvc mockMvc;

    private MockMultipartFile foto() {
        return new MockMultipartFile(
                "imagen", "atardecer.png", "image/png", FotosDePrueba.png(FotosDePrueba.atardecer()));
    }

    @Test
    @DisplayName("200: la respuesta tiene la forma del contrato")
    void devuelveLaPaletaConLaFormaDelContrato() throws Exception {
        mockMvc.perform(multipart("/api/paletas").file(foto()).param("n", "6").param("modo", "vivos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.modo").value("vivos"))
                .andExpect(jsonPath("$.colores", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.colores", hasSize(lessThanOrEqualTo(6))))
                .andExpect(jsonPath("$.colores[*].hex", everyItem(matchesPattern("#[0-9a-f]{6}"))))
                .andExpect(jsonPath("$.colores[0].rgb", hasSize(3)))
                .andExpect(jsonPath("$.colores[0].porcentaje").isNumber())
                .andExpect(jsonPath("$.colores[0].variable").value("--color-1"))
                .andExpect(jsonPath("$.css", startsWith(":root {")));
    }

    @Test
    @DisplayName("sin modo, la respuesta dice presentes")
    void elModoPorDefectoViajaEnLaRespuesta() throws Exception {
        mockMvc.perform(multipart("/api/paletas").file(foto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("presentes"))
                .andExpect(jsonPath("$.colores", hasSize(greaterThan(0))));
    }

    @Test
    void elNumeroDeColoresPorDefectoEsSeis() throws Exception {
        // Una foto con muchos colores distintos: el atardecer de prueba solo tiene cinco,
        // y en presentes salen cinco, no seis. Eso se comprueba en ExtractorPaletaTest.
        MockMultipartFile degradado = new MockMultipartFile(
                "imagen", "degradado.png", "image/png", FotosDePrueba.png(FotosDePrueba.deTamano(200, 200)));

        mockMvc.perform(multipart("/api/paletas").file(degradado).param("modo", "presentes"))
                .andExpect(jsonPath("$.colores", hasSize(6)))
                .andExpect(jsonPath("$.colores[5].variable").value("--color-6"));
    }

    @Test
    @DisplayName("400: falta la imagen")
    void rechazaUnaPeticionSinImagen() throws Exception {
        mockMvc.perform(multipart("/api/paletas").param("n", "6"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Falta la imagen"))
                .andExpect(jsonPath("$.detail", containsString("imagen")))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400: la imagen llega vacía")
    void rechazaUnaImagenVacia() throws Exception {
        MockMultipartFile vacia = new MockMultipartFile("imagen", "vacia.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/paletas").file(vacia))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Falta la imagen"));
    }

    @Test
    @DisplayName("400: n fuera de 2-12")
    void rechazaUnNumeroDeColoresFueraDeRango() throws Exception {
        mockMvc.perform(multipart("/api/paletas").file(foto()).param("n", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Número de colores inválido"))
                .andExpect(jsonPath("$.detail", containsString("12")));
    }

    @Test
    @DisplayName("400: n no es un número")
    void rechazaUnNQueNoEsNumero() throws Exception {
        mockMvc.perform(multipart("/api/paletas").file(foto()).param("n", "seis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    @DisplayName("400: modo que no existe (B05)")
    void rechazaUnModoQueNoExiste() throws Exception {
        mockMvc.perform(multipart("/api/paletas").file(foto()).param("modo", "apagados"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Modo inválido"))
                .andExpect(jsonPath("$.detail", containsString("vivos")));
    }

    @Test
    @DisplayName("422: un .txt renombrado a .jpg")
    void rechazaUnArchivoQueNoEsImagen() throws Exception {
        MockMultipartFile falsa = new MockMultipartFile(
                "imagen", "foto.jpg", "image/jpeg", FotosDePrueba.textoDisfrazadoDeFoto());

        mockMvc.perform(multipart("/api/paletas").file(falsa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("No se puede leer la imagen"))
                .andExpect(jsonPath("$.type", containsString("imagen-ilegible")));
    }

    @Test
    @DisplayName("422: más de 40 megapíxeles")
    void rechazaUnaFotoDeDemasiadosMegapixeles() throws Exception {
        MockMultipartFile enorme = new MockMultipartFile(
                "imagen", "enorme.png", "image/png", FotosDePrueba.pngSoloCabecera(8000, 6000));

        mockMvc.perform(multipart("/api/paletas").file(enorme))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("40")));
    }

    @Test
    @DisplayName("un PNG transparente no rompe nada (I01)")
    void analizaUnPngTransparente() throws Exception {
        MockMultipartFile transparente = new MockMultipartFile(
                "imagen",
                "logo.png",
                "image/png",
                FotosDePrueba.png(FotosDePrueba.mitadTransparente(0x2b7a4b)));

        mockMvc.perform(multipart("/api/paletas").file(transparente).param("modo", "vivos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colores[0].hex").value("#2b7a4b"));
    }

    @Test
    @DisplayName("una foto de un solo color devuelve un solo color (I01)")
    void analizaUnaFotoDeUnSoloColor() throws Exception {
        MockMultipartFile plana = new MockMultipartFile(
                "imagen", "plana.png", "image/png", FotosDePrueba.png(FotosDePrueba.unColor(0x7c5cff)));

        mockMvc.perform(multipart("/api/paletas").file(plana).param("n", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colores", hasSize(1)))
                .andExpect(jsonPath("$.colores[0].hex").value("#7c5cff"))
                .andExpect(jsonPath("$.colores[0].porcentaje").value(100.0));
    }
}
