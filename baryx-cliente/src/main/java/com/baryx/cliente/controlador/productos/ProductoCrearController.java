/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.productos;

import com.baryx.common.constantes.Constantes;
import com.baryx.cliente.controlador.MenuPrincipalController;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.cliente.utilidad.TecladoVirtualSimple;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Controlador para la vista de creación de productos.
 * 
 * Responsabilidades:
 * - Validar datos del formulario
 * - Guardar nuevo producto
 * - Comunicarse con el MenuPrincipalController para navegación
 * 
 * Campos del formulario:
 * - Nombre del producto
 * - Cantidad en stock
 * - Precio de venta
 * - Categoría a la que pertenece
 */
public class ProductoCrearController implements com.baryx.cliente.controlador.SubvistaController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductoCrearController.class);
    
    // ==================== COMPONENTES DEL FORMULARIO ====================
    
    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtCantidad;

    @FXML
    private TextField txtPrecio;

    @FXML
    private ComboBox<String> comboCategoria;

    @FXML
    private Label errorNombre;

    @FXML
    private Label errorCantidad;

    @FXML
    private Label errorPrecio;

    @FXML
    private Label errorCategoria;
    
    // ==================== ATRIBUTOS ====================
    
    private MenuPrincipalController menuPrincipal;
    private com.baryx.cliente.servicio.CategoriaServicio categoriaServicio;
    private com.baryx.cliente.servicio.ProductoServicio productoServicio;
    private java.util.Map<String, Long> categoriaNombreAId = new java.util.HashMap<>();
    
    // ==================== INICIALIZACIÓN ====================
    
    /**
     * Inicializa el controlador después de que se cargue el FXML.
     */
    @FXML
    public void initialize() {
        logger.info("Inicializando ProductoCrearController");
        
        // Inicializar servicio con token vacío temporalmente
        categoriaServicio = new com.baryx.cliente.servicio.CategoriaServicio("");
        
        
        

        // Forzar que solo se ingresen números en precio (permite punto decimal)
        txtPrecio.textProperty().addListener((obs, old, nuevo) -> {
            if (!nuevo.matches("\\d*\\.?\\d*")) {
                txtPrecio.setText(old);
            } else if (!nuevo.isEmpty()) {
                ocultarError(errorPrecio);
            }
        });
        
        // Listeners para limpiar errores al escribir
        txtNombre.textProperty().addListener((obs, old, nuevo) -> {
            if (!nuevo.trim().isEmpty()) {
                ocultarError(errorNombre);
            }
        });
        
        comboCategoria.valueProperty().addListener((obs, old, nuevo) -> {
            if (nuevo != null) {
                ocultarError(errorCategoria);
            }
        });
        
        // Activar teclado virtual para campos de texto
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtNombre));
        
    }
    
    /**
     * Carga las categorías desde el servidor y las muestra en el ComboBox.
     */
    private void cargarCategorias() {
        categoriaServicio.listarCategoriasAsync()
                .thenAccept(categorias -> {
                    if (categorias != null && !categorias.isEmpty()) {
                        // Ordenar categorías por orden
                        categorias.sort((c1, c2) -> Integer.compare(c1.getOrden(), c2.getOrden()));
                        
                        // Limpiar mapa anterior
                        categoriaNombreAId.clear();
                        
                        // Extraer nombres y mapear a IDs
                        java.util.List<String> nombresCategories = categorias.stream()
                            .map(cat -> {
                                categoriaNombreAId.put(cat.getNombre(), cat.getIdCategoria());
                                return cat.getNombre();
                            })
                            .collect(java.util.stream.Collectors.toList());
                        
                        javafx.application.Platform.runLater(() -> {
                            comboCategoria.setItems(FXCollections.observableArrayList(nombresCategories));
                            logger.info("Cargadas {} categorías con mapeo de IDs", nombresCategories.size());
                        });
                    }
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar categorías", error);
                    javafx.application.Platform.runLater(() -> {
                        if (txtNombre.getScene() != null) {
                            Stage stage = (Stage) txtNombre.getScene().getWindow();
                            NotificacionUtil.mostrarError(stage, "Error al cargar categorías: " + error.getMessage());
                        }
                    });
                    return null;
                });
    }
    
    /**
     * Método llamado por MenuPrincipalController después de cargar el FXML.
     * Permite inyectar la referencia al controlador padre.
     */
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
        logger.info("Referencia al MenuPrincipalController establecida");
        
        // Reinicializar servicios con el token correcto
        if (menuPrincipal.getUsuarioActual() != null) {
            String token = menuPrincipal.getUsuarioActual().getToken();
            categoriaServicio = new com.baryx.cliente.servicio.CategoriaServicio(token);
            productoServicio = new com.baryx.cliente.servicio.ProductoServicio(token);
            cargarCategorias();
        }
    }
    
    // ==================== ACCIONES ====================
    
    /**
     * Acción: Guardar nuevo producto.
     * 
     * Proceso:
     * 1. Validar campos del formulario
     * 2. Crear objeto Producto
     * 3. Enviar al servidor (TODO: implementar servicio)
     * 4. Mostrar notificación de éxito
     * 5. Volver al menú de productos
     */
    @FXML
    private void guardar() {
        logger.info("Intentando guardar nuevo producto");
        
        // Validar formulario
        if (!validarFormulario()) {
            logger.warn("Formulario inválido, no se puede guardar");
            return;
        }
        
        // Obtener datos del formulario
        String nombre = txtNombre.getText().trim();
        BigDecimal precio = new BigDecimal(txtPrecio.getText().trim());
        String categoriaNombre = comboCategoria.getValue();
        
        // Obtener ID de categoría del mapa
        Long idCategoria = categoriaNombreAId.get(categoriaNombre);
        if (idCategoria == null) {
            logger.error("No se encontró ID para la categoría: {}", categoriaNombre);
            Stage stage = (Stage) txtNombre.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, "Error: categoría no válida");
            return;
        }
        
        logger.info("Producto a guardar - Nombre: {}, Precio: {}, Categoría: {} (ID: {})", 
                   nombre, precio, categoriaNombre, idCategoria);
        
        com.baryx.common.dto.ProductoDto nuevoProducto = com.baryx.common.dto.ProductoDto.builder()
            .nombre(nombre.toUpperCase())
            .precio(precio)
            .stockActual(0)
            .stockMinimo(0)
            .requiereStock(false)
            .idCategoria(idCategoria)
            .build();
        
        // Llamar servicio para crear producto
        productoServicio.crearAsync(nuevoProducto,
            creado -> javafx.application.Platform.runLater(() -> {
                Stage stage = (Stage) txtNombre.getScene().getWindow();
                NotificacionUtil.mostrarExito(stage, Constantes.Mensajes.PRODUCTO_CREADO);
                limpiarFormulario();
                if (menuPrincipal != null) {
                    menuPrincipal.volverAtrasPublico();
                }
            }),
            error -> javafx.application.Platform.runLater(() -> {
                Stage stage = (Stage) txtNombre.getScene().getWindow();
                String mensaje = "Error al crear producto: " + error.getMessage();
                NotificacionUtil.mostrarError(stage, mensaje);
                logger.error("Error al crear producto", error);
            })
        );
    }
    
    /**
     * Acción: Cancelar creación y volver atrás.
     */
    @FXML
    private void cancelar() {
        logger.info("Cancelando creación de producto");
        
        // Limpiar formulario
        limpiarFormulario();
        
        // Volver al menú de productos
        if (menuPrincipal != null) {
            menuPrincipal.volverAtrasPublico();
        }
    }
    
    // ==================== VALIDACIÓN ====================
    
    /**
     * Valida todos los campos del formulario.
     * 
     * @return true si el formulario es válido, false en caso contrario
     */
    private boolean validarFormulario() {
        boolean esValido = true;
        
        // Validar nombre
        if (txtNombre.getText() == null || txtNombre.getText().trim().isEmpty()) {
            mostrarError(errorNombre, Constantes.Mensajes.NOMBRE_REQUERIDO);
            esValido = false;
        } else if (txtNombre.getText().trim().length() < 3) {
            mostrarError(errorNombre, Constantes.Mensajes.NOMBRE_MIN_LENGTH);
            esValido = false;
        } else if (txtNombre.getText().trim().length() > 100) {
            mostrarError(errorNombre, Constantes.Mensajes.NOMBRE_MAX_LENGTH);
            esValido = false;
        }
        
        
        // Validar precio
        if (txtPrecio.getText() == null || txtPrecio.getText().trim().isEmpty()) {
            mostrarError(errorPrecio, Constantes.Mensajes.PRECIO_MAYOR_CERO);
            esValido = false;
        } else {
            try {
                BigDecimal precio = new BigDecimal(txtPrecio.getText().trim());
                if (precio.compareTo(BigDecimal.ZERO) <= 0) {
                    mostrarError(errorPrecio, Constantes.Mensajes.PRECIO_MAYOR_CERO);
                    esValido = false;
                } else if (precio.compareTo(new BigDecimal("999999999")) > 0) {
                    mostrarError(errorPrecio, Constantes.Mensajes.PRECIO_GRANDE);
                    esValido = false;
                }
            } catch (NumberFormatException e) {
                mostrarError(errorPrecio, Constantes.Mensajes.PRECIO_INVALIDO);
                esValido = false;
            }
        }
        
        // Validar categoría
        if (comboCategoria.getValue() == null) {
            mostrarError(errorCategoria, Constantes.Mensajes.DEBE_SELECCIONAR_CATEGORIA);
            esValido = false;
        }
        
        return esValido;
    }
    
    /**
     * Muestra un mensaje de error en un label específico.
     */
    private void mostrarError(Label labelError, String mensaje) {
        labelError.setText(mensaje);
        labelError.setVisible(true);
        labelError.setManaged(true);
    }
    
    /**
     * Oculta un label de error.
     */
    private void ocultarError(Label labelError) {
        labelError.setVisible(false);
        labelError.setManaged(false);
    }
    
    /**
     * Limpia todos los campos del formulario.
     */
    private void limpiarFormulario() {
        txtNombre.clear();
        txtCantidad.clear();
        txtPrecio.clear();
        comboCategoria.setValue(null);
        ocultarError(errorNombre);
        ocultarError(errorCantidad);
        ocultarError(errorPrecio);
        ocultarError(errorCategoria);
    }
}
