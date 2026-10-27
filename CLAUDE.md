# Photo Palette

API en Java que convierte una foto en una paleta de color, y una web de una sola página que
la enseña. El contrato, el sketch y el backlog están en
`Photo Palette — concepto, sketch y backlog.md`: ese documento es la fuente de la verdad.

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

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. Route only to skills in the session's available-skills list; answer directly for quick questions or small scoped edits.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
- Author a backlog-ready spec/issue → invoke /spec
