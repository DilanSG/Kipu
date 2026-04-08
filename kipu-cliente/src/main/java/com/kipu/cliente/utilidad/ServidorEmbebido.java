/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Gestor del servidor Spring Boot embebido en el cliente (Host Mode).
 * 
 * Esta clase permite que un cliente JavaFX actúe como "host" del bar,
 * arrancando automáticamente el servidor Spring Boot como un proceso
 * independiente al iniciar la aplicación.
 * 
 * Estrategia de lanzamiento:
 * El servidor se distribuye como una carpeta portable generada por jpackage
 * (app-image) que incluye su propio JRE embebido. El cliente busca el
 * ejecutable nativo del servidor ({@code bin/KipuServidor} en Linux,
 * {@code KipuServidor.exe} en Windows) en la carpeta {@code servidor/}
 * ubicada junto a la instalación del cliente o en rutas conocidas por SO.
 * 
 * Esto elimina la necesidad de tener Java/JDK instalado en el sistema:
 * tanto el cliente como el servidor traen su propio runtime.
 * 
 * Persistencia del servidor:
 * - El servidor se lanza como proceso hijo, pero NO se destruye al cerrar el cliente
 * - Si el cliente se cierra y se vuelve a abrir, detecta el servidor ya activo
 *   mediante un health check y se conecta sin lanzar otro proceso
 * - El servidor solo se detiene al apagar/reiniciar el equipo
 * 
 * Flujo de operación:
 * 1. El cliente detecta que está en "host mode" (configuración local)
 * 2. Hace un health check para ver si el servidor ya está corriendo
 * 3a. Si ya está corriendo: se conecta directamente (sin lanzar nuevo proceso)
 * 3b. Si no está corriendo: busca el ejecutable nativo del servidor y lo lanza
 * 4. Espera a que el health check responda OK (máx. 60 segundos)
 * 5. Configura la URL del servidor como localhost:puerto
 * 
 * Variables de entorno:
 * El servidor necesita las variables de configuración de BD (DB_HOST, DB_PORT,
 * DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET). Estas se cargan desde el archivo
 * {@code ~/.kipu/.env} que genera el script {@code setup-inicial.sh/.bat}.
 * El proceso del servidor hereda estas variables de entorno del cliente.
 * 
 * @see ConfiguracionCliente para la configuración de host mode
 */
public class ServidorEmbebido {

    private static final Logger logger = LoggerFactory.getLogger(ServidorEmbebido.class);

    /**
     * Tiempo máximo en segundos para esperar que el servidor arranque
     * y responda al health check. 60 segundos es conservador para
     * hardware de gama baja con disco HDD.
     */
    private static final int TIMEOUT_ARRANQUE_SEGUNDOS = 60;

    /**
     * Intervalo en milisegundos entre cada intento de health check
     * durante el arranque del servidor.
     */
    private static final int INTERVALO_HEALTH_CHECK_MS = 2000;

    /**
     * Puerto por defecto del servidor Spring Boot.
     * Puede ser sobreescrito por configuración.
     */
    private static final int PUERTO_DEFECTO = 8080;

    /**
     * Nombre del ejecutable nativo del servidor generado por jpackage.
     * En Linux no tiene extensión; en Windows se agrega .exe.
     */
    private static final String NOMBRE_EJECUTABLE_SERVIDOR = "KipuServidor";

    /**
     * Nombre de la carpeta que contiene el servidor portable.
     * Debe coincidir con la estructura generada por package.sh.
     */
    private static final String NOMBRE_CARPETA_SERVIDOR = "servidor";

    /**
     * Referencia al proceso del servidor en ejecución.
     * null si el servidor no está corriendo o no se usa host mode.
     */
    private static final AtomicReference<Process> procesoServidor = new AtomicReference<>(null);

    /**
     * Indica si el servidor fue iniciado por este cliente en esta sesión.
     */
    private static final AtomicBoolean servidorIniciado = new AtomicBoolean(false);

    /**
     * Indica si el servidor ya estaba corriendo cuando el cliente arrancó.
     */
    private static final AtomicBoolean servidorPreexistente = new AtomicBoolean(false);

