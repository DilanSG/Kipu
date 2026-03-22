/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx.
 */
package com.baryx.cliente.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

/**
 * Servicio de verificación de actualizaciones.
 *
 * <p>Al arrancar la aplicación, consulta el endpoint público del backend web
 * {@code GET /v1/releases/latest?channel=stable} y compara la versión remota
 * con la versión local embebida en {@code app.properties}.
 *
 * <p>Si hay una versión más nueva disponible retorna un {@link UpdateInfo}
 * con la versión y la URL de la página de descargas (no descarga
 * automáticamente — el usuario decide).
 *
 * <p>El servicio falla silenciosamente: si no hay conexión o el servidor
 * no responde, {@link #verificarActualizacion()} retorna {@link Optional#empty()}.
 */
public class UpdateCheckServicio {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCheckServicio.class);

    /** URL base del backend web de Baryx. Inyectada como system-property por jpackage. */
    private static final String API_URL = System.getProperty(
            "baryx.api.url",
            "https://api.baryx.app"   // valor por defecto producción
    );

    private static final String DOWNLOADS_PAGE = API_URL.replace("api.", "").replaceFirst("/api$", "") + "/downloads";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    /**
     * Información de una actualización disponible.
     *
     * @param versionRemota  La versión nueva disponible (ej: "0.1.0")
     * @param versionLocal   La versión actualmente instalada (ej: "0.0.1")
     * @param urlDescarga    URL a la página de descargas de la web
     * @param notas          Notas del release (puede ser null)
     */
    public record UpdateInfo(
            String versionRemota,
            String versionLocal,
            String urlDescarga,
            String notas
    ) {}

    /**
     * Verifica si hay una nueva versión disponible.
     *
     * @return {@link Optional} con {@link UpdateInfo} si hay actualización, vacío si no hay o si hay error
     */
    public Optional<UpdateInfo> verificarActualizacion() {
        String versionLocal = leerVersionLocal();
        if (versionLocal == null || versionLocal.isBlank()) {
            logger.warn("[Update] No se pudo leer la versión local de app.properties");
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/v1/releases/latest?channel=stable"))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.debug("[Update] Respuesta inesperada del servidor: {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = MAPPER.readTree(response.body());
            String versionRemota = root.path("version").asText(null);
            String notas = root.path("notes").asText(null);

            if (versionRemota == null || versionRemota.isBlank()) {
                logger.debug("[Update] El servidor no devolvió versión");
                return Optional.empty();
            }

            if (esMasNueva(versionRemota, versionLocal)) {
                logger.info("[Update] Nueva versión disponible: {} (actual: {})", versionRemota, versionLocal);
                return Optional.of(new UpdateInfo(versionRemota, versionLocal, DOWNLOADS_PAGE, notas));
            }

            logger.debug("[Update] Sin actualizaciones. Local={}, Remota={}", versionLocal, versionRemota);
            return Optional.empty();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("[Update] Verificación interrumpida");
            return Optional.empty();
        } catch (IOException e) {
            logger.debug("[Update] Sin conexión con el servidor de actualizaciones: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /** Lee la versión desde src/main/resources/app.properties (filtrado por Maven). */
    private String leerVersionLocal() {
        try (InputStream is = getClass().getResourceAsStream("/app.properties")) {
            if (is == null) return null;
            Properties props = new Properties();
            props.load(is);
            return props.getProperty("app.version");
        } catch (IOException e) {
            logger.debug("[Update] No se pudo leer app.properties: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Comparación semántica simple: true si {@code remota} > {@code local}.
     * Soporta versiones tipo MAJOR.MINOR.PATCH (ej: "0.1.0").
     */
    private boolean esMasNueva(String remota, String local) {
        try {
            int[] r = parseSemver(remota);
            int[] l = parseSemver(local);
            for (int i = 0; i < 3; i++) {
                if (r[i] > l[i]) return true;
                if (r[i] < l[i]) return false;
            }
            return false;
        } catch (Exception e) {
            logger.debug("[Update] No se pudo comparar versiones {} vs {}: {}", remota, local, e.getMessage());
            return false;
        }
    }

    private int[] parseSemver(String version) {
        String[] partes = version.replaceAll("[^0-9.]", "").split("\\.");
        int[] nums = new int[3];
        for (int i = 0; i < Math.min(partes.length, 3); i++) {
            nums[i] = Integer.parseInt(partes[i]);
        }
        return nums;
    }
}
