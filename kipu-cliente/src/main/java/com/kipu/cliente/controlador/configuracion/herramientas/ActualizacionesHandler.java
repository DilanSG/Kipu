/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.servicio.VerificacionActualizacionServicio;
import com.kipu.cliente.servicio.VerificacionActualizacionServicio.UpdateInfo;
import com.kipu.cliente.utilidad.IdiomaUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/*Handler para la herramienta Actualizaciones en el menú de configuración.
 * Verifica actualizaciones contra el backend web, descarga e instala directamente.*/
public class ActualizacionesHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(ActualizacionesHandler.class);

    private final GestorModales gestor;
    private final VerificacionActualizacionServicio servicio = new VerificacionActualizacionServicio();

    // Flag estática para evitar búsquedas duplicadas entre instancias del modal
    private static final String PAGINA_DESCARGAS = System.getProperty(
            "kipu.api.url", "https://kipu.app") + "/downloads";

    private static volatile boolean operacionEnCurso = false;

    // ── Estado persistente entre aperturas del modal ──
    private static String ultimaFechaVerificacion = null;
    private static String ultimaVersionEncontrada = null;
    private static UpdateInfo ultimoResultado = null;

    // ── Estado del modal ──
    private VBox estadoBox;
    private VBox accionesBox;
    private Label lblEstado;
    private Label valVersionInstalada;
    private Label valUltimaVerificacion;
    private Label valVersionEncontrada;
    private Label valEnlaceDescarga;
    private HBox filaVersionEncontrada;
    private HBox filaEnlaceDescarga;

    public ActualizacionesHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Abriendo gestor de Actualizaciones");

        VBox modal = new VBox(12);
        modal.setMaxWidth(560);
        modal.setMaxHeight(520);
        modal.setPadding(new Insets(20));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header centrado ───
        HBox header = gestor.crearHeaderModal(
            IdiomaUtil.obtener("ctrl.actualizaciones.titulo"), "icono-cfg-actualizaciones");
        header.setAlignment(Pos.CENTER);

        // ─── Estado (ícono + texto) ───
        estadoBox = new VBox(8);
        estadoBox.setAlignment(Pos.CENTER);
        estadoBox.setPadding(new Insets(16, 0, 12, 0));

        // ─── Sección de información ───
        VBox infoSection = construirSeccionInfo();

        // ─── Acciones ───
        accionesBox = new VBox(10);
        accionesBox.setAlignment(Pos.CENTER);
        accionesBox.setPadding(new Insets(8, 0, 0, 0));

        // Restaurar estado o mostrar estado inicial
        if (ultimoResultado != null) {
            restaurarEstadoActualizacionDisponible(ultimoResultado);
        } else if (ultimaFechaVerificacion != null) {
            mostrarSistemaActualizado();
            agregarBotonBuscar();
        } else {
            mostrarEstadoInicial();
            agregarBotonBuscar();
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        modal.getChildren().addAll(
            header, gestor.crearSeparador(), estadoBox,
            gestor.crearSeparador(), infoSection, spacer,
            gestor.crearSeparador(), accionesBox);
        gestor.mostrarModal(modal);
    }

    // ==================== SECCIÓN DE INFORMACIÓN ====================

    /**
     * Construye la sección de info con filas de detalle.
     * Las filas de versión encontrada y enlace se ocultan hasta tener datos.
     */
    private VBox construirSeccionInfo() {
        VBox info = new VBox(6);
        info.setPadding(new Insets(8, 4, 4, 4));

        String vLocal = servicio.obtenerVersionLocal();
        if (vLocal == null || vLocal.contains("${")) vLocal = "0.0.0";

        valVersionInstalada = gestor.crearInfoValor("v" + vLocal);
        valVersionInstalada.setStyle("-fx-text-fill: #e8e8e8; -fx-font-size: 14px;");
        valUltimaVerificacion = gestor.crearInfoValor(
            ultimaFechaVerificacion != null
                ? ultimaFechaVerificacion
                : IdiomaUtil.obtener("ctrl.actualizaciones.ultima.valor"));
        valUltimaVerificacion.setStyle("-fx-text-fill: #e8e8e8; -fx-font-size: 14px;");

        valVersionEncontrada = gestor.crearInfoValor("—");
        valVersionEncontrada.setStyle("-fx-text-fill: #e8e8e8; -fx-font-size: 14px;");

        // Enlace clickeable a la página de descargas
        valEnlaceDescarga = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.pagina_descargas"));
        valEnlaceDescarga.setStyle("-fx-text-fill: #7799cc; -fx-font-size: 14px; -fx-underline: true;");
        valEnlaceDescarga.setCursor(Cursor.HAND);
        valEnlaceDescarga.setOnMouseEntered(e -> valEnlaceDescarga.setStyle(
            "-fx-text-fill: #99bbee; -fx-font-size: 14px; -fx-underline: true;"));
        valEnlaceDescarga.setOnMouseExited(e -> valEnlaceDescarga.setStyle(
            "-fx-text-fill: #7799cc; -fx-font-size: 14px; -fx-underline: true;"));
        valEnlaceDescarga.setOnMouseClicked(e -> abrirPaginaDescargas());

        filaVersionEncontrada = crearFilaInfo(
            IdiomaUtil.obtener("ctrl.actualizaciones.version_encontrada"),
            valVersionEncontrada);
        filaEnlaceDescarga = crearFilaInfo(
            IdiomaUtil.obtener("ctrl.actualizaciones.enlace_descarga"),
            valEnlaceDescarga);

        // Ocultar filas dinámicas hasta que haya datos
        boolean mostrar = ultimaVersionEncontrada != null;
        filaVersionEncontrada.setVisible(mostrar);
        filaVersionEncontrada.setManaged(mostrar);
        filaEnlaceDescarga.setVisible(false);
        filaEnlaceDescarga.setManaged(false);

        if (mostrar) {
            valVersionEncontrada.setText("v" + ultimaVersionEncontrada);
            if (ultimoResultado != null) {
                filaEnlaceDescarga.setVisible(true);
                filaEnlaceDescarga.setManaged(true);
            }
        }

        info.getChildren().addAll(
            crearFilaInfo(IdiomaUtil.obtener("ctrl.actualizaciones.version_instalada"),
                valVersionInstalada),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.actualizaciones.ultima"),
                valUltimaVerificacion),
            filaVersionEncontrada,
            filaEnlaceDescarga
        );

        return info;
    }

    /**
     * Crea una fila de información con label de título y un Label de valor.
     */
    private HBox crearFilaInfo(String titulo, Label valorLabel) {
        Label labelTitulo = gestor.crearInfoLabel(titulo);
        labelTitulo.setStyle("-fx-text-fill: #888; -fx-font-size: 14px;");
        labelTitulo.setMinWidth(160);
        HBox fila = new HBox(10, labelTitulo, valorLabel);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    // ------------------------------------------------------------------
    // Buscar actualizaciones
    // ------------------------------------------------------------------

    private void agregarBotonBuscar() {
        accionesBox.getChildren().clear();
        Button btnBuscar = new Button(IdiomaUtil.obtener("ctrl.actualizaciones.btn.buscar"));
        btnBuscar.getStyleClass().add("btn-metodo-pago");
        btnBuscar.setPrefWidth(260);
        btnBuscar.setPrefHeight(42);
        btnBuscar.setOnAction(e -> buscarActualizaciones(btnBuscar));
        accionesBox.getChildren().add(btnBuscar);
    }

    private void buscarActualizaciones(Button btnBuscar) {
        if (operacionEnCurso) return;
        operacionEnCurso = true;

        btnBuscar.setDisable(true);
        btnBuscar.setText(IdiomaUtil.obtener("ctrl.actualizaciones.buscando"));
        mostrarEstadoBuscando();

        new Thread(() -> {
            Optional<UpdateInfo> resultado = servicio.verificarActualizacion();
            String ahora = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

            Platform.runLater(() -> {
                operacionEnCurso = false;
                ultimaFechaVerificacion = ahora;
                valUltimaVerificacion.setText(ahora);

                if (resultado.isPresent()) {
                    UpdateInfo info = resultado.get();
                    ultimoResultado = info;
                    ultimaVersionEncontrada = info.versionRemota();
                    mostrarActualizacionDisponible(info);
                } else {
                    ultimoResultado = null;
                    String vLocal = servicio.obtenerVersionLocal();
                    if (vLocal == null || vLocal.contains("${")) vLocal = "0.0.0";
                    ultimaVersionEncontrada = vLocal;
                    valVersionEncontrada.setText("v" + vLocal);
                    filaVersionEncontrada.setVisible(true);
                    filaVersionEncontrada.setManaged(true);
                    filaEnlaceDescarga.setVisible(false);
                    filaEnlaceDescarga.setManaged(false);
                    mostrarSistemaActualizado();
                    agregarBotonBuscar();
                }
            });
        }, "kipu-check-update") {
            { setDaemon(true); }
        }.start();
    }

    // ------------------------------------------------------------------
    // Estados visuales
    // ------------------------------------------------------------------

    private void mostrarEstadoInicial() {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u2139", "#1a4a6e", "#3a8fd4", "#5ab0f7");
        lblEstado = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.listo_verificar"));
        lblEstado.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 16px; -fx-font-weight: 500;");
        estadoBox.getChildren().addAll(icono, lblEstado);
    }

    private void mostrarEstadoBuscando() {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u21BB", "#4a4a1e", "#d4af37", "#d4af37");
        lblEstado = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.buscando"));
        lblEstado.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 500;");
        estadoBox.getChildren().addAll(icono, lblEstado);
    }

    private void mostrarSistemaActualizado() {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u2713", "#2d5016", "#5a9e2f", "#7cc33f");
        lblEstado = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.actualizado"));
        lblEstado.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 16px; -fx-font-weight: 500;");
        estadoBox.getChildren().addAll(icono, lblEstado);
    }

    private void mostrarActualizacionDisponible(UpdateInfo info) {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u2191", "#4a3a0e", "#d4af37", "#ffd700");
        lblEstado = new Label(String.format(
                IdiomaUtil.obtener("ctrl.actualizaciones.nueva_disponible"),
                info.versionRemota()));
        lblEstado.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 17px; -fx-font-weight: 600;");
        estadoBox.getChildren().addAll(icono, lblEstado);

        // Notas del release (filtrar secciones vacías como "Dependencies")
        String notasLimpias = limpiarNotas(info.notas());
        if (notasLimpias != null && !notasLimpias.isBlank()) {
            Label lblNotas = new Label(notasLimpias);
            lblNotas.setWrapText(true);
            lblNotas.setMaxWidth(500);
            lblNotas.setStyle("-fx-text-fill: #999; -fx-font-size: 13px; -fx-padding: 4 16 0 16;");
            estadoBox.getChildren().add(lblNotas);
        }

        // Actualizar filas de info
        valVersionEncontrada.setText("v" + info.versionRemota());
        filaVersionEncontrada.setVisible(true);
        filaVersionEncontrada.setManaged(true);

        filaEnlaceDescarga.setVisible(true);
        filaEnlaceDescarga.setManaged(true);

        // Botones de acción
        accionesBox.getChildren().clear();

        if (info.urlDescargaDirecta() != null && !info.urlDescargaDirecta().isBlank()) {
            Button btnInstalar = new Button(
                    IdiomaUtil.obtener("ctrl.actualizaciones.btn.descargar_instalar"));
            btnInstalar.getStyleClass().add("btn-metodo-pago");
            btnInstalar.setPrefWidth(280);
            btnInstalar.setPrefHeight(44);
            btnInstalar.setStyle(btnInstalar.getStyle()
                    + "-fx-font-weight: 700; -fx-font-size: 14px;");
            btnInstalar.setOnAction(e -> iniciarDescargaEInstalacion(info, btnInstalar));
            accionesBox.getChildren().add(btnInstalar);
        } else {
            Label sinAsset = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.sin_asset"));
            sinAsset.setWrapText(true);
            sinAsset.setMaxWidth(480);
            sinAsset.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
            accionesBox.getChildren().add(sinAsset);
        }
    }

    /** Restaura el estado visual cuando se reabre el modal con un resultado previo. */
    private void restaurarEstadoActualizacionDisponible(UpdateInfo info) {
        mostrarActualizacionDisponible(info);
    }

    private void mostrarError(String mensaje) {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u2717", "#5a1a1a", "#c44", "#ff6b6b");
        lblEstado = new Label(mensaje);
        lblEstado.setWrapText(true);
        lblEstado.setMaxWidth(500);
        lblEstado.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 15px; -fx-font-weight: 500;");
        estadoBox.getChildren().addAll(icono, lblEstado);
    }

    // ------------------------------------------------------------------
    // Descarga e instalación
    // ------------------------------------------------------------------

    private void iniciarDescargaEInstalacion(UpdateInfo info, Button btnInstalar) {
        if (operacionEnCurso) return;
        operacionEnCurso = true;

        btnInstalar.setDisable(true);

        // Reemplazar botones por barra de progreso
        accionesBox.getChildren().clear();

        Label lblProgreso = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.descargando"));
        lblProgreso.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 14px;");

        ProgressBar barraProgreso = new ProgressBar(0);
        barraProgreso.setPrefWidth(420);
        barraProgreso.setPrefHeight(20);
        barraProgreso.setStyle(
                "-fx-accent: #d4af37; -fx-control-inner-background: #333;");

        Label lblPorcentaje = new Label("0%");
        lblPorcentaje.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 14px;");

        accionesBox.getChildren().addAll(lblProgreso, barraProgreso, lblPorcentaje);

        new Thread(() -> {
            try {
                // Descargar
                Path archivoDescargado = servicio.descargarInstalador(
                        info.urlDescargaDirecta(),
                        info.versionRemota(),
                        (bytesLeidos, bytesTotales) -> Platform.runLater(() -> {
                            if (bytesTotales > 0) {
                                double progreso = (double) bytesLeidos / bytesTotales;
                                barraProgreso.setProgress(progreso);
                                lblPorcentaje.setText(String.format("%.0f%%", progreso * 100));
                                lblProgreso.setText(String.format(
                                        IdiomaUtil.obtener("ctrl.actualizaciones.descargando_progreso"),
                                        bytesLeidos / (1024 * 1024),
                                        bytesTotales / (1024 * 1024)));
                            } else {
                                barraProgreso.setProgress(-1); // indeterminado
                                lblPorcentaje.setText(formatearMB(bytesLeidos));
                            }
                        }));

                // Verificar checksum
                if (info.checksumEsperado() != null && !info.checksumEsperado().isBlank()) {
                    Platform.runLater(() ->
                            lblProgreso.setText(IdiomaUtil.obtener("ctrl.actualizaciones.verificando")));
                    if (!servicio.verificarChecksum(archivoDescargado, info.checksumEsperado())) {
                        Platform.runLater(() -> {
                            operacionEnCurso = false;
                            mostrarError(IdiomaUtil.obtener("ctrl.actualizaciones.error_checksum"));
                            mostrarBotonReintentar(info);
                        });
                        return;
                    }
                }

                // Instalar
                Platform.runLater(() -> {
                    barraProgreso.setProgress(1.0);
                    lblPorcentaje.setText("100%");
                    lblProgreso.setText(IdiomaUtil.obtener("ctrl.actualizaciones.instalando"));
                });

                boolean exitoso = servicio.instalarActualizacion(archivoDescargado);

                Platform.runLater(() -> {
                    operacionEnCurso = false;
                    if (exitoso) {
                        if (servicio.esMacOS()) {
                            mostrarDmgAbierto(info.versionRemota());
                        } else {
                            mostrarInstalacionExitosa(info.versionRemota());
                        }
                    } else {
                        mostrarError(IdiomaUtil.obtener("ctrl.actualizaciones.error_instalar"));
                        mostrarBotonReintentar(info);
                    }
                });

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    operacionEnCurso = false;
                    mostrarError(IdiomaUtil.obtener("ctrl.actualizaciones.error_interrumpido"));
                });
            } catch (Exception ex) {
                logger.warn("[Update] Error durante descarga/instalación: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    operacionEnCurso = false;
                    mostrarError(String.format(
                            IdiomaUtil.obtener("ctrl.actualizaciones.error_descarga"),
                            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
                    mostrarBotonReintentar(info);
                });
            }
        }, "kipu-download-install") {
            { setDaemon(true); }
        }.start();
    }

    private void mostrarInstalacionExitosa(String versionNueva) {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u2713", "#2d5016", "#5a9e2f", "#7cc33f");
        lblEstado = new Label(String.format(
                IdiomaUtil.obtener("ctrl.actualizaciones.instalacion_exitosa"),
                versionNueva));
        lblEstado.setStyle("-fx-text-fill: #7cc33f; -fx-font-size: 17px; -fx-font-weight: 600;");
        estadoBox.getChildren().addAll(icono, lblEstado);

        accionesBox.getChildren().clear();
        Button btnReiniciar = new Button(
                IdiomaUtil.obtener("ctrl.actualizaciones.btn.reiniciar"));
        btnReiniciar.getStyleClass().add("btn-metodo-pago");
        btnReiniciar.setPrefWidth(240);
        btnReiniciar.setPrefHeight(42);
        btnReiniciar.setStyle(btnReiniciar.getStyle()
                + "-fx-font-weight: 700; -fx-font-size: 14px;");
        btnReiniciar.setOnAction(e -> servicio.reiniciarAplicacion());
        accionesBox.getChildren().add(btnReiniciar);
    }

    /** macOS: el DMG se abrió pero el usuario debe arrastrar manualmente. */
    private void mostrarDmgAbierto(String versionNueva) {
        estadoBox.getChildren().clear();
        StackPane icono = crearIconoCircular("\u2191", "#4a3a0e", "#d4af37", "#ffd700");
        lblEstado = new Label(String.format(
                IdiomaUtil.obtener("ctrl.actualizaciones.macos_dmg_abierto"),
                versionNueva));
        lblEstado.setWrapText(true);
        lblEstado.setMaxWidth(480);
        lblEstado.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 14px; -fx-font-weight: 500;");
        estadoBox.getChildren().addAll(icono, lblEstado);

        accionesBox.getChildren().clear();
        Button btnReiniciar = new Button(
                IdiomaUtil.obtener("ctrl.actualizaciones.btn.reiniciar"));
        btnReiniciar.getStyleClass().add("btn-metodo-pago");
        btnReiniciar.setPrefWidth(240);
        btnReiniciar.setPrefHeight(42);
        btnReiniciar.setOnAction(e -> servicio.reiniciarAplicacion());
        accionesBox.getChildren().add(btnReiniciar);
    }

    private void mostrarBotonReintentar(UpdateInfo info) {
        accionesBox.getChildren().clear();
        Button btnReintentar = new Button(
                IdiomaUtil.obtener("ctrl.actualizaciones.btn.reintentar"));
        btnReintentar.getStyleClass().add("btn-metodo-pago");
        btnReintentar.setPrefWidth(200);
        btnReintentar.setPrefHeight(42);
        btnReintentar.setOnAction(e -> iniciarDescargaEInstalacion(info, btnReintentar));
        accionesBox.getChildren().add(btnReintentar);
    }

    // ------------------------------------------------------------------
    // Helpers UI
    // ------------------------------------------------------------------

    private StackPane crearIconoCircular(String simbolo, String fondoColor,
                                         String bordeColor, String textoColor) {
        Circle circulo = new Circle(28);
        circulo.setFill(Color.web(fondoColor));
        circulo.setStroke(Color.web(bordeColor));
        circulo.setStrokeWidth(2);
        Label iconLabel = new Label(simbolo);
        iconLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: " + textoColor + "; -fx-font-weight: 700;");
        return new StackPane(circulo, iconLabel);
    }

    /** Abre la página de descargas del sitio web en el navegador del sistema. */
    private void abrirPaginaDescargas() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(PAGINA_DESCARGAS));
                logger.info("[Update] Abriendo página de descargas: {}", PAGINA_DESCARGAS);
            } else {
                // Fallback para Linux
                new ProcessBuilder("xdg-open", PAGINA_DESCARGAS).start();
            }
        } catch (Exception e) {
            logger.warn("[Update] No se pudo abrir el navegador: {}", e.getMessage());
        }
    }

    /**
     * Limpia las notas del release eliminando secciones vacías
     * (como "Dependencies" de GitHub auto-generated notes).
     */
    private String limpiarNotas(String notas) {
        if (notas == null || notas.isBlank()) return null;
        // Eliminar secciones Markdown con encabezado pero sin contenido real
        String limpio = notas
            .replaceAll("(?mi)^#{1,4}\\s*(Dependencies|Dependencias)\\s*\\n*", "")
            .replaceAll("(?m)^\\*\\s*\\n", "")   // Bullets vacíos
            .replaceAll("\\n{3,}", "\n\n")         // Múltiples saltos de línea
            .strip();
        return limpio.isBlank() ? null : limpio;
    }

    private String formatearMB(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
