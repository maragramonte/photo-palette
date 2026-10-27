package com.maragramonte.photopalette.web;

import com.maragramonte.photopalette.dominio.DemasiadosAnalisisException;
import com.maragramonte.photopalette.dominio.ImagenAusenteException;
import com.maragramonte.photopalette.dominio.ImagenDemasiadoGrandeException;
import com.maragramonte.photopalette.dominio.ImagenIlegibleException;
import com.maragramonte.photopalette.dominio.ModoInvalidoException;
import com.maragramonte.photopalette.dominio.NumeroColoresInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce los errores del dominio a ProblemDetail (RFC 9457) con texto en español.
 *
 * <p>El frontend pinta el campo {@code detail} tal cual, así que cada mensaje tiene que
 * poder leerlo una persona que acaba de arrastrar una foto y no sabe qué es un megapíxel.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErrores {

    private static final String BASE_TIPO = "https://maragramonte.github.io/photo-palette/errores/";

    // --- 400 ---------------------------------------------------------------

    @ExceptionHandler({
        ImagenAusenteException.class,
        MissingServletRequestPartException.class,
        MissingServletRequestParameterException.class
    })
    public ProblemDetail faltaLaImagen(Exception e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.BAD_REQUEST,
                "falta-la-imagen",
                "Falta la imagen",
                e instanceof ImagenAusenteException
                        ? e.getMessage()
                        : "No has adjuntado ninguna imagen en el campo 'imagen'.",
                peticion);
    }

    @ExceptionHandler(NumeroColoresInvalidoException.class)
    public ProblemDetail numeroInvalido(NumeroColoresInvalidoException e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.BAD_REQUEST, "n-invalido", "Número de colores inválido", e.getMessage(), peticion);
    }

    @ExceptionHandler(ModoInvalidoException.class)
    public ProblemDetail modoInvalido(ModoInvalidoException e, HttpServletRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "modo-invalido", "Modo inválido", e.getMessage(), peticion);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class})
    public ProblemDetail parametroInvalido(Exception e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.BAD_REQUEST,
                "parametro-invalido",
                "Parámetro inválido",
                "Alguno de los parámetros no tiene el formato esperado. Revisa 'n' y 'modo'.",
                peticion);
    }

    // --- 413 ---------------------------------------------------------------

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail demasiadoPesada(MaxUploadSizeExceededException e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "imagen-demasiado-pesada",
                "La imagen pesa demasiado",
                "La imagen pasa de 10 MB. Expórtala más ligera y vuelve a subirla.",
                peticion);
    }

    // --- 422 ---------------------------------------------------------------

    @ExceptionHandler(ImagenIlegibleException.class)
    public ProblemDetail ilegible(ImagenIlegibleException e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "imagen-ilegible",
                "No se puede leer la imagen",
                e.getMessage(),
                peticion);
    }

    @ExceptionHandler(ImagenDemasiadoGrandeException.class)
    public ProblemDetail demasiadosPixeles(
            ImagenDemasiadoGrandeException e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "demasiados-pixeles",
                "La imagen tiene demasiados píxeles",
                e.getMessage(),
                peticion);
    }

    // --- 503 ---------------------------------------------------------------

    @ExceptionHandler(DemasiadosAnalisisException.class)
    public ProblemDetail ocupado(DemasiadosAnalisisException e, HttpServletRequest peticion) {
        return problema(
                HttpStatus.SERVICE_UNAVAILABLE,
                "servidor-ocupado",
                "El servidor está ocupado",
                e.getMessage(),
                peticion);
    }

    static ProblemDetail problema(
            HttpStatus estado, String tipo, String titulo, String detalle, HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create(BASE_TIPO + tipo));
        problema.setTitle(titulo);
        problema.setDetail(detalle);
        if (peticion != null) {
            problema.setInstance(URI.create(peticion.getRequestURI()));
        }
        return problema;
    }
}
