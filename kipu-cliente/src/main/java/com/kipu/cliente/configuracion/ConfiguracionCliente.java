/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.configuracion;

import com.kipu.common.constantes.Constantes;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/* Configuración global del cliente Kipu. Esta clase centraliza la gestión de la configuración del cliente, incluyendo:
- URL del servidor REST al que se conecta el cliente
- Token JWT de autenticación del usuario actual
- Configuración de host mode (si el cliente arranca el servidor embebido)
- Configuración de gráficos (animaciones, pantalla completa)
 *Esta clase también se encarga de cargar y guardar la configuración en un archivo properties para persistencia entre sesiones.
 * 
 * Seguridad del token JWT:
 * - El token se almacena solo en memoria (variable estática) y NO se guarda en disco para evitar riesgos de seguridad.
 * - Si se cierra la aplicación, el token se pierde y el usuario debe volver a autenticarse al abrir el cliente nuevamente.
 * - El token expira en el servidor después de un tiempo definido en Constantes.Jwt.EXPIRACION_MS (24 horas), lo que añade una capa adicional de seguridad en caso de que el token sea comprometido.
 * Seguridad:
 * - El token NO se guarda en disco (mayor seguridad)
 * - Si se cierra la aplicación, el usuario debe volver a hacer login
 * - El token expira en Constantes.Jwt.EXPIRACION_MS (24 horas) en el servidor
 * Host Mode:
 * - Si hostMode es true, el cliente arranca un servidor Spring Boot embebido usando el ejecutable nativo de la carpeta servidor/ (jpackage app-image)
 * - Si hostMode es false, el cliente se conecta a un servidor externo en la URL configurada (por defecto http://localhost:8080)
 * - La configuración de host mode se persiste en el archivo properties para que se recuerde entre sesiones, permitiendo al usuario cambiar fácilmente entre modo host y modo cliente.
 * Gráficos y rendimiento:
 * - La configuración de animaciones y pantalla completa también se persiste en el archivo properties para que el usuario pueda personalizar su experiencia visual y que esta se mantenga entre sesiones.
 * - Si el usuario desactiva las animaciones, los componentes animados (FondoAnimado, LineaDivisoriaInteractiva) se muestran en modo estático sin efectos para mejorar el rendimiento en equipos más modestos o para usuarios que prefieren una interfaz más simple.
 * Uso:
 * - Para obtener la URL del servidor: ConfiguracionCliente.getUrlServidor()
 * - Para establecer el token JWT después del login: ConfiguracionCliente.setTokenJwt(token)
 * - Para obtener el token JWT para incluir en headers: ConfiguracionCliente.getTokenJwt()
 * - Para limpiar la sesión al cerrar sesión: ConfiguracionCliente.limpiarSesion()
 * - Para verificar si el host mode está activo: ConfiguracionCliente.isHostMode()  */
