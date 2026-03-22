/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.excepcion;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.RespuestaApi;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.common.excepcion.ValidacionException;
import com.baryx.servidor.modelo.entidad.LogCritico;
import com.baryx.servidor.repositorio.LogCriticoRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

// Manejador global de excepciones para la aplicación, utilizando @RestControllerAdvice para interceptar y manejar excepciones de manera centralizada.
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    private static final Logger logger = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    private final LogCriticoRepositorio logCriticoRepositorio;

    public ManejadorGlobalExcepciones(LogCriticoRepositorio logCriticoRepositorio) {
        this.logCriticoRepositorio = logCriticoRepositorio;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(RespuestaApi.error(ex.getCodigoError(), ex.getMessage()));
    }

    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarValidacion(ValidacionException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(RespuestaApi.error(ex.getCodigoError(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarValidacionArgumentos(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String campo = ((FieldError) error).getField();
            String mensaje = error.getDefaultMessage();
            errores.put(campo, mensaje);
        });
        
        RespuestaApi<Void> respuesta = RespuestaApi.error(
                Constantes.CodigosError.VALIDACION_ERROR, 
                Constantes.Mensajes.ERROR_VALIDACION);
        respuesta.setDetalles(errores);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<RespuestaApi<Void>> manejarCredencialesInvalidas(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaApi.error(
                        Constantes.CodigosError.CREDENCIALES_INVALIDAS, 
                        Constantes.Mensajes.CREDENCIALES_INVALIDAS));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaApi<Void>> manejarExcepcionGeneral(Exception ex) {
        // Loguear siempre las excepciones no manejadas para diagnóstico
        logger.error("Excepción no manejada: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        // Registrar en la tabla logs_criticos para visibilidad desde el panel admin
        registrarLogCritico(ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespuestaApi.error(
                        Constantes.CodigosError.ERROR_INTERNO, 
                        Constantes.Mensajes.ERROR_DESCONOCIDO));
    }

    /**
     * Registra automáticamente una excepción no manejada como log crítico en la base de datos.
     * Se ejecuta en un bloque try-catch para no interferir con la respuesta al cliente.
     */
    private void registrarLogCritico(Exception ex) {
        try {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            // Limitar el stack trace a 4000 caracteres para no desbordar la columna TEXT
            if (stackTrace.length() > 4000) {
                stackTrace = stackTrace.substring(0, 4000) + "\n... [truncado]";
            }

            LogCritico log = LogCritico.builder()
                    .nivel("CRITICO")
                    .origen(ex.getClass().getSimpleName())
                    .mensaje(ex.getMessage() != null ? ex.getMessage() : "Sin mensaje")
                    .detalle(stackTrace)
                    .build();

            logCriticoRepositorio.save(log);
            logger.debug("Log crítico guardado en BD para excepción: {}", ex.getClass().getSimpleName());
        } catch (Exception logEx) {
            // No permitir que un fallo al guardar el log afecte la respuesta
            logger.error("Error al guardar log crítico en BD: {}", logEx.getMessage());
        }
    }
}
