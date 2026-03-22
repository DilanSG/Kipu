/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.MetodoPagoDto;
import com.baryx.common.dto.RespuestaApi;
import com.baryx.common.excepcion.ConexionException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio del cliente para consumir la API de métodos de pago.
 * 
 * Este servicio se comunica con el servidor para gestionar los métodos de pago
 * disponibles en el sistema. Los métodos de pago son dinámicos y configurables
 * por el administrador.
 * 
 * Funcionalidades:
 * - Listar métodos de pago activos (todos los roles)
 * - Crear nuevo método de pago (solo ADMIN)
 * - Eliminar método de pago (solo ADMIN, excepto EFECTIVO)
 * 
 * Endpoints consumidos:
 * - GET /api/metodos-pago: Lista todos los métodos de pago activos
 * - POST /api/metodos-pago: Crea un nuevo método de pago
 * - DELETE /api/metodos-pago/{id}: Elimina un método de pago
 * 
 * Todas las llamadas requieren token JWT en el header Authorization.
 * 
 * @see MetodoPagoDto para el modelo de datos
 * @see MesaDetalleController para el consumidor principal
 */
public class MetodoPagoServicio extends ServicioHttpBase {
    
    private static final Logger logger = LoggerFactory.getLogger(MetodoPagoServicio.class);
    
    private final String token;
    
    /**
     * Constructor del servicio.
     * 
     * @param token Token JWT para autenticación
     */
    public MetodoPagoServicio(String token) {
        this.token = token;
    }

    @Override
    protected String obtenerToken() {
        return token;
    }
    
    /**
     * Lista todos los métodos de pago activos de forma asincrónica.
     * Los métodos se devuelven ordenados por campo 'orden' (EFECTIVO primero).
     * 
     * @return CompletableFuture con la lista de métodos de pago
     */
    public CompletableFuture<List<MetodoPagoDto>> listarMetodosPagoAsync() {
        logger.debug("Solicitando lista de métodos de pago activos");
        
        HttpRequest request = construirRequest(Constantes.Endpoints.METODOS_PAGO)
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al obtener métodos de pago. Código: {}", response.statusCode());
                        throw new ConexionException("Error al obtener métodos de pago. Código HTTP: " + response.statusCode());
                    }
                    
                    try {
                        RespuestaApi<List<MetodoPagoDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<MetodoPagoDto>>>() {}
                        );
                        
                        List<MetodoPagoDto> metodos = respuesta.getDatos();
                        logger.info("Métodos de pago obtenidos: {}", metodos != null ? metodos.size() : 0);
                        return metodos;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de métodos de pago", e);
                        throw new ConexionException("Error al parsear respuesta de métodos de pago: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Crea un nuevo método de pago de forma asincrónica.
     * Solo disponible para usuarios con rol ADMIN.
     * 
     * @param dto Datos del nuevo método de pago
     * @return CompletableFuture con el método de pago creado
     */
    public CompletableFuture<MetodoPagoDto> crearMetodoPagoAsync(MetodoPagoDto dto) {
        logger.info("Creando nuevo método de pago: {}", dto.getNombre());
        
        try {
            String jsonBody = objectMapper.writeValueAsString(dto);
            
            HttpRequest request = construirRequest(Constantes.Endpoints.METODOS_PAGO)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 201 || response.statusCode() == 200) {
                                RespuestaApi<MetodoPagoDto> respuesta = objectMapper.readValue(
                                        response.body(),
                                        new TypeReference<RespuestaApi<MetodoPagoDto>>() {}
                                );
                                logger.info("Método de pago creado exitosamente: {}", respuesta.getDatos().getNombre());
                                return respuesta.getDatos();
                            } else {
                                logger.error("Error al crear método de pago. Código: {}, Body: {}", 
                                        response.statusCode(), response.body());
                                throw new ConexionException("Error al crear método de pago. Código HTTP: " + response.statusCode());
                            }
                        } catch (ConexionException ce) {
                            throw ce;
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de creación de método de pago", e);
                            throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.error("Error al serializar DTO de método de pago", e);
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud: " + e.getMessage()));
        }
    }
    
    /**
     * Actualiza un método de pago existente de forma asincrónica.
     * Solo disponible para usuarios con rol ADMIN.
     * No permite actualizar el método predeterminado (EFECTIVO).
     * El nombre se normaliza a mayúsculas en el servidor.
     * 
     * @param dto Datos actualizados del método de pago (debe incluir idMetodoPago y nombre)
     * @return CompletableFuture con el método de pago actualizado
     */
    public CompletableFuture<MetodoPagoDto> actualizarMetodoPagoAsync(MetodoPagoDto dto) {
        logger.info("Actualizando método de pago ID: {} con nombre: {}", dto.getIdMetodoPago(), dto.getNombre());
        
        try {
            String jsonBody = objectMapper.writeValueAsString(dto);
            
            HttpRequest request = construirRequest(Constantes.Endpoints.METODOS_PAGO + "/" + dto.getIdMetodoPago())
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 200) {
                                RespuestaApi<MetodoPagoDto> respuesta = objectMapper.readValue(
                                        response.body(),
                                        new TypeReference<RespuestaApi<MetodoPagoDto>>() {}
                                );
                                logger.info("Método de pago actualizado exitosamente: {}", respuesta.getDatos().getNombre());
                                return respuesta.getDatos();
                            } else {
                                logger.error("Error al actualizar método de pago. Código: {}, Body: {}", 
                                        response.statusCode(), response.body());
                                throw new ConexionException("Error al actualizar método de pago. Código HTTP: " + response.statusCode());
                            }
                        } catch (ConexionException ce) {
                            throw ce;
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de actualización de método de pago", e);
                            throw new ConexionException("Error al procesar respuesta: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.error("Error al serializar DTO de método de pago para actualización", e);
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud: " + e.getMessage()));
        }
    }
    
    /**
     * Elimina un método de pago de forma asincrónica.
     * Solo disponible para usuarios con rol ADMIN.
     * No permite eliminar el método predeterminado (EFECTIVO).
     * 
     * @param idMetodoPago ID del método de pago a eliminar
     * @return CompletableFuture<Void> que se completa cuando se elimina
     */
    public CompletableFuture<Void> eliminarMetodoPagoAsync(Long idMetodoPago) {
        logger.info("Eliminando método de pago con ID: {}", idMetodoPago);
        
        HttpRequest request = construirRequest(Constantes.Endpoints.METODOS_PAGO + "/" + idMetodoPago)
                .DELETE()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al eliminar método de pago. Código: {}, Body: {}", 
                                response.statusCode(), response.body());
                        throw new ConexionException("Error al eliminar método de pago. Código HTTP: " + response.statusCode());
                    }
                    logger.info("Método de pago eliminado exitosamente");
                    return null;
                });
    }
}
