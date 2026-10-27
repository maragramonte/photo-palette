package com.maragramonte.photopalette.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.maragramonte.photopalette.FotosDePrueba;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Los límites de uso y el CORS, contra un Tomcat de verdad.
 *
 * <p>Hace falta servidor real, no MockMvc: el 413 lo decide Tomcat al trocear el multipart,
 * y la IP del cliente solo sale de {@code X-Forwarded-For} si pasa por el valve de Tomcat
 * que activa {@code server.forward-headers-strategy=native}. Como el test llama desde
 * 127.0.0.1, que para Tomcat es un proxy de confianza, la cabecera se respeta igual que
 * detrás de Caddy en el VPS.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "photopalette.peticiones-por-minuto=3",
            "photopalette.origenes-permitidos=https://maragramonte.github.io"
        })
class LimitesHttpTest {

    private static final int LIMITE = 3;

    @Autowired
    private TestRestTemplate cliente;

    private ResponseEntity<String> analizar(String ipDelCliente) {
        return analizar(ipDelCliente, FotosDePrueba.png(FotosDePrueba.atardecer()), null);
    }

    private ResponseEntity<String> analizar(String ipDelCliente, byte[] foto, String origen) {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("imagen", new ByteArrayResource(foto) {
            @Override
            public String getFilename() {
                return "atardecer.png";
            }
        });

        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (ipDelCliente != null) {
            cabeceras.set("X-Forwarded-For", ipDelCliente);
        }
        if (origen != null) {
            cabeceras.setOrigin(origen);
        }
        return cliente.postForEntity("/api/paletas", new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    @Test
    @DisplayName("429 al pasarse del cupo de análisis por minuto (B09)")
    void cortaALaPeticionQueSePasaDelLimite() {
        String ip = "203.0.113.10";

        for (int i = 1; i <= LIMITE; i++) {
            assertThat(analizar(ip).getStatusCode()).as("petición %d", i).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> cortada = analizar(ip);

        assertThat(cortada.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(cortada.getHeaders().getContentType())
                .isNotNull()
                .matches(tipo -> tipo.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(cortada.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(cortada.getBody()).contains("Demasiadas peticiones").contains("\"status\":429");
    }

    @Test
    @DisplayName("dos IP distintas detrás del proxy tienen cupos separados (B09)")
    void cadaIpTieneSuPropioCupo() {
        String unaIp = "203.0.113.20";
        String otraIp = "203.0.113.21";

        for (int i = 1; i <= LIMITE; i++) {
            assertThat(analizar(unaIp).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(analizar(unaIp).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // La otra IP no ha gastado nada: si los cupos se mezclasen, esto sería un 429.
        assertThat(analizar(otraIp).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /api/salud responde 200 y no gasta cupo (B10)")
    void laSaludQuedaFueraDelLimite() {
        String ip = "203.0.113.30";

        for (int i = 0; i < LIMITE * 3; i++) {
            ResponseEntity<Void> salud = cliente.exchange(
                    "/api/salud",
                    HttpMethod.GET,
                    new HttpEntity<>(cabecerasCon("X-Forwarded-For", ip)),
                    Void.class);
            assertThat(salud.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        // Y el cupo de análisis sigue intacto.
        assertThat(analizar(ip).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("413 si la imagen pasa de 10 MB")
    void rechazaUnaImagenDemasiadoPesada() {
        byte[] onceMegas = new byte[11 * 1024 * 1024];
        // Cabecera de PNG para que no sea el formato lo que falla, sino el peso.
        System.arraycopy(FotosDePrueba.pngSoloCabecera(100, 100), 0, onceMegas, 0, 40);

        ResponseEntity<String> respuesta = analizar("203.0.113.40", onceMegas, null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(respuesta.getBody()).contains("10 MB");
    }

    @Test
    @DisplayName("CORS: la web publicada sí puede llamar a la API (B07)")
    void aceptaElOrigenDeLaWebPublicada() {
        ResponseEntity<String> respuesta = analizar(
                "203.0.113.50",
                FotosDePrueba.png(FotosDePrueba.atardecer()),
                "https://maragramonte.github.io");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("https://maragramonte.github.io");
    }

    @Test
    @DisplayName("CORS: cualquier otro origen se rechaza (B07)")
    void rechazaCualquierOtroOrigen() {
        ResponseEntity<String> respuesta = analizar(
                "203.0.113.51", FotosDePrueba.png(FotosDePrueba.atardecer()), "https://copion.example");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    @DisplayName("CORS: el preflight del origen permitido pasa y no gasta cupo")
    void respondeAlPreflight() {
        HttpHeaders cabeceras = cabecerasCon("Origin", "https://maragramonte.github.io");
        cabeceras.set("Access-Control-Request-Method", "POST");
        cabeceras.set("Access-Control-Request-Headers", "content-type");
        cabeceras.set("X-Forwarded-For", "203.0.113.60");

        ResponseEntity<Void> preflight = cliente.exchange(
                "/api/paletas", HttpMethod.OPTIONS, new HttpEntity<>(cabeceras), Void.class);

        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preflight.getHeaders().getAccessControlAllowMethods())
                .contains(HttpMethod.POST, HttpMethod.GET);
    }

    @Test
    @DisplayName("sin cabecera Origin la API responde igual: curl y el VPS no hacen CORS")
    void sinOrigenNoHayCors() {
        ResponseEntity<String> respuesta = analizar("203.0.113.70");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    @DisplayName("el error del 429 llega con cabecera CORS, para que la web pueda contarlo")
    void el429TambienLlevaCabecerasCors() {
        String ip = "203.0.113.80";
        String origen = "https://maragramonte.github.io";
        byte[] foto = FotosDePrueba.png(FotosDePrueba.atardecer());

        List<HttpStatus> estados = new java.util.ArrayList<>();
        for (int i = 0; i <= LIMITE; i++) {
            estados.add(HttpStatus.valueOf(analizar(ip, foto, origen).getStatusCode().value()));
        }

        ResponseEntity<String> cortada = analizar(ip, foto, origen);
        assertThat(estados).endsWith(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(cortada.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origen);
    }

    private static HttpHeaders cabecerasCon(String nombre, String valor) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(nombre, valor);
        return cabeceras;
    }
}