public class ConfiguracionCliente {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ConfiguracionCliente.class);
    private static final String NOMBRE_ARCHIVO_PROPIEDADES = "kipu-cliente.properties";
    private static String urlServidor = "http://localhost:8080";
    private static String tokenJwt;
    private static boolean hostMode = false;
    private static int puertoServidor = 8080;
    private static String rutaJarServidor = "";
    private static boolean animacionesActivas = true;
    private static boolean pantallaCompleta = true;
    private static String nombreCliente = "";
    private static String ipLocalCache;
    private static String idioma = "es";
    private static boolean tutorialConexionCompletado = false;
    private static boolean setupPostgresCompletado = false;
    private static String businessId = "";
    private static String perfilPantalla = "AUTO";
    private static double escalaManual = 1.0;

    /**
     * Obtiene la IP LAN del equipo, detectada desde las interfaces de red.
     * El resultado se cachea para evitar llamadas repetidas al sistema.
     * Esta IP se envía al servidor como header X-Client-IP para que
     * el registro de clientes no dependa de request.getRemoteAddr()
     * (que puede dar IPs incorrectas detrás de NAT/VirtualBox).
     *
     * @return IP LAN del equipo, o "127.0.0.1" si no se puede detectar
     */
    public static String getIpLocal() {
        if (ipLocalCache == null) {
            ipLocalCache = detectarIpLocal();
        }
        return ipLocalCache;
    }

    // Detecta la IP LAN real iterando las interfaces de red activas
    private static String detectarIpLocal() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> dirs = iface.getInetAddresses();
                while (dirs.hasMoreElements()) {
                    java.net.InetAddress addr = dirs.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudo detectar IP local: {}", e.getMessage());
        }
        return "127.0.0.1";
    }

    // GETTERS Y SETTERS DE GRÁFICOS Y RENDIMIENTO
    // Método para verificar si las animaciones visuales están activas (FondoAnimado, LineaDivisoriaInteractiva).
    public static boolean isAnimacionesActivas() {
        return animacionesActivas;
    }
    // Activa o desactiva las animaciones visuales. Si se desactivan, los componentes animados se muestran en modo estático sin efectos para mejorar el rendimiento.
    public static void setAnimacionesActivas(boolean activas) {
        animacionesActivas = activas;
        guardarPropiedades();
    }
    // Verifica si el cliente está configurado para iniciar en modo pantalla completa. 
    public static boolean isPantallaCompleta() {
        return pantallaCompleta;
    }

    // Configura si el cliente inicia en modo pantalla completa. Esta configuración se persiste para que se mantenga entre sesiones.
    public static void setPantallaCompleta(boolean completa) {
        pantallaCompleta = completa;
        guardarPropiedades();
    }

    // INICIALIZACIÓN ESTÁTICA
    static {
        cargarPropiedades();
    }
    
    public static String getUrlServidor() {
        return urlServidor;
    }
    
    public static void setUrlServidor(String url) {
        urlServidor = url;
    }
    
    public static String getTokenJwt() {
        return tokenJwt;
    }
    
    public static void setTokenJwt(String token) {
        tokenJwt = token;
    }
    
    public static void limpiarSesion() {
        tokenJwt = null;
    }

    // ===== GETTERS Y SETTERS DE HOST MODE =====

    // Verifica si el cliente está configurado para iniciar en host mode, lo que significa que arranca un servidor embebido en lugar de conectarse a un servidor externo. Esta configuración se persiste entre sesiones.
    public static boolean isHostMode() {
        return hostMode;
    }

    // Configura si el cliente inicia en host mode. Si se habilita, el cliente arranca un servidor Spring Boot embebido usando el ejecutable nativo de la carpeta servidor/ (jpackage app-image). Si se deshabilita, el cliente se conecta a un servidor externo en la URL configurada. Esta configuración se guarda automáticamente para que se recuerde entre sesiones.
    public static void setHostMode(boolean habilitado) {
        hostMode = habilitado;
        guardarPropiedades();
    }

    // Obtiene el puerto configurado para el servidor embebido en host mode. Este valor se persiste en la configuración para que se mantenga entre sesiones.
    public static int getPuertoServidor() {
        return puertoServidor;
    }

    // Configura el puerto para el servidor embebido en host mode. Este valor se guarda automáticamente en la configuración para que se recuerde entre sesiones. El puerto por defecto es 8080, pero el usuario puede cambiarlo si ese puerto ya está en uso en su sistema.
    public static void setPuertoServidor(int puerto) {
        puertoServidor = puerto;
        guardarPropiedades();
    }

    // Obtiene la ruta configurada al JAR del servidor para host mode. Si está vacía, el cliente intentará encontrar el JAR automáticamente en ubicaciones estándar (carpeta servidor/ dentro del app-image de jpackage). Esta configuración se persiste para que se mantenga entre sesiones.
    public static String getRutaJarServidor() {
        return rutaJarServidor;
    }

    /**
     * Obtiene el nombre del equipo cliente. Si no se ha configurado uno,
     * auto-genera uno basado en el hostname del sistema operativo.
     * Esto garantiza que cada cliente tenga un identificador único
     * incluso sin configuración explícita del usuario.
     *
     * @return Nombre del equipo, nunca vacío
     */
    public static String getNombreCliente() {
        if (nombreCliente == null || nombreCliente.isEmpty()) {
            try {
                return java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return "Cliente-" + System.getProperty("user.name", "desconocido");
            }
        }
        return nombreCliente;
    }

    public static void setNombreCliente(String nombre) {
        nombreCliente = nombre != null ? nombre.trim() : "";
        guardarPropiedades();
    }

    /**
     * Obtiene el código del idioma configurado ("es", "en", "pt").
     *
     * @return Código ISO 639-1 del idioma activo
     */
    public static String getIdioma() {
        return idioma;
    }

    /**
     * Configura el idioma del sistema. Se persiste automáticamente.
     *
     * @param codigoIdioma Código ISO 639-1 ("es", "en", "pt")
     */
    public static void setIdioma(String codigoIdioma) {
        idioma = (codigoIdioma != null && !codigoIdioma.isEmpty()) ? codigoIdioma.trim() : "es";
        guardarPropiedades();
    }

    public static boolean isTutorialConexionCompletado() {
        return tutorialConexionCompletado;
    }

    public static void setTutorialConexionCompletado(boolean completado) {
        tutorialConexionCompletado = completado;
        guardarPropiedades();
    }

    /** Indica si el asistente de PostgreSQL ya se ejecutó exitosamente en host mode. */
    public static boolean isSetupPostgresCompletado() {
        return setupPostgresCompletado;
    }

    public static void setSetupPostgresCompletado(boolean completado) {
        setupPostgresCompletado = completado;
        guardarPropiedades();
    }

    /** Nombre del negocio para la sincronización cloud (BUSINESS_ID). */
    public static String getBusinessId() {
        return businessId;
    }

    public static void setBusinessId(String id) {
        businessId = (id != null) ? id.trim() : "";
        guardarPropiedades();
    }

    /** Perfil de resolución/pantalla: "AUTO" o nombre del enum ResolucionPerfil */
    public static String getPerfilPantalla() {
        return perfilPantalla;
    }

    public static void setPerfilPantalla(String perfil) {
        perfilPantalla = (perfil != null && !perfil.isEmpty()) ? perfil.trim() : "AUTO";
        guardarPropiedades();
    }

    /** Factor de escala manual de la interfaz (0.5 – 2.0, por defecto 1.0) */
    public static double getEscalaManual() {
        return escalaManual;
    }

    public static void setEscalaManual(double escala) {
        escalaManual = Math.max(0.5, Math.min(2.0, escala));
        guardarPropiedades();
    }

    /**
     * Verifica si el host necesita el asistente de configuración inicial.
     * Retorna true solo si está en host mode, no hay .env Y no hay ninguna BD guardada.
     * Si ya existen BDs configuradas o el .env existe, el setup no es necesario.
     */
    public static boolean necesitaSetupInicial() {
        if (!hostMode) return false;
        Path envFile = java.nio.file.Paths.get(System.getProperty("user.home"), ".kipu", ".env");
        if (java.nio.file.Files.exists(envFile)) return false;
        // Si hay BDs PostgreSQL guardadas, el setup ya se hizo antes
        boolean hayBdsGuardadas = ConfiguracionBd.cargarTodas().stream()
                .anyMatch(c -> c.getTipo() != ConfiguracionBd.TipoBd.NUBE);
        if (hayBdsGuardadas) return false;
        return true;
    }

    // Cache de la URI de Atlas descifrada (se lee una sola vez)
    private static String atlasUriCache;

    /**
     * Obtiene la URI de Atlas MongoDB cifrada en el JAR en tiempo de build.
     * <p>
     * Prioridad: 1) cloud.dat cifrado en classpath (generado por Maven/CifradoNube),
     * 2) system property -Dkipu.atlas.uri (backward compat / override manual).
     *
     * @return URI del cluster, o cadena vacía si no fue embebida
     */
    public static String getAtlasUriEmbebida() {
        if (atlasUriCache != null) return atlasUriCache;

        // Intento 1: cloud.dat cifrado en classpath (producción)
        try {
            String descifrada = com.kipu.common.utilidad.CifradoNube.leerDesdeClasspath();
            if (!descifrada.isEmpty()) {
                atlasUriCache = descifrada;
                return atlasUriCache;
            }
        } catch (Exception e) {
            // Fallback silencioso
        }

        // Intento 2: system property (backward compat con jpackage anterior)
        String prop = System.getProperty("kipu.atlas.uri", "");
        if (!prop.isEmpty()) {
            atlasUriCache = prop;
            return atlasUriCache;
        }

        // Intento 3: MONGODB_URI desde ~/.kipu/.env (desarrollo / config manual)
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".kipu", ".env");
            if (java.nio.file.Files.exists(envPath)) {
                for (String linea : java.nio.file.Files.readAllLines(envPath)) {
                    linea = linea.trim();
                    if (linea.startsWith("MONGODB_URI=")) {
                        String valor = linea.substring("MONGODB_URI=".length()).trim();
                        // Quitar comillas simples o dobles si las tiene
                        if ((valor.startsWith("'") && valor.endsWith("'"))
                                || (valor.startsWith("\"") && valor.endsWith("\""))) {
                            valor = valor.substring(1, valor.length() - 1);
                        }
                        if (!valor.isEmpty()) {
                            atlasUriCache = valor;
                            return atlasUriCache;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silencioso
        }

        atlasUriCache = "";
        return atlasUriCache;
    }

    /** Invalida la caché de la URI de Atlas para forzar releer desde las fuentes. */
    public static void invalidarCacheAtlasUri() {
        atlasUriCache = null;
    }

    //PERSISTENCIA DE CONFIGURACIÓN

    /*Carga la configuración desde el archivo kipu-cliente.properties. 
     * Busca el archivo en múltiples ubicaciones (en orden de prioridad):
     * 1. $HOME/.kipu/kipu-cliente.properties (ubicación principal para instalaciones)
     * 2. Directorio de trabajo actual (desarrollo y portable)
     * 3. Directorio del JAR del cliente (jpackage app-image)*/

    private static void cargarPropiedades() {
        Path archivoProps = buscarArchivoPropiedades();
        if (archivoProps == null) {
            logger.debug("Archivo de configuración no encontrado en ninguna ubicación, usando valores por defecto");
            return;
        }

        logger.debug("Cargando propiedades desde: {}", archivoProps.toAbsolutePath());

        try (InputStream input = Files.newInputStream(archivoProps)) {
            Properties props = new Properties();
            props.load(input);

            hostMode = Boolean.parseBoolean(props.getProperty("host.mode", "false"));
            puertoServidor = Integer.parseInt(props.getProperty("servidor.puerto", "8080"));
            rutaJarServidor = props.getProperty("servidor.jar.ruta", "");

            // Gráficos y rendimiento
            animacionesActivas = Boolean.parseBoolean(props.getProperty("graficos.animaciones", "true"));
            pantallaCompleta = Boolean.parseBoolean(props.getProperty("graficos.pantalla.completa", "true"));

            // Nombre descriptivo del equipo cliente
            nombreCliente = props.getProperty("cliente.nombre", "");

            // Idioma del sistema
            idioma = props.getProperty("idioma", "es");

            // Tutorial de conexión
            tutorialConexionCompletado = Boolean.parseBoolean(props.getProperty("tutorial.conexion.completado", "false"));

            // Setup PostgreSQL completado (host mode)
            setupPostgresCompletado = Boolean.parseBoolean(props.getProperty("setup.postgres.completado", "false"));

            // Business ID para sincronización cloud
            businessId = props.getProperty("business.id", "");

            // Resolución y escalado de pantalla
            perfilPantalla = props.getProperty("pantalla.perfil", "AUTO");
            try {
                escalaManual = Double.parseDouble(props.getProperty("pantalla.escala.manual", "1.0"));
                escalaManual = Math.max(0.5, Math.min(2.0, escalaManual));
            } catch (NumberFormatException e) {
                escalaManual = 1.0;
            }

            // Si hay IP configurada y NO es host mode, usarla
            String ipConfigurada = props.getProperty("servidor.ip", "");
            if (!ipConfigurada.isEmpty() && !hostMode) {
                urlServidor = "http://" + ipConfigurada + ":" + puertoServidor;
            } else if (hostMode) {
                urlServidor = "http://localhost:" + puertoServidor;
            }

            logger.info("Configuración cargada desde {}: hostMode={}, puerto={}, url={}", 
                    archivoProps, hostMode, puertoServidor, urlServidor);
        } catch (IOException e) {
            logger.warn("Error leyendo configuración, usando valores por defecto: {}", e.getMessage());
        } catch (NumberFormatException e) {
            logger.warn("Puerto inválido en configuración, usando 8080 por defecto");
            puertoServidor = 8080;
        }
    }

    // Busca el archivo de propiedades en múltiples ubicaciones estándar y devuelve la ruta si se encuentra, o null si no se encuentra en ninguna ubicación.
    private static Path buscarArchivoPropiedades() {
        Path homeKipu = Paths.get(System.getProperty("user.home"), ".kipu", NOMBRE_ARCHIVO_PROPIEDADES);
        if (Files.exists(homeKipu)) {
            return homeKipu;
        }
        
        Path cwd = Paths.get(NOMBRE_ARCHIVO_PROPIEDADES);
        if (Files.exists(cwd)) {
            return cwd;
        }

        try {
            var codeSource = ConfiguracionCliente.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path jarDir = Paths.get(codeSource.getLocation().toURI());
                if (Files.isRegularFile(jarDir)) {
                    jarDir = jarDir.getParent();
                }
                Path enJarDir = jarDir.resolve(NOMBRE_ARCHIVO_PROPIEDADES);
                if (Files.exists(enJarDir)) {
                    return enJarDir;
                }
                Path enPadre = jarDir.getParent().resolve(NOMBRE_ARCHIVO_PROPIEDADES);
                if (enPadre != null && Files.exists(enPadre)) {
                    return enPadre;
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudo buscar propiedades desde code source: {}", e.getMessage());
        }

        return null;
    }

    /* Guarda la configuración actual en un archivo properties para persistencia entre sesiones. 
     * La configuración se guarda en $HOME/.kipu/kipu-cliente.properties para instalaciones, o en el directorio de trabajo actual para desarrollo/portable.
     * Si no se puede crear el directorio o escribir el archivo, se registra un error pero no se lanza una excepción para evitar interrumpir la aplicación. */

    private static void guardarPropiedades() {
        Properties props = new Properties();
        props.setProperty("host.mode", String.valueOf(hostMode));
        props.setProperty("servidor.puerto", String.valueOf(puertoServidor));
        props.setProperty("servidor.jar.ruta", rutaJarServidor);
        props.setProperty("graficos.animaciones", String.valueOf(animacionesActivas));
        props.setProperty("graficos.pantalla.completa", String.valueOf(pantallaCompleta));

        // Nombre descriptivo del equipo cliente
        if (nombreCliente != null && !nombreCliente.isEmpty()) {
            props.setProperty("cliente.nombre", nombreCliente);
        }

        // Idioma del sistema
        props.setProperty("idioma", idioma != null ? idioma : "es");

        // Tutorial de conexión
        props.setProperty("tutorial.conexion.completado", String.valueOf(tutorialConexionCompletado));

        // Setup PostgreSQL completado
        props.setProperty("setup.postgres.completado", String.valueOf(setupPostgresCompletado));

        // Business ID para cloud sync
        if (businessId != null && !businessId.isEmpty()) {
            props.setProperty("business.id", businessId);
        }

        // Resolución y escalado de pantalla
        props.setProperty("pantalla.perfil", perfilPantalla != null ? perfilPantalla : "AUTO");
        props.setProperty("pantalla.escala.manual", String.valueOf(escalaManual));

        // Extraer IP de la URL actual (sin http:// y sin :puerto)
        String ip = urlServidor.replace("http://", "").replaceAll(":\\d+$", "");
        if (!"localhost".equals(ip)) {
            props.setProperty("servidor.ip", ip);
        }

        // Guardar siempre en $HOME/.kipu/ para instalaciones, y en el directorio de trabajo para desarrollo/portable
        Path dirKipu = Paths.get(System.getProperty("user.home"), ".kipu");
        try {
            Files.createDirectories(dirKipu);
        } catch (IOException e) {
            logger.error("No se pudo crear directorio de configuración {}: {}", dirKipu, e.getMessage());
            return;
        }

        Path archivoProps = dirKipu.resolve(NOMBRE_ARCHIVO_PROPIEDADES);
        try (OutputStream output = Files.newOutputStream(archivoProps)) {
            props.store(output, "Configuración del cliente Kipu - Generado automáticamente");
            logger.info("Configuración guardada en {}", archivoProps.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Error guardando configuración: {}", e.getMessage());
        }
    }
}
