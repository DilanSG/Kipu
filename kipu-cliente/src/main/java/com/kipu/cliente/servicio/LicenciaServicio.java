/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu.
 */
package com.kipu.cliente.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Servicio de validación de licencias con soporte de trial local.
 *
 * <h2>Flujo de arranque</h2>
 * <ol>
 *   <li>Verifica si hay trial local activo (14 días sin key, sin internet).</li>
 *   <li>Si hay key: valida online → cachea → usa offline si falla.</li>
 *   <li>Si no hay key y trial expiró: bloquea (TRIAL_EXPIRED).</li>
 *   <li>Retorna estado con días restantes para avisos de renovación.</li>
 * </ol>
 */
public class LicenciaServicio {

    private static final Logger logger = LoggerFactory.getLogger(LicenciaServicio.class);

    private static final String API_URL = System.getProperty("kipu.api.url", "https://kipuweb.onrender.com");
    private static final String WEB_URL = System.getProperty("kipu.web.url", "https://www.kipu.org");

    private static final int DIAS_TRIAL = 14;
    public static final int DIAS_AVISO_RENOVACION = 5;
    private static final int CACHE_TTL_HOURS = 72;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // Tipos públicos
    // ------------------------------------------------------------------

    public enum EstadoLicencia {
        VALID,           // Licencia activa pagada
        TRIAL,           // Trial local activo (sin key)
        TRIAL_EXPIRED,   // Trial local expirado, necesita comprar
        EXPIRED,         // Licencia pagada expirada
        REVOKED,         // Licencia revocada por admin
        NO_KEY,          // Sin key y sin trial (no debería ocurrir normalmente)
        OFFLINE,         // Sin conexión, usando caché válida
        ERROR            // Error inesperado
    }

    public record ResultadoValidacion(
            EstadoLicencia estado,
            String licenseKey,
            String plan,
            String expira,          // ISO-8601 o null
            String mensaje,         // descripción human-readable
            int diasRestantes,      // días hasta expiración (-1 si no aplica)
            boolean enGracia        // si está en período de gracia post-expiración
    ) {
        /** Constructor de compatibilidad sin días restantes */
        public ResultadoValidacion(EstadoLicencia estado, String licenseKey, String plan,
                                   String expira, String mensaje) {
            this(estado, licenseKey, plan, expira, mensaje, -1, false);
        }
    }

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    /**
     * Carga y valida la licencia. Nunca lanza excepción — siempre retorna un resultado.
     * Flujo: bootstrap token → key presente → validar online → trial local → bloqueado.
     */
    public ResultadoValidacion cargarYValidar() {
        // Intentar canjear bootstrap token si hay uno embebido
        canjearBootstrapToken();

        String licenseKey = leerLicenseKey();

        // Si hay key configurada, validar contra el servidor
        if (licenseKey != null && !licenseKey.isBlank()) {
            return validarConKey(licenseKey);
        }

        // Sin key: verificar trial local
        return verificarTrialLocal();
    }

