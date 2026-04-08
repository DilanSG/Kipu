/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.servicio;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.common.constantes.Constantes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Clase base para servicios HTTP del cliente.
 * Comparte una única instancia de HttpClient y ObjectMapper
 * para evitar crear recursos duplicados en cada servicio.
 */
public abstract class ServicioHttpBase {

    private static final HttpClient CLIENTE_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Constantes.Http.TIMEOUT_CONEXION_SEGUNDOS))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = crearObjectMapper();

    protected final HttpClient httpClient = CLIENTE_HTTP;
    protected final ObjectMapper objectMapper = OBJECT_MAPPER;

    private static ObjectMapper crearObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Construye un HttpRequest.Builder con URL, headers comunes y timeout.
     * Incluye Content-Type, Authorization (si hay token), X-Client-Name y X-Client-IP.
     */
    protected HttpRequest.Builder construirRequest(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ConfiguracionCliente.getUrlServidor() + endpoint))
                .header(Constantes.Http.HEADER_CONTENT_TYPE, Constantes.Http.CONTENT_TYPE_JSON)
                .timeout(Duration.ofSeconds(Constantes.Http.TIMEOUT_LECTURA_SEGUNDOS));

        String token = obtenerToken();
        if (token != null && !token.isEmpty()) {
            builder.header(Constantes.Http.HEADER_AUTHORIZATION,
                          Constantes.Http.BEARER_PREFIX + token);
        }

        String nombreCliente = ConfiguracionCliente.getNombreCliente();
        if (nombreCliente != null && !nombreCliente.isEmpty()) {
            builder.header(Constantes.Http.HEADER_CLIENT_NAME, nombreCliente);
        }

        String ipLocal = ConfiguracionCliente.getIpLocal();
        if (ipLocal != null && !ipLocal.isEmpty()) {
            builder.header(Constantes.Http.HEADER_CLIENT_IP, ipLocal);
        }

        return builder;
    }

    /**
     * Obtiene el token JWT. Las subclases con token inyectado
     * pueden sobreescribir este método.
     */
    protected String obtenerToken() {
        return ConfiguracionCliente.getTokenJwt();
    }
}
