/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.productos;

import com.kipu.cliente.componente.BordeInteractivoModal;
import com.kipu.cliente.controlador.MenuPrincipalController;
import com.kipu.cliente.servicio.CategoriaServicio;
import com.kipu.cliente.servicio.ProductoServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.cliente.utilidad.TecladoVirtualSimple;
import com.kipu.common.dto.CategoriaDto;
import com.kipu.common.dto.ProductoDto;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para la vista de Control de Inventario.
 * 
 * Responsabilidades:
 * - Mostrar productos agrupados por categorías en el orden personalizado
 * - Cada producto muestra: Cantidad Inicial, Entrada, Salida, Baja,
 *   Salida Especial/Cortesía, Inventario Actual
 * - Permite ajustar movimientos de stock (entrada, salida, baja, cortesía)
 * - Los productos se organizan en secciones por categoría, siguiendo el orden
 *   configurado en el listado de categorías
 * 
 * Columnas de la tabla:
 * - Código: Código de 3 dígitos del producto (000-999)
 * - Producto: Nombre del producto
 * - Cantidad Inicial: Stock al inicio del periodo (stockActual antes de movimientos)
 * - Entrada: Unidades ingresadas al inventario
 * - Salida: Unidades vendidas/salidas normales
 * - Baja: Unidades dadas de baja (dañadas, vencidas)
 * - Salida Especial/Cortesía: Unidades de cortesía o salida especial
 * - Inventario Actual: Stock actual calculado
 * - Opciones: Botones de acción (ajustar stock)
 * 
 * Solo accesible por rol ADMIN.
 */
public class InventarioControlController implements com.kipu.cliente.controlador.SubvistaController {
    
    private static final Logger logger = LoggerFactory.getLogger(InventarioControlController.class);
    
    // ==================== COMPONENTES FXML ====================
    
    @FXML private TextField campoBusqueda;
    @FXML private ComboBox<String> comboFiltroCategoria;
    @FXML private VBox contenedorInventario;
    @FXML private StackPane contenedorModal;
    @FXML private StackPane contenidoModalInterno;
    
    // ==================== SERVICIOS ====================
    
    private MenuPrincipalController menuPrincipal;
    private CategoriaServicio categoriaServicio;
    private ProductoServicio productoServicio;
    
    // ==================== CONSTANTES DE ANCHO DE COLUMNAS ====================
    
    /** Anchos fijos para cada columna de la tabla (en píxeles) */
    private static final double ANCHO_CODIGO = 60;
    private static final double ANCHO_PRODUCTO = 180;
    private static final double ANCHO_INICIAL = 90;
    private static final double ANCHO_ENTRADA = 75;
    private static final double ANCHO_SALIDA = 75;
    private static final double ANCHO_BAJA = 65;
    private static final double ANCHO_CORTESIA = 85;
    private static final double ANCHO_ACTUAL = 90;
    private static final double ANCHO_OPCIONES = 70;
    
    /** Ancho máximo del contenedor de cada sección de categoría */
    private static final double ANCHO_MAX_SECCION = 900;
    
    // ==================== DATOS ====================
    
    /** Lista completa de categorías ordenadas */
    private List<CategoriaDto> categorias = new ArrayList<>();
    
    /** Mapa de productos por ID de categoría */
    private Map<Long, List<ProductoDto>> productosPorCategoria = new HashMap<>();
    
    /** Mapa de movimientos de inventario por ID de producto: [entrada, salida, baja, cortesia] */
    private Map<Long, int[]> movimientosProducto = new HashMap<>();
    
    /** Para modales */
    private StackPane currentModalRoot;
    private BordeInteractivoModal bordeModal;
    
    // ==================== INICIALIZACIÓN ====================
    
    /**
     * Inicializa el controlador después de que se cargue el FXML.
     * Configura servicios con token temporal y prepara el filtro de categorías.
     */
    @FXML
    public void initialize() {
        logger.info("Inicializando InventarioControlController");
        
        // Inicializar servicios con token temporal (se reinicializan en setMenuPrincipal)
        categoriaServicio = new CategoriaServicio("");
        productoServicio = new ProductoServicio("");
        
        // Configurar filtro de categorías
        configurarFiltroCategoria();
        
        // Configurar búsqueda en tiempo real
        configurarBusqueda();
        
        // Activar teclado virtual para el campo de búsqueda
        Platform.runLater(() -> TecladoVirtualSimple.activar(campoBusqueda));
    }
    
