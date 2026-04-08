/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.servicio;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LoginDto;
import com.kipu.common.dto.LoginPinDto;
import com.kipu.common.dto.VerificarCodigoDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.common.excepcion.ConexionException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio cliente para autenticación de usuarios.
 * 
 * Responsabilidades:
 * - Autenticar usuarios (ADMIN con usuario/contraseña, CAJERO/MESERO con código/PIN)
 * - Verificar existencia de códigos de empleado
 * - Verificar la salud del servidor
 * - Gestionar la sesión local (token JWT)
 * 
 * Todas las operaciones de red son asíncronas usando java.net.http.HttpClient
 * con CompletableFuture para no bloquear el hilo de JavaFX.
 * 
 * La URL del servidor se obtiene de ConfiguracionCliente.getUrlServidor()
 * para permitir configuración dinámica en redes LAN.
 * 
 * @see ConfiguracionCliente para configuración de conexión
 * @see Constantes.Endpoints para rutas API
 */
public class AutenticacionServicio extends ServicioHttpBase {

    private static final Logger logger = LoggerFactory.getLogger(AutenticacionServicio.class);

    /**
     * Realiza login con código PIN de forma asíncrona (para cajeros y meseros).
     * 
     * Proceso:
     * 1. Serializa LoginPinDto a JSON
     * 2. Envía POST asíncrono a /api/usuarios/login-pin
     * 3. Parsea la respuesta como AuthRespuestaDto
     * 4. Almacena el token JWT en ConfiguracionCliente
     * 
     * @param loginPinDto DTO con código (2 dígitos) y PIN (4 dígitos)
     * @return CompletableFuture con AuthRespuestaDto (token + datos del usuario)
     */
    public CompletableFuture<AuthRespuestaDto> loginConPinAsync(LoginPinDto loginPinDto) {
        logger.info("Intentando login con código PIN: {}", loginPinDto.getCodigo());

        try {
            String requestBody = objectMapper.writeValueAsString(loginPinDto);

            HttpRequest request = construirRequest(Constantes.Endpoints.LOGIN_PIN)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 200) {
                                // El servidor devuelve AuthRespuestaDto directamente
                                AuthRespuestaDto respuesta = objectMapper.readValue(
                                        response.body(), AuthRespuestaDto.class);
                                ConfiguracionCliente.setTokenJwt(respuesta.getToken());
                                logger.info("Login con PIN exitoso para código: {}", loginPinDto.getCodigo());
                                return respuesta;
                            } else {
                                logger.error("Error en login con PIN. Status: {}", response.statusCode());
                                throw new ConexionException("Error en login con PIN: " + response.statusCode());
                            }
                        } catch (ConexionException e) {
                            throw e;
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de login PIN", e);
                            throw new ConexionException("Error al procesar respuesta del servidor", e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud de login PIN", e));
        }
    }

    /**
     * Realiza login tradicional de forma asíncrona con usuario y contraseña (para administradores).
     * 
     * Proceso:
     * 1. Serializa LoginDto a JSON
     * 2. Envía POST asíncrono a /api/usuarios/login
     * 3. Parsea la respuesta como RespuestaApi<AuthRespuestaDto>
     * 4. Almacena el token JWT en ConfiguracionCliente
     * 
     * @param loginDto DTO con nombreUsuario y contrasena
     * @return CompletableFuture con AuthRespuestaDto (token + datos del usuario)
     */
    public CompletableFuture<AuthRespuestaDto> loginAsync(LoginDto loginDto) {
        logger.info("Intentando login para usuario: {}", loginDto.getNombreUsuario());

        try {
            String requestBody = objectMapper.writeValueAsString(loginDto);

            HttpRequest request = construirRequest(Constantes.Endpoints.LOGIN)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            RespuestaApi<AuthRespuestaDto> respuesta = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<AuthRespuestaDto>>() {});

                            if (respuesta.getExito() && respuesta.getDatos() != null) {
                                ConfiguracionCliente.setTokenJwt(respuesta.getDatos().getToken());
                                logger.info("Login exitoso para usuario: {}", loginDto.getNombreUsuario());
                                return respuesta.getDatos();
                            } else {
                                String mensaje = respuesta.getMensaje() != null ? 
                                        respuesta.getMensaje() : "Error en login";
                                throw new ConexionException(mensaje);
                            }
                        } catch (ConexionException e) {
                            throw e;
                        } catch (Exception e) {
                            logger.error("Error al parsear respuesta de login", e);
                            throw new ConexionException("Error al procesar respuesta del servidor", e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar solicitud de login", e));
        }
    }

    /**
     * Verifica si existe un usuario activo con el código especificado, de forma asíncrona.
     * 
     * Este método se utiliza en el proceso de autenticación de dos pasos
     * para validar el código antes de solicitar el PIN.
     * 
     * @param codigo código de empleado de 2 dígitos (01-99)
     * @return CompletableFuture con VerificarCodigoDto (existe, nombreCompleto, activo)
     */
    public CompletableFuture<VerificarCodigoDto> verificarCodigoAsync(String codigo) {
        logger.info("Verificando código: {}", codigo);

        HttpRequest request = construirRequest(Constantes.Endpoints.VERIFICAR_CODIGO + "/" + codigo)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            VerificarCodigoDto resultado = objectMapper.readValue(
                                    response.body(), VerificarCodigoDto.class);
                            logger.info("Código verificado: {} - Usuario: {}", codigo, resultado.getNombreCompleto());
                            return resultado;
                        } else if (response.statusCode() == 404) {
                            logger.warn("Código no encontrado: {}", codigo);
                            throw new RuntimeException("Código no encontrado");
                        } else if (response.statusCode() == 400) {
                            logger.warn("Código no válido o usuario inactivo: {}", codigo);
                            throw new RuntimeException("Código no válido o usuario inactivo");
                        } else {
                            logger.error("Error del servidor al verificar código: {}", response.statusCode());
                            throw new ConexionException("Error del servidor: " + response.statusCode());
                        }
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de verificación de código", e);
                        throw new ConexionException("Error al procesar respuesta del servidor", e);
                    }
                });
    }

    /**
     * Cierra la sesión actual.
     * Operación local que limpia el token JWT almacenado en memoria.
     */
    public void logout() {
        logger.info("Cerrando sesión");
        ConfiguracionCliente.limpiarSesion();
    }

    /**
     * Verifica la salud del servidor de forma asíncrona.
     * 
     * @return CompletableFuture<Boolean> true si el servidor responde correctamente
     */
    public CompletableFuture<Boolean> verificarConexionAsync() {
        HttpRequest request = construirRequest(Constantes.Endpoints.HEALTH)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            RespuestaApi<String> respuesta = objectMapper.readValue(
                                    response.body(),
                                    new TypeReference<RespuestaApi<String>>() {});
                            return respuesta != null && respuesta.getExito();
                        }
                        return false;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de health check", e);
                        return false;
                    }
                })
                .exceptionally(error -> {
                    logger.error("Error verificando conexión: {}", error.getMessage());
                    return false;
                });
    }
}
