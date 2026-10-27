package com.maragramonte.photopalette.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Deja pasar un número limitado de análisis por minuto y por IP; el resto recibe un 429.
 *
 * <p>La IP sale de {@code request.getRemoteAddr()}. Detras de Caddy eso sería siempre la IP
 * del contenedor del proxy, así que la aplicación arranca con
 * {@code server.forward-headers-strategy=native}: Tomcat lee entonces
 * {@code X-Forwarded-For}, pero solo cuando la conexion viene de un proxy de la red interna
 * de Docker. Una cabecera falsa enviada desde internet no cuela, porque el puerto 8080 no
 * se publica fuera del VPS.
 *
 * <p>{@code GET /api/salud} queda fuera: la web lo usa para decidir si la subida está
 * disponible y un 429 ahi apagaría la subida sin motivo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroLimitePorIp extends OncePerRequestFilter {

    private static final String RUTA_LIMITADA = "/api/paletas";
    private static final Duration VENTANA = Duration.ofMinutes(1);
    private static final Duration CADUCIDAD = Duration.ofMinutes(10);
    private static final int MAX_IPS = 10_000;

    private final Map<String, Visitante> visitantes = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final int peticionesPorMinuto;

    public FiltroLimitePorIp(
            ObjectMapper json, @Value("${photopalette.peticiones-por-minuto:10}") int peticionesPorMinuto) {
        this.json = json;
        this.peticionesPorMinuto = peticionesPorMinuto;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        // Solo el análisis gasta credito. El preflight OPTIONS y la salud, no.
        return !("POST".equals(peticion.getMethod()) && RUTA_LIMITADA.equals(peticion.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {

        Bucket cubo = cuboDe(peticion.getRemoteAddr());
        if (cubo.tryConsume(1)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }
        responderDemasiadasPeticiones(peticion, respuesta);
    }

    private Bucket cuboDe(String ip) {
        if (visitantes.size() > MAX_IPS) {
            purgar();
        }
        Visitante visitante = visitantes.compute(
                ip == null ? "desconocida" : ip,
                (clave, actual) -> actual == null ? nuevoVisitante() : actual.visto());
        return visitante.cubo();
    }

    private Visitante nuevoVisitante() {
        Bucket cubo = Bucket.builder()
                .addLimit(limite -> limite.capacity(peticionesPorMinuto)
                        .refillGreedy(peticionesPorMinuto, VENTANA))
                .build();
        return new Visitante(cubo, Instant.now());
    }

    /** Las IP que no vuelven se olvidan: el mapa no puede crecer sin tope. */
    private void purgar() {
        Instant limite = Instant.now().minus(CADUCIDAD);
        visitantes.values().removeIf(visitante -> visitante.ultimaVez().isBefore(limite));
    }

    private void responderDemasiadasPeticiones(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws IOException {
        ProblemDetail problema = ManejadorErrores.problema(
                HttpStatus.TOO_MANY_REQUESTS,
                "demasiadas-peticiones",
                "Demasiadas peticiones",
                "Has analizado " + peticionesPorMinuto
                        + " fotos en un minuto. Espera un momento y vuelve a intentarlo.",
                peticion);
        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.setHeader("Retry-After", String.valueOf(VENTANA.toSeconds()));
        json.writeValue(respuesta.getOutputStream(), problema);
    }

    private record Visitante(Bucket cubo, Instant ultimaVez) {

        Visitante visto() {
            return new Visitante(cubo, Instant.now());
        }
    }
}
