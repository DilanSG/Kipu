/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.servicio;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.LogCriticoDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.common.excepcion.ConexionException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio del cliente para consumir la API de logs críticos.
 *
 * Funcionalidades:
 * - Listar logs críticos (ADMIN)
 * - Listar logs pendientes (ADMIN)
 * - Registrar un log crítico desde el cliente (cualquier rol)
 * - Marcar un log como resuelto (ADMIN)
 *
 * Endpoints consumidos:
 * - GET  /api/logs-criticos           → Lista todos los logs
 * - GET  /api/logs-criticos/pendientes → Lista logs no resueltos
 * - POST /api/logs-criticos            → Registra un nuevo log
 * - PUT  /api/logs-criticos/{id}/resolver → Marca como resuelto
 *
 * @see LogCriticoDto
 */
public class LogCriticoServicio extends ServicioHttpBase {

    private static final Logger logger = LoggerFactory.getLogger(LogCriticoServicio.class);

    private final String token;

    /**
     * Constructor del servicio.
     *
     * @param token Token JWT para autenticación
     */
    public LogCriticoServicio(String token) {
        this.token = token;
    }

    @Override
    protected String obtenerToken() {
        return token;
    }

    /**
     * Lista todos los logs críticos de forma asincrónica.
     *
     * @return CompletableFuture con la lista de logs
     */
    public CompletableFuture<List<LogCriticoDto>> listarTodosAsync() {
        logger.debug("Solicitando lista de logs críticos");

        HttpRequest request = construirRequest(Constantes.Endpoints.LOGS_CRITICOS).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al obtener logs críticos. Código: {}", response.statusCode());
                        throw new ConexionException("Error al obtener logs. Código HTTP: " + response.statusCode());
                    }
                    try {
                        RespuestaApi<List<LogCriticoDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<LogCriticoDto>>>() {}
                        );
                        List<LogCriticoDto> logs = respuesta.getDatos();
                        logger.info("Logs críticos obtenidos: {}", logs != null ? logs.size() : 0);
                        return logs;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de logs críticos", e);
                        throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                    }
                });
    }

    /**
     * Lista logs pendientes (no resueltos) de forma asincrónica.
     *
     * @return CompletableFuture con la lista de logs pendientes
     */
    public CompletableFuture<List<LogCriticoDto>> listarPendientesAsync() {
        logger.debug("Solicitando logs críticos pendientes");

        HttpRequest request = construirRequest(Constantes.Endpoints.LOGS_CRITICOS_PENDIENTES).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al obtener logs pendientes. Código: {}", response.statusCode());
                        throw new ConexionException("Error al obtener logs pendientes. Código HTTP: " + response.statusCode());
                    }
                    try {
                        RespuestaApi<List<LogCriticoDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<LogCriticoDto>>>() {}
                        );
                        return respuesta.getDatos();
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de logs pendientes", e);
                        throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                    }
                });
    }

    /**
     * Registra un log crítico en el servidor de forma asincrónica.
     * Este método es invocado automáticamente cuando se captura un error crítico
     * en el cliente (crash, error de renderizado, etc.).
     *
     * @param dto Datos del log a registrar
     * @return CompletableFuture con el log registrado
     */
    public CompletableFuture<LogCriticoDto> registrarAsync(LogCriticoDto dto) {
        logger.info("Registrando log crítico: {} - {}", dto.getNivel(), dto.getOrigen());

        String url = Constantes.Endpoints.LOGS_CRITICOS;

        try {
            String jsonBody = objectMapper.writeValueAsString(dto);

            HttpRequest request = construirRequest(url)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 201 || response.statusCode() == 200) {
                                RespuestaApi<LogCriticoDto> respuesta = objectMapper.readValue(
                                        response.body(),
                                        new TypeReference<RespuestaApi<LogCriticoDto>>() {}
                                );
                                logger.info("Log crítico registrado exitosamente con ID: {}",
                                        respuesta.getDatos().getIdLog());
                                return respuesta.getDatos();
                            } else {
                                logger.error("Error al registrar log. Código: {}", response.statusCode());
                                throw new ConexionException("Error al registrar log. Código HTTP: " + response.statusCode());
                            }
                        } catch (ConexionException ce) {
                            throw ce;
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de registro de log", e);
                            throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.error("Error al serializar log crítico", e);
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud: " + e.getMessage()));
        }
    }

    /**
     * Marca un log como resuelto de forma asincrónica.
     *
     * @param idLog ID del log a resolver
     * @return CompletableFuture con el log actualizado
     */
    public CompletableFuture<LogCriticoDto> marcarResueltoAsync(Long idLog) {
        return cambiarEstadoAsync(idLog, "RESUELTO");
    }

    /**
     * Cambia el estado de un log de forma asincrónica.
     *
     * @param idLog  ID del log
     * @param estado Nuevo estado: NOTIFICACION_ERROR, EN_REVISION, RESUELTO
     * @return CompletableFuture con el log actualizado
     */
    public CompletableFuture<LogCriticoDto> cambiarEstadoAsync(Long idLog, String estado) {
        logger.info("Cambiando estado del log {} a {}", idLog, estado);

        String endpoint = Constantes.Endpoints.LOGS_CRITICOS
                + "/" + idLog + "/estado?estado=" + estado;

        HttpRequest request = construirRequest(endpoint)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            RespuestaApi<LogCriticoDto> respuesta = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<LogCriticoDto>>() {}
                            );
                            logger.info("Log {} cambiado a estado {}", idLog, estado);
                            return respuesta.getDatos();
                        } else {
                            logger.error("Error al cambiar estado del log. Código: {}", response.statusCode());
                            throw new ConexionException("Error al cambiar estado. Código HTTP: " + response.statusCode());
                        }
                    } catch (ConexionException ce) {
                        throw ce;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de cambio de estado", e);
                        throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                    }
                });
    }
}
