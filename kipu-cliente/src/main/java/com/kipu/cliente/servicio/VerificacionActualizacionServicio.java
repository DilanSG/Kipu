/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu.
 */
package com.kipu.cliente.servicio;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;

/**
 * Servicio de verificación y descarga de actualizaciones.
 *
 * <p>Consulta el endpoint {@code GET /v1/releases/latest?channel=stable} del backend web,
 * compara versiones y puede descargar el instalador para la plataforma actual.
 */
public class VerificacionActualizacionServicio {

    private static final Logger logger = LoggerFactory.getLogger(VerificacionActualizacionServicio.class);

    private static final String API_URL = System.getProperty(
            "kipu.api.url",
            "https://kipuweb.onrender.com"
    );

    private static final Path DIRECTORIO_UPDATES = Paths.get(
            System.getProperty("user.home"), ".kipu", "updates");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // Tipos públicos
    // ------------------------------------------------------------------

    /**
     * Información de una actualización disponible.
     */
    public record UpdateInfo(
            String versionRemota,
            String versionLocal,
            String urlDescargaDirecta,  // URL del asset (.deb/.exe) para esta plataforma
            String notas,
            String checksumEsperado,    // SHA-256 del asset (puede ser null)
            boolean forzada
    ) {}

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    /** Retorna la versión local embebida en app.properties. */
    public String obtenerVersionLocal() {
        return leerVersionLocal();
    }

    /**
     * Verifica si hay una nueva versión disponible consultando el backend web.
     */
    public Optional<UpdateInfo> verificarActualizacion() {
        String versionLocal = leerVersionLocal();
        if (versionLocal == null || versionLocal.isBlank()) {
            logger.warn("[Update] No se pudo leer la versión local de app.properties");
            return Optional.empty();
        }

        // Reintentar hasta 3 veces con espera progresiva (Render cold start ~30s)
        int maxIntentos = 3;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                logger.debug("[Update] Intento {}/{} de verificación...", intento, maxIntentos);
                return consultarServidor(versionLocal);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("[Update] Verificación interrumpida");
                return Optional.empty();
            } catch (IOException e) {
                String detalle = obtenerMensajeError(e);
                if (intento < maxIntentos) {
                    int esperaSegundos = intento * 5; // 5s, 10s
                    logger.debug("[Update] Intento {}/{} falló ({}). Reintentando en {}s...",
                            intento, maxIntentos, detalle, esperaSegundos);
                    try {
                        Thread.sleep(esperaSegundos * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                } else {
                    logger.debug("[Update] Sin conexión tras {} intentos: {}", maxIntentos, detalle);
                }
            }
        }
        return Optional.empty();
    }

