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
import com.baryx.cliente.servicio.ProductoServicio;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.cliente.utilidad.TecladoVirtualSimple;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.common.dto.CategoriaDto;
import com.baryx.common.dto.ProductoDto;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la vista de listado de productos organizado por categorías.
 * Presenta un accordion donde cada categoría se puede expandir para mostrar sus productos.
 */
public class ProductoListadoController implements com.baryx.cliente.controlador.SubvistaController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductoListadoController.class);
    
    @FXML private VBox contenedorCategorias;
    @FXML private StackPane contenedorModal;
    @FXML private StackPane contenidoModalInterno;
    
    private MenuPrincipalController menuPrincipal;
    private CategoriaServicio categoriaServicio;
    private ProductoServicio productoServicio;
    
    // Mapa para rastrear el estado de expansión de cada categoría
    private Map<Long, Boolean> categoriasExpanded = new HashMap<>();
    private Map<Long, VBox> categoriasProductosContainers = new HashMap<>();
    
    // Para modales
    private StackPane currentModalRoot;
    private BordeInteractivoModal bordeModal;
    
    @FXML
    public void initialize() {
        logger.info("Inicializando ProductoListadoController");
        
        // Inicializar servicio temporalmente
        categoriaServicio = new CategoriaServicio("");
        productoServicio = new com.baryx.cliente.servicio.ProductoServicio("");
        
    }
    
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
        
        // Reinicializar servicio con token
        if (menuPrincipal.getUsuarioActual() != null) {
            String token = menuPrincipal.getUsuarioActual().getToken();
            categoriaServicio = new CategoriaServicio(token);
            productoServicio = new com.baryx.cliente.servicio.ProductoServicio(token);
            cargarCategorias();
        }
    }
    
    /**
     * Carga las categorías y sus productos desde el servidor.
     */
    private void cargarCategorias() {
        categoriaServicio.listarCategoriasAsync()
                .thenAccept(categoriasList -> {
                    if (categoriasList != null && !categoriasList.isEmpty()) {
                        // Ordenar por ordenVisualizacion
                        categoriasList.sort((c1, c2) -> Integer.compare(c1.getOrden(), c2.getOrden()));
                        
                        Platform.runLater(() -> {
                            contenedorCategorias.getChildren().clear();
                            for (CategoriaDto categoria : categoriasList) {
                                VBox categoriaSection = crearSeccionCategoria(categoria);
                                contenedorCategorias.getChildren().add(categoriaSection);
                            }
                            logger.info("Cargadas {} categorías", categoriasList.size());
                        });
                    }
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar categorías", error);
                    Platform.runLater(() -> mostrarError(IdiomaUtil.obtener("ctrl.productos.error_categorias")));
                    return null;
                });
    }
    
    /**
     * Crea una sección de categoría con header expandible y contenedor de productos.
     */
    private VBox crearSeccionCategoria(CategoriaDto categoria) {
        VBox section = new VBox(12);
        section.getStyleClass().add("categoria-accordion-section");
        
        // Header de categoría (clickeable para expandir/colapsar)
        HBox header = new HBox(16);
        header.getStyleClass().add("categoria-accordion-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 16, 20));
        header.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #d4af37; -fx-border-width: 2px; " +
                       "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-cursor: hand;");
        
        // Icono de expansión
        Label iconoExpansion = new Label("▶");
        iconoExpansion.setStyle("-fx-font-size: 14px; -fx-text-fill: #d4af37;");
        
        // Nombre de categoría
        Label nombreLabel = new Label(categoria.getNombre());
        nombreLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #f5f5f5;");
        HBox.setHgrow(nombreLabel, Priority.ALWAYS);
        
        // Contador de productos
        Label contadorLabel = new Label("...");
        contadorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #b0b0b0;");
        
        header.getChildren().addAll(iconoExpansion, nombreLabel, contadorLabel);
        
        // Contenedor de productos (inicialmente oculto)
        VBox productosContainer = new VBox(12);
        productosContainer.setPadding(new Insets(12, 0, 0, 0));
        productosContainer.setManaged(false);
        productosContainer.setVisible(false);
        
        categoriasProductosContainers.put(categoria.getIdCategoria(), productosContainer);
        categoriasExpanded.put(categoria.getIdCategoria(), false);
        
        // Click handler para expandir/colapsar
        header.setOnMouseClicked(e -> toggleCategoria(categoria, iconoExpansion, productosContainer));
        
        // Efecto hover
        header.setOnMouseEntered(e -> header.setStyle(header.getStyle() + "-fx-background-color: #353535;"));
        header.setOnMouseExited(e -> header.setStyle(header.getStyle().replace("-fx-background-color: #353535;", "-fx-background-color: #2a2a2a;")));
        
        section.getChildren().addAll(header, productosContainer);
        
        // Cargar productos de esta categoría
        // TODO: Implementar cuando exista ProductoServicio
        // Por ahora, simular algunos productos
        cargarProductosCategoria(categoria, productosContainer, contadorLabel);
        
        return section;
    }
    
    /**
     * Carga los productos de una categoría.
     */
    private void cargarProductosCategoria(CategoriaDto categoria, VBox container, Label contador) {
        productoServicio.listarPorCategoriaAsync(categoria.getIdCategoria())
            .thenAccept(productos -> {
                Platform.runLater(() -> {
                    container.getChildren().clear();
                    if (productos != null && !productos.isEmpty()) {
                        contador.setText(productos.size() + " producto" + (productos.size() != 1 ? "s" : ""));
                        for (ProductoDto producto : productos) {
                            HBox productoCard = crearProductoCard(producto, categoria);
                            container.getChildren().add(productoCard);
                        }
                    } else {
                        contador.setText(IdiomaUtil.obtener("ctrl.productos_listado.cero_productos"));
                        Label empty = new Label(IdiomaUtil.obtener("ctrl.inventario.vacio_categoria"));
                        empty.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
                        container.getChildren().add(empty);
                    }
                });
            })
            .exceptionally(error -> {
                logger.error("Error al cargar productos de categoría", error);
                Platform.runLater(() -> contador.setText(IdiomaUtil.obtener("ctrl.productos_listado.error")));
                return null;
            });
    }
    
    /**
     * Crea una tarjeta de producto con botones de acción.
     */
    private HBox crearProductoCard(ProductoDto producto, CategoriaDto categoria) {
        HBox card = new HBox(16);
        card.getStyleClass().add("producto-card-accordion");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #232323; -fx-border-color: rgba(64, 64, 64, 0.6); " +
                     "-fx-border-width: 1.5px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        
        VBox infoBox = new VBox(4);
        Label nombre = new Label(producto.getNombre());
        nombre.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: #f5f5f5;");
        
        HBox detalles = new HBox(16);
        Label precio = new Label(String.format("$%.0f", producto.getPrecio().doubleValue()));
        precio.setStyle("-fx-font-size: 14px; -fx-text-fill: #d4af37; -fx-font-weight: 600;");
        
        Label stock = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.productos.stock"), producto.getStockActual()));
        stock.setStyle("-fx-font-size: 13px; -fx-text-fill: #b0b0b0;");
        
        detalles.getChildren().addAll(precio, stock);
        infoBox.getChildren().addAll(nombre, detalles);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        HBox botonesBox = new HBox(8);
        Button btnEditar = new Button();
        Region iconoEditar = new Region();
        iconoEditar.getStyleClass().addAll("icono-svg-pequeno", "icono-editar");
        btnEditar.setGraphic(iconoEditar);
        btnEditar.getStyleClass().add("boton-accion-mini");
        btnEditar.setOnAction(e -> mostrarFormularioEdicion(producto, categoria));
        
        Button btnEliminar = new Button();
        Region iconoEliminar = new Region();
        iconoEliminar.getStyleClass().addAll("icono-svg-pequeno", "icono-eliminar");
        btnEliminar.setGraphic(iconoEliminar);
        btnEliminar.getStyleClass().add("boton-accion-mini-rojo");
        btnEliminar.setOnAction(e -> confirmarEliminacion(producto, categoria));
        
        botonesBox.getChildren().addAll(btnEditar, btnEliminar);
        card.getChildren().addAll(infoBox, botonesBox);
        
        return card;
    }
    
    /**
     * Muestra formulario de edición de producto.
     */
    private void mostrarFormularioEdicion(ProductoDto producto, CategoriaDto categoria) {
        StackPane modalRoot = crearContenedorModalLuxury(500, 450);
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));
        contenido.setAlignment(Pos.TOP_CENTER);
        
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.editar.producto"));
        titulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #d4af37;");
        
        TextField txtNombre = new TextField(producto.getNombre());
        txtNombre.setPromptText(IdiomaUtil.obtener("ctrl.editar.producto.prompt.nombre"));
        txtNombre.setStyle("-fx-pref-height: 40px;");
        txtNombre.getStyleClass().add("campo-texto-formulario");
        
        TextField txtPrecio = new TextField(producto.getPrecio().toString());
        txtPrecio.setPromptText(IdiomaUtil.obtener("ctrl.editar.producto.prompt.precio"));
        txtPrecio.setStyle("-fx-pref-height: 40px;");
        txtPrecio.getStyleClass().add("campo-texto-formulario");
        
        
        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);
        
        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setOnAction(e -> cerrarOverlay());
        
        Button btnGuardar = new Button(IdiomaUtil.obtener("ctrl.btn.guardar"));
        btnGuardar.getStyleClass().add("btn-confirmar-luxury");
        btnGuardar.setOnAction(e -> {
            try {
                producto.setNombre(txtNombre.getText().toUpperCase());
                producto.setPrecio(new java.math.BigDecimal(txtPrecio.getText()));
                
                productoServicio.actualizarProductoAsync(producto, 
                    actualizado -> {
                        Platform.runLater(() -> {
                            cerrarOverlay();
                            VBox container = categoriasProductosContainers.get(categoria.getIdCategoria());
                            Label contadorLabel = null;
                            if (container != null && container.getParent() != null) {
                                VBox section = (VBox) container.getParent();
                                HBox header = (HBox) section.getChildren().get(0);
                                contadorLabel = (Label) header.getChildren().get(2);
                            }
                            if (contadorLabel != null) {
                                cargarProductosCategoria(categoria, container, contadorLabel);
                            }
                        });
                    },
                    error -> Platform.runLater(() -> mostrarError(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.productos_listado.error_generico"), error.getMessage()))));
            } catch (Exception ex) {
                mostrarError(IdiomaUtil.obtener("ctrl.editar.producto.error.datos"));
            }
        });
        
        botones.getChildren().addAll(btnCancelar, btnGuardar);
        contenido.getChildren().addAll(titulo, txtNombre, txtPrecio, botones);
        modalRoot.getChildren().add(contenido);
        
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
        
        // Activar teclado virtual para los campos del modal de edición
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtNombre));
    }
    
    /**
     * Muestra modal de confirmación de eliminación.
     */
    private void confirmarEliminacion(ProductoDto producto, CategoriaDto categoria) {
        StackPane modalRoot = crearContenedorModalLuxury(450, 300);
        VBox contenido = new VBox(24);
        contenido.setPadding(new Insets(30));
        contenido.setAlignment(Pos.CENTER);
        
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.confirmar.eliminacion"));
        titulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffaa00;");
        
        Label mensaje = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.confirmar.eliminar_producto"), producto.getNombre()));
        mensaje.setWrapText(true);
        mensaje.setStyle("-fx-font-size: 14px; -fx-text-fill: #f5f5f5; -fx-text-alignment: center;");
        
        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);
        
        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setOnAction(e -> cerrarOverlay());
        
        Button btnEliminar = new Button(IdiomaUtil.obtener("ctrl.btn.eliminar"));
        btnEliminar.getStyleClass().add("btn-eliminar-luxury");
        btnEliminar.setOnAction(e -> {
            productoServicio.eliminarProductoAsync(producto.getIdProducto(),
                () -> {
                    Platform.runLater(() -> {
                        cerrarOverlay();
                        VBox container = categoriasProductosContainers.get(categoria.getIdCategoria());
                        Label contadorLabel = null;
                        if (container != null && container.getParent() != null) {
                            VBox section = (VBox) container.getParent();
                            HBox header = (HBox) section.getChildren().get(0);
                            contadorLabel = (Label) header.getChildren().get(2);
                        }
                        if (contadorLabel != null) {
                            cargarProductosCategoria(categoria, container, contadorLabel);
                        }
                    });
                },
                error -> Platform.runLater(() -> mostrarError(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.productos_listado.error_generico"), error.getMessage()))));
        });
        
        botones.getChildren().addAll(btnCancelar, btnEliminar);
        contenido.getChildren().addAll(titulo, mensaje, botones);
        modalRoot.getChildren().add(contenido);
        
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
    }
    
    /**
     * Alterna la expansión/colapso de una categoría.
     */
    private void toggleCategoria(CategoriaDto categoria, Label icono, VBox productosContainer) {
        Long id = categoria.getIdCategoria();
        boolean expanded = categoriasExpanded.getOrDefault(id, false);
        
        if (expanded) {
            // Colapsar
            productosContainer.setManaged(false);
            productosContainer.setVisible(false);
            icono.setText("▶");
            categoriasExpanded.put(id, false);
        } else {
            // Expandir
            productosContainer.setManaged(true);
            productosContainer.setVisible(true);
            icono.setText("▼");
            categoriasExpanded.put(id, true);
        }
    }
    
    // ==================== SISTEMA DE MODALES CRISTAL ====================
    
    /**
     * Crea un contenedor modal con efecto cristal tintado negro.
     * Diseño limpio sin animaciones pesadas (FondoAnimado/Canvas/AnimationTimer)
     * para máximo rendimiento en hardware de gama baja.
     * El efecto premium se logra con fondo semi-transparente oscuro,
     * borde dorado sutil y sombra difusa dorada.
     *
     * @param width  ancho máximo del modal
     * @param height alto máximo del modal
     * @return StackPane configurado como contenedor modal cristal
     */
    private StackPane crearContenedorModalLuxury(int width, int height) {
        StackPane modalRoot = new StackPane();
        modalRoot.setMaxWidth(width);
        modalRoot.setMaxHeight(height);
        // Efecto cristal tintado negro (el borde dorado interactivo lo maneja BordeInteractivoModal)
        modalRoot.setStyle(
            "-fx-background-color: rgba(18, 18, 18, 0.92);" +
            "-fx-background-radius: 16px;" +
            "-fx-border-width: 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.12), 24, 0, 0, 0);"
        );

        currentModalRoot = modalRoot;
        return modalRoot;
    }
    
    /**
     * Muestra el overlay modal con transición fade-in suave.
     */
    private void mostrarOverlay() {
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
        
        MotorAnimaciones.fade(contenedorModal, 1, 0, 200, () -> {
            contenedorModal.setVisible(false);
            contenedorModal.setManaged(false);
            contenidoModalInterno.getChildren().clear();
            currentModalRoot = null;
        });
    }
    
    private void mostrarError(String mensaje) {
        if (menuPrincipal != null) {
            Stage stage = (Stage) contenedorCategorias.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, mensaje);
        }
    }
}
