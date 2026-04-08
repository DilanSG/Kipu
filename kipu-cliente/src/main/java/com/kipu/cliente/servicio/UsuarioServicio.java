/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.servicio;

import com.kipu.common.dto.UsuarioDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.common.constantes.Constantes;
import com.kipu.common.excepcion.ConexionException;
import com.kipu.common.excepcion.ValidacionException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para gestionar usuarios del sistema de forma asíncrona.
 * 
 * Maneja todas las operaciones CRUD de usuarios con el backend
 * usando java.net.http.HttpClient con CompletableFuture para
 * operaciones no bloqueantes.
 * 
 * La URL del servidor se obtiene de ConfiguracionCliente.getUrlServidor()
 * para permitir configuración dinámica en redes LAN.
 * 
 * Endpoints consumidos:
 * - GET /api/usuarios: Lista todos los usuarios
 * - POST /api/usuarios: Crea un nuevo usuario
 * - PUT /api/usuarios/{id}: Actualiza un usuario
 * - DELETE /api/usuarios/{id}: Elimina un usuario
 * - GET /api/usuarios/buscar?termino=X: Busca usuarios
 * 
 * @see UsuarioDto para el modelo de datos
 * @see UsuariosController para el consumidor principal
 * @see Constantes.Endpoints para rutas API
 */
public class UsuarioServicio extends ServicioHttpBase {
    
    private static final Logger logger = LoggerFactory.getLogger(UsuarioServicio.class);
    
    /**
     * Obtiene la lista de todos los usuarios del sistema de forma asíncrona.
     * 
     * @return CompletableFuture con la lista de usuarios
     */
    public CompletableFuture<List<UsuarioDto>> listarUsuariosAsync() {
        logger.info("Obteniendo lista de usuarios");
        
        HttpRequest request = construirRequest(Constantes.Endpoints.USUARIOS)
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ConexionException("Error al obtener usuarios. Código HTTP: " + response.statusCode());
                    }
                    
                    try {
                        RespuestaApi<List<UsuarioDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<UsuarioDto>>>() {});
                        
