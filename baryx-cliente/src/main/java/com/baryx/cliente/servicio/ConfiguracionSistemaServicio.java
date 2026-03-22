/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.ConfiguracionSistemaDto;
import com.baryx.common.dto.RespuestaApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Servicio cliente para consumir el endpoint de configuración del sistema.
 *
 * Permite obtener y actualizar configuraciones globales almacenadas en el servidor,
 * como el idioma del sistema.
 *
 * Uso:
 * <pre>
 *   ConfiguracionSistemaServicio servicio = new ConfiguracionSistemaServicio();
 *   ConfiguracionSistemaDto config = servicio.obtenerConfiguracion("idioma");
 *   servicio.actualizarConfiguracion("idioma", "en");
 * </pre>
 *
 * @see com.baryx.servidor.controlador.ConfiguracionSistemaController
 */
public class ConfiguracionSistemaServicio extends ServicioHttpBase {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionSistemaServicio.class);

    /**
     * Obtiene una configuración del sistema por su clave.
     *
     * @param clave Clave de la configuración (ej: "idioma")
     * @return DTO con la configuración, o null si hay error
     */
    public ConfiguracionSistemaDto obtenerConfiguracion(String clave) {
        logger.debug("Obteniendo configuración del sistema: {}", clave);

        HttpRequest request = construirRequest(Constantes.Endpoints.CONFIGURACION_SISTEMA + "/" + clave)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Error al obtener configuración '{}'. Código: {}", clave, response.statusCode());
                return null;
            }

            RespuestaApi<ConfiguracionSistemaDto> respuesta = objectMapper.readValue(
                    response.body(), new TypeReference<RespuestaApi<ConfiguracionSistemaDto>>() {});

            if (respuesta.getExito() != null && respuesta.getExito()) {
                return respuesta.getDatos();
            }
            return null;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Error de conexión al obtener configuración '{}': {}", clave, e.getMessage());
            return null;
        }
    }

    /**
     * Actualiza una configuración del sistema en el servidor.
     * Requiere rol ADMIN.
     *
     * @param clave Clave de la configuración a actualizar
     * @param nuevoValor Nuevo valor a establecer
     * @return DTO con la configuración actualizada, o null si hay error
     * @throws IOException si hay error de conexión
     */
    public ConfiguracionSistemaDto actualizarConfiguracion(String clave, String nuevoValor) throws IOException {
        logger.info("Actualizando configuración '{}' a '{}'", clave, nuevoValor);

        ConfiguracionSistemaDto dto = ConfiguracionSistemaDto.builder()
                .clave(clave)
                .valor(nuevoValor)
                .build();

        try {
            String jsonBody = objectMapper.writeValueAsString(dto);

            HttpRequest request = construirRequest(Constantes.Endpoints.CONFIGURACION_SISTEMA + "/" + clave)
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Error al actualizar configuración '{}'. Código: {}", clave, response.statusCode());
                throw new IOException("Error HTTP: " + response.statusCode());
            }

            RespuestaApi<ConfiguracionSistemaDto> respuesta = objectMapper.readValue(
                    response.body(), new TypeReference<RespuestaApi<ConfiguracionSistemaDto>>() {});

            if (respuesta.getExito() != null && respuesta.getExito()) {
                logger.info("Configuración '{}' actualizada exitosamente", clave);
                return respuesta.getDatos();
            }

            throw new IOException("Respuesta no exitosa del servidor");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operación interrumpida", e);
        }
    }
}
