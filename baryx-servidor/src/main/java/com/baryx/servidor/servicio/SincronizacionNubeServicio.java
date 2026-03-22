/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.servicio;

import com.baryx.servidor.modelo.entidad.SyncColaPendiente;
import com.baryx.servidor.repositorio.*;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de sincronización unidireccional PostgreSQL → MongoDB Atlas.
 *
 * Lee registros pendientes de la cola {@code sync_cola_pendiente}
 * (poblada por triggers CDC de PostgreSQL) y los envía en batch
 * a MongoDB Atlas como documentos JSON.
 *
 * Ejecuta dos tareas programadas:
 * - Sincronización periódica (cada N minutos, configurable)
 * - Limpieza de registros sincronizados antiguos (diaria a las 06:00)
 *
 * Si no hay conexión a internet o MongoDB Atlas está caído,
 * los datos se acumulan en la cola local sin afectar la operación del POS.
 */
@Service
@RequiredArgsConstructor
public class SincronizacionNubeServicio {

    private static final Logger logger = LoggerFactory.getLogger(SincronizacionNubeServicio.class);

    private final SyncColaRepositorio syncColaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ProductoRepositorio productoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final MetodoPagoRepositorio metodoPagoRepositorio;
    private final MesaRepositorio mesaRepositorio;
    private final PedidoRepositorio pedidoRepositorio;
    private final LogCriticoRepositorio logCriticoRepositorio;
    private final ConfiguracionSistemaRepositorio configuracionRepositorio;

    @Value("${sync.nube.habilitado:false}")
    private boolean habilitado;

    @Value("${sync.nube.mongodb-uri:}")
    private String mongoUri;

    @Value("${sync.nube.business-id:negocio1}")
    private String businessId;

    @Value("${sync.nube.mongodb-db-name:}")
    private String mongoDbName;

    @Value("${sync.nube.tamano-lote:100}")
    private int tamanoLote;

    @Value("${sync.nube.dias-retencion:7}")
    private int diasRetencion;

    @Value("${sync.nube.max-intentos-por-registro:5}")
    private int maxIntentos;

    private MongoClient mongoClient;
    private MongoDatabase database;

    /** Contador de ciclos consecutivos fallidos (para alertas) */
    private final AtomicInteger ciclosFallidos = new AtomicInteger(0);

    /** Timestamp de la última sincronización exitosa */
    private volatile LocalDateTime ultimaSincronizacionExitosa;

    /** Último error registrado */
    private volatile String ultimoError;

    /** Estado cacheado de la conexión a la nube (actualizado por el ciclo de sincronización) */
    private volatile boolean conexionNubeActiva = false;

    /**
     * Tarea programada: sincroniza registros pendientes con MongoDB Atlas.
     * Solo se ejecuta si la sincronización está habilitada y configurada.
     */
    @Scheduled(fixedDelayString = "#{${sync.nube.intervalo-minutos:5} > 0 ? ${sync.nube.intervalo-minutos:5} : 1}",
               timeUnit = TimeUnit.MINUTES,
               initialDelay = 1)
    @Transactional
    public void sincronizarPendientes() {
        if (!habilitado || mongoUri == null || mongoUri.isBlank()) {
            return;
        }

        try {
            if (!conectarAMongoDB()) {
                conexionNubeActiva = false;
                int fallos = ciclosFallidos.incrementAndGet();
                if (fallos >= 3 && fallos % 3 == 0) {
                    logger.error("Sincronización con nube fallida por {} ciclos consecutivos", fallos);
                }
                return;
            }

            List<SyncColaPendiente> pendientes = syncColaRepositorio
                    .findBySincronizadoFalseAndErrorFalseOrderByFechaCreacionAsc(
                            PageRequest.of(0, tamanoLote));

            if (pendientes.isEmpty()) {
                ciclosFallidos.set(0);
                conexionNubeActiva = true;
                return;
            }

            logger.info("Sincronizando {} registros con MongoDB Atlas", pendientes.size());
            int exitosos = 0;

            for (SyncColaPendiente registro : pendientes) {
                try {
                    enviarAMongoDB(registro);
                    registro.setSincronizado(true);
                    registro.setFechaSincronizacion(LocalDateTime.now());
                    exitosos++;
                } catch (Exception e) {
                    registro.setIntentosFallidos(registro.getIntentosFallidos() + 1);
                    if (registro.getIntentosFallidos() >= maxIntentos) {
                        registro.setError(true);
                        registro.setDetalleError(truncar(e.getMessage(), 500));
                        logger.warn("Registro sync id={} marcado como error permanente: {}",
                                registro.getId(), e.getMessage());
                    }
                }
            }

            syncColaRepositorio.saveAll(pendientes);

            if (exitosos > 0) {
                ultimaSincronizacionExitosa = LocalDateTime.now();
                ultimoError = null;
                ciclosFallidos.set(0);
                conexionNubeActiva = true;
                logger.info("Sincronización completada: {}/{} registros enviados", exitosos, pendientes.size());
            }

        } catch (Exception e) {
            ultimoError = truncar(e.getMessage(), 500);
            ciclosFallidos.incrementAndGet();
            conexionNubeActiva = false;
            logger.error("Error en ciclo de sincronización con la nube: {}", e.getMessage());
        }
    }

