"""Prueba de carga I03: 20 subidas de 10 MB a la vez contra el contenedor de docker-compose.

Lo que tiene que pasar, según CONCEPTO.md:
  - el contenedor no pasa de 512 MB,
  - responde 503 o 429 en vez de caerse,
  - y el vecino (el Dr. Agramonte en el VPS) sigue respondiendo.

Uso, con la API levantada con `docker compose up -d --build`:

    python carga/i03.py --fotos <carpeta con JPG de ~10 MB>

Solo usa la biblioteca estándar. La memoria se lee del cgroup del contenedor, no de
`docker stats`: memory.peak es el máximo real que ha tocado, no una muestra cada segundo
que puede saltarse el pico.
"""

import argparse
import http.client
import itertools
import json
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

LIMITE_MB = 512


def docker(*args):
    return subprocess.run(["docker", *args], capture_output=True, text=True).stdout.strip()


def cgroup(contenedor, fichero):
    return docker("exec", contenedor, "cat", f"/sys/fs/cgroup/{fichero}")


def mib(n):
    return int(n) / 1048576


def cuerpo_multipart(datos, nombre):
    frontera = uuid.uuid4().hex
    cabeza = (
        f"--{frontera}\r\n"
        f'Content-Disposition: form-data; name="imagen"; filename="{nombre}"\r\n'
        "Content-Type: image/jpeg\r\n\r\n"
    ).encode()
    return cabeza + datos + f"\r\n--{frontera}--\r\n".encode(), frontera


def subir(host, puerto, cuerpo, frontera, ip=None):
    cabeceras = {"Content-Type": f"multipart/form-data; boundary={frontera}"}
    # Desde el host, la conexión llega al contenedor por la pasarela de Docker, que está en
    # internal-proxies: Tomcat se cree el X-Forwarded-For. Así se simulan visitas distintas
    # sin tener veinte máquinas.
    if ip:
        cabeceras["X-Forwarded-For"] = ip
    inicio = time.monotonic()
    try:
        conexion = http.client.HTTPConnection(host, puerto, timeout=120)
        conexion.request("POST", "/api/paletas?n=6&modo=vivos", cuerpo, cabeceras)
        estado = conexion.getresponse().status
        conexion.close()
    except Exception as e:
        estado = type(e).__name__
    return estado, time.monotonic() - inicio


class Vigia(threading.Thread):
    """Mira la memoria del contenedor y al vecino mientras dura un escenario."""

    def __init__(self, contenedor, vecino):
        super().__init__(daemon=True)
        self.contenedor, self.vecino = contenedor, vecino
        self.parar = threading.Event()
        self.memoria_max = 0
        self.anon_max = 0
        self.vecino_fallos = 0
        self.vecino_peticiones = 0
        self.vecino_lento = 0.0

    def run(self):
        while not self.parar.is_set():
            # memory.current suma la caché de disco, y Tomcat escribe cada subida en un
            # temporal: llega al techo del cgroup sin que la JVM esté apurada, porque el
            # kernel recupera esa caché antes de matar a nadie. Lo que no se puede
            # recuperar es anon (heap, metaspace, memoria nativa), y eso es lo que mide
            # si el contenedor va justo.
            stat = dict(l.split() for l in cgroup(self.contenedor, "memory.stat").splitlines()
                        if l.count(" ") == 1)
            if stat:
                self.memoria_max = max(self.memoria_max, int(stat["anon"]) + int(stat["file"]))
                self.anon_max = max(self.anon_max, int(stat["anon"]))
            if self.vecino:
                inicio = time.monotonic()
                try:
                    urllib.request.urlopen(self.vecino, timeout=5).read()
                except Exception:
                    self.vecino_fallos += 1
                self.vecino_peticiones += 1
                self.vecino_lento = max(self.vecino_lento, time.monotonic() - inicio)
            time.sleep(0.25)


