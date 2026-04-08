/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.servicio;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.RegistrarVentaDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.common.dto.VentaDto;
import com.kipu.common.excepcion.ConexionException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Servicio del cliente para registrar ventas en el backend. */
public class VentaServicio extends ServicioHttpBase {

    private static final Logger logger = LoggerFactory.getLogger(VentaServicio.class);

    private final String token;

    public VentaServicio(String token) {
        this.token = token;
    }

    @Override
    protected String obtenerToken() {
        return token;
    }

    /**
     * Registra una venta en el backend de forma asíncrona.
     * @param dto Datos de la venta (mesa, pagos, líneas, montos)
     * @param idCajero ID del usuario cajero que procesa el pago
     * @param nombreCajero Nombre del cajero
     * @return CompletableFuture con la venta registrada
     */
    public CompletableFuture<VentaDto> registrarVentaAsync(
            RegistrarVentaDto dto, Long idCajero, String nombreCajero) {

        logger.info("Registrando venta para mesa {} con {} pagos",
                dto.getNumeroMesa(), dto.getPagos() != null ? dto.getPagos().size() : 0);

        try {
            String body = objectMapper.writeValueAsString(dto);
            String endpoint = Constantes.Endpoints.VENTAS
                    + "?idCajero=" + idCajero
                    + "&nombreCajero=" + URLEncoder.encode(nombreCajero, StandardCharsets.UTF_8);

            HttpRequest request = construirRequest(endpoint)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 201 || response.statusCode() == 200) {
                                RespuestaApi<VentaDto> respuesta = objectMapper.readValue(
                                        response.body(),
                                        new TypeReference<RespuestaApi<VentaDto>>() {});
                                logger.info("Venta registrada exitosamente: ID {}",
                                        respuesta.getDatos() != null ? respuesta.getDatos().getIdVenta() : "?");
                                return respuesta.getDatos();
                            } else {
                                logger.error("Error al registrar venta. Código: {}, Respuesta: {}",
                                        response.statusCode(), response.body());
                                throw new ConexionException(
                                        "Error al registrar venta: código " + response.statusCode());
                            }
                        } catch (ConexionException e) {
                            throw e;
                        } catch (Exception e) {
                            logger.error("Error al procesar respuesta de venta", e);
                            throw new ConexionException("Error al procesar respuesta del servidor");
                        }
                    });
        } catch (Exception e) {
            logger.error("Error al serializar datos de venta", e);
            return CompletableFuture.failedFuture(
                    new ConexionException("Error al preparar datos de venta"));
        }
    }

    /**
     * Lista ventas registradas desde la fecha indicada.
     * @param desde Fecha/hora de inicio de la sesión de caja
     * @return CompletableFuture con la lista de ventas
     */
    public CompletableFuture<List<VentaDto>> listarVentasDesdeAsync(LocalDateTime desde) {
        logger.debug("Solicitando ventas desde: {}", desde);

        String endpoint = Constantes.Endpoints.VENTAS
                + "?desde=" + URLEncoder.encode(desde.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), StandardCharsets.UTF_8);

        HttpRequest request = construirRequest(endpoint)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Error al listar ventas. Código: {}", response.statusCode());
                        throw new ConexionException("Error al listar ventas: código " + response.statusCode());
                    }
                    try {
                        RespuestaApi<List<VentaDto>> respuesta = objectMapper.readValue(
                                response.body(),
                                new TypeReference<RespuestaApi<List<VentaDto>>>() {});
                        List<VentaDto> ventas = respuesta.getDatos();
                        logger.info("Ventas obtenidas: {}", ventas != null ? ventas.size() : 0);
                        return ventas;
                    } catch (Exception e) {
                        logger.error("Error al parsear respuesta de ventas", e);
                        throw new ConexionException("Error al parsear respuesta de ventas: " + e.getMessage());
                    }
                });
    }
}