    /** Ejecuta la consulta HTTP al endpoint de releases. */
    private Optional<UpdateInfo> consultarServidor(String versionLocal)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/v1/releases/latest?channel=stable"))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            logger.debug("[Update] No hay releases publicados en el servidor");
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            logger.debug("[Update] Respuesta inesperada del servidor: {}", response.statusCode());
            return Optional.empty();
        }

        JsonNode root = MAPPER.readTree(response.body());
        String versionRemota = root.path("version").asText(null);
        String notas = root.path("notes").asText(null);
        boolean forzada = root.path("isForced").asBoolean(false);

        // Extraer URL del asset por plataforma
        String plataforma = detectarPlataforma();
        JsonNode urlNode = root.path("url");
        String urlAssets = urlNode.isTextual() ? urlNode.asText("") : "";
        String urlDescargaDirecta = null;
        String checksumEsperado = null;

        // url y checksum se almacenan como JSON stringificado en la BD
        if (!urlAssets.isBlank()) {
            JsonNode assetsNode = MAPPER.readTree(urlAssets);
            urlDescargaDirecta = assetsNode.path(plataforma).asText(null);
        }
        String checksumRaw = root.path("checksum").asText("");
        if (!checksumRaw.isBlank()) {
            try {
                JsonNode checksumNode = MAPPER.readTree(checksumRaw);
                checksumEsperado = checksumNode.path(plataforma).asText(null);
            } catch (Exception ignored) {
                // checksum puede ser un string plano, no JSON
            }
        }

        if (versionRemota == null || versionRemota.isBlank()) {
            logger.debug("[Update] El servidor no devolvió versión");
            return Optional.empty();
        }

        if (esMasNueva(versionRemota, versionLocal)) {
            logger.info("[Update] Nueva versión disponible: {} (actual: {})", versionRemota, versionLocal);
            return Optional.of(new UpdateInfo(
                    versionRemota, versionLocal, urlDescargaDirecta,
                    notas, checksumEsperado, forzada));
        }

        logger.debug("[Update] Sin actualizaciones. Local={}, Remota={}", versionLocal, versionRemota);
        return Optional.empty();
    }

    /**
     * Descarga el instalador desde la URL del release.
     *
     * @param urlDescarga URL directa del asset (.deb/.exe/.dmg) para la plataforma actual
     * @param version     Versión del release (para nombrar el archivo)
     * @param progreso    Callback (bytesDescargados, bytesTotales). totales puede ser -1 si no se conoce.
     * @return Ruta al archivo descargado
     */
    public Path descargarInstalador(String urlDescarga, String version,
                                    BiConsumer<Long, Long> progreso)
            throws IOException, InterruptedException {

        Files.createDirectories(DIRECTORIO_UPDATES);

        String nombreArchivo = extraerNombreArchivo(urlDescarga, version);
        Path archivoDestino = DIRECTORIO_UPDATES.resolve(nombreArchivo);

        // Si ya existe y está completo (de un intento anterior), reutilizar
        if (Files.exists(archivoDestino) && Files.size(archivoDestino) > 0) {
            logger.info("[Update] Archivo ya descargado: {}", archivoDestino);
            progreso.accept(Files.size(archivoDestino), Files.size(archivoDestino));
            return archivoDestino;
        }

        logger.info("[Update] Descargando instalador desde: {}", urlDescarga);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlDescarga))
                .header("Accept", "application/octet-stream")
                .GET()
                .timeout(Duration.ofMinutes(10))
                .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Error descargando instalador: HTTP " + response.statusCode());
        }

        long totalBytes = response.headers()
                .firstValueAsLong("Content-Length").orElse(-1L);

        Path archivoTemporal = DIRECTORIO_UPDATES.resolve(nombreArchivo + ".tmp");
        try (InputStream is = response.body();
             var os = Files.newOutputStream(archivoTemporal)) {

            byte[] buffer = new byte[8192];
            long bytesLeidos = 0;
            int n;
            while ((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
                bytesLeidos += n;
                progreso.accept(bytesLeidos, totalBytes);
            }
        }

        Files.move(archivoTemporal, archivoDestino, StandardCopyOption.REPLACE_EXISTING);
        logger.info("[Update] Descarga completada: {} ({} bytes)", archivoDestino, Files.size(archivoDestino));
        return archivoDestino;
    }

    /**
     * Verifica el checksum SHA-256 de un archivo descargado.
     *
     * @return true si el checksum coincide, false si no coincide o no se puede verificar
     */
    public boolean verificarChecksum(Path archivo, String checksumEsperado) {
        if (checksumEsperado == null || checksumEsperado.isBlank()) {
            logger.debug("[Update] Sin checksum para verificar, omitiendo");
            return true; // Sin checksum provisto, se asume correcto
        }

        try (InputStream is = Files.newInputStream(archivo)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            String checksumReal = hex.toString();
            boolean coincide = checksumReal.equalsIgnoreCase(checksumEsperado);
            if (!coincide) {
                logger.warn("[Update] Checksum no coincide. Esperado: {}, Obtenido: {}",
                        checksumEsperado, checksumReal);
            }
            return coincide;
        } catch (Exception e) {
            logger.warn("[Update] Error verificando checksum: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Instala la actualización descargada según la plataforma actual.
     *
     * @return true si la instalación fue exitosa
     */
    public boolean instalarActualizacion(Path archivoInstalador) throws IOException, InterruptedException {
        String plataforma = detectarPlataforma();
        logger.info("[Update] Instalando para {}: {}", plataforma, archivoInstalador);

        return switch (plataforma) {
            case "linux" -> instalarLinux(archivoInstalador);
            case "windows" -> instalarWindows(archivoInstalador);
            case "macos" -> instalarMacOS(archivoInstalador);
            default -> {
                logger.warn("[Update] Plataforma no soportada para instalación automática: {}", plataforma);
                yield false;
            }
        };
    }

    /** Linux: instala .deb usando pkexec (prompt gráfico de contraseña). */
    private boolean instalarLinux(Path archivoDeb) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "pkexec", "dpkg", "-i", archivoDeb.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        String salida;
        try (InputStream is = proceso.getInputStream()) {
            salida = new String(is.readAllBytes());
        }

        int exitCode = proceso.waitFor();
        if (exitCode == 0) {
            logger.info("[Update] Instalación Linux exitosa");
            limpiarDescargas();
            return true;
        } else {
            logger.warn("[Update] Instalación Linux falló (código {}): {}", exitCode, salida);
            return false;
        }
    }

    /** Windows: ejecuta el instalador .exe (Inno Setup maneja UAC internamente). */
    private boolean instalarWindows(Path archivoExe) throws IOException, InterruptedException {
        // /VERYSILENT: sin interfaz, /SUPPRESSMSGBOXES: sin diálogos de confirmación
        // /NORESTART: no reiniciar Windows, la app se encarga de relanzarse
        ProcessBuilder pb = new ProcessBuilder(
                archivoExe.toAbsolutePath().toString(),
                "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART");
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        String salida;
        try (InputStream is = proceso.getInputStream()) {
            salida = new String(is.readAllBytes());
        }

        int exitCode = proceso.waitFor();
        if (exitCode == 0) {
            logger.info("[Update] Instalación Windows exitosa");
            limpiarDescargas();
            return true;
        } else {
            logger.warn("[Update] Instalación Windows falló (código {}): {}", exitCode, salida);
            return false;
        }
    }

    /** macOS: abre el .dmg con el comando 'open' y deja que el usuario arrastre a Applications. */
    private boolean instalarMacOS(Path archivoDmg) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("open", archivoDmg.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        int exitCode = proceso.waitFor();
        if (exitCode == 0) {
            logger.info("[Update] DMG abierto — el usuario debe arrastrar a Applications y reiniciar");
            // No limpiar: el DMG debe permanecer accesible mientras el usuario arrastra
            return true;
        } else {
            logger.warn("[Update] No se pudo abrir el DMG (código {})", exitCode);
            return false;
        }
    }

    /** @return true si la plataforma actual es macOS. */
    public boolean esMacOS() {
        return "macos".equals(detectarPlataforma());
    }

    /** Reinicia la aplicación detectando el ejecutable según la plataforma. */
    public void reiniciarAplicacion() {
        try {
            String ejecutable = resolverEjecutable();
            if (ejecutable == null) {
                logger.warn("[Update] No se pudo resolver la ruta del ejecutable para reiniciar");
                return;
            }

            Path exe = Path.of(ejecutable);
            if (!Files.exists(exe)) {
                logger.warn("[Update] Ejecutable no encontrado en: {}", ejecutable);
                return;
            }

            logger.info("[Update] Reiniciando aplicación: {}", ejecutable);
            new ProcessBuilder(ejecutable).start();
            System.exit(0);
        } catch (IOException e) {
            logger.error("[Update] Error al reiniciar: {}", e.getMessage());
        }
    }

    /** Limpia archivos descargados previamente. */
    public void limpiarDescargas() {
        try {
            if (Files.exists(DIRECTORIO_UPDATES)) {
                try (var archivos = Files.list(DIRECTORIO_UPDATES)) {
                    archivos.forEach(f -> {
                        try { Files.deleteIfExists(f); }
                        catch (IOException ignored) {}
                    });
                }
            }
        } catch (IOException e) {
            logger.debug("[Update] Error limpiando descargas: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /** Extrae el nombre del archivo de la URL de descarga, con fallback por plataforma. */
    private String extraerNombreArchivo(String urlDescarga, String version) {
        // Intentar extraer el nombre real del archivo desde la URL
        try {
            String path = URI.create(urlDescarga).getPath();
            if (path != null && path.contains("/")) {
                String nombre = path.substring(path.lastIndexOf('/') + 1);
                if (!nombre.isBlank() && nombre.contains(".")) {
                    return nombre;
                }
            }
        } catch (Exception ignored) {}

        // Fallback: construir nombre por plataforma
        String plataforma = detectarPlataforma();
        return switch (plataforma) {
            case "windows" -> "Kipu-" + version + ".exe";
            case "macos" -> "Kipu-" + version + ".dmg";
            default -> "kipu_" + version + "_amd64.deb";
        };
    }

    /** Resuelve la ruta del ejecutable de Kipu según la plataforma actual. */
    private String resolverEjecutable() {
        String plataforma = detectarPlataforma();
        return switch (plataforma) {
            case "linux" -> "/opt/kipu/cliente/bin/Kipu";
            case "windows" -> resolverEjecutableWindows();
            case "macos" -> "/Applications/Kipu.app/Contents/MacOS/Kipu";
            default -> null;
        };
    }

    /** Busca el ejecutable de Kipu en las rutas estándar de Windows. */
    private String resolverEjecutableWindows() {
        // Ruta estándar de Inno Setup: Program Files\Kipu\Cliente\Kipu.exe
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            Path candidato = Path.of(programFiles, "Kipu", "Cliente", "Kipu.exe");
            if (Files.exists(candidato)) return candidato.toString();
        }
        // Fallback: LocalAppData (instalación por usuario)
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Path candidato = Path.of(localAppData, "Kipu", "Cliente", "Kipu.exe");
            if (Files.exists(candidato)) return candidato.toString();
        }
        // Último recurso: junto al JAR ejecutándose
        Path dirActual = Path.of(System.getProperty("user.dir"));
        Path candidato = dirActual.resolve("Kipu.exe");
        if (Files.exists(candidato)) return candidato.toString();
        return null;
    }

    private String detectarPlataforma() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) return "linux";
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        return "linux";
    }

    /** Lee la versión desde app.properties (filtrado por Maven). */
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

    /** Comparación semántica: true si {@code remota} > {@code local}. */
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

    /** Extrae mensaje de la cadena de causas cuando getMessage() es null. */
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
