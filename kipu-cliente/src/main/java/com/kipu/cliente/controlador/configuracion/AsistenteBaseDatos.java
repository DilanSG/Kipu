/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion;

import com.kipu.cliente.componente.ToggleSwitch;
import com.kipu.cliente.configuracion.ConfiguracionBd;
import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.utilidad.DetectorDependencias;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.ServidorEmbebido;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Asistente GUI para la configuración de bases de datos PostgreSQL.
 * Permite crear múltiples configuraciones de BD, guardarlas como
 * tarjetas y elegir cuál activar. Incluye detección de Atlas MongoDB.
 *
 * Dos modos de operación:
 * - Lista de BDs guardadas (tarjetas) con opción de conectar/eliminar
 * - Asistente paso a paso para crear una nueva configuración de BD
 */
public class AsistenteBaseDatos {

    private static final Logger logger = LoggerFactory.getLogger(AsistenteBaseDatos.class);

    private static final String ESTILO_CAMPO =
            "-fx-background-color: #0f0f0f; -fx-text-fill: #f5f5f5; -fx-border-color: #333; " +
            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 14; " +
            "-fx-prompt-text-fill: #555;";
    private static final String ESTILO_CAMPO_FOCUS =
            "-fx-background-color: #0f0f0f; -fx-text-fill: #f5f5f5; -fx-border-color: #d4af37; " +
            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 14; " +
            "-fx-prompt-text-fill: #555;";
    private static final String ESTILO_CAMPO_ERROR =
            "-fx-background-color: #0f0f0f; -fx-text-fill: #f5f5f5; -fx-border-color: #8b0000; " +
            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 14;";
    private static final String ESTILO_BOTON_PRINCIPAL =
            "-fx-background-color: linear-gradient(to bottom, #d4af37, #b8984e); -fx-text-fill: #0a0a0a; " +
            "-fx-font-weight: 700; -fx-padding: 12 28; -fx-cursor: hand; " +
            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(212,175,55,0.25), 8, 0, 0, 2);";
    private static final String ESTILO_BOTON_SECUNDARIO =
            "-fx-background-color: transparent; -fx-text-fill: #d4af37; -fx-border-color: #d4af37; " +
            "-fx-font-weight: 600; -fx-padding: 10 22; -fx-cursor: hand; " +
            "-fx-background-radius: 8; -fx-border-radius: 8;";
    private static final String ESTILO_BOTON_TERCIARIO =
            "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #b0b0b0; -fx-border-color: #333; " +
            "-fx-padding: 8 16; -fx-cursor: hand; " +
            "-fx-background-radius: 6; -fx-border-radius: 6;";
    private static final String ESTILO_CARD =
            "-fx-background-color: #0f0f0f; -fx-background-radius: 10; -fx-padding: 18; " +
            "-fx-border-color: #222; -fx-border-radius: 10; -fx-border-width: 1;";
    private static final String ESTILO_SECCION_TITULO =
            "-fx-font-weight: 700; -fx-text-fill: #d4af37;";

    private final StackPane contenedorRaiz;
    private final Consumer<Boolean> callbackCompletado;
    private final boolean modoInicial;
    private StackPane overlay;
    private VBox panelPrincipal;

    // Campos del formulario
    private TextField campoHost;
    private TextField campoPuerto;
    private TextField campoSuperUsuario;
    private PasswordField campoSuperPassword;
    private TextField campoDbNombre;
    private TextField campoDbUsuario;
    private PasswordField campoDbPassword;
    private PasswordField campoDbPasswordConfirmar;
    private TextField campoBusinessId;
    private Label labelEstado;
    private Circle indicadorEstado;
    private Button btnSiguiente;
    private VBox contenidoPasos;
    private Circle[] indicadoresPaso;
    private Line[] lineasPaso;

    private int pasoActual = 0;

    // Datos finales para exportación
    private String ultimoHost, ultimoPuerto, ultimoDbNombre, ultimoDbUsuario, ultimoBusinessId;

    /**
     * Crea el asistente en modo configuración (desde herramientas).
     * Si hay BDs guardadas muestra la lista completa; si no, inicia el wizard.
     */
    public AsistenteBaseDatos(StackPane contenedorRaiz, Consumer<Boolean> callbackCompletado) {
        this(contenedorRaiz, callbackCompletado, false);
    }

    /**
     * Crea el asistente de base de datos.
     *
     * @param contenedorRaiz StackPane del shell sobre el cual montar el overlay
     * @param callbackCompletado Se invoca con true si el setup terminó bien, false si se canceló
     * @param modoInicial true = startup (solo wizard paso a paso), false = herramientas (lista completa)
     */
    public AsistenteBaseDatos(StackPane contenedorRaiz, Consumer<Boolean> callbackCompletado, boolean modoInicial) {
        this.contenedorRaiz = contenedorRaiz;
        this.callbackCompletado = callbackCompletado;
        this.modoInicial = modoInicial;
    }

    /** Muestra el asistente como overlay bloqueante sobre la aplicación. */
    public void mostrar() {
        overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.88);");
        overlay.setAlignment(Pos.CENTER);

        panelPrincipal = new VBox(0);
        panelPrincipal.setMaxWidth(620);
        panelPrincipal.setMaxHeight(680);
        panelPrincipal.setStyle(GestorModales.ESTILO_MODAL_LUXURY +
                "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 24, 0, 0, 8);");
        panelPrincipal.setAlignment(Pos.TOP_CENTER);

        // ── HEADER con icono y título ──
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 28, 16, 28));
        header.setStyle("-fx-background-color: rgba(212,175,55,0.04); -fx-background-radius: 12 12 0 0;");

        // Icono de PostgreSQL estilizado (círculo con símbolo de BD)
        StackPane iconoDb = new StackPane();
        Circle circuloIcono = new Circle(20);
        circuloIcono.setFill(Color.web("#0f0f0f"));
        circuloIcono.setStroke(Color.web("#d4af37"));
        circuloIcono.setStrokeWidth(1.5);
        Label simboloDb = new Label("\uD83D\uDDC4"); // 🗄 icono base de datos
        simboloDb.getStyleClass().add("icono-texto-md");
        iconoDb.getChildren().addAll(circuloIcono, simboloDb);

        VBox headerTexto = new VBox(2);
        Label titulo = new Label(IdiomaUtil.obtener("asistente.pg.titulo"));
        titulo.getStyleClass().add("modal-titulo-lg");
        Label subtitulo = new Label(IdiomaUtil.obtener("asistente.pg.subtitulo"));
        subtitulo.getStyleClass().add("modal-subtitulo");
        subtitulo.setStyle("-fx-wrap-text: true;");
        subtitulo.setWrapText(true);
        headerTexto.getChildren().addAll(titulo, subtitulo);
        HBox.setHgrow(headerTexto, Priority.ALWAYS);

        header.getChildren().addAll(iconoDb, headerTexto);

        // ── PROGRESS BAR con dots conectados ──
        HBox progressBar = crearIndicadorPasos();
        progressBar.setPadding(new Insets(8, 28, 16, 28));

        // ── Separador dorado sutil ──
        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: linear-gradient(to right, transparent, rgba(212,175,55,0.2), transparent);");

        // ── Contenido dinámico por paso ──
        contenidoPasos = new VBox(14);
        contenidoPasos.setAlignment(Pos.TOP_LEFT);
        contenidoPasos.setPadding(new Insets(20, 28, 12, 28));
        VBox.setVgrow(contenidoPasos, Priority.ALWAYS);

        // ── Separador inferior ──
        Region sepInf = new Region();
        sepInf.setMinHeight(1);
        sepInf.setMaxHeight(1);
        sepInf.setStyle("-fx-background-color: rgba(255,255,255,0.05);");

        // ── Barra de estado + botones ──
        HBox barraAcciones = new HBox(12);
        barraAcciones.setAlignment(Pos.CENTER_RIGHT);
        barraAcciones.setPadding(new Insets(14, 28, 20, 28));

        HBox barraEstado = new HBox(8);
        barraEstado.setAlignment(Pos.CENTER_LEFT);
        indicadorEstado = new Circle(4, Color.web("#444"));
        labelEstado = new Label("");
        labelEstado.getStyleClass().add("estado-texto");
        labelEstado.setStyle("-fx-text-fill: #666;");
        barraEstado.getChildren().addAll(indicadorEstado, labelEstado);

        Button btnCancelar = new Button(IdiomaUtil.obtener("asistente.pg.cancelar"));
        btnCancelar.getStyleClass().add("texto-info");
        btnCancelar.setStyle(ESTILO_BOTON_SECUNDARIO);
        btnCancelar.setOnAction(e -> cerrar(false));

        btnSiguiente = new Button(IdiomaUtil.obtener("asistente.pg.siguiente"));
        btnSiguiente.getStyleClass().add("panel-seccion-titulo");
        btnSiguiente.setStyle(ESTILO_BOTON_PRINCIPAL);
        btnSiguiente.setOnAction(e -> avanzarPaso());

        Region espacioAcciones = new Region();
        HBox.setHgrow(espacioAcciones, Priority.ALWAYS);
        barraAcciones.getChildren().addAll(barraEstado, espacioAcciones, btnCancelar, btnSiguiente);

        panelPrincipal.getChildren().addAll(header, progressBar, sep, contenidoPasos, sepInf, barraAcciones);
        overlay.getChildren().add(panelPrincipal);
        contenedorRaiz.getChildren().add(overlay);

        // En modo inicial (startup): siempre wizard paso a paso
        // En modo herramientas: lista completa si hay BDs, wizard si no
        if (modoInicial) {
            mostrarPaso(0);
        } else {
            List<ConfiguracionBd> guardadas = ConfiguracionBd.cargarTodas();
            if (guardadas.isEmpty()) {
                mostrarPaso(0);
            } else {
                mostrarListaBds();
            }
        }
    }

    /** Crea un progress bar visual con dots conectados por líneas. */
    private HBox crearIndicadorPasos() {
        HBox contenedor = new HBox(0);
        contenedor.setAlignment(Pos.CENTER);

        String[] nombres = {
            IdiomaUtil.obtener("asistente.pg.paso.detectar"),
            IdiomaUtil.obtener("asistente.pg.paso.conectar"),
            IdiomaUtil.obtener("asistente.pg.paso.crear"),
            IdiomaUtil.obtener("asistente.pg.paso.negocio")
        };

        indicadoresPaso = new Circle[4];
        lineasPaso = new Line[3];

        for (int i = 0; i < 4; i++) {
            // Dot
            VBox dotGroup = new VBox(4);
            dotGroup.setAlignment(Pos.CENTER);

            Circle dot = new Circle(8);
            dot.setFill(i == 0 ? Color.web("#d4af37") : Color.web("#2a2a2a"));
            dot.setStroke(i == 0 ? Color.web("#d4af37") : Color.web("#404040"));
            dot.setStrokeWidth(1.5);
            indicadoresPaso[i] = dot;

            // Número dentro del dot
            Label num = new Label(String.valueOf(i + 1));
            num.getStyleClass().add("badge-texto");
            num.setStyle("-fx-font-weight: 700; -fx-text-fill: " +
                    (i == 0 ? "#0a0a0a" : "#555") + ";");
            StackPane dotStack = new StackPane(dot, num);

            // Label debajo
            Label lbl = new Label(nombres[i]);
            lbl.getStyleClass().add("dato-label");
            lbl.setStyle("-fx-text-fill: " +
                    (i == 0 ? "#d4af37" : "#555") + "; -fx-font-weight: " +
                    (i == 0 ? "600" : "400") + ";");

            dotGroup.getChildren().addAll(dotStack, lbl);
            contenedor.getChildren().add(dotGroup);

            // Línea conectora
            if (i < 3) {
                Line linea = new Line(0, 0, 50, 0);
                linea.setStroke(Color.web("#2a2a2a"));
                linea.setStrokeWidth(1.5);
                lineasPaso[i] = linea;

                StackPane lineaWrapper = new StackPane(linea);
                lineaWrapper.setPadding(new Insets(0, 6, 14, 6));
                HBox.setHgrow(lineaWrapper, Priority.ALWAYS);
                contenedor.getChildren().add(lineaWrapper);
            }
        }
        return contenedor;
    }

    /** Actualiza el progress bar: pasos completados (verde), activo (dorado), pendientes (gris). */
    private void actualizarIndicador(int paso) {
        for (int i = 0; i < 4; i++) {
            Circle dot = indicadoresPaso[i];
            boolean activo = i == paso;
            boolean completado = i < paso;

            if (completado) {
                dot.setFill(Color.web("#a8b991"));
                dot.setStroke(Color.web("#a8b991"));
            } else if (activo) {
                dot.setFill(Color.web("#d4af37"));
                dot.setStroke(Color.web("#d4af37"));
            } else {
                dot.setFill(Color.web("#2a2a2a"));
                dot.setStroke(Color.web("#404040"));
            }

            // Actualizar número y label del dot
            StackPane dotStack = (StackPane) ((VBox) indicadoresPaso[i].getParent().getParent()).getChildren().get(0);
            Label num = (Label) dotStack.getChildren().get(1);
            Label lbl = (Label) ((VBox) indicadoresPaso[i].getParent().getParent()).getChildren().get(1);

            String color = completado ? "#a8b991" : activo ? "#d4af37" : "#555";
            String numColor = (completado || activo) ? "#0a0a0a" : "#555";
            num.getStyleClass().add("badge-texto");
            num.setStyle("-fx-font-weight: 700; -fx-text-fill: " + numColor + ";");
            if (completado) num.setText("✓");
            lbl.getStyleClass().add("dato-label");
            lbl.setStyle("-fx-text-fill: " + color +
                    "; -fx-font-weight: " + ((completado || activo) ? "600" : "400") + ";");
        }
        // Actualizar líneas conectoras
        for (int i = 0; i < 3; i++) {
            lineasPaso[i].setStroke(i < paso ? Color.web("#a8b991") : Color.web("#2a2a2a"));
        }
    }

    // ==================== PASO 0: DETECCIÓN ====================

    private void mostrarPaso(int paso) {
        pasoActual = paso;
        contenidoPasos.getChildren().clear();
        actualizarIndicador(paso);

        switch (paso) {
            case 0 -> mostrarPasoDeteccion();
            case 1 -> mostrarPasoConexion();
            case 2 -> mostrarPasoCrearBd();
            case 3 -> mostrarPasoNegocio();
        }
    }

    private void mostrarPasoDeteccion() {
        btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.verificar"));
        btnSiguiente.setDisable(false);

        Label info = new Label(IdiomaUtil.obtener("asistente.pg.detectando"));
        info.getStyleClass().add("texto-info");
        info.setStyle("-fx-wrap-text: true;");
        info.setWrapText(true);

        // Card de resultados
        VBox resultados = new VBox(10);
        resultados.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("\uD83D\uDD0D  " + IdiomaUtil.obtener("asistente.pg.paso.detectar"));
        seccionTitulo.getStyleClass().add("texto-info");
        seccionTitulo.setStyle(ESTILO_SECCION_TITULO);

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #222;");

        Label lblPsql = new Label("⏳  " + IdiomaUtil.obtener("asistente.pg.buscando_postgresql"));
        lblPsql.getStyleClass().add("texto-info");
        lblPsql.setStyle("-fx-text-fill: #888;");

        Label lblServicio = new Label("⏳  " + IdiomaUtil.obtener("asistente.pg.verificando_servicio"));
        lblServicio.getStyleClass().add("texto-info");
        lblServicio.setStyle("-fx-text-fill: #888;");

        Label lblComando = new Label("");
        lblComando.getStyleClass().add("texto-hint-sm");
        lblComando.setStyle("-fx-text-fill: #888; -fx-font-family: monospace; " +
                           "-fx-wrap-text: true; -fx-padding: 10; -fx-background-color: #0a0a0a; " +
                           "-fx-background-radius: 6; -fx-border-color: #222; -fx-border-radius: 6;");
        lblComando.setWrapText(true);
        lblComando.setVisible(false);
        lblComando.setManaged(false);

        resultados.getChildren().addAll(seccionTitulo, divider, lblPsql, lblServicio, lblComando);
        contenidoPasos.getChildren().addAll(info, resultados);

        // Ejecutar detección automáticamente
        CompletableFuture.runAsync(() -> {
            var deteccion = DetectorDependencias.detectarPostgresql();
            boolean activo = DetectorDependencias.postgresqlEstaActivo();

            Platform.runLater(() -> {
                if (deteccion.encontrado()) {
                    lblPsql.setText("✓  PostgreSQL: " + deteccion.version());
                    lblPsql.setStyle("-fx-text-fill: #a8b991;");
                } else {
                    lblPsql.setText("✗  " + IdiomaUtil.obtener("asistente.pg.no_encontrado"));
                    lblPsql.setStyle("-fx-text-fill: #ef4444;");
                    lblComando.setText(DetectorDependencias.obtenerComandoInstalacion());
                    lblComando.setVisible(true);
                    lblComando.setManaged(true);
                }

                if (activo) {
                    lblServicio.setText("✓  " + IdiomaUtil.obtener("asistente.pg.servicio_activo"));
                    lblServicio.setStyle("-fx-text-fill: #a8b991;");
                    setEstado(IdiomaUtil.obtener("asistente.pg.listo"), "#a8b991");
                    btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.siguiente"));
                } else {
                    lblServicio.setText("✗  " + IdiomaUtil.obtener("asistente.pg.servicio_inactivo"));
                    lblServicio.setStyle("-fx-text-fill: #daa520;");
                    setEstado(IdiomaUtil.obtener("asistente.pg.iniciar_pg"), "#daa520");
                    // Se puede continuar incluso si no se detecta (el usuario puede tener PG remoto)
                    btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.siguiente"));
                }
            });
        });
    }

    // ==================== PASO 1: CONEXIÓN ====================

    private void mostrarPasoConexion() {
        btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.probar_conexion"));
        btnSiguiente.setDisable(false);
        setEstado("", "#444");

        Label info = new Label(IdiomaUtil.obtener("asistente.pg.paso1_info"));
        info.getStyleClass().add("texto-info");
        info.setStyle("-fx-wrap-text: true;");
        info.setWrapText(true);

        // Card contenedora
        VBox card = new VBox(12);
        card.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("\uD83D\uDD17  " + IdiomaUtil.obtener("asistente.pg.paso.conectar"));
        seccionTitulo.getStyleClass().add("texto-info");
        seccionTitulo.setStyle(ESTILO_SECCION_TITULO);

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #222;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        campoHost = crearCampo("localhost");
        campoPuerto = crearCampo("5432");
        campoSuperUsuario = crearCampo("postgres");
        campoSuperPassword = new PasswordField();
        campoSuperPassword.getStyleClass().add("texto-info");
        campoSuperPassword.setStyle(ESTILO_CAMPO);
        campoSuperPassword.setPromptText("••••••");
        campoSuperPassword.setPrefWidth(280);
        agregarFocusStyle(campoSuperPassword);

        // Fila 0: Host
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.host")), 0, 0);
        VBox hostGroup = crearCampoConDefault(campoHost, "localhost");
        grid.add(hostGroup, 1, 0);

        // Fila 1: Puerto
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.puerto")), 0, 1);
        VBox puertoGroup = crearCampoConDefault(campoPuerto, "5432");
        grid.add(puertoGroup, 1, 1);

        // Fila 2: Superusuario
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.superusuario")), 0, 2);
        VBox userGroup = crearCampoConDefault(campoSuperUsuario, "postgres");
        grid.add(userGroup, 1, 2);

        // Fila 3: Contraseña
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.superusuario_password")), 0, 3);
        grid.add(campoSuperPassword, 1, 3);

        Label hint = new Label("ℹ  " + IdiomaUtil.obtener("asistente.pg.hint_superusuario"));
        hint.getStyleClass().add("texto-hint-sm");
        hint.setStyle("-fx-text-fill: #555; -fx-wrap-text: true; -fx-padding: 6 0 0 0;");
        hint.setWrapText(true);

        card.getChildren().addAll(seccionTitulo, divider, grid, hint);
        contenidoPasos.getChildren().addAll(info, card);
    }

    // ==================== PASO 2: CREAR BD ====================

    private void mostrarPasoCrearBd() {
        btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.crear_bd"));
        btnSiguiente.setDisable(false);
        setEstado("", "#444");

        Label info = new Label(IdiomaUtil.obtener("asistente.pg.paso2_info"));
        info.getStyleClass().add("texto-info");
        info.setStyle("-fx-wrap-text: true;");
        info.setWrapText(true);

        // Card contenedora
        VBox card = new VBox(12);
        card.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("\uD83D\uDDC3  " + IdiomaUtil.obtener("asistente.pg.paso.crear"));
        seccionTitulo.getStyleClass().add("texto-info");
        seccionTitulo.setStyle(ESTILO_SECCION_TITULO);

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #222;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        campoDbNombre = crearCampo("kipu_db");
        campoDbUsuario = crearCampo("kipu_admin");
        campoDbPassword = new PasswordField();
        campoDbPassword.getStyleClass().add("texto-info");
        campoDbPassword.setStyle(ESTILO_CAMPO);
        campoDbPassword.setPrefWidth(280);
        campoDbPassword.setPromptText("••••••");
        agregarFocusStyle(campoDbPassword);
        campoDbPasswordConfirmar = new PasswordField();
        campoDbPasswordConfirmar.getStyleClass().add("texto-info");
        campoDbPasswordConfirmar.setStyle(ESTILO_CAMPO);
        campoDbPasswordConfirmar.setPrefWidth(280);
        campoDbPasswordConfirmar.setPromptText("••••••");
        agregarFocusStyle(campoDbPasswordConfirmar);

        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.nombre_bd")), 0, 0);
        grid.add(crearCampoConDefault(campoDbNombre, "kipu_db"), 1, 0);
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.usuario_app")), 0, 1);
        grid.add(crearCampoConDefault(campoDbUsuario, "kipu_admin"), 1, 1);
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.password_app")), 0, 2);
        grid.add(campoDbPassword, 1, 2);
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.confirmar_password")), 0, 3);
        grid.add(campoDbPasswordConfirmar, 1, 3);

        card.getChildren().addAll(seccionTitulo, divider, grid);
        contenidoPasos.getChildren().addAll(info, card);
    }

    // ==================== PASO 3: NEGOCIO ====================

    private void mostrarPasoNegocio() {
        btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.finalizar"));
        btnSiguiente.setDisable(false);
        setEstado("", "#444");

        Label info = new Label(IdiomaUtil.obtener("asistente.pg.paso3_info"));
        info.getStyleClass().add("texto-info");
        info.setStyle("-fx-wrap-text: true;");
        info.setWrapText(true);

        // Card contenedora
        VBox card = new VBox(12);
        card.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("\uD83C\uDFEA  " + IdiomaUtil.obtener("asistente.pg.paso.negocio"));
        seccionTitulo.getStyleClass().add("texto-info");
        seccionTitulo.setStyle(ESTILO_SECCION_TITULO);

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #222;");

        campoBusinessId = crearCampo("");
        campoBusinessId.setPromptText(IdiomaUtil.obtener("asistente.pg.placeholder_negocio"));

        Label notaNube = new Label("ℹ  " + IdiomaUtil.obtener("asistente.pg.nota_nube"));
        notaNube.getStyleClass().add("texto-hint-sm");
        notaNube.setStyle("-fx-text-fill: #666; -fx-wrap-text: true;");
        notaNube.setWrapText(true);

        Label notaUnico = new Label("⚠  " + IdiomaUtil.obtener("asistente.pg.negocio_unico"));
        notaUnico.getStyleClass().add("texto-hint-sm");
        notaUnico.setStyle("-fx-text-fill: #daa520; -fx-wrap-text: true;");
        notaUnico.setWrapText(true);

        HBox labelRow = new HBox(8, crearLabel(IdiomaUtil.obtener("asistente.pg.nombre_negocio")));
        labelRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(seccionTitulo, divider, labelRow, campoBusinessId, notaNube, notaUnico);
        contenidoPasos.getChildren().addAll(info, card);
    }

    // ==================== NAVEGACIÓN ENTRE PASOS ====================

    private void avanzarPaso() {
        switch (pasoActual) {
            case 0 -> mostrarPaso(1); // Detección → Conexión
            case 1 -> validarConexionYAvanzar();
            case 2 -> crearBaseDatosYAvanzar();
            case 3 -> finalizarSetup();
        }
    }

    private void validarConexionYAvanzar() {
        String host = campoHost.getText().trim();
        int puerto;
        try {
            puerto = Integer.parseInt(campoPuerto.getText().trim());
        } catch (NumberFormatException e) {
            setEstado(IdiomaUtil.obtener("asistente.pg.error_puerto"), "#ef4444");
            campoPuerto.setStyle(ESTILO_CAMPO_ERROR);
            return;
        }
        String usuario = campoSuperUsuario.getText().trim();
        String password = campoSuperPassword.getText();

        if (host.isEmpty() || usuario.isEmpty()) {
            setEstado(IdiomaUtil.obtener("asistente.pg.campos_vacios"), "#ef4444");
            return;
        }

        btnSiguiente.setDisable(true);
        setEstado(IdiomaUtil.obtener("asistente.pg.conectando"), "#daa520");

        CompletableFuture.supplyAsync(() ->
            DetectorDependencias.verificarConexionJdbc(host, puerto, usuario, password, "postgres")
        ).thenAcceptAsync(resultado -> {
            if (resultado.encontrado()) {
                setEstado("✓ " + resultado.version(), "#a8b991");
                // Guardar datos para el siguiente paso
                campoHost.setUserData("validado");
                mostrarPaso(2);
            } else {
                setEstado("✗ " + resultado.version(), "#ef4444");
                btnSiguiente.setDisable(false);
            }
        }, Platform::runLater);
    }

    private void crearBaseDatosYAvanzar() {
        String dbNombre = campoDbNombre.getText().trim();
        String dbUsuario = campoDbUsuario.getText().trim();
        String dbPassword = campoDbPassword.getText();
        String dbPasswordConfirm = campoDbPasswordConfirmar.getText();

        if (dbNombre.isEmpty() || dbUsuario.isEmpty() || dbPassword.isEmpty()) {
            setEstado(IdiomaUtil.obtener("asistente.pg.campos_vacios"), "#ef4444");
            return;
        }

        if (!dbPassword.equals(dbPasswordConfirm)) {
            setEstado(IdiomaUtil.obtener("asistente.pg.passwords_no_coinciden"), "#ef4444");
            campoDbPasswordConfirmar.setStyle(ESTILO_CAMPO_ERROR);
            return;
        }

        // Validar nombres seguros (solo alfanumérico y guión bajo)
        if (!dbNombre.matches("[a-zA-Z_][a-zA-Z0-9_]*") || !dbUsuario.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            setEstado(IdiomaUtil.obtener("asistente.pg.nombre_invalido"), "#ef4444");
            return;
        }

        String host = campoHost.getText().trim();
        int puerto = Integer.parseInt(campoPuerto.getText().trim());
        String superUsuario = campoSuperUsuario.getText().trim();
        String superPassword = campoSuperPassword.getText();

        btnSiguiente.setDisable(true);
        setEstado(IdiomaUtil.obtener("asistente.pg.creando_bd"), "#daa520");

        CompletableFuture.runAsync(() -> {
            // Paso 1: Crear usuario de aplicación
            String sqlCrearUsuario = String.format(
                    "DO $$ BEGIN " +
                    "IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN " +
                    "CREATE ROLE \"%s\" WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE; " +
                    "ELSE ALTER ROLE \"%s\" WITH PASSWORD '%s'; " +
                    "END IF; END $$;",
                    dbUsuario, dbUsuario, dbPassword.replace("'", "''"),
                    dbUsuario, dbPassword.replace("'", "''"));

            String err = DetectorDependencias.ejecutarSqlAdmin(host, puerto, superUsuario, superPassword, "postgres", sqlCrearUsuario);
            if (err != null) throw new RuntimeException("Error creando usuario: " + err);

            // Paso 2: Crear base de datos
            String sqlCrearBd = String.format(
                    "SELECT CASE WHEN NOT EXISTS (SELECT FROM pg_database WHERE datname = '%s') " +
                    "THEN (SELECT 'create') ELSE (SELECT 'exists') END",
                    dbNombre);
            // Verificar si existe y crear con statement separado
            var resultado = DetectorDependencias.verificarConexionJdbc(host, puerto, superUsuario, superPassword, "postgres");
            if (!resultado.encontrado()) throw new RuntimeException("Conexión perdida");

            // Intentar crear DB (ignorar error si ya existe)
            String errDb = DetectorDependencias.ejecutarSqlAdmin(host, puerto, superUsuario, superPassword, "postgres",
                    String.format("CREATE DATABASE \"%s\" OWNER \"%s\" ENCODING 'UTF8'", dbNombre, dbUsuario));
            if (errDb != null && !errDb.contains("already exists") && !errDb.contains("ya existe")) {
                throw new RuntimeException("Error creando BD: " + errDb);
            }

            // Paso 3: Asignar owner si ya existía
            DetectorDependencias.ejecutarSqlAdmin(host, puerto, superUsuario, superPassword, "postgres",
                    String.format("ALTER DATABASE \"%s\" OWNER TO \"%s\"", dbNombre, dbUsuario));

            // Paso 4: Aplicar permisos sobre schema public
            String sqlPermisos = String.format(
                    "ALTER SCHEMA public OWNER TO \"%s\"; " +
                    "GRANT ALL ON SCHEMA public TO \"%s\"; " +
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"%s\"; " +
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO \"%s\"; " +
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO \"%s\";",
                    dbUsuario, dbUsuario, dbUsuario, dbUsuario, dbUsuario);
            DetectorDependencias.ejecutarSqlAdmin(host, puerto, superUsuario, superPassword, dbNombre, sqlPermisos);

            // Paso 5: Verificar que el nuevo usuario puede conectarse
            var testConexion = DetectorDependencias.verificarConexionJdbc(host, puerto, dbUsuario, dbPassword, dbNombre);
            if (!testConexion.encontrado()) {
                throw new RuntimeException("El usuario creado no puede conectarse: " + testConexion.version());
            }

        }).thenRunAsync(() -> {
            setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.bd_creada"), "#a8b991");
            mostrarPaso(3);
        }, Platform::runLater)
        .exceptionally(ex -> {
            Platform.runLater(() -> {
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                setEstado("✗ " + msg, "#ef4444");
                btnSiguiente.setDisable(false);
            });
            return null;
        });
    }

    private void finalizarSetup() {
        String businessId = campoBusinessId.getText().trim();

        // Business ID es obligatorio (necesario para la nube)
        if (businessId.isEmpty()) {
            setEstado(IdiomaUtil.obtener("asistente.pg.negocio_requerido"), "#ef4444");
            campoBusinessId.setStyle(ESTILO_CAMPO_ERROR);
            return;
        }

        // Validar unicidad del nombre de negocio
        if (ConfiguracionBd.existeBusinessId(businessId)) {
            setEstado(IdiomaUtil.obtener("asistente.pg.negocio_duplicado"), "#ef4444");
            campoBusinessId.setStyle(ESTILO_CAMPO_ERROR);
            return;
        }

        btnSiguiente.setDisable(true);
        setEstado(IdiomaUtil.obtener("asistente.pg.generando_env"), "#daa520");

        CompletableFuture.runAsync(() -> {
            try {
                Path dirKipu = Paths.get(System.getProperty("user.home"), ".kipu");
                Files.createDirectories(dirKipu);

                // Generar JWT_SECRET seguro
                byte[] secretBytes = new byte[48];
                new SecureRandom().nextBytes(secretBytes);
                String jwtSecret = Base64.getEncoder().encodeToString(secretBytes);

                String host = campoHost.getText().trim();
                String puerto = campoPuerto.getText().trim();
                String dbNombre = campoDbNombre.getText().trim();
                String dbUsuario = campoDbUsuario.getText().trim();
                String dbPassword = campoDbPassword.getText();

                // Construir .env con valores seguros (comillas simples para evitar expansión)
                StringBuilder env = new StringBuilder();
                env.append("# Configuración Kipu - Generado por asistente GUI\n");
                env.append("DB_HOST='").append(host).append("'\n");
                env.append("DB_PORT='").append(puerto).append("'\n");
                env.append("DB_NAME='").append(dbNombre).append("'\n");
                env.append("DB_USER='").append(dbUsuario).append("'\n");
                env.append("DB_PASSWORD='").append(dbPassword).append("'\n");
                env.append("JWT_SECRET='").append(jwtSecret).append("'\n");

                // Cloud sync solo si se proporcionó business ID
                if (!businessId.isEmpty()) {
                    env.append("BUSINESS_ID='").append(businessId).append("'\n");
                    env.append("SYNC_HABILITADO='false'\n"); // Deshabilitado por defecto
                    // La URI de Atlas viene embebida en la JVM, no se escribe a disco
                    String atlasUri = ConfiguracionCliente.getAtlasUriEmbebida();
                    if (!atlasUri.isEmpty()) {
                        // Derivar nombre de DB en Atlas: kipu_{businessId}
                        env.append("MONGODB_DB_NAME='kipu_").append(
                                businessId.toLowerCase().replaceAll("[^a-z0-9_]", "_")).append("'\n");
                        // La URI se pasa como variable de entorno al subprocess del servidor
                    }
                }

                Path envFile = dirKipu.resolve(".env");
                Files.writeString(envFile, env.toString());
                // Restringir permisos del archivo (solo owner)
                envFile.toFile().setReadable(false, false);
                envFile.toFile().setReadable(true, true);
                envFile.toFile().setWritable(false, false);
                envFile.toFile().setWritable(true, true);

                logger.info("Archivo .env generado en: {}", envFile);

                // Guardar datos para posible exportación
                ultimoHost = host;
                ultimoPuerto = puerto;
                ultimoDbNombre = dbNombre;
                ultimoDbUsuario = dbUsuario;
                ultimoBusinessId = businessId;

                // Actualizar estado en ConfiguracionCliente
                ConfiguracionCliente.setSetupPostgresCompletado(true);
                if (!businessId.isEmpty()) {
                    ConfiguracionCliente.setBusinessId(businessId);
                }

                // Guardar configuración como tarjeta en databases.json
                ConfiguracionBd nuevaBd = new ConfiguracionBd(
                        businessId, host, Integer.parseInt(puerto),
                        dbNombre, dbUsuario, businessId);
                ConfiguracionBd.activar(""); // desactivar todas
                nuevaBd.setActiva(true);
                ConfiguracionBd.agregar(nuevaBd);

            } catch (Exception e) {
                throw new RuntimeException("Error generando .env: " + e.getMessage(), e);
            }
        }).thenRunAsync(() -> {
            setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.completado"), "#a8b991");

            // Reemplazar contenido con resumen + botón exportar
            contenidoPasos.getChildren().clear();
            mostrarResumenFinal();

            if (modoInicial) {
                // En modo startup: cerrar directamente tras completar
                btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.iniciar_servidor"));
                btnSiguiente.setDisable(false);
                btnSiguiente.setOnAction(e -> cerrar(true));
            } else {
                btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.ver_lista"));
                btnSiguiente.setDisable(false);
                btnSiguiente.setOnAction(e -> mostrarListaBds());
            }
        }, Platform::runLater)
        .exceptionally(ex -> {
            Platform.runLater(() -> {
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                setEstado("✗ " + msg, "#ef4444");
                btnSiguiente.setDisable(false);
            });
            return null;
        });
    }

    // ==================== RESUMEN FINAL ====================

    /** Muestra un resumen con todos los datos configurados y opción de exportar. */
    private void mostrarResumenFinal() {
        VBox card = new VBox(10);
        card.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("✓  " + IdiomaUtil.obtener("asistente.pg.resumen_titulo"));
        seccionTitulo.getStyleClass().add("panel-seccion-titulo");
        seccionTitulo.setStyle("-fx-text-fill: #a8b991;");

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: rgba(168,185,145,0.2);");

        // Grid de datos configurados
        GridPane resumen = new GridPane();
        resumen.setHgap(16);
        resumen.setVgap(6);

        int fila = 0;
        resumen.add(crearResumenLabel("Host"), 0, fila);
        resumen.add(crearResumenValor(ultimoHost + ":" + ultimoPuerto), 1, fila++);
        resumen.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nombre_bd")), 0, fila);
        resumen.add(crearResumenValor(ultimoDbNombre), 1, fila++);
        resumen.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.usuario_app")), 0, fila);
        resumen.add(crearResumenValor(ultimoDbUsuario), 1, fila++);
        if (ultimoBusinessId != null && !ultimoBusinessId.isEmpty()) {
            resumen.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nombre_negocio")), 0, fila);
            resumen.add(crearResumenValor(ultimoBusinessId), 1, fila++);
        }
        resumen.add(crearResumenLabel(".env"), 0, fila);
        resumen.add(crearResumenValor("~/.kipu/.env"), 1, fila);

        // Botón de exportar
        Button btnExportar = new Button("\uD83D\uDCBE  " + IdiomaUtil.obtener("asistente.pg.guardar_archivo"));
        btnExportar.getStyleClass().add("texto-secundario-sm");
        btnExportar.setStyle(ESTILO_BOTON_TERCIARIO);
        btnExportar.setOnAction(e -> exportarConfiguracion());

        Label hintExportar = new Label(IdiomaUtil.obtener("asistente.pg.guardar_archivo_hint"));
        hintExportar.getStyleClass().add("texto-hint");
        hintExportar.setStyle("-fx-wrap-text: true;");
        hintExportar.setWrapText(true);

        HBox exportRow = new HBox(10, btnExportar, hintExportar);
        exportRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(seccionTitulo, divider, resumen, new Region(), exportRow);
        contenidoPasos.getChildren().add(card);
    }

    /** Exporta toda la configuración a un archivo .txt con FileChooser. */
    private void exportarConfiguracion() {
        Window ventana = overlay.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(IdiomaUtil.obtener("asistente.pg.guardar_archivo"));
        chooser.setInitialFileName("kipu-config-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File archivo = chooser.showSaveDialog(ventana);
        if (archivo == null) return;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ================================================\n");
            sb.append("# Kipu - Configuración de Base de Datos\n");
            sb.append("# Generado: ").append(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            sb.append("# ================================================\n\n");

            sb.append("[PostgreSQL]\n");
            sb.append("Host       = ").append(ultimoHost).append("\n");
            sb.append("Puerto     = ").append(ultimoPuerto).append("\n");
            sb.append("Base Datos = ").append(ultimoDbNombre).append("\n");
            sb.append("Usuario    = ").append(ultimoDbUsuario).append("\n");
            sb.append("\n");

            if (ultimoBusinessId != null && !ultimoBusinessId.isEmpty()) {
                sb.append("[Cloud Sync]\n");
                sb.append("Business ID = ").append(ultimoBusinessId).append("\n");
                sb.append("\n");
            }

            sb.append("[Archivos Generados]\n");
            sb.append(".env       = ~/.kipu/.env\n");
            sb.append("Propiedades= ~/.kipu/kipu-cliente.properties\n");
            sb.append("\n");
            sb.append("# NOTA: Las contraseñas NO se incluyen en este archivo por seguridad.\n");
            sb.append("# Si necesita las credenciales, revise ~/.kipu/.env\n");

            Files.writeString(archivo.toPath(), sb.toString());
            setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.archivo_guardado"), "#a8b991");
            logger.info("Configuración exportada a: {}", archivo.getAbsolutePath());
        } catch (Exception ex) {
            setEstado("✗ " + ex.getMessage(), "#ef4444");
            logger.error("Error exportando configuración: {}", ex.getMessage());
        }
    }

    private Label crearResumenLabel(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-secundario");
        l.setStyle("-fx-text-fill: #777; -fx-min-width: 100;");
        return l;
    }

    private Label crearResumenValor(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dato-valor");
        l.setStyle("-fx-text-fill: #e8e8e8; " +
                "-fx-font-family: 'Monospace';" );
        return l;
    }

    // ==================== UTILIDADES ====================

    private void cerrar(boolean completado) {
        contenedorRaiz.getChildren().remove(overlay);
        callbackCompletado.accept(completado);
    }

    private void setEstado(String texto, String color) {
        indicadorEstado.setFill(Color.web(color));
        labelEstado.setText(texto);
        labelEstado.setStyle("-fx-text-fill: " + color + ";");
    }

    private TextField crearCampo(String valorDefecto) {
        TextField campo = new TextField(valorDefecto);
        campo.getStyleClass().add("texto-info");
        campo.setStyle(ESTILO_CAMPO);
        campo.setPrefWidth(280);
        agregarFocusStyle(campo);
        return campo;
    }

    /** Envuelve un campo con su hint de "(default: ...)" debajo. */
    private VBox crearCampoConDefault(TextField campo, String valorDefault) {
        Label defaultHint = new Label(IdiomaUtil.obtener("asistente.pg.valor_default") + ": " + valorDefault);
        defaultHint.getStyleClass().add("texto-hint");
        defaultHint.setStyle("-fx-padding: 1 0 0 2;");
        VBox group = new VBox(2, campo, defaultHint);
        return group;
    }

    /** Agrega efecto visual de focus dorado en los campos. */
    private void agregarFocusStyle(TextInputControl campo) {
        campo.focusedProperty().addListener((obs, old, focused) -> {
            if (!ESTILO_CAMPO_ERROR.equals(campo.getStyle())) {
                campo.setStyle(focused ? ESTILO_CAMPO_FOCUS : ESTILO_CAMPO);
            }
        });
    }

    private Label crearLabel(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dato-valor");
        l.setStyle("-fx-text-fill: #b0b0b0;");
        l.setMinWidth(130);
        return l;
    }

    /** Extrae el nombre del cluster desde la URI de MongoDB Atlas. */
    private String extraerClusterSimple(String mongoUri) {
        if (mongoUri == null || mongoUri.isBlank()) return "";
        int idx = mongoUri.indexOf("appName=");
        if (idx >= 0) {
            String cluster = mongoUri.substring(idx + "appName=".length());
            int amp = cluster.indexOf('&');
            return amp > 0 ? cluster.substring(0, amp) : cluster;
        }
        // Intentar extraer del host: mongodb+srv://user:pass@CLUSTER.mongodb.net
        try {
            int at = mongoUri.indexOf('@');
            if (at > 0) {
                String host = mongoUri.substring(at + 1);
                int slash = host.indexOf('/');
                if (slash > 0) host = host.substring(0, slash);
                int dot = host.indexOf('.');
                if (dot > 0) return host.substring(0, dot);
            }
        } catch (Exception e) { /* ignorar */ }
        return "";
    }

    // ==================== VISTA DE LISTA DE BASES DE DATOS ====================

    /** Muestra la vista de tarjetas con todas las BDs guardadas + card de nube. */
    private void mostrarListaBds() {
        contenidoPasos.getChildren().clear();
        setEstado("", "#444");

        // Ocultar progress dots en modo lista
        ocultarIndicadorPasos();

        btnSiguiente.setText(IdiomaUtil.obtener("asistente.pg.iniciar_servidor"));
        btnSiguiente.setDisable(false);
        btnSiguiente.setOnAction(e -> cerrar(true));

        // Título de la sección
        Label titulo = new Label("\uD83D\uDDC4  " + IdiomaUtil.obtener("asistente.pg.lista_titulo"));
        titulo.getStyleClass().add("modal-titulo-dorado");

        // Botón "Nueva BD"
        Button btnNueva = new Button("＋  " + IdiomaUtil.obtener("asistente.pg.nueva_bd"));
        btnNueva.getStyleClass().add("texto-info");
        btnNueva.setStyle(ESTILO_BOTON_SECUNDARIO + " -fx-padding: 8 16;");
        btnNueva.getStyleClass().add("panel-btn-secundario");
        btnNueva.setOnAction(e -> mostrarCrearBdSimplificado());

        HBox headerLista = new HBox(12, titulo, new Region(), btnNueva);
        headerLista.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerLista.getChildren().get(1), Priority.ALWAYS);

        contenidoPasos.getChildren().add(headerLista);

        // ScrollPane para las tarjetas
        VBox listaCards = new VBox(10);

        // Cargar todas las configs una sola vez
        List<ConfiguracionBd> guardadas = ConfiguracionBd.cargarTodas();

        // — Card de BD en la nube (MongoDB Atlas) —
        listaCards.getChildren().add(crearCardNube(guardadas));

        // — Cards de BDs PostgreSQL guardadas (excluir NUBE, ya tiene su propia card) —
        List<ConfiguracionBd> postgresGuardadas = guardadas.stream()
                .filter(c -> c.getTipo() != ConfiguracionBd.TipoBd.NUBE)
                .toList();
        if (postgresGuardadas.isEmpty()) {
            Label vacio = new Label(IdiomaUtil.obtener("asistente.pg.sin_bds"));
            vacio.getStyleClass().add("texto-info");
            vacio.setStyle("-fx-text-fill: #555; -fx-padding: 16 0 0 0;");
            vacio.setAlignment(Pos.CENTER);
            listaCards.getChildren().add(vacio);
        } else {
            for (ConfiguracionBd bd : postgresGuardadas) {
                listaCards.getChildren().add(crearCardBd(bd));
            }
        }

        ScrollPane scroll = new ScrollPane(listaCards);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        contenidoPasos.getChildren().add(scroll);
    }

    /** Crea la tarjeta de conexión a la nube (MongoDB Atlas). */
    private VBox crearCardNube(List<ConfiguracionBd> todasLasConfigs) {
        String atlasUri = ConfiguracionCliente.getAtlasUriEmbebida();
        boolean tieneUri = atlasUri != null && !atlasUri.isBlank();
        String cluster = tieneUri ? extraerClusterSimple(atlasUri) : "";

        // Buscar si ya hay una config de tipo NUBE guardada
        ConfiguracionBd nubeExistente = todasLasConfigs.stream()
                .filter(c -> c.getTipo() == ConfiguracionBd.TipoBd.NUBE)
                .findFirst().orElse(null);
        boolean nubeConectada = nubeExistente != null;

        VBox card = new VBox(10);
        String bordeColor = nubeConectada ? "#6c5ce7" : "#222";
        card.setStyle(ESTILO_CARD + " -fx-border-color: " + bordeColor + "; -fx-cursor: hand;");

        // Fila superior: icono nube + título + badge
        HBox filaTop = new HBox(8);
        filaTop.setAlignment(Pos.CENTER_LEFT);

        Label iconoNube = new Label("☁");
        iconoNube.getStyleClass().add("icono-texto-md");
        Label lblTitulo = new Label(IdiomaUtil.obtener("asistente.pg.nube_titulo"));
        lblTitulo.getStyleClass().add("panel-seccion-titulo");
        lblTitulo.setStyle("-fx-text-fill: " +
                (nubeConectada ? "#6c5ce7" : "#e8e8e8") + ";");

        filaTop.getChildren().addAll(iconoNube, lblTitulo);

        if (nubeConectada) {
            Label badge = new Label("● " + IdiomaUtil.obtener("asistente.pg.nube_conectada"));
            badge.getStyleClass().add("badge-texto");
            badge.setStyle("-fx-text-fill: #6c5ce7; -fx-font-weight: 600; " +
                    "-fx-background-color: rgba(108,92,231,0.15); -fx-padding: 2 8; " +
                    "-fx-background-radius: 10; -fx-border-color: rgba(108,92,231,0.3); -fx-border-radius: 10;");
            filaTop.getChildren().add(badge);
        }

        Region espacioTop = new Region();
        HBox.setHgrow(espacioTop, Priority.ALWAYS);

        // Badge tipo
        Label tipoLabel = new Label("MongoDB Atlas");
        tipoLabel.getStyleClass().add("texto-hint");
        tipoLabel.setStyle("-fx-font-style: italic;");
        filaTop.getChildren().addAll(espacioTop, tipoLabel);

        // Fila de datos
        HBox filaDatos = new HBox(16);
        filaDatos.setAlignment(Pos.CENTER_LEFT);

        if (tieneUri) {
            filaDatos.getChildren().add(crearDatoCard("Cluster", cluster.isEmpty() ? "Atlas" : cluster));
            String estado = nubeConectada ? "✓ " + IdiomaUtil.obtener("asistente.pg.nube_conectada")
                    : IdiomaUtil.obtener("asistente.pg.nube_disponible");
            filaDatos.getChildren().add(crearDatoCard(
                    IdiomaUtil.obtener("asistente.pg.nube_estado"), estado));
            if (nubeConectada) {
                filaDatos.getChildren().add(crearDatoCard(
                        IdiomaUtil.obtener("asistente.pg.nombre_negocio"), nubeExistente.getBusinessId()));
            }
        } else {
            Label noUri = new Label(nubeConectada
                    ? "✓  " + IdiomaUtil.obtener("asistente.pg.nube_conectada")
                    : IdiomaUtil.obtener("asistente.pg.nube_click_configurar"));
            noUri.getStyleClass().add("texto-secundario");
            noUri.setStyle("-fx-text-fill: " + (nubeConectada ? "#6c5ce7" : "#999") + ";");
            filaDatos.getChildren().add(noUri);
            if (nubeConectada) {
                filaDatos.getChildren().add(crearDatoCard(
                        IdiomaUtil.obtener("asistente.pg.nombre_negocio"), nubeExistente.getBusinessId()));
            }
        }

        card.getChildren().addAll(filaTop, filaDatos);

        // Desconectar (solo cuando está conectada)
        if (nubeConectada) {
            Region sep = new Region();
            sep.setMinHeight(1); sep.setMaxHeight(1);
            sep.setStyle("-fx-background-color: #1a1a1a;");

            HBox filaAcciones = new HBox(8);
            filaAcciones.setAlignment(Pos.CENTER_RIGHT);

            Button btnDesconectar = new Button(IdiomaUtil.obtener("asistente.pg.nube_desconectar"));
            btnDesconectar.getStyleClass().add("texto-hint-sm");
            btnDesconectar.setStyle("-fx-background-color: transparent; -fx-text-fill: #8b0000; " +
                    "-fx-border-color: #8b0000; -fx-padding: 6 14; " +
                    "-fx-cursor: hand; -fx-background-radius: 6; -fx-border-radius: 6;");
            btnDesconectar.setOnAction(e -> {
                e.consume();
                ConfiguracionBd.eliminar(nubeExistente.getId());
                actualizarSyncHabilitado(false);
                setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.nube_desconectada_ok"), "#a8b991");
                mostrarListaBds();
            });
            filaAcciones.getChildren().add(btnDesconectar);
            card.getChildren().addAll(sep, filaAcciones);
        }

        // Click handlers — siempre clickeable
        if (nubeConectada) {
            card.setOnMouseClicked(e -> {
                if (e.getTarget() instanceof Button) return;
                mostrarDetalleNube(nubeExistente, cluster, atlasUri);
            });
        } else {
            card.setOnMouseClicked(e -> {
                if (e.getTarget() instanceof Button || e.getTarget() instanceof TextField) return;
                toggleFormularioInlineNube(card, cluster);
            });
        }
        card.setOnMouseEntered(e -> card.setStyle(ESTILO_CARD +
                " -fx-border-color: " + (nubeConectada ? "#6c5ce7" : "#333") +
                "; -fx-cursor: hand; -fx-translate-y: -1;"));
        card.setOnMouseExited(e -> card.setStyle(ESTILO_CARD +
                " -fx-border-color: " + bordeColor + "; -fx-cursor: hand;"));

        return card;
    }

    /** Muestra/oculta el formulario inline para conectar la nube dentro de la card. */
    private void toggleFormularioInlineNube(VBox card, String cluster) {
        String formId = "nube-inline-form";
        boolean eliminado = card.getChildren().removeIf(child -> formId.equals(child.getId()));
        if (eliminado) return;

        VBox form = new VBox(8);
        form.setId(formId);
        form.setStyle("-fx-padding: 8 0 0 0;");

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: rgba(108,92,231,0.3);");

        Label lbl = crearLabel(IdiomaUtil.obtener("asistente.pg.nombre_negocio"));
        TextField campoNegocio = crearCampo("");
        campoNegocio.setPromptText(IdiomaUtil.obtener("asistente.pg.placeholder_negocio"));

        Label lblError = new Label("");
        lblError.getStyleClass().add("texto-hint-sm");
        lblError.setStyle("-fx-text-fill: #ef4444;");
        lblError.setVisible(false);
        lblError.setManaged(false);

        Button btnConectar = new Button(IdiomaUtil.obtener("asistente.pg.nube_conectar"));
        btnConectar.getStyleClass().add("texto-secundario-sm");
        btnConectar.setStyle("-fx-background-color: linear-gradient(to bottom, #6c5ce7, #5a4bd1); " +
                "-fx-text-fill: #fff; -fx-font-weight: 700; -fx-padding: 8 18; " +
                "-fx-cursor: hand; -fx-background-radius: 6; -fx-border-radius: 6;");
        btnConectar.setOnAction(e -> {
            e.consume();
            String negocio = campoNegocio.getText().trim();

            if (negocio.isEmpty()) {
                lblError.setText(IdiomaUtil.obtener("asistente.pg.negocio_requerido"));
                lblError.setVisible(true); lblError.setManaged(true);
                campoNegocio.setStyle(ESTILO_CAMPO_ERROR);
                return;
            }
            if (ConfiguracionBd.existeBusinessId(negocio)) {
                lblError.setText(IdiomaUtil.obtener("asistente.pg.negocio_duplicado"));
                lblError.setVisible(true); lblError.setManaged(true);
                campoNegocio.setStyle(ESTILO_CAMPO_ERROR);
                return;
            }

            btnConectar.setDisable(true);

            ConfiguracionBd nube = new ConfiguracionBd(negocio, negocio,
                    cluster.isEmpty() ? "Atlas" : cluster);
            ConfiguracionBd.agregar(nube);
            ConfiguracionCliente.setBusinessId(negocio);
            actualizarSyncHabilitado(true);
            actualizarMongoDbNameEnEnv(negocio);
            setEstado(IdiomaUtil.obtener("asistente.pg.reiniciando_servidor"), "#daa520");

            reiniciarServidorEmbebido().thenRunAsync(() -> {
                // Tras reinicio, notificar toggle + forzar verificación de conexión
                notificarServidorSyncToggle(true);
                forzarVerificacionConexionNube();
                setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.nube_conectada_ok"), "#a8b991");
                mostrarListaBds();
            }, Platform::runLater).exceptionally(ex -> {
                Platform.runLater(() -> {
                    // Si restart falla, el servidor existente sigue vivo — solo toggle + verificar
                    notificarServidorSyncToggle(true);
                    forzarVerificacionConexionNube();
                    setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.nube_conectada_ok"), "#a8b991");
                    mostrarListaBds();
                });
                return null;
            });
        });

        HBox filaBtn = new HBox(btnConectar);
        filaBtn.setAlignment(Pos.CENTER_RIGHT);

        form.getChildren().addAll(divider, lbl, campoNegocio, lblError, filaBtn);
        card.getChildren().add(form);
    }

    /** Muestra el panel simplificado para crear una nueva configuración de BD. */
    private void mostrarCrearBdSimplificado() {
        contenidoPasos.getChildren().clear();
        ocultarIndicadorPasos();

        Label titulo = new Label("＋  " + IdiomaUtil.obtener("asistente.pg.nueva_bd_titulo"));
        titulo.getStyleClass().add("modal-titulo-dorado");

        Label info = new Label(IdiomaUtil.obtener("asistente.pg.nueva_bd_info"));
        info.getStyleClass().add("texto-secundario");
        info.setStyle("-fx-text-fill: #aaa; -fx-wrap-text: true;");
        info.setWrapText(true);

        VBox cardForm = new VBox(12);
        cardForm.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("\uD83D\uDDC3  " + IdiomaUtil.obtener("asistente.pg.paso.crear"));
        seccionTitulo.getStyleClass().add("texto-info");
        seccionTitulo.setStyle(ESTILO_SECCION_TITULO);

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: #222;");

        TextField campoNombreBd = crearCampo("");
        campoNombreBd.setPromptText("kipu_db");

        PasswordField campoPasswordBd = new PasswordField();
        campoPasswordBd.getStyleClass().add("texto-info");
        campoPasswordBd.setStyle(ESTILO_CAMPO);
        campoPasswordBd.setPrefWidth(280);
        agregarFocusStyle(campoPasswordBd);

        PasswordField campoAdminPassword = new PasswordField();
        campoAdminPassword.getStyleClass().add("texto-info");
        campoAdminPassword.setStyle(ESTILO_CAMPO);
        campoAdminPassword.setPrefWidth(280);
        campoAdminPassword.setPromptText("••••••");
        agregarFocusStyle(campoAdminPassword);

        Label hintAdmin = new Label(IdiomaUtil.obtener("asistente.pg.hint_admin_pg"));
        hintAdmin.getStyleClass().add("texto-hint");
        hintAdmin.setStyle("-fx-wrap-text: true;");
        hintAdmin.setWrapText(true);

        TextField campoNegocio = crearCampo("");
        campoNegocio.setPromptText(IdiomaUtil.obtener("asistente.pg.placeholder_negocio"));

        ToggleSwitch toggleGuardar = new ToggleSwitch();
        Label lblToggle = new Label(IdiomaUtil.obtener("asistente.pg.guardar_passwords"));
        lblToggle.getStyleClass().add("texto-secundario");
        lblToggle.setStyle("-fx-text-fill: #e8e8e8;");
        HBox toggleRow = new HBox(10, toggleGuardar, lblToggle);
        toggleRow.setAlignment(Pos.CENTER_LEFT);

        Label hintToggle = new Label(IdiomaUtil.obtener("asistente.pg.guardar_passwords_hint"));
        hintToggle.getStyleClass().add("texto-hint");
        hintToggle.setStyle("-fx-wrap-text: true;");
        hintToggle.setWrapText(true);

        Label notaUnico = new Label("⚠  " + IdiomaUtil.obtener("asistente.pg.negocio_unico"));
        notaUnico.getStyleClass().add("texto-hint");
        notaUnico.setStyle("-fx-text-fill: #daa520; -fx-wrap-text: true;");
        notaUnico.setWrapText(true);

        Label lblError = new Label("");
        lblError.getStyleClass().add("texto-hint-sm");
        lblError.setStyle("-fx-text-fill: #ef4444;");
        lblError.setVisible(false);
        lblError.setManaged(false);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        int fila = 0;
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.nombre_bd")), 0, fila);
        grid.add(crearCampoConDefault(campoNombreBd, "kipu_db"), 1, fila++);
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.password_app")), 0, fila);
        grid.add(campoPasswordBd, 1, fila++);
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.nombre_negocio")), 0, fila);
        grid.add(campoNegocio, 1, fila++);
        grid.add(crearLabel(IdiomaUtil.obtener("asistente.pg.admin_pg_password")), 0, fila);
        VBox adminBox = new VBox(4, campoAdminPassword, hintAdmin);
        grid.add(adminBox, 1, fila);

        HBox botones = new HBox(10);
        botones.setAlignment(Pos.CENTER_RIGHT);

        Button btnCancelar = new Button(IdiomaUtil.obtener("asistente.pg.cancelar"));
        btnCancelar.getStyleClass().add("texto-secundario-sm");
        btnCancelar.setStyle(ESTILO_BOTON_TERCIARIO);
        btnCancelar.setOnAction(e -> mostrarListaBds());

        Button btnCrear = new Button(IdiomaUtil.obtener("asistente.pg.crear_config"));
        btnCrear.getStyleClass().add("panel-seccion-titulo");
        btnCrear.setStyle(ESTILO_BOTON_PRINCIPAL + " -fx-padding: 10 22;");
        btnCrear.getStyleClass().add("panel-btn-principal");
        btnCrear.setOnAction(e -> {
            String nombre = campoNombreBd.getText().trim();
            String password = campoPasswordBd.getText();
            String negocio = campoNegocio.getText().trim();
            String adminPass = campoAdminPassword.getText();

            if (nombre.isEmpty() || password.isEmpty() || negocio.isEmpty() || adminPass.isEmpty()) {
                lblError.setText(IdiomaUtil.obtener("asistente.pg.campos_vacios"));
                lblError.setVisible(true); lblError.setManaged(true);
                return;
            }
            if (!nombre.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                lblError.setText(IdiomaUtil.obtener("asistente.pg.nombre_invalido"));
                lblError.setVisible(true); lblError.setManaged(true);
                campoNombreBd.setStyle(ESTILO_CAMPO_ERROR);
                return;
            }
            if (ConfiguracionBd.existeBusinessId(negocio)) {
                lblError.setText(IdiomaUtil.obtener("asistente.pg.negocio_duplicado"));
                lblError.setVisible(true); lblError.setManaged(true);
                campoNegocio.setStyle(ESTILO_CAMPO_ERROR);
                return;
            }

            btnCrear.setDisable(true);
            btnCancelar.setDisable(true);
            boolean guardarPasswords = toggleGuardar.isSelected();

            // Paso 1: Crear rol y BD en PostgreSQL
            setEstado(IdiomaUtil.obtener("asistente.pg.creando_bd"), "#daa520");

            CompletableFuture.runAsync(() -> {
                // Crear rol PostgreSQL
                String sqlCrearUsuario = String.format(
                        "DO $$ BEGIN " +
                        "IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN " +
                        "CREATE ROLE \"%s\" WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE; " +
                        "ELSE ALTER ROLE \"%s\" WITH PASSWORD '%s'; " +
                        "END IF; END $$;",
                        nombre, nombre, password.replace("'", "''"),
                        nombre, password.replace("'", "''"));

                String err = DetectorDependencias.ejecutarSqlAdmin(
                        "localhost", 5432, "postgres", adminPass, "postgres", sqlCrearUsuario);
                if (err != null) throw new RuntimeException("Error creando usuario: " + err);

                // Crear base de datos
                String errDb = DetectorDependencias.ejecutarSqlAdmin(
                        "localhost", 5432, "postgres", adminPass, "postgres",
                        String.format("CREATE DATABASE \"%s\" OWNER \"%s\" ENCODING 'UTF8'", nombre, nombre));
                if (errDb != null && !errDb.contains("already exists") && !errDb.contains("ya existe")) {
                    throw new RuntimeException("Error creando BD: " + errDb);
                }

                // Asignar owner si ya existía
                DetectorDependencias.ejecutarSqlAdmin("localhost", 5432, "postgres", adminPass, "postgres",
                        String.format("ALTER DATABASE \"%s\" OWNER TO \"%s\"", nombre, nombre));

                // Aplicar permisos sobre schema public
                String sqlPermisos = String.format(
                        "ALTER SCHEMA public OWNER TO \"%s\"; " +
                        "GRANT ALL ON SCHEMA public TO \"%s\"; " +
                        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"%s\"; " +
                        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO \"%s\"; " +
                        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO \"%s\";",
                        nombre, nombre, nombre, nombre, nombre);
                DetectorDependencias.ejecutarSqlAdmin(
                        "localhost", 5432, "postgres", adminPass, nombre, sqlPermisos);

                // Verificar conexión con el nuevo usuario
                var testConexion = DetectorDependencias.verificarConexionJdbc(
                        "localhost", 5432, nombre, password, nombre);
                if (!testConexion.encontrado()) {
                    throw new RuntimeException(IdiomaUtil.obtener("asistente.pg.error_conexion_nueva"));
                }

                // Paso 2: Generar .env
                try {
                    Path dirKipu = Paths.get(System.getProperty("user.home"), ".kipu");
                    Files.createDirectories(dirKipu);

                    byte[] secretBytes = new byte[48];
                    new SecureRandom().nextBytes(secretBytes);
                    String jwtSecret = Base64.getEncoder().encodeToString(secretBytes);

                    StringBuilder env = new StringBuilder();
                    env.append("# Configuración Kipu - Generado por asistente GUI\n");
                    env.append("DB_HOST='localhost'\n");
                    env.append("DB_PORT='5432'\n");
                    env.append("DB_NAME='").append(nombre).append("'\n");
                    env.append("DB_USER='").append(nombre).append("'\n");
                    env.append("DB_PASSWORD='").append(password).append("'\n");
                    env.append("JWT_SECRET='").append(jwtSecret).append("'\n");
                    env.append("BUSINESS_ID='").append(negocio).append("'\n");
                    env.append("SYNC_HABILITADO='").append(leerSyncHabilitadoActual()).append("'\n");

                    String atlasUri = ConfiguracionCliente.getAtlasUriEmbebida();
                    if (atlasUri != null && !atlasUri.isEmpty()) {
                        env.append("MONGODB_DB_NAME='kipu_").append(
                                negocio.toLowerCase().replaceAll("[^a-z0-9_]", "_")).append("'\n");
                    }

                    Path envFile = dirKipu.resolve(".env");
                    Files.writeString(envFile, env.toString());
                    envFile.toFile().setReadable(false, false);
                    envFile.toFile().setReadable(true, true);
                    envFile.toFile().setWritable(false, false);
                    envFile.toFile().setWritable(true, true);

                    ConfiguracionCliente.setSetupPostgresCompletado(true);
                    ConfiguracionCliente.setBusinessId(negocio);

                    ConfiguracionBd nueva = new ConfiguracionBd(
                            negocio, "localhost", 5432, nombre, nombre, negocio);
                    ConfiguracionBd.activar("");
                    nueva.setActiva(true);
                    ConfiguracionBd.agregar(nueva);

                } catch (Exception ex) {
                    throw new RuntimeException(ex.getMessage(), ex);
                }
            }).thenComposeAsync(v -> {
                // Paso 3: Reiniciar servidor para aplicar migraciones
                Platform.runLater(() -> setEstado(
                        IdiomaUtil.obtener("asistente.pg.reiniciando_servidor"), "#daa520"));
                return reiniciarServidorEmbebido();
            }).thenRunAsync(() -> {
                setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.config_creada"), "#a8b991");
                if (guardarPasswords) {
                    guardarArchivoCredenciales(nombre, password, negocio);
                }
                mostrarListaBds();
            }, Platform::runLater)
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setEstado("✗ " + msg, "#ef4444");
                    btnCrear.setDisable(false);
                    btnCancelar.setDisable(false);
                });
                return null;
            });
        });

        botones.getChildren().addAll(btnCancelar, btnCrear);

        cardForm.getChildren().addAll(seccionTitulo, divider, grid, new Region(),
                toggleRow, hintToggle, notaUnico, lblError, botones);
        contenidoPasos.getChildren().addAll(titulo, info, cardForm);

        btnSiguiente.setText("← " + IdiomaUtil.obtener("asistente.pg.volver_lista"));
        btnSiguiente.setDisable(false);
        btnSiguiente.setOnAction(e -> mostrarListaBds());
    }

    /** Guarda las credenciales en un archivo en la ubicación elegida por el usuario. */
    private void guardarArchivoCredenciales(String nombreBd, String password, String negocio) {
        Window ventana = overlay.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(IdiomaUtil.obtener("asistente.pg.guardar_passwords_titulo"));
        chooser.setInitialFileName("kipu-credenciales-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File archivo = chooser.showSaveDialog(ventana);
        if (archivo == null) return;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ================================================\n");
            sb.append("# Kipu - Credenciales de Base de Datos\n");
            sb.append("# Generado: ").append(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            sb.append("# ⚠ MANTENGA ESTE ARCHIVO EN UN LUGAR SEGURO\n");
            sb.append("# ================================================\n\n");

            sb.append("[PostgreSQL]\n");
            sb.append("Host       = localhost\n");
            sb.append("Puerto     = 5432\n");
            sb.append("Base Datos = ").append(nombreBd).append("\n");
            sb.append("Usuario    = ").append(nombreBd).append("\n");
            sb.append("Contraseña = ").append(password).append("\n");
            sb.append("\n");
            sb.append("[Negocio]\n");
            sb.append("Nombre     = ").append(negocio).append("\n");

            Files.writeString(archivo.toPath(), sb.toString());
            archivo.setReadable(false, false);
            archivo.setReadable(true, true);
            archivo.setWritable(false, false);
            archivo.setWritable(true, true);

            setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.credenciales_guardadas"), "#a8b991");
            logger.info("Credenciales guardadas en: {}", archivo.getAbsolutePath());
        } catch (Exception ex) {
            setEstado("✗ " + ex.getMessage(), "#ef4444");
            logger.error("Error guardando credenciales: {}", ex.getMessage());
        }
    }

    /** Muestra el detalle de la configuración de nube en un sub-panel. */
    private void mostrarDetalleNube(ConfiguracionBd nubeConfig, String cluster, String atlasUri) {
        contenidoPasos.getChildren().clear();

        VBox card = new VBox(10);
        card.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("☁  " + IdiomaUtil.obtener("asistente.pg.nube_detalle"));
        seccionTitulo.getStyleClass().add("panel-seccion-titulo");
        seccionTitulo.setStyle("-fx-text-fill: #6c5ce7;");

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: rgba(108,92,231,0.2);");

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(8);
        int fila = 0;
        grid.add(crearResumenLabel("Cluster"), 0, fila);
        grid.add(crearResumenValor(cluster.isEmpty() ? "Atlas" : cluster), 1, fila++);
        grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nube_estado")), 0, fila);
        if (nubeConfig != null) {
            grid.add(crearResumenValor("✓ " + IdiomaUtil.obtener("asistente.pg.nube_conectada")), 1, fila++);
            grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nombre_negocio")), 0, fila);
            grid.add(crearResumenValor(nubeConfig.getBusinessId()), 1, fila++);
            grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nube_fecha")), 0, fila);
            grid.add(crearResumenValor(nubeConfig.getFechaCreacion()), 1, fila++);
        } else {
            grid.add(crearResumenValor(IdiomaUtil.obtener("asistente.pg.nube_disponible")), 1, fila++);
        }
        grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nube_uri")), 0, fila);
        // Mostrar URI enmascarada por seguridad
        String uriMasked = atlasUri.length() > 30
                ? atlasUri.substring(0, 20) + "•••" + atlasUri.substring(atlasUri.length() - 15)
                : "•••";
        grid.add(crearResumenValor(uriMasked), 1, fila);

        Button btnVolver = new Button("← " + IdiomaUtil.obtener("asistente.pg.volver_lista"));
        btnVolver.getStyleClass().add("texto-secundario-sm");
        btnVolver.setStyle(ESTILO_BOTON_TERCIARIO);
        btnVolver.setOnAction(e -> mostrarListaBds());

        card.getChildren().addAll(seccionTitulo, divider, grid, new Region(), btnVolver);
        contenidoPasos.getChildren().add(card);
    }

    /** Muestra el detalle de una BD PostgreSQL en un sub-panel. */
    private void mostrarDetalleBd(ConfiguracionBd bd) {
        contenidoPasos.getChildren().clear();

        VBox card = new VBox(10);
        card.setStyle(ESTILO_CARD);

        Label seccionTitulo = new Label("\uD83D\uDDC3  " + bd.getAlias());
        seccionTitulo.getStyleClass().add("panel-seccion-titulo");
        seccionTitulo.setStyle("-fx-text-fill: " + (bd.isActiva() ? "#d4af37" : "#e8e8e8") + ";");

        Region divider = new Region();
        divider.setMinHeight(1); divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: rgba(212,175,55,0.2);");

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(8);
        int fila = 0;
        grid.add(crearResumenLabel("Host"), 0, fila);
        grid.add(crearResumenValor(bd.getHost() + ":" + bd.getPuerto()), 1, fila++);
        grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nombre_bd")), 0, fila);
        grid.add(crearResumenValor(bd.getNombreBd()), 1, fila++);
        grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.usuario_app")), 0, fila);
        grid.add(crearResumenValor(bd.getUsuario()), 1, fila++);
        String bId = bd.getBusinessId();
        if (bId != null && !bId.isEmpty()) {
            grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nombre_negocio")), 0, fila);
            grid.add(crearResumenValor(bId), 1, fila++);
        }
        grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nube_fecha")), 0, fila);
        grid.add(crearResumenValor(bd.getFechaCreacion()), 1, fila++);
        grid.add(crearResumenLabel(IdiomaUtil.obtener("asistente.pg.nube_estado")), 0, fila);
        grid.add(crearResumenValor(bd.isActiva()
                ? "✓ " + IdiomaUtil.obtener("asistente.pg.activa")
                : IdiomaUtil.obtener("asistente.pg.detalle_inactiva")), 1, fila);

        // Acciones
        HBox acciones = new HBox(10);
        acciones.setAlignment(Pos.CENTER_RIGHT);

        Button btnVolver = new Button("← " + IdiomaUtil.obtener("asistente.pg.volver_lista"));
        btnVolver.getStyleClass().add("texto-secundario-sm");
        btnVolver.setStyle(ESTILO_BOTON_TERCIARIO);
        btnVolver.setOnAction(e -> mostrarListaBds());

        if (!bd.isActiva()) {
            Button btnConectar = new Button(IdiomaUtil.obtener("asistente.pg.conectar_bd"));
            btnConectar.getStyleClass().addAll("panel-btn-principal", "panel-seccion-titulo");
            btnConectar.setStyle(ESTILO_BOTON_PRINCIPAL + " -fx-padding: 8 18;");
            btnConectar.setOnAction(e -> {
                activarBd(bd);
                mostrarDetalleBd(bd);
            });
            acciones.getChildren().add(btnConectar);
        }

        Button btnEliminar = new Button(IdiomaUtil.obtener("asistente.pg.eliminar_bd"));
        btnEliminar.getStyleClass().add("panel-btn-eliminar");
        btnEliminar.setStyle("-fx-background-color: transparent; -fx-text-fill: #8b0000; " +
                "-fx-border-color: #8b0000; -fx-padding: 8 18; " +
                "-fx-cursor: hand; -fx-background-radius: 6; -fx-border-radius: 6;");
        btnEliminar.setOnAction(e -> {
            if (bd.isActiva()) {
                setEstado("✗ " + IdiomaUtil.obtener("asistente.pg.no_eliminar_activa"), "#daa520");
            } else {
                ConfiguracionBd.eliminar(bd.getId());
                setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.bd_eliminada"), "#a8b991");
                mostrarListaBds();
            }
        });
        acciones.getChildren().add(btnEliminar);

        card.getChildren().addAll(seccionTitulo, divider, grid, new Region(), acciones, btnVolver);
        contenidoPasos.getChildren().add(card);
    }

    /** Crea una tarjeta visual clickable para una configuración de BD PostgreSQL. */
    private VBox crearCardBd(ConfiguracionBd bd) {
        VBox card = new VBox(8);
        String bordeColor = bd.isActiva() ? "#d4af37" : "#222";
        card.setStyle(ESTILO_CARD + " -fx-border-color: " + bordeColor + "; -fx-cursor: hand;");

        // Fila superior: icono + alias + badge activa + tipo
        HBox filaTop = new HBox(8);
        filaTop.setAlignment(Pos.CENTER_LEFT);

        Label iconoPg = new Label("\uD83D\uDDC3");
        iconoPg.getStyleClass().add("icono-texto-sm");
        Label lblAlias = new Label(bd.getAlias());
        lblAlias.getStyleClass().add("panel-seccion-titulo");
        lblAlias.setStyle("-fx-text-fill: " + (bd.isActiva() ? "#d4af37" : "#e8e8e8") + ";");

        filaTop.getChildren().addAll(iconoPg, lblAlias);

        if (bd.isActiva()) {
            Label badge = new Label("● " + IdiomaUtil.obtener("asistente.pg.activa"));
            badge.getStyleClass().add("badge-texto");
            badge.setStyle("-fx-text-fill: #a8b991; " +
                    "-fx-background-color: rgba(168,185,145,0.15); -fx-padding: 2 8; " +
                    "-fx-background-radius: 10; -fx-border-color: rgba(168,185,145,0.3); -fx-border-radius: 10;");
            filaTop.getChildren().add(badge);
        }

        Region espacioTop = new Region();
        HBox.setHgrow(espacioTop, Priority.ALWAYS);

        Label tipoLabel = new Label("PostgreSQL");
        tipoLabel.getStyleClass().add("texto-hint-sm");
        tipoLabel.setStyle("-fx-text-fill: #555; -fx-font-style: italic;");
        Label lblFecha = new Label(bd.getFechaCreacion());
        lblFecha.getStyleClass().add("texto-hint-sm");
        lblFecha.setStyle("-fx-text-fill: #555;");
        VBox metaRight = new VBox(1, tipoLabel, lblFecha);
        metaRight.setAlignment(Pos.CENTER_RIGHT);
        filaTop.getChildren().addAll(espacioTop, metaRight);

        // Fila de datos
        HBox filaDatos = new HBox(16);
        filaDatos.setAlignment(Pos.CENTER_LEFT);
        filaDatos.getChildren().addAll(
                crearDatoCard("Host", bd.getHost() + ":" + bd.getPuerto()),
                crearDatoCard("BD", bd.getNombreBd()),
                crearDatoCard(IdiomaUtil.obtener("asistente.pg.usuario_app"), bd.getUsuario())
        );
        String bId = bd.getBusinessId();
        if (bId != null && !bId.isEmpty()) {
            filaDatos.getChildren().add(crearDatoCard(
                    IdiomaUtil.obtener("asistente.pg.nombre_negocio"), bId));
        }

        // Fila de acciones
        HBox filaAcciones = new HBox(8);
        filaAcciones.setAlignment(Pos.CENTER_RIGHT);

        if (!bd.isActiva()) {
            Button btnConectar = new Button(IdiomaUtil.obtener("asistente.pg.conectar_bd"));
            btnConectar.getStyleClass().addAll("panel-btn-principal", "panel-seccion-titulo");
            btnConectar.setStyle(ESTILO_BOTON_PRINCIPAL + " -fx-padding: 6 14;");
            btnConectar.setOnAction(e -> {
                e.consume();
                activarBd(bd);
            });
            filaAcciones.getChildren().add(btnConectar);
        }

        Button btnEliminar = new Button(IdiomaUtil.obtener("asistente.pg.eliminar_bd"));
        btnEliminar.getStyleClass().add("panel-btn-eliminar");
        btnEliminar.setStyle("-fx-background-color: transparent; -fx-text-fill: #8b0000; " +
                "-fx-border-color: #8b0000; -fx-padding: 6 14; " +
                "-fx-cursor: hand; -fx-background-radius: 6; -fx-border-radius: 6;");
        btnEliminar.setOnAction(e -> {
            e.consume();
            eliminarBd(bd);
        });
        filaAcciones.getChildren().add(btnEliminar);

        // Separador fino
        Region sep = new Region();
        sep.setMinHeight(1); sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #1a1a1a;");

        card.getChildren().addAll(filaTop, filaDatos, sep, filaAcciones);

        // Click para abrir detalle
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button) return;
            mostrarDetalleBd(bd);
        });
        card.setOnMouseEntered(e -> card.setStyle(ESTILO_CARD +
                " -fx-border-color: " + (bd.isActiva() ? "#d4af37" : "#333") +
                "; -fx-cursor: hand; -fx-translate-y: -1;"));
        card.setOnMouseExited(e -> card.setStyle(ESTILO_CARD +
                " -fx-border-color: " + bordeColor + "; -fx-cursor: hand;"));

        return card;
    }

    /** Crea un par label/valor pequeño para la tarjeta. */
    private VBox crearDatoCard(String label, String valor) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("texto-hint-sm");
        lbl.setStyle("-fx-text-fill: #555;");
        Label val = new Label(valor);
        val.getStyleClass().add("dato-valor");
        val.setStyle("-fx-text-fill: #ccc;");
        return new VBox(1, lbl, val);
    }

    /** Activa una BD: actualiza .env y recarga la lista. */
    private void activarBd(ConfiguracionBd bd) {
        try {
            ConfiguracionBd.activar(bd.getId());
            actualizarEnv(bd);
            ConfiguracionCliente.setBusinessId(bd.getBusinessId() != null ? bd.getBusinessId() : "");
            setEstado(IdiomaUtil.obtener("asistente.pg.reiniciando_servidor"), "#daa520");

            reiniciarServidorEmbebido().thenRunAsync(() -> {
                setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.bd_conectada"), "#a8b991");
                mostrarListaBds();
            }, Platform::runLater).exceptionally(ex -> {
                Platform.runLater(() -> {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setEstado("✗ " + msg, "#ef4444");
                });
                return null;
            });
        } catch (Exception ex) {
            setEstado("✗ " + ex.getMessage(), "#ef4444");
        }
    }

    /** Actualiza el archivo .env con los datos de la BD seleccionada. */
    private void actualizarEnv(ConfiguracionBd bd) {
        try {
            Path envFile = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
            if (!Files.exists(envFile)) return;

            // Leer .env existente, actualizar solo las claves de BD
            List<String> lineas = Files.readAllLines(envFile);
            StringBuilder nuevo = new StringBuilder();
            for (String linea : lineas) {
                String lt = linea.trim();
                if (lt.startsWith("DB_HOST=")) {
                    nuevo.append("DB_HOST='").append(bd.getHost()).append("'\n");
                } else if (lt.startsWith("DB_PORT=")) {
                    nuevo.append("DB_PORT='").append(bd.getPuerto()).append("'\n");
                } else if (lt.startsWith("DB_NAME=")) {
                    nuevo.append("DB_NAME='").append(bd.getNombreBd()).append("'\n");
                } else if (lt.startsWith("DB_USER=")) {
                    nuevo.append("DB_USER='").append(bd.getUsuario()).append("'\n");
                } else if (lt.startsWith("BUSINESS_ID=")) {
                    String bId = bd.getBusinessId();
                    if (bId != null && !bId.isEmpty()) {
                        nuevo.append("BUSINESS_ID='").append(bId).append("'\n");
                    }
                } else if (lt.startsWith("MONGODB_DB_NAME=")) {
                    String bId = bd.getBusinessId();
                    if (bId != null && !bId.isEmpty()) {
                        nuevo.append("MONGODB_DB_NAME='kipu_").append(
                                bId.toLowerCase().replaceAll("[^a-z0-9_]", "_")).append("'\n");
                    }
                } else {
                    nuevo.append(linea).append("\n");
                }
            }
            Files.writeString(envFile, nuevo.toString());
            envFile.toFile().setReadable(false, false);
            envFile.toFile().setReadable(true, true);
            envFile.toFile().setWritable(false, false);
            envFile.toFile().setWritable(true, true);
        } catch (Exception e) {
            logger.error("Error actualizando .env: {}", e.getMessage());
        }
    }

    /** Actualiza SYNC_HABILITADO en el archivo .env y notifica al servidor en runtime. */
    private void actualizarSyncHabilitado(boolean habilitado) {
        try {
            Path envFile = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
            if (!Files.exists(envFile)) return;

            String valor = habilitado ? "true" : "false";
            List<String> lineas = Files.readAllLines(envFile);
            StringBuilder nuevo = new StringBuilder();
            boolean encontrado = false;
            for (String linea : lineas) {
                if (linea.trim().startsWith("SYNC_HABILITADO=")) {
                    nuevo.append("SYNC_HABILITADO='").append(valor).append("'\n");
                    encontrado = true;
                } else {
                    nuevo.append(linea).append("\n");
                }
            }
            if (!encontrado) {
                nuevo.append("SYNC_HABILITADO='").append(valor).append("'\n");
            }
            Files.writeString(envFile, nuevo.toString());
        } catch (Exception e) {
            logger.error("Error actualizando SYNC_HABILITADO en .env: {}", e.getMessage());
        }

        // Notificar al servidor activo para que aplique el cambio en runtime
        notificarServidorSyncToggle(habilitado);
    }

    /** Llama POST /api/sync/toggle en el servidor para cambiar habilitado en runtime. */
    private void notificarServidorSyncToggle(boolean habilitado) {
        try {
            String urlBase = ConfiguracionCliente.getUrlServidor();
            if (urlBase == null || urlBase.isBlank()) return;

            URL url = URI.create(urlBase + "/api/sync/toggle?habilitado=" + habilitado).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            String token = ConfiguracionCliente.getTokenJwt();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            conn.setDoOutput(true);
            conn.getOutputStream().close();
            int code = conn.getResponseCode();
            conn.disconnect();

            logger.info("Servidor notificado: sync.nube.habilitado={} (HTTP {})", habilitado, code);
        } catch (Exception e) {
            logger.warn("No se pudo notificar al servidor sobre toggle sync: {}", e.getMessage());
        }
    }

    /** Elimina una BD tras confirmación. */
    private void eliminarBd(ConfiguracionBd bd) {
        if (bd.isActiva()) {
            setEstado("✗ " + IdiomaUtil.obtener("asistente.pg.no_eliminar_activa"), "#daa520");
            return;
        }
        ConfiguracionBd.eliminar(bd.getId());
        setEstado("✓ " + IdiomaUtil.obtener("asistente.pg.bd_eliminada"), "#a8b991");
        mostrarListaBds();
    }

    /** Fuerza una verificación real de conexión a MongoDB Atlas en el servidor. */
    private void forzarVerificacionConexionNube() {
        try {
            String urlBase = ConfiguracionCliente.getUrlServidor();
            if (urlBase == null || urlBase.isBlank()) return;

            URL url = URI.create(urlBase + "/api/sync/verificar-conexion").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            String token = ConfiguracionCliente.getTokenJwt();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            conn.setDoOutput(true);
            conn.getOutputStream().close();
            int code = conn.getResponseCode();
            conn.disconnect();

            logger.info("Verificación forzada de conexión nube: HTTP {}", code);
        } catch (Exception e) {
            logger.warn("No se pudo forzar verificación de conexión nube: {}", e.getMessage());
        }
    }

    /** Oculta los indicadores de progreso (steps) en modo lista. */
    private void ocultarIndicadorPasos() {
        if (indicadoresPaso != null) {
            for (Circle dot : indicadoresPaso) {
                dot.getParent().getParent().setVisible(false);
                dot.getParent().getParent().setManaged(false);
            }
        }
        if (lineasPaso != null) {
            for (Line linea : lineasPaso) {
                linea.getParent().setVisible(false);
                linea.getParent().setManaged(false);
            }
        }
    }

    /** Restaura los indicadores de progreso al volver al asistente. */
    private void restaurarIndicadorPasos() {
        if (indicadoresPaso != null) {
            for (Circle dot : indicadoresPaso) {
                dot.getParent().getParent().setVisible(true);
                dot.getParent().getParent().setManaged(true);
            }
        }
        if (lineasPaso != null) {
            for (Line linea : lineasPaso) {
                linea.getParent().setVisible(true);
                linea.getParent().setManaged(true);
            }
        }
    }

    /** Reinicia el servidor embebido (detener → iniciar) para aplicar nueva configuración .env. */
    private CompletableFuture<Boolean> reiniciarServidorEmbebido() {
        if (ServidorEmbebido.esHost()) {
            // Detener (funciona tanto con proceso propio como preexistente)
            return ServidorEmbebido.detenerServidor().thenComposeAsync(exitoApagado -> {
                if (!exitoApagado) {
                    logger.warn("No se pudo detener el servidor, intentando iniciar de todas formas");
                }
                return ServidorEmbebido.iniciarServidor(mensaje ->
                        Platform.runLater(() -> setEstado(mensaje, "#daa520"))
                );
            });
        }
        return CompletableFuture.completedFuture(true);
    }

    /** Lee el valor actual de SYNC_HABILITADO desde .env (para preservarlo al reescribir). */
    private String leerSyncHabilitadoActual() {
        try {
            Path envFile = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
            if (Files.exists(envFile)) {
                for (String linea : Files.readAllLines(envFile)) {
                    if (linea.trim().startsWith("SYNC_HABILITADO=")) {
                        String valor = linea.substring(linea.indexOf('=') + 1).trim();
                        if (valor.startsWith("'") && valor.endsWith("'")) {
                            valor = valor.substring(1, valor.length() - 1);
                        }
                        return valor;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo leer SYNC_HABILITADO actual: {}", e.getMessage());
        }
        return "false";
    }

    /** Guarda la URI de MongoDB Atlas en .env para que ServidorEmbebido la inyecte al servidor. */
    private void guardarMongoUriEnEnv(String uri) {
        try {
            Path envFile = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
            if (!Files.exists(envFile)) return;

            List<String> lineas = Files.readAllLines(envFile);
            StringBuilder nuevo = new StringBuilder();
            boolean encontrado = false;
            for (String linea : lineas) {
                if (linea.trim().startsWith("MONGODB_URI=")) {
                    nuevo.append("MONGODB_URI='").append(uri).append("'\n");
                    encontrado = true;
                } else {
                    nuevo.append(linea).append("\n");
                }
            }
            if (!encontrado) {
                nuevo.append("MONGODB_URI='").append(uri).append("'\n");
            }
            Files.writeString(envFile, nuevo.toString());
            // Limpiar cache para que getAtlasUriEmbebida() lo detecte
            ConfiguracionCliente.invalidarCacheAtlasUri();
            logger.info("MONGODB_URI guardada en .env");
        } catch (Exception e) {
            logger.error("Error guardando MONGODB_URI en .env: {}", e.getMessage());
        }
    }

    /** Actualiza o añade MONGODB_DB_NAME en .env basándose en el nombre del negocio. */
    private void actualizarMongoDbNameEnEnv(String negocio) {
        try {
            Path envFile = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
            if (!Files.exists(envFile)) return;

            String dbName = "kipu_" + negocio.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            List<String> lineas = Files.readAllLines(envFile);
            StringBuilder nuevo = new StringBuilder();
            boolean encontrado = false;
            for (String linea : lineas) {
                if (linea.trim().startsWith("MONGODB_DB_NAME=")) {
                    nuevo.append("MONGODB_DB_NAME='").append(dbName).append("'\n");
                    encontrado = true;
                } else {
                    nuevo.append(linea).append("\n");
                }
            }
            if (!encontrado) {
                nuevo.append("MONGODB_DB_NAME='").append(dbName).append("'\n");
            }
            Files.writeString(envFile, nuevo.toString());
        } catch (Exception e) {
            logger.error("Error actualizando MONGODB_DB_NAME en .env: {}", e.getMessage());
        }
    }
}
