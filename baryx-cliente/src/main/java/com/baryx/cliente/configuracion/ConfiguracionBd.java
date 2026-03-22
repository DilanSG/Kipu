/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.configuracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modelo de configuración de una base de datos PostgreSQL guardada.
 * Se persiste como lista JSON en ~/.baryx/databases.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfiguracionBd {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionBd.class);
    private static final Path ARCHIVO_BDS = Paths.get(
            System.getProperty("user.home"), ".baryx", "databases.json");
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Tipo de base de datos: POSTGRESQL (local) o NUBE (MongoDB Atlas). */
    public enum TipoBd { POSTGRESQL, NUBE }

    private String id;
    private String alias;
    private String host;
    private int puerto;
    private String nombreBd;
    private String usuario;
    private String businessId;
    private String fechaCreacion;
    private boolean activa;
    private TipoBd tipo;
    private String clusterNube;

    public ConfiguracionBd() {}

    /** Constructor para BD PostgreSQL local. */
    public ConfiguracionBd(String alias, String host, int puerto, String nombreBd,
                           String usuario, String businessId) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.alias = alias;
        this.host = host;
        this.puerto = puerto;
        this.nombreBd = nombreBd;
        this.usuario = usuario;
        this.businessId = businessId;
        this.fechaCreacion = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.activa = false;
        this.tipo = TipoBd.POSTGRESQL;
    }

    /** Constructor para BD en la nube (MongoDB Atlas). */
    public ConfiguracionBd(String alias, String businessId, String clusterNube) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.alias = alias;
        this.businessId = businessId;
        this.clusterNube = clusterNube;
        this.fechaCreacion = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.activa = false;
        this.tipo = TipoBd.NUBE;
    }

    // ==================== PERSISTENCIA ====================

    /** Carga todas las configuraciones de BD guardadas en disco. */
    public static List<ConfiguracionBd> cargarTodas() {
        if (!Files.exists(ARCHIVO_BDS)) return new ArrayList<>();
        try {
            String json = Files.readString(ARCHIVO_BDS);
            if (json.isBlank()) return new ArrayList<>();
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            logger.error("Error cargando databases.json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Guarda la lista completa de configuraciones a disco. */
    public static void guardarTodas(List<ConfiguracionBd> configs) {
        try {
            Files.createDirectories(ARCHIVO_BDS.getParent());
            mapper.writeValue(ARCHIVO_BDS.toFile(), configs);
            logger.info("databases.json guardado con {} configuraciones", configs.size());
        } catch (Exception e) {
            logger.error("Error guardando databases.json: {}", e.getMessage(), e);
        }
    }

    /** Agrega una nueva configuración y la guarda. */
    public static void agregar(ConfiguracionBd config) {
        List<ConfiguracionBd> todas = cargarTodas();
        todas.add(config);
        guardarTodas(todas);
    }

    /** Marca una configuración como activa (desactiva las demás). */
    public static void activar(String id) {
        List<ConfiguracionBd> todas = cargarTodas();
        for (ConfiguracionBd c : todas) {
            c.setActiva(c.getId().equals(id));
        }
        guardarTodas(todas);
    }

    /** Elimina una configuración por ID. */
    public static void eliminar(String id) {
        List<ConfiguracionBd> todas = cargarTodas();
        todas.removeIf(c -> c.getId().equals(id));
        guardarTodas(todas);
    }

    /** Verifica si ya existe una configuración con el mismo businessId (case-insensitive). */
    public static boolean existeBusinessId(String businessId) {
        if (businessId == null || businessId.isBlank()) return false;
        String normalizado = businessId.trim().toLowerCase();
        return cargarTodas().stream()
                .anyMatch(c -> c.getBusinessId() != null
                        && c.getBusinessId().trim().toLowerCase().equals(normalizado));
    }

    // ==================== GETTERS / SETTERS ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPuerto() { return puerto; }
    public void setPuerto(int puerto) { this.puerto = puerto; }

    public String getNombreBd() { return nombreBd; }
    public void setNombreBd(String nombreBd) { this.nombreBd = nombreBd; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }

    public String getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(String fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public TipoBd getTipo() { return tipo != null ? tipo : TipoBd.POSTGRESQL; }
    public void setTipo(TipoBd tipo) { this.tipo = tipo; }

    public String getClusterNube() { return clusterNube; }
    public void setClusterNube(String clusterNube) { this.clusterNube = clusterNube; }
}