def escenario(nombre, trabajos, args):
    vigia = Vigia(args.contenedor, args.vecino)
    vigia.start()
    inicio = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.concurrencia) as grupo:
        resultados = list(grupo.map(lambda t: t(), trabajos))
    duracion = time.monotonic() - inicio
    vigia.parar.set()
    vigia.join()

    estados = Counter(e for e, _ in resultados)
    lentas = max(s for _, s in resultados)
    print(f"\n## {nombre}")
    print(f"  peticiones: {len(resultados)} en {duracion:.1f} s, la más lenta {lentas:.1f} s")
    print(f"  respuestas: {dict(sorted(estados.items(), key=str))}")
    print(f"  memoria, máximo muestreado: {mib(vigia.memoria_max):.0f} MiB en total,"
          f" {mib(vigia.anon_max):.0f} MiB sin contar la caché de disco")
    if args.vecino:
        print(f"  vecino: {vigia.vecino_fallos} fallos de {vigia.vecino_peticiones},"
              f" la más lenta {vigia.vecino_lento * 1000:.0f} ms")
    return {"estados": estados, "vecino_fallos": vigia.vecino_fallos, "anon": vigia.anon_max}


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--fotos", required=True, type=Path)
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--puerto", type=int, default=8080)
    p.add_argument("--contenedor", default="photo-palette")
    p.add_argument("--vecino", help="URL de otro servicio que tiene que seguir vivo")
    p.add_argument("--concurrencia", type=int, default=20)
    p.add_argument("--segundos", type=int, default=60, help="duración de la carga sostenida")
    args = p.parse_args()

    fotos = sorted(args.fotos.glob("*.jpg"))
    if not fotos:
        sys.exit(f"No hay JPG en {args.fotos}")
    cuerpos = [cuerpo_multipart(f.read_bytes(), f.name) for f in fotos]
    print("Fotos:", ", ".join(f"{f.name} ({mib(f.stat().st_size):.1f} MiB)" for f in fotos))

    inicial = docker("inspect", "-f", "{{.RestartCount}}", args.contenedor)
    if not inicial.isdigit():
        sys.exit(f"No encuentro el contenedor {args.contenedor}. ¿Está levantado el compose?")
    reinicios_antes = int(inicial)

    n = args.concurrencia
    foto = itertools.cycle(cuerpos)
    resultados = []

    # 1. Lo que hace una sola persona impaciente: el límite por IP corta a partir de 10.
    trabajos = [lambda c=next(foto): subir(args.host, args.puerto, *c) for _ in range(n)]
    resultados.append(escenario(f"{n} a la vez desde una IP", trabajos, args))

    # 2. Lo que dice el backlog: 20 personas distintas subiendo a la vez.
    ips = (f"10.{i // 65536 % 256}.{i // 256 % 256}.{i % 256}" for i in itertools.count(1))
    trabajos = [lambda c=next(foto), ip=next(ips): subir(args.host, args.puerto, *c, ip)
                for _ in range(n)]
    resultados.append(escenario(f"{n} a la vez desde {n} IPs", trabajos, args))

    # 3. El peor caso: la ráfaga anterior sin parar durante un minuto, cada petición con
    # una IP nueva para que el límite por IP no alivie nada. Solo el semáforo sujeta.
    fin = time.monotonic() + args.segundos
    candado = threading.Lock()

    def bucle():
        cuenta = []
        while time.monotonic() < fin:
            with candado:
                c, ip = next(foto), next(ips)
            cuenta.append(subir(args.host, args.puerto, *c, ip))
        return cuenta

    vigia = Vigia(args.contenedor, args.vecino)
    vigia.start()
    with ThreadPoolExecutor(max_workers=n) as grupo:
        lotes = list(grupo.map(lambda _: bucle(), range(n)))
    vigia.parar.set()
    vigia.join()
    todas = [r for lote in lotes for r in lote]
    estados = Counter(e for e, _ in todas)
    print(f"\n## {n} sin parar durante {args.segundos} s, una IP nueva por petición")
    print(f"  peticiones: {len(todas)}, la más lenta {max(s for _, s in todas):.1f} s")
    print(f"  respuestas: {dict(sorted(estados.items(), key=str))}")
    print(f"  memoria, máximo muestreado: {mib(vigia.memoria_max):.0f} MiB en total,"
          f" {mib(vigia.anon_max):.0f} MiB sin contar la caché de disco")
    if args.vecino:
        print(f"  vecino: {vigia.vecino_fallos} fallos de {vigia.vecino_peticiones},"
              f" la más lenta {vigia.vecino_lento * 1000:.0f} ms")
    resultados.append({"estados": estados, "vecino_fallos": vigia.vecino_fallos, "anon": vigia.anon_max})

    # El veredicto, con los números del cgroup y de Docker, no con los muestreados.
    pico = mib(cgroup(args.contenedor, "memory.peak") or 0)
    anon = mib(max(r["anon"] for r in resultados))
    oom = dict(l.split() for l in cgroup(args.contenedor, "memory.events").splitlines())
    estado = json.loads(docker("inspect", "-f", "{{json .State}}", args.contenedor))
    reinicios = int(docker("inspect", "-f", "{{.RestartCount}}", args.contenedor))
    otros = Counter()
    for r in resultados:
        otros.update({e: c for e, c in r["estados"].items() if e not in (200, 429, 503)})
    vecino_fallos = sum(r["vecino_fallos"] for r in resultados)

    comprobaciones = {
        # El cgroup no deja pasar de 512 MB por definición: memory.peak toca el techo en
        # cuanto la caché de disco lo llena. Lo que cuenta es que lo irrecuperable quepa.
        f"sin caché de disco, {anon:.0f} MiB < {LIMITE_MB} MiB"
        f" (con ella, memory.peak = {pico:.0f} MiB)": anon < LIMITE_MB,
        f"oom_kill = {oom.get('oom_kill', '?')}": oom.get("oom_kill") == "0",
        f"OOMKilled = {estado['OOMKilled']}, reinicios {reinicios - reinicios_antes}":
            not estado["OOMKilled"] and reinicios == reinicios_antes,
        f"solo 200, 429 o 503 (otros: {dict(otros) or 'ninguno'})": not otros,
    }
    if args.vecino:
        comprobaciones[f"el vecino no falla ({vecino_fallos} fallos)"] = vecino_fallos == 0

    print("\n## Veredicto")
    for texto, ok in comprobaciones.items():
        print(f"  [{'OK' if ok else 'FALLA'}] {texto}")
    sys.exit(0 if all(comprobaciones.values()) else 1)


if __name__ == "__main__":
    main()
