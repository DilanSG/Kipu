/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Detecta si las dependencias externas (PostgreSQL, Java) están instaladas
 * en el sistema operativo. Se usa en el asistente de configuración
 * del host para guiar al usuario en la instalación de requisitos.
 */
public class DetectorDependencias {

    private static final Logger logger = LoggerFactory.getLogger(DetectorDependencias.class);

    /** Resultado de la detección de una dependencia. */
    public record ResultadoDeteccion(boolean encontrado, String version) {
        public ResultadoDeteccion(boolean encontrado) {
            this(encontrado, "");
        }
    }

    /**
     * Detecta si PostgreSQL está instalado y accesible.
     * Busca el comando {@code psql} en el PATH del sistema.
     */
    public static ResultadoDeteccion detectarPostgresql() {
        try {
            // Intentar psql --version
            ProcessBuilder pb = new ProcessBuilder("psql", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean terminado = p.waitFor(5, TimeUnit.SECONDS);

            if (terminado && p.exitValue() == 0) {
                try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String linea = reader.readLine();
                    String version = linea != null ? linea.trim() : "desconocida";
                    String ruta = obtenerRutaComando("psql");
                    logger.info("PostgreSQL detectado: {}", version);
                    return new ResultadoDeteccion(true, version);
                }
            }
        } catch (Exception e) {
            logger.debug("psql no encontrado en PATH: {}", e.getMessage());
        }

        // Buscar en ubicaciones estándar
        String[] rutasComunes = esWindows()
                ? new String[]{"C:\\Program Files\\PostgreSQL", "C:\\Program Files (x86)\\PostgreSQL"}
                : new String[]{"/usr/lib/postgresql", "/usr/local/pgsql", "/opt/postgresql"};

        for (String ruta : rutasComunes) {
            if (Files.isDirectory(Path.of(ruta))) {
                logger.info("Directorio de PostgreSQL encontrado: {}", ruta);
                return new ResultadoDeteccion(true, "encontrado en " + ruta);
            }
        }

        logger.info("PostgreSQL no detectado en el sistema");
        return new ResultadoDeteccion(false);
    }

    /**
     * Detecta si el servicio de PostgreSQL está en ejecución.
     */
    public static boolean postgresqlEstaActivo() {
        try {
            ProcessBuilder pb;
            if (esWindows()) {
                pb = new ProcessBuilder("sc", "query", "postgresql");
            } else {
                pb = new ProcessBuilder("pg_isready", "-q");
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean terminado = p.waitFor(5, TimeUnit.SECONDS);
            return terminado && p.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("No se pudo verificar estado de PostgreSQL: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica la conectividad JDBC a una instancia de PostgreSQL.
     *
     * @param host     Hostname o IP
     * @param puerto   Puerto TCP
     * @param usuario  Usuario de PostgreSQL
     * @param password Contraseña
     * @param baseDatos Nombre de la base de datos (usar "postgres" para test inicial)
     * @return ResultadoDeteccion con la versión del servidor si conecta
     */
    public static ResultadoDeteccion verificarConexionJdbc(String host, int puerto,
                                                           String usuario, String password,
                                                           String baseDatos) {
        String url = "jdbc:postgresql://" + host + ":" + puerto + "/" + baseDatos;
        try {
            // Cargar driver explícitamente (puede no estar en host classpath)
            Class.forName("org.postgresql.Driver");
            try (var conn = java.sql.DriverManager.getConnection(url, usuario, password)) {
                var meta = conn.getMetaData();
                String version = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
                logger.info("Conexión JDBC exitosa: {} en {}:{}", version, host, puerto);
                return new ResultadoDeteccion(true, version);
            }
        } catch (ClassNotFoundException e) {
            logger.warn("Driver PostgreSQL no disponible en classpath");
            return new ResultadoDeteccion(false, "Driver no disponible");
        } catch (Exception e) {
            logger.info("Conexión JDBC fallida a {}:{} - {}", host, puerto, e.getMessage());
            return new ResultadoDeteccion(false, e.getMessage());
        }
    }

    /**
     * Ejecuta un comando SQL como superusuario contra PostgreSQL usando JDBC.
     * Útil para crear usuarios/bases de datos desde el asistente GUI.
     *
     * @return null si éxito, o el mensaje de error
     */
    public static String ejecutarSqlAdmin(String host, int puerto, String superUsuario,
                                          String superPassword, String baseDatos, String sql) {
        String url = "jdbc:postgresql://" + host + ":" + puerto + "/" + baseDatos;
        try {
            Class.forName("org.postgresql.Driver");
            try (var conn = java.sql.DriverManager.getConnection(url, superUsuario, superPassword);
                 var stmt = conn.createStatement()) {
                stmt.execute(sql);
                return null; // Éxito
            }
        } catch (Exception e) {
            logger.warn("Error ejecutando SQL en {}:{}/{}: {}", host, puerto, baseDatos, e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Genera el comando de instalación de PostgreSQL según el SO.
     */
    public static String obtenerComandoInstalacion() {
        if (esWindows()) {
            return "Descargue PostgreSQL desde: https://www.postgresql.org/download/windows/";
        }
        // Detectar distribución Linux
        try {
            if (Files.exists(Path.of("/etc/debian_version"))) {
                return "sudo apt update && sudo apt install -y postgresql postgresql-client";
            } else if (Files.exists(Path.of("/etc/redhat-release"))) {
                return "sudo dnf install -y postgresql-server postgresql && sudo postgresql-setup --initdb && sudo systemctl start postgresql";
            } else if (Files.exists(Path.of("/etc/arch-release"))) {
                return "sudo pacman -S postgresql && sudo -u postgres initdb -D /var/lib/postgres/data && sudo systemctl start postgresql";
            }
        } catch (Exception e) {
            // Ignorar
        }
        return "sudo apt install -y postgresql postgresql-client";
    }

    private static String obtenerRutaComando(String comando) {
        try {
            String which = esWindows() ? "where" : "which";
            ProcessBuilder pb = new ProcessBuilder(which, comando);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) {
                try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String linea = reader.readLine();
                    return linea != null ? linea.trim() : "";
                }
            }
        } catch (Exception e) {
            // Ignorar
        }
        return "";
    }

    private static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
