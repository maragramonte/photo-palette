# Desplegar la API en Fly.io

Fly construye el **mismo `Dockerfile`** que usa el VPS. No es un camino alternativo: es el
mismo contenedor en otra máquina. El día que levantes el servidor propio, `docker compose up
-d` y nada más que cambiar, salvo volver a apuntar `web/js/config.js`.

El despliegue en servidor propio está en el [README](../README.md#desplegar).

---

## Antes de empezar

Fly pide tarjeta aunque el gasto sea pequeño. Una `shared-cpu-1x` de 512 MB cuesta unos 3-4
€ al mes si está encendida siempre; con `auto_stop_machines` apagada entre visitas, bastante
menos. No hay sorpresas de tráfico: la API manda JSON de dos kilobytes.

```bash
# macOS / Linux
curl -L https://fly.io/install.sh | sh

# Windows (PowerShell)
pwsh -Command "iwr https://fly.io/install.ps1 -useb | iex"

fly auth signup     # o `fly auth login` si ya tienes cuenta
```

---

## Desplegar

Desde la raíz del repositorio:

```bash
fly launch --no-deploy --copy-config --name photo-palette --region mad
fly deploy
```

`--copy-config` le dice que use el [`fly.toml`](../fly.toml) que ya está escrito en lugar de
inventarse uno. `--no-deploy` separa crear la aplicación de publicarla, para que puedas leer
lo que ha hecho antes de que suba nada.

Si el nombre `photo-palette` está cogido, elige otro con `--name` y cambia también la línea
`app =` del `fly.toml`.

Comprobar que responde:

```bash
fly status
curl -fsS https://photo-palette.fly.dev/api/salud   # 200 sin cuerpo

curl -X POST "https://photo-palette.fly.dev/api/paletas?n=6&modo=vivos" \
  -F imagen=@web/fotos/marcador-01.jpg
```

---

## Conectar la web

Una línea en [`web/js/config.js`](../web/js/config.js):

```js
api: EN_LOCAL ? "http://localhost:8080" : "https://photo-palette.fly.dev",
```

`git push` y el workflow `web.yml` republica Pages solo. CORS no hay que tocarlo: el
`fly.toml` ya trae `PHOTOPALETTE_ORIGENES_PERMITIDOS` con la URL de GitHub Pages.

Si cambias el nombre de la aplicación o le pones dominio propio, actualiza esa variable:

```bash
fly secrets set PHOTOPALETTE_ORIGENES_PERMITIDOS="https://maragramonte.github.io"
```

---

## Dos cosas que conviene entender

### La máquina duerme, y eso está bien

Con `auto_stop_machines = "suspend"`, Fly suspende la máquina cuando nadie la usa y la
devuelve en menos de un segundo con la memoria intacta. Si tu `flyctl` no acepta `suspend`,
cámbialo por `"stop"`: entonces la apaga del todo y volver cuesta unos ocho segundos,
porque Spring Boot arranca de cero.

En los dos casos, el primer visitante no se come esa espera en la subida. La web pide
`/api/salud` nada más cargar, así que la máquina despierta mientras la persona lee el
titular, y para cuando arrastra su foto ya lleva un rato en marcha. Mientras tanto la zona
de subida dice *"Comprobando que la API está despierta…"*.

Para que no duerma nunca, en `fly.toml`:

```toml
min_machines_running = 1
auto_stop_machines = "off"
```

### El límite por IP necesita el perfil `fly`

Tomcat solo hace caso a `X-Forwarded-For` cuando la conexión viene de una dirección que
reconoce como proxy propio, y su lista de serie es toda IPv4. Fly conecta desde su red
privada `fdaa::/16`. Sin el perfil `fly`, Tomcat ignoraría la cabecera, vería todas las
visitas como una sola dirección y **las 10 peticiones por minuto serían un cupo único para
todo el mundo**.

No se rompería nada, y de ahí el peligro: el servicio respondería igual y los tests
seguirían en verde. Por eso la lista de proxies vive en el perfil `fly` de
[`application.yml`](../src/main/resources/application.yml) y hay un test,
`PerfilFlyTest`, que comprueba que sigue cubriendo `fdaa:` y que no se fía de nadie de
internet.

El `fly.toml` activa ese perfil con `SPRING_PROFILES_ACTIVE = "fly"`. Si lo quitas, quitas
el límite por IP sin enterarte.

---

## Mantenimiento

```bash
fly logs                      # registro en vivo
fly status                    # estado de las máquinas
fly deploy                    # publicar lo que haya en el repositorio
fly scale memory 1024         # subir a 1 GB si hiciera falta
fly apps destroy photo-palette
```

Para volver al VPS, `fly scale count 0` apaga esto sin borrar nada, y el
[`docker-compose.yml`](../docker-compose.yml) levanta exactamente la misma imagen en tu
servidor.

---

## Lo que esto no sustituye

**I03 sigue pendiente.** La prueba de carga —20 subidas de 10 MB a la vez— hay que hacerla
contra el `docker-compose` local, con los mismos 512 MB, antes de publicar la subida. Fly te
da la máquina, no la garantía de que el contenedor aguante. Y lo que el backlog quiere saber
de verdad es si este servicio puede convivir con el Dr. Agramonte en un mismo servidor,
cosa que en Fly no se prueba porque están separados.
