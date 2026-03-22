/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.*;
import com.baryx.common.excepcion.ConexionException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio cliente para gestionar mesas y pedidos de forma asíncrona.
 * 
 * Todas las operaciones de red usan java.net.http.HttpClient con 
 * CompletableFuture para no bloquear el hilo de JavaFX.
 * 
 * La URL del servidor se obtiene de ConfiguracionCliente.getUrlServidor()
 * para permitir configuración dinámica en redes LAN.
 * 
 * Endpoints consumidos:
 * - GET /api/mesas/{id}/pedido: Obtiene mesa con su pedido
 * - POST /api/mesas/{numMesa}/pedido: Guarda pedido en una mesa
 * - GET /api/mesas/activas: Lista mesas activas
 * - POST /api/mesas: Crea o recupera una mesa
 * - GET /api/mesas/numero/{num}: Busca mesa por número
 * - DELETE /api/mesas/{id}: Elimina una mesa
 * 
 * @see MesaConPedidoDto para detalle de mesa con pedido
 * @see MesaActivaDto para listado de mesas activas
 */
public class MesaServicio extends ServicioHttpBase {

    private static final Logger logger = LoggerFactory.getLogger(MesaServicio.class);

    private final String token;

    /**
     * Constructor del servicio.
     * 
     * @param token Token JWT para autenticación
     */
    public MesaServicio(String token) {
        this.token = token;
    }

    @Override
    protected String obtenerToken() {
        return token;
    }

    /**
     * Obtiene una mesa con su pedido completo de forma asíncrona.
     * 
     * @param idMesa ID de la mesa
     * @return CompletableFuture con MesaConPedidoDto
     */
    public CompletableFuture<MesaConPedidoDto> obtenerMesaConPedidoAsync(Long idMesa) {
        HttpRequest request = construirRequest(
                Constantes.Endpoints.MESAS + "/" + idMesa + "/pedido")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ConexionException("Error del servidor: " + response.statusCode());
                    }
                    try {
                        RespuestaApi<MesaConPedidoDto> apiResponse = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<MesaConPedidoDto>>() {});
                        return apiResponse.getDatos();
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de mesa con pedido", e);
                        throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                    }
                });
    }

    /**
     * Guarda el pedido de una mesa usando número de mesa e ID de mesero, de forma asíncrona.
     * 
     * @param numeroMesa número de la mesa
     * @param idMesero ID del mesero asignado (puede ser null)
     * @param pedidoDto datos del pedido
     * @return CompletableFuture con MesaConPedidoDto actualizada
     */
    public CompletableFuture<MesaConPedidoDto> guardarPedidoAsync(String numeroMesa, Long idMesero, PedidoDto pedidoDto) {
        try {
            String jsonBody = objectMapper.writeValueAsString(pedidoDto);
            
            // Construir URL con query param opcional de idMesero
            String path = Constantes.Endpoints.MESAS + "/" + numeroMesa + "/pedido";
            if (idMesero != null) {
                path += "?idMesero=" + idMesero;
            }

            HttpRequest request = construirRequest(path)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new ConexionException("Error del servidor: " + response.statusCode());
                        }
                        try {
                            RespuestaApi<MesaConPedidoDto> apiResponse = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<MesaConPedidoDto>>() {});
                            return apiResponse.getDatos();
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de guardar pedido", e);
                            throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud de guardar pedido", e));
        }
    }

    /**
     * Obtiene mesas activas de forma asíncrona, opcionalmente filtradas por mesero.
     * 
     * @param idMesero ID del mesero para filtrar (null para todas)
     * @return CompletableFuture con lista de mesas activas
     */
    public CompletableFuture<List<MesaActivaDto>> obtenerMesasActivasAsync(Long idMesero) {
        String path = Constantes.Endpoints.MESAS + "/activas";
        if (idMesero != null) {
            path += "?idMesero=" + idMesero;
        }

        HttpRequest request = construirRequest(path)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ConexionException("Error del servidor: " + response.statusCode());
                    }
                    try {
                        RespuestaApi<List<MesaActivaDto>> apiResponse = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<MesaActivaDto>>>() {});
                        return apiResponse.getDatos();
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de mesas activas", e);
                        throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                    }
                });
    }

    /**
     * Crea o recupera una mesa por número de forma asíncrona.
     * 
     * @param numeroMesa número de la mesa
     * @param idMesero ID del mesero asignado
     * @return CompletableFuture con MesaConPedidoDto
     */
    public CompletableFuture<MesaConPedidoDto> crearOObtenerMesaAsync(String numeroMesa, Long idMesero) {
        try {
            CreacionMesaDto dto = new CreacionMesaDto(numeroMesa, idMesero);
            String jsonBody = objectMapper.writeValueAsString(dto);

            HttpRequest request = construirRequest(Constantes.Endpoints.MESAS)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new ConexionException("Error del servidor: " + response.statusCode());
                        }
                        try {
                            RespuestaApi<MesaConPedidoDto> apiResponse = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<MesaConPedidoDto>>() {});
                            return apiResponse.getDatos();
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de crear mesa", e);
                            throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud de crear mesa", e));
        }
    }

    /**
     * Busca una mesa por número de forma asíncrona.
     * Retorna null si la mesa no existe (404).
     * 
     * @param numeroMesa número de la mesa
     * @return CompletableFuture con MesaConPedidoDto o null si no existe
     */
    public CompletableFuture<MesaConPedidoDto> buscarMesaPorNumeroAsync(String numeroMesa) {
        HttpRequest request = construirRequest(
                Constantes.Endpoints.MESAS + "/numero/" + numeroMesa)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            RespuestaApi<MesaConPedidoDto> apiResponse = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<MesaConPedidoDto>>() {});
                            return apiResponse.getDatos();
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de buscar mesa", e);
                            throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                        }
                    } else if (response.statusCode() == 404) {
                        return null;
                    } else {
                        throw new ConexionException("Error del servidor: " + response.statusCode());
                    }
                });
    }

    /**
     * Elimina una mesa completamente de la BD (hard delete) de forma asíncrona.
     * 
     * @param idMesa ID de la mesa a eliminar
     * @return CompletableFuture<Void> que se completa cuando se elimina
     */
    public CompletableFuture<Void> anularMesaAsync(Long idMesa) {
        HttpRequest request = construirRequest(
                Constantes.Endpoints.MESAS + "/" + idMesa)
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 204) {
                        throw new ConexionException("Error del servidor al anular mesa: " + response.statusCode());
                    }
                    logger.info("Mesa {} anulada exitosamente", idMesa);
                    return null;
                });
    }
}
