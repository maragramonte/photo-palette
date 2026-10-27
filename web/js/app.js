/*
 * Photo Palette — la web.
 *
 * Tres reglas que explican casi todo el archivo:
 *
 *  1. Ningún color de una paleta se escribe aquí. El feed los lee de paletas.json (que
 *     genera el mismo código Java de la API) y las fotos subidas los piden a la API.
 *  2. La web tiene que seguir siendo útil con la API caída: el feed es estático, así que
 *     solo se desactiva la subida, y se dice por qué.
 *  3. Todo lo que se puede pulsar es un <button>, para que funcione con teclado sin
 *     inventar nada.
 */
(() => {
  "use strict";

  const CONFIG = window.PHOTO_PALETTE || {};
  const API = (CONFIG.api || "").replace(/\/$/, "");
  const MAX_BYTES = 10 * 1024 * 1024;
  const TIPOS = ["image/jpeg", "image/png"];
  const COLORES = 6;

  const EXPLICACION = {
    presentes:
      "Más presentes: los colores que más píxeles ocupan. Es fiel a la foto, y por eso " +
      "una foto oscura da una paleta oscura.",
    vivos:
      "Vivos: se ignoran las sombras, se tira el ruido y se ordena por saturación sin " +
      "repetir tonos. Puede devolver menos de " + COLORES + " colores, y eso es correcto.",
  };

  const estado = {
    modo: "presentes",
    fotos: [],
    apiViva: false,
    abierta: null,
    devolverFocoA: null,
  };

  const $ = (id) => document.getElementById(id);
  const nodos = {
    tarjetas: $("tarjetas"),
    resumen: $("feedResumen"),
    explicacion: $("explicacionModo"),
    interruptor: document.querySelectorAll(".interruptor__opcion"),
    formulario: $("formularioSubida"),
    caja: document.querySelector(".subida__caja"),
    archivo: $("archivo"),
    probarEjemplo: $("probarEjemplo"),
    estadoSubida: $("subidaEstado"),
    telon: $("telon"),
    detalle: $("detalle"),
    detalleNombre: $("detalleNombre"),
    detalleCuerpo: $("detalleCuerpo"),
    cerrarDetalle: $("cerrarDetalle"),
    aviso: $("avisoCopiado"),
  };

  // --- Color ---------------------------------------------------------------

  /** Pasa una componente de 0-255 a luz lineal, como manda la WCAG. */
  const aLineal = (componente) => {
    const v = componente / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };

  const luminancia = ([r, g, b]) => 0.2126 * aLineal(r) + 0.7152 * aLineal(g) + 0.0722 * aLineal(b);

  const contraste = (unColor, otroColor) => {
    const a = luminancia(unColor);
    const b = luminancia(otroColor);
    const [claro, oscuro] = a > b ? [a, b] : [b, a];
    return (claro + 0.05) / (oscuro + 0.05);
  };

  /**
   * Elige blanco o casi negro para escribir encima de un color (F05).
   *
   * No se decide por si el color "parece oscuro": se calculan los dos contrastes y gana el
   * mayor. Se devuelve también la razón de contraste para poder enseñarla, porque un 4,5:1
   * es lo que pide la WCAG en nivel AA para texto normal.
   */
  const textoLegible = (rgb) => {
    const blanco = [255, 255, 255];
    const negro = [11, 13, 26];
    const conBlanco = contraste(rgb, blanco);
    const conNegro = contraste(rgb, negro);
    const gana = conBlanco >= conNegro;
    return {
      hex: gana ? "#ffffff" : "#0b0d1a",
      nombre: gana ? "blanco" : "negro",
      razon: gana ? conBlanco : conNegro,
    };
  };

  const saturacion = ([r, g, b]) => {
    const maximo = Math.max(r, g, b);
    return maximo === 0 ? 0 : (maximo - Math.min(r, g, b)) / maximo;
  };

  // --- Feed ----------------------------------------------------------------

  const paletaDe = (foto) => (foto.paletas || {})[estado.modo] || null;

  function pintarFeed() {
    nodos.tarjetas.replaceChildren();

    if (!estado.fotos.length) {
      const vacio = document.createElement("li");
      vacio.className = "vacio";
      vacio.textContent =
        "Todavía no hay paletas. Copia tus fotos en web/fotos y ejecuta " +
        "mvn compile exec:java@paletas para generar paletas.json.";
      nodos.tarjetas.append(vacio);
      nodos.resumen.textContent = "Sin paletas que mostrar.";
      return;
    }

    estado.fotos.forEach((foto) => nodos.tarjetas.append(tarjetaDe(foto)));

    const cuantas = estado.fotos.length;
    nodos.resumen.textContent =
      cuantas + (cuantas === 1 ? " foto" : " fotos") + ", con su paleta real calculada por la API.";
  }

  function tarjetaDe(foto) {
    const paleta = paletaDe(foto);
    const colores = paleta ? paleta.colores : [];

    const item = document.createElement("li");
    const boton = document.createElement("button");
    boton.type = "button";
    boton.className = "tarjeta" + (foto.esTuya ? " es-nueva" : "");
    boton.setAttribute(
      "aria-label",
      "Ver la paleta de " + foto.nombre + ": " + colores.length +
        (colores.length === 1 ? " color" : " colores")
    );
    boton.addEventListener("click", () => abrirDetalle(foto, boton));

    const foto_ = document.createElement("img");
    foto_.className = "tarjeta__foto";
    foto_.src = foto.archivo;
    foto_.alt = foto.alt || foto.nombre;
    foto_.loading = "lazy";
    foto_.decoding = "async";
    boton.append(foto_);

    if (foto.esTuya) {
      const etiqueta = document.createElement("span");
      etiqueta.className = "tarjeta__etiqueta";
      etiqueta.textContent = "Tu foto";
      boton.append(etiqueta);
    }

    const pie = document.createElement("span");
    pie.className = "tarjeta__pie";
    const nombre = document.createElement("span");
    nombre.className = "tarjeta__nombre";
    nombre.textContent = foto.nombre;
    const cuantos = document.createElement("span");
    cuantos.className = "tarjeta__cuantos";
    cuantos.textContent = colores.length
      ? colores.length + (colores.length === 1 ? " color" : " colores")
      : "calculando…";
    pie.append(nombre, cuantos);
    boton.append(pie);

    const muestras = document.createElement("span");
    muestras.className = "muestras";
    muestras.setAttribute("aria-hidden", "true");
    colores.forEach((color) => {
      const trozo = document.createElement("span");
      trozo.className = "muestras__color";
      trozo.style.background = color.hex;
      muestras.append(trozo);
    });
    boton.append(muestras);

    item.append(boton);
    return item;
  }

  // --- Detalle -------------------------------------------------------------

  function abrirDetalle(foto, origen, enVivo) {
    const paleta = paletaDe(foto);
    if (!paleta) {
      return;
    }
    estado.abierta = foto;
    estado.devolverFocoA = origen || null;

    const bloques = [bloqueMuestras(paleta), bloqueCss(paleta), bloqueVistaPrevia(paleta)];
    if (enVivo) {
      bloques.unshift(avisoEnVivo());
    }

    nodos.detalleNombre.textContent = foto.nombre;
    nodos.detalleCuerpo.replaceChildren(...bloques);

    nodos.telon.hidden = false;
    nodos.detalle.hidden = false;
    document.body.style.overflow = "hidden";
    nodos.cerrarDetalle.focus();
  }

  function cerrarDetalle() {
    nodos.telon.hidden = true;
    nodos.detalle.hidden = true;
    document.body.style.overflow = "";
    estado.abierta = null;
    if (estado.devolverFocoA) {
      estado.devolverFocoA.focus();
      estado.devolverFocoA = null;
    }
  }

  function bloqueMuestras(paleta) {
    const bloque = seccion("Los colores · pulsa uno para copiar su HEX");
    const rejilla = document.createElement("div");
    rejilla.className = "paleta-grande";

    paleta.colores.forEach((color) => {
      const texto = textoLegible(color.rgb);
      const boton = document.createElement("button");
      boton.type = "button";
      boton.className = "color-grande";
      boton.style.background = color.hex;
      boton.style.color = texto.hex;
      boton.setAttribute("aria-label", "Copiar " + color.hex);

      const hex = document.createElement("span");
      hex.className = "color-grande__hex";
      hex.textContent = color.hex;

      const dato = document.createElement("span");
      dato.className = "color-grande__dato";
      dato.textContent = color.porcentaje + " % · " + color.variable;

      boton.append(hex, dato);
      boton.addEventListener("click", () => copiar(color.hex, "Copiado " + color.hex));
      rejilla.append(boton);
    });

    bloque.append(rejilla);
    return bloque;
  }

  function bloqueCss(paleta) {
    const bloque = seccion("Pégalo en tu hoja de estilos");
    const codigo = document.createElement("pre");
    codigo.className = "codigo";
    codigo.textContent = paleta.css;

    const acciones = document.createElement("div");
    acciones.className = "codigo__acciones";
    const boton = document.createElement("button");
    boton.type = "button";
    boton.className = "boton";
    boton.textContent = "Copiar el bloque :root";
    boton.addEventListener("click", () => copiar(paleta.css, "Bloque :root copiado"));
    acciones.append(boton);

    bloque.append(codigo, acciones);
    return bloque;
  }

  /**
   * La vista previa (F05): una tarjeta pintada solo con los colores de la paleta.
   *
   * El fondo es el color más oscuro, la superficie el siguiente y el botón el más saturado.
   * El texto no se elige a ojo: se calcula el contraste y se enseña el resultado, para que
   * se vea que cumple AA y no haya que fiarse.
   */
  function bloqueVistaPrevia(paleta) {
    const bloque = seccion("Vista previa con esta paleta");
    const porLuz = [...paleta.colores].sort((a, b) => luminancia(a.rgb) - luminancia(b.rgb));
    const porColor = [...paleta.colores].sort((a, b) => saturacion(b.rgb) - saturacion(a.rgb));

    const fondo = porLuz[0];
    const superficie = porLuz[1] || porLuz[0];
    const acento = porColor[0];
    const textoSuperficie = textoLegible(superficie.rgb);
    const textoAcento = textoLegible(acento.rgb);

    const marco = document.createElement("div");
    marco.className = "vista-previa";
    marco.style.background = fondo.hex;

    const tarjeta = document.createElement("div");
    tarjeta.className = "vista-previa__tarjeta";
    tarjeta.style.background = superficie.hex;
    tarjeta.style.color = textoSuperficie.hex;

    const titulo = document.createElement("p");
    titulo.className = "vista-previa__titulo";
    titulo.textContent = "Tu próxima web";

    const cuerpo = document.createElement("p");
    cuerpo.className = "vista-previa__texto";
    cuerpo.textContent = "Fondo, superficie, texto y botón salen de esta foto. Nada más.";

    const boton = document.createElement("button");
    boton.className = "vista-previa__boton";
    boton.type = "button";
    boton.disabled = true;
    boton.style.background = acento.hex;
    boton.style.color = textoAcento.hex;
    boton.textContent = "Un botón";

    tarjeta.append(titulo, cuerpo, boton);
    marco.append(tarjeta);

    const nota = document.createElement("p");
    nota.className = "contraste";
    nota.textContent =
      "Texto " + textoSuperficie.nombre + " sobre " + superficie.hex + ": contraste " +
      textoSuperficie.razon.toFixed(1) + ":1 " + (textoSuperficie.razon >= 4.5 ? "(AA)" : "(por debajo de AA)") +
      ". En el botón, " + textoAcento.nombre + " sobre " + acento.hex + ": " +
      textoAcento.razon.toFixed(1) + ":1.";

    bloque.append(marco, nota);
    return bloque;
  }

  /** Deja claro que esta paleta no sale de paletas.json: la API la acaba de calcular. */
  function avisoEnVivo() {
    const aviso = document.createElement("p");
    aviso.className = "en-vivo";
    const punto = document.createElement("span");
    punto.className = "en-vivo__punto";
    punto.setAttribute("aria-hidden", "true");
    const texto = document.createElement("span");
    texto.textContent = "Calculada ahora mismo por la API, no leída de paletas.json.";
    aviso.append(punto, texto);
    return aviso;
  }

  function seccion(titulo) {
    const bloque = document.createElement("div");
    bloque.className = "bloque";
    const encabezado = document.createElement("h3");
    encabezado.className = "bloque__titulo";
    encabezado.textContent = titulo;
    bloque.append(encabezado);
    return bloque;
  }

  // --- Copiar --------------------------------------------------------------

  /**
   * Copiar con dos redes debajo.
   *
   * La Clipboard API es la buena, pero falla en más sitios de los que parece: contexto no
   * seguro, permiso denegado, un navegador antiguo. Antes de decirle a nadie que copie a
   * mano un HEX, se prueba también el método viejo.
   */
  async function copiar(texto, aviso) {
    if (navigator.clipboard && window.isSecureContext) {
      try {
        await navigator.clipboard.writeText(texto);
        mostrarAviso(aviso + " ✓");
        return;
      } catch (error) {
        // Sin permiso para el portapapeles: queda el método viejo.
      }
    }
    if (copiarAlaAntigua(texto)) {
      mostrarAviso(aviso + " ✓");
    } else {
      mostrarAviso("El navegador no ha dejado copiar. Selecciónalo a mano.");
    }
  }

  function copiarAlaAntigua(texto) {
    const campo = document.createElement("textarea");
    campo.value = texto;
    campo.setAttribute("readonly", "");
    campo.style.position = "fixed";
    campo.style.opacity = "0";
    document.body.append(campo);
    campo.select();
    let copiado = false;
    try {
      copiado = document.execCommand("copy");
    } catch (error) {
      copiado = false;
    }
    campo.remove();
    return copiado;
  }

  let temporizadorAviso = null;
  function mostrarAviso(texto) {
    nodos.aviso.textContent = texto;
    nodos.aviso.classList.add("se-ve");
    clearTimeout(temporizadorAviso);
    temporizadorAviso = setTimeout(() => nodos.aviso.classList.remove("se-ve"), 1800);
  }

  // --- Interruptor de modo -------------------------------------------------

  async function cambiarModo(modo) {
    estado.modo = modo;
    nodos.interruptor.forEach((boton) =>
      boton.setAttribute("aria-checked", String(boton.dataset.modo === modo))
    );
    nodos.explicacion.textContent = EXPLICACION[modo];
    pintarFeed();

    // Las fotos subidas solo traen la paleta del modo que estaba activo cuando se
    // subieron. La del otro modo se pide a la API la primera vez que hace falta.
    const pendientes = estado.fotos.filter((foto) => foto.fichero && !foto.paletas[modo]);
    for (const foto of pendientes) {
      try {
        foto.paletas[modo] = await pedirPaleta(foto.fichero, modo);
      } catch (error) {
        decirEstado(mensajeDe(error), "error");
      }
      pintarFeed();
    }
  }

  // --- Subida --------------------------------------------------------------

  function decirEstado(texto, tono) {
    nodos.estadoSubida.textContent = texto || "";
    if (tono) {
      nodos.estadoSubida.dataset.tono = tono;
    } else {
      delete nodos.estadoSubida.dataset.tono;
    }
  }

  function mensajeDe(error) {
    return error && error.message
      ? error.message
      : "No se ha podido analizar la foto. Inténtalo otra vez.";
  }

  async function pedirPaleta(fichero, modo) {
    const cuerpo = new FormData();
    cuerpo.append("imagen", fichero);

    const respuesta = await fetch(
      API + "/api/paletas?n=" + COLORES + "&modo=" + modo,
      { method: "POST", body: cuerpo }
    ).catch(() => {
      throw new Error("No se ha podido llegar a la API. Comprueba tu conexión.");
    });

    if (respuesta.ok) {
      return respuesta.json();
    }
    // La API contesta ProblemDetail (RFC 9457): su `detail` está escrito para leerse.
    const problema = await respuesta.json().catch(() => null);
    const error = new Error(
      (problema && problema.detail) ||
        "La API ha respondido " + respuesta.status + " y no ha explicado por qué."
    );
    error.estado = respuesta.status;
    throw error;
  }

  async function subir(fichero) {
    if (!fichero) {
      return;
    }
    if (!TIPOS.includes(fichero.type)) {
      decirEstado("Solo JPG y PNG. El WebP todavía no se puede analizar.", "error");
      return;
    }
    if (fichero.size > MAX_BYTES) {
      const megas = (fichero.size / 1024 / 1024).toFixed(1);
      decirEstado("Tu foto pesa " + megas + " MB y el máximo son 10 MB.", "error");
      return;
    }
    if (!estado.apiViva) {
      decirEstado("La API no está disponible, así que no se puede analizar tu foto.", "error");
      return;
    }

    nodos.caja.classList.add("esta-ocupada");
    decirEstado("Analizando tu foto…", "trabajando");

    try {
      const paleta = await conUnReintento(() => pedirPaleta(fichero, estado.modo));
      const foto = {
        archivo: URL.createObjectURL(fichero),
        nombre: fichero.name.replace(/\.[^.]+$/, ""),
        alt: "Foto que acabas de subir: " + fichero.name,
        esTuya: true,
        fichero: fichero,
        paletas: { [estado.modo]: paleta },
      };
      estado.fotos.unshift(foto);
      pintarFeed();
      decirEstado(
        "Listo: " + paleta.colores.length +
          (paleta.colores.length === 1 ? " color" : " colores") + " en modo " + paleta.modo + ".",
        null
      );
      abrirDetalle(foto, nodos.tarjetas.querySelector(".tarjeta"));
    } catch (error) {
      decirEstado(mensajeDe(error), "error");
      ofrecerReintento(fichero);
    } finally {
      nodos.caja.classList.remove("esta-ocupada");
      nodos.formulario.reset();
    }
  }

  /** El 503 significa "ahora mismo no, vuelve en unos segundos", así que se vuelve. */
  async function conUnReintento(intento) {
    try {
      return await intento();
    } catch (error) {
      if (error.estado !== 503) {
        throw error;
      }
      decirEstado("La API está ocupada. Reintentando en 3 segundos…", "trabajando");
      await new Promise((sigue) => setTimeout(sigue, 3000));
      return intento();
    }
  }

  function ofrecerReintento(fichero) {
    const boton = document.createElement("button");
    boton.type = "button";
    boton.className = "boton";
    boton.style.marginTop = "var(--e-1)";
    boton.textContent = "Reintentar";
    boton.addEventListener("click", () => {
      boton.remove();
      subir(fichero);
    });
    nodos.estadoSubida.after(boton);
  }

  // --- Foto de ejemplo -----------------------------------------------------

  /**
   * Analiza una foto del feed en vivo, para quien llega sin ninguna a mano.
   *
   * Usa las mismas fotos del feed a propósito: no hay que mantener un juego aparte de
   * imágenes de ejemplo, y el día que el feed lleve fotos de verdad este botón las usa
   * sin tocar nada.
   *
   * No añade tarjeta, porque duplicaría una foto que ya está en el feed. Abre el detalle
   * directamente, que es donde está lo que se viene a ver: los colores y el bloque CSS.
   * Y de paso demuestra algo que el feed solo no puede: que la API está viva, porque esta
   * paleta se acaba de calcular y no viene del JSON.
   */
  async function analizarEjemplo() {
    const candidatas = estado.fotos.filter((foto) => !foto.esTuya);
    if (!candidatas.length) {
      decirEstado("Todavía no hay ninguna foto en el feed que analizar.", "error");
      return;
    }
    if (!estado.apiViva) {
      decirEstado("La API no está disponible, así que no se puede analizar nada.", "error");
      return;
    }

    const elegida = candidatas[Math.floor(Math.random() * candidatas.length)];
    nodos.probarEjemplo.disabled = true;
    decirEstado("Analizando " + elegida.nombre + " con la API…", "trabajando");

    try {
      const descarga = await fetch(elegida.archivo);
      if (!descarga.ok) {
        throw new Error("No se ha podido leer " + elegida.archivo + ".");
      }
      const blob = await descarga.blob();
      const fichero = new File([blob], elegida.archivo.split("/").pop(), { type: blob.type });

      const paleta = await conUnReintento(() => pedirPaleta(fichero, estado.modo));
      decirEstado(
        elegida.nombre + ": " + paleta.colores.length +
          (paleta.colores.length === 1 ? " color" : " colores") + " en modo " + paleta.modo + ".",
        null
      );
      abrirDetalle(
        { nombre: elegida.nombre, paletas: { [estado.modo]: paleta } },
        nodos.probarEjemplo,
        true
      );
    } catch (error) {
      decirEstado(mensajeDe(error), "error");
    } finally {
      nodos.probarEjemplo.disabled = false;
    }
  }

  // --- Salud ---------------------------------------------------------------

  /**
   * Pregunta a la API si está viva antes de ofrecer la subida.
   *
   * Esta llamada hace dos cosas. La evidente: decidir si se puede subir una foto. La otra:
   * si el servidor tarda en contestar —acaba de arrancar, o va justo—, esto se come la
   * espera mientras el visitante lee el titular, no cuando arrastra su foto.
   */
  async function comprobarSalud() {
    if (!API) {
      estado.apiViva = false;
      nodos.probarEjemplo.disabled = true;
      decirEstado(
        "La subida está apagada porque no hay ninguna API configurada (web/js/config.js). " +
          "El feed funciona igual: sus paletas son estáticas.",
        "error"
      );
      return;
    }
    decirEstado("Comprobando que la API responde…", "trabajando");
    try {
      const respuesta = await fetch(API + "/api/salud", { method: "GET" });
      estado.apiViva = respuesta.ok;
    } catch (error) {
      estado.apiViva = false;
    }

    nodos.probarEjemplo.disabled = !estado.apiViva;

    if (estado.apiViva) {
      decirEstado("");
    } else {
      decirEstado("La API no responde ahora mismo. El feed sigue funcionando.", "error");
      const boton = document.createElement("button");
      boton.type = "button";
      boton.className = "boton";
      boton.style.marginTop = "var(--e-1)";
      boton.textContent = "Volver a comprobar";
      boton.addEventListener("click", () => {
        boton.remove();
        comprobarSalud();
      });
      nodos.estadoSubida.after(boton);
    }
  }

  // --- Arranque ------------------------------------------------------------

  async function cargarFeed() {
    try {
      const respuesta = await fetch("paletas.json", { cache: "no-cache" });
      if (!respuesta.ok) {
        throw new Error("paletas.json ha respondido " + respuesta.status);
      }
      const datos = await respuesta.json();
      estado.fotos = Array.isArray(datos.fotos) ? datos.fotos : [];
    } catch (error) {
      estado.fotos = [];
      nodos.resumen.textContent =
        "No se ha podido leer paletas.json. Sírvela por HTTP, no abriendo el archivo.";
    }
    pintarFeed();
  }

  function conectarEventos() {
    nodos.interruptor.forEach((boton) =>
      boton.addEventListener("click", () => cambiarModo(boton.dataset.modo))
    );

    nodos.archivo.addEventListener("change", (evento) => subir(evento.target.files[0]));
    nodos.probarEjemplo.addEventListener("click", analizarEjemplo);
    nodos.formulario.addEventListener("submit", (evento) => evento.preventDefault());

    ["dragenter", "dragover"].forEach((nombre) =>
      nodos.caja.addEventListener(nombre, (evento) => {
        evento.preventDefault();
        nodos.caja.classList.add("esta-encima");
      })
    );
    ["dragleave", "drop"].forEach((nombre) =>
      nodos.caja.addEventListener(nombre, () => nodos.caja.classList.remove("esta-encima"))
    );
    nodos.caja.addEventListener("drop", (evento) => {
      evento.preventDefault();
      subir(evento.dataTransfer.files[0]);
    });

    nodos.cerrarDetalle.addEventListener("click", cerrarDetalle);
    nodos.telon.addEventListener("click", cerrarDetalle);
    document.addEventListener("keydown", (evento) => {
      if (evento.key === "Escape" && estado.abierta) {
        cerrarDetalle();
      }
    });

    const enlaceGitHub = document.querySelector('a[href*="github.com"]');
    if (enlaceGitHub && CONFIG.repositorio) {
      document.querySelectorAll('a[href*="github.com"]').forEach((enlace) => {
        enlace.href = enlace.href.includes("#readme") ? CONFIG.repositorio + "#readme" : CONFIG.repositorio;
      });
    }
  }

  nodos.explicacion.textContent = EXPLICACION[estado.modo];
  conectarEventos();
  cargarFeed();
  comprobarSalud();
})();