    /**
     * Activa una licencia: guarda la key localmente y la registra en el servidor.
     * Envía datos del equipo (device hash) para vincular la licencia al hardware.
     */
    public ResultadoValidacion activarLicencia(String licenseKey) {
        guardarLicenseKeyLocal(licenseKey);

        try {
            String deviceHash = calcularDeviceHash();
            return activarEnServidor(licenseKey, deviceHash);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null,
                    "Activación interrumpida. Verifica tu conexión a Internet.");
        } catch (IOException e) {
            String detalle = obtenerMensajeError(e);
            logger.warn("[License] Error de conexión al activar: {}", detalle, e);
            return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null,
                    "No se pudo conectar al servidor de licencias (" + detalle + "). "
                    + "Verifica tu conexión a Internet e intenta de nuevo.");
        }
    }

    /**
     * Calcula el device hash de este equipo (público para mostrarlo en config).
     */
    public String obtenerDeviceHash() {
        return calcularDeviceHash();
    }

    /**
     * URL de la tienda web para comprar/renovar licencias.
     */
    public static String getUrlTienda() {
        return WEB_URL;
    }

    /**
     * Lee el último resultado de validación cacheado sin ir al servidor.
     * Útil para mostrar info en el panel de configuración sin bloquear.
     */
    public ResultadoValidacion leerCacheLocal() {
        String licenseKey = leerLicenseKey();

        if (licenseKey == null || licenseKey.isBlank()) {
            // Sin key: verificar trial
            return verificarTrialLocal();
        }

        return usarCache(licenseKey, "Usando datos guardados");
    }

    // ------------------------------------------------------------------
    // Trial local (14 días sin key, sin internet)
    // ------------------------------------------------------------------

    private Path rutaTrial() {
        return Paths.get(System.getProperty("user.home"), ".kipu", "trial.properties");
    }

    private ResultadoValidacion verificarTrialLocal() {
        try {
            Path trialFile = rutaTrial();

            if (!Files.exists(trialFile)) {
                // Primera instalación: crear trial
                iniciarTrial();
                long diasRestantes = DIAS_TRIAL;
                String expira = LocalDate.now().plusDays(DIAS_TRIAL).toString();
                return new ResultadoValidacion(EstadoLicencia.TRIAL, null, "trial", expira,
                        "Prueba gratis de " + DIAS_TRIAL + " días activada. ¡Bienvenido a Kipu!",
                        (int) diasRestantes, false);
            }

            // Leer fecha de inicio del trial
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(trialFile, StandardCharsets.UTF_8)) {
                props.load(r);
            }
            String inicioStr = props.getProperty("trial.start", "");
            if (inicioStr.isBlank()) {
                iniciarTrial();
                return new ResultadoValidacion(EstadoLicencia.TRIAL, null, "trial",
                        LocalDate.now().plusDays(DIAS_TRIAL).toString(),
                        "Prueba gratis activada.", DIAS_TRIAL, false);
            }

            LocalDate inicio = LocalDate.parse(inicioStr);
            LocalDate expira = inicio.plusDays(DIAS_TRIAL);
            long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(), expira);

            if (diasRestantes <= 0) {
                // Trial expirado
                logger.info("[License] Trial local expirado (inició: {})", inicio);
                return new ResultadoValidacion(EstadoLicencia.TRIAL_EXPIRED, null, "trial",
                        expira.toString(),
                        "Tu prueba gratis de " + DIAS_TRIAL + " días ha terminado. " +
                        "Adquiere una licencia en " + WEB_URL + " para continuar usando Kipu.",
                        0, false);
            }

            logger.info("[License] Trial activo: {} días restantes", diasRestantes);
            return new ResultadoValidacion(EstadoLicencia.TRIAL, null, "trial",
                    expira.toString(),
                    "Prueba gratis activa. " + diasRestantes + " días restantes.",
                    (int) diasRestantes, false);

        } catch (Exception e) {
            logger.warn("[License] Error verificando trial local: {}", e.getMessage());
            // Si falla la lectura del trial, iniciar uno nuevo
            iniciarTrial();
            return new ResultadoValidacion(EstadoLicencia.TRIAL, null, "trial",
                    LocalDate.now().plusDays(DIAS_TRIAL).toString(),
                    "Prueba gratis activada.", DIAS_TRIAL, false);
        }
    }

    private void iniciarTrial() {
        try {
            Path trialFile = rutaTrial();
            Files.createDirectories(trialFile.getParent());
            String contenido = "trial.start=" + LocalDate.now() + "\n"
                    + "trial.days=" + DIAS_TRIAL + "\n";
            Files.writeString(trialFile, contenido, StandardCharsets.UTF_8);
            logger.info("[License] Trial de {} días iniciado", DIAS_TRIAL);
        } catch (IOException e) {
            logger.warn("[License] No se pudo crear archivo de trial: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Validación con key (online → caché)
    // ------------------------------------------------------------------

    private ResultadoValidacion validarConKey(String licenseKey) {
        try {
            String deviceHash = calcularDeviceHash();
            return validarOnline(licenseKey, deviceHash);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return usarCache(licenseKey, "Validación interrumpida");
        } catch (IOException e) {
            String detalle = obtenerMensajeError(e);
            logger.debug("[License] Sin conexión con servidor de licencias: {}", detalle);
            return usarCache(licenseKey, "Sin conexión — usando validación guardada");
        }
    }

    // ------------------------------------------------------------------
    // Activación de dispositivo en servidor
    // ------------------------------------------------------------------

    private ResultadoValidacion activarEnServidor(String licenseKey, String deviceHash)
            throws IOException, InterruptedException {

        String body = MAPPER.writeValueAsString(java.util.Map.of(
                "licenseKey", licenseKey,
                "deviceHash", deviceHash,
                "deviceLabel", obtenerDeviceLabel()
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/v1/licenses/activate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = MAPPER.readTree(response.body());

        if (response.statusCode() == 404) {
            return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null,
                    "Clave de licencia no encontrada. Verifica que la ingresaste correctamente.");
        }

        if (response.statusCode() == 409) {
            String errorMsg = root.path("error").asText("Error de activación");
            if (errorMsg.contains("SEAT_LIMIT")) {
                return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null,
                        "Esta licencia ya está vinculada a otro equipo. " +
                        "Desvincula el equipo anterior desde tu panel en " + WEB_URL +
                        " o contacta soporte.");
            }
            return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null, errorMsg);
        }

        if (response.statusCode() != 200) {
            return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null,
                    "Error del servidor (" + response.statusCode() + "). Intenta de nuevo.");
        }

        // Activación exitosa: ahora validar para obtener datos completos
        logger.info("[License] Activación exitosa — validando licencia...");
        return validarOnline(licenseKey, deviceHash);
    }

    private String obtenerDeviceLabel() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            String os = System.getProperty("os.name", "");
            return hostname + " (" + os + ")";
        } catch (Exception e) {
            return System.getProperty("user.name", "Kipu Desktop");
        }
    }

    // ------------------------------------------------------------------
    // Validación online
    // ------------------------------------------------------------------

    private ResultadoValidacion validarOnline(String licenseKey, String deviceHash)
            throws IOException, InterruptedException {

        String body = MAPPER.writeValueAsString(java.util.Map.of(
                "deviceHash", deviceHash,
                "deviceLabel", obtenerDeviceLabel()
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/v1/licenses/" + encodeKey(licenseKey) + "/validate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = MAPPER.readTree(response.body());

        if (response.statusCode() == 404) {
            guardarCache(licenseKey, EstadoLicencia.ERROR, null, null, -1, false);
            return new ResultadoValidacion(EstadoLicencia.ERROR, licenseKey, null, null,
                    "Clave de licencia no encontrada en el servidor.");
        }

        if (response.statusCode() != 200) {
            return usarCache(licenseKey, "Error del servidor (" + response.statusCode() + ")");
        }

        String statusStr = root.path("status").asText("error");
        JsonNode licenseNode = root.path("license");
        String plan = licenseNode.path("plan").asText(null);
        int diasRestantes = root.path("daysRemaining").asInt(-1);
        boolean enGracia = root.path("inGrace").asBoolean(false);
        String expira = null;
        if (!licenseNode.path("expiresAt").isMissingNode()) {
            expira = Instant.ofEpochMilli(licenseNode.path("expiresAt").asLong()).toString();
        }

        EstadoLicencia estado = switch (statusStr) {
            case "valid" -> EstadoLicencia.VALID;
            case "expired" -> EstadoLicencia.EXPIRED;
            case "revoked" -> EstadoLicencia.REVOKED;
            default -> EstadoLicencia.ERROR;
        };

        guardarCache(licenseKey, estado, plan, expira, diasRestantes, enGracia);

        String mensaje = switch (estado) {
            case VALID -> {
                if (diasRestantes >= 0 && diasRestantes <= DIAS_AVISO_RENOVACION) {
                    yield "Licencia activa. Plan: " + plan + ". ¡Quedan " + diasRestantes
                            + " días! Renueva en " + WEB_URL;
                }
                yield "Licencia activa. Plan: " + plan
                        + (diasRestantes >= 0 ? " (" + diasRestantes + " días restantes)" : "");
            }
            case EXPIRED -> {
                if (enGracia) {
                    yield "Tu licencia ha expirado pero estás en período de gracia. "
                            + "Renueva pronto en " + WEB_URL + " para no perder acceso.";
                }
                yield "Tu licencia ha expirado. Renueva en " + WEB_URL + " para seguir usando Kipu.";
            }
            case REVOKED -> "La licencia ha sido revocada. Contacta soporte o adquiere una nueva en " + WEB_URL;
            default -> "Error de validación.";
        };

        logger.info("[License] Estado: {} | Plan: {} | Días: {} | Gracia: {}", estado, plan, diasRestantes, enGracia);
        return new ResultadoValidacion(estado, licenseKey, plan, expira, mensaje, diasRestantes, enGracia);
    }

    // ------------------------------------------------------------------
    // Caché local
    // ------------------------------------------------------------------

    private Path rutaCache() {
        return Paths.get(System.getProperty("user.home"), ".kipu", "license-cache.json");
    }

    private void guardarCache(String key, EstadoLicencia estado, String plan, String expira,
                              int diasRestantes, boolean enGracia) {
        try {
            Path cache = rutaCache();
            Files.createDirectories(cache.getParent());
            String json = MAPPER.writeValueAsString(java.util.Map.of(
                    "key", key,
                    "estado", estado.name(),
                    "plan", plan != null ? plan : "",
                    "expira", expira != null ? expira : "",
                    "diasRestantes", diasRestantes,
                    "enGracia", enGracia,
                    "cachedAt", Instant.now().toString()
            ));
            Files.writeString(cache, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.debug("[License] No se pudo guardar caché: {}", e.getMessage());
        }
    }

    private ResultadoValidacion usarCache(String licenseKey, String razon) {
        try {
            Path cache = rutaCache();
            if (!Files.exists(cache)) {
                return new ResultadoValidacion(EstadoLicencia.OFFLINE, licenseKey, null, null,
                        razon + ". No hay caché local.", -1, false);
            }

            JsonNode root = MAPPER.readTree(Files.readString(cache));
            String cachedAt = root.path("cachedAt").asText(null);

            if (cachedAt != null) {
                Instant ts = Instant.parse(cachedAt);
                if (Instant.now().isAfter(ts.plusSeconds((long) CACHE_TTL_HOURS * 3600))) {
                    return new ResultadoValidacion(EstadoLicencia.OFFLINE, licenseKey, null, null,
                            razon + ". Caché expirada — verifica tu conexión a Internet.", -1, false);
                }
            }

            String estadoStr = root.path("estado").asText(EstadoLicencia.OFFLINE.name());
            EstadoLicencia estado = EstadoLicencia.valueOf(estadoStr);
            String plan = root.path("plan").asText(null);
            String expira = root.path("expira").asText(null);
            int diasRestantes = root.path("diasRestantes").asInt(-1);
            boolean enGracia = root.path("enGracia").asBoolean(false);

            if ("".equals(plan)) plan = null;
            if ("".equals(expira)) expira = null;

            logger.info("[License] Usando caché. Estado: {} | Razón: {}", estado, razon);
            return new ResultadoValidacion(estado, licenseKey, plan, expira,
                    razon + " (modo offline).", diasRestantes, enGracia);

        } catch (Exception e) {
            logger.warn("[License] Error leyendo caché: {}", e.getMessage());
            return new ResultadoValidacion(EstadoLicencia.OFFLINE, licenseKey, null, null,
                    razon, -1, false);
        }
    }

    // ------------------------------------------------------------------
    // Bootstrap token
    // ------------------------------------------------------------------

    /**
     * Intenta canjear un bootstrap token embebido en el instalador por una key real.
     * El token es de un solo uso y corta vida. Si el canje es exitoso, guarda la key
     * localmente y retorna true. Si falla, retorna false (el flujo continúa normal).
     */
    private boolean canjearBootstrapToken() {
        // 1. Leer bootstrap.token del classpath (license.properties)
        try (InputStream is = getClass().getResourceAsStream("/license.properties")) {
            if (is == null) return false;
            Properties props = new Properties();
            props.load(is);
            String bootstrapToken = props.getProperty("bootstrap.token", "").trim();
            if (bootstrapToken.isBlank() || !bootstrapToken.startsWith("BST-")) return false;

            // 2. Verificar que no haya una key local ya guardada
            Path localProps = Paths.get(System.getProperty("user.home"), ".kipu", "license.properties");
            if (Files.exists(localProps)) {
                Properties local = new Properties();
                try (Reader r = Files.newBufferedReader(localProps, StandardCharsets.UTF_8)) {
                    local.load(r);
                }
                String claveExistente = local.getProperty("license.key", "").trim();
                if (!claveExistente.isBlank()) {
                    logger.debug("[License] Key local ya existe — omitiendo canje de bootstrap");
                    return false;
                }
            }

            // 3. Canjear contra el servidor
            logger.info("[License] Canjeando bootstrap token...");
            String deviceHash = calcularDeviceHash();
            String body = MAPPER.writeValueAsString(java.util.Map.of(
                    "token", bootstrapToken,
                    "deviceHash", deviceHash,
                    "deviceLabel", obtenerDeviceLabel()
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/v1/licenses/bootstrap/exchange"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(response.body());
                String licenseKey = root.path("licenseKey").asText("");
                if (!licenseKey.isBlank()) {
                    guardarLicenseKeyLocal(licenseKey);
                    logger.info("[License] Bootstrap canjeado exitosamente — key guardada");
                    return true;
                }
            } else {
                logger.warn("[License] Canje de bootstrap fallido (HTTP {}): {}",
                        response.statusCode(), response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[License] Canje de bootstrap interrumpido");
        } catch (Exception e) {
            logger.debug("[License] No se pudo canjear bootstrap token: {}", obtenerMensajeError(e));
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Carga la clave de licencia con prioridad:
     * 1. ~/.kipu/license.properties (key ingresada por el usuario en la app)
     * 2. /license.properties en el classpath (clave embebida por CI en el instalador)
     */
    private String leerLicenseKey() {
        // 1. Archivo local del usuario (toma prioridad sobre la key embebida)
        try {
            Path localProps = Paths.get(
                    System.getProperty("user.home"), ".kipu", "license.properties");
            if (Files.exists(localProps)) {
                Properties local = new Properties();
                try (Reader r = Files.newBufferedReader(localProps, StandardCharsets.UTF_8)) {
                    local.load(r);
                }
                String key = local.getProperty("license.key", "").trim();
                if (!key.isBlank()) {
                    logger.debug("[License] Usando key local de ~/.kipu/license.properties");
                    return key;
                }
            }
        } catch (Exception e) {
            logger.debug("[License] No se pudo leer license.properties local: {}", e.getMessage());
        }

        // 2. Key embebida por CI en el instalador (classpath)
        try (InputStream is = getClass().getResourceAsStream("/license.properties")) {
            if (is == null) return null;
            Properties props = new Properties();
            props.load(is);
            String key = props.getProperty("license.key", "").trim();
            if (!key.isBlank()) {
                logger.debug("[License] Usando key embebida del classpath");
                return key;
            }
        } catch (IOException e) {
            logger.debug("[License] No se pudo leer license.properties classpath: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Guarda la key de licencia en ~/.kipu/license.properties.
     */
    private void guardarLicenseKeyLocal(String key) {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".kipu");
            Files.createDirectories(dir);
            Path propsPath = dir.resolve("license.properties");
            String contenido = "license.key=" + key + "\n";
            Files.writeString(propsPath, contenido, StandardCharsets.UTF_8);
            logger.info("[License] Key guardada en {}", propsPath);
        } catch (IOException e) {
            logger.warn("[License] No se pudo guardar key local: {}", e.getMessage());
        }
    }

    /**
     * Genera un hash SHA-256 del hardware del equipo para usarlo como identificador de dispositivo.
     * Se basa en la MAC address de la primera interfaz de red no-loopback disponible.
     */
    private String calcularDeviceHash() {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                byte[] mac = iface.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    for (byte b : mac) sb.append(String.format("%02x", b));
                    break;
                }
            }
            // Fall back to username + OS if no MAC found
            if (sb.isEmpty()) {
                sb.append(System.getProperty("user.name", "unknown"))
                  .append(System.getProperty("os.name", "unknown"));
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();

        } catch (Exception e) {
            logger.warn("[License] No se pudo calcular device hash: {}", e.getMessage());
            return "unknown-device";
        }
    }

    private String encodeKey(String key) {
        return java.net.URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    /**
     * Extrae un mensaje descriptivo de una excepción, recorriendo la cadena de causas
     * si el mensaje principal es null (común en IOException de HttpClient).
     */
    private String obtenerMensajeError(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        Throwable causa = e.getCause();
        while (causa != null) {
            if (causa.getMessage() != null && !causa.getMessage().isBlank()) {
                return causa.getMessage();
            }
            causa = causa.getCause();
        }
        return e.getClass().getSimpleName();
    }
}