    /**
     * Establece la referencia al controlador principal del menú.
     * Se llama automáticamente por MenuPrincipalController al cargar la subvista.
     * Reinicializa los servicios con el token JWT del usuario autenticado.
     * 
     * @param menuPrincipal Controlador del menú principal
     */
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
        
        // Reinicializar servicios con token válido
        if (menuPrincipal.getUsuarioActual() != null) {
            String token = menuPrincipal.getUsuarioActual().getToken();
            categoriaServicio = new CategoriaServicio(token);
            productoServicio = new ProductoServicio(token);
            cargarInventarioCompleto();
        }
    }
    
    /**
     * Configura el ComboBox de filtro por categoría.
     * Se añade la opción "Todas" por defecto y se escucha cambios de selección.
     */
    private void configurarFiltroCategoria() {
        comboFiltroCategoria.setItems(FXCollections.observableArrayList(IdiomaUtil.obtener("ctrl.inventario.filtro.todas")));
        comboFiltroCategoria.setValue(IdiomaUtil.obtener("ctrl.inventario.filtro.todas"));
        
        comboFiltroCategoria.setOnAction(e -> filtrarPorCategoria());
    }
    
    /**
     * Configura la búsqueda en tiempo real al escribir en el campo de búsqueda.
     * Filtra los productos visibles por nombre o código.
     */
    private void configurarBusqueda() {
        campoBusqueda.textProperty().addListener((obs, viejo, nuevo) -> {
            filtrarProductos(nuevo);
        });
    }
    
    // ==================== CARGA DE DATOS ====================
    
    /**
     * Carga el inventario completo: categorías ordenadas y sus productos.
     * Primero carga las categorías (ordenadas por el campo 'orden'),
     * luego carga los productos de cada categoría.
     */
    private void cargarInventarioCompleto() {
        logger.info("Cargando inventario completo");
        
        // Mostrar indicador de carga
        mostrarIndicadorCarga();
        
        categoriaServicio.listarCategoriasAsync()
                .thenAccept(listaCategorias -> {
                    if (listaCategorias != null && !listaCategorias.isEmpty()) {
                        // Ordenar categorías por el campo 'orden' (personalizado por el admin)
                        listaCategorias.sort((c1, c2) -> Integer.compare(c1.getOrden(), c2.getOrden()));
                        categorias = listaCategorias;
                        
                        // Actualizar combo de filtro con categorías
                        Platform.runLater(() -> {
                            List<String> opciones = new ArrayList<>();
                            opciones.add(IdiomaUtil.obtener("ctrl.inventario.filtro.todas"));
                            for (CategoriaDto cat : categorias) {
                                opciones.add(cat.getNombre());
                            }
                            comboFiltroCategoria.setItems(FXCollections.observableArrayList(opciones));
                            comboFiltroCategoria.setValue(IdiomaUtil.obtener("ctrl.inventario.filtro.todas"));
                        });
                        
                        // Cargar productos de todas las categorías
                        cargarProductosTodasCategorias();
                    } else {
                        Platform.runLater(() -> {
                            contenedorInventario.getChildren().clear();
                            mostrarMensajeVacio(IdiomaUtil.obtener("ctrl.inventario.vacio_categorias"));
                        });
                    }
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar categorías para inventario", error);
                    Platform.runLater(() -> {
                        contenedorInventario.getChildren().clear();
                        mostrarMensajeError(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.inventario.error_categorias"), error.getMessage()));
                    });
                    return null;
                });
    }
    
    /**
     * Carga los productos de todas las categorías de forma secuencial.
     * Cada categoría genera una sección en la vista con su tabla de inventario.
     */
    private void cargarProductosTodasCategorias() {
        productoServicio.listarTodosAsync()
                .thenAccept(todosProductos -> {
                    if (todosProductos != null) {
                        // Agrupar productos por categoría
                        productosPorCategoria.clear();
                        for (ProductoDto producto : todosProductos) {
                            productosPorCategoria
                                    .computeIfAbsent(producto.getIdCategoria(), k -> new ArrayList<>())
                                    .add(producto);
                        }
                        
                        // Inicializar movimientos en cero para cada producto
                        for (ProductoDto producto : todosProductos) {
                            if (!movimientosProducto.containsKey(producto.getIdProducto())) {
                                // [entrada, salida, baja, cortesia]
                                movimientosProducto.put(producto.getIdProducto(), new int[]{0, 0, 0, 0});
                            }
                        }
                        
                        Platform.runLater(this::construirVistaInventario);
                    }
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar productos para inventario", error);
                    Platform.runLater(() -> mostrarMensajeError(IdiomaUtil.obtener("ctrl.inventario.error_productos")));
                    return null;
                });
    }
    
    // ==================== CONSTRUCCIÓN DE LA VISTA ====================
    
    /**
     * Construye la vista completa del inventario con secciones por categoría.
     * Cada sección contiene un header con el nombre de la categoría y una tabla
     * de productos con las columnas de inventario.
     */
    private void construirVistaInventario() {
        contenedorInventario.getChildren().clear();
        
        String filtroCategoria = comboFiltroCategoria.getValue();
        String filtroBusqueda = campoBusqueda.getText();
        
        boolean algunaSeccionVisible = false;
        
        for (CategoriaDto categoria : categorias) {
            // Filtrar por categoría si hay filtro activo
            if (filtroCategoria != null && !IdiomaUtil.obtener("ctrl.inventario.filtro.todas").equals(filtroCategoria) 
                    && !categoria.getNombre().equals(filtroCategoria)) {
                continue;
            }
            
            List<ProductoDto> productos = productosPorCategoria.getOrDefault(
                    categoria.getIdCategoria(), new ArrayList<>());
            
            // Filtrar por búsqueda si hay texto
            if (filtroBusqueda != null && !filtroBusqueda.isEmpty()) {
                String filtroLower = filtroBusqueda.toLowerCase();
                productos = productos.stream()
                        .filter(p -> p.getNombre().toLowerCase().contains(filtroLower) 
                                || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(filtroLower)))
                        .collect(Collectors.toList());
            }
            
            // Solo mostrar categoría si tiene productos (o no hay filtro de búsqueda)
            if (!productos.isEmpty() || (filtroBusqueda == null || filtroBusqueda.isEmpty())) {
                VBox seccionCategoria = crearSeccionCategoria(categoria, productos);
                contenedorInventario.getChildren().add(seccionCategoria);
                algunaSeccionVisible = true;
            }
        }
        
        if (!algunaSeccionVisible) {
            mostrarMensajeVacio(IdiomaUtil.obtener("ctrl.inventario.vacio_filtros"));
        }
    }
    
    /**
     * Crea una sección de inventario para una categoría específica.
     * Incluye un header con el nombre y contador de productos, y una tabla
     * con las columnas de inventario.
     * 
     * @param categoria Categoría a mostrar
     * @param productos Lista de productos de esta categoría
     * @return VBox con la sección completa
     */
    private VBox crearSeccionCategoria(CategoriaDto categoria, List<ProductoDto> productos) {
        VBox seccion = new VBox(0);
        seccion.getStyleClass().add("inventario-seccion");
        seccion.setMaxWidth(ANCHO_MAX_SECCION);
        seccion.setAlignment(Pos.TOP_CENTER);
        
        // Header de categoría
        HBox header = crearHeaderCategoria(categoria, productos.size());
        seccion.getChildren().add(header);
        
        if (!productos.isEmpty()) {
            // Header de columnas de la tabla
            HBox headerColumnas = crearHeaderColumnas();
            seccion.getChildren().add(headerColumnas);
            
            // Filas de productos
            for (int i = 0; i < productos.size(); i++) {
                HBox fila = crearFilaProducto(productos.get(i), i % 2 == 0);
                seccion.getChildren().add(fila);
            }
        } else {
            // Mensaje de categoría vacía
            Label vacio = new Label(IdiomaUtil.obtener("ctrl.inventario.vacio_categoria"));
            vacio.setStyle("-fx-text-fill: #888; -fx-font-style: italic; -fx-padding: 16px 20px;");
            seccion.getChildren().add(vacio);
        }
        
        return seccion;
    }
    
    /**
     * Crea el header de una sección de categoría con nombre y contador.
     * 
     * @param categoria Categoría
     * @param cantidadProductos Cantidad de productos en la categoría
     * @return HBox con el header de categoría
     */
    private HBox crearHeaderCategoria(CategoriaDto categoria, int cantidadProductos) {
        HBox header = new HBox(16);
        header.getStyleClass().add("inventario-categoria-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        
        // Nombre de categoría
        Label nombre = new Label(categoria.getNombre());
        nombre.getStyleClass().add("inventario-categoria-nombre");
        HBox.setHgrow(nombre, Priority.ALWAYS);
        
        // Contador de productos
        Label contador = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.inventario.producto_count"), cantidadProductos, (cantidadProductos != 1 ? "s" : "")));
        contador.getStyleClass().add("inventario-categoria-contador");
        
        header.getChildren().addAll(nombre, contador);
        return header;
    }
    
    /**
     * Crea el header de columnas de la tabla de inventario.
     * Columnas: Código | Producto | Cant. Inicial | Entrada | Salida | Baja | Cortesía | Inv. Actual | Opciones
     * Usa anchos fijos constantes para garantizar alineación con las filas.
     * 
     * @return HBox con las columnas del header
     */
    private HBox crearHeaderColumnas() {
        HBox header = new HBox(0);
        header.getStyleClass().add("inventario-tabla-header");
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(10, 8, 10, 8));
        
        // Columnas con anchos fijos (constantes de clase)
        Label colCodigo = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.codigo"), ANCHO_CODIGO);
        Label colProducto = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.producto"), ANCHO_PRODUCTO);
        colProducto.setAlignment(Pos.CENTER_LEFT);
        Label colInicial = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.inicial"), ANCHO_INICIAL);
        Label colEntrada = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.entrada"), ANCHO_ENTRADA);
        Label colSalida = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.salida"), ANCHO_SALIDA);
        Label colBaja = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.baja"), ANCHO_BAJA);
        Label colCortesia = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.cortesia"), ANCHO_CORTESIA);
        Label colActual = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.actual"), ANCHO_ACTUAL);
        Label colOpciones = crearLabelColumna(IdiomaUtil.obtener("ctrl.inventario.col.opciones"), ANCHO_OPCIONES);
        
        header.getChildren().addAll(colCodigo, colProducto, colInicial, colEntrada, colSalida, 
                                     colBaja, colCortesia, colActual, colOpciones);
        return header;
    }
    
    /**
     * Crea un label de columna para el header de la tabla.
     * Usa ancho fijo para garantizar alineación con las celdas de datos.
     * 
     * @param texto Texto de la columna
     * @param anchoPref Ancho fijo
     * @return Label configurado
     */
    private Label crearLabelColumna(String texto, double anchoPref) {
        Label label = new Label(texto);
        label.getStyleClass().add("inventario-columna-header");
        label.setPrefWidth(anchoPref);
        label.setMinWidth(anchoPref);
        label.setMaxWidth(anchoPref);
        label.setAlignment(Pos.CENTER);
        return label;
    }
    
    /**
     * Crea una fila de producto en la tabla de inventario.
     * Muestra todos los valores de las columnas y los botones de acción.
     * 
     * @param producto Producto a mostrar
     * @param par Si la fila es par (para alternar colores)
     * @return HBox con la fila del producto
     */
    private HBox crearFilaProducto(ProductoDto producto, boolean par) {
        HBox fila = new HBox(0);
        fila.getStyleClass().add("inventario-fila");
        if (par) {
            fila.getStyleClass().add("inventario-fila-par");
        }
        fila.setAlignment(Pos.CENTER);
        fila.setPadding(new Insets(8, 8, 8, 8));
        
        // Obtener movimientos del producto (o inicializar en cero)
        int[] movimientos = movimientosProducto.getOrDefault(
                producto.getIdProducto(), new int[]{0, 0, 0, 0});
        
        // Calcular valores
        int cantidadInicial = producto.getStockActual() != null ? producto.getStockActual() : 0;
        int entrada = movimientos[0];
        int salida = movimientos[1];
        int baja = movimientos[2];
        int cortesia = movimientos[3];
        int inventarioActual = cantidadInicial + entrada - salida - baja - cortesia;
        
        // Columna: Código (3 dígitos)
        String codigoTexto = producto.getCodigo() != null ? producto.getCodigo() : "---";
        Label colCodigo = crearCeldaValor(codigoTexto, ANCHO_CODIGO);
        colCodigo.getStyleClass().add("inventario-celda-codigo");
        
        // Columna: Producto (solo nombre)
        Label colProducto = crearCeldaValor(producto.getNombre(), ANCHO_PRODUCTO);
        colProducto.getStyleClass().add("inventario-celda-producto");
        colProducto.setAlignment(Pos.CENTER_LEFT);
        
        // Columna: Cantidad Inicial
        Label colInicial = crearCeldaValor(String.valueOf(cantidadInicial), ANCHO_INICIAL);
        colInicial.getStyleClass().add("inventario-celda-inicial");
        
        // Columna: Entrada (verde)
        Label colEntrada = crearCeldaValor(String.valueOf(entrada), ANCHO_ENTRADA);
        colEntrada.getStyleClass().add("inventario-celda-entrada");
        
        // Columna: Salida (naranja)
        Label colSalida = crearCeldaValor(String.valueOf(salida), ANCHO_SALIDA);
        colSalida.getStyleClass().add("inventario-celda-salida");
        
        // Columna: Baja (rojo)
        Label colBaja = crearCeldaValor(String.valueOf(baja), ANCHO_BAJA);
        colBaja.getStyleClass().add("inventario-celda-baja");
        
        // Columna: Cortesía (morado)
        Label colCortesia = crearCeldaValor(String.valueOf(cortesia), ANCHO_CORTESIA);
        colCortesia.getStyleClass().add("inventario-celda-cortesia");
        
        // Columna: Inventario Actual (resaltado)
        Label colActual = crearCeldaValor(String.valueOf(inventarioActual), ANCHO_ACTUAL);
        colActual.getStyleClass().add("inventario-celda-actual");
        // Resaltar en rojo si está debajo del stock mínimo
        if (producto.getStockMinimo() != null && inventarioActual <= producto.getStockMinimo()) {
            colActual.getStyleClass().add("inventario-stock-bajo");
        }
        
        // Columna: Opciones
        HBox colOpciones = crearBotonesAccion(producto);
        colOpciones.setPrefWidth(ANCHO_OPCIONES);
        colOpciones.setMinWidth(ANCHO_OPCIONES);
        colOpciones.setMaxWidth(ANCHO_OPCIONES);
        
        fila.getChildren().addAll(colCodigo, colProducto, colInicial, colEntrada, colSalida, 
                                   colBaja, colCortesia, colActual, colOpciones);
        return fila;
    }
    
    /**
     * Crea una celda de valor numérico para la tabla.
     * 
     * @param valor Valor a mostrar
     * @param ancho Ancho de la celda
     * @return Label configurado como celda
     */
    private Label crearCeldaValor(String valor, double ancho) {
        Label label = new Label(valor);
        label.getStyleClass().add("inventario-celda");
        label.setPrefWidth(ancho);
        label.setMinWidth(ancho);
        label.setMaxWidth(ancho);
        label.setAlignment(Pos.CENTER);
        return label;
    }
    
    /**
     * Crea los botones de acción para un producto en la tabla.
     * Incluye botón de ajuste de stock.
     * 
     * @param producto Producto asociado
     * @return HBox con los botones
     */
    private HBox crearBotonesAccion(ProductoDto producto) {
        HBox botones = new HBox(6);
        botones.setAlignment(Pos.CENTER);
        
        // Botón ajustar stock con icono SVG de engranaje
        Button btnAjustar = new Button();
        Region iconoEngranaje = new Region();
        iconoEngranaje.getStyleClass().add("icono-cfg-header");
        iconoEngranaje.setMinSize(14, 14);
        iconoEngranaje.setPrefSize(14, 14);
        iconoEngranaje.setMaxSize(14, 14);
        btnAjustar.setGraphic(iconoEngranaje);
        btnAjustar.getStyleClass().add("inventario-boton-ajustar");
        btnAjustar.setTooltip(new Tooltip("Ajustar movimientos de stock"));
        btnAjustar.setOnAction(e -> mostrarModalAjusteStock(producto));
        
        botones.getChildren().add(btnAjustar);
        return botones;
    }
    
    // ==================== ACCIONES ====================
    
    /**
     * Acción: Buscar producto por nombre o código.
     * Filtra la vista de inventario según el texto ingresado.
     */
    @FXML
    private void buscarProducto() {
        String texto = campoBusqueda.getText();
        filtrarProductos(texto);
    }
    
    /**
     * Acción: Actualizar todos los datos del inventario desde el servidor.
     */
    @FXML
    private void actualizarInventario() {
        logger.info("Actualizando inventario desde el servidor");
        cargarInventarioCompleto();
    }
    
    /**
     * Filtra los productos visibles según el texto de búsqueda.
     * 
     * @param texto Texto de búsqueda
     */
    private void filtrarProductos(String texto) {
        construirVistaInventario();
    }
    
    /**
     * Filtra la vista por categoría seleccionada en el combo.
     */
    private void filtrarPorCategoria() {
        construirVistaInventario();
    }
    
    // ==================== MODAL DE AJUSTE DE STOCK ====================
    
    /**
     * Muestra el modal de ajuste de stock para un producto específico.
     * Permite registrar movimientos de entrada, salida, baja y cortesía.
     * 
     * @param producto Producto al que se ajusta el stock
     */
    private void mostrarModalAjusteStock(ProductoDto producto) {
        StackPane modalRoot = crearContenedorModalLuxury(520, 550);
        
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));
        contenido.setAlignment(Pos.TOP_CENTER);
        
        // Título
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.inventario.ajuste_titulo"));
        titulo.getStyleClass().add("modal-titulo-lg");
        titulo.setStyle("-fx-text-fill: #d4af37;");
        
        // Nombre del producto
        Label nombreProducto = new Label(producto.getNombre());
        nombreProducto.getStyleClass().add("tutorial-seccion-titulo");
        
        // Stock actual
        Label stockInfo = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.inventario.stock_actual"), producto.getStockActual()));
        stockInfo.getStyleClass().add("modal-mensaje");
        stockInfo.setStyle("-fx-text-fill: #b0b0b0;");
        
        // Obtener movimientos actuales
        int[] movimientos = movimientosProducto.getOrDefault(
                producto.getIdProducto(), new int[]{0, 0, 0, 0});
        
        // Campos de movimiento
        VBox camposMovimiento = new VBox(16);
        camposMovimiento.setPadding(new Insets(16, 0, 0, 0));
        
        // Entrada
        HBox filaEntrada = crearCampoMovimiento(IdiomaUtil.obtener("ctrl.inventario.entrada"), movimientos[0], "#a8b991");
        Spinner<Integer> spinnerEntrada = (Spinner<Integer>) filaEntrada.getChildren().get(1);
        
        // Salida
        HBox filaSalida = crearCampoMovimiento(IdiomaUtil.obtener("ctrl.inventario.salida"), movimientos[1], "#daa520");
        Spinner<Integer> spinnerSalida = (Spinner<Integer>) filaSalida.getChildren().get(1);
        
        // Baja
        HBox filaBaja = crearCampoMovimiento(IdiomaUtil.obtener("ctrl.inventario.baja"), movimientos[2], "#8b0000");
        Spinner<Integer> spinnerBaja = (Spinner<Integer>) filaBaja.getChildren().get(1);
        
        // Cortesía
        HBox filaCortesia = crearCampoMovimiento(IdiomaUtil.obtener("ctrl.inventario.cortesia"), movimientos[3], "#9b59b6");
        Spinner<Integer> spinnerCortesia = (Spinner<Integer>) filaCortesia.getChildren().get(1);
        
        camposMovimiento.getChildren().addAll(filaEntrada, filaSalida, filaBaja, filaCortesia);
        
        // Preview del inventario resultante
        int stockBase = producto.getStockActual() != null ? producto.getStockActual() : 0;
        Label previewLabel = new Label();
        previewLabel.getStyleClass().add("tutorial-titulo");
        previewLabel.setStyle("-fx-font-weight: bold;");
        actualizarPreview(previewLabel, stockBase, spinnerEntrada, spinnerSalida, spinnerBaja, spinnerCortesia);
        
        // Listeners para actualizar preview en tiempo real
        spinnerEntrada.valueProperty().addListener((obs, v, n) -> 
                actualizarPreview(previewLabel, stockBase, spinnerEntrada, spinnerSalida, spinnerBaja, spinnerCortesia));
        spinnerSalida.valueProperty().addListener((obs, v, n) -> 
                actualizarPreview(previewLabel, stockBase, spinnerEntrada, spinnerSalida, spinnerBaja, spinnerCortesia));
        spinnerBaja.valueProperty().addListener((obs, v, n) -> 
                actualizarPreview(previewLabel, stockBase, spinnerEntrada, spinnerSalida, spinnerBaja, spinnerCortesia));
        spinnerCortesia.valueProperty().addListener((obs, v, n) -> 
                actualizarPreview(previewLabel, stockBase, spinnerEntrada, spinnerSalida, spinnerBaja, spinnerCortesia));
        
        // Botones
        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);
        botones.setPadding(new Insets(10, 0, 0, 0));
        
        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setPrefHeight(44);
        btnCancelar.setPrefWidth(130);
        btnCancelar.setOnAction(e -> cerrarOverlay());
        
        Button btnGuardar = new Button(IdiomaUtil.obtener("ctrl.btn.guardar"));
        btnGuardar.getStyleClass().add("btn-confirmar-luxury");
        btnGuardar.setPrefHeight(44);
        btnGuardar.setPrefWidth(130);
        btnGuardar.setOnAction(e -> {
            // Guardar movimientos
            int[] nuevosMovimientos = new int[]{
                    spinnerEntrada.getValue(),
                    spinnerSalida.getValue(),
                    spinnerBaja.getValue(),
                    spinnerCortesia.getValue()
            };
            movimientosProducto.put(producto.getIdProducto(), nuevosMovimientos);
            
            // Calcular nuevo stock
            int nuevoStock = stockBase + nuevosMovimientos[0] - nuevosMovimientos[1] 
                           - nuevosMovimientos[2] - nuevosMovimientos[3];
            
            // Actualizar stock en el servidor
            producto.setStockActual(Math.max(0, nuevoStock));
            productoServicio.actualizarProductoAsync(producto,
                    actualizado -> {
                        Platform.runLater(() -> {
                            cerrarOverlay();
                            construirVistaInventario();
                            Stage stage = (Stage) contenedorInventario.getScene().getWindow();
                            NotificacionUtil.mostrarExito(stage, 
                                    "Stock actualizado: " + producto.getNombre() + " → " + nuevoStock + " unidades");
                        });
                    },
                    error -> {
                        Platform.runLater(() -> {
                            Stage stage = (Stage) contenedorInventario.getScene().getWindow();
                            NotificacionUtil.mostrarError(stage, 
                                    "Error al actualizar stock: " + error.getMessage());
                        });
                    });
        });
        
        botones.getChildren().addAll(btnCancelar, btnGuardar);
        
        contenido.getChildren().addAll(titulo, nombreProducto, stockInfo, camposMovimiento, 
                                        previewLabel, botones);
        modalRoot.getChildren().add(contenido);
        
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
    }
    
    /**
     * Crea un campo de movimiento con label y spinner para el modal de ajuste.
     * 
     * @param etiqueta Texto del label (ej: "Entrada (+)")
     * @param valorInicial Valor inicial del spinner
     * @param color Color de acento para el tipo de movimiento
     * @return HBox con el campo configurado
     */
    private HBox crearCampoMovimiento(String etiqueta, int valorInicial, String color) {
        HBox fila = new HBox(16);
        fila.setAlignment(Pos.CENTER_LEFT);
        
        Label label = new Label(etiqueta);
        label.getStyleClass().add("panel-seccion-titulo");
        label.setStyle("-fx-text-fill: " + color + "; " +
                       "-fx-min-width: 140px; -fx-pref-width: 140px;");
        
        Spinner<Integer> spinner = new Spinner<>(0, 99999, valorInicial);
        spinner.setEditable(true);
        spinner.setPrefWidth(160);
        spinner.setPrefHeight(40);
        spinner.getStyleClass().add("inventario-spinner");
        
        // Validar que solo se ingresen números
        spinner.getEditor().textProperty().addListener((obs, viejo, nuevo) -> {
            if (!nuevo.matches("\\d*")) {
                spinner.getEditor().setText(viejo);
            }
        });
        
        fila.getChildren().addAll(label, spinner);
        return fila;
    }
    
    /**
     * Actualiza el preview del inventario resultante en el modal.
     * 
     * @param previewLabel Label donde se muestra el resultado
     * @param stockBase Stock base (actual)
     * @param spinnerEntrada Spinner de entrada
     * @param spinnerSalida Spinner de salida
     * @param spinnerBaja Spinner de baja
     * @param spinnerCortesia Spinner de cortesía
     */
    private void actualizarPreview(Label previewLabel, int stockBase, 
                                    Spinner<Integer> spinnerEntrada, Spinner<Integer> spinnerSalida,
                                    Spinner<Integer> spinnerBaja, Spinner<Integer> spinnerCortesia) {
        int resultado = stockBase + spinnerEntrada.getValue() - spinnerSalida.getValue() 
                       - spinnerBaja.getValue() - spinnerCortesia.getValue();
        previewLabel.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.inventario.preview"), resultado));
        
        if (resultado < 0) {
            previewLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff4444;");
        } else if (resultado == 0) {
            previewLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #daa520;");
        } else {
            previewLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #a8b991;");
        }
    }
    
    // ==================== INDICADORES VISUALES ====================
    
    /**
     * Muestra un indicador de carga mientras se cargan los datos.
     */
    private void mostrarIndicadorCarga() {
        contenedorInventario.getChildren().clear();
        
        VBox indicador = new VBox(16);
        indicador.setAlignment(Pos.CENTER);
        indicador.setPadding(new Insets(60));
        
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);
        spinner.setStyle("-fx-progress-color: #d4af37;");
        
        Label texto = new Label(IdiomaUtil.obtener("ctrl.inventario.cargando"));
        texto.getStyleClass().add("modal-mensaje");
        texto.setStyle("-fx-text-fill: #b0b0b0;");
        
        indicador.getChildren().addAll(spinner, texto);
        contenedorInventario.getChildren().add(indicador);
    }
    
    /**
     * Muestra un mensaje cuando no hay datos para mostrar.
     * 
     * @param mensaje Mensaje a mostrar
     */
    private void mostrarMensajeVacio(String mensaje) {
        VBox contenedor = new VBox(12);
        contenedor.setAlignment(Pos.CENTER);
        contenedor.setPadding(new Insets(60));
        
        // Icono SVG de caja/inventario vacío
        Region icono = new Region();
        icono.getStyleClass().add("icono-inventario-vacio");
        icono.setMinSize(40, 40);
        icono.setPrefSize(40, 40);
        icono.setMaxSize(40, 40);
        
        Label texto = new Label(mensaje);
        texto.getStyleClass().add("tutorial-titulo");
        texto.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
        texto.setWrapText(true);
        
        contenedor.getChildren().addAll(icono, texto);
        contenedorInventario.getChildren().add(contenedor);
    }
    
    /**
     * Muestra un mensaje de error en el contenedor principal.
     * 
     * @param mensaje Mensaje de error
     */
    private void mostrarMensajeError(String mensaje) {
        VBox contenedor = new VBox(12);
        contenedor.setAlignment(Pos.CENTER);
        contenedor.setPadding(new Insets(60));
        
        Region icono = new Region();
        icono.getStyleClass().add("icono-inventario-error");
        icono.setPrefSize(48, 48);
        icono.setMinSize(48, 48);
        icono.setMaxSize(48, 48);
        
        Label texto = new Label(mensaje);
        texto.getStyleClass().add("tutorial-titulo");
        texto.setStyle("-fx-text-fill: #ff6b6b;");
        texto.setWrapText(true);
        
        Button btnReintentar = new Button(IdiomaUtil.obtener("ctrl.btn.reintentar"));
        btnReintentar.getStyleClass().add("boton-secundario");
        btnReintentar.setOnAction(e -> cargarInventarioCompleto());
        
        contenedor.getChildren().addAll(icono, texto, btnReintentar);
        contenedorInventario.getChildren().add(contenedor);
    }
    
    // ==================== SISTEMA DE MODALES ====================
    
    /**
     * Crea un contenedor modal con efecto cristal tintado negro.
     * Diseño limpio sin animaciones pesadas para rendimiento óptimo.
     * 
     * @param width Ancho máximo del modal
     * @param height Alto máximo del modal
     * @return StackPane configurado como contenedor modal
     */
    private StackPane crearContenedorModalLuxury(int width, int height) {
        StackPane modalRoot = new StackPane();
        modalRoot.setMaxWidth(width);
        modalRoot.setMaxHeight(height);
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
     * Muestra el overlay modal con transición fade-in.
     */
    private void mostrarOverlay() {
        contenedorModal.setVisible(true);
        contenedorModal.setManaged(true);
        
        // Activar brillo interactivo del borde dorado
        bordeModal = new BordeInteractivoModal();
        bordeModal.iniciar(currentModalRoot);
        
        contenedorModal.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), contenedorModal);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    /**
     * Cierra el overlay modal con transición fade-out.
     */
    private void cerrarOverlay() {
        if (bordeModal != null) {
            bordeModal.detener();
            bordeModal = null;
        }
        
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), contenedorModal);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            contenedorModal.setVisible(false);
            contenedorModal.setManaged(false);
            contenidoModalInterno.getChildren().clear();
            currentModalRoot = null;
        });
        fadeOut.play();
    }
}
