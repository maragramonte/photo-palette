# Compilación: una imagen con Maven que no llega al VPS.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /obra

# Las dependencias en su propia capa: si solo cambia el código, Docker no vuelve a
# descargar medio Maven Central.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Ejecución: solo el JRE y el jar.
FROM eclipse-temurin:21-jre AS ejecucion

# curl es para el HEALTHCHECK de aquí abajo, nada más.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin paleta
WORKDIR /app
COPY --from=construccion /obra/target/photo-palette-*.jar app.jar
USER paleta

EXPOSE 8080

# El contenedor va limitado a 512 MB: se le dice a la JVM que ese es su mundo, en vez de
# dejar que calcule el heap mirando la RAM del VPS entero. El 65 % son ~333 MB de heap y
# deja ~180 MB para metaspace, code cache, stacks y memoria nativa de ImageIO. headless
# porque se usa java.awt.image sin pantalla.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -Djava.awt.headless=true"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/salud || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