    /**
     * Hilo que consume los logs del subproceso del servidor.
     */
    private static Thread hiloLogs;

    /**
     * Inicia el servidor Spring Boot como subproceso de forma asíncrona.
     * 
     * Proceso:
     * 1. Verifica si el servidor ya está corriendo (health check)
     * 2. Busca el ejecutable nativo del servidor en rutas conocidas
     * 3. Carga las variables de entorno desde ~/.kipu/.env
     * 4. Lanza el proceso y comienza a consumir sus logs
     * 5. Espera al health check en un loop con timeout
     * 
     * @param callbackProgreso Consumer que recibe mensajes de progreso para la UI.
     *                         El callback debe usar Platform.runLater() si actualiza la UI.
     * @return CompletableFuture que se completa con true si el servidor arrancó,
     *         o falla con excepción si no pudo arrancar
     */
    public static CompletableFuture<Boolean> iniciarServidor(Consumer<String> callbackProgreso) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int puerto = ConfiguracionCliente.getPuertoServidor();

                // Paso 1: Verificar si el servidor ya está corriendo
                callbackProgreso.accept("Verificando servidor existente...");
                if (verificarHealthCheck(puerto)) {
                    logger.info("Servidor ya activo detectado en puerto {} — reutilizando", puerto);
                    callbackProgreso.accept("Servidor ya activo — conectando...");
                    ConfiguracionCliente.setUrlServidor("http://localhost:" + puerto);
                    servidorPreexistente.set(true);
                    servidorIniciado.set(true);
                    return true;
                }

                // Paso 2: Buscar el ejecutable nativo del servidor
                callbackProgreso.accept("Buscando servidor...");
                Path rutaEjecutable = buscarEjecutableServidor();
                if (rutaEjecutable == null) {
                    throw new RuntimeException(
                            "No se encontró el servidor Kipu. "
                            + "Verifique que la carpeta 'servidor/' esté en la ubicación correcta. "
                            + "Consulte el README incluido en el paquete para más detalles.");
                }
                logger.info("Ejecutable del servidor encontrado: {}", rutaEjecutable);

                // Paso 3: Cargar variables de entorno y lanzar
                callbackProgreso.accept("Iniciando servidor...");
                Path dirServidor = rutaEjecutable.getParent().getParent(); // bin/X -> servidor/
                Map<String, String> variablesEnv = cargarVariablesEntorno();

                ProcessBuilder builder = new ProcessBuilder(rutaEjecutable.toString());
                builder.directory(dirServidor.toFile());
                builder.redirectErrorStream(true);

                // Inyectar variables de entorno del .env
                if (variablesEnv != null && !variablesEnv.isEmpty()) {
                    builder.environment().putAll(variablesEnv);
                    logger.info("Variables de entorno cargadas desde .env ({} variables)", variablesEnv.size());
                }

                // Inyectar Atlas URI embebida en la JVM del cliente al servidor
                // Esta URI viene como -Dkipu.atlas.uri=... en el jpackage del cliente
                // y NUNCA se escribe a disco para protegerla de distribución.
                String atlasUri = ConfiguracionCliente.getAtlasUriEmbebida();
                if (!atlasUri.isEmpty()) {
                    builder.environment().put("MONGODB_URI", atlasUri);
                    logger.info("Atlas URI inyectada al servidor desde propiedad embebida");
                }

                Process proceso = builder.start();
                procesoServidor.set(proceso);
                servidorIniciado.set(true);

                // Paso 4: Consumir logs del servidor en hilo separado
                iniciarConsumoLogs(proceso);

                // Paso 5: Esperar a que el servidor responda
                callbackProgreso.accept("Esperando respuesta del servidor...");
                boolean exito = esperarArranque(puerto, callbackProgreso);

                if (!exito) {
                    matarProceso();
                    throw new RuntimeException(
                            "El servidor no respondió después de " + TIMEOUT_ARRANQUE_SEGUNDOS + " segundos. "
                            + "Verifique que PostgreSQL esté activo y que haya ejecutado setup-inicial primero.");
                }

