package com.maragramonte.photopalette.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.maragramonte.photopalette.dominio.ExtractorPaleta;
import com.maragramonte.photopalette.dominio.LectorImagen;
import com.maragramonte.photopalette.dominio.Modo;
import com.maragramonte.photopalette.dominio.RespuestaPaleta;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Genera el {@code paletas.json} que lee el feed de la web (tarea B06).
 *
 * <p>GitHub Pages no ejecuta Java, así que el feed no puede llamar a la API al cargar. En su
 * lugar, este comando analiza las fotos del feed con el mismo código que la API y deja el
 * resultado en un JSON estático. Consecuencia buscada: ningún color de la web esta escrito a
 * mano, y el feed sigue funcionando aunque la API este caida.
 *
 * <pre>
 *   mvn -q compile exec:java@paletas
 * </pre>
 *
 * <p>Los nombres y los textos alternativos salen de {@code web/fotos/fotos.json}. Si ese
 * archivo no existe, se usan todas las fotos de la carpeta y el nombre del archivo, y el
 * comando avisa de que faltan los textos alternativos.
 */
public final class GeneradorPaletasJson {

    private static final int COLORES = 6;
    private static final List<String> EXTENSIONES = List.of(".jpg", ".jpeg", ".png");

    private final LectorImagen lector = new LectorImagen();
    private final ExtractorPaleta extractor = new ExtractorPaleta();
    private final ObjectMapper json =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path carpetaFotos = Path.of(args.length > 0 ? args[0] : "web/fotos");
        Path destino = Path.of(args.length > 1 ? args[1] : "web/paletas.json");
        int salida = new GeneradorPaletasJson().generar(carpetaFotos, destino);
        if (salida != 0) {
            System.exit(salida);
        }
    }

    int generar(Path carpetaFotos, Path destino) throws IOException {
        if (!Files.isDirectory(carpetaFotos)) {
            System.err.println("No existe la carpeta de fotos: " + carpetaFotos.toAbsolutePath());
            return 1;
        }

        List<Foto> fotos = leerManifiesto(carpetaFotos);
        if (fotos.isEmpty()) {
            System.err.println("No hay ningún JPG ni PNG en " + carpetaFotos.toAbsolutePath()
                    + ". Copia ahí las fotos del feed (tarea D03) y vuelve a ejecutarlo.");
            return 1;
        }

        List<Map<String, Object>> entradas = new ArrayList<>();
        for (Foto foto : fotos) {
            Path archivo = carpetaFotos.resolve(foto.archivo());
            if (!Files.isRegularFile(archivo)) {
                System.err.println("  ! " + foto.archivo() + ": no está en la carpeta, se salta");
                continue;
            }
            byte[] datos = Files.readAllBytes(archivo);
            int[] pixeles = lector.muestrear(datos);

            Map<String, Object> paletas = new LinkedHashMap<>();
            for (Modo modo : Modo.values()) {
                RespuestaPaleta paleta = RespuestaPaleta.de(modo, extractor.extraer(pixeles, COLORES, modo));
                paletas.put(modo.parametro(), paleta);
            }

            Map<String, Object> entrada = new LinkedHashMap<>();
            entrada.put("archivo", carpetaFotos.getFileName() + "/" + foto.archivo());
            entrada.put("nombre", foto.nombre());
            entrada.put("alt", foto.alt());
            entrada.put("peso", Files.size(archivo));
            entrada.put("paletas", paletas);
            entradas.add(entrada);

            System.out.printf(
                    Locale.ROOT,
                    "  %-28s %3d KB   presentes: %d colores   vivos: %d colores%n",
                    foto.archivo(),
                    Files.size(archivo) / 1024,
                    ((RespuestaPaleta) paletas.get("presentes")).colores().size(),
                    ((RespuestaPaleta) paletas.get("vivos")).colores().size());
        }

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("generado", Instant.now().toString());
        salida.put("colores", COLORES);
        salida.put("fotos", entradas);

        Path padre = destino.toAbsolutePath().getParent();
        if (padre != null) {
            Files.createDirectories(padre);
        }
        json.writeValue(destino.toFile(), salida);
        System.out.println("Escrito " + destino.toAbsolutePath() + " con " + entradas.size() + " fotos.");
        return entradas.isEmpty() ? 1 : 0;
    }

    private List<Foto> leerManifiesto(Path carpetaFotos) throws IOException {
        Path manifiesto = carpetaFotos.resolve("fotos.json");
        if (Files.isRegularFile(manifiesto)) {
            return List.of(json.readValue(manifiesto.toFile(), Foto[].class));
        }
        System.out.println("Sin fotos.json: se usan los nombres de archivo y el alt queda vacío.");
        try (Stream<Path> archivos = Files.list(carpetaFotos)) {
            return archivos.filter(Files::isRegularFile)
                    .filter(GeneradorPaletasJson::esImagen)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(archivo -> {
                        String nombre = archivo.getFileName().toString();
                        return new Foto(nombre, nombre.replaceFirst("\\.[^.]+$", ""), "");
                    })
                    .toList();
        }
    }

    private static boolean esImagen(Path archivo) {
        String nombre = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONES.stream().anyMatch(nombre::endsWith);
    }

    /** Una entrada de {@code web/fotos/fotos.json}. */
    record Foto(String archivo, String nombre, String alt) {}
}