    /**
     * Limpieza diaria: elimina registros ya sincronizados con más de N días.
     * Se ejecuta todos los días a las 06:00.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void limpiarRegistrosAntiguos() {
        if (!habilitado) {
            return;
        }

        LocalDateTime limite = LocalDateTime.now().minusDays(diasRetencion);
        long eliminados = syncColaRepositorio
                .deleteBySincronizadoTrueAndFechaSincronizacionBefore(limite);

        if (eliminados > 0) {
            logger.info("Limpieza sync: {} registros antiguos eliminados (>{} días)", eliminados, diasRetencion);
        }
    }

    // ── Métodos de consulta para el controlador de monitoreo ──

    public boolean estaHabilitado() {
        return habilitado;
    }

    /** Permite activar/desactivar la sincronización en runtime sin reiniciar el servidor. */
    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
        logger.info("Sincronización con la nube {}", habilitado ? "HABILITADA" : "DESHABILITADA");

        // Al habilitar, intentar conexión inmediata para que el estado se reporte correcto
        if (habilitado && mongoUri != null && !mongoUri.isBlank()) {
            CompletableFuture.runAsync(() -> {
                try {
                    boolean conectado = conectarAMongoDB();
                    conexionNubeActiva = conectado;
                    logger.info("Verificación inmediata tras habilitar sync: conectado={}", conectado);
                } catch (Exception e) {
                    conexionNubeActiva = false;
                    logger.warn("Error en verificación inmediata de MongoDB: {}", e.getMessage());
                }
            });
        } else if (!habilitado) {
            conexionNubeActiva = false;
        }
    }

    public LocalDateTime getUltimaSincronizacionExitosa() {
        return ultimaSincronizacionExitosa;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public long contarPendientes() {
        return syncColaRepositorio.countBySincronizadoFalseAndErrorFalse();
    }

    public long contarErrores() {
        return syncColaRepositorio.countByErrorTrue();
    }

    public String getBusinessId() {
        return businessId;
    }

    /** Retorna la URI de MongoDB enmascarada (oculta credenciales). */
    public String getMongoUriEnmascarada() {
        if (mongoUri == null || mongoUri.isBlank()) return "";
        // mongodb+srv://user:pass@cluster.mongodb.net/db → mongodb+srv://***@cluster.mongodb.net/db
        return mongoUri.replaceAll("://[^@]+@", "://***@");
    }

    /** Retorna el nombre de la base de datos MongoDB (configurable via .env o usa businessId). */
    public String getNombreBaseDatos() {
        return (mongoDbName != null && !mongoDbName.isBlank()) ? mongoDbName : businessId;
    }

    public boolean verificarConexionNube() {
        if (!habilitado || mongoUri == null || mongoUri.isBlank()) {
            return false;
        }
        return conexionNubeActiva;
    }

    /**
     * Fuerza una verificación real de conexión a MongoDB Atlas (no cacheada).
     * Incluye logging detallado de cada paso para diagnóstico.
     *
     * @return Mapa con resultado detallado: exito, latencia, etapa, error
     */
    public Map<String, Object> forzarVerificacionConexion() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        long inicio = System.currentTimeMillis();

        logger.info("=== VERIFICACIÓN FORZADA DE CONEXIÓN A MONGODB ATLAS ===");

        // Paso 1: Verificar configuración
        logger.debug("[PASO 1] Verificando configuración de sincronización...");
        logger.debug("  habilitado={}, mongoUri={}, businessId={}",
                habilitado, mongoUri != null ? "definida (" + mongoUri.length() + " chars)" : "NULL", businessId);

        if (!habilitado) {
            logger.warn("[PASO 1] FALLO: Sincronización NO está habilitada (sync.nube.habilitado=false)");
            resultado.put("exito", false);
            resultado.put("etapa", "configuracion");
            resultado.put("error", "Sincronización deshabilitada (sync.nube.habilitado=false)");
            return resultado;
        }

        if (mongoUri == null || mongoUri.isBlank()) {
            logger.warn("[PASO 1] FALLO: MONGODB_URI no está definida o está vacía");
            resultado.put("exito", false);
            resultado.put("etapa", "configuracion");
            resultado.put("error", "MONGODB_URI no definida");
            return resultado;
        }

        logger.debug("[PASO 1] OK — URI enmascarada: {}", getMongoUriEnmascarada());

        // Paso 2: Crear/reutilizar cliente MongoDB
        logger.debug("[PASO 2] Preparando cliente MongoDB...");
        try {
            // Cerrar cliente existente para forzar reconexión limpia
            if (mongoClient != null) {
                logger.debug("[PASO 2] Cerrando cliente MongoDB existente para reconexión limpia...");
                cerrarClienteMongo();
            }

            logger.debug("[PASO 2] Creando nuevo MongoClient con timeouts: connect=5s, read=10s, serverSelection=5s");
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(mongoUri))
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout(5, TimeUnit.SECONDS)
                                   .readTimeout(10, TimeUnit.SECONDS))
                    .applyToClusterSettings(builder ->
                            builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                    .build();

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(getNombreBaseDatos());
            logger.debug("[PASO 2] OK — MongoClient creado, database='{}' seleccionada", getNombreBaseDatos());

        } catch (Exception e) {
            long ms = System.currentTimeMillis() - inicio;
            logger.error("[PASO 2] FALLO al crear MongoClient ({}ms): {} — {}", ms, e.getClass().getSimpleName(), e.getMessage());
            resultado.put("exito", false);
            resultado.put("etapa", "crear_cliente");
            resultado.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            resultado.put("latenciaMs", ms);
            return resultado;
        }

        // Paso 3: Enviar ping
        logger.debug("[PASO 3] Enviando comando ping a MongoDB Atlas...");
        try {
            long pingInicio = System.currentTimeMillis();
            Document pingResult = database.runCommand(new Document("ping", 1));
            long pingMs = System.currentTimeMillis() - pingInicio;
            long totalMs = System.currentTimeMillis() - inicio;

            logger.info("[PASO 3] OK — Ping exitoso en {}ms (total: {}ms). Respuesta: {}", pingMs, totalMs, pingResult.toJson());

            conexionNubeActiva = true;
            ciclosFallidos.set(0);

            resultado.put("exito", true);
            resultado.put("etapa", "completado");
            resultado.put("latenciaMs", totalMs);
            resultado.put("pingMs", pingMs);
            resultado.put("respuestaPing", pingResult.toJson());
            return resultado;

        } catch (Exception e) {
            long totalMs = System.currentTimeMillis() - inicio;
            logger.error("[PASO 3] FALLO ping a MongoDB ({}ms): {} — {}",
                    totalMs, e.getClass().getSimpleName(), e.getMessage());
            logger.debug("[PASO 3] Stack trace completo:", e);

            conexionNubeActiva = false;

            resultado.put("exito", false);
            resultado.put("etapa", "ping");
            resultado.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            resultado.put("latenciaMs", totalMs);
            return resultado;
        }
    }

    /**
     * Respalda TODOS los datos de PostgreSQL a MongoDB Atlas.
     * Cada tabla se convierte en una colección con los mismos datos.
     *
     * @return Mapa con resultado: exito, tablas procesadas, registros totales, errores
     */
    @Transactional(readOnly = true)
    public Map<String, Object> respaldarTodosLosDatos() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        long inicio = System.currentTimeMillis();

        logger.info("=== RESPALDO COMPLETO DE DATOS A MONGODB ATLAS ===");

        if (!habilitado || mongoUri == null || mongoUri.isBlank()) {
            resultado.put("exito", false);
            resultado.put("error", "Sincronización no habilitada o URI no configurada");
            return resultado;
        }

        // Conectar a MongoDB
        if (!conectarAMongoDB()) {
            resultado.put("exito", false);
            resultado.put("error", "No se pudo conectar a MongoDB Atlas");
            return resultado;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // Mix-in para evitar recursión infinita en relaciones bidireccionales
        mapper.addMixIn(Object.class, JsonIdentityMixIn.class);

        int totalRegistros = 0;
        int tablasExitosas = 0;
        Map<String, Object> detalleTablas = new LinkedHashMap<>();
        ReplaceOptions upsert = new ReplaceOptions().upsert(true);

        // ── Usuarios ──
        try {
            List<?> datos = usuarioRepositorio.findAll();
            int count = subirColeccion(mapper, "usuarios", datos, "idUsuario", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("usuarios", count);
            logger.info("[RESPALDO] usuarios: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("usuarios", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en usuarios: {}", e.getMessage());
        }

        // ── Categorías ──
        try {
            List<?> datos = categoriaRepositorio.findAll();
            int count = subirColeccion(mapper, "categorias", datos, "idCategoria", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("categorias", count);
            logger.info("[RESPALDO] categorias: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("categorias", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en categorias: {}", e.getMessage());
        }

        // ── Productos ──
        try {
            List<?> datos = productoRepositorio.findAll();
            int count = subirColeccion(mapper, "productos", datos, "idProducto", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("productos", count);
            logger.info("[RESPALDO] productos: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("productos", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en productos: {}", e.getMessage());
        }

        // ── Métodos de pago ──
        try {
            List<?> datos = metodoPagoRepositorio.findAll();
            int count = subirColeccion(mapper, "metodos_pago", datos, "idMetodoPago", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("metodos_pago", count);
            logger.info("[RESPALDO] metodos_pago: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("metodos_pago", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en metodos_pago: {}", e.getMessage());
        }

        // ── Mesas ──
        try {
            List<?> datos = mesaRepositorio.findAll();
            int count = subirColeccion(mapper, "mesas", datos, "idMesa", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("mesas", count);
            logger.info("[RESPALDO] mesas: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("mesas", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en mesas: {}", e.getMessage());
        }

        // ── Pedidos ──
        try {
            List<?> datos = pedidoRepositorio.findAll();
            int count = subirColeccion(mapper, "pedidos", datos, "idPedido", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("pedidos", count);
            logger.info("[RESPALDO] pedidos: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("pedidos", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en pedidos: {}", e.getMessage());
        }

        // ── Logs críticos ──
        try {
            List<?> datos = logCriticoRepositorio.findAll();
            int count = subirColeccion(mapper, "logs_criticos", datos, "idLog", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("logs_criticos", count);
            logger.info("[RESPALDO] logs_criticos: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("logs_criticos", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en logs_criticos: {}", e.getMessage());
        }

        // ── Configuración del sistema ──
        try {
            List<?> datos = configuracionRepositorio.findAll();
            int count = subirColeccion(mapper, "configuracion_sistema", datos, "idConfiguracion", upsert);
            totalRegistros += count;
            tablasExitosas++;
            detalleTablas.put("configuracion_sistema", count);
            logger.info("[RESPALDO] configuracion_sistema: {} registros", count);
        } catch (Exception e) {
            detalleTablas.put("configuracion_sistema", "ERROR: " + e.getMessage());
            logger.error("[RESPALDO] Error en configuracion_sistema: {}", e.getMessage());
        }

        long totalMs = System.currentTimeMillis() - inicio;
        resultado.put("exito", true);
        resultado.put("tablasExitosas", tablasExitosas);
        resultado.put("totalRegistros", totalRegistros);
        resultado.put("tiempoMs", totalMs);
        resultado.put("baseDatos", getNombreBaseDatos());
        resultado.put("businessId", businessId);
        resultado.put("detalle", detalleTablas);
        resultado.put("fechaRespaldo", LocalDateTime.now());

        logger.info("=== RESPALDO COMPLETADO: {} tablas, {} registros en {}ms ===",
                tablasExitosas, totalRegistros, totalMs);

        return resultado;
    }

    /**
     * Sube una lista de entidades JPA a una colección MongoDB.
     * Convierte cada entidad a JSON y hace upsert por ID compuesto.
     */
    private int subirColeccion(ObjectMapper mapper, String nombreColeccion,
                               List<?> entidades, String campoId,
                               ReplaceOptions upsert) {
        if (entidades.isEmpty()) return 0;

        MongoCollection<Document> coleccion = database.getCollection(nombreColeccion);
        int count = 0;

        for (Object entidad : entidades) {
            try {
                String json = mapper.writeValueAsString(entidad);
                Document doc = Document.parse(json);

                // ID compuesto: businessId_tabla_idRegistro
                Object idValor = doc.get(campoId);
                String idCompuesto = businessId + "_" + nombreColeccion + "_" + idVal(idValor);
                doc.put("_id", idCompuesto);
                doc.put("businessId", businessId);
                doc.put("respaldadoEn", LocalDateTime.now().toString());

                coleccion.replaceOne(
                        Filters.eq("_id", idCompuesto),
                        doc, upsert);
                count++;
            } catch (Exception e) {
                logger.warn("[RESPALDO] Error en registro de {}: {}", nombreColeccion, e.getMessage());
            }
        }
        return count;
    }

    private String idVal(Object valor) {
        return valor != null ? valor.toString() : "unknown";
    }

    // ── Métodos privados ──

    /**
     * Inicializa o verifica la conexión al cluster de MongoDB Atlas.
     * Usa inicialización lazy para no bloquear el arranque de la app.
     */
    private boolean conectarAMongoDB() {
        try {
            if (mongoClient == null) {
                crearMongoClient();
            }
            // Ping para verificar conectividad
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            logger.debug("Sin conexión a MongoDB Atlas (primer intento): {}", e.getMessage());
            // Cliente posiblemente stale — cerrar y reintentar una vez
            cerrarClienteMongo();
            try {
                crearMongoClient();
                database.runCommand(new Document("ping", 1));
                return true;
            } catch (Exception ex) {
                logger.debug("Sin conexión a MongoDB Atlas (reintento): {}", ex.getMessage());
                cerrarClienteMongo();
                return false;
            }
        }
    }

    private void crearMongoClient() {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri))
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(10, TimeUnit.SECONDS)
                               .readTimeout(30, TimeUnit.SECONDS))
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(10, TimeUnit.SECONDS))
                .build();

        mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(getNombreBaseDatos());
    }

    private void cerrarClienteMongo() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception ignored) {
            }
            mongoClient = null;
            database = null;
        }
    }

    /**
     * Envía un registro de la cola a la colección correspondiente en MongoDB.
     * Usa upsert para idempotencia (si se reenvía, sobrescribe sin duplicar).
     */
    private void enviarAMongoDB(SyncColaPendiente registro) {
        MongoCollection<Document> coleccion = database.getCollection(registro.getTablaOrigen());

        String idCompuesto = businessId + "_" + registro.getTablaOrigen()
                + "_" + registro.getIdRegistro();

        switch (registro.getOperacion()) {
            case "INSERT", "UPDATE" -> {
                Document documento = Document.parse(registro.getDatosJson());
                documento.put("_id", idCompuesto);
                documento.put("businessId", businessId);
                documento.put("sincronizadoEn", LocalDateTime.now().toString());
                documento.put("operacionOrigen", registro.getOperacion());

                coleccion.replaceOne(
                        Filters.eq("_id", idCompuesto),
                        documento,
                        new ReplaceOptions().upsert(true)
                );
            }
            case "DELETE" -> coleccion.deleteOne(Filters.eq("_id", idCompuesto));
            default -> logger.warn("Operación desconocida en sync: {}", registro.getOperacion());
        }
    }

    private String truncar(String texto, int maxLongitud) {
        if (texto == null) return null;
        return texto.length() <= maxLongitud ? texto : texto.substring(0, maxLongitud);
    }

    @PreDestroy
    public void cerrarConexion() {
        cerrarClienteMongo();
        logger.info("Conexión a MongoDB Atlas cerrada");
    }

    /** Mix-in para evitar recursión infinita en entidades con relaciones bidireccionales */
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@jsonId")
    private abstract static class JsonIdentityMixIn { }
}
