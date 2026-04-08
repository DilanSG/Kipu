/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.servicio.LogCriticoServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NavegacionUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LogCriticoDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Handler para la herramienta "Logs del Sistema" en el menú de configuración.
 *
 * Muestra un modal con los logs críticos almacenados en la base de datos:
 * crashes, errores de renderizado, fallos graves que requieren atención.
 *
 * Funcionalidades:
 * - Lista los logs más recientes con nivel, origen, mensaje y fecha
 * - Filtro rápido: Todos / Errores / En Revisión / Resueltos
 * - Gestión de estados: NOTIFICACION_ERROR → EN_REVISION → RESUELTO
 * - Modal de detalle al hacer clic en un log con toda la información
 * - Botón de copiar al portapapeles en el modal de detalle
 * - Indicador visual por nivel (rojo=CRITICO, naranja=ERROR)
 * - Badge de estado con color (rojo=Error, amarillo=En revisión, verde=Resuelto)
 *
 * Solo accesible por rol ADMIN.
 *
 * @see LogCriticoServicio
 * @see LogCriticoDto
 */
public class LogsCriticosHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(LogsCriticosHandler.class);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm:ss", Locale.of("es", "ES"));

    /** Estado de notificación de error (nuevo, sin revisar) */
    private static final String ESTADO_ERROR = "NOTIFICACION_ERROR";
    /** Estado en revisión (siendo investigado) */
    private static final String ESTADO_EN_REVISION = "EN_REVISION";
    /** Estado resuelto */
    private static final String ESTADO_RESUELTO = "RESUELTO";

    private final GestorModales gestor;

    // Referencia al contenedor de logs para poder recargarlo
    private VBox contenedorLogs;
    private LogCriticoServicio servicio;

    /** Filtro activo: null=todos, o un valor de estado específico */
    private String filtroActual = null;

    /** Lista de botones de filtro para actualizar estilos */
    private Button btnFiltroTodos;
    private Button btnFiltroErrores;
    private Button btnFiltroRevision;
    private Button btnFiltroResueltos;

    /**
     * Crea el handler vinculado al gestor de modales del panel de configuración.
     *
     * @param gestor Gestor de modales para mostrar/cerrar overlays
     */
    public LogsCriticosHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Abriendo Logs del Sistema");

        // Obtener token del usuario actual
        AuthRespuestaDto usuario = NavegacionUtil.getUsuarioActual();
        if (usuario == null || usuario.getToken() == null) {
            logger.error("No hay sesión activa para acceder a logs");
            return;
        }
        servicio = new LogCriticoServicio(usuario.getToken());

        VBox modal = new VBox(12);
        modal.setMaxWidth(750);
        modal.setMaxHeight(560);
        modal.setPadding(new Insets(20));
        modal.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333; " +
                "-fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10;");

        // ── Header ──
        HBox header = crearHeader();

        // ── Separador ──
        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #d4af37;");

        // ── Barra de filtros ──
        HBox filtros = crearBarraFiltros();

        // ── Contenedor de logs (scrollable) ──
        contenedorLogs = new VBox(8);
        contenedorLogs.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane scroll = new ScrollPane(contenedorLogs);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1a1a1a; -fx-background-color: transparent; " +
                "-fx-border-color: transparent;");
        scroll.setPrefHeight(420);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // ── Estado de carga inicial ──
        Label cargando = new Label(IdiomaUtil.obtener("ctrl.logs.cargando"));
        cargando.getStyleClass().add("texto-info");
        cargando.setStyle("-fx-text-fill: #999;");
        contenedorLogs.setAlignment(Pos.CENTER);
        contenedorLogs.getChildren().add(cargando);

        modal.getChildren().addAll(header, sep, filtros, scroll);
        gestor.mostrarModal(modal);

        // Cargar logs de forma asíncrona
        cargarLogs();
    }

    // ── Crea el header del modal con título y botón cerrar ──
    private HBox crearHeader() {
        Label icono = new Label("\u26a0");
        icono.getStyleClass().add("icono-texto-md");
        icono.setStyle("-fx-text-fill: #d4af37;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.logs.titulo"));
        titulo.getStyleClass().add("tutorial-seccion-titulo");

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        Label cerrar = new Label("\u2715");
        cerrar.getStyleClass().add("tutorial-titulo");
        cerrar.setStyle("-fx-text-fill: #666; -fx-cursor: hand;");
        cerrar.setOnMouseClicked(e -> gestor.cerrarModalActual());
        cerrar.setOnMouseEntered(e ->
                cerrar.setStyle("-fx-text-fill: #f5f5f5; -fx-cursor: hand;"));
        cerrar.setOnMouseExited(e ->
                cerrar.setStyle("-fx-text-fill: #666; -fx-cursor: hand;"));

        HBox header = new HBox(10, icono, titulo, espacio, cerrar);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    // ── Barra de filtros: Todos / Errores / En Revisión / Resueltos + Recargar ──
    private HBox crearBarraFiltros() {
        String estiloActivo = "-fx-background-color: #d4af37; -fx-text-fill: #0a0a0a; " +
                "-fx-font-weight: 700; -fx-padding: 5 12; " +
                "-fx-background-radius: 4; -fx-cursor: hand;";
        String estiloInactivo = "-fx-background-color: #2a2a2a; -fx-text-fill: #999; " +
                "-fx-font-weight: 600; -fx-padding: 5 12; " +
                "-fx-background-radius: 4; -fx-border-color: #404040; -fx-border-radius: 4; -fx-cursor: hand;";
        String estiloRecargar = "-fx-background-color: transparent; -fx-text-fill: #d4af37; " +
                "-fx-font-weight: 600; -fx-padding: 5 12; -fx-cursor: hand;";

        btnFiltroTodos = new Button(IdiomaUtil.obtener("ctrl.logs.filtro.todos"));
        btnFiltroErrores = new Button(IdiomaUtil.obtener("ctrl.logs.filtro.errores"));
        btnFiltroRevision = new Button(IdiomaUtil.obtener("ctrl.logs.filtro.en_revision"));
        btnFiltroResueltos = new Button(IdiomaUtil.obtener("ctrl.logs.filtro.resueltos"));
        Button btnRecargar = new Button(IdiomaUtil.obtener("ctrl.logs.recargar"));

        btnFiltroTodos.getStyleClass().add("texto-hint-sm");
        btnFiltroErrores.getStyleClass().add("texto-hint-sm");
        btnFiltroRevision.getStyleClass().add("texto-hint-sm");
        btnFiltroResueltos.getStyleClass().add("texto-hint-sm");
        btnRecargar.getStyleClass().add("texto-hint-sm");

        btnFiltroTodos.setStyle(estiloActivo);
        btnFiltroErrores.setStyle(estiloInactivo);
        btnFiltroRevision.setStyle(estiloInactivo);
        btnFiltroResueltos.setStyle(estiloInactivo);
        btnRecargar.setStyle(estiloRecargar);

        btnFiltroTodos.setOnAction(e -> aplicarFiltro(null, estiloActivo, estiloInactivo));
        btnFiltroErrores.setOnAction(e -> aplicarFiltro(ESTADO_ERROR, estiloActivo, estiloInactivo));
        btnFiltroRevision.setOnAction(e -> aplicarFiltro(ESTADO_EN_REVISION, estiloActivo, estiloInactivo));
        btnFiltroResueltos.setOnAction(e -> aplicarFiltro(ESTADO_RESUELTO, estiloActivo, estiloInactivo));
        btnRecargar.setOnAction(e -> cargarLogs());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        HBox filtros = new HBox(6, btnFiltroTodos, btnFiltroErrores, btnFiltroRevision,
                btnFiltroResueltos, espacio, btnRecargar);
        filtros.setAlignment(Pos.CENTER_LEFT);
        filtros.setPadding(new Insets(6, 0, 2, 0));
        return filtros;
    }

    // ── Aplica un filtro y actualiza los estilos de todos los botones ──
    private void aplicarFiltro(String estado, String estiloActivo, String estiloInactivo) {
        filtroActual = estado;
        btnFiltroTodos.setStyle(estado == null ? estiloActivo : estiloInactivo);
        btnFiltroErrores.setStyle(ESTADO_ERROR.equals(estado) ? estiloActivo : estiloInactivo);
        btnFiltroRevision.setStyle(ESTADO_EN_REVISION.equals(estado) ? estiloActivo : estiloInactivo);
        btnFiltroResueltos.setStyle(ESTADO_RESUELTO.equals(estado) ? estiloActivo : estiloInactivo);
        cargarLogs();
    }

    // ── Carga los logs desde el servidor ──
    private void cargarLogs() {
        contenedorLogs.getChildren().clear();
        contenedorLogs.setAlignment(Pos.CENTER);
        Label cargando = new Label(IdiomaUtil.obtener("ctrl.logs.cargando"));
        cargando.getStyleClass().add("texto-info");
        cargando.setStyle("-fx-text-fill: #999;");
        contenedorLogs.getChildren().add(cargando);

        // Siempre cargamos todos y filtramos en el cliente para simplicidad
        servicio.listarTodosAsync()
                .thenAccept(logs -> Platform.runLater(() -> mostrarLogs(logs)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        contenedorLogs.getChildren().clear();
                        Label error = new Label(MessageFormat.format(
                                IdiomaUtil.obtener("ctrl.logs.error_cargar"), ex.getMessage()));
                        error.getStyleClass().add("texto-secundario-sm");
                        error.setStyle("-fx-text-fill: #8b0000;");
                        error.setWrapText(true);
                        contenedorLogs.getChildren().add(error);
                    });
                    logger.error("Error al cargar logs críticos", ex);
                    return null;
                });
    }

    // ── Renderiza la lista de logs en el contenedor ──
    private void mostrarLogs(List<LogCriticoDto> logs) {
        contenedorLogs.getChildren().clear();
        contenedorLogs.setAlignment(Pos.TOP_LEFT);

        // Aplicar filtro por estado si hay uno activo
        List<LogCriticoDto> logsFiltrados = logs;
        if (filtroActual != null && logs != null) {
            logsFiltrados = logs.stream()
                    .filter(l -> filtroActual.equals(l.getEstado()))
                    .toList();
        }

        if (logsFiltrados == null || logsFiltrados.isEmpty()) {
            contenedorLogs.setAlignment(Pos.CENTER);
            VBox vacio = new VBox(8);
            vacio.setAlignment(Pos.CENTER);

            Label iconoCheck = new Label("\u2713");
            iconoCheck.setStyle("-fx-font-size: 28px; -fx-text-fill: #a8b991;");

            Label msg = new Label(filtroActual != null
                    ? IdiomaUtil.obtener("ctrl.logs.vacio_pendientes")
                    : IdiomaUtil.obtener("ctrl.logs.vacio_todos"));
            msg.getStyleClass().add("texto-info");
            msg.setStyle("-fx-text-fill: #999;");

            vacio.getChildren().addAll(iconoCheck, msg);
            contenedorLogs.getChildren().add(vacio);
            return;
        }

        for (LogCriticoDto log : logsFiltrados) {
            contenedorLogs.getChildren().add(crearCardLog(log));
        }
    }

    // ── Crea una tarjeta visual para un log individual (clickeable para ver detalle) ──
    private VBox crearCardLog(LogCriticoDto log) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle("-fx-background-color: #222; -fx-background-radius: 6; " +
                "-fx-border-color: #333; -fx-border-radius: 6; -fx-cursor: hand;");

        // Atenuar visualmente los resueltos
        if (ESTADO_RESUELTO.equals(log.getEstado())) {
            card.setOpacity(0.6);
        }

        // ── Fila superior: indicador de nivel + origen + badge estado + fecha ──
        boolean esCritico = "CRITICO".equalsIgnoreCase(log.getNivel());
        Circle indicador = new Circle(4);
        indicador.setFill(esCritico ? Color.web("#8b0000") : Color.web("#daa520"));

        Label nivel = new Label(log.getNivel());
        nivel.getStyleClass().add("texto-hint");
        nivel.setStyle("-fx-font-weight: 700; -fx-text-fill: " +
                (esCritico ? "#ff4444" : "#daa520") + "; -fx-padding: 1 6; " +
                "-fx-background-color: " + (esCritico ? "#2a0a0a" : "#2a2206") + "; " +
                "-fx-background-radius: 3;");

        Label origen = new Label(log.getOrigen());
        origen.getStyleClass().add("texto-hint-sm");
        origen.setStyle("-fx-text-fill: #b0b0b0; -fx-font-weight: 600;");

        // Badge de estado con color
        Label badgeEstado = crearBadgeEstado(log.getEstado());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        Label fecha = new Label(log.getFechaCreacion() != null
                ? log.getFechaCreacion().format(FORMATO_FECHA) : "\u2014");
        fecha.getStyleClass().add("texto-hint");
        fecha.setStyle("-fx-text-fill: #666;");

        HBox filaTop = new HBox(6, indicador, nivel, origen, badgeEstado, espacio, fecha);
        filaTop.setAlignment(Pos.CENTER_LEFT);

        // ── Mensaje ──
        Label mensaje = new Label(log.getMensaje());
        mensaje.getStyleClass().add("texto-secundario-sm");
        mensaje.setStyle("-fx-text-fill: #e8e8e8; -fx-font-weight: 400;");
        mensaje.setWrapText(true);

        card.getChildren().addAll(filaTop, mensaje);

        // ── Detalle (stack trace) — mostrar resumen si hay ──
        if (log.getDetalle() != null && !log.getDetalle().isBlank()) {
            String resumen = log.getDetalle().length() > 200
                    ? log.getDetalle().substring(0, 200) + "..."
                    : log.getDetalle();
            Label detalle = new Label(resumen);
            detalle.getStyleClass().add("texto-hint");
            detalle.setStyle("-fx-text-fill: #777; -fx-font-family: monospace;");
            detalle.setWrapText(true);
            card.getChildren().add(detalle);
        }

        // ── Fila inferior: cliente + usuario + botón de acción ──
        HBox filaBottom = new HBox(8);
        filaBottom.setAlignment(Pos.CENTER_LEFT);
        filaBottom.setPadding(new Insets(4, 0, 0, 0));

        if (log.getNombreCliente() != null) {
            Label cliente = new Label("\ud83d\udccd " + log.getNombreCliente());
            cliente.getStyleClass().add("texto-hint");
            cliente.setStyle("-fx-text-fill: #666;");
            filaBottom.getChildren().add(cliente);
        }
        if (log.getUsuario() != null) {
            Label user = new Label("\ud83d\udc64 " + log.getUsuario());
            user.getStyleClass().add("texto-hint");
            user.setStyle("-fx-text-fill: #666;");
            filaBottom.getChildren().add(user);
        }

        Region espacioBottom = new Region();
        HBox.setHgrow(espacioBottom, Priority.ALWAYS);
        filaBottom.getChildren().add(espacioBottom);

        // Botón de acción según estado actual
        agregarBotonEstado(log, card, filaBottom);

        card.getChildren().add(filaBottom);

        // ── Click en la tarjeta abre el modal de detalle ──
        card.setOnMouseClicked(e -> abrirModalDetalle(log));

        // Hover effect
        card.setOnMouseEntered(e -> {
            if (!ESTADO_RESUELTO.equals(log.getEstado())) {
                card.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 6; " +
                        "-fx-border-color: #d4af37; -fx-border-radius: 6; -fx-cursor: hand;");
            }
        });
        card.setOnMouseExited(e ->
                card.setStyle("-fx-background-color: #222; -fx-background-radius: 6; " +
                        "-fx-border-color: #333; -fx-border-radius: 6; -fx-cursor: hand;"));

        return card;
    }

    // ── Crea un badge visual para el estado del log ──
    private Label crearBadgeEstado(String estado) {
        Label badge = new Label();
        badge.setStyle("-fx-font-size: 9px; -fx-font-weight: 700; -fx-padding: 2 8; " +
                "-fx-background-radius: 10;");

        if (ESTADO_ERROR.equals(estado)) {
            badge.setText(IdiomaUtil.obtener("ctrl.logs.estado.notificacion_error"));
            badge.setStyle(badge.getStyle() +
                    "-fx-background-color: #3a0a0a; -fx-text-fill: #ff6666; -fx-border-color: #ff4444; " +
                    "-fx-border-radius: 10; -fx-border-width: 0.5;");
        } else if (ESTADO_EN_REVISION.equals(estado)) {
            badge.setText(IdiomaUtil.obtener("ctrl.logs.estado.en_revision"));
            badge.setStyle(badge.getStyle() +
                    "-fx-background-color: #2a2206; -fx-text-fill: #daa520; -fx-border-color: #daa520; " +
                    "-fx-border-radius: 10; -fx-border-width: 0.5;");
        } else if (ESTADO_RESUELTO.equals(estado)) {
            badge.setText(IdiomaUtil.obtener("ctrl.logs.estado.resuelto"));
            badge.setStyle(badge.getStyle() +
                    "-fx-background-color: #1a2a1a; -fx-text-fill: #a8b991; -fx-border-color: #a8b991; " +
                    "-fx-border-radius: 10; -fx-border-width: 0.5;");
        }

        return badge;
    }

    // ── Agrega el botón de transición de estado según el estado actual ──
    private void agregarBotonEstado(LogCriticoDto log, VBox card, HBox filaBottom) {
        String estado = log.getEstado();

        if (ESTADO_ERROR.equals(estado)) {
            // Error → En Revisión
            Button btn = new Button(IdiomaUtil.obtener("ctrl.logs.boton.en_revision"));
            btn.getStyleClass().add("texto-hint");
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #daa520; " +
                    "-fx-cursor: hand; -fx-padding: 2 8; " +
                    "-fx-border-color: #daa520; -fx-border-radius: 3; -fx-background-radius: 3;");
            btn.setOnAction(e -> {
                e.consume();
                cambiarEstado(log.getIdLog(), ESTADO_EN_REVISION, btn);
            });
            filaBottom.getChildren().add(btn);
        } else if (ESTADO_EN_REVISION.equals(estado)) {
            // En Revisión → Resuelto
            Button btn = new Button(IdiomaUtil.obtener("ctrl.logs.boton.resolver"));
            btn.getStyleClass().add("texto-hint");
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a8b991; " +
                    "-fx-cursor: hand; -fx-padding: 2 8; " +
                    "-fx-border-color: #a8b991; -fx-border-radius: 3; -fx-background-radius: 3;");
            btn.setOnAction(e -> {
                e.consume();
                cambiarEstado(log.getIdLog(), ESTADO_RESUELTO, btn);
            });
            filaBottom.getChildren().add(btn);
        } else {
            // Resuelto — mostrar label
            Label resueltoLabel = new Label(IdiomaUtil.obtener("ctrl.logs.resuelto"));
            resueltoLabel.getStyleClass().add("texto-hint");
            resueltoLabel.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");
            filaBottom.getChildren().add(resueltoLabel);
        }
    }

    // ── Cambia el estado de un log y recarga la lista ──
    private void cambiarEstado(Long idLog, String nuevoEstado, Button btn) {
        btn.setDisable(true);
        btn.setText(IdiomaUtil.obtener("ctrl.logs.marcando"));

        servicio.cambiarEstadoAsync(idLog, nuevoEstado)
                .thenAccept(logActualizado -> Platform.runLater(() -> {
                    NotificacionUtil.mostrarExito(gestor.obtenerStage(),
                            IdiomaUtil.obtener("ctrl.logs.estado_exito"));
                    cargarLogs();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        btn.setDisable(false);
                        // Restaurar texto según transición
                        if (ESTADO_EN_REVISION.equals(nuevoEstado)) {
                            btn.setText(IdiomaUtil.obtener("ctrl.logs.boton.en_revision"));
                        } else {
                            btn.setText(IdiomaUtil.obtener("ctrl.logs.boton.resolver"));
                        }
                        NotificacionUtil.mostrarError(gestor.obtenerStage(),
                                MessageFormat.format(IdiomaUtil.obtener("ctrl.logs.error_estado"),
                                        ex.getMessage()));
                    });
                    return null;
                });
    }

    // ══════════════════════════════════════════════════════════════
    //  MODAL DE DETALLE — muestra toda la información del log
    // ══════════════════════════════════════════════════════════════

    // ── Abre un modal overlay con el detalle completo de un log ──
    private void abrirModalDetalle(LogCriticoDto log) {
        VBox contenido = new VBox(12);
        contenido.setMaxWidth(700);
        contenido.setMaxHeight(500);
        contenido.setPadding(new Insets(20));
        contenido.setStyle("-fx-background-color: #141414; -fx-border-color: rgba(212,175,55,0.5); " +
                "-fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10;");

        // ── Header del detalle ──
        Label iconoDetalle = new Label("\ud83d\udcdd");
        iconoDetalle.getStyleClass().add("icono-texto-sm");

        Label tituloDetalle = new Label(IdiomaUtil.obtener("ctrl.logs.detalle_titulo"));
        tituloDetalle.getStyleClass().add("modal-titulo-dorado");

        Region espHeader = new Region();
        HBox.setHgrow(espHeader, Priority.ALWAYS);

        // Botón copiar
        Button btnCopiar = new Button(IdiomaUtil.obtener("ctrl.logs.copiar"));
        btnCopiar.getStyleClass().add("texto-hint-sm");
        btnCopiar.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #d4af37; " +
                "-fx-cursor: hand; -fx-padding: 4 12; " +
                "-fx-border-color: #d4af37; -fx-border-radius: 4; -fx-background-radius: 4;");
        btnCopiar.setOnAction(e -> copiarLogAlPortapapeles(log, btnCopiar));

        // Botón cerrar
        Label cerrarDetalle = new Label("\u2715");
        cerrarDetalle.getStyleClass().add("panel-seccion-titulo");
        cerrarDetalle.setStyle("-fx-text-fill: #666; -fx-cursor: hand;");
        cerrarDetalle.setOnMouseEntered(e ->
                cerrarDetalle.setStyle("-fx-text-fill: #f5f5f5; -fx-cursor: hand;"));
        cerrarDetalle.setOnMouseExited(e ->
                cerrarDetalle.setStyle("-fx-text-fill: #666; -fx-cursor: hand;"));

        HBox headerDetalle = new HBox(8, iconoDetalle, tituloDetalle, espHeader, btnCopiar, cerrarDetalle);
        headerDetalle.setAlignment(Pos.CENTER_LEFT);

        // ── Separador ──
        Region sepDetalle = new Region();
        sepDetalle.setMinHeight(1);
        sepDetalle.setMaxHeight(1);
        sepDetalle.setStyle("-fx-background-color: rgba(212,175,55,0.3);");

        // ── Campos del log en un scroll ──
        VBox campos = new VBox(8);
        campos.setPadding(new Insets(4, 0, 4, 0));

        // Nivel + Estado en la misma fila
        HBox filaEstado = new HBox(12);
        filaEstado.getChildren().addAll(
                crearCampoDetalle(IdiomaUtil.obtener("ctrl.logs.campo.nivel"), log.getNivel()),
                crearCampoDetalle(IdiomaUtil.obtener("ctrl.logs.campo.estado"),
                        obtenerTextoEstado(log.getEstado()))
        );

        campos.getChildren().add(filaEstado);
        campos.getChildren().add(crearCampoDetalle(
                IdiomaUtil.obtener("ctrl.logs.campo.origen"), log.getOrigen()));
        campos.getChildren().add(crearCampoDetalle(
                IdiomaUtil.obtener("ctrl.logs.campo.mensaje"), log.getMensaje()));

        // Detalle / Stack trace — sección más grande
        if (log.getDetalle() != null && !log.getDetalle().isBlank()) {
            VBox seccionDetalle = new VBox(4);
            Label labelDetalle = new Label(IdiomaUtil.obtener("ctrl.logs.campo.detalle"));
            labelDetalle.getStyleClass().add("texto-hint");
            labelDetalle.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: 600;");

            Label valorDetalle = new Label(log.getDetalle());
            valorDetalle.getStyleClass().add("texto-hint-sm");
            valorDetalle.setStyle("-fx-text-fill: #ccc; -fx-font-family: monospace; " +
                    "-fx-background-color: #0a0a0a; -fx-padding: 8; -fx-background-radius: 4;");
            valorDetalle.setWrapText(true);

            seccionDetalle.getChildren().addAll(labelDetalle, valorDetalle);
            campos.getChildren().add(seccionDetalle);
        }

        // Fila de metadata: usuario, IP, cliente, fecha
        HBox filaMeta = new HBox(12);
        if (log.getUsuario() != null) {
            filaMeta.getChildren().add(crearCampoDetalle(
                    IdiomaUtil.obtener("ctrl.logs.campo.usuario"), log.getUsuario()));
        }
        if (log.getIpCliente() != null) {
            filaMeta.getChildren().add(crearCampoDetalle(
                    IdiomaUtil.obtener("ctrl.logs.campo.ip"), log.getIpCliente()));
        }
        if (log.getNombreCliente() != null) {
            filaMeta.getChildren().add(crearCampoDetalle(
                    IdiomaUtil.obtener("ctrl.logs.campo.cliente"), log.getNombreCliente()));
        }
        if (!filaMeta.getChildren().isEmpty()) {
            campos.getChildren().add(filaMeta);
        }

        campos.getChildren().add(crearCampoDetalle(
                IdiomaUtil.obtener("ctrl.logs.campo.fecha"),
                log.getFechaCreacion() != null ? log.getFechaCreacion().format(FORMATO_FECHA) : "\u2014"));

        ScrollPane scrollDetalle = new ScrollPane(campos);
        scrollDetalle.setFitToWidth(true);
        scrollDetalle.setStyle("-fx-background: #141414; -fx-background-color: transparent; " +
                "-fx-border-color: transparent;");
        VBox.setVgrow(scrollDetalle, Priority.ALWAYS);

        contenido.getChildren().addAll(headerDetalle, sepDetalle, scrollDetalle);

        // Crear overlay interno sobre el modal actual
        StackPane overlayDetalle = new StackPane();
        overlayDetalle.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");
        overlayDetalle.setAlignment(Pos.CENTER);
        overlayDetalle.getChildren().add(contenido);

        // Cerrar al hacer clic fuera
        overlayDetalle.setOnMouseClicked(e -> {
            if (e.getTarget() == overlayDetalle) {
                gestor.cerrarOverlayInterno(overlayDetalle);
            }
        });
        cerrarDetalle.setOnMouseClicked(e -> gestor.cerrarOverlayInterno(overlayDetalle));

        gestor.mostrarOverlayInterno(overlayDetalle);
    }

    // ── Crea un par label-valor para los campos del modal de detalle ──
    private VBox crearCampoDetalle(String etiqueta, String valor) {
        VBox campo = new VBox(2);
        Label lbl = new Label(etiqueta);
        lbl.getStyleClass().add("texto-hint");
        lbl.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: 600;");

        Label val = new Label(valor != null ? valor : "\u2014");
        val.getStyleClass().add("texto-secundario-sm");
        val.setStyle("-fx-text-fill: #e8e8e8;");
        val.setWrapText(true);

        campo.getChildren().addAll(lbl, val);
        return campo;
    }

    // ── Obtiene el texto traducido para un estado ──
    private String obtenerTextoEstado(String estado) {
        if (ESTADO_ERROR.equals(estado)) {
            return IdiomaUtil.obtener("ctrl.logs.estado.notificacion_error");
        } else if (ESTADO_EN_REVISION.equals(estado)) {
            return IdiomaUtil.obtener("ctrl.logs.estado.en_revision");
        } else if (ESTADO_RESUELTO.equals(estado)) {
            return IdiomaUtil.obtener("ctrl.logs.estado.resuelto");
        }
        return estado;
    }

    // ── Copia toda la información del log al portapapeles ──
    private void copiarLogAlPortapapeles(LogCriticoDto log, Button btnCopiar) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LOG #").append(log.getIdLog()).append(" ===\n");
        sb.append(IdiomaUtil.obtener("ctrl.logs.campo.nivel")).append(": ").append(log.getNivel()).append("\n");
        sb.append(IdiomaUtil.obtener("ctrl.logs.campo.estado")).append(": ").append(obtenerTextoEstado(log.getEstado())).append("\n");
        sb.append(IdiomaUtil.obtener("ctrl.logs.campo.origen")).append(": ").append(log.getOrigen()).append("\n");
        sb.append(IdiomaUtil.obtener("ctrl.logs.campo.mensaje")).append(": ").append(log.getMensaje()).append("\n");

        if (log.getDetalle() != null && !log.getDetalle().isBlank()) {
            sb.append(IdiomaUtil.obtener("ctrl.logs.campo.detalle")).append(":\n").append(log.getDetalle()).append("\n");
        }
        if (log.getUsuario() != null) {
            sb.append(IdiomaUtil.obtener("ctrl.logs.campo.usuario")).append(": ").append(log.getUsuario()).append("\n");
        }
        if (log.getIpCliente() != null) {
            sb.append(IdiomaUtil.obtener("ctrl.logs.campo.ip")).append(": ").append(log.getIpCliente()).append("\n");
        }
        if (log.getNombreCliente() != null) {
            sb.append(IdiomaUtil.obtener("ctrl.logs.campo.cliente")).append(": ").append(log.getNombreCliente()).append("\n");
        }
        if (log.getFechaCreacion() != null) {
            sb.append(IdiomaUtil.obtener("ctrl.logs.campo.fecha")).append(": ").append(log.getFechaCreacion().format(FORMATO_FECHA)).append("\n");
        }

        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(contenido);

        // Feedback visual en el botón
        String textoOriginal = btnCopiar.getText();
        btnCopiar.setText(IdiomaUtil.obtener("ctrl.logs.copiado"));
        btnCopiar.setStyle("-fx-background-color: #1a2a1a; -fx-text-fill: #a8b991; " +
                "-fx-padding: 4 12; " +
                "-fx-border-color: #a8b991; -fx-border-radius: 4; -fx-background-radius: 4;");

        // Restaurar después de 2 segundos
        javafx.animation.PauseTransition pausa = new javafx.animation.PauseTransition(
                javafx.util.Duration.seconds(2));
        pausa.setOnFinished(e -> {
            btnCopiar.setText(textoOriginal);
            btnCopiar.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #d4af37; " +
                    "-fx-cursor: hand; -fx-padding: 4 12; " +
                    "-fx-border-color: #d4af37; -fx-border-radius: 4; -fx-background-radius: 4;");
        });
        pausa.play();
    }
}
