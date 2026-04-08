/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.componente;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.MonitorConexion;
import com.kipu.cliente.utilidad.ServidorEmbebido;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Panel embebible de configuración de conexión LAN + Nube.
 *
 * Puede mostrarse como overlay dentro del login cuando el usuario
 * hace clic en "Conectar" (host mode desconectado).
 *
 * Secciones:
 * 1. LAN: Detecta si el servidor ya está activo → card verde.
 *    Si no, muestra botón "Conectar" para arrancar el servidor.
 * 2. Nube (Atlas): Verifica config y conexión. Contenedor colapsable.
 */
public class PanelConexionRed {

    private static final Logger logger = LoggerFactory.getLogger(PanelConexionRed.class);

    private Consumer<Boolean> onConexionLanCompleta;
    private Consumer<Boolean> onCerrar;

    // Componentes LAN
    private VBox cardLan;
    private Circle indicadorLan;
    private Label labelEstadoLan;
    private Label labelInfoLan;
    private Button botonConectarLan;
    private ProgressBar barraProgresoLan;
    private Label labelProgresoLan;
    private HBox infoConectadaBox;
    private Button botonApagarLan;
    private Button botonReiniciarLan;
    private HBox accionesServidorBox;

    // Componentes Nube
    private VBox cardNube;
    private VBox contenedorDatosNube;
    private Circle indicadorNube;
    private Label labelEstadoNube;
    private Label labelInfoNube;
    private Button botonToggleDatosNube;
    private PasswordField campoMongoUri;
    private TextField campoBusinessId;
    private TextField campoDbNombre;
    private Label labelUriEstado;
    private Button botonGuardarAtlas;
    private Button botonSoloLan;

    private boolean datosNubeVisibles = false;
    private FondoAnimado fondoAnimado;
    private Timeline timelineProgreso;
    private TutorialNube tutorial;
    private StackPane overlayRef;
    private boolean mostrarTutorialAlAbrir = false;
    private HBox ipClienteBox;
    private Label labelIpClientes;

    /**
     * Construye el panel de conexión sin botón de tutorial.
     */
    public StackPane construir(StackPane contenedorRaiz,
                                Consumer<Boolean> onLanCompleta,
                                Consumer<Boolean> onCerrar) {
        return construir(contenedorRaiz, onLanCompleta, onCerrar, false);
    }

    /**
     * Construye el panel de conexión como un StackPane overlay completo.
     *
     * @param contenedorRaiz       StackPane padre donde se montará el overlay
     * @param onLanCompleta        callback cuando la conexión LAN se establece
     * @param onCerrar             callback al cerrar el panel
     * @param conTutorialInteractivo si true, añade botón de tutorial arriba a la izquierda
     * @return StackPane overlay listo para agregar al contenedor raíz
     */
    public StackPane construir(StackPane contenedorRaiz,
                                Consumer<Boolean> onLanCompleta,
                                Consumer<Boolean> onCerrar,
                                boolean conTutorialInteractivo) {
        this.mostrarTutorialAlAbrir = conTutorialInteractivo;
        this.onConexionLanCompleta = onLanCompleta;
        this.onCerrar = onCerrar;

        // Overlay con fondo semitransparente
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");

        // Fondo animado
        fondoAnimado = new FondoAnimado();

        // Contenido central — se ajusta al contenido interno
        VBox contenido = new VBox(0);
        contenido.setAlignment(Pos.CENTER);
        contenido.setMaxWidth(660);
        contenido.setMaxHeight(Region.USE_PREF_SIZE);
        contenido.setStyle("-fx-background-color: transparent;");

        // Logo (más grande) — fuera del scroll para que no lo afecte
        ImageView logo = new ImageView();
        try {
            logo.setImage(new Image(getClass().getResourceAsStream("/imagenes/LOGOPNG.png")));
        } catch (Exception e) {
            logger.debug("No se pudo cargar logo para panel conexión");
        }
        logo.setFitWidth(300);
        logo.setPreserveRatio(true);

        VBox contenedorLogo = new VBox(logo);
        contenedorLogo.setAlignment(Pos.CENTER);
        contenedorLogo.setPadding(new Insets(24, 0, 0, 0));

        // Botón cerrar en esquina superior derecha
        Label cerrar = new Label("✕");
        cerrar.setMinSize(36, 36);
        cerrar.setPrefSize(36, 36);
        cerrar.setAlignment(Pos.CENTER);
        cerrar.getStyleClass().add("panel-cerrar");
        cerrar.setOnMouseEntered(e ->
            cerrar.setStyle("-fx-text-fill: #f5f5f5; " +
                "-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 6;"));
        cerrar.setOnMouseExited(e ->
            cerrar.setStyle("-fx-text-fill: #666;"));
        cerrar.setOnMouseClicked(e -> cerrarPanel(overlay, contenedorRaiz));

        HBox barraSuperior = new HBox();
        barraSuperior.setAlignment(Pos.TOP_RIGHT);
        barraSuperior.setPadding(new Insets(12, 12, 0, 0));
        barraSuperior.getChildren().add(cerrar);
        barraSuperior.setPickOnBounds(false);

        // Sección LAN
        cardLan = construirCardLan();

        // Separador
        Region separador = new Region();
        separador.setPrefHeight(16);

        // Sección Nube
        cardNube = construirCardNube();

        // Info footer
        Label labelInfo = new Label(IdiomaUtil.obtener("splash.info_host"));
        labelInfo.getStyleClass().add("red-info");
        labelInfo.setStyle("-fx-text-fill: #555;");
        labelInfo.setPadding(new Insets(20, 0, 0, 0));

        VBox panelCards = new VBox(20);
        panelCards.setAlignment(Pos.CENTER);
        panelCards.setPadding(new Insets(20, 44, 30, 44));
        panelCards.getChildren().addAll(cardLan, separador, cardNube, labelInfo);

        // ScrollPane solo para las cards (el logo queda fuera)
        ScrollPane scroll = new ScrollPane(panelCards);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setMaxWidth(660);
        // Limitar scroll al 80% del overlay para dejar espacio al logo
        scroll.maxHeightProperty().bind(overlay.heightProperty().multiply(0.75));

        contenido.getChildren().addAll(contenedorLogo, scroll);
        overlay.getChildren().addAll(fondoAnimado, contenido, barraSuperior);
        StackPane.setAlignment(barraSuperior, Pos.TOP_RIGHT);

        this.overlayRef = overlay;

        // Botón de tutorial (arriba a la izquierda)
        if (mostrarTutorialAlAbrir) {
            Button botonTutorial = new Button("?  " + IdiomaUtil.obtener("tutorial.boton"));
            botonTutorial.getStyleClass().add("panel-btn-secundario");
            botonTutorial.setStyle(
                "-fx-background-color: rgba(212,175,55,0.12); -fx-text-fill: #d4af37; " +
                "-fx-font-weight: 600; -fx-cursor: hand; " +
                "-fx-border-color: rgba(212,175,55,0.3); -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-padding: 8 16;");
            botonTutorial.setOnAction(e -> iniciarTutorial());

            HBox barraIzquierda = new HBox(botonTutorial);
            barraIzquierda.setAlignment(Pos.TOP_LEFT);
            barraIzquierda.setPadding(new Insets(16, 0, 0, 16));
            barraIzquierda.setPickOnBounds(false);
            overlay.getChildren().add(barraIzquierda);
            StackPane.setAlignment(barraIzquierda, Pos.TOP_LEFT);

            // Auto-iniciar tutorial solo si nunca se ha conectado antes
            if (!ConfiguracionCliente.isTutorialConexionCompletado()) {
                javafx.application.Platform.runLater(() -> iniciarTutorial());
            }
        }

        // Evitar propagación de clics del overlay al fondo
        overlay.setOnMouseClicked(e -> e.consume());

        // Verificar estado inicial
        Platform.runLater(this::verificarEstadoInicial);

        return overlay;
    }

