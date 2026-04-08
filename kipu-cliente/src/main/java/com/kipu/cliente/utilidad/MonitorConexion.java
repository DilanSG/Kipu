/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitor de conexión con el servidor Kipu.
 * 
 * Realiza health checks periódicos al servidor y expone el estado
 * de la conexión como propiedades observables de JavaFX, permitiendo
 * que cualquier vista se suscriba a los cambios y muestre un indicador visual.
 * 
 * Estados de conexión:
 * - CONECTADO (verde):    El servidor responde en menos de 1 segundo
 * - LENTO (amarillo):     El servidor responde pero tarda más de 1 segundo 
 * - DESCONECTADO (rojo):  El servidor no responde o hay error de conexión
 * 
 * Funcionamiento:
 * - Hace un GET a /api/usuarios/health cada 5 segundos
 * - Mide el tiempo de respuesta para determinar si es lento
 * - Se ejecuta en un hilo separado para no bloquear la UI
 * - Es un singleton — todas las vistas comparten la misma instancia
 * - Se puede detener y reiniciar según sea necesario
 * 
 * Uso típico:
 * 1. MonitorConexion.getInstancia().iniciar() — comienza el monitoreo
 * 2. MonitorConexion.getInstancia().crearIndicador() — crea el widget visual
 * 3. MonitorConexion.getInstancia().detener() — cuando la vista se cierra
 * 
 * @see ConfiguracionCliente para la URL del servidor
 */
public class MonitorConexion {

    private static final Logger logger = LoggerFactory.getLogger(MonitorConexion.class);

    /**
     * Intervalo entre health checks en segundos.
     * 5 segundos es un balance entre reactividad y carga de red.
     */
    private static final int INTERVALO_CHECK_SEGUNDOS = 5;

    /**
     * Umbral en milisegundos para considerar la conexión como "lenta".
     * Si el health check tarda más de esto, el indicador se pone amarillo.
     */
    private static final long UMBRAL_LENTO_MS = 1000;

    /**
     * Timeout de conexión para el health check en milisegundos.
     * Si no responde en este tiempo, se considera desconectado.
     */
    private static final int TIMEOUT_MS = 3000;

    /**
     * Instancia singleton del monitor.
     */
    private static final AtomicReference<MonitorConexion> instancia = new AtomicReference<>();

    /**
     * Estado actual de la conexión (observable desde JavaFX).
     */
    private final ObjectProperty<EstadoConexion> estadoProperty = new SimpleObjectProperty<>(EstadoConexion.DESCONECTADO);

    /**
     * Texto descriptivo del estado actual (observable desde JavaFX).
     */
    private final StringProperty textoEstadoProperty = new SimpleStringProperty(IdiomaUtil.obtener("ctrl.monitor.verificando"));

    /**
     * Timeline de JavaFX que ejecuta el check periódicamente.
     * Se usa Timeline en lugar de ScheduledExecutorService para
     * simplificar la interacción con el hilo de JavaFX.
     */
    private Timeline timeline;

    /**
     * Último tiempo de respuesta medido en ms (-1 si desconectado).
     */
    private long ultimoTiempoRespuesta = -1;

    /**
     * Enumeración de los posibles estados de conexión con el servidor.
     */
    public enum EstadoConexion {
        /** Servidor responde rápidamente (< 1s) */
        CONECTADO("#2ecc71", "ctrl.monitor.conectado"),          // Verde
        /** Servidor responde pero con demora (> 1s) */
        LENTO("#f39c12", "ctrl.monitor.lento"),          // Amarillo/ámbar
        /** Servidor no responde */
        DESCONECTADO("#e74c3c", "ctrl.monitor.desconectado");     // Rojo

        private final String color;
        private final String claveI18n;

        EstadoConexion(String color, String claveI18n) {
            this.color = color;
            this.claveI18n = claveI18n;
        }

        public String getColor() { return color; }
        public String getDescripcion() { return IdiomaUtil.obtener(claveI18n); }
    }

    /**
     * Constructor privado (singleton).
     */
    private MonitorConexion() {}

