# Desarrollo

Cómo se trabaja en este repositorio. Qué es el proyecto y por qué está en el
[README](../README.md); el contrato, el sketch y el backlog están en
[`CONCEPTO.md`](CONCEPTO.md),
que es la fuente de la verdad.

## Cómo se mueve esto

```bash
mvn test                                   # los tests del backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # la API en localhost:8080
mvn compile exec:java@paletas              # regenera web/paletas.json desde web/fotos
cd web && python -m http.server 8000       # la web en localhost:8000
```

El perfil `dev` es el único que permite llamar a la API desde `localhost`. Publicado, solo
acepta `https://maragramonte.github.io`.

## Reglas del proyecto

- **Ningún color de la web se escribe a mano.** El feed lee `web/paletas.json`, que genera
  `GeneradorPaletasJson` con el mismo código que la API. Si hace falta un color nuevo en el
  feed, se cambia la foto, no el CSS.
- **Los valores de diseño viven en `web/css/tokens.css`.** Nada de colores, radios ni
  espaciados sueltos en el resto del CSS.
- **El código y los comentarios están en español**, nombres de clase incluidos. Es
  deliberado: este repo también es portfolio.
- **La foto nunca se guarda.** Se analiza en memoria y se descarta.
- **Solo JPG y PNG.** El `ImageIO` del JDK no lee WebP y no se añade la dependencia.
