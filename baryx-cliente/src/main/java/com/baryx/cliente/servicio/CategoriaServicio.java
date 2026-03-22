/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.servicio;

import com.baryx.common.dto.CategoriaDto;
import com.baryx.common.constantes.Constantes;
import com.baryx.common.excepcion.ConexionException;
import com.baryx.common.dto.RespuestaApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio del cliente para consumir la API de categorías.
 * 
 * Este servicio se comunica con el servidor para obtener las categorías
 * de productos disponibles. Las categorías se usan para organizar
 * el menú de productos en la vista de detalle de mesa.
 * 
 * OPTIMIZADO: Usa Constantes.Endpoints para rutas y Constantes.Http para configuración.
 * 
 * Endpoints consumidos:
 * - GET /api/categorias: Lista todas las categorías activas
 * 
 * Métodos asincrónicos:
 * - listarCategoriasAsync(): Obtiene lista de categorías sin bloquear el hilo UI
 * 
 * Todas las llamadas requieren token JWT en el header Authorization.
 * 
 * @see CategoriaDto para el modelo de datos
 * @see MesaDetalleController para el consumidor principal
 * @see Constantes.Endpoints para rutas API
 */
public class CategoriaServicio extends ServicioHttpBase {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoriaServicio.class);
    
    private final String token;
    
    /**
     * Constructor del servicio.
     * 
     * @param token Token JWT para autenticación
     */
    public CategoriaServicio(String token) {
        this.token = token;
    }

    @Override
    protected String obtenerToken() {
        return token;
    }
    
    /**
     * Lista todas las categorías activas de forma asincrónica.
     * 
     * Este método no bloquea el hilo de JavaFX, permitiendo que la UI
     * siga respondiendo mientras se cargan los datos.
     * 
     * Uso típico:
     * <pre>
     * categoriaServicio.listarCategoriasAsync()
     *     .thenAccept(categorias -> {
     *         Platform.runLater(() -> {
     *             // Actualizar UI con las categorías
     *         });
     *     })
     *     .exceptionally(error -> {
     *         // Manejar error
     *         return null;
     *     });
     * </pre>
     * 
     * @return CompletableFuture con la lista de categorías
     */
    public CompletableFuture<List<CategoriaDto>> listarCategoriasAsync() {
        logger.debug("Solicitando lista de categorías activas");
        
        // URL dinámica desde ConfiguracionCliente para soporte LAN
        HttpRequest request = construirRequest(Constantes.Endpoints.CATEGORIAS)
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al obtener categorías. Código: {}", response.statusCode());
                        throw new ConexionException("Error al obtener categorías. Código HTTP: " + response.statusCode());
                    }
                    
                    try {
                        // Parsear respuesta: RespuestaApi<List<CategoriaDto>>
                        RespuestaApi<List<CategoriaDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<CategoriaDto>>>() {}
                        );
                        
                        if (respuesta.getExito()) {
                            logger.info("Categorías obtenidas exitosamente: {} categorías", 
                                    respuesta.getDatos().size());
                            return respuesta.getDatos();
                        } else {
                            logger.error("Error en respuesta del servidor: {}", respuesta.getMensaje());
                            throw new ConexionException(respuesta.getMensaje());
                        }
                        
                    } catch (ConexionException e) {
                        throw e; // Re-lanzar excepción de dominio
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de categorías", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
        });
    }
    /**
    * Crea una nueva categoría de forma asincrónica.
    * 
    * @param categoriaDto Datos de la nueva categoría
    * @return CompletableFuture con la categoría creada
    */
    public CompletableFuture<CategoriaDto> crearCategoriaAsync(CategoriaDto categoriaDto) {
        logger.debug("Creando nueva categoría: {}", categoriaDto.getNombre());
        
        try {
            String requestBody = objectMapper.writeValueAsString(categoriaDto);
            
            HttpRequest request = construirRequest(Constantes.Endpoints.CATEGORIAS)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::procesarRespuestaCategoria);
            
        } catch (Exception e) {
            CompletableFuture<CategoriaDto> failed = new CompletableFuture<>();
            failed.completeExceptionally(new ConexionException("Error al preparar petición de creación", e));
            return failed;
        }
    }
    
    /**
     * Actualiza una categoría existente de forma asincrónica.
     * 
     * @param categoriaDto Datos actualizados de la categoría (debe incluir ID)
     * @return CompletableFuture con la categoría actualizada
     */
    public CompletableFuture<CategoriaDto> actualizarCategoriaAsync(CategoriaDto categoriaDto) {
        logger.debug("Actualizando categoría ID: {}", categoriaDto.getIdCategoria());
        
        if (categoriaDto.getIdCategoria() == null) {
            CompletableFuture<CategoriaDto> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("El ID de categoría es necesario para actualizar"));
            return failed;
        }
        
        try {
            String requestBody = objectMapper.writeValueAsString(categoriaDto);
            
            HttpRequest request = construirRequest(Constantes.Endpoints.CATEGORIAS + "/" + categoriaDto.getIdCategoria())
                    .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::procesarRespuestaCategoria);
            
        } catch (Exception e) {
            CompletableFuture<CategoriaDto> failed = new CompletableFuture<>();
            failed.completeExceptionally(new ConexionException("Error al preparar petición de actualización", e));
            return failed;
        }
    }
    
    /**
     * Procesa la respuesta HTTP para una operación de categoría individual.
     */
    /**
     * Elimina una categoría de forma asíncrona.
     * Envía una solicitud DELETE a /api/categorias/{id}
     * 
     * @param idCategoria ID de la categoría a eliminar
     * @return CompletableFuture con la respuesta (True si fue exitoso)
     */
    public CompletableFuture<Boolean> eliminarCategoriaAsync(Long idCategoria) {
        HttpRequest request = construirRequest(Constantes.Endpoints.CATEGORIAS + "/" + idCategoria)
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 204) {
                        return true;
                    } else {
                        logger.error("Error al eliminar categoría: Status {}", response.statusCode());
                        throw new RuntimeException("Error al eliminar categoría (Status " + response.statusCode() + ")");
                    }
                });
    }

    private CategoriaDto procesarRespuestaCategoria(HttpResponse<String> response) {
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            logger.error("Error en operación de categoría. Código: {}", response.statusCode());
            throw new RuntimeException("Error en operación de categoría. Código HTTP: " + response.statusCode());
        }
        
        try {
            RespuestaApi<CategoriaDto> respuesta = objectMapper.readValue(
                    response.body(),
                    new TypeReference<RespuestaApi<CategoriaDto>>() {}
            );
            
            if (respuesta.getExito()) {
                return respuesta.getDatos();
            } else {
                throw new RuntimeException(respuesta.getMensaje());
            }
            
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            logger.error("Error al parsear respuesta de categoría", e);
            throw new RuntimeException("Error al procesar respuesta del servidor", e);
        }
    }
}
