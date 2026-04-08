/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.servicio.MetodoPagoServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NavegacionUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.MetodoPagoDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler para la gestión de Métodos de Pago desde el panel de configuración.
 *
 * Permite al administrador realizar CRUD completo de métodos de pago:
 * - Listar métodos de pago activos con su código (00-99)
 * - Crear nuevos métodos de pago (se asigna código automáticamente)
 * - Editar nombre y código de métodos existentes (con intercambio si hay conflicto)
 * - Eliminar métodos de pago (excepto EFECTIVO que es predeterminado)
 *
 * Cada método de pago tiene un código de 2 dígitos (00-99) que lo identifica.
 * Si al editar se asigna un código que ya pertenece a otro método, se ofrece
 * intercambiar los códigos automáticamente.
 *
 * @see MetodoPagoServicio para las llamadas HTTP al servidor
 * @see MetodoPagoDto para el modelo de datos
 * @see GestorModales para la infraestructura de modales
 */
public class MetodosPagoHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(MetodosPagoHandler.class);

    /** Gestor de modales para mostrar/cerrar paneles overlay */
    private final GestorModales gestor;

    /** Servicio HTTP para operaciones CRUD de métodos de pago */
    private MetodoPagoServicio servicio;

    /** Lista local de métodos de pago cargados desde el servidor */
    private List<MetodoPagoDto> metodosCargados = new ArrayList<>();

    /** Contenedor VBox de la lista de métodos en el modal (para refrescar) */
    private VBox listaMetodosContenedor;

    /**
     * Crea el handler de métodos de pago vinculado al gestor de modales.
     *
     * @param gestor Gestor centralizado de modales
     */
    public MetodosPagoHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    /**
     * Abre el modal de gestión de métodos de pago.
     * Carga los métodos desde el servidor y muestra la interfaz CRUD completa.
     */
    @Override
    public void abrir() {
        logger.info("Abriendo gestión de Métodos de Pago");

        // Obtener token del usuario autenticado
        AuthRespuestaDto usuario = NavegacionUtil.getUsuarioActual();
        if (usuario == null || usuario.getToken() == null) {
            Stage stage = gestor.obtenerStage();
            NotificacionUtil.mostrarError(stage, IdiomaUtil.obtener("ctrl.metodos_pago.sin_sesion"));
            return;
        }
        servicio = new MetodoPagoServicio(usuario.getToken());

        // Construir y mostrar el modal
        VBox modal = construirModal();
        gestor.mostrarModal(modal);

        // Cargar métodos de pago desde el servidor
        cargarMetodos();
    }

    // ==================== CONSTRUCCIÓN DEL MODAL ====================

    /**
     * Construye el modal principal con header, sección de crear y lista de métodos.
     *
     * @return VBox con el contenido del modal
     */
    private VBox construirModal() {
        VBox modal = new VBox(14);
        modal.setMaxWidth(580);
        modal.setMaxHeight(620);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // Header
        HBox header = gestor.crearHeaderModal(IdiomaUtil.obtener("ctrl.metodos_pago.header"), "icono-cfg-pago");

        // Descripción
        Label descripcion = new Label(
            IdiomaUtil.obtener("ctrl.metodos_pago.descripcion"));
        descripcion.getStyleClass().add("texto-hint-sm");
        descripcion.setStyle("-fx-text-fill: #888;");
        descripcion.setWrapText(true);

        // Sección crear nuevo método
        HBox filaCrear = construirFilaCrear();

        // Label de sección
        Label lblMetodos = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.actuales"));
        lblMetodos.getStyleClass().add("panel-seccion-titulo");
        lblMetodos.setStyle("-fx-text-fill: #999;");
        VBox.setMargin(lblMetodos, new Insets(4, 0, 0, 0));

        // Lista de métodos en scroll
        listaMetodosContenedor = new VBox(8);
        listaMetodosContenedor.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(listaMetodosContenedor);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Nota informativa
        Label nota = new Label(
            IdiomaUtil.obtener("ctrl.metodos_pago.nota"));
        nota.getStyleClass().add("texto-hint");
        nota.setStyle("-fx-padding: 4 0 0 0;");
        nota.setWrapText(true);

        modal.getChildren().addAll(
            header, gestor.crearSeparador(),
            descripcion, filaCrear,
            gestor.crearSeparador(),
            lblMetodos, scroll,
            gestor.crearSeparador(), nota);

        return modal;
    }

    /**
     * Construye la fila de creación: campo de texto + botón "+" para crear nuevo método.
     *
     * @return HBox con el formulario de creación
     */
    private HBox construirFilaCrear() {
        HBox fila = new HBox(10);
        fila.setAlignment(Pos.CENTER_LEFT);

        TextField txtNuevo = new TextField();
        txtNuevo.setPromptText(IdiomaUtil.obtener("ctrl.metodos_pago.prompt"));
        txtNuevo.setPrefHeight(40);
        txtNuevo.getStyleClass().add("panel-btn-principal");
        txtNuevo.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #404040; -fx-border-width: 1.5px; " +
            "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-text-fill: #f5f5f5; " +
            "-fx-prompt-text-fill: #666;");
        HBox.setHgrow(txtNuevo, Priority.ALWAYS);

        // Botón crear con "+"
        Button btnCrear = new Button("+");
        btnCrear.setPrefSize(40, 40);
        btnCrear.setMinSize(40, 40);
        btnCrear.setStyle("-fx-background-color: #d4af37; -fx-text-fill: #0a0a0a; " +
            "-fx-font-weight: 700; -fx-background-radius: 6; -fx-cursor: hand;");
        btnCrear.getStyleClass().add("icono-texto-md");

        btnCrear.setOnAction(e -> crearMetodo(txtNuevo));
        txtNuevo.setOnAction(e -> btnCrear.fire());

        fila.getChildren().addAll(txtNuevo, btnCrear);
        return fila;
    }

    // ==================== OPERACIONES CRUD ====================

    /**
     * Carga los métodos de pago desde el servidor y renderiza la lista.
     */
    private void cargarMetodos() {
        // Indicador de carga
        listaMetodosContenedor.getChildren().clear();
        Label cargando = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.cargando"));
        cargando.getStyleClass().add("estado-texto");
        cargando.setStyle("-fx-text-fill: #888;");
        listaMetodosContenedor.getChildren().add(cargando);

        servicio.listarMetodosPagoAsync()
            .thenAccept(metodos -> Platform.runLater(() -> {
                metodosCargados = metodos != null ? new ArrayList<>(metodos) : new ArrayList<>();
                renderizarLista();
            }))
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    listaMetodosContenedor.getChildren().clear();
                    Label errorLbl = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.error_cargar"), error.getCause().getMessage()));
                    errorLbl.getStyleClass().add("estado-texto");
                    errorLbl.setStyle("-fx-text-fill: #ff6b6b;");
                    errorLbl.setWrapText(true);
                    listaMetodosContenedor.getChildren().add(errorLbl);
                });
                return null;
            });
    }

    /**
     * Crea un nuevo método de pago con el nombre ingresado.
     * El código se asigna automáticamente en el servidor.
     *
     * @param txtNuevo Campo de texto con el nombre del nuevo método
     */
    private void crearMetodo(TextField txtNuevo) {
        String nombre = txtNuevo.getText().trim();
        if (nombre.isEmpty()) {
            NotificacionUtil.mostrarAdvertencia(gestor.obtenerStage(),
                IdiomaUtil.obtener("ctrl.metodos_pago.nombre_vacio"));
            return;
        }

        MetodoPagoDto dto = MetodoPagoDto.builder()
            .nombre(nombre.toUpperCase())
            .build();

        txtNuevo.setDisable(true);

        servicio.crearMetodoPagoAsync(dto)
            .thenAccept(creado -> Platform.runLater(() -> {
                metodosCargados.add(creado);
                renderizarLista();
                txtNuevo.clear();
                txtNuevo.setDisable(false);
                NotificacionUtil.mostrarExito(gestor.obtenerStage(),
                    java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.creado"), creado.getNombre(), creado.getCodigo()));
            }))
            .exceptionally(error -> {
                Platform.runLater(() -> {
                    txtNuevo.setDisable(false);
                    NotificacionUtil.mostrarError(gestor.obtenerStage(),
                        java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.error_crear"), error.getCause().getMessage()));
                });
                return null;
            });
    }

    /**
     * Elimina un método de pago previa confirmación del usuario.
     *
     * @param metodo Método de pago a eliminar
     */
    private void eliminarMetodo(MetodoPagoDto metodo) {
        // Construir overlay de confirmación
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        overlay.setAlignment(Pos.CENTER);

        VBox confirmacion = new VBox(14);
        confirmacion.setPadding(new Insets(24));
        confirmacion.setAlignment(Pos.CENTER);
        confirmacion.setMaxWidth(380);
        confirmacion.setMaxHeight(200);
        confirmacion.setStyle("-fx-background-color: rgba(18,18,18,0.95); " +
            "-fx-border-color: #8b0000; -fx-border-width: 1.5px; -fx-border-radius: 12px; " +
            "-fx-background-radius: 12px;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.eliminar_titulo"));
        titulo.getStyleClass().add("modal-titulo-lg");
        titulo.setStyle("-fx-text-fill: #ff6b6b;");

        Label mensaje = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.eliminar_msg"), metodo.getNombre(), metodo.getCodigo()));
        mensaje.getStyleClass().add("modal-mensaje");
        mensaje.setWrapText(true);

        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);

        Button btnSi = new Button(IdiomaUtil.obtener("ctrl.btn.eliminar"));
        btnSi.setPrefSize(120, 38);
        btnSi.getStyleClass().add("panel-btn-secundario");
        btnSi.setStyle("-fx-background-color: #8b0000; -fx-text-fill: #f5f5f5; -fx-font-weight: 600; " +
            "-fx-background-radius: 6; -fx-cursor: hand;");

        Button btnNo = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnNo.setPrefSize(120, 38);
        btnNo.getStyleClass().add("panel-btn-secundario");
        btnNo.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-weight: 600; " +
            "-fx-border-color: #404040; -fx-border-width: 1px; " +
            "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");

        btnNo.setOnAction(e -> overlay.getScene().getRoot().getClass()); // no-op, overlay removed below
        btnNo.setOnAction(e -> {
            ((StackPane) overlay.getParent()).getChildren().remove(overlay);
        });

        btnSi.setOnAction(e -> {
            btnSi.setDisable(true);
            btnSi.setText("...");
            servicio.eliminarMetodoPagoAsync(metodo.getIdMetodoPago())
                .thenAccept(v -> Platform.runLater(() -> {
                    metodosCargados.removeIf(m -> m.getIdMetodoPago().equals(metodo.getIdMetodoPago()));
                    renderizarLista();
                    ((StackPane) overlay.getParent()).getChildren().remove(overlay);
                    NotificacionUtil.mostrarExito(gestor.obtenerStage(),
                        java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.eliminado"), metodo.getNombre()));
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        btnSi.setDisable(false);
                        btnSi.setText(IdiomaUtil.obtener("ctrl.btn.eliminar"));
                        NotificacionUtil.mostrarError(gestor.obtenerStage(),
                            "Error: " + error.getCause().getMessage());
                    });
                    return null;
                });
        });

        botones.getChildren().addAll(btnSi, btnNo);
        confirmacion.getChildren().addAll(titulo, mensaje, botones);
        overlay.getChildren().add(confirmacion);

        // Agregar overlay sobre el modal actual usando el contenedorRaiz
        gestor.mostrarOverlayInterno(overlay);
    }

    /**
     * Abre un mini-modal de edición para cambiar nombre y/o código de un método.
     * Si el código nuevo ya está en uso, muestra confirmación de intercambio.
     *
     * @param metodo Método de pago a editar
     */
    private void editarMetodo(MetodoPagoDto metodo) {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        overlay.setAlignment(Pos.CENTER);

        VBox edicion = new VBox(12);
        edicion.setPadding(new Insets(24));
        edicion.setAlignment(Pos.CENTER_LEFT);
        edicion.setMaxWidth(420);
        edicion.setMaxHeight(320);
        edicion.setStyle("-fx-background-color: rgba(18,18,18,0.95); " +
            "-fx-border-color: rgba(212,175,55,0.4); -fx-border-width: 1.5px; -fx-border-radius: 12px; " +
            "-fx-background-radius: 12px;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.editar_titulo"));
        titulo.getStyleClass().add("modal-titulo-lg");
        titulo.setStyle("-fx-text-fill: #d4af37;");

        // Campo nombre
        Label lblNombre = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.nombre"));
        lblNombre.getStyleClass().add("texto-hint-sm");
        lblNombre.setStyle("-fx-text-fill: #999;");

        TextField txtNombre = new TextField(metodo.getNombre());
        txtNombre.setPrefHeight(38);
        txtNombre.getStyleClass().add("panel-btn-principal");
        txtNombre.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #404040; -fx-border-width: 1.5px; " +
            "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-text-fill: #f5f5f5;");

        // Campo código
        Label lblCodigo = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.codigo"));
        lblCodigo.getStyleClass().add("texto-hint-sm");
        lblCodigo.setStyle("-fx-text-fill: #999;");

        TextField txtCodigo = new TextField(metodo.getCodigo() != null ? metodo.getCodigo() : "");
        txtCodigo.setPrefHeight(38);
        txtCodigo.setPrefWidth(80);
        txtCodigo.setMaxWidth(80);
        txtCodigo.getStyleClass().add("panel-btn-principal");
        txtCodigo.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #404040; -fx-border-width: 1.5px; " +
            "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-text-fill: #f5f5f5; " +
            "-fx-alignment: center;");

        // Limitar a 2 dígitos numéricos
        txtCodigo.setTextFormatter(new TextFormatter<>(change -> {
            String nuevoTexto = change.getControlNewText();
            if (nuevoTexto.length() > 2) return null;
            if (!nuevoTexto.isEmpty() && !nuevoTexto.matches("\\d*")) return null;
            return change;
        }));

        // Info de código actual de otro método si hay conflicto
        Label lblConflicto = new Label();
        lblConflicto.getStyleClass().add("texto-hint");
        lblConflicto.setStyle("-fx-text-fill: #daa520;");
        lblConflicto.setWrapText(true);
        lblConflicto.setVisible(false);
        lblConflicto.setManaged(false);

        // Detectar conflicto de código en tiempo real
        txtCodigo.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() == 2) {
                String codigoPadded = String.format("%2s", newVal).replace(' ', '0');
                // Buscar si otro método ya tiene este código
                MetodoPagoDto conflicto = metodosCargados.stream()
                    .filter(m -> codigoPadded.equals(m.getCodigo())
                        && !m.getIdMetodoPago().equals(metodo.getIdMetodoPago()))
                    .findFirst().orElse(null);
                if (conflicto != null) {
                    lblConflicto.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.conflicto"), codigoPadded, conflicto.getNombre()));
                    lblConflicto.setVisible(true);
                    lblConflicto.setManaged(true);
                } else {
                    lblConflicto.setVisible(false);
                    lblConflicto.setManaged(false);
                }
            } else {
                lblConflicto.setVisible(false);
                lblConflicto.setManaged(false);
            }
        });

        // Botones
        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);

        Button btnGuardar = new Button(IdiomaUtil.obtener("ctrl.btn.guardar"));
        btnGuardar.setPrefSize(130, 38);
        btnGuardar.getStyleClass().add("panel-btn-secundario");
        btnGuardar.setStyle("-fx-background-color: #d4af37; -fx-text-fill: #0a0a0a; -fx-font-weight: 700; " +
            "-fx-background-radius: 6; -fx-cursor: hand;");

        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.setPrefSize(130, 38);
        btnCancelar.getStyleClass().add("panel-btn-secundario");
        btnCancelar.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-weight: 600; " +
            "-fx-border-color: #404040; -fx-border-width: 1px; " +
            "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");

        btnCancelar.setOnAction(e -> gestor.cerrarOverlayInterno(overlay));

        btnGuardar.setOnAction(e -> {
            String nuevoNombre = txtNombre.getText().trim();
            String nuevoCodigo = txtCodigo.getText().trim();

            if (nuevoNombre.isEmpty()) {
                NotificacionUtil.mostrarAdvertencia(gestor.obtenerStage(), IdiomaUtil.obtener("ctrl.metodos_pago.nombre_vacio_editar"));
                return;
            }

            // Padear código a 2 dígitos
            if (!nuevoCodigo.isEmpty() && nuevoCodigo.length() == 1) {
                nuevoCodigo = "0" + nuevoCodigo;
            }

            // Verificar si hay conflicto de código y confirmar el swap
            String codigoFinal = nuevoCodigo;
            boolean codigoCambio = !codigoFinal.equals(metodo.getCodigo() != null ? metodo.getCodigo() : "");

            // Buscar conflicto de código para confirmar swap
            if (codigoCambio && !codigoFinal.isEmpty()) {
                MetodoPagoDto conflicto = metodosCargados.stream()
                    .filter(m -> codigoFinal.equals(m.getCodigo())
                        && !m.getIdMetodoPago().equals(metodo.getIdMetodoPago()))
                    .findFirst().orElse(null);

                if (conflicto != null) {
                    // Mostrar confirmación de swap
                    mostrarConfirmacionSwap(overlay, metodo, nuevoNombre, codigoFinal, conflicto);
                    return;
                }
            }

            // Sin conflicto, guardar directamente
            ejecutarActualizacion(overlay, metodo, nuevoNombre, codigoFinal);
        });

        txtNombre.setOnAction(e -> btnGuardar.fire());

        botones.getChildren().addAll(btnGuardar, btnCancelar);

        edicion.getChildren().addAll(titulo, lblNombre, txtNombre, lblCodigo, txtCodigo, lblConflicto, botones);
        overlay.getChildren().add(edicion);

        gestor.mostrarOverlayInterno(overlay);
        Platform.runLater(() -> txtNombre.selectAll());
    }

    /**
     * Muestra un diálogo de confirmación para intercambiar códigos entre dos métodos.
     *
     * @param overlayPadre   Overlay del modal de edición
     * @param metodo         Método que se está editando
     * @param nuevoNombre    Nuevo nombre a asignar
     * @param nuevoCodigo    Código nuevo deseado
     * @param conflicto      Método que actualmente tiene el código deseado
     */
    private void mostrarConfirmacionSwap(StackPane overlayPadre, MetodoPagoDto metodo,
                                          String nuevoNombre, String nuevoCodigo, MetodoPagoDto conflicto) {
        VBox contenido = new VBox(14);
        contenido.setPadding(new Insets(20));
        contenido.setAlignment(Pos.CENTER);
        contenido.setMaxWidth(380);
        contenido.setStyle("-fx-background-color: rgba(18,18,18,0.98); " +
            "-fx-border-color: #daa520; -fx-border-width: 1.5px; -fx-border-radius: 12px; " +
            "-fx-background-radius: 12px;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.swap_titulo"));
        titulo.getStyleClass().add("modal-titulo-lg");
        titulo.setStyle("-fx-text-fill: #daa520;");

        Label msg = new Label(
            java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.swap_msg"),
                conflicto.getNombre(), nuevoCodigo,
                nuevoNombre.toUpperCase(), nuevoCodigo,
                conflicto.getNombre(), metodo.getCodigo() != null ? metodo.getCodigo() : "??"));
        msg.getStyleClass().add("estado-texto");
        msg.setStyle("-fx-text-fill: #e8e8e8;");
        msg.setWrapText(true);

        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);

        Button btnSi = new Button(IdiomaUtil.obtener("ctrl.metodos_pago.btn.intercambiar"));
        btnSi.setPrefSize(140, 36);
        btnSi.getStyleClass().add("panel-btn-secundario");
        btnSi.setStyle("-fx-background-color: #d4af37; -fx-text-fill: #0a0a0a; -fx-font-weight: 700; " +
            "-fx-background-radius: 6; -fx-cursor: hand;");

        Button btnNo = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnNo.setPrefSize(110, 36);
        btnNo.getStyleClass().add("panel-btn-secundario");
        btnNo.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-weight: 600; " +
            "-fx-border-color: #404040; -fx-border-width: 1px; " +
            "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");

        // Reemplazar overlay de edición por overlay de confirmación
        btnNo.setOnAction(e -> {
            // Volver al overlay de edición (ya está visible, solo quitar confirmación)
            overlayPadre.getChildren().removeIf(n -> n instanceof VBox && n != overlayPadre.getChildren().get(0));
        });

        btnSi.setOnAction(e -> ejecutarActualizacion(overlayPadre, metodo, nuevoNombre, nuevoCodigo));

        botones.getChildren().addAll(btnSi, btnNo);
        contenido.getChildren().addAll(titulo, msg, botones);

        // Agregar sobre el overlay de edición
        overlayPadre.getChildren().add(contenido);
    }

    /**
     * Ejecuta la actualización del método de pago en el servidor.
     * Si hay intercambio de códigos, el servidor lo maneja automáticamente.
     *
     * @param overlay     Overlay a cerrar cuando la operación termine
     * @param metodo      Método de pago a actualizar
     * @param nuevoNombre Nuevo nombre
     * @param nuevoCodigo Nuevo código (puede causar swap en el servidor)
     */
    private void ejecutarActualizacion(StackPane overlay, MetodoPagoDto metodo,
                                        String nuevoNombre, String nuevoCodigo) {
        MetodoPagoDto dto = MetodoPagoDto.builder()
            .idMetodoPago(metodo.getIdMetodoPago())
            .nombre(nuevoNombre.toUpperCase())
            .codigo(nuevoCodigo.isEmpty() ? metodo.getCodigo() : nuevoCodigo)
            .build();

        servicio.actualizarMetodoPagoAsync(dto)
            .thenAccept(actualizado -> Platform.runLater(() -> {
                // Recargar toda la lista desde el servidor para reflejar swaps
                cargarMetodos();
                gestor.cerrarOverlayInterno(overlay);
                NotificacionUtil.mostrarExito(gestor.obtenerStage(),
                    java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.actualizado"), actualizado.getNombre(), actualizado.getCodigo()));
            }))
            .exceptionally(error -> {
                Platform.runLater(() ->
                    NotificacionUtil.mostrarError(gestor.obtenerStage(),
                        java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.metodos_pago.error_actualizar"), error.getCause().getMessage())));
                return null;
            });
    }

    // ==================== RENDERIZADO ====================

    /**
     * Renderiza la lista de métodos de pago en el contenedor del modal.
     * Cada fila muestra: código | nombre | badge/acciones.
     */
    private void renderizarLista() {
        listaMetodosContenedor.getChildren().clear();

        if (metodosCargados.isEmpty()) {
            Label vacio = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.vacio"));
            vacio.getStyleClass().add("estado-texto");
            vacio.setStyle("-fx-text-fill: #888;");
            listaMetodosContenedor.getChildren().add(vacio);
            return;
        }

        for (MetodoPagoDto metodo : metodosCargados) {
            HBox fila = construirFilaMetodo(metodo);
            listaMetodosContenedor.getChildren().add(fila);
        }
    }

    /**
     * Construye una fila visual para un método de pago.
     * Muestra código, nombre, y botones de acción (editar/eliminar) o badge predeterminado.
     *
     * @param metodo Método de pago a representar
     * @return HBox con la fila del método
     */
    private HBox construirFilaMetodo(MetodoPagoDto metodo) {
        HBox fila = new HBox(12);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setPadding(new Insets(10, 14, 10, 14));
        fila.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #2a2a2a; " +
            "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        // Badge de código
        Label lblCodigo = new Label(metodo.getCodigo() != null ? metodo.getCodigo() : "--");
        lblCodigo.setMinWidth(36);
        lblCodigo.setMaxWidth(36);
        lblCodigo.setAlignment(Pos.CENTER);
        lblCodigo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37; " +
            "-fx-background-color: rgba(212,175,55,0.1); -fx-padding: 4 8; " +
            "-fx-background-radius: 4;");
        lblCodigo.getStyleClass().add("panel-seccion-titulo");

        // Nombre del método
        Label lblNombre = new Label(metodo.getNombre());
        lblNombre.getStyleClass().add("panel-seccion-titulo");
        lblNombre.setStyle("-fx-text-fill: #f5f5f5;");
        lblNombre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNombre, Priority.ALWAYS);

        fila.getChildren().addAll(lblCodigo, lblNombre);

        if (Boolean.TRUE.equals(metodo.getEsPredeterminado())) {
            // Badge predeterminado - no editable ni eliminable
            Label badge = new Label(IdiomaUtil.obtener("ctrl.metodos_pago.predeterminado"));
            badge.getStyleClass().add("badge-texto");
            badge.setStyle("-fx-text-fill: #d4af37; " +
                "-fx-background-color: rgba(212,175,55,0.12); -fx-padding: 3 8; " +
                "-fx-border-color: rgba(212,175,55,0.3); -fx-border-width: 1px; " +
                "-fx-border-radius: 10px; -fx-background-radius: 10px;");
            fila.getChildren().add(badge);
        } else {
            // Botones de acción: editar + eliminar
            HBox acciones = new HBox(6);
            acciones.setAlignment(Pos.CENTER_RIGHT);

            Button btnEditar = new Button("✎");
            btnEditar.setMinSize(32, 32);
            btnEditar.setPrefSize(32, 32);
            btnEditar.getStyleClass().add("panel-seccion-titulo");
            btnEditar.setStyle("-fx-background-color: rgba(212,175,55,0.15); -fx-text-fill: #d4af37; " +
                "-fx-background-radius: 6; -fx-cursor: hand;");
            btnEditar.setOnAction(e -> editarMetodo(metodo));

            Button btnEliminar = new Button("✕");
            btnEliminar.setMinSize(32, 32);
            btnEliminar.setPrefSize(32, 32);
            btnEliminar.getStyleClass().add("panel-seccion-titulo");
            btnEliminar.setStyle("-fx-background-color: rgba(139,0,0,0.15); -fx-text-fill: #ff6b6b; " +
                "-fx-background-radius: 6; -fx-cursor: hand;");
            btnEliminar.setOnAction(e -> eliminarMetodo(metodo));

            acciones.getChildren().addAll(btnEditar, btnEliminar);
            fila.getChildren().add(acciones);
        }

        return fila;
    }
}
