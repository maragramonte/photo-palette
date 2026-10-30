package com.maragramonte.photopalette.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * La purga de FiltroLimitePorIp: lo que impide que el mapa de IP crezca sin tope.
 *
 * <p>En producción salta al pasar de 10.000 IP y olvida las que llevan diez minutos sin
 * volver. Aquí el tope es 3 y el reloj se mueve a mano. Lo que importa es que olvide a las que
 * se fueron y no a las que siguen: si una IP que se ha pasado del límite saliera del mapa con
 * la purga, bastaría con mandar peticiones desde muchas IP para lavar el 429 de otra.
 */
class PurgaDeIpsTest {

    private static final int MAX_IPS = 3;

    private final RelojManual reloj = new RelojManual();

    private FiltroLimitePorIp filtro(int peticionesPorMinuto) {
        return new FiltroLimitePorIp(new ObjectMapper(), peticionesPorMinuto, reloj, MAX_IPS);
    }

    private static int analizar(FiltroLimitePorIp filtro, String ip) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/paletas");
        peticion.setRemoteAddr(ip);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta.getStatus();
    }

    @Test
    @DisplayName("por debajo del tope no olvida a nadie, aunque hayan pasado diez minutos")
    void sinPasarDelTopeNoPurga() throws Exception {
        FiltroLimitePorIp filtro = filtro(10);
        for (String ip : new String[] {"10.0.0.1", "10.0.0.2", "10.0.0.3"}) {
            analizar(filtro, ip);
        }
        reloj.avanzar(Duration.ofMinutes(11));
        analizar(filtro, "10.0.0.4");

        assertThat(filtro.ipsRecordadas()).isEqualTo(4);
    }

    @Test
    @DisplayName("al pasar del tope olvida las IP que llevan diez minutos sin volver")
    void olvidaLasQueSeFueron() throws Exception {
        FiltroLimitePorIp filtro = filtro(10);
        for (String ip : new String[] {"10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4"}) {
            analizar(filtro, ip);
        }
        reloj.avanzar(Duration.ofMinutes(11));
        analizar(filtro, "10.0.0.5");

        assertThat(filtro.ipsRecordadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("no olvida las IP que han vuelto hace menos de diez minutos")
    void recuerdaLasQueSiguen() throws Exception {
        FiltroLimitePorIp filtro = filtro(10);
        analizar(filtro, "10.0.0.1");
        analizar(filtro, "10.0.0.2");
        reloj.avanzar(Duration.ofMinutes(11));
        analizar(filtro, "10.0.0.3");
        analizar(filtro, "10.0.0.4");
        analizar(filtro, "10.0.0.5");

        assertThat(filtro.ipsRecordadas()).isEqualTo(3);
    }

    @Test
    @DisplayName("una IP que ya gastó su minuto sigue con su 429 después de una purga")
    void laPurgaNoLavaUn429() throws Exception {
        FiltroLimitePorIp filtro = filtro(2);
        analizar(filtro, "10.0.0.1");
        analizar(filtro, "10.0.0.2");
        reloj.avanzar(Duration.ofMinutes(11));

        assertThat(analizar(filtro, "10.9.9.9")).isEqualTo(200);
        assertThat(analizar(filtro, "10.9.9.9")).isEqualTo(200);
        assertThat(analizar(filtro, "10.9.9.9")).isEqualTo(429);

        // Con la cuarta IP el mapa pasa del tope; la quinta dispara la purga.
        analizar(filtro, "10.0.0.3");
        analizar(filtro, "10.0.0.4");
        assertThat(filtro.ipsRecordadas()).as("la purga ha olvidado las dos primeras").isEqualTo(3);
        assertThat(analizar(filtro, "10.9.9.9")).isEqualTo(429);
    }

    /** Un reloj que solo avanza cuando el test lo dice. */
    private static final class RelojManual extends Clock {

        private Instant ahora = Instant.parse("2026-01-01T12:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }
}
