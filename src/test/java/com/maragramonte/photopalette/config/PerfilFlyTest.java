package com.maragramonte.photopalette.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vigila la lista de proxies de confianza del perfil de Fly.
 *
 * <p>Esto no comprueba que el código funcione, comprueba que el límite por IP siga
 * existiendo en producción. Tomcat solo hace caso a {@code X-Forwarded-For} cuando la
 * conexión viene de una dirección que reconoce como proxy propio, y su lista de serie es
 * toda IPv4. Fly conecta desde su red privada {@code fdaa::/16}: si alguien recorta esta
 * expresión, Tomcat dejará de fiarse de la cabecera, todas las visitas pasarán a verse como
 * una sola dirección y compartirán un único cupo de peticiones por minuto.
 *
 * <p>Nada se rompería: el servicio seguiría respondiendo y el resto de tests seguiría en
 * verde. Por eso hace falta un test que mire justo a esto.
 *
 * <p>La lista solo se toca en el perfil {@code fly}. En el VPS, detrás de Caddy en Docker,
 * manda la de serie de Tomcat, que ya es la correcta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("fly")
class PerfilFlyTest {

    private static final String PROPIEDAD = "server.tomcat.remoteip.internal-proxies";

    @Autowired
    private Environment entorno;

    private Pattern proxiesDeConfianza() {
        String expresion = entorno.getProperty(PROPIEDAD);
        assertThat(expresion).as(PROPIEDAD).isNotBlank();
        return Pattern.compile(expresion);
    }

    @ParameterizedTest
    @DisplayName("se fía del proxy de Fly, que es IPv6 y empieza por fdaa:")
    @ValueSource(strings = {"fdaa:0:1::3", "fdaa:2:4c0b:a7b:1c:0:a:2", "fdaa::1"})
    void confiaEnLaRedPrivadaDeFly(String direccion) {
        assertThat(proxiesDeConfianza().matcher(direccion).matches())
                .as("Fly conecta desde %s; si no es de confianza, el cupo por IP se vuelve único", direccion)
                .isTrue();
    }

    @ParameterizedTest
    @DisplayName("sigue fiándose de las redes privadas de Docker, para el despliegue en el VPS")
    @ValueSource(strings = {"172.18.0.1", "10.0.0.7", "192.168.1.9", "127.0.0.1", "::1"})
    void confiaEnLasRedesPrivadasDeSiempre(String direccion) {
        assertThat(proxiesDeConfianza().matcher(direccion).matches()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("no se fía de nadie de internet: una cabecera falsa no cuela")
    @ValueSource(strings = {"203.0.113.9", "8.8.8.8", "2001:db8::1", "172.32.0.1", "11.0.0.1"})
    void noConfiaEnDireccionesPublicas(String direccion) {
        assertThat(proxiesDeConfianza().matcher(direccion).matches())
                .as("%s no es un proxy nuestro y no debería poder decidir su propia IP", direccion)
                .isFalse();
    }
}
