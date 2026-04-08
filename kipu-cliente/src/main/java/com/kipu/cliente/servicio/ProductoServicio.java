/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.servicio;

import com.kipu.common.dto.ProductoDto;
import com.kipu.common.constantes.Constantes;
import com.kipu.common.excepcion.ConexionException;
import com.kipu.common.dto.RespuestaApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio del cliente para consumir la API de productos.
 * 
 * Este servicio se comunica con el servidor para obtener productos
 * filtrados por categoría o todos los productos activos.
 * 
 * OPTIMIZADO: Usa Constantes.Endpoints para rutas y Constantes.Http para configuración.
 * 
 * Endpoints consumidos:
 * - GET /api/productos: Lista todos los productos activos
 * - GET /api/productos/categoria/{idCategoria}: Productos de una categoría
 * 
 * Métodos asincrónicos:
 * - listarPorCategoriaAsync(idCategoria): Obtiene productos de una categoría
 * - listarTodosAsync(): Obtiene todos los productos activos
 * 
 * Todas las llamadas requieren token JWT en el header Authorization.
 * 
 * @see ProductoDto para el modelo de datos
 * @see MesaDetalleController para el consumidor principal
 * @see Constantes.Endpoints para rutas API
 */
public class ProductoServicio extends ServicioHttpBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductoServicio.class);
    
    private final String token;
    
    /**
     * Constructor del servicio.
     * 
     * @param token Token JWT para autenticación
     */
    public ProductoServicio(String token) {
        this.token = token;
    }

    @Override
    protected String obtenerToken() {
        return token;
    }
    
    /**
     * Lista productos de una categoría específica de forma asincrónica.
     * 
     * @param idCategoria ID de la categoría
     * @return CompletableFuture con la lista de productos
     */
    public CompletableFuture<List<ProductoDto>> listarPorCategoriaAsync(Long idCategoria) {
        logger.debug("Solicitando productos de categoría: {}", idCategoria);
        
        // URL dinámica desde ConfiguracionCliente para soporte LAN
        HttpRequest request = construirRequest(Constantes.Endpoints.PRODUCTOS + "/categoria/" + idCategoria)
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al obtener productos. Código: {}", response.statusCode());
                        throw new RuntimeException("Error al obtener productos: " + response.statusCode());
                    }
                    
                    try {
                        RespuestaApi<List<ProductoDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<ProductoDto>>>() {}
                        );
                        
                        if (respuesta.getExito()) {
                            logger.info("Productos obtenidos: {} productos", respuesta.getDatos().size());
                            return respuesta.getDatos();
                        } else {
                            logger.error("Error en respuesta: {}", respuesta.getMensaje());
                            throw new ConexionException(respuesta.getMensaje());
                        }
                        
                    } catch (ConexionException e) {
                        throw e; // Re-lanzar excepción de dominio
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de productos", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
                });
    }
    
    /**
     * Lista todos los productos activos de forma asincrónica.
     * 
     * @return CompletableFuture con la lista de productos
     */
    public CompletableFuture<List<ProductoDto>> listarTodosAsync() {
        logger.debug("Solicitando todos los productos activos");
        
        // URL dinámica desde ConfiguracionCliente para soporte LAN
        HttpRequest request = construirRequest(Constantes.Endpoints.PRODUCTOS)
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al obtener productos. Código: {}", response.statusCode());
                        throw new ConexionException("Error al obtener productos. Código HTTP: " + response.statusCode());
                    }
                    
                    try {
                        RespuestaApi<List<ProductoDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<ProductoDto>>>() {}
                        );
                        
                        if (respuesta.getExito()) {
                            logger.info("Todos los productos obtenidos: {} productos", respuesta.getDatos().size());
                            return respuesta.getDatos();
                        } else {
                            logger.error("Error en respuesta: {}", respuesta.getMensaje());
                            throw new ConexionException(respuesta.getMensaje());
                        }
                        
                    } catch (ConexionException e) {
                        throw e; // Re-lanzar excepción de dominio
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de productos", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
                });
    }
    
    /**
     * Crea un nuevo producto de forma asincrónica.
     * 
     * @param producto Producto a crear
     * @param onSuccess Callback de éxito con el producto creado
     * @param onError Callback de error
     */
    public void crearAsync(ProductoDto producto, 
                          java.util.function.Consumer<ProductoDto> onSuccess,
                          java.util.function.Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Creando nuevo producto: {}", producto.getNombre());
                
                // Convertir ProductoDto a JSON
                String requestBody = objectMapper.writeValueAsString(producto);
                
                // Construir request POST
                HttpRequest request = construirRequest(Constantes.Endpoints.PRODUCTOS)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                
                // Ejecutar request
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                // Verificar código de respuesta
                if (response.statusCode() != 201 && response.statusCode() != 200) {
                    logger.error("Error al crear producto. Código: {}", response.statusCode());
                    throw new ConexionException("Error al crear producto. Código HTTP: " + response.statusCode());
                }
                
                // Parsear respuesta
                RespuestaApi<ProductoDto> respuesta = objectMapper.readValue(
                    response.body(), 
                    new TypeReference<RespuestaApi<ProductoDto>>() {}
                );
                
                logger.info("Producto creado exitosamente: {}", respuesta.getDatos().getNombre());
                return respuesta.getDatos();
                
            } catch (Exception e) {
                logger.error("Error al crear producto", e);
                throw new ConexionException("Error al crear producto: " + e.getMessage());
            }
        }).thenAccept(onSuccess)
          .exceptionally(error -> {
              onError.accept(error.getCause());
              return null;
          });
    }
    
    /**
     * Actualiza un producto existente de forma asincrónica.
     * 
     * @param producto Producto con datos actualizados
     * @param onSuccess Callback de éxito
     * @param onError Callback de error
     */
    public void actualizarProductoAsync(ProductoDto producto, 
                                       java.util.function.Consumer<ProductoDto> onSuccess,
                                       java.util.function.Consumer<Throwable> onError) {
        logger.debug("Actualizando producto: {}", producto.getIdProducto());
        
        try {
            String requestBody = objectMapper.writeValueAsString(producto);
            
            HttpRequest request = construirRequest(Constantes.Endpoints.PRODUCTOS + "/" + producto.getIdProducto())
                    .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new ConexionException("Error al actualizar producto. Código: " + response.statusCode());
                        }
                        
                        try {
                            RespuestaApi<ProductoDto> respuesta = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<ProductoDto>>() {}
                            );
                            
                            if (respuesta.getExito()) {
                                logger.info("Producto actualizado: {}", producto.getNombre());
                                return respuesta.getDatos();
                            } else {
                                throw new ConexionException(respuesta.getMensaje());
                            }
                        } catch (Exception e) {
                            throw new ConexionException("Error processing response", e);
                        }
                    })
                    .thenAccept(onSuccess)
                    .exceptionally(ex -> {
                        onError.accept(ex);
                        return null;
                    });
                    
        } catch (Exception e) {
            onError.accept(e);
        }
    }
    
    /**
     * Elimina un producto de forma asincrónica.
     * 
     * @param idProducto ID del producto a eliminar
     * @param onSuccess Callback de éxito
     * @param onError Callback de error
     */
    public void eliminarProductoAsync(Long idProducto,
                                     Runnable onSuccess,
                                     java.util.function.Consumer<Throwable> onError) {
        logger.debug("Eliminando producto: {}", idProducto);
        
        HttpRequest request = construirRequest(Constantes.Endpoints.PRODUCTOS + "/" + idProducto)
                .DELETE()
                .build();
        
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 204) {
                        throw new ConexionException("Error al eliminar producto. Código: " + response.statusCode());
                    }
                    logger.info("Producto eliminado exitosamente: {}", idProducto);
                    return null;
                })
                .thenRun(onSuccess)
                .exceptionally(ex -> {
                    onError.accept(ex);
                    return null;
                });
    }
}
