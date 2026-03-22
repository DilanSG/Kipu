/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.productos;

import com.baryx.cliente.controlador.MenuPrincipalController;
import com.baryx.cliente.servicio.CategoriaServicio;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NavegacionUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.cliente.utilidad.TecladoVirtualSimple;
import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.CategoriaDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador para la vista de creación de categorías.
 * 
 * Responsabilidades:
 * - Validar datos del formulario
 * - Guardar nueva categoría
 * - Gestionar el ordenamiento de categorías mediante Drag & Drop
 * - Comunicarse con el MenuPrincipalController para navegación
 */
public class CategoriaCrearController implements com.baryx.cliente.controlador.SubvistaController {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoriaCrearController.class);
    
    // ==================== COMPONENTES DEL FORMULARIO ====================
    
    @FXML private TextField txtNombre;
    @FXML private TextArea txtDescripcion;
    @FXML private FlowPane contenedorColores;
    @FXML private FlowPane contenedorCategorias;
    @FXML private Label errorNombre;
    
    // ==================== PALETA DE COLORES ====================
    
    /** Colores curados que combinan con el hover dorado (#d4af37) */
    static final String[][] PALETA_COLORES = {
        {"#000000", "Negro"},
        {"#5C1010", "Burdeos"},
        {"#0D1B3E", "Azul Noche"},
        {"#0D3B1E", "Esmeralda"},
        {"#2D0A4E", "P\u00farpura"},
        {"#4E2600", "Bronce"},
        {"#0A3D3A", "Teal"},
        {"#2A2A2A", "Grafito"},
    };
    
    private String colorSeleccionado = "#000000";
    private Region indicadorColorActual;
    
    // ==================== ATRIBUTOS ====================
    
    private MenuPrincipalController menuPrincipal;
    private CategoriaServicio categoriaServicio;
    private ObservableList<CategoriaDto> listaCategorias;
    private CategoriaDto categoriaPrevisualizacion; // La categoría que estamos creando
    private boolean huboCambiosOrden = false;
    private static final String ID_NUEVA_CATEGORIA = "NUEVA";
    
    // ==================== INICIALIZACIÓN ====================
    
    @FXML
    public void initialize() {
        logger.info("Inicializando CategoriaCrearController");
        
        // Inicializar servicio
        String token = NavegacionUtil.getUsuarioActual().getToken();
        this.categoriaServicio = new CategoriaServicio(token);
        this.listaCategorias = FXCollections.observableArrayList();
        
        // Inicializar categoría de previsualización
        this.categoriaPrevisualizacion = new CategoriaDto();
        this.categoriaPrevisualizacion.setNombre("NUEVA CATEGORÍA"); // Placeholder inicial
        this.categoriaPrevisualizacion.setIdCategoria(null); // ID nulo indica que es nueva
        this.categoriaPrevisualizacion.setColor("#000000");
        
        // Construir selector de colores inline
        construirSelectorColores();
        
        // Listeners validación y actualización en tiempo real
        txtNombre.textProperty().addListener((obs, old, nuevo) -> {
            boolean isEmpty = nuevo == null || nuevo.trim().isEmpty();
            if (!isEmpty) ocultarError(errorNombre);
            
            // Actualizar nombre en la card de previsualización
            String nombreMostrar = isEmpty ? "NUEVA CATEGORÍA" : nuevo.toUpperCase();
            categoriaPrevisualizacion.setNombre(nombreMostrar);
            
            // Refrescar solo la card correspondiente (optimización básica: re-renderizar todo es más seguro por ahora)
            renderizarCategorias();
        });
        
        // Cargar categorías existentes para ordenar
        cargarCategorias();
        
        // Activar teclado virtual para campos de texto
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtNombre));
        
        logger.info("CategoriaCrearController inicializado correctamente");
    }
    
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
    }
    
    private void cargarCategorias() {
        categoriaServicio.listarCategoriasAsync()
            .thenAccept(categorias -> {
                Platform.runLater(() -> {
                    // Ordenar existentes por campo 'orden'
                    categorias.sort((c1, c2) -> {
                        int o1 = c1.getOrden() != null ? c1.getOrden() : 999;
                        int o2 = c2.getOrden() != null ? c2.getOrden() : 999;
                        return Integer.compare(o1, o2);
                    });
                    
                    listaCategorias.setAll(categorias);
                    // Agregar la nueva categoría al final por defecto
                    listaCategorias.add(categoriaPrevisualizacion);
                    
                    renderizarCategorias();
                });
            })
            .exceptionally(error -> {
                logger.error("Error al cargar categorías", error);
                return null;
            });
    }
    
    // ==================== DRAG AND DROP LÓGICA ====================
    
    private void renderizarCategorias() {
        contenedorCategorias.getChildren().clear();
        
        for (CategoriaDto cat : listaCategorias) {
            Button card = crearCardArrastrable(cat);
            contenedorCategorias.getChildren().add(card);
        }
    }
    
    private Button crearCardArrastrable(CategoriaDto categoria) {
        String texto = categoria.getNombre() != null ? categoria.getNombre().toUpperCase() : "SIN NOMBRE";
        Button card = new Button(texto);
        
        // Estilo diferente para la categoría nueva
        if (categoria == categoriaPrevisualizacion) {
             card.getStyleClass().add("card-categoria-producto-nueva"); 
             // Ajustar tamaño para grid compacto
             card.setStyle("-fx-pref-width: 160; -fx-pref-height: 60; -fx-font-size: 11px; -fx-cursor: move;");
        } else {
             card.getStyleClass().add("card-categoria-producto");
             card.setStyle("-fx-pref-width: 160; -fx-pref-height: 60; -fx-font-size: 11px; -fx-cursor: move;");
        }
        
        card.setWrapText(true);
        
        // Tooltip con instrucciones
        Tooltip tooltip = new Tooltip("Arrastra para reordenar");
        tooltip.setShowDelay(javafx.util.Duration.seconds(1));
        card.setTooltip(tooltip);
        
        // Configurar Drag and Drop
        
        // 1. Detectar inicio del arrastre
        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            
            // Usar ID o marca especial para la nueva
            if (categoria == categoriaPrevisualizacion) {
                content.putString(ID_NUEVA_CATEGORIA);
            } else {
                content.putString(categoria.getIdCategoria().toString());
            }
            
            db.setContent(content);
            card.setOpacity(0.4);
            event.consume();
        });
        
        // 2. Arrastrar sobre otro nodo
        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        
        // 3. Soltar (Drop)
        card.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasString()) {
                String idStr = db.getString();
                CategoriaDto catOrigen = null;
                
                if (ID_NUEVA_CATEGORIA.equals(idStr)) {
                    catOrigen = categoriaPrevisualizacion;
                } else {
                    try {
                        Long id = Long.parseLong(idStr);
                        catOrigen = listaCategorias.stream()
                                .filter(c -> c != categoriaPrevisualizacion && c.getIdCategoria().equals(id))
                                .findFirst().orElse(null);
                    } catch (NumberFormatException e) {
                        // Ignorar IDs inválidos
                    }
                }
                
                if (catOrigen != null) {
                    int indiceOrigen = listaCategorias.indexOf(catOrigen);
                    int indiceDestino = listaCategorias.indexOf(categoria);
                    
                    if (indiceOrigen >= 0 && indiceDestino >= 0) {
                        listaCategorias.remove(indiceOrigen);
                        listaCategorias.add(indiceDestino, catOrigen);
                        
                        renderizarCategorias();
                        huboCambiosOrden = true;
                        success = true;
                    }
                }
            }
            
            event.setDropCompleted(success);
            event.consume();
        });
        
        // 4. Finalizar arrastre
        card.setOnDragDone(event -> {
            card.setOpacity(1.0);
            event.consume();
        });
        
        return card;
    }
    
    // ==================== ACCIONES ====================
    
    @FXML
    private void guardar() {
        logger.info("Intentando guardar nueva categoría con orden personalizado");
        
        if (!validarFormulario()) {
            return;
        }
        
        Stage stage = (Stage) txtNombre.getScene().getWindow();
        
        // 1. Configurar datos finales de la categoría nueva
        categoriaPrevisualizacion.setNombre(txtNombre.getText().trim());
        categoriaPrevisualizacion.setDescripcion(txtDescripcion.getText().trim());
        categoriaPrevisualizacion.setActivo(true);
        
        // Calcular su orden basado en la posición actual en la lista
        int indiceNueva = listaCategorias.indexOf(categoriaPrevisualizacion);
        categoriaPrevisualizacion.setOrden(indiceNueva + 1); // 1-based index
        
        // 2. Guardar nueva categoría
        categoriaServicio.crearCategoriaAsync(categoriaPrevisualizacion)
            .thenCompose(catCreada -> {
                // 3. Actualizar el orden de las DEMÁS categorías si es necesario
                // Recorremos toda la lista visual.
                List<CompletableFuture<CategoriaDto>> futures = new ArrayList<>();
                for (int i = 0; i < listaCategorias.size(); i++) {
                    CategoriaDto c = listaCategorias.get(i);
                    int nuevoOrden = i + 1;
                    
                    // Si es la categoría nueva, ya se guardó con el orden correcto (teóricamente, 
                    // aunque el servidor podría ignorarlo si no lo manejamos, pero el cliente lo envió).
                    // Si es una categoría existente, verificamos si su orden cambió.
                    if (c != categoriaPrevisualizacion) { // Es una existente
                        if (c.getOrden() == null || c.getOrden() != nuevoOrden) {
                            c.setOrden(nuevoOrden);
                            futures.add(categoriaServicio.actualizarCategoriaAsync(c));
                        }
                    }
                }
                
                if (futures.isEmpty()) {
                    return CompletableFuture.completedFuture(catCreada);
                } else {
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> catCreada);
                }
            })
            .thenAccept(cat -> {
                Platform.runLater(() -> {
                    NotificacionUtil.mostrarExito(stage, Constantes.Mensajes.CATEGORIA_CREADA);
                    if (menuPrincipal != null) menuPrincipal.volverAtrasPublico();
                });
            })
            .exceptionally(error -> {
                logger.error("Error al guardar categoría", error);
                Platform.runLater(() -> {
                    NotificacionUtil.mostrarError(stage, java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.categorias.error_guardar"), error.getMessage()));
                });
                return null;
            });
    }
    
    @FXML
    private void cancelar() {
        limpiarFormulario();
        if (menuPrincipal != null) {
            menuPrincipal.volverAtrasPublico();
        }
    }
    
    // ==================== VALIDACIÓN Y UTILIDADES ====================
    
    private boolean validarFormulario() {
        boolean esValido = true;
        
        if (txtNombre.getText() == null || txtNombre.getText().trim().isEmpty()) {
            mostrarError(errorNombre, Constantes.Mensajes.NOMBRE_REQUERIDO);
            esValido = false;
        } else if (txtNombre.getText().trim().length() < 3) {
            mostrarError(errorNombre, Constantes.Mensajes.NOMBRE_MIN_LENGTH);
            esValido = false;
        }
        
        return esValido;
    }
    
    private void mostrarError(Label label, String mensaje) {
        label.setText(mensaje);
        label.setVisible(true);
        label.setManaged(true);
    }
    
    private void ocultarError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
    
    private void limpiarFormulario() {
        txtNombre.clear();
        txtDescripcion.clear();
        colorSeleccionado = "#000000";
        construirSelectorColores();
        ocultarError(errorNombre);
    }

    /** Construye el selector visual de colores con swatches circulares. */
    private void construirSelectorColores() {
        if (contenedorColores == null) return;
        contenedorColores.getChildren().clear();
        
        for (String[] entrada : PALETA_COLORES) {
            String hex = entrada[0];
            String nombre = entrada[1];
            
            StackPane swatch = crearSwatchColor(hex, nombre, colorSeleccionado.equals(hex));
            swatch.setOnMouseClicked(e -> {
                colorSeleccionado = hex;
                categoriaPrevisualizacion.setColor(hex);
                construirSelectorColores();
                renderizarCategorias();
            });
            contenedorColores.getChildren().add(swatch);
        }
    }

    /**
     * Crea un swatch circular de color con indicador de selecci\u00f3n.
     * @param hex     Color en hexadecimal
     * @param nombre  Nombre descriptivo para tooltip
     * @param activo  Si est\u00e1 seleccionado actualmente
     * @return StackPane con el swatch
     */
    static StackPane crearSwatchColor(String hex, String nombre, boolean activo) {
        Region circulo = new Region();
        circulo.setPrefSize(36, 36);
        circulo.setMinSize(36, 36);
        circulo.setMaxSize(36, 36);
        circulo.setStyle(
            "-fx-background-color: " + hex + ";" +
            "-fx-background-radius: 18;" +
            "-fx-border-radius: 18;" +
            "-fx-border-width: 2;" +
            "-fx-border-color: " + (activo ? "#d4af37" : "rgba(255,255,255,0.15)") + ";" +
            "-fx-cursor: hand;"
        );
        
        StackPane wrapper = new StackPane(circulo);
        wrapper.setPrefSize(44, 44);
        
        if (activo) {
            Region check = new Region();
            check.setPrefSize(14, 14);
            check.setMaxSize(14, 14);
            check.setStyle(
                "-fx-background-color: #d4af37;" +
                "-fx-background-radius: 7;" +
                "-fx-border-radius: 7;" +
                "-fx-border-color: #000000;" +
                "-fx-border-width: 1.5;"
            );
            wrapper.getChildren().add(check);
        }
        
        Tooltip tip = new Tooltip(nombre);
        tip.setShowDelay(javafx.util.Duration.millis(300));
        Tooltip.install(wrapper, tip);
        
        // Hover: borde dorado
        wrapper.setOnMouseEntered(e -> {
            if (!activo) circulo.setStyle(circulo.getStyle().replace(
                "rgba(255,255,255,0.15)", "rgba(212,175,55,0.6)"));
        });
        wrapper.setOnMouseExited(e -> {
            if (!activo) circulo.setStyle(circulo.getStyle().replace(
                "rgba(212,175,55,0.6)", "rgba(255,255,255,0.15)"));
        });
        
        return wrapper;
    }
}