    // ══════════════════════════════════════
    // CARD LAN
    // ══════════════════════════════════════

    private VBox construirCardLan() {
        VBox card = new VBox(14);
        card.setStyle("-fx-background-color: #141414; -fx-background-radius: 14; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 14; -fx-border-width: 1;");
        card.setPadding(new Insets(24, 28, 24, 28));
        card.setMaxWidth(580);

        // Título con icono
        indicadorLan = new Circle(7, Color.web("#555"));
        Label titulo = new Label(IdiomaUtil.obtener("splash.lan.titulo"));
        titulo.getStyleClass().add("red-titulo");

        Region spacerHeader = new Region();
        HBox.setHgrow(spacerHeader, Priority.ALWAYS);

        // Botón reiniciar servidor (icono amarillo ↻)
        botonReiniciarLan = new Button("↻");
        botonReiniciarLan.getStyleClass().add("icono-texto-sm");
        botonReiniciarLan.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #daa520; " +
            "-fx-cursor: hand; -fx-padding: 2 6; -fx-font-weight: 700;");
        botonReiniciarLan.setOnMouseEntered(e -> botonReiniciarLan.setStyle(
            "-fx-background-color: rgba(218,165,32,0.15); -fx-text-fill: #daa520; " +
            "-fx-cursor: hand; -fx-padding: 2 6; -fx-font-weight: 700; -fx-background-radius: 6;"));
        botonReiniciarLan.setOnMouseExited(e -> botonReiniciarLan.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #daa520; " +
            "-fx-cursor: hand; -fx-padding: 2 6; -fx-font-weight: 700;"));
        botonReiniciarLan.setOnAction(e -> ejecutarReinicio());
        Tooltip.install(botonReiniciarLan, new Tooltip(IdiomaUtil.obtener("panel.lan.reiniciar")));

        // Botón apagar servidor (icono rojo ⏻)
        botonApagarLan = new Button("⏻");
        botonApagarLan.getStyleClass().add("icono-texto-sm");
        botonApagarLan.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #e74c3c; " +
            "-fx-cursor: hand; -fx-padding: 2 6;");
        botonApagarLan.setOnMouseEntered(e -> botonApagarLan.setStyle(
            "-fx-background-color: rgba(231,76,60,0.15); -fx-text-fill: #e74c3c; " +
            "-fx-cursor: hand; -fx-padding: 2 6; -fx-background-radius: 6;"));
        botonApagarLan.setOnMouseExited(e -> botonApagarLan.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #e74c3c; " +
            "-fx-cursor: hand; -fx-padding: 2 6;"));
        botonApagarLan.setOnAction(e -> ejecutarApagado());
        Tooltip.install(botonApagarLan, new Tooltip(IdiomaUtil.obtener("panel.lan.apagar")));

        // Contenedor de acciones (oculto hasta que conecta)
        accionesServidorBox = new HBox(4, botonReiniciarLan, botonApagarLan);
        accionesServidorBox.setAlignment(Pos.CENTER_RIGHT);
        accionesServidorBox.setVisible(false);
        accionesServidorBox.setManaged(false);

        HBox headerLan = new HBox(10, indicadorLan, titulo, spacerHeader, accionesServidorBox);
        headerLan.setAlignment(Pos.CENTER_LEFT);

        // Estado
        labelEstadoLan = new Label(IdiomaUtil.obtener("splash.lan.preparando"));
        labelEstadoLan.getStyleClass().add("red-estado");

        // Info adicional (IP, puerto + PID) — se muestra al conectar
        labelInfoLan = new Label("");
        labelInfoLan.getStyleClass().add("red-info");

        infoConectadaBox = new HBox(8, labelInfoLan);
        infoConectadaBox.setAlignment(Pos.CENTER_LEFT);
        infoConectadaBox.setPadding(new Insets(6, 12, 6, 12));
        infoConectadaBox.setStyle(
            "-fx-background-color: rgba(255,255,255,0.04); -fx-background-radius: 6; " +
            "-fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 6; -fx-border-width: 1;");
        infoConectadaBox.setVisible(false);
        infoConectadaBox.setManaged(false);

        // IP de conexión para clientes
        labelIpClientes = new Label("");
        labelIpClientes.getStyleClass().add("red-ip-titulo");
        Label labelIpTitulo = new Label(IdiomaUtil.obtener("panel.lan.ip_clientes"));
        labelIpTitulo.getStyleClass().add("red-ip-label");
        ipClienteBox = new HBox(8, labelIpTitulo, labelIpClientes);
        ipClienteBox.setAlignment(Pos.CENTER_LEFT);
        ipClienteBox.setPadding(new Insets(8, 14, 8, 14));
        ipClienteBox.setStyle(
            "-fx-background-color: rgba(212,175,55,0.06); -fx-background-radius: 8; " +
            "-fx-border-color: rgba(212,175,55,0.15); -fx-border-radius: 8; -fx-border-width: 1;");
        ipClienteBox.setVisible(false);
        ipClienteBox.setManaged(false);

        // Barra de progreso (oculta por defecto)
        barraProgresoLan = new ProgressBar(0);
        barraProgresoLan.setMaxWidth(Double.MAX_VALUE);
        barraProgresoLan.setPrefHeight(6);
        barraProgresoLan.getStyleClass().add("splash-barra");
        barraProgresoLan.setVisible(false);
        barraProgresoLan.setManaged(false);

        labelProgresoLan = new Label("0%");
        labelProgresoLan.getStyleClass().add("red-progreso");
        labelProgresoLan.setVisible(false);
        labelProgresoLan.setManaged(false);

        // Sincronizar label de porcentaje con el progreso real de la barra
        barraProgresoLan.progressProperty().addListener((obs, oldVal, newVal) ->
            labelProgresoLan.setText((int)(newVal.doubleValue() * 100) + "%")
        );

        // Botón conectar (visible solo si el servidor no está activo)
        botonConectarLan = new Button(IdiomaUtil.obtener("panel.lan.conectar"));
        botonConectarLan.getStyleClass().add("panel-btn-principal");
        botonConectarLan.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #d4af37, #b8984e); " +
            "-fx-text-fill: #0a0a0a; -fx-font-weight: 700; " +
            "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 12 32;");
        botonConectarLan.setOnAction(e -> iniciarServidorLan());
        botonConectarLan.setVisible(false);
        botonConectarLan.setManaged(false);

        card.getChildren().addAll(headerLan, labelEstadoLan, infoConectadaBox, ipClienteBox,
                barraProgresoLan, labelProgresoLan, botonConectarLan);

        return card;
    }

    // ══════════════════════════════════════
    // CARD NUBE
    // ══════════════════════════════════════

    private VBox construirCardNube() {
        VBox card = new VBox(14);
        card.setStyle("-fx-background-color: #141414; -fx-background-radius: 14; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 14; -fx-border-width: 1;");
        card.setPadding(new Insets(24, 28, 24, 28));
        card.setMaxWidth(580);

        // Título con indicador
        indicadorNube = new Circle(7, Color.web("#555"));
        Label titulo = new Label(IdiomaUtil.obtener("splash.nube.titulo"));
        titulo.getStyleClass().add("red-titulo");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Botón toggle para mostrar/ocultar datos
        botonToggleDatosNube = new Button(IdiomaUtil.obtener("panel.nube.configurar"));
        botonToggleDatosNube.getStyleClass().add("texto-hint");
        botonToggleDatosNube.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #777; " +
            "-fx-cursor: hand; -fx-border-color: #444; -fx-border-radius: 4; " +
            "-fx-background-radius: 4; -fx-padding: 4 10;");
        botonToggleDatosNube.setOnAction(e -> toggleDatosNube());

        HBox headerNube = new HBox(10, indicadorNube, titulo, spacer, botonToggleDatosNube);
        headerNube.setAlignment(Pos.CENTER_LEFT);

        // Estado
        labelEstadoNube = new Label(IdiomaUtil.obtener("splash.nube.esperando"));
        labelEstadoNube.getStyleClass().add("red-estado");

        labelInfoNube = new Label("");
        labelInfoNube.getStyleClass().add("red-info");
        labelInfoNube.setStyle("-fx-text-fill: #888;");
        labelInfoNube.setWrapText(true);
        labelInfoNube.setVisible(false);
        labelInfoNube.setManaged(false);

        // Contenedor de datos colapsable
        contenedorDatosNube = construirContenedorDatosNube();
        contenedorDatosNube.setVisible(false);
        contenedorDatosNube.setManaged(false);

        card.getChildren().addAll(headerNube, labelEstadoNube, labelInfoNube, contenedorDatosNube);

        return card;
    }

    private VBox construirContenedorDatosNube() {
        VBox contenedor = new VBox(12);
        contenedor.setPadding(new Insets(12, 0, 4, 0));
        contenedor.setStyle("-fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;");

        // Estado de la URI embebida (solo lectura)
        Label labelUri = new Label(IdiomaUtil.obtener("panel.nube.uri_embebida"));
        labelUri.getStyleClass().add("red-uri-label");
        String uriEmbebida = ConfiguracionCliente.getAtlasUriEmbebida();
        boolean tieneUri = uriEmbebida != null && !uriEmbebida.isBlank();
        labelUriEstado = new Label(tieneUri
                ? "✓ " + IdiomaUtil.obtener("panel.nube.uri_disponible")
                : "✗ " + IdiomaUtil.obtener("panel.nube.uri_no_disponible"));
        labelUriEstado.getStyleClass().add("estado-texto-bold");
        labelUriEstado.setStyle("-fx-text-fill: " + (tieneUri ? "#a8b991" : "#e74c3c") + ";");

        // Campo manual de URI (solo visible si no hay URI embebida ni en .env)
        Label labelUriManual = new Label(IdiomaUtil.obtener("splash.config.mongo_uri"));
        labelUriManual.getStyleClass().add("red-uri-label");
        labelUriManual.setStyle("-fx-padding: 6 0 0 0;");
        campoMongoUri = new PasswordField();
        campoMongoUri.setPromptText("mongodb+srv://user:pass@cluster.mongodb.net/");
        campoMongoUri.getStyleClass().add("campo-texto");
        campoMongoUri.setPrefHeight(36);
        Label hintUri = new Label(IdiomaUtil.obtener("panel.nube.uri_hint"));
        hintUri.getStyleClass().add("red-uri-hint");
        hintUri.setWrapText(true);
        // Ocultar campo URI si ya hay una embebida (producción)
        labelUriManual.setVisible(!tieneUri);
        labelUriManual.setManaged(!tieneUri);
        campoMongoUri.setVisible(!tieneUri);
        campoMongoUri.setManaged(!tieneUri);
        hintUri.setVisible(!tieneUri);
        hintUri.setManaged(!tieneUri);

        // Identificador del negocio
        Label labelBusinessId = new Label(IdiomaUtil.obtener("splash.config.business_id"));
        labelBusinessId.getStyleClass().add("red-uri-label");
        labelBusinessId.setStyle("-fx-padding: 6 0 0 0;");
        campoBusinessId = new TextField();
        campoBusinessId.setPromptText("mi_negocio_1");
        campoBusinessId.getStyleClass().add("campo-texto");
        campoBusinessId.setPrefHeight(36);

        // Nombre de la base de datos Atlas (derivado del business ID)
        Label labelDbNombre = new Label(IdiomaUtil.obtener("panel.nube.nombre_db"));
        labelDbNombre.getStyleClass().add("red-uri-label");
        labelDbNombre.setStyle("-fx-padding: 6 0 0 0;");
        campoDbNombre = new TextField();
        campoDbNombre.setPromptText("kipu_mi_negocio");
        campoDbNombre.getStyleClass().add("campo-texto");
        campoDbNombre.setPrefHeight(36);

        // Auto-derivar nombre de DB cuando cambia el business ID
        campoBusinessId.textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.isBlank()) {
                campoDbNombre.setText("kipu_" + val.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_"));
            }
        });

        Label hintDb = new Label(IdiomaUtil.obtener("panel.nube.nombre_db_hint"));
        hintDb.getStyleClass().add("red-uri-hint");
        hintDb.setWrapText(true);

        botonGuardarAtlas = new Button(IdiomaUtil.obtener("splash.config.guardar"));
        botonGuardarAtlas.getStyleClass().add("panel-btn-secundario");
        botonGuardarAtlas.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #d4af37, #b8984e); " +
            "-fx-text-fill: #0a0a0a; -fx-font-weight: 600; " +
            "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 24;");
        botonGuardarAtlas.setOnAction(e -> guardarConfigAtlas());

        botonSoloLan = new Button(IdiomaUtil.obtener("splash.config.solo_lan"));
        botonSoloLan.getStyleClass().add("panel-btn-secundario");
        botonSoloLan.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #777; " +
            "-fx-cursor: hand; -fx-border-color: #555; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-padding: 8 20;");
        botonSoloLan.setOnAction(e -> continuarSoloLan());

        HBox botones = new HBox(12, botonGuardarAtlas, botonSoloLan);
        botones.setAlignment(Pos.CENTER);
        botones.setPadding(new Insets(8, 0, 0, 0));

        contenedor.getChildren().addAll(labelUri, labelUriEstado,
                labelUriManual, campoMongoUri, hintUri,
                labelBusinessId, campoBusinessId,
                labelDbNombre, campoDbNombre, hintDb,
                botones);
        return contenedor;
    }

    // ══════════════════════════════════════
    // LÓGICA DE VERIFICACIÓN INICIAL
    // ══════════════════════════════════════

    private void verificarEstadoInicial() {
        // Verificar LAN: ¿el servidor ya está corriendo?
        int puerto = ConfiguracionCliente.getPuertoServidor();
        CompletableFuture.supplyAsync(() -> {
            try {
                String url = "http://localhost:" + puerto + "/api/usuarios/health";
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                conn.disconnect();
                return code == 200;
            } catch (Exception e) {
                return false;
            }
        }).thenAcceptAsync(servidorActivo -> {
            if (servidorActivo) {
                // Servidor ya corriendo → card LAN verde
                mostrarLanConectada();
                notificarTutorial(TutorialNube.Evento.SERVIDOR_YA_ACTIVO);
            } else {
                // Servidor no activo → mostrar botón conectar
                mostrarLanDesconectada();
            }
            // Verificar nube después
            verificarEstadoNube();
        }, Platform::runLater);
    }

    private void mostrarLanConectada() {
        indicadorLan.setFill(Color.web("#a8b991"));
        cardLan.setStyle("-fx-background-color: rgba(168,185,145,0.08); -fx-background-radius: 12; " +
                "-fx-border-color: rgba(168,185,145,0.3); -fx-border-radius: 12; -fx-border-width: 1;");
        labelEstadoLan.setText(IdiomaUtil.obtener("panel.lan.configurada"));
        labelEstadoLan.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");

        int puerto = ConfiguracionCliente.getPuertoServidor();

        // Obtener PID del proceso
        long pid = ServidorEmbebido.obtenerPid();
        if (pid > 0) {
            labelInfoLan.setText("localhost:" + puerto + "  ·  PID " + pid);
        } else {
            // Servidor preexistente — buscar PID async
            labelInfoLan.setText("localhost:" + puerto);
            ServidorEmbebido.buscarProcesosServidor().thenAcceptAsync(procesos -> {
                if (!procesos.isEmpty()) {
                    String pidEncontrado = procesos.get(0)[0];
                    labelInfoLan.setText("localhost:" + puerto + "  ·  PID " + pidEncontrado);
                }
            }, Platform::runLater);
        }

        infoConectadaBox.setVisible(true);
        infoConectadaBox.setManaged(true);
        botonApagarLan.setDisable(false);
        botonReiniciarLan.setDisable(false);

        // Mostrar iconos de acciones en el header de la card LAN
        accionesServidorBox.setVisible(true);
        accionesServidorBox.setManaged(true);

        // Mostrar IP de conexión para clientes
        String ipLocal = ConfiguracionCliente.getIpLocal();
        int puertoClientes = ConfiguracionCliente.getPuertoServidor();
        labelIpClientes.setText(ipLocal + ":" + puertoClientes);
        ipClienteBox.setVisible(true);
        ipClienteBox.setManaged(true);

        // Actualizar IP en tutorial si está activo
        if (tutorial != null) {
            tutorial.setIpLocal(ipLocal);
        }

        botonConectarLan.setVisible(false);
        botonConectarLan.setManaged(false);
        barraProgresoLan.setVisible(false);
        barraProgresoLan.setManaged(false);
        labelProgresoLan.setVisible(false);
        labelProgresoLan.setManaged(false);

        // Notificar que la LAN está lista
        ConfiguracionCliente.setUrlServidor("http://localhost:" + puerto);
        ServidorEmbebido.marcarConectado();
        // Registrar que ya se realizó la primera conexión exitosa
        if (!ConfiguracionCliente.isTutorialConexionCompletado()) {
            ConfiguracionCliente.setTutorialConexionCompletado(true);
        }
    }

    private void mostrarLanDesconectada() {
        indicadorLan.setFill(Color.web("#e74c3c"));
        cardLan.setStyle("-fx-background-color: rgba(231,76,60,0.05); -fx-background-radius: 14; " +
                "-fx-border-color: rgba(231,76,60,0.25); -fx-border-radius: 14; -fx-border-width: 1;");
        labelEstadoLan.setText(IdiomaUtil.obtener("panel.lan.desconectada"));
        labelEstadoLan.setStyle("-fx-text-fill: #e74c3c;");

        infoConectadaBox.setVisible(false);
        infoConectadaBox.setManaged(false);
        ipClienteBox.setVisible(false);
        ipClienteBox.setManaged(false);
        accionesServidorBox.setVisible(false);
        accionesServidorBox.setManaged(false);

        botonConectarLan.setVisible(true);
        botonConectarLan.setManaged(true);
    }

    // ══════════════════════════════════════
    // APAGAR SERVIDOR LAN
    // ══════════════════════════════════════

    private void ejecutarApagado() {
        botonApagarLan.setDisable(true);
        botonReiniciarLan.setDisable(true);

        ServidorEmbebido.detenerServidor().thenAcceptAsync(exito -> {
            if (exito) {
                MonitorConexion.getInstancia().detener();
                MonitorConexion.getInstancia().iniciar();
                mostrarLanDesconectada();
            } else {
                botonApagarLan.setDisable(false);
                botonReiniciarLan.setDisable(false);
                labelEstadoLan.setText(IdiomaUtil.obtener("ctrl.apagar.error"));
                labelEstadoLan.setStyle("-fx-text-fill: #e74c3c;");
            }
        }, Platform::runLater);
    }

    // ══════════════════════════════════════
    // REINICIAR SERVIDOR LAN
    // ══════════════════════════════════════

    private void ejecutarReinicio() {
        botonApagarLan.setDisable(true);
        botonReiniciarLan.setDisable(true);
        labelEstadoLan.setText(IdiomaUtil.obtener("panel.lan.reiniciando"));
        labelEstadoLan.setStyle("-fx-text-fill: #daa520;");

        ServidorEmbebido.detenerServidor().thenComposeAsync(exitoApagado -> {
            if (!exitoApagado) {
                throw new RuntimeException(IdiomaUtil.obtener("ctrl.apagar.error"));
            }
            MonitorConexion.getInstancia().detener();
            return ServidorEmbebido.iniciarServidor(mensaje ->
                Platform.runLater(() -> labelEstadoLan.setText(mensaje))
            );
        }).thenAcceptAsync(exitoInicio -> {
            if (exitoInicio) {
                MonitorConexion.getInstancia().iniciar();
                mostrarLanConectada();
            } else {
                mostrarLanDesconectada();
            }
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                botonApagarLan.setDisable(false);
                botonReiniciarLan.setDisable(false);
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                labelEstadoLan.setText(msg);
                labelEstadoLan.setStyle("-fx-text-fill: #e74c3c;");
            });
            return null;
        });
    }

    // ══════════════════════════════════════
    // INICIAR SERVIDOR LAN
    // ══════════════════════════════════════

    private void iniciarServidorLan() {
        botonConectarLan.setDisable(true);
        botonConectarLan.setText(IdiomaUtil.obtener("panel.lan.iniciando"));

        // Estado amarillo durante el arranque
        indicadorLan.setFill(Color.web("#daa520"));
        cardLan.setStyle("-fx-background-color: rgba(218,165,32,0.04); -fx-background-radius: 14; " +
                "-fx-border-color: rgba(218,165,32,0.2); -fx-border-radius: 14; -fx-border-width: 1;");
        labelEstadoLan.setStyle("-fx-text-fill: #daa520;");

        barraProgresoLan.setVisible(true);
        barraProgresoLan.setManaged(true);
        labelProgresoLan.setVisible(true);
        labelProgresoLan.setManaged(true);

        // Notificar tutorial que el servidor está iniciando
        notificarTutorial(TutorialNube.Evento.SERVIDOR_INICIANDO);

        // Simular progreso gradual
        simularProgresoGradual();

        ServidorEmbebido.iniciarServidor(mensaje ->
            Platform.runLater(() -> labelEstadoLan.setText(mensaje))
        ).thenAcceptAsync(exito -> {
            // Servidor listo → detener animación simulada
            if (timelineProgreso != null) timelineProgreso.stop();
            // Mostrar conectado de inmediato
            labelEstadoLan.setText(IdiomaUtil.obtener("splash.lan.conectado"));
            labelEstadoLan.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");
            animarProgreso(barraProgresoLan, labelProgresoLan, 1.0, 400, () -> {
                mostrarLanConectada();
                // Notificar tutorial que el servidor conectó exitosamente
                notificarTutorial(TutorialNube.Evento.SERVIDOR_CONECTADO);
                // Reiniciar monitor de conexión
                MonitorConexion.getInstancia().detener();
                new Timeline(new KeyFrame(Duration.millis(300),
                    ev -> MonitorConexion.getInstancia().iniciar())).play();

                if (onConexionLanCompleta != null) {
                    onConexionLanCompleta.accept(true);
                }
            });
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                // Detener animación simulada al haber error
                if (timelineProgreso != null) timelineProgreso.stop();
                barraProgresoLan.getStyleClass().add("splash-barra-error");
                indicadorLan.setFill(Color.web("#e74c3c"));
                labelProgresoLan.setStyle("-fx-text-fill: #e74c3c;");
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                labelEstadoLan.setText(msg);
                labelEstadoLan.setStyle("-fx-text-fill: #e74c3c;");
                botonConectarLan.setDisable(false);
                botonConectarLan.setText(IdiomaUtil.obtener("panel.lan.reintentar"));
                botonConectarLan.setVisible(true);
                botonConectarLan.setManaged(true);
                // Notificar tutorial del error
                notificarTutorial(TutorialNube.Evento.SERVIDOR_ERROR);
            });
            return null;
        });
    }

    /**
     * Simula progreso visual mientras el servidor arranca (~25 seg).
     * Se almacena en {@code timelineProgreso} para poder detenerla
     * al conectar o al producirse un error.
     */
    private void simularProgresoGradual() {
        double[] hitos   = {0.08, 0.18, 0.30, 0.42, 0.55, 0.66, 0.76, 0.84, 0.90};
        double[] tiempos = {1.0,  3.0,  5.5,  8.5,  12.0, 16.0, 19.5, 22.5, 25.0};
        String[] mensajes = {
            IdiomaUtil.obtener("splash.lan.verificando"),
            IdiomaUtil.obtener("splash.lan.buscando"),
            IdiomaUtil.obtener("splash.lan.iniciando"),
            IdiomaUtil.obtener("splash.lan.cargando_bd"),
            IdiomaUtil.obtener("splash.lan.migraciones"),
            IdiomaUtil.obtener("splash.lan.servicios"),
            IdiomaUtil.obtener("splash.lan.seguridad"),
            IdiomaUtil.obtener("splash.lan.health_check"),
            IdiomaUtil.obtener("splash.lan.casi_listo"),
        };

        timelineProgreso = new Timeline();
        for (int i = 0; i < hitos.length; i++) {
            double progreso = hitos[i];
            String mensaje = mensajes[i];

            timelineProgreso.getKeyFrames().add(
                new KeyFrame(Duration.seconds(tiempos[i]),
                    e -> labelEstadoLan.setText(mensaje),
                    new KeyValue(barraProgresoLan.progressProperty(), progreso)
                )
            );
        }
        timelineProgreso.play();
    }

    // ══════════════════════════════════════
    // VERIFICACIÓN NUBE
    // ══════════════════════════════════════

    private void verificarEstadoNube() {
        String atlasUri = ConfiguracionCliente.getAtlasUriEmbebida();
        String businessId = obtenerConfigAtlas("BUSINESS_ID");
        String dbNombre = obtenerConfigAtlas("MONGODB_DB_NAME");
        boolean tieneUri = atlasUri != null && !atlasUri.isBlank();

        if (!tieneUri) {
            // Sin URI embebida → cloud no disponible en este build
            indicadorNube.setFill(Color.web("#555"));
            labelEstadoNube.setText(IdiomaUtil.obtener("panel.nube.uri_no_disponible"));
            labelEstadoNube.setStyle("-fx-text-fill: #777;");
            campoBusinessId.setText("negocio1");
            return;
        }

        if (businessId == null || businessId.isBlank()) {
            // URI embebida pero sin business ID configurado → pendiente de configurar
            indicadorNube.setFill(Color.web("#daa520"));
            labelEstadoNube.setText(IdiomaUtil.obtener("splash.nube.requiere_config"));
            labelEstadoNube.setStyle("-fx-text-fill: #daa520;");
            campoBusinessId.setText("negocio1");
            return;
        }

        // Pre-llenar campos
        campoBusinessId.setText(businessId);
        if (dbNombre != null && !dbNombre.isBlank()) {
            campoDbNombre.setText(dbNombre);
        }

        // Hay config → verificar conectividad
        indicadorNube.setFill(Color.web("#daa520"));
        cardNube.setStyle("-fx-background-color: rgba(218,165,32,0.04); -fx-background-radius: 12; " +
                "-fx-border-color: rgba(218,165,32,0.2); -fx-border-radius: 12; -fx-border-width: 1;");
        labelEstadoNube.setText(IdiomaUtil.obtener("splash.nube.verificando"));

        CompletableFuture.supplyAsync(() -> {
            try {
                String url = ConfiguracionCliente.getUrlServidor() + "/api/sync/estado";
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                return code == 200 || code == 401 || code == 403;
            } catch (Exception e) {
                return false;
            }
        }).thenAcceptAsync(disponible -> {
            if (disponible) {
                indicadorNube.setFill(Color.web("#a8b991"));
                cardNube.setStyle("-fx-background-color: rgba(168,185,145,0.05); -fx-background-radius: 12; " +
                        "-fx-border-color: rgba(168,185,145,0.2); -fx-border-radius: 12; -fx-border-width: 1;");
                labelEstadoNube.setText(IdiomaUtil.obtener("splash.nube.configurada"));
                labelEstadoNube.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");

                String cluster = extraerCluster(atlasUri);
                String infoTexto = construirTextoInfoNube(cluster, businessId);
                if (!infoTexto.isBlank()) {
                    labelInfoNube.setText(infoTexto);
                    labelInfoNube.setStyle("-fx-text-fill: #888;");
                    labelInfoNube.setVisible(true);
                    labelInfoNube.setManaged(true);
                }
            } else {
                // URI embebida + config local pero servidor no responde aún
                indicadorNube.setFill(Color.web("#daa520"));
                cardNube.setStyle("-fx-background-color: rgba(218,165,32,0.04); -fx-background-radius: 12; " +
                        "-fx-border-color: rgba(218,165,32,0.2); -fx-border-radius: 12; -fx-border-width: 1;");
                labelEstadoNube.setText(IdiomaUtil.obtener("panel.nube.config_presente_sin_conexion"));
                labelEstadoNube.setStyle("-fx-text-fill: #daa520;");
            }
        }, Platform::runLater);
    }

    // ══════════════════════════════════════
    // TOGGLE DATOS NUBE
    // ══════════════════════════════════════

    private void toggleDatosNube() {
        datosNubeVisibles = !datosNubeVisibles;
        contenedorDatosNube.setVisible(datosNubeVisibles);
        contenedorDatosNube.setManaged(datosNubeVisibles);
        botonToggleDatosNube.setText(datosNubeVisibles
                ? IdiomaUtil.obtener("panel.nube.ocultar_config")
                : IdiomaUtil.obtener("panel.nube.configurar"));
        if (datosNubeVisibles) {
            notificarTutorial(TutorialNube.Evento.NUBE_CONFIG_ABIERTA);
        }
    }

    // ══════════════════════════════════════
    // GUARDAR CONFIG ATLAS
    // ══════════════════════════════════════

    private void guardarConfigAtlas() {
        String businessId = campoBusinessId.getText().trim();
        String dbNombre = campoDbNombre.getText().trim();
        String uriManual = campoMongoUri.getText().trim();

        if (businessId.isEmpty()) {
            campoBusinessId.getStyleClass().add("campo-texto-error");
            return;
        }
        campoBusinessId.getStyleClass().remove("campo-texto-error");

        // Verificar que haya URI (embebida o manual)
        String uriExistente = ConfiguracionCliente.getAtlasUriEmbebida();
        boolean tieneUriEmbebida = uriExistente != null && !uriExistente.isBlank();
        if (!tieneUriEmbebida && uriManual.isEmpty()) {
            campoMongoUri.getStyleClass().add("campo-texto-error");
            return;
        }
        campoMongoUri.getStyleClass().remove("campo-texto-error");

        // Sanitizar nombre de DB si el usuario lo editó manualmente
        if (dbNombre.isEmpty()) {
            dbNombre = "kipu_" + businessId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }

        guardarEnvNube(businessId, dbNombre, uriManual);

        // Invalidar caché para que relea el .env en el próximo acceso
        ConfiguracionCliente.invalidarCacheAtlasUri();

        // Limpiar campo URI sensible
        campoMongoUri.clear();

        // Actualizar estado de URI
        labelUriEstado.setText("✓ " + IdiomaUtil.obtener("panel.nube.uri_disponible"));
        labelUriEstado.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");

        // Actualizar visual
        indicadorNube.setFill(Color.web("#a8b991"));
        cardNube.setStyle("-fx-background-color: rgba(168,185,145,0.05); -fx-background-radius: 12; " +
                "-fx-border-color: rgba(168,185,145,0.2); -fx-border-radius: 12; -fx-border-width: 1;");
        labelEstadoNube.setText(IdiomaUtil.obtener("splash.nube.guardada"));
        labelEstadoNube.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");

        String atlasUri = ConfiguracionCliente.getAtlasUriEmbebida();
        String cluster = extraerCluster(atlasUri);
        String infoTexto = construirTextoInfoNube(cluster, businessId);
        if (!infoTexto.isBlank()) {
            labelInfoNube.setText(infoTexto + "  ·  \uD83D\uDDC3 " + dbNombre);
            labelInfoNube.setStyle("-fx-text-fill: #888;");
            labelInfoNube.setVisible(true);
            labelInfoNube.setManaged(true);
        }

        // Ocultar datos después de guardar
        contenedorDatosNube.setVisible(false);
        contenedorDatosNube.setManaged(false);
        datosNubeVisibles = false;
        botonToggleDatosNube.setText(IdiomaUtil.obtener("panel.nube.configurar"));

        // Notificar tutorial que la nube se configuró
        notificarTutorial(TutorialNube.Evento.NUBE_CONFIGURADA);
    }

    private void continuarSoloLan() {
        indicadorNube.setFill(Color.web("#555"));
        labelEstadoNube.setText(IdiomaUtil.obtener("splash.nube.omitida"));
        labelEstadoNube.setStyle("-fx-text-fill: #666;");

        contenedorDatosNube.setVisible(false);
        contenedorDatosNube.setManaged(false);
        datosNubeVisibles = false;
        botonToggleDatosNube.setText(IdiomaUtil.obtener("panel.nube.configurar"));
    }

    // ══════════════════════════════════════
    // TUTORIAL INTERACTIVO
    // ══════════════════════════════════════

    private void iniciarTutorial() {
        if (tutorial != null && tutorial.isActivo()) {
            tutorial.detener();
            return;
        }
        if (overlayRef == null) return;

        tutorial = new TutorialNube();
        tutorial.registrarNodos(cardLan, cardNube, botonConectarLan,
            ipClienteBox, botonApagarLan, botonToggleDatosNube,
            campoDbNombre, campoBusinessId);
        tutorial.setIpLocal(ConfiguracionCliente.getIpLocal());
        tutorial.iniciar(overlayRef);
    }

    private void notificarTutorial(TutorialNube.Evento evento) {
        if (tutorial != null && tutorial.isActivo()) {
            tutorial.notificarEvento(evento);
        }
    }

    // ══════════════════════════════════════
    // CERRAR PANEL
    // ══════════════════════════════════════

    private void cerrarPanel(StackPane overlay, StackPane contenedorRaiz) {
        if (tutorial != null && tutorial.isActivo()) {
            tutorial.detener();
        }
        if (fondoAnimado != null) {
            fondoAnimado.detener();
        }
        if (timelineProgreso != null) {
            timelineProgreso.stop();
        }

        contenedorRaiz.getChildren().remove(overlay);
        if (onCerrar != null) {
            onCerrar.accept(true);
        }
    }

    // ══════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════

    private void animarProgreso(ProgressBar barra, Label labelPorcentaje,
                                 double objetivo, double duracionMs, Runnable alFinalizar) {
        Timeline anim = new Timeline(
            new KeyFrame(Duration.millis(duracionMs),
                new KeyValue(barra.progressProperty(), objetivo)
            )
        );
        if (alFinalizar != null) {
            anim.setOnFinished(e -> alFinalizar.run());
        }
        anim.play();
    }

    private String obtenerConfigAtlas(String clave) {
        Path envPath = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
        if (!Files.exists(envPath)) return null;

        try {
            for (String linea : Files.readAllLines(envPath)) {
                linea = linea.trim();
                if (linea.startsWith("#") || linea.isEmpty()) continue;
                int sep = linea.indexOf('=');
                if (sep > 0 && linea.substring(0, sep).trim().equals(clave)) {
                    String valor = linea.substring(sep + 1).trim();
                    if (valor.length() >= 2
                            && ((valor.startsWith("'") && valor.endsWith("'"))
                            || (valor.startsWith("\"") && valor.endsWith("\"")))) {
                        valor = valor.substring(1, valor.length() - 1);
                    }
                    return valor;
                }
            }
        } catch (IOException e) {
            logger.debug("No se pudo leer .env: {}", e.getMessage());
        }
        return null;
    }

    /** Guarda la configuración de nube en .env. Si se provee URI manual, la persiste en .env para desarrollo. */
    private void guardarEnvNube(String businessId, String dbNombre, String uriManual) {
        Path envDir = Paths.get(System.getProperty("user.home"), ".kipu");
        Path envPath = envDir.resolve(".env");

        try {
            Files.createDirectories(envDir);

            java.util.LinkedHashMap<String, String> variables = new java.util.LinkedHashMap<>();
            if (Files.exists(envPath)) {
                for (String linea : Files.readAllLines(envPath)) {
                    linea = linea.trim();
                    if (linea.startsWith("#") || linea.isEmpty()) continue;
                    int sep = linea.indexOf('=');
                    if (sep > 0) {
                        variables.put(linea.substring(0, sep).trim(), linea.substring(sep + 1).trim());
                    }
                }
            }

            // Si hay URI manual (no embebida), guardarla en .env para que el servidor la lea
            if (uriManual != null && !uriManual.isBlank()) {
                variables.put("MONGODB_URI", uriManual);
            }
            // Si ya hay URI embebida en cloud.dat, no necesitamos MONGODB_URI en .env
            // (ServidorEmbebido la inyecta directo como env var al subprocess)

            variables.put("BUSINESS_ID", businessId);
            variables.put("MONGODB_DB_NAME", dbNombre);
            variables.put("SYNC_HABILITADO", "true");
            variables.remove("BAR_ID");

            StringBuilder sb = new StringBuilder();
            sb.append("# Configuración Kipu - Generado automáticamente\n");
            for (var entry : variables.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            Files.writeString(envPath, sb.toString());

            // Actualizar también en ConfiguracionCliente
            ConfiguracionCliente.setBusinessId(businessId);

            logger.info("Configuración nube guardada en {} (DB: {})", envPath, dbNombre);
        } catch (IOException e) {
            logger.error("Error guardando configuración nube: {}", e.getMessage());
        }
    }

    private String extraerCluster(String mongoUri) {
        if (mongoUri == null || mongoUri.isBlank()) return null;
        // Intentar appName= primero
        int idx = mongoUri.indexOf("appName=");
        if (idx >= 0) {
            String cluster = mongoUri.substring(idx + "appName=".length());
            int amp = cluster.indexOf('&');
            if (amp > 0) cluster = cluster.substring(0, amp);
            return cluster;
        }
        // Fallback: extraer hostname desde mongodb+srv://user:pass@host/db
        int at = mongoUri.indexOf('@');
        if (at >= 0) {
            String resto = mongoUri.substring(at + 1);
            int slash = resto.indexOf('/');
            if (slash > 0) return resto.substring(0, slash);
            int question = resto.indexOf('?');
            if (question > 0) return resto.substring(0, question);
            if (!resto.isBlank()) return resto;
        }
        return null;
    }

    private String construirTextoInfoNube(String cluster, String businessId) {
        StringBuilder sb = new StringBuilder();
        if (cluster != null && !cluster.isBlank()) {
            sb.append("☁ ").append(cluster);
        }
        if (businessId != null && !businessId.isBlank()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append("🏢 ").append(businessId);
        }
        return sb.toString();
    }
}
