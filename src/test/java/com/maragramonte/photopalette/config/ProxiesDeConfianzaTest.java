package com.maragramonte.photopalette.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Vigila la lista de proxies de confianza (P02).
 *
 * <p>Esto no comprueba que el código funcione: comprueba que el límite por IP siga
 * existiendo en producción. Tomcat solo hace caso a {@code X-Forwarded-For} cuando la
 * conexión llega desde una dirección que reconoce como proxy propio, y la lista que trae de
 * serie es toda IPv4 más el loopback. Le falta la IPv6 privada, {@code fc00::/7}, que es
 * desde donde conecta el proxy de casi cualquier host gestionado.
 *
 * <p>Si alguien recorta esa expresión, Tomcat dejará de fiarse de la cabecera, todas las
 * visitas pasarán a verse como una sola dirección y compartirán un único cupo de diez
 * peticiones por minuto. Lo peligroso es que no se rompe nada: el servicio responde igual y
 * el resto de los tests sigue en verde, porque llaman desde {@code 127.0.0.1}, que sí está
 * en la lista de serie. Por eso hace falta un test que mire justo a esto.
 *
 * <p>La lista no depende del host. Vale para Caddy en el VPS, para el proxy de Render y para
 * el {@code fdaa::/16} de Fly, porque lo que dice es «fíate de las redes privadas, de las dos
 * familias, y de nadie más».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProxiesDeConfianzaTest {

    private static final String PROPIEDAD = "server.tomcat.remoteip.internal-proxies";

    @Autowired
    private Environment entorno;

    private Pattern proxiesDeConfianza() {
        String expresion = entorno.getProperty(PROPIEDAD);
        assertThat(expresion).as(PROPIEDAD).isNotBlank();
        return Pattern.compile(expresion);
    }

    @ParameterizedTest
    @DisplayName("se fía de la IPv6 privada, que es por donde entra el proxy del host")
    @ValueSource(strings = {"fd00:1234::5", "fdaa:0:1::3", "fdaa:2:4c0b:a7b:1c:0:a:2", "fc01:2::3"})
    void confiaEnLaIpv6Privada(String direccion) {
        assertThat(proxiesDeConfianza().matcher(direccion).matches())
                .as("el proxy conecta desde %s; si no es de confianza, el cupo por IP se vuelve único", direccion)
                .isTrue();
    }

    @ParameterizedTest
    @DisplayName("sigue fiándose de las redes privadas de siempre, para Caddy en el VPS")
    @ValueSource(strings = {"172.18.0.1", "10.0.0.7", "192.168.1.9", "127.0.0.1", "::1"})
    void confiaEnLasRedesPrivadasDeSiempre(String direccion) {
        assertThat(proxiesDeConfianza().matcher(direccion).matches()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("no se fía de nadie de internet: una cabecera falsa no cuela")
    @ValueSource(strings = {"203.0.113.9", "8.8.8.8", "2001:db8::1", "172.32.0.1", "11.0.0.1", "face:b00c::1"})
    void noConfiaEnDireccionesPublicas(String direccion) {
        assertThat(proxiesDeConfianza().matcher(direccion).matches())
                .as("%s no es un proxy nuestro y no debería poder decidir su propia IP", direccion)
                .isFalse();
    }
}
