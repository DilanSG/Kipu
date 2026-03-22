/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.productos;

import com.baryx.cliente.componente.BordeInteractivoModal;
import com.baryx.cliente.componente.MotorAnimaciones;
import com.baryx.cliente.controlador.MenuPrincipalController;
import com.baryx.cliente.servicio.CategoriaServicio;
import com.baryx.cliente.utilidad.AlertaUtil;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.cliente.utilidad.TecladoVirtualSimple;
import com.baryx.common.dto.CategoriaDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Controlador para el Listado de Categorías.
 * Permite ver, reordenar (drag & drop), editar y eliminar categorías.
 */
public class CategoriaListadoController implements com.baryx.cliente.controlador.SubvistaController {

    private static final Logger logger = LoggerFactory.getLogger(CategoriaListadoController.class);

    @FXML private FlowPane contenedorCategorias;
    @FXML private StackPane contenedorModal;
    @FXML private StackPane contenidoModalInterno;

    private CategoriaServicio categoriaServicio;
    private MenuPrincipalController menuPrincipal;
    private List<CategoriaDto> listaCategorias = new ArrayList<>();
    
    // Almacena la categoría que se está expandiendo actualmente
    private CategoriaDto categoriaExpandida = null;

    // --- REFERENCIA AL MODAL ACTUAL ---
    private StackPane currentModalRoot = null;
    private BordeInteractivoModal bordeModal = null;

