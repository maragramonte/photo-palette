package com.maragramonte.photopalette.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * La API solo acepta peticiones del navegador desde los origenes configurados.
 *
 * <p>Va como filtro, y el primero de todos, en lugar de con {@code addCorsMappings}: así las
 * cabeceras CORS tambien salen en las respuestas que nunca llegan a un controlador, como el
 * 429 del límite por IP. Sin eso, el navegador vería un error de CORS en vez del mensaje de
 * la API, y la web no podría explicarle nada al usuario.
 *
 * <p>Ojo con lo que CORS no hace: no protege contra abuso, porque un script sin navegador lo
 * ignora. De eso se encargan los límites de tamaño, de megapíxeles, de tasa por IP y de
 * análisis simultáneos.
 */
@Configuration
public class ConfiguracionCors {

    private final List<String> origenes;

    public ConfiguracionCors(
            @Value("${photopalette.origenes-permitidos:https://maragramonte.github.io}")
                    List<String> origenes) {
        this.origenes = origenes;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> filtroCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenes);
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/api/**", cors);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(origen));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