                        if (respuesta.getExito() && respuesta.getDatos() != null) {
                            logger.info("Se obtuvieron {} usuarios", respuesta.getDatos().size());
                            return respuesta.getDatos();
                        } else {
                            throw new ConexionException("Respuesta inválida del servidor");
                        }
                    } catch (ConexionException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de usuarios", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
                });
    }
    
    /**
     * Crea un nuevo usuario en el sistema de forma asíncrona.
     * 
     * @param usuario datos del usuario a crear
     * @return CompletableFuture con el usuario creado (incluye ID asignado)
     */
    public CompletableFuture<UsuarioDto> crearUsuarioAsync(UsuarioDto usuario) {
        logger.info("Creando nuevo usuario: {}", usuario.getNombreCompleto());
        
        try {
            String requestBody = objectMapper.writeValueAsString(usuario);
            
            HttpRequest request = construirRequest(Constantes.Endpoints.USUARIOS)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> procesarRespuestaUsuario(response, "crear"));
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud de creación de usuario", e));
        }
    }
    
    /**
     * Actualiza un usuario existente de forma asíncrona.
     * 
     * @param usuario datos actualizados del usuario
     * @return CompletableFuture con el usuario actualizado
     */
    public CompletableFuture<UsuarioDto> actualizarUsuarioAsync(UsuarioDto usuario) {
        logger.info("Actualizando usuario ID: {}", usuario.getIdUsuario());
        
        try {
            String requestBody = objectMapper.writeValueAsString(usuario);
            
            HttpRequest request = construirRequest(
                    Constantes.Endpoints.USUARIOS + "/" + usuario.getIdUsuario())
                    .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> procesarRespuestaUsuario(response, "actualizar"));
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud de actualización de usuario", e));
        }
    }
    
    /**
     * Elimina un usuario del sistema de forma asíncrona.
     * 
     * @param idUsuario ID del usuario a eliminar
     * @return CompletableFuture<Void> que se completa cuando se elimina
     */
    public CompletableFuture<Void> eliminarUsuarioAsync(Long idUsuario) {
        logger.info("Eliminando usuario ID: {}", idUsuario);
        
        HttpRequest request = construirRequest(
                Constantes.Endpoints.USUARIOS + "/" + idUsuario)
                .DELETE()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 204) {
                        throw new ConexionException("Error al eliminar usuario. Código HTTP: " + response.statusCode());
                    }
                    logger.info("Usuario eliminado exitosamente");
                    return null;
                });
    }
    
    /**
     * Busca usuarios por nombre, código o usuario de forma asíncrona.
     * 
     * @param termino término de búsqueda
     * @return CompletableFuture con la lista de usuarios que coinciden
     */
    public CompletableFuture<List<UsuarioDto>> buscarUsuariosAsync(String termino) {
        logger.info("Buscando usuarios con término: {}", termino);
        
        HttpRequest request = construirRequest(
                Constantes.Endpoints.USUARIOS_BUSCAR + "?termino=" + URLEncoder.encode(termino, StandardCharsets.UTF_8))
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ConexionException("Error al buscar usuarios. Código HTTP: " + response.statusCode());
                    }
                    
                    try {
                        RespuestaApi<List<UsuarioDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<UsuarioDto>>>() {});
                        
                        if (respuesta.getExito() && respuesta.getDatos() != null) {
                            logger.info("Se encontraron {} usuarios", respuesta.getDatos().size());
                            return respuesta.getDatos();
                        } else {
                            throw new ConexionException("Respuesta inválida del servidor");
                        }
                    } catch (ConexionException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de búsqueda de usuarios", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
                });
    }
    
    /**
     * Lista usuarios activos filtrados por rol de forma asíncrona.
     * Útil para obtener la lista de meseros, cajeros, etc.
     * 
     * Endpoint consumido: GET /api/usuarios/por-rol/{rol}
     * 
     * @param rol nombre del rol (MESERO, CAJERO, ADMIN)
     * @return CompletableFuture con la lista de usuarios con ese rol
     */
    public CompletableFuture<List<UsuarioDto>> listarPorRolAsync(String rol) {
        logger.info("Obteniendo usuarios con rol: {}", rol);
        
        HttpRequest request = construirRequest(
                Constantes.Endpoints.USUARIOS_POR_ROL + "/" + rol)
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ConexionException("Error al obtener usuarios por rol. Código HTTP: " + response.statusCode());
                    }
                    
                    try {
                        RespuestaApi<List<UsuarioDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<UsuarioDto>>>() {});
                        
                        if (respuesta.getExito() && respuesta.getDatos() != null) {
                            logger.info("Se obtuvieron {} usuarios con rol {}", respuesta.getDatos().size(), rol);
                            return respuesta.getDatos();
                        } else {
                            throw new ConexionException("Respuesta inválida del servidor");
                        }
                    } catch (ConexionException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de usuarios por rol", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
                });
    }
    
    /**
     * Procesa la respuesta HTTP para operaciones de usuario individual (crear/actualizar).
     * Maneja códigos de error diferenciando entre errores de validación (400) y otros.
     * 
     * @param response respuesta HTTP del servidor
     * @param operacion nombre de la operación para mensajes de log
     * @return UsuarioDto con los datos del usuario
     */
    private UsuarioDto procesarRespuestaUsuario(HttpResponse<String> response, String operacion) {
        try {
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                // Intentar extraer mensaje de error del servidor
                try {
                    RespuestaApi<?> errorRespuesta = objectMapper.readValue(
                            response.body(), new TypeReference<RespuestaApi<?>>() {});
                    String mensaje = errorRespuesta.getMensaje() != null ? 
                            errorRespuesta.getMensaje() : "Error al " + operacion + " usuario";
                    
                    // Códigos 400 son errores de validación
                    if (response.statusCode() == 400) {
                        throw new ValidacionException(mensaje);
                    }
                    throw new ConexionException(mensaje);
                } catch (ValidacionException | ConexionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ConexionException("Error al " + operacion + " usuario. Código HTTP: " + response.statusCode());
                }
            }
            
            RespuestaApi<UsuarioDto> respuesta = objectMapper.readValue(
                    response.body(),
                    new TypeReference<RespuestaApi<UsuarioDto>>() {});
            
            if (respuesta.getExito() && respuesta.getDatos() != null) {
                logger.info("Usuario {} exitosamente: ID {}", operacion, respuesta.getDatos().getIdUsuario());
                return respuesta.getDatos();
            } else {
                throw new ConexionException("Respuesta inválida del servidor");
            }
        } catch (ValidacionException | ConexionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al parsear respuesta de {} usuario", operacion, e);
            throw new ConexionException("Error al procesar respuesta del servidor", e);
        }
    }
}