    @FXML
    public void initialize() {
        logger.info("Inicializando CategoriaListadoController");
        
        // Inicializar servicio
        String token = com.baryx.cliente.utilidad.NavegacionUtil.getUsuarioActual().getToken();
        this.categoriaServicio = new CategoriaServicio(token);
        
        cargarCategorias();
    }

    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
    }

    /**
     * Carga todas las categorías desde el servidor.
     */
    private void cargarCategorias() {
        categoriaServicio.listarCategoriasAsync()
                .thenAccept(categorias -> Platform.runLater(() -> {
                    this.listaCategorias = new ArrayList<>(categorias);
                    // Asegurar orden
                    this.listaCategorias.sort((a, b) -> Integer.compare(
                        a.getOrden() != null ? a.getOrden() : 99, 
                        b.getOrden() != null ? b.getOrden() : 99));
                    renderizarCategorias();
                }))
                .exceptionally(ex -> {
                    logger.error("Error al cargar categorías", ex);
                    return null;
                });
    }

    /**
     * Renderiza el grid de categorías.
     */
    private void renderizarCategorias() {
        contenedorCategorias.getChildren().clear();
        for (CategoriaDto cat : listaCategorias) {
            contenedorCategorias.getChildren().add(crearCardCategoria(cat));
        }
    }

    /**
     * Crea una tarjeta (card) para una categoría con lógica de expansión y drag & drop.
     */
    private Node crearCardCategoria(CategoriaDto categoria) {
        VBox card = new VBox(15);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("card-categoria-producto");
        card.setPrefSize(250, 120); // Tamaño grande solicitado
        card.setMaxWidth(250); // Forzar ancho
        
        Label nombre = new Label(categoria.getNombre().toUpperCase());
        nombre.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #d4af37;");
        nombre.setWrapText(true);
        nombre.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        card.getChildren().add(nombre);

        // Si esta card es la expandida, añadir botones de acción
        if (categoria.equals(categoriaExpandida)) {
            card.setPrefHeight(180); // Expandir altura
            
            HBox acciones = new HBox(20);
            acciones.setAlignment(Pos.CENTER);
            
            Button btnEditar = new Button(IdiomaUtil.obtener("ctrl.btn.editar"));
            Region iconoEditar = new Region();
            iconoEditar.getStyleClass().addAll("icono-svg-pequeno", "icono-editar");
            btnEditar.setGraphic(iconoEditar);
            btnEditar.getStyleClass().add("boton-accion-mini");
            btnEditar.setStyle("-fx-font-size: 13px; -fx-padding: 8 15;");
            btnEditar.setOnAction(e -> { e.consume(); mostrarFormularioEdicion(categoria); });
            
            Button btnEliminar = new Button(IdiomaUtil.obtener("ctrl.btn.eliminar"));
            Region iconoEliminar = new Region();
            iconoEliminar.getStyleClass().addAll("icono-svg-pequeno", "icono-eliminar");
            btnEliminar.setGraphic(iconoEliminar);
            btnEliminar.getStyleClass().add("boton-accion-mini-rojo");
            btnEliminar.setStyle("-fx-font-size: 13px; -fx-padding: 8 15;");
            btnEliminar.setOnAction(e -> { e.consume(); confirmarEliminacion(categoria); });
            
            acciones.getChildren().addAll(btnEditar, btnEliminar);
            card.getChildren().add(acciones);
        }

        // --- LÓGICA DE CLIC (EXPANSIÓN) ---
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                if (categoria.equals(categoriaExpandida)) {
                    categoriaExpandida = null;
                } else {
                    categoriaExpandida = categoria;
                }
                renderizarCategorias();
            }
        });

        // --- LÓGICA DE DRAG AND DROP ---
        configurarDragAndDrop(card, categoria);

        return card;
    }

    private void configurarDragAndDrop(VBox card, CategoriaDto categoria) {
        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(categoria.getIdCategoria()));
            db.setContent(content);
            card.setOpacity(0.5);
            event.consume();
        });

        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        card.setOnDragEntered(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                card.getStyleClass().add("card-drag-over");
            }
            event.consume();
        });

        card.setOnDragExited(event -> {
            card.getStyleClass().remove("card-drag-over");
            event.consume();
        });

        card.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                Long idArrastrado = Long.valueOf(db.getString());
                int indiceOrigen = -1;
                for (int i = 0; i < listaCategorias.size(); i++) {
                    if (listaCategorias.get(i).getIdCategoria().equals(idArrastrado)) {
                        indiceOrigen = i;
                        break;
                    }
                }
                int indiceDestino = listaCategorias.indexOf(categoria);
                if (indiceOrigen != -1 && indiceDestino != -1 && indiceOrigen != indiceDestino) {
                    CategoriaDto movida = listaCategorias.remove(indiceOrigen);
                    listaCategorias.add(indiceDestino, movida);
                    actualizarOrdenYGuardar();
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        card.setOnDragDone(event -> {
            card.setOpacity(1.0);
            renderizarCategorias();
            event.consume();
        });
    }

    private void actualizarOrdenYGuardar() {
        List<CompletableFuture<CategoriaDto>> futures = new ArrayList<>();
        for (int i = 0; i < listaCategorias.size(); i++) {
            CategoriaDto c = listaCategorias.get(i);
            c.setOrden(i + 1);
            futures.add(categoriaServicio.actualizarCategoriaAsync(c));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    logger.info("Orden de categorías actualizado");
                    Platform.runLater(() -> NotificacionUtil.mostrarExito(getStage(), IdiomaUtil.obtener("ctrl.categorias.orden_actualizado")));
                })
                .exceptionally(ex -> {
                    logger.error("Error al actualizar orden", ex);
                    return null;
                });
        renderizarCategorias();
    }

    // ============================================
    // SISTEMA DE MODALES CRISTAL (OVERLAYS)
    // ============================================

    /**
     * Crea un contenedor modal con efecto cristal tintado negro.
     * Sin animaciones pesadas (FondoAnimado/Canvas/AnimationTimer)
     * para máximo rendimiento en hardware de gama baja.
     * El efecto premium se logra con fondo semi-transparente oscuro,
     * borde dorado sutil y sombra difusa dorada.
     *
     * @param contenido nodo con el contenido interno del modal
     * @param maxWidth  ancho máximo del modal
     * @param maxHeight alto máximo del modal
     * @return StackPane configurado como contenedor modal cristal
     */
    private StackPane crearContenedorModalLuxury(Node contenido, double maxWidth, double maxHeight) {
        StackPane modalRoot = new StackPane();
        modalRoot.getStyleClass().add("modal-luxury");
        modalRoot.setMaxSize(maxWidth, maxHeight);
        // Efecto cristal tintado negro (el borde dorado interactivo lo maneja BordeInteractivoModal)
        modalRoot.setStyle(
            "-fx-background-color: rgba(18, 18, 18, 0.92);" +
            "-fx-background-radius: 16px;" +
            "-fx-border-width: 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.12), 24, 0, 0, 0);"
        );

        // Clipping para bordes redondeados
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(modalRoot.widthProperty());
        clip.heightProperty().bind(modalRoot.heightProperty());
        modalRoot.setClip(clip);

        // Contenido del modal
        modalRoot.getChildren().add(contenido);

        this.currentModalRoot = modalRoot;
        return modalRoot;
    }

    /**
     * Muestra el overlay modal con transición fade-in suave.
     */
    private void mostrarOverlay(Node contenidoModalLuxury) {
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(contenidoModalLuxury);
        contenedorModal.setVisible(true);
        contenedorModal.setManaged(true);
        
        // Activar brillo interactivo del borde dorado (basado en eventos de mouse)
        bordeModal = new BordeInteractivoModal();
        bordeModal.iniciar(currentModalRoot);
        
        // Transición fade-in para entrada elegante
        contenedorModal.setOpacity(0);
        MotorAnimaciones.fade(contenedorModal, 0, 1, 250);
    }

    /**
     * Cierra el overlay modal con transición fade-out suave.
     */
    private void cerrarOverlay() {
        // Detener brillo interactivo del borde
        if (bordeModal != null) {
            bordeModal.detener();
            bordeModal = null;
        }
        
        currentModalRoot = null;
        
        MotorAnimaciones.fade(contenedorModal, 1, 0, 200, () -> {
            contenedorModal.setVisible(false);
            contenedorModal.setManaged(false);
        });
    }

    private void confirmarEliminacion(CategoriaDto categoria) {
        VBox modalContent = new VBox(25);
        modalContent.setAlignment(Pos.CENTER);
        modalContent.setPadding(new javafx.geometry.Insets(30));

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.confirmar.eliminar_categoria"));
        titulo.getStyleClass().add("modal-titulo");
        
        Label mensaje = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.confirmar.eliminar_categoria.msg"), categoria.getNombre()));
        mensaje.getStyleClass().add("modal-mensaje");

        HBox botones = new HBox(20);
        botones.setAlignment(Pos.CENTER);
        botones.getStyleClass().add("modal-botones");

        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setPrefWidth(120);
        btnCancelar.setOnAction(e -> cerrarOverlay());

        Button btnEliminar = new Button(IdiomaUtil.obtener("ctrl.btn.eliminar"));
        btnEliminar.getStyleClass().add("btn-eliminar-luxury");
        btnEliminar.setOnAction(e -> {
            cerrarOverlay();
            eliminarCategoria(categoria);
        });

        botones.getChildren().addAll(btnCancelar, btnEliminar);
        modalContent.getChildren().addAll(titulo, mensaje, botones);
        
        mostrarOverlay(crearContenedorModalLuxury(modalContent, 450, 250));
    }

    private void mostrarFormularioEdicion(CategoriaDto categoria) {
        VBox modalContent = new VBox(25);
        modalContent.setAlignment(Pos.TOP_CENTER);
        modalContent.setPadding(new javafx.geometry.Insets(30));

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.categorias.editar_titulo"));
        titulo.getStyleClass().add("modal-titulo");
        
        VBox campos = new VBox(15);
        campos.setAlignment(Pos.CENTER_LEFT);
        
        Label lblNombre = new Label(IdiomaUtil.obtener("ctrl.categorias.label.nombre"));
        lblNombre.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: bold;");
        TextField txtNombre = new TextField(categoria.getNombre());
        txtNombre.getStyleClass().add("campo-texto-formulario");
        
        Label lblDesc = new Label(IdiomaUtil.obtener("ctrl.categorias.label.descripcion"));
        lblDesc.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: bold;");
        TextArea txtDesc = new TextArea(categoria.getDescripcion());
        txtDesc.getStyleClass().addAll("campo-texto-formulario", "text-area");
        txtDesc.setPrefHeight(120);
        txtDesc.setWrapText(true);

        Label lblColor = new Label(IdiomaUtil.obtener("ctrl.categorias.label.color"));
        lblColor.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: bold;");
        
        final String[] colorEditSeleccionado = { 
            (categoria.getColor() != null && !categoria.getColor().isBlank()) 
                ? categoria.getColor() : "#000000" 
        };
        
        FlowPane contenedorColoresEdit = new FlowPane(10, 10);
        contenedorColoresEdit.setAlignment(Pos.CENTER_LEFT);
        
        Runnable[] reconstruir = new Runnable[1];
        reconstruir[0] = () -> {
            contenedorColoresEdit.getChildren().clear();
            for (String[] entrada : CategoriaCrearController.PALETA_COLORES) {
                String hex = entrada[0];
                String nombre = entrada[1];
                boolean activo = colorEditSeleccionado[0].equalsIgnoreCase(hex);
                StackPane swatch = CategoriaCrearController.crearSwatchColor(hex, nombre, activo);
                swatch.setOnMouseClicked(ev -> {
                    colorEditSeleccionado[0] = hex;
                    reconstruir[0].run();
                });
                contenedorColoresEdit.getChildren().add(swatch);
            }
        };
        reconstruir[0].run();

        campos.getChildren().addAll(lblNombre, txtNombre, lblDesc, txtDesc, lblColor, contenedorColoresEdit);

        HBox botones = new HBox(20);
        botones.setAlignment(Pos.CENTER);
        botones.getStyleClass().add("modal-botones");

        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setPrefWidth(140);
        btnCancelar.setOnAction(e -> cerrarOverlay());

        Button btnGuardar = new Button(IdiomaUtil.obtener("ctrl.categorias.guardar_cambios"));
        btnGuardar.getStyleClass().add("btn-confirmar-luxury"); 
        btnGuardar.setOnAction(e -> {
            categoria.setNombre(txtNombre.getText().toUpperCase());
            categoria.setDescripcion(txtDesc.getText());
            categoria.setColor(colorEditSeleccionado[0]);
            cerrarOverlay();
            
            categoriaServicio.actualizarCategoriaAsync(categoria)
                    .thenAccept(res -> Platform.runLater(() -> {
                        NotificacionUtil.mostrarExito(getStage(), IdiomaUtil.obtener("ctrl.categorias.actualizada"));
                        renderizarCategorias();
                    }));
        });

        botones.getChildren().addAll(btnCancelar, btnGuardar);
        modalContent.getChildren().addAll(titulo, campos, botones);
        
        mostrarOverlay(crearContenedorModalLuxury(modalContent, 500, 550));
        
        // Activar teclado virtual para los campos del modal de edición
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtNombre));
    }

    private void eliminarCategoria(CategoriaDto categoria) {
        categoriaServicio.eliminarCategoriaAsync(categoria.getIdCategoria())
                .thenRun(() -> Platform.runLater(() -> {
                    NotificacionUtil.mostrarExito(getStage(), IdiomaUtil.obtener("ctrl.categorias.eliminada"));
                    listaCategorias.remove(categoria);
                    if (categoria.equals(categoriaExpandida)) categoriaExpandida = null;
                    renderizarCategorias();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> AlertaUtil.mostrarError(IdiomaUtil.obtener("ctrl.categorias.error_eliminar_titulo"), IdiomaUtil.obtener("ctrl.categorias.error_eliminar_msg")));
                    return null;
                });
    }

    private Stage getStage() {
        return (Stage) contenedorCategorias.getScene().getWindow();
    }
}
