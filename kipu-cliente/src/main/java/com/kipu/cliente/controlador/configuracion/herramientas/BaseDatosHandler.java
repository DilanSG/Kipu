/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler del modal de estado de Base de Datos.
 *
 * Muestra toda la información de conexión a la BD (host, puerto, nombre,
 * usuario, contraseña oculta con hover-reveal), estado del servidor vía
 * health check, información de migraciones, driver, ORM y pool de conexiones.
 * Incluye sección de sincronización con la nube (pendiente de implementación).
 *
 * @see GestorModales
 */
public class BaseDatosHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(BaseDatosHandler.class);

    /** Gestor de modales compartido */
    private final GestorModales gestor;

    // Referencias a labels de la card nube (para actualizar desde el botón verificar)
    private Circle indNube;
    private Label estadoNube;
    private Label valHabilitado, valConexion, valCluster, valBaseDatos;
    private Label valBusinessId, valPendientes, valErrores, valUltimaSync;

    /**
     * Crea un handler de base de datos vinculado al gestor de modales.
     *
     * @param gestor Gestor de modales que provee infraestructura visual
     */
    public BaseDatosHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    /**
     * Abre el modal de estado de Base de Datos.
     * Muestra datos de conexión, servidor API, estado de nube y botón de verificación.
     */
    @Override
    public void abrir() {
        logger.info("Abriendo estado de Base de Datos");

        VBox modal = new VBox(10);
        modal.setMaxWidth(580);
        modal.setMaxHeight(600);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header con botón de asistente BD ───
        HBox header = gestor.crearHeaderModal(IdiomaUtil.obtener("ctrl.bd.header"), "icono-cfg-basedatos");

        // Insertar botón de configuración/asistente antes del botón cerrar (✕)
        Button btnAsistente = new Button();
        btnAsistente.setGraphic(crearIconoConfiguracion());
        btnAsistente.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4;");
        btnAsistente.setOnMouseEntered(e -> btnAsistente.setStyle(
            "-fx-background-color: rgba(212,175,55,0.15); -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4;"));
        btnAsistente.setOnMouseExited(e -> btnAsistente.setStyle(
            "-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4;"));
        Tooltip.install(btnAsistente, new Tooltip(IdiomaUtil.obtener("ctrl.bd.abrir_asistente")));
        btnAsistente.setOnAction(e -> abrirAsistenteBd());
        // Insertar antes del último hijo (botón cerrar ✕)
        int idxCerrar = header.getChildren().size() - 1;
        header.getChildren().add(idxCerrar, btnAsistente);

        // ─── Card: Conexión y Datos de la BD ───
        VBox cardConexion = construirCardConexion();

        // ─── Card: Servidor API ───
        Circle indHealth = new Circle(4, Color.web("#666"));
        Label estadoHealth = new Label(IdiomaUtil.obtener("ctrl.bd.verificando_estado"));
        estadoHealth.getStyleClass().add("texto-secundario-sm");
        estadoHealth.setStyle("-fx-text-fill: #888;");
        Label valLatencia = gestor.crearInfoValor("—");
        VBox cardServidor = construirCardServidor(indHealth, estadoHealth, valLatencia);

        // ─── Card: Nube ───
        VBox cardNube = construirCardNube();

        // ─── Botón verificar (centrado, estilo método de pago, siempre dorado) ───
        Button btnVerificar = new Button(IdiomaUtil.obtener("ctrl.bd.verificar"));
        btnVerificar.getStyleClass().add("btn-metodo-pago");
        btnVerificar.setPrefWidth(260);
        btnVerificar.setPrefHeight(44);
        HBox filaBtns = new HBox(btnVerificar);
        filaBtns.setAlignment(Pos.CENTER);

        // ScrollPane para contenido extenso
        VBox contenidoModal = new VBox(10,
            gestor.crearSeparador(), cardConexion, cardServidor, cardNube, filaBtns);
        contenidoModal.setPadding(new Insets(0));
        ScrollPane scroll = new ScrollPane(contenidoModal);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; " +
            "-fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        modal.getChildren().addAll(header, scroll);
        gestor.mostrarModal(modal);

        // Health check + verificación nube automático al abrir + al pulsar botón
        Runnable verificar = () -> {
            verificarConexionBD(indHealth, estadoHealth, valLatencia, btnVerificar);
            forzarVerificacionNube(btnVerificar);
        };
        btnVerificar.setOnAction(ev -> verificar.run());
        verificar.run();
    }

    // ==================== CARDS DE INFORMACIÓN ====================

    /**
     * Construye la card de datos de conexión a la BD.
     * Incluye motor, host, nombre, usuario, contraseña con hover-reveal,
     * URL JDBC, driver, ORM, migraciones y pool.
     *
     * @return VBox con la card de conexión
     */
    private VBox construirCardConexion() {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.bd.titulo_conexion"));
        titulo.getStyleClass().add("texto-secundario-sm");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        // Datos de BD desde variables de entorno
        String dbHost = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
        String dbPort = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "5432";
        String dbName = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "kipu_db";
        String dbUser = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "kipu_admin";
        String dbPass = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : IdiomaUtil.obtener("ctrl.bd.no_definida");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(5);

        grid.addRow(0, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.motor")),
            gestor.crearInfoValor("PostgreSQL 15+"));
        grid.addRow(1, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.host")),
            gestor.crearInfoValor(dbHost + ":" + dbPort));
        grid.addRow(2, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.base_datos")),
            gestor.crearInfoValor(dbName));
        grid.addRow(3, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.usuario")),
            gestor.crearInfoValor(dbUser));

        // Contraseña oculta con hover-reveal
        String passReal = dbPass;
        String passMascara = "●".repeat(Math.min(passReal.length(), 12));
        Label lblPassword = gestor.crearInfoValor(passMascara);
        lblPassword.getStyleClass().add("texto-secundario-sm");
        lblPassword.setStyle("-fx-text-fill: #888; -fx-font-weight: 600; " +
            "-fx-cursor: hand;");
        lblPassword.setOnMouseEntered(e -> {
            lblPassword.setText(passReal);
            lblPassword.setStyle("-fx-text-fill: #e8e8e8; -fx-font-weight: 600; " +
                "-fx-cursor: hand;");
        });
        lblPassword.setOnMouseExited(e -> {
            lblPassword.setText(passMascara);
            lblPassword.setStyle("-fx-text-fill: #888; -fx-font-weight: 600; " +
                "-fx-cursor: hand;");
        });
        grid.addRow(4, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.contrasena")), lblPassword);

        grid.addRow(5, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.url")),
            gestor.crearInfoValor("jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName));
        grid.addRow(6, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.driver")),
            gestor.crearInfoValor("org.postgresql.Driver"));
        grid.addRow(7, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.orm")),
            gestor.crearInfoValor("Hibernate / JPA (validate)"));
        grid.addRow(8, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.migraciones")),
            gestor.crearInfoValor("Flyway (V1 \u2192 V5)"));
        grid.addRow(9, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.pool")),
            gestor.crearInfoValor("HikariCP (m\u00e1x 10, m\u00edn 2)"));

        card.getChildren().addAll(titulo, grid);
        return card;
    }

    /**
     * Construye la card de información del servidor API.
     * Incluye health check, URL, host, puerto, modo host, latencia
     * y JWT de sesión con hover-reveal.
     *
     * @param indHealth   Círculo indicador de estado
     * @param estadoHealth Label de estado del health check
     * @param valLatencia  Label para mostrar latencia
     * @return VBox con la card del servidor
     */
    private VBox construirCardServidor(Circle indHealth, Label estadoHealth,
                                       Label valLatencia) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.bd.titulo_servidor"));
        titulo.getStyleClass().add("texto-secundario-sm");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        HBox filaHealth = new HBox(6, indHealth, estadoHealth);
        filaHealth.setAlignment(Pos.CENTER_LEFT);

        String urlServidor = ConfiguracionCliente.getUrlServidor();
        String hostServidor = urlServidor.replace("http://", "").replaceAll(":\\d+$", "");
        int puertoApp = ConfiguracionCliente.getPuertoServidor();

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);

        grid.addRow(0, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.url_servidor")),
            gestor.crearInfoValor(urlServidor));
        grid.addRow(1, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.host_servidor")),
            gestor.crearInfoValor(hostServidor));
        grid.addRow(2, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.puerto")),
            gestor.crearInfoValor(String.valueOf(puertoApp)));
        grid.addRow(3, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.modo_host")),
            gestor.crearInfoValor(ConfiguracionCliente.isHostMode()
                ? IdiomaUtil.obtener("ctrl.red.host.si") : IdiomaUtil.obtener("ctrl.red.host.no")));
        grid.addRow(4, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.latencia")), valLatencia);

        // JWT oculto con hover-reveal
        String tokenActual = ConfiguracionCliente.getTokenJwt();
        String tokenDisplay = tokenActual != null && !tokenActual.isEmpty()
            ? tokenActual.substring(0, Math.min(8, tokenActual.length())) + "\u25cf\u25cf\u25cf\u25cf\u25cf\u25cf\u25cf\u25cf"
            : IdiomaUtil.obtener("ctrl.bd.sin_sesion");
        Label lblToken = gestor.crearInfoValor(tokenDisplay);
        lblToken.getStyleClass().add("texto-secundario-sm");
        lblToken.setStyle("-fx-text-fill: #888; -fx-font-weight: 600; " +
            "-fx-cursor: hand;");
        if (tokenActual != null && !tokenActual.isEmpty()) {
            String tokenCorto = tokenActual.length() > 40
                ? tokenActual.substring(0, 40) + "..." : tokenActual;
            lblToken.setOnMouseEntered(e -> {
                lblToken.setText(tokenCorto);
                lblToken.setStyle("-fx-text-fill: #e8e8e8; -fx-font-weight: 600; " +
                    "-fx-cursor: hand;");
            });
            lblToken.setOnMouseExited(e -> {
                lblToken.setText(tokenDisplay);
                lblToken.setStyle("-fx-text-fill: #888; -fx-font-weight: 600; " +
                    "-fx-cursor: hand;");
            });
        }
        grid.addRow(5, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.label.jwt")), lblToken);

        card.getChildren().addAll(titulo, filaHealth, grid);
        return card;
    }

    /**
     * Construye la card de sincronización con la nube.
     * Consulta /api/sync/estado para mostrar estado real de la conexión Atlas.
     *
     * @return VBox con la card de nube
     */
    private VBox construirCardNube() {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.bd.titulo_nube"));
        titulo.getStyleClass().add("texto-secundario-sm");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        Circle indNubeLocal = new Circle(4, Color.web("#666"));
        this.indNube = indNubeLocal;
        Label estadoNubeLocal = new Label(IdiomaUtil.obtener("ctrl.bd.verificando_estado"));
        estadoNubeLocal.getStyleClass().add("texto-secundario-sm");
        estadoNubeLocal.setStyle("-fx-text-fill: #888;");
        this.estadoNube = estadoNubeLocal;
        HBox filaNubeEstado = new HBox(6, indNubeLocal, estadoNubeLocal);
        filaNubeEstado.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);

        this.valHabilitado = gestor.crearInfoValor("—");
        this.valConexion = gestor.crearInfoValor("—");
        this.valConexion.getStyleClass().add("texto-secundario-sm");
        this.valCluster = gestor.crearInfoValor("—");
        this.valBaseDatos = gestor.crearInfoValor("—");
        this.valBusinessId = gestor.crearInfoValor("—");
        this.valPendientes = gestor.crearInfoValor("—");
        this.valErrores = gestor.crearInfoValor("—");
        this.valUltimaSync = gestor.crearInfoValor("—");

        grid.addRow(0, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.habilitado")), valHabilitado);
        grid.addRow(1, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.conexion")), valConexion);
        grid.addRow(2, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.cluster")), valCluster);
        grid.addRow(3, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.base_datos")), valBaseDatos);
        grid.addRow(4, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.business_id")), valBusinessId);
        grid.addRow(5, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.pendientes")), valPendientes);
        grid.addRow(6, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.errores")), valErrores);
        grid.addRow(7, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.bd.nube.label.ultima_sync")), valUltimaSync);

        card.getChildren().addAll(titulo, filaNubeEstado, grid);

        // Consultar estado async (lectura inicial, no fuerza conexión)
        consultarEstadoNube(indNubeLocal, estadoNubeLocal, valHabilitado, valConexion,
            valCluster, valBaseDatos, valBusinessId, valPendientes, valErrores, valUltimaSync);

        return card;
    }

    /**
     * Consulta /api/sync/estado asíncronamente y llena los labels de la card de nube.
     */
    private void consultarEstadoNube(Circle indNube, Label estadoNube,
                                      Label valHabilitado, Label valConexion,
                                      Label valCluster, Label valBaseDatos,
                                      Label valBusinessId, Label valPendientes,
                                      Label valErrores, Label valUltimaSync) {
        CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/sync/estado").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = reader.readLine()) != null) sb.append(linea);
                    reader.close();
                    conn.disconnect();
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> resp = mapper.readValue(
                        sb.toString(), new TypeReference<Map<String, Object>>() {});
                    Object datos = resp.get("datos");
                    if (datos instanceof Map) {
                        return (Map<String, Object>) datos;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                logger.debug("No se pudo consultar estado nube: {}", e.getMessage());
            }
            return null;
        }).thenAccept(estado -> Platform.runLater(() -> {
            if (estado == null || estado.isEmpty()) {
                indNube.setFill(Color.web("#666"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.bd.nube.no_disponible"));
                estadoNube.setStyle("-fx-text-fill: #666;");
                valHabilitado.setText("—");
                return;
            }

            boolean habilitado = Boolean.TRUE.equals(estado.get("habilitado"));
            boolean conectado = Boolean.TRUE.equals(estado.get("conexionNube"));
            String mongoUri = estado.get("mongoUri") != null ? estado.get("mongoUri").toString() : "";
            String businessId = estado.get("businessId") != null ? estado.get("businessId").toString() : "—";
            String baseDatos = estado.get("baseDatos") != null ? estado.get("baseDatos").toString() : "—";
            Object pendientes = estado.get("registrosPendientes");
            Object errores = estado.get("registrosConError");
            Object ultimaSync = estado.get("ultimaSincronizacion");
            Object ultimoError = estado.get("ultimoError");

            valHabilitado.setText(habilitado
                ? IdiomaUtil.obtener("ctrl.bd.nube.si") : IdiomaUtil.obtener("ctrl.bd.nube.no"));
            valBusinessId.setText(businessId);
            valBaseDatos.setText(baseDatos);
            valPendientes.setText(pendientes != null ? pendientes.toString() : "0");
            valErrores.setText(errores != null ? errores.toString() : "0");
            valUltimaSync.setText(ultimaSync != null ? ultimaSync.toString().replace("T", " ") : "—");

            // Extraer cluster de la URI enmascarada
            String cluster = extraerCluster(mongoUri);
            valCluster.setText(cluster.isEmpty() ? "—" : cluster);

            if (habilitado && conectado) {
                indNube.setFill(Color.web("#a8b991"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.bd.nube.conectada"));
                estadoNube.setStyle("-fx-text-fill: #a8b991;");
                valConexion.setText(IdiomaUtil.obtener("ctrl.bd.nube.activa"));
                valConexion.setStyle("-fx-text-fill: #a8b991;");
            } else if (habilitado) {
                indNube.setFill(Color.web("#daa520"));
                String msg = ultimoError != null
                    ? IdiomaUtil.obtener("ctrl.bd.nube.error_conexion")
                    : IdiomaUtil.obtener("ctrl.bd.nube.sin_conexion");
                estadoNube.setText(msg);
                estadoNube.setStyle("-fx-text-fill: #daa520;");
                valConexion.setText(IdiomaUtil.obtener("ctrl.bd.nube.inactiva"));
                valConexion.setStyle("-fx-text-fill: #daa520;");
            } else {
                indNube.setFill(Color.web("#555"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.bd.nube.deshabilitada"));
                estadoNube.setStyle("-fx-text-fill: #555;");
                valConexion.setText(IdiomaUtil.obtener("ctrl.bd.nube.no"));
                valConexion.setStyle("-fx-text-fill: #555;");
            }
        }));
    }

    /** Extrae el nombre del cluster del parámetro appName de la URI MongoDB. */
    private String extraerCluster(String mongoUri) {
        if (mongoUri == null || mongoUri.isBlank()) return "";
        int idx = mongoUri.indexOf("appName=");
        if (idx >= 0) {
            String valor = mongoUri.substring(idx + "appName=".length());
            int amp = valor.indexOf('&');
            return amp > 0 ? valor.substring(0, amp) : valor;
        }
        return "";
    }

    // ==================== VERIFICACIÓN DE CONEXIÓN ====================

    /**
     * Verifica la conexión al servidor vía health check y actualiza indicadores.
     * El botón permanece siempre dorado (el cambio de color está reservado
     * para la futura comprobación de sincronización con la nube).
     *
     * @param indHealth    Indicador visual de estado
     * @param estadoHealth Label descriptivo del estado
     * @param valLatencia  Label donde mostrar la latencia
     * @param btnVerificar Botón que se deshabilita durante la verificación
     */
    private void verificarConexionBD(Circle indHealth, Label estadoHealth,
                                     Label valLatencia, Button btnVerificar) {
        String textoOriginal = btnVerificar.getText();
        btnVerificar.setText(IdiomaUtil.obtener("ctrl.bd.verificando"));
        btnVerificar.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                long inicio = System.currentTimeMillis();
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/usuarios/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                long latencia = System.currentTimeMillis() - inicio;
                conn.disconnect();
                return new long[]{code, latencia};
            } catch (Exception ex) {
                return new long[]{0, -1};
            }
        }).thenAccept(r -> Platform.runLater(() -> {
            boolean ok = r[0] >= 200 && r[0] < 400;
            String color = ok ? "#a8b991" : "#cc4444";
            indHealth.setFill(Color.web(color));
            estadoHealth.setText(ok ? java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.bd.activo"), r[0])
                : IdiomaUtil.obtener("ctrl.bd.no_responde"));
            estadoHealth.setStyle("-fx-text-fill: " + color + ";");
            valLatencia.setText(ok ? r[1] + " ms" : "—");

            btnVerificar.setDisable(false);
            btnVerificar.setText(IdiomaUtil.obtener("ctrl.bd.verificar"));
            btnVerificar.setStyle("");
        }));
    }

    /**
     * Fuerza una verificación real de conexión a MongoDB Atlas via POST /api/sync/verificar-conexion.
     * Actualiza los labels de la card nube con el resultado detallado.
     */
    private void forzarVerificacionNube(Button btnVerificar) {
        logger.info("[NUBE-VERIFY] Iniciando verificación forzada de conexión a MongoDB Atlas...");

        // Indicar en la card que se está verificando
        Platform.runLater(() -> {
            indNube.setFill(Color.web("#daa520"));
            estadoNube.setText("Verificando conexión a Atlas...");
            estadoNube.setStyle("-fx-text-fill: #daa520;");
            valConexion.setText("verificando...");
            valConexion.setStyle("-fx-text-fill: #daa520;");
        });

        CompletableFuture.supplyAsync(() -> {
            try {
                String urlBase = ConfiguracionCliente.getUrlServidor();
                logger.info("[NUBE-VERIFY] URL servidor: {}", urlBase);

                URL url = URI.create(urlBase + "/api/sync/verificar-conexion").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(30000); // 30s — la verificación real tarda
                conn.setReadTimeout(30000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    logger.info("[NUBE-VERIFY] Token JWT configurado (longitud: {})", token.length());
                } else {
                    logger.warn("[NUBE-VERIFY] NO hay token JWT disponible");
                }

                conn.setDoOutput(true);
                conn.getOutputStream().close(); // POST vacío

                int code = conn.getResponseCode();
                logger.info("[NUBE-VERIFY] Código respuesta HTTP: {}", code);

                BufferedReader reader;
                if (code >= 200 && code < 400) {
                    reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                } else {
                    reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                }
                StringBuilder sb = new StringBuilder();
                String linea;
                while ((linea = reader.readLine()) != null) sb.append(linea);
                reader.close();
                conn.disconnect();

                String cuerpo = sb.toString();
                logger.info("[NUBE-VERIFY] Respuesta completa: {}", cuerpo);

                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> resp = mapper.readValue(
                    cuerpo, new TypeReference<Map<String, Object>>() {});
                Object datos = resp.get("datos");
                if (datos instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultado = (Map<String, Object>) datos;
                    return resultado;
                }
                logger.warn("[NUBE-VERIFY] Respuesta no contiene 'datos' como Map: {}", resp);
                return null;
            } catch (Exception ex) {
                logger.error("[NUBE-VERIFY] Error al verificar conexión nube: {}", ex.getMessage(), ex);
                return null;
            }
        }).thenAccept(resultado -> Platform.runLater(() -> {
            if (resultado == null) {
                indNube.setFill(Color.web("#cc4444"));
                estadoNube.setText("Error al contactar el servidor");
                estadoNube.setStyle("-fx-text-fill: #cc4444;");
                valConexion.setText("error");
                valConexion.setStyle("-fx-text-fill: #cc4444;");
                logger.error("[NUBE-VERIFY] No se obtuvo respuesta del servidor");
                return;
            }

            boolean exito = Boolean.TRUE.equals(resultado.get("exito"));
            String etapa = resultado.get("etapa") != null ? resultado.get("etapa").toString() : "";
            String error = resultado.get("error") != null ? resultado.get("error").toString() : "";
            Object latenciaObj = resultado.get("latenciaMs");
            Object pingObj = resultado.get("pingMs");

            logger.info("[NUBE-VERIFY] ===== RESULTADO VERIFICACIÓN =====");
            logger.info("[NUBE-VERIFY] Éxito: {}", exito);
            logger.info("[NUBE-VERIFY] Etapa: {}", etapa);
            logger.info("[NUBE-VERIFY] Error: {}", error.isEmpty() ? "(ninguno)" : error);
            logger.info("[NUBE-VERIFY] Latencia total: {} ms", latenciaObj);
            logger.info("[NUBE-VERIFY] Ping: {} ms", pingObj);
            logger.info("[NUBE-VERIFY] Datos completos: {}", resultado);
            logger.info("[NUBE-VERIFY] ================================");

            if (exito) {
                indNube.setFill(Color.web("#a8b991"));
                estadoNube.setText("Conectada a Atlas (" + pingObj + " ms)");
                estadoNube.setStyle("-fx-text-fill: #a8b991;");
                valConexion.setText("ACTIVA — ping " + pingObj + " ms");
                valConexion.setStyle("-fx-text-fill: #a8b991;");
            } else {
                indNube.setFill(Color.web("#cc4444"));
                String msgError = error.length() > 80 ? error.substring(0, 80) + "..." : error;
                estadoNube.setText("Fallo: " + etapa);
                estadoNube.setStyle("-fx-text-fill: #cc4444;");
                valConexion.setText(msgError.isEmpty() ? "SIN CONEXIÓN" : msgError);
                valConexion.setStyle("-fx-text-fill: #cc4444;");
            }

            // Refrescar estado completo de nube tras verificación
            consultarEstadoNubeActualizar();
        }));
    }

    /**
     * Refresca los datos de la sección nube sin tocar indicador/estado de conexión
     * (esos ya fueron actualizados por forzarVerificacionNube).
     */
    private void consultarEstadoNubeActualizar() {
        CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/sync/estado").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    reader.close();
                    conn.disconnect();
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> resp = mapper.readValue(
                        sb.toString(), new TypeReference<Map<String, Object>>() {});
                    Object datos = resp.get("datos");
                    if (datos instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> d = (Map<String, Object>) datos;
                        return d;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                logger.debug("[NUBE-VERIFY] No se pudo refrescar estado nube: {}", e.getMessage());
            }
            return null;
        }).thenAccept(estado -> Platform.runLater(() -> {
            if (estado == null) return;

            boolean habilitado = Boolean.TRUE.equals(estado.get("habilitado"));
            String mongoUri = estado.get("mongoUri") != null ? estado.get("mongoUri").toString() : "";
            String businessId = estado.get("businessId") != null ? estado.get("businessId").toString() : "—";
            String baseDatos = estado.get("baseDatos") != null ? estado.get("baseDatos").toString() : "—";
            Object pendientes = estado.get("registrosPendientes");
            Object errores = estado.get("registrosConError");
            Object ultimaSync = estado.get("ultimaSincronizacion");

            valHabilitado.setText(habilitado
                ? IdiomaUtil.obtener("ctrl.bd.nube.si") : IdiomaUtil.obtener("ctrl.bd.nube.no"));
            valBusinessId.setText(businessId);
            valBaseDatos.setText(baseDatos);
            valPendientes.setText(pendientes != null ? pendientes.toString() : "0");
            valErrores.setText(errores != null ? errores.toString() : "0");
            valUltimaSync.setText(ultimaSync != null ? ultimaSync.toString().replace("T", " ") : "—");

            String cluster = extraerCluster(mongoUri);
            valCluster.setText(cluster.isEmpty() ? "—" : cluster);
        }));
    }

    // ==================== ASISTENTE DE BASE DE DATOS ====================

    /** Crea un icono SVG de engranaje (configuración) de 16x16. */
    private Region crearIconoConfiguracion() {
        Region icono = new Region();
        icono.getStyleClass().addAll("config-tile-icono-svg", "icono-configuracion");
        icono.setMinSize(16, 16);
        icono.setPrefSize(16, 16);
        icono.setMaxSize(16, 16);
        return icono;
    }

    /** Abre el asistente de base de datos desde el modal. */
    private void abrirAsistenteBd() {
        gestor.cerrarModalActual();
        StackPane raiz = gestor.getContenedorRaiz();
        var asistente = new com.kipu.cliente.controlador.configuracion.AsistenteBaseDatos(
                raiz, completado -> {
            if (completado) {
                logger.info("Asistente de BD completado desde modal de configuración");
            }
        });
        asistente.mostrar();
    }
}