    /**
     * Obtiene la instancia singleton del monitor de conexión.
     * Thread-safe mediante AtomicReference.
     * 
     * @return la instancia única del monitor
     */
    public static MonitorConexion getInstancia() {
        MonitorConexion monitor = instancia.get();
        if (monitor == null) {
            monitor = new MonitorConexion();
            if (!instancia.compareAndSet(null, monitor)) {
                // Otro hilo creó la instancia primero, usar esa
                monitor = instancia.get();
            }
        }
        return monitor;
    }

    /**
     * Inicia el monitoreo periódico de la conexión.
     * Si ya está corriendo, no hace nada.
     * 
     * Ejecuta un health check inmediato y luego cada INTERVALO_CHECK_SEGUNDOS.
     */
    public void iniciar() {
        if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) {
            return; // Ya está corriendo
        }

        logger.debug("Iniciando monitor de conexión (intervalo: {}s)", INTERVALO_CHECK_SEGUNDOS);

        // Ejecutar un check inmediato
        ejecutarHealthCheck();

        // Programar checks periódicos
        timeline = new Timeline(
            new KeyFrame(Duration.seconds(INTERVALO_CHECK_SEGUNDOS), event -> ejecutarHealthCheck())
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    /**
     * Detiene el monitoreo periódico.
     * Seguro llamar múltiples veces.
     */
    public void detener() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
            logger.debug("Monitor de conexión detenido");
        }
    }

    /**
     * Ejecuta un health check asíncrono al servidor.
     * 
     * Mide el tiempo de ida y vuelta para determinar:
     * - < 1000ms = CONECTADO (verde)
     * - >= 1000ms = LENTO (amarillo) 
     * - Sin respuesta = DESCONECTADO (rojo)
     * 
     * Se ejecuta en CompletableFuture para no bloquear el hilo de JavaFX.
     */
    private void ejecutarHealthCheck() {
        CompletableFuture.supplyAsync(() -> {
            long inicio = System.currentTimeMillis();
            try {
                String urlBase = ConfiguracionCliente.getUrlServidor();
                URL url = URI.create(urlBase + "/api/usuarios/health").toURL();
                HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
                conexion.setRequestMethod("GET");
                conexion.setConnectTimeout(TIMEOUT_MS);
                conexion.setReadTimeout(TIMEOUT_MS);

                // Incluir JWT y nombre del equipo para mantener el registro
                // del cliente activo en RegistroClientesServicio del servidor
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conexion.setRequestProperty("Authorization", "Bearer " + token);
                }
                String nombreCliente = ConfiguracionCliente.getNombreCliente();
                if (nombreCliente != null && !nombreCliente.isEmpty()) {
                    conexion.setRequestProperty("X-Client-Name", nombreCliente);
                }

                // IP real del equipo para registro correcto a través de NAT
                String ipLocal = ConfiguracionCliente.getIpLocal();
                if (ipLocal != null && !ipLocal.isEmpty()) {
                    conexion.setRequestProperty("X-Client-IP", ipLocal);
                }

                int codigo = conexion.getResponseCode();
                conexion.disconnect();

                long duracion = System.currentTimeMillis() - inicio;
                ultimoTiempoRespuesta = duracion;

                // Cualquier respuesta HTTP indica que el servidor está vivo
                if (codigo > 0) {
                    return duracion >= UMBRAL_LENTO_MS ? EstadoConexion.LENTO : EstadoConexion.CONECTADO;
                }
                return EstadoConexion.DESCONECTADO;
            } catch (Exception e) {
                ultimoTiempoRespuesta = -1;
                return EstadoConexion.DESCONECTADO;
            }
        }).thenAcceptAsync(nuevoEstado -> {
            // Actualizar propiedades en el hilo de JavaFX
            EstadoConexion estadoAnterior = estadoProperty.get();
            estadoProperty.set(nuevoEstado);

            // Construir texto descriptivo (solo estado, sin ms)
            textoEstadoProperty.set(nuevoEstado.getDescripcion());

            // Log solo cuando cambia el estado
            if (estadoAnterior != nuevoEstado) {
                if (nuevoEstado == EstadoConexion.DESCONECTADO) {
                    logger.warn("Conexión con servidor perdida");
                } else if (nuevoEstado == EstadoConexion.LENTO) {
                    logger.warn("Conexión con servidor lenta ({}ms)", ultimoTiempoRespuesta);
                } else {
                    logger.info("Conexión con servidor restablecida ({}ms)", ultimoTiempoRespuesta);
                }
            }
        }, Platform::runLater);
    }

    /**
     * Crea un indicador visual de estado de conexión.
     * 
     * Componente reutilizable: un círculo de color + label de texto.
     * Se actualiza automáticamente al cambiar el estado de conexión.
     * 
     * Estilos:
     * - Circulo de 8px de radio con color según estado
     * - Tooltip con información detallada al hacer hover
     * - Texto opcional que se puede ocultar con CSS
     * 
     * @param mostrarTexto si true, muestra el texto del estado junto al circulo
     * @return HBox con el indicador listo para agregar a cualquier layout
     */
    public HBox crearIndicador(boolean mostrarTexto) {
        // Círculo indicador de color
        Circle circulo = new Circle(6);
        circulo.setStyle("-fx-fill: " + estadoProperty.get().getColor() + ";");

        // Tooltip con info detallada (incluye ms para referencia al hacer hover)
        Tooltip tooltip = new Tooltip(construirTextoTooltip());
        tooltip.getStyleClass().add("texto-secundario-sm");
        Tooltip.install(circulo, tooltip);

        HBox contenedor = new HBox(8);
        contenedor.setAlignment(javafx.geometry.Pos.CENTER);
        contenedor.getStyleClass().add("indicador-conexion");

        if (mostrarTexto) {
            Label labelEstado = new Label(textoEstadoProperty.get());
            labelEstado.getStyleClass().add("texto-secundario-sm");
            labelEstado.setStyle("-fx-text-fill: " + estadoProperty.get().getColor() + ";");

            // Bind reactivo: actualizar al cambiar estado
            estadoProperty.addListener((obs, viejo, nuevo) -> {
                circulo.setStyle("-fx-fill: " + nuevo.getColor() + ";");
                labelEstado.setText(textoEstadoProperty.get());
                labelEstado.setStyle("-fx-text-fill: " + nuevo.getColor() + ";");
                tooltip.setText(construirTextoTooltip());
            });

            // También actualizar el texto sin cambiar de estado
            textoEstadoProperty.addListener((obs, viejo, nuevo) -> {
                labelEstado.setText(nuevo);
                tooltip.setText(construirTextoTooltip());
            });

            contenedor.getChildren().addAll(circulo, labelEstado);
        } else {
            // Solo el círculo, sin texto
            estadoProperty.addListener((obs, viejo, nuevo) -> {
                circulo.setStyle("-fx-fill: " + nuevo.getColor() + ";");
                tooltip.setText(construirTextoTooltip());
            });

            textoEstadoProperty.addListener((obs, viejo, nuevo) -> {
                tooltip.setText(construirTextoTooltip());
            });

            contenedor.getChildren().add(circulo);
        }

        return contenedor;
    }

    // ===== Getters de propiedades observables =====

    public ObjectProperty<EstadoConexion> estadoProperty() {
        return estadoProperty;
    }

    public StringProperty textoEstadoProperty() {
        return textoEstadoProperty;
    }

    public EstadoConexion getEstado() {
        return estadoProperty.get();
    }

    public long getUltimoTiempoRespuesta() {
        return ultimoTiempoRespuesta;
    }

    /**
     * Construye texto detallado para tooltips (incluye estado + latencia en ms).
     * Ej: "Conectado (45ms)", "Conexión lenta (1200ms)", "Desconectado"
     *
     * @return texto descriptivo con latencia para tooltip
     */
    private String construirTextoTooltip() {
        EstadoConexion estado = estadoProperty.get();
        if (ultimoTiempoRespuesta >= 0 && estado != EstadoConexion.DESCONECTADO) {
            return estado.getDescripcion() + " (" + ultimoTiempoRespuesta + "ms)";
        }
        return estado.getDescripcion();
    }
}
