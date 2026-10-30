package com.maragramonte.photopalette.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maragramonte.photopalette.dominio.DemasiadosAnalisisException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pone tope a las subidas que se leen a la vez, antes de que Spring lea el multipart.
 *
 * <p>El semáforo de {@code ServicioPaletas} llega tarde para la memoria: cuando lo consulta,
 * el controlador ya ha copiado la foto entera al heap con {@code getBytes()}. En la prueba de
 * carga I03, veinte subidas de 10 MB a la vez dejaron el contenedor a 21 MB de su límite, y
 * casi todas eran peticiones que iban a acabar en 503 de todos modos. Este filtro corre antes
 * del {@code DispatcherServlet}, así que lo que sobra se rechaza sin haber leído un byte del
 * cuerpo: Tomcat lo descarta al vuelo con {@code max-swallow-size}, sin guardarlo.
 *
 * <p>Va después del límite por IP: una IP que ya gastó su minuto recibe su 429, no un 503
 * que la invitaría a reintentar. Y deja algo más de hueco que los análisis simultáneos, para
 * que una subida pueda ir llegando mientras otras dos se analizan.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FiltroSubidasEnCurso extends OncePerRequestFilter {

    private static final String RUTA_LIMITADA = "/api/paletas";

    private final Semaphore enCurso;
    private final ObjectMapper json;

    public FiltroSubidasEnCurso(
            ObjectMapper json, @Value("${photopalette.subidas-simultaneas:4}") int subidasSimultaneas) {
        this.json = json;
        this.enCurso = new Semaphore(subidasSimultaneas);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !("POST".equals(peticion.getMethod()) && RUTA_LIMITADA.equals(peticion.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {

        if (!enCurso.tryAcquire()) {
            responderOcupado(peticion, respuesta);
            return;
        }
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            enCurso.release();
        }
    }

    /** El mismo 503 que da el servicio: el frontend no tiene por qué saber quién lo dijo. */
    private void responderOcupado(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws IOException {
        ProblemDetail problema = ManejadorErrores.problema(
                HttpStatus.SERVICE_UNAVAILABLE,
                "servidor-ocupado",
                "El servidor está ocupado",
                new DemasiadosAnalisisException().getMessage(),
                peticion);
        respuesta.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}