                // Paso 6: Configurar URL del servidor
                ConfiguracionCliente.setUrlServidor("http://localhost:" + puerto);
                callbackProgreso.accept("Servidor listo");
                logger.info("Servidor embebido iniciado exitosamente en puerto {}", puerto);

                return true;

            } catch (Exception e) {
                logger.error("Error iniciando servidor embebido", e);
                throw new RuntimeException("Error al iniciar el servidor: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Mata el proceso del servidor solo en caso de fallo durante el arranque.
     */
    private static void matarProceso() {
        Process proceso = procesoServidor.getAndSet(null);
        if (proceso == null || !proceso.isAlive()) {
            return;
        }

        logger.info("Terminando proceso de servidor fallido...");
        try {
            proceso.destroy();
            boolean terminado = proceso.waitFor(10, TimeUnit.SECONDS);
            if (!terminado) {
                logger.warn("Forzando terminación del proceso...");
                proceso.destroyForcibly();
                proceso.waitFor(5, TimeUnit.SECONDS);
            }
            logger.info("Proceso de servidor terminado");
        } catch (InterruptedException e) {
            logger.error("Interrumpido mientras se terminaba el proceso", e);
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            servidorIniciado.set(false);
            servidorPreexistente.set(false);
            if (hiloLogs != null) {
                hiloLogs.interrupt();
                hiloLogs = null;
            }
        }
    }

    /**
     * Limpia recursos del cliente sin detener el servidor.
     * Se llama al cerrar la aplicación cliente.
     */
    public static void desconectar() {
        logger.info("Desconectando del servidor embebido (el servidor permanece activo)");
        if (hiloLogs != null) {
            hiloLogs.interrupt();
            hiloLogs = null;
        }
        servidorIniciado.set(false);
        servidorPreexistente.set(false);
    }

    /**
     * Verifica si el servidor embebido está corriendo.
     */
    public static boolean estaActivo() {
        Process proceso = procesoServidor.get();
        return proceso != null && proceso.isAlive();
    }

    /**
     * Verifica si este cliente está conectado al servidor en host mode.
     */
    public static boolean esHost() {
        return servidorIniciado.get();
    }

    /**
     * Verifica si el servidor ya estaba corriendo cuando el cliente arrancó.
     */
    public static boolean esServidorPreexistente() {
        return servidorPreexistente.get();
    }

    /**
     * Marca el servidor como conectado (preexistente) sin haberlo lanzado.
     * Se usa cuando el PanelConexionRed detecta un servidor ya activo.
     */
    public static void marcarConectado() {
        servidorPreexistente.set(true);
        servidorIniciado.set(true);
    }

    /**
     * Retorna el PID del proceso del servidor si fue lanzado por este cliente, o -1.
     */
    public static long obtenerPid() {
        Process proceso = procesoServidor.get();
        if (proceso != null && proceso.isAlive()) {
            try {
                return proceso.pid();
            } catch (UnsupportedOperationException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Detiene el servidor embebido de forma controlada.
     * Si el servidor fue lanzado por este cliente, mata el proceso directamente.
     * Si era preexistente, intenta detenerlo buscando el proceso por puerto.
     *
     * @return CompletableFuture que se completa con true si se detuvo exitosamente
     */
    public static CompletableFuture<Boolean> detenerServidor() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Process proceso = procesoServidor.get();
                if (proceso != null && proceso.isAlive()) {
                    logger.info("Deteniendo servidor embebido (proceso propio)...");
                    proceso.destroy();
                    boolean terminado = proceso.waitFor(15, TimeUnit.SECONDS);
                    if (!terminado) {
                        logger.warn("Forzando terminación del servidor...");
                        proceso.destroyForcibly();
                        proceso.waitFor(5, TimeUnit.SECONDS);
                    }
                    procesoServidor.set(null);
                } else if (servidorPreexistente.get()) {
                    // Servidor preexistente: buscar PID por puerto y matarlo
                    logger.info("Deteniendo servidor preexistente...");
                    int puerto = ConfiguracionCliente.getPuertoServidor();
                    matarProcesoPorPuerto(puerto);
                } else {
                    logger.info("No hay servidor activo que detener");
                    return false;
                }

                // Limpiar estado
                servidorIniciado.set(false);
                servidorPreexistente.set(false);
                if (hiloLogs != null) {
                    hiloLogs.interrupt();
                    hiloLogs = null;
                }

                // Verificar que realmente se detuvo
                int puerto = ConfiguracionCliente.getPuertoServidor();
                Thread.sleep(1000);
                boolean sigueActivo = verificarHealthCheck(puerto);
                if (sigueActivo) {
                    logger.warn("El servidor sigue respondiendo después del intento de detención");
                    return false;
                }

                logger.info("Servidor detenido exitosamente");
                return true;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrumpido durante la detención del servidor", e);
                return false;
            } catch (Exception e) {
                logger.error("Error deteniendo servidor: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Busca procesos Java del servidor Kipu en el sistema operativo.
     * En Linux usa "ps aux | grep java", en Windows usa "wmic" o "tasklist".
     * Retorna una lista con info de cada proceso encontrado: [PID, comando].
     */
    public static CompletableFuture<List<String[]>> buscarProcesosServidor() {
        return CompletableFuture.supplyAsync(() -> {
            List<String[]> encontrados = new ArrayList<>();
            boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

            try {
                ProcessBuilder pb;
                if (esWindows) {
                    pb = new ProcessBuilder("wmic", "process", "where",
                            "name like '%java%'", "get", "processid,commandline", "/format:list");
                } else {
                    pb = new ProcessBuilder("bash", "-c",
                            "ps aux | grep -i java | grep -v grep");
                }
                pb.redirectErrorStream(true);
                Process proceso = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proceso.getInputStream()))) {
                    String linea;

                    if (esWindows) {
                        String pid = null;
                        String cmd = null;
                        while ((linea = reader.readLine()) != null) {
                            linea = linea.trim();
                            if (linea.startsWith("CommandLine=")) {
                                cmd = linea.substring("CommandLine=".length());
                            } else if (linea.startsWith("ProcessId=")) {
                                pid = linea.substring("ProcessId=".length());
                            }
                            if (pid != null && cmd != null) {
                                if (esProcesoServidor(cmd)) {
                                    encontrados.add(new String[]{pid, cmd});
                                }
                                pid = null;
                                cmd = null;
                            }
                        }
                    } else {
                        while ((linea = reader.readLine()) != null) {
                            if (esProcesoServidor(linea)) {
                                String[] partes = linea.trim().split("\\s+", 11);
                                if (partes.length >= 2) {
                                    String pid = partes[1];
                                    String cmd = partes.length >= 11 ? partes[10] : linea;
                                    encontrados.add(new String[]{pid, cmd});
                                }
                            }
                        }
                    }
                }

                proceso.waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Error buscando procesos del servidor: {}", e.getMessage());
            }

            logger.info("Procesos de servidor Kipu encontrados: {}", encontrados.size());
            return encontrados;
        });
    }

    /**
     * Determina si una línea de proceso corresponde al servidor Kipu.
     */
    private static boolean esProcesoServidor(String linea) {
        if (linea == null) return false;
        String lower = linea.toLowerCase();
        return (lower.contains("kipu") && lower.contains("servidor"))
                || lower.contains("kipu-servidor")
                || lower.contains("kipuservidor");
    }

    /**
     * Mata un proceso por su PID.
     *
     * @return true si el comando se ejecutó sin errores
     */
    public static boolean matarProcesoPorPid(String pid) {
        boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            // Validar que el PID sea numérico
            if (!pid.matches("\\d+")) {
                logger.warn("PID inválido: {}", pid);
                return false;
            }

            ProcessBuilder pb;
            if (esWindows) {
                pb = new ProcessBuilder("taskkill", "/PID", pid, "/F");
            } else {
                pb = new ProcessBuilder("kill", "-15", pid);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean terminado = p.waitFor(10, TimeUnit.SECONDS);

            if (terminado && p.exitValue() == 0) {
                logger.info("Proceso {} terminado exitosamente", pid);

                // Limpiar estado si coincide con nuestro proceso
                servidorIniciado.set(false);
                servidorPreexistente.set(false);
                return true;
            } else {
                // Intentar SIGKILL en Linux
                if (!esWindows) {
                    ProcessBuilder pbForce = new ProcessBuilder("kill", "-9", pid);
                    pbForce.redirectErrorStream(true);
                    Process pForce = pbForce.start();
                    pForce.waitFor(5, TimeUnit.SECONDS);
                    if (pForce.exitValue() == 0) {
                        logger.info("Proceso {} terminado forzosamente (SIGKILL)", pid);
                        servidorIniciado.set(false);
                        servidorPreexistente.set(false);
                        return true;
                    }
                }
                logger.warn("No se pudo matar el proceso {}", pid);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error matando proceso {}: {}", pid, e.getMessage());
            return false;
        }
    }

    /**
     * Busca y mata el proceso que escucha en el puerto dado.
     * Usa herramientas del SO (fuser en Linux, netstat+taskkill en Windows).
     */
    private static void matarProcesoPorPuerto(int puerto) {
        boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            if (esWindows) {
                // Windows: buscar PID con netstat y matarlo con taskkill
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c",
                        "for /f \"tokens=5\" %a in ('netstat -aon ^| findstr :" + puerto + "') do taskkill /PID %a /F");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor(10, TimeUnit.SECONDS);
            } else {
                // Linux/macOS: usar fuser para matar el proceso en el puerto
                ProcessBuilder pb = new ProcessBuilder("fuser", "-k", puerto + "/tcp");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.warn("No se pudo matar el proceso por puerto {}: {}", puerto, e.getMessage());
        }
    }

    // ============================================
    // MÉTODOS PRIVADOS
    // ============================================

    /**
     * Busca el ejecutable nativo del servidor en rutas conocidas por SO.
     * 
     * El servidor se distribuye como carpeta portable (jpackage app-image)
     * con la estructura: servidor/bin/KipuServidor (Linux) o
     * servidor/KipuServidor.exe (Windows).
     * 
     * Orden de búsqueda:
     * 1. Ruta configurada explícitamente por el usuario
     * 2. Carpeta hermana 'servidor/' junto a la instalación del cliente
     * 3. Rutas estándar por sistema operativo
     * 4. Directorio de trabajo actual (desarrollo)
     * 
     * @return Path al ejecutable encontrado, o null si no existe
     */
    private static Path buscarEjecutableServidor() {
        boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String nombreBin = esWindows
                ? NOMBRE_EJECUTABLE_SERVIDOR + ".exe"
                : NOMBRE_EJECUTABLE_SERVIDOR;

        // Subdirectorio donde jpackage pone el ejecutable
        // Linux/Mac: servidor/bin/KipuServidor
        // Windows:   servidor/KipuServidor.exe (en raíz del app-image)
        String rutaRelativa = esWindows
                ? nombreBin
                : "bin" + File.separator + nombreBin;

        // Ruta configurada explícitamente
        String rutaConfigurada = ConfiguracionCliente.getRutaJarServidor();
        if (rutaConfigurada != null && !rutaConfigurada.isEmpty()) {
            Path ruta = Paths.get(rutaConfigurada);
            if (Files.isExecutable(ruta)) {
                return ruta;
            }
            // Si es un directorio (la carpeta servidor/), buscar el ejecutable dentro
            if (Files.isDirectory(ruta)) {
                Path ejecutable = ruta.resolve(rutaRelativa);
                if (Files.isExecutable(ejecutable)) {
                    return ejecutable;
                }
            }
            logger.warn("Ruta configurada no válida: {}", rutaConfigurada);
        }

        // Directorio de la aplicación (code source del JAR/clase)
        Path dirApp = obtenerDirectorioAplicacion();
        // Directorio de trabajo actual
        Path dirActual = Paths.get("").toAbsolutePath();

        logger.debug("Buscando servidor. AppDir: {}, CWD: {}", dirApp, dirActual);

        // Lista de carpetas candidatas donde puede estar servidor/
        List<Path> candidatas = new ArrayList<>();

        // Rutas relativas a la instalación del cliente (jpackage)
        if (dirApp != null) {
            // El cliente instalado vive en lib/app/ dentro de su app-image
            // servidor/ puede estar junto a la raíz de instalación o un nivel arriba
            candidatas.add(dirApp.resolve("../../" + NOMBRE_CARPETA_SERVIDOR));
            candidatas.add(dirApp.resolve("../" + NOMBRE_CARPETA_SERVIDOR));
            candidatas.add(dirApp.resolve(NOMBRE_CARPETA_SERVIDOR));
            // Si servidor/ está junto al directorio de instalación del cliente
            // Ej: /opt/kipu/cliente/kipuCliente/ y /opt/kipu/servidor/
            candidatas.add(dirApp.resolve("../../../" + NOMBRE_CARPETA_SERVIDOR));
        }

        // Rutas relativas al CWD (desarrollo y portable)
        candidatas.add(dirActual.resolve(NOMBRE_CARPETA_SERVIDOR));
        candidatas.add(dirActual.resolve("dist/" + NOMBRE_CARPETA_SERVIDOR));
        candidatas.add(dirActual.resolve("../" + NOMBRE_CARPETA_SERVIDOR));

        // === Rutas estándar por sistema operativo ===
        if (esWindows) {
            // Windows: C:\Kipu\servidor\ o C:\Program Files\Kipu\servidor\
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                candidatas.add(Paths.get(programFiles, "Kipu", NOMBRE_CARPETA_SERVIDOR));
            }
            candidatas.add(Paths.get("C:\\Kipu", NOMBRE_CARPETA_SERVIDOR));
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                candidatas.add(Paths.get(localAppData, "Kipu", NOMBRE_CARPETA_SERVIDOR));
            }
        } else {
            // Linux: /opt/kipu/servidor/
            candidatas.add(Paths.get("/opt/kipu", NOMBRE_CARPETA_SERVIDOR));
            candidatas.add(Paths.get("/opt/kipu/cliente", NOMBRE_CARPETA_SERVIDOR));
            // macOS: /Applications/Kipu/servidor/
            candidatas.add(Paths.get("/Applications/Kipu", NOMBRE_CARPETA_SERVIDOR));
            // Home del usuario
            String home = System.getProperty("user.home");
            if (home != null) {
                candidatas.add(Paths.get(home, "kipu", NOMBRE_CARPETA_SERVIDOR));
                candidatas.add(Paths.get(home, "Kipu", NOMBRE_CARPETA_SERVIDOR));
            }
        }

        // Buscar el ejecutable en cada carpeta candidata
        for (Path carpeta : candidatas) {
            Path ejecutable = carpeta.resolve(rutaRelativa).normalize();
            if (Files.isExecutable(ejecutable)) {
                logger.info("Servidor encontrado en: {}", ejecutable);
                return ejecutable;
            }
        }

        logger.error("No se encontró el ejecutable del servidor en ninguna ruta conocida");
        return null;
    }

    /**
     * Obtiene el directorio donde está instalada la aplicación cliente.
     * 
     * @return directorio del JAR del cliente, o null si no se puede determinar
     */
    private static Path obtenerDirectorioAplicacion() {
        try {
            var codeSource = ServidorEmbebido.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path jarPath = Paths.get(codeSource.getLocation().toURI());
                if (Files.isRegularFile(jarPath)) {
                    return jarPath.getParent();
                }
                return jarPath;
            }
        } catch (Exception e) {
            logger.debug("No se pudo determinar el directorio de la aplicación: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Carga las variables de entorno desde el archivo ~/.kipu/.env.
     * 
     * Este archivo se genera con el script setup-inicial.sh/.bat y contiene
     * las credenciales de PostgreSQL y el JWT secret. El servidor Spring Boot
     * las lee como variables de entorno para su application.yml.
     * 
     * @return mapa con las variables de entorno, o null si no se encontró .env
     */
    private static Map<String, String> cargarVariablesEntorno() {
        // Buscar .env en ubicaciones conocidas
        Path[] candidatas = {
                Paths.get(System.getProperty("user.home"), ".kipu", ".env"),
                Paths.get("").toAbsolutePath().resolve(".env"),
        };

        for (Path rutaEnv : candidatas) {
            if (Files.exists(rutaEnv)) {
                try {
                    Map<String, String> variables = new java.util.HashMap<>();
                    List<String> lineas = Files.readAllLines(rutaEnv);
                    for (String linea : lineas) {
                        linea = linea.trim();
                        // Ignorar comentarios y líneas vacías
                        if (linea.isEmpty() || linea.startsWith("#")) {
                            continue;
                        }
                        int separador = linea.indexOf('=');
                        if (separador > 0) {
                            String clave = linea.substring(0, separador).trim();
                            String valor = linea.substring(separador + 1).trim();
                            // Remover comillas envolventes (simples o dobles)
                            if (valor.length() >= 2
                                    && ((valor.startsWith("'") && valor.endsWith("'"))
                                    || (valor.startsWith("\"") && valor.endsWith("\"")))) {
                                valor = valor.substring(1, valor.length() - 1);
                            }
                            variables.put(clave, valor);
                        }
                    }
                    logger.info("Variables de entorno cargadas desde: {}", rutaEnv);
                    return variables;
                } catch (IOException e) {
                    logger.warn("Error leyendo .env desde {}: {}", rutaEnv, e.getMessage());
                }
            }
        }

        logger.warn("No se encontró archivo .env — el servidor usará valores por defecto de application.yml");
        return null;
    }

    /**
     * Inicia un hilo daemon que consume y redirige los logs del subproceso
     * del servidor al sistema de logging del cliente.
     * 
     * @param proceso el proceso del servidor cuyo output se va a consumir
     */
    private static void iniciarConsumoLogs(Process proceso) {
        hiloLogs = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    logger.info("[SERVIDOR] {}", linea);
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    logger.debug("Stream de logs del servidor cerrado");
                }
            }
        }, "kipu-servidor-logs");

        hiloLogs.setDaemon(true);
        hiloLogs.start();
    }

    /**
     * Espera a que el servidor Spring Boot arranque y responda al health check.
     * 
     * @param puerto   puerto donde escucha el servidor
     * @param callback consumer para reportar progreso a la UI
     * @return true si el servidor respondió antes del timeout
     */
    private static boolean esperarArranque(int puerto, Consumer<String> callback) {
        long inicio = System.currentTimeMillis();
        long timeoutMs = TIMEOUT_ARRANQUE_SEGUNDOS * 1000L;

        while (System.currentTimeMillis() - inicio < timeoutMs) {
            // Verificar que el proceso no haya muerto
            Process proceso = procesoServidor.get();
            if (proceso != null && !proceso.isAlive()) {
                logger.error("El proceso del servidor terminó inesperadamente (código: {})", proceso.exitValue());
                callback.accept("Error: el servidor terminó inesperadamente");
                return false;
            }

            // Intentar health check
            if (verificarHealthCheck(puerto)) {
                long duracion = (System.currentTimeMillis() - inicio) / 1000;
                callback.accept("Servidor listo (" + duracion + "s)");
                return true;
            }

            // Reportar progreso
            long transcurrido = (System.currentTimeMillis() - inicio) / 1000;
            callback.accept("Iniciando servidor... (" + transcurrido + "s)");

            try {
                Thread.sleep(INTERVALO_HEALTH_CHECK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        logger.error("Timeout esperando arranque del servidor ({} segundos)", TIMEOUT_ARRANQUE_SEGUNDOS);
        return false;
    }

    /**
     * Verifica si el servidor responde al endpoint de health check.
     * 
     * @param puerto puerto donde escucha el servidor
     * @return true si el servidor responde con cualquier código HTTP
     */
    private static boolean verificarHealthCheck(int puerto) {
        try {
            URL url = URI.create("http://localhost:" + puerto + "/api/usuarios/health").toURL();
            HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
            conexion.setRequestMethod("GET");
            conexion.setConnectTimeout(3000);
            conexion.setReadTimeout(3000);

            int codigo = conexion.getResponseCode();
            conexion.disconnect();

            return codigo > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
