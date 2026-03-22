/* Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular. */
package com.baryx.cliente.controlador.facturacion;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.CategoriaDto;
import com.baryx.common.dto.MetodoPagoDto;
import com.baryx.common.dto.ProductoDto;
import com.baryx.cliente.componente.ToggleSwitch;
import com.baryx.cliente.modelo.LineaPedido;
import com.baryx.cliente.modelo.Mesa;
import com.baryx.cliente.servicio.CategoriaServicio;
import com.baryx.cliente.servicio.MetodoPagoServicio;
import com.baryx.cliente.servicio.ProductoServicio;
import com.baryx.cliente.servicio.VentaServicio;
import com.baryx.common.dto.RegistrarVentaDto;
import com.baryx.common.dto.PagoDto;
import com.baryx.common.dto.LineaVentaDto;
import com.baryx.common.dto.VentaDto;
import com.baryx.cliente.componente.BordeInteractivoModal;
import com.baryx.cliente.utilidad.NavegacionUtil;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador para la vista de detalle de mesa.
 * Carga categorías y productos reales desde la BD.
 */
public class MesaDetalleController {

    private static final Logger logger = LoggerFactory.getLogger(MesaDetalleController.class);

    @FXML
    private Label txtNumeroMesa;
    @FXML
    private Label txtMesero;
    @FXML
    private VBox listaPedido;
    @FXML
    private Label txtTotal;
    @FXML
    private GridPane gridCategorias;
    @FXML
    private GridPane gridProductos;
    @FXML
    private Label lblTituloProductos;
    @FXML
    private ToggleSwitch toggleVistaResumida;
    @FXML
    private StackPane contenedorModal;
    @FXML
    private StackPane contenidoModalInterno;
    @FXML
    private StackPane barraAcciones;
    @FXML
    private Separator separadorBarraAcciones;
    @FXML
    private Label lblTituloMetodoPagoFlotante;

    private Mesa mesa;
    private FacturacionController controladorPadre;

    // Order management
    private ObservableList<LineaPedido> lineasPedido = FXCollections.observableArrayList();
    private boolean vistaResumida = false;
    private BigDecimal totalPedido = BigDecimal.ZERO;

    private CategoriaServicio categoriaServicio;
    private ProductoServicio productoServicio;
    private com.baryx.cliente.servicio.MesaServicio mesaServicio;
    private MetodoPagoServicio metodoPagoServicio;
    private VentaServicio ventaServicio;
    private CategoriaDto categoriaSeleccionada;

    // Lista de métodos de pago cargados desde el servidor
    private List<MetodoPagoDto> metodosPagoCargados = new ArrayList<>();

    // Cache de todos los productos para búsqueda por código con teclado
    private List<ProductoDto> todosLosProductosCache = new ArrayList<>();
    private StringBuilder bufferCodigoTeclado = new StringBuilder();
    private PauseTransition temporizadorCodigo;

    // Modal system state
    private StackPane currentModalRoot;
    private BordeInteractivoModal bordeModal;

    // Debounce para guardado en backend
    // Evita enviar múltiples requests concurrentes al agregar productos rápidamente
    private PauseTransition debounceGuardado;
    private final AtomicBoolean guardadoEnProceso = new AtomicBoolean(false);
    private boolean guardadoPendiente = false;

    @FXML
    public void initialize() {
        logger.info("Inicializando MesaDetalleController");

        // Inicializar servicios con el token del usuario actual
        String token = NavegacionUtil.getUsuarioActual().getToken();
        categoriaServicio = new CategoriaServicio(token);
        productoServicio = new ProductoServicio(token);
        mesaServicio = new com.baryx.cliente.servicio.MesaServicio(token);
        metodoPagoServicio = new MetodoPagoServicio(token);
        ventaServicio = new VentaServicio(token);

        // Configurar listener del toggle
        if (toggleVistaResumida != null) {
            toggleVistaResumida.selectedProperty().addListener((obs, oldVal, newVal) -> {
                vistaResumida = newVal;
                renderizarPedido();
            });
        }

        // Cargar categorías y cache de productos
        cargarCategorias();
        cargarCacheProductos();
    }

    /**
     * Carga las categorías desde el servidor y las muestra en el grid.
     * Las categorías se muestran como cards grandes táctiles.
     */
    private void cargarCategorias() {
        logger.info("Cargando categorías desde el servidor");

        categoriaServicio.listarCategoriasAsync()
                .thenAccept(categorias -> {
                    Platform.runLater(() -> {
                        // Ordenar por campo 'orden'
                        categorias.sort((c1, c2) -> {
                            int o1 = c1.getOrden() != null ? c1.getOrden() : 999;
                            int o2 = c2.getOrden() != null ? c2.getOrden() : 999;
                            return Integer.compare(o1, o2);
                        });
                        mostrarCategorias(categorias);
                    });
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar categorías", error);
                    Platform.runLater(() -> {
                        if (gridCategorias != null && gridCategorias.getScene() != null) {
                            Stage stage = (Stage) gridCategorias.getScene().getWindow();
                            NotificacionUtil.mostrarError(stage,
                                    Constantes.Mensajes.ERROR_CARGAR_CATEGORIAS + ": " + error.getMessage());
                        }
                    });
                    return null;
                });
    }

    /**
     * Muestra las categorías en el grid como cards táctiles.
     * Grid de 2 columnas, filas dinámicas.
     * 
     * @param categorias Lista de categorías a mostrar
     */
    private void mostrarCategorias(List<CategoriaDto> categorias) {
        gridCategorias.getChildren().clear();

        if (categorias == null || categorias.isEmpty()) {
            logger.warn("No hay categorías disponibles");
            mostrarMensajeSinCategorias();
            return;
        }

        logger.info("Mostrando {} categorías", categorias.size());

        int columnas = 2; // 2 categorías por fila (layout 2xN)
        int fila = 0;
        int columna = 0;

        for (CategoriaDto categoria : categorias) {
            Button btnCategoria = crearCardCategoria(categoria);
            gridCategorias.add(btnCategoria, columna, fila);

            columna++;
            if (columna >= columnas) {
                columna = 0;
                fila++;
            }
        }

        // Preseleccionar automáticamente la primera categoría
        if (!categorias.isEmpty()) {
            CategoriaDto primeraCategoria = categorias.get(0);
            categoriaSeleccionada = primeraCategoria;
            logger.info("Preseleccionando primera categoría: {}", primeraCategoria.getNombre());
            cargarProductosDeCategoria(primeraCategoria);
        }
    }

    /**
     * Crea un card visual para una categoría.
     * El card es un botón grande táctil con estilo luxury.
     * 
     * @param categoria Categoría a representar
     * @return Button configurado como card
     */
    private Button crearCardCategoria(CategoriaDto categoria) {
        Button card = new Button();
        card.setText(categoria.getNombre().toUpperCase());
        card.getStyleClass().addAll("card-categoria-producto");
        card.setPrefWidth(220);
        card.setPrefHeight(100);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAlignment(Pos.CENTER);
        card.setWrapText(true);

        // Aplicar color de la categoría como tinte del cristal
        String colorHex = categoria.getColor();
        if (colorHex != null && !colorHex.isBlank() && !"#000000".equals(colorHex)) {
            int[] rgb = hexARgb(colorHex);
            String bgColor = String.format("rgba(%d, %d, %d, 0.20)", rgb[0], rgb[1], rgb[2]);
            String borderColor = String.format("rgba(%d, %d, %d, 0.30)", rgb[0], rgb[1], rgb[2]);
            card.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + borderColor + ";");
        }

        // Al hacer clic, cargar productos de esa categoría en el grid de abajo
        card.setOnAction(e -> {
            categoriaSeleccionada = categoria;
            cargarProductosDeCategoria(categoria);
        });

        return card;
    }

    /**
     * Carga los productos de una categoría específica.
     * Solo actualiza el grid de productos, las categorías permanecen visibles.
     * 
     * @param categoria Categoría seleccionada
     */
    private void cargarProductosDeCategoria(CategoriaDto categoria) {
        logger.info("Cargando productos de categoría: {}", categoria.getNombre());

        // Actualizar título de la sección de productos
        lblTituloProductos.setText(Constantes.Mensajes.LABEL_PRODUCTOS + categoria.getNombre().toUpperCase());
        lblTituloProductos.setStyle("-fx-font-size: 18px; -fx-text-fill: #d4af37; -fx-font-weight: 700;");

        productoServicio.listarPorCategoriaAsync(categoria.getIdCategoria())
                .thenAccept(productos -> {
                    Platform.runLater(() -> {
                        mostrarProductos(productos);
                    });
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar productos", error);
                    Platform.runLater(() -> {
                        Stage stage = (Stage) gridProductos.getScene().getWindow();
                        NotificacionUtil.mostrarError(stage,
                                Constantes.Mensajes.ERROR_CARGAR_PRODUCTOS + ": " + error.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Muestra los productos en el grid de productos (debajo de categorías).
     * Grid de 3 columnas, filas dinámicas.
     * 
     * @param productos Lista de productos
     */
    private void mostrarProductos(List<ProductoDto> productos) {
        gridProductos.getChildren().clear();

        if (productos == null || productos.isEmpty()) {
            logger.warn("No hay productos en la categoría seleccionada");
            mostrarMensajeSinProductos();
            return;
        }

        logger.info("Mostrando {} productos", productos.size());

        int columnas = 3; // 3 productos por fila
        int fila = 0;
        int columna = 0;

        for (ProductoDto producto : productos) {
            Button btnProducto = crearCardProducto(producto);
            gridProductos.add(btnProducto, columna, fila);

            columna++;
            if (columna >= columnas) {
                columna = 0;
                fila++;
            }
        }
    }

    /**
     * Crea un card visual para un producto.
     * 
     * @param producto Producto a representar
     * @return Button configurado como card
     */
    private Button crearCardProducto(ProductoDto producto) {
        Button card = new Button();
        card.getStyleClass().addAll("card-producto-menu");
        card.setPrefWidth(200);
        card.setPrefHeight(90);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);

        // Código de producto arriba, grande y dorado, centrado
        Label lblCodigo = new Label(producto.getCodigo() != null ? producto.getCodigo() : "");
        lblCodigo.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 700; -fx-font-family: 'Roboto';");
        lblCodigo.setMaxWidth(Double.MAX_VALUE);
        lblCodigo.setAlignment(Pos.CENTER);

        // Nombre del producto debajo del código, centrado
        Label lblNombre = new Label(producto.getNombre().toUpperCase());
        lblNombre.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 12px; -fx-font-weight: 600;");
        lblNombre.setMaxWidth(Double.MAX_VALUE);
        lblNombre.setAlignment(Pos.CENTER);
        lblNombre.setWrapText(true);

        // Precio centrado en el medio de la card
        Label lblPrecio = new Label(formatearPesosColombianos(producto.getPrecio()));
        lblPrecio.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 16px; -fx-font-weight: 700;");
        lblPrecio.setMaxWidth(Double.MAX_VALUE);
        lblPrecio.setAlignment(Pos.CENTER);
        VBox.setVgrow(lblPrecio, Priority.ALWAYS);

        VBox contenido = new VBox(1, lblCodigo, lblNombre, lblPrecio);
        contenido.setAlignment(Pos.CENTER);
        contenido.setMaxWidth(Double.MAX_VALUE);
        contenido.setMaxHeight(Double.MAX_VALUE);
        card.setGraphic(contenido);

        // Heredar color de la categoría seleccionada con mayor intensidad
        if (categoriaSeleccionada != null) {
            String colorHex = categoriaSeleccionada.getColor();
            if (colorHex != null && !colorHex.isBlank() && !"#000000".equals(colorHex)) {
                int[] rgb = hexARgb(colorHex);
                String bgColor = String.format("rgba(%d, %d, %d, 0.35)", rgb[0], rgb[1], rgb[2]);
                String borderColor = String.format("rgba(%d, %d, %d, 0.45)", rgb[0], rgb[1], rgb[2]);
                card.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + borderColor + ";");
            }
        }

        // Al hacer clic, agregar al pedido
        card.setOnAction(e -> agregarProductoAlPedido(producto));

        return card;
    }

    /**
     * Muestra mensaje cuando no hay categorías.
     */
    private void mostrarMensajeSinCategorias() {
        Label mensaje = new Label(Constantes.Mensajes.LABEL_NO_HAY_CATEGORIAS);
        mensaje.setStyle("-fx-font-size: 18px; -fx-text-fill: #b0b0b0;");
        gridCategorias.add(mensaje, 0, 0);
    }

    /**
     * Muestra mensaje cuando no hay productos en una categoría.
     */
    private void mostrarMensajeSinProductos() {
        Label mensaje = new Label(Constantes.Mensajes.LABEL_NO_HAY_PRODUCTOS);
        mensaje.setStyle("-fx-font-size: 16px; -fx-text-fill: #b0b0b0; -fx-font-style: italic;");
        gridProductos.add(mensaje, 0, 0);
    }

    public void setMesa(Mesa mesa) {
        this.mesa = mesa;
        if (mesa != null) {
            logger.info("Cargando datos de mesa: {}", mesa.getNombre());
            txtNumeroMesa.setText(Constantes.Mensajes.LABEL_TITULO_MESA + mesa.getNombre().toUpperCase());

            String nombreMesero = mesa.getMeseroNombre();
            if (nombreMesero == null || nombreMesero.isBlank()) {
                nombreMesero = IdiomaUtil.obtener("ctrl.facturacion.sin_mesero");
            }
            txtMesero.setText(Constantes.Mensajes.LABEL_MESERO + ": " + nombreMesero);

            // Cargar pedido existente si la mesa ya está guardada en BD
            if (mesa.getIdMesa() != null) {
                cargarPedidoExistente();
            } else {
                actualizarTotalPedido();
            }

            // Configurar atajo de teclado '-' para abrir panel de pago
            configurarAtajoTecladoPago();
        }
    }

    private void cargarPedidoExistente() {
        if (mesa.getIdMesa() == null)
            return;

        mesaServicio.obtenerMesaConPedidoAsync(mesa.getIdMesa())
                .thenAcceptAsync(mesaDto -> {
                    if (mesaDto != null && mesaDto.getPedido() != null) {

                        lineasPedido.clear();
                        for (com.baryx.common.dto.LineaPedidoDto lineaDto : mesaDto.getPedido().getLineas()) {
                            lineasPedido.add(new LineaPedido(
                                    lineaDto.getIdProducto(),
                                    lineaDto.getNombreProducto(),
                                    lineaDto.getPrecioUnitario(),
                                    lineaDto.getTimestamp()));
                        }

                        renderizarPedido();
                        actualizarTotalPedido();
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error cargando pedido existente", ex);
                    Platform.runLater(() -> {
                        if (txtNumeroMesa != null && txtNumeroMesa.getScene() != null) {
                            NotificacionUtil.mostrarError((Stage) txtNumeroMesa.getScene().getWindow(),
                                    "Error cargando pedido: " + ex.getCause().getMessage());
                        }
                    });
                    return null;
                });
    }

    public void setControladorPadre(FacturacionController controlador) {
        this.controladorPadre = controlador;
    }

    // ============================================
    // ORDER MANAGEMENT METHODS
    // ============================================

    /**
     * Agrega un producto al pedido y persiste los cambios.
     * Usa debounce de 300ms para evitar múltiples requests concurrentes
     * cuando el usuario agrega productos rápidamente.
     */
    private void agregarProductoAlPedido(ProductoDto producto) {
        LineaPedido linea = new LineaPedido(
                producto.getIdProducto(),
                producto.getNombre(),
                producto.getPrecio(),
                LocalDateTime.now());
        lineasPedido.add(linea);
        logger.info("Producto agregado localmente: {} - {}", producto.getNombre(),
                formatearPesosColombianos(producto.getPrecio()));

        renderizarPedido();
        actualizarTotalPedido();

        // Persistir en backend con debounce para evitar race conditions
        programarGuardadoEnBackend();
    }

    /**
     * Programa el guardado del pedido en backend con debounce.
     * Si hay un guardado en proceso, marca como pendiente para reintento.
     * Si no, espera 300ms desde el último producto agregado antes de enviar.
     * Esto evita enviar N requests concurrentes cuando se agregan N productos rápido.
     */
    private void programarGuardadoEnBackend() {
        // Si hay un guardado HTTP en curso, marcar que hay cambios pendientes
        // El callback del guardado actual disparará otro guardado al terminar
        if (guardadoEnProceso.get()) {
            guardadoPendiente = true;
            logger.debug("Guardado en proceso, marcando como pendiente");
            return;
        }

        // Cancelar timer anterior si existe (resetear el debounce)
        if (debounceGuardado != null) {
            debounceGuardado.stop();
        }

        // Crear nuevo timer de 300ms
        debounceGuardado = new PauseTransition(javafx.util.Duration.millis(300));
        debounceGuardado.setOnFinished(event -> guardarPedidoEnBackend());
        debounceGuardado.play();
    }

    /**
     * Guarda el estado actual del pedido en el backend.
     * Este método se invoca a través del debounce, nunca directamente desde agregarProducto.
     * Usa un flag atómico para evitar requests concurrentes y procesa cambios pendientes.
     */
    private void guardarPedidoEnBackend() {
        if (mesa == null)
            return;

        // Marcar que hay un guardado HTTP en curso
        guardadoEnProceso.set(true);
        guardadoPendiente = false;

        // Convertir lineas locales a DTOs (snapshot del estado actual)
        java.util.List<com.baryx.common.dto.LineaPedidoDto> lineasDto = new java.util.ArrayList<>();
        for (LineaPedido linea : lineasPedido) {
            lineasDto.add(new com.baryx.common.dto.LineaPedidoDto(
                    linea.getIdProducto(),
                    linea.getNombreProducto(),
                    linea.getPrecioUnitario(),
                    linea.getTimestamp()));
        }

        com.baryx.common.dto.PedidoDto pedidoDto = new com.baryx.common.dto.PedidoDto(
                lineasDto,
                totalPedido,
                LocalDateTime.now()
        );

        // Ejecutar en hilo de fondo para no bloquear UI
        mesaServicio.guardarPedidoAsync(
                mesa.getNombre(),  // numeroMesa
                mesa.getMeseroId(),  // idMesero
                pedidoDto)
                .thenAcceptAsync(resultado -> {
                    logger.info("Pedido guardado exitosamente en backend");
                    
                    // Actualizar ID de mesa si es nueva
                    if (mesa.getIdMesa() == null && resultado.getIdMesa() != null) {
                        mesa.setIdMesa(resultado.getIdMesa());
                        logger.info("Mesa creada en BD con ID: {}", resultado.getIdMesa());
                    }
                    
                    // Actualizar información del mesero si viene del servidor
                    if (resultado.getMesero() != null && txtMesero != null) {
                        txtMesero.setText(Constantes.Mensajes.LABEL_MESERO + ": " + resultado.getMesero().getNombre());
                    }
                    
                    // Liberar el flag y procesar cambios pendientes
                    guardadoEnProceso.set(false);
                    if (guardadoPendiente) {
                        logger.debug("Procesando guardado pendiente");
                        programarGuardadoEnBackend();
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error guardando pedido en backend", ex);
                    Platform.runLater(() -> {
                        // Liberar el flag incluso si hay error
                        guardadoEnProceso.set(false);
                        
                        if (txtNumeroMesa != null && txtNumeroMesa.getScene() != null) {
                            NotificacionUtil.mostrarError((Stage) txtNumeroMesa.getScene().getWindow(),
                                    "Error guardando pedido: " + ex.getCause().getMessage());
                        }
                        
                        // Reintentar cambios pendientes incluso tras error
                        if (guardadoPendiente) {
                            logger.debug("Reintentando guardado pendiente tras error");
                            programarGuardadoEnBackend();
                        }
                    });
                    return null;
                });
    }

    /**
     * Renderiza el pedido según el modo de vista activo.
     */
    private void renderizarPedido() {
        listaPedido.getChildren().clear();

        if (vistaResumida) {
            renderizarPedidoResumido();
        } else {
            renderizarPedidoDetallado();
        }
    }

    /**
     * Vista detallada: una línea por cada click en producto.
     * Cada fila es deslizable hacia la izquierda para revelar botón de eliminar.
     */
    private void renderizarPedidoDetallado() {
        int numero = 1;
        for (int i = 0; i < lineasPedido.size(); i++) {
            LineaPedido linea = lineasPedido.get(i);
            // Índice directo en la lista para eliminación por swipe
            final int indiceLinea = i;
            StackPane filaSwipeable = crearFilaProductoSwipeable(
                    numero++,
                    linea.getNombreProducto(),
                    1,
                    linea.getPrecioUnitario(),
                    () -> eliminarLineaPorSwipe(indiceLinea, null));
            listaPedido.getChildren().add(filaSwipeable);
        }
    }

    /**
     * Vista resumida: agrupa productos iguales y suma cantidades.
     * Cada fila es deslizable; al eliminar se remueven TODAS las líneas de ese producto.
     */
    private void renderizarPedidoResumido() {
        // Usar LinkedHashMap para mantener orden de primera aparición
        Map<Long, ItemResumen> resumen = new java.util.LinkedHashMap<>();

        for (LineaPedido linea : lineasPedido) {
            resumen.computeIfAbsent(
                    linea.getIdProducto(),
                    k -> new ItemResumen(linea.getIdProducto(), linea.getNombreProducto(), linea.getPrecioUnitario())).incrementar();
        }

        int numero = 1;
        for (ItemResumen item : resumen.values()) {
            BigDecimal precioTotal = item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad()));
            final Long idProducto = item.getIdProducto();
            StackPane filaSwipeable = crearFilaProductoSwipeable(
                    numero++,
                    item.getNombre(),
                    item.getCantidad(),
                    precioTotal,
                    () -> eliminarLineaPorSwipe(-1, idProducto));
            listaPedido.getChildren().add(filaSwipeable);
        }
    }

    // ==================== SWIPE-TO-DELETE ====================

    /**
     * Distancia mínima en píxeles que el usuario debe arrastrar hacia la izquierda
     * para considerar que se ha "revelado" la zona de eliminación.
     */
    private static final double SWIPE_UMBRAL = 60.0;

    /**
     * Distancia máxima de desplazamiento hacia la izquierda.
     * Equivale al ancho de la zona del botón de basura.
     */
    private static final double SWIPE_MAX = 70.0;

    /**
     * Crea una fila visual para un producto con soporte de swipe-to-delete.
     * 
     * Estructura en capas (StackPane):
     * - CAPA INFERIOR: Icono de basura posicionado a la derecha (siempre presente, oculto)
     * - CAPA SUPERIOR: Fila con datos del producto (fondo opaco que cubre el icono)
     * 
     * Al arrastrar la capa superior hacia la izquierda, se descubre el icono de basura
     * que estaba debajo. El icono no se mueve — solo la fila se desplaza.
     *
     * @param numero   Número de fila visible
     * @param nombre   Nombre del producto
     * @param cantidad Cantidad de unidades
     * @param precio   Precio total de la línea
     * @param onEliminar Acción a ejecutar al confirmar eliminación
     * @return StackPane con la fila deslizable
     */
    private StackPane crearFilaProductoSwipeable(int numero, String nombre, int cantidad,
                                                  BigDecimal precio, Runnable onEliminar) {

        // === CAPA INFERIOR: Icono de basura (estático, se revela al deslizar) ===
        // Posicionado a la derecha del wrapper, fondo totalmente transparente
        Region iconoBasura = new Region();
        iconoBasura.setPrefSize(24, 24);
        iconoBasura.setMinSize(24, 24);
        iconoBasura.setMaxSize(24, 24);
        iconoBasura.setStyle(
            "-fx-background-color: #ff6b6b;" +
            "-fx-shape: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z';" +
            "-fx-scale-shape: true;"
        );
        
        // Contenedor del icono — transparente, solo contiene el icono centrado
        StackPane zonaEliminar = new StackPane();
        zonaEliminar.setPrefWidth(SWIPE_MAX);
        zonaEliminar.setMinWidth(SWIPE_MAX);
        zonaEliminar.setMaxWidth(SWIPE_MAX);
        zonaEliminar.setStyle("-fx-background-color: transparent;");
        zonaEliminar.setAlignment(Pos.CENTER);
        zonaEliminar.getChildren().add(iconoBasura);
        zonaEliminar.setCursor(javafx.scene.Cursor.HAND);
        // Inicialmente invisible (el icono aún no se ha revelado)
        zonaEliminar.setOpacity(0);

        // Hover: icono se ilumina a blanco
        zonaEliminar.setOnMouseEntered(e -> {
            iconoBasura.setStyle(
                "-fx-background-color: #ff4444;" +
                "-fx-shape: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z';" +
                "-fx-scale-shape: true;" +
                "-fx-effect: dropshadow(gaussian, rgba(255, 68, 68, 0.6), 8, 0, 0, 0);"
            );
        });
        zonaEliminar.setOnMouseExited(e -> {
            iconoBasura.setStyle(
                "-fx-background-color: #ff6b6b;" +
                "-fx-shape: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z';" +
                "-fx-scale-shape: true;"
            );
        });

        // Click en icono ejecuta la eliminación
        zonaEliminar.setOnMouseClicked(e -> {
            e.consume();
            onEliminar.run();
        });

        // HBox para posicionar la zona de eliminación a la derecha
        HBox capaInferior = new HBox();
        capaInferior.setAlignment(Pos.CENTER_RIGHT);
        capaInferior.setPadding(new Insets(0, 2, 0, 0));
        capaInferior.setStyle("-fx-background-color: transparent;");
        capaInferior.getChildren().add(zonaEliminar);
        capaInferior.setPickOnBounds(false); // Solo clickeable donde hay contenido

        // === CAPA SUPERIOR: Fila con datos del producto (se desliza) ===
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle("-fx-background-color: transparent;");

        // Columna # - ancho fijo 35px
        Label lblNumero = new Label(String.valueOf(numero));
        lblNumero.setPrefWidth(35);
        lblNumero.setMinWidth(35);
        lblNumero.setMaxWidth(35);
        lblNumero.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 13px;");

        // Columna Producto - se expande
        Label lblNombre = new Label(nombre);
        HBox.setHgrow(lblNombre, javafx.scene.layout.Priority.ALWAYS);
        lblNombre.setMaxWidth(Double.MAX_VALUE);
        lblNombre.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 13px; -fx-text-overrun: ellipsis;");

        // Columna Cantidad - 50px centrado
        Label lblCantidad = new Label(String.valueOf(cantidad));
        lblCantidad.setPrefWidth(50);
        lblCantidad.setMinWidth(50);
        lblCantidad.setMaxWidth(50);
        lblCantidad.setAlignment(Pos.CENTER);
        lblCantidad.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");

        // Columna Precio - 90px derecha
        Label lblPrecio = new Label(formatearPesosColombianos(precio));
        lblPrecio.setPrefWidth(90);
        lblPrecio.setMinWidth(90);
        lblPrecio.setMaxWidth(90);
        lblPrecio.setAlignment(Pos.CENTER_RIGHT);
        lblPrecio.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");

        row.getChildren().addAll(lblNumero, lblNombre, lblCantidad, lblPrecio);

        // === WRAPPER PRINCIPAL (StackPane con capas) ===
        StackPane wrapper = new StackPane();
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.setStyle("-fx-background-color: transparent;");
        // Capa inferior (icono) primero, capa superior (fila) encima
        wrapper.getChildren().addAll(capaInferior, row);

        // === SISTEMA DE SWIPE/DRAG sobre la fila ===
        final double[] startX = {0};
        final double[] currentOffset = {0};
        final boolean[] dragging = {false};

        row.setOnMousePressed(e -> {
            startX[0] = e.getSceneX();
            currentOffset[0] = row.getTranslateX();
            dragging[0] = false;
            e.consume();
        });

        row.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - startX[0];
            dragging[0] = true;

            // Solo desplazamiento hacia la izquierda (negativo)
            double nuevoOffset = currentOffset[0] + deltaX;
            nuevoOffset = Math.max(-SWIPE_MAX, Math.min(0, nuevoOffset));

            row.setTranslateX(nuevoOffset);

            // Revelar gradualmente el icono de basura según el progreso del swipe
            double progreso = Math.abs(nuevoOffset) / SWIPE_MAX;
            zonaEliminar.setOpacity(progreso);
            e.consume();
        });

        row.setOnMouseReleased(e -> {
            if (!dragging[0]) return;

            double offset = row.getTranslateX();

            if (Math.abs(offset) >= SWIPE_UMBRAL) {
                // Swipe suficiente: mantener abierto, mostrar icono
                animarDesplazamientoFila(row, offset, -SWIPE_MAX, zonaEliminar, true);
            } else {
                // Swipe insuficiente: volver a posición original, ocultar icono
                animarDesplazamientoFila(row, offset, 0, zonaEliminar, false);
            }
            e.consume();
        });

        return wrapper;
    }

    /**
     * Anima suavemente el desplazamiento de la fila a la posición objetivo.
     * También anima la opacidad del icono de basura.
     * 
     * @param row          La fila del producto a animar
     * @param desde        Posición actual de translateX
     * @param hasta        Posición objetivo de translateX
     * @param zonaEliminar La zona del icono de basura para animar su opacidad
     * @param revelar      true = mostrar icono (opacidad 1), false = ocultar (opacidad 0)
     */
    private void animarDesplazamientoFila(HBox row, double desde, double hasta,
                                           StackPane zonaEliminar, boolean revelar) {
        // Animación de desplazamiento de la fila
        javafx.animation.TranslateTransition transicion = new javafx.animation.TranslateTransition(
            javafx.util.Duration.millis(200), row);
        transicion.setFromX(desde);
        transicion.setToX(hasta);
        transicion.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        transicion.setOnFinished(e -> {
            // Animación completada - no se requiere cambio de estilo
            // ya que la fila permanece transparente siempre
        });
        transicion.play();

        // Animación de opacidad del icono
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), zonaEliminar);
        fade.setToValue(revelar ? 1.0 : 0.0);
        fade.play();
    }

    /**
     * Elimina una línea del pedido activada por swipe-to-delete.
     * 
     * Lógica según rol:
     * - ADMIN / CAJERO: Eliminación inmediata sin confirmación
     * - MESERO: Bloqueado. Muestra mensaje indicando que la funcionalidad
     *           estará disponible próximamente. Resetea el swipe.
     * 
     * @param indiceLinea Índice directo en lineasPedido (modo detallado). -1 si se usa idProducto.
     * @param idProducto  ID del producto a eliminar todas sus ocurrencias (modo resumido). null si se usa índice.
     */
    private void eliminarLineaPorSwipe(int indiceLinea, Long idProducto) {
        // Verificar que hay líneas para eliminar
        if (lineasPedido.isEmpty()) return;

        // Si tras eliminar quedaría vacío, redirigir a confirmación de anular mesa
        boolean eliminariaUltima = false;
        if (indiceLinea >= 0 && lineasPedido.size() == 1) {
            eliminariaUltima = true;
        } else if (idProducto != null) {
            long restantes = lineasPedido.stream()
                .filter(l -> !l.getIdProducto().equals(idProducto)).count();
            if (restantes == 0) eliminariaUltima = true;
        }

        if (eliminariaUltima) {
            // Si es MESERO, no puede anular la mesa (la anulación solo es para ADMIN/CAJERO)
            AuthRespuestaDto usuarioAnular = NavegacionUtil.getUsuarioActual();
            String rolAnular = (usuarioAnular != null) ? usuarioAnular.getRol() : "";
            if (Constantes.Roles.MESERO.equals(rolAnular)) {
                Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                NotificacionUtil.mostrarAdvertencia(stage,
                        "No tienes permiso para anular mesas.\n" +
                        "Solicita a un cajero o administrador que realice esta acción.");
                resetearSwipeFilas();
                return;
            }
            mostrarConfirmacionAnularMesa();
            return;
        }

        // Determinar rol del usuario actual
        AuthRespuestaDto usuario = NavegacionUtil.getUsuarioActual();
        String rol = (usuario != null) ? usuario.getRol() : "";

        if (Constantes.Roles.MESERO.equals(rol)) {
            // MESERO: No tiene permiso para eliminar líneas del pedido.
            // Esta funcionalidad se habilitará en una versión futura con flujo de autorización.
            Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
            NotificacionUtil.mostrarAdvertencia(stage,
                    "No tienes permiso para eliminar líneas del pedido.\n" +
                    "Esta funcionalidad estará disponible próximamente para meseros.");
            // Resetear el swipe para cerrar la zona de basura abierta
            resetearSwipeFilas();
            return;
        } else {
            // ADMIN / CAJERO: Eliminación directa sin confirmación
            ejecutarEliminacionSwipe(indiceLinea, idProducto);
        }
    }

    /**
     * Ejecuta la eliminación efectiva de una línea o grupo de líneas del pedido.
     * Actualiza la vista y sincroniza con el backend.
     * 
     * @param indiceLinea Índice directo (modo detallado). -1 si se usa idProducto.
     * @param idProducto  ID del producto (modo resumido). null si se usa índice.
     */
    private void ejecutarEliminacionSwipe(int indiceLinea, Long idProducto) {
        if (indiceLinea >= 0 && indiceLinea < lineasPedido.size()) {
            // Modo detallado: eliminar línea específica por índice
            lineasPedido.remove(indiceLinea);
        } else if (idProducto != null) {
            // Modo resumido: eliminar TODAS las líneas de ese producto
            lineasPedido.removeIf(l -> l.getIdProducto().equals(idProducto));
        }

        renderizarPedido();
        actualizarTotalPedido();
        programarGuardadoEnBackend();

        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
        NotificacionUtil.mostrarExito(stage, "Línea eliminada");
    }

    /**
     * Muestra modal de confirmación para meseros antes de eliminar una línea.
     * Placeholder simplificado — será reemplazado por un flujo más complejo
     * en futuras versiones (ej: requerir autorización de un cajero/admin).
     * 
     * @param indiceLinea Índice de la línea (modo detallado). -1 si usa idProducto.
     * @param idProducto  ID del producto (modo resumido). null si usa índice.
     */
    private void mostrarConfirmacionEliminarSwipe(int indiceLinea, Long idProducto) {
        StackPane modalRoot = crearContenedorModalLuxury(450, 250);
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));
        contenido.setAlignment(Pos.CENTER);

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.confirmar.eliminacion"));
        titulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #d4af37;");

        Label mensaje = new Label(IdiomaUtil.obtener("ctrl.confirmar.eliminar_linea"));
        mensaje.setWrapText(true);
        mensaje.setStyle("-fx-font-size: 14px; -fx-text-fill: #f5f5f5; -fx-text-alignment: center;");

        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);

        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setOnAction(e -> {
            cerrarOverlay();
            // Resetear swipe de todas las filas
            resetearSwipeFilas();
        });

        Button btnEliminar = new Button(IdiomaUtil.obtener("ctrl.btn.eliminar"));
        btnEliminar.getStyleClass().add("btn-eliminar-luxury");
        btnEliminar.setOnAction(e -> {
            cerrarOverlay();
            ejecutarEliminacionSwipe(indiceLinea, idProducto);
        });

        botones.getChildren().addAll(btnCancelar, btnEliminar);
        contenido.getChildren().addAll(titulo, mensaje, botones);

        modalRoot.getChildren().add(contenido);
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
    }

    /**
     * Resetea el desplazamiento de todas las filas deslizables a posición original.
     * Se usa al cancelar una eliminación para cerrar las zonas de basura abiertas.
     * 
     * Estructura de cada fila: StackPane(wrapper) → [capaInferior(HBox), row(HBox)]
     * La row es el segundo hijo del wrapper.
     */
    private void resetearSwipeFilas() {
        for (javafx.scene.Node nodo : listaPedido.getChildren()) {
            if (nodo instanceof StackPane) {
                StackPane wrapper = (StackPane) nodo;
                // La fila (row) es el segundo hijo; la capa inferior es el primero
                if (wrapper.getChildren().size() >= 2 
                        && wrapper.getChildren().get(1) instanceof HBox) {
                    HBox row = (HBox) wrapper.getChildren().get(1);
                    if (row.getTranslateX() != 0) {
                        // Buscar la zona eliminar (primer hijo del primer HBox)
                        HBox capaInferior = (HBox) wrapper.getChildren().get(0);
                        StackPane zonaEliminar = (StackPane) capaInferior.getChildren().get(0);
                        animarDesplazamientoFila(row, row.getTranslateX(), 0, zonaEliminar, false);
                    }
                }
            }
        }
    }

    /**
     * Formatea un valor a pesos colombianos: $50.000
     */
    private String formatearPesosColombianos(BigDecimal valor) {
        NumberFormat format = NumberFormat.getInstance(Locale.of("es", "CO"));
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        format.setGroupingUsed(true);
        return "$" + format.format(valor);
    }

    /** Convierte un color hexadecimal (#RRGGBB) a array [r, g, b]. */
    private int[] hexARgb(String hex) {
        String limpio = hex.startsWith("#") ? hex.substring(1) : hex;
        return new int[]{
                Integer.parseInt(limpio.substring(0, 2), 16),
                Integer.parseInt(limpio.substring(2, 4), 16),
                Integer.parseInt(limpio.substring(4, 6), 16)
        };
    }

    /**
     * Actualiza el total del pedido.
     */
    private void actualizarTotalPedido() {
        totalPedido = lineasPedido.stream()
                .map(LineaPedido::getPrecioUnitario)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        txtTotal.setText(formatearPesosColombianos(totalPedido));
    }

    /**
     * Clase interna para resumir items del pedido.
     */
    private static class ItemResumen {
        private final Long idProducto;
        private final String nombre;
        private final BigDecimal precioUnitario;
        private int cantidad = 0;

        ItemResumen(Long idProducto, String nombre, BigDecimal precioUnitario) {
            this.idProducto = idProducto;
            this.nombre = nombre;
            this.precioUnitario = precioUnitario;
        }

        void incrementar() {
            cantidad++;
        }

        Long getIdProducto() {
            return idProducto;
        }

        String getNombre() {
            return nombre;
        }

        BigDecimal getPrecioUnitario() {
            return precioUnitario;
        }

        int getCantidad() {
            return cantidad;
        }
    }

    @FXML
    private void volverAtras() {
        logger.info("MesaDetalleController.volverAtras() llamado");
        if (controladorPadre != null) {
            logger.info("Llamando a controladorPadre.volverDeDetalleMesa()");
            controladorPadre.volverDeDetalleMesa();
        } else {
            logger.error("controladorPadre es null, no se puede volver");
        }
    }

    @FXML
    private void generarFactura() {
        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, "Generar factura - En desarrollo");
    }

    @FXML
    private void aplicarDescuento() {
        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, "Aplicar descuento - En desarrollo");
    }

    @FXML
    private void buscarProducto() {
        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, "Buscar producto - En desarrollo");
    }

    @FXML
    private void asignarFormaPago() {
        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, "Forma de pago - En desarrollo");
    }

    @FXML
    private void anularCuenta() {
        // Los meseros no tienen permiso para anular mesas.
        // Solo ADMIN y CAJERO pueden ejecutar esta acción.
        AuthRespuestaDto usuario = NavegacionUtil.getUsuarioActual();
        if (usuario != null && Constantes.Roles.MESERO.equals(usuario.getRol())) {
            Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
            NotificacionUtil.mostrarAdvertencia(stage,
                    IdiomaUtil.obtener("ctrl.pago.no_permiso_anular"));
            return;
        }
        mostrarConfirmacionAnularMesa();
    }

    @FXML
    private void mostrarMasOpciones() {
        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, "Más opciones - En desarrollo");
    }

    public void configurarTeclaEsc() {
        // Unificado en configurarAtajoTecladoPago()
    }

    /**
     * Maneja ESC con navegación contextual entre paneles de pago.
     * @return true si ESC fue manejado internamente (panel2→panel1, panel1→categorías)
     */
    public boolean manejarEsc() {
        if (panelVentas != null && panelVentas.isVisible()) {
            logger.info("ESC: Ventas → Categorías");
            ocultarPanelVentas();
            return true;
        }
        if (panelOpciones != null && panelOpciones.isVisible()) {
            logger.info("ESC: Opciones → Categorías");
            ocultarPanelOpciones();
            return true;
        }
        if (panelPago != null && panelPago.isVisible()) {
            if (panelMetodosPagoDetalle != null && panelMetodosPagoDetalle.isVisible()) {
                logger.info("ESC: Panel 2 → Panel 1");
                volverAResumenSimple();
            } else {
                logger.info("ESC: Panel 1 → Categorías");
                volverACategorias();
            }
            return true;
        }
        return false;
    }

    // ==================== SISTEMA DE MODALES LUXURY ====================

    /**
     * Crea un contenedor modal con efecto cristal tintado negro.
     * Sin animaciones pesadas (FondoAnimado/Canvas/AnimationTimer)
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
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(250), contenedorModal);
        fadeIn.setToValue(1.0);
        fadeIn.play();
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
        
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), contenedorModal);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            contenedorModal.setVisible(false);
            contenedorModal.setManaged(false);
            contenidoModalInterno.getChildren().clear();
            currentModalRoot = null;
        });
        fadeOut.play();
    }

    /**
     * Muestra confirmación para anular mesa.
     */
    private void mostrarConfirmacionAnularMesa() {
        StackPane modalRoot = crearContenedorModalLuxury(500, 300);
        VBox contenido = new VBox(24);
        contenido.setPadding(new Insets(30));
        contenido.setAlignment(Pos.CENTER);

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.confirmar.anular_mesa"));
        titulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffaa00;");

        Label mensaje = new Label(IdiomaUtil.obtener("ctrl.confirmar.anular_mesa.msg"));
        mensaje.setWrapText(true);
        mensaje.setStyle("-fx-font-size: 14px; -fx-text-fill: #f5f5f5; -fx-text-alignment: center;");

        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);

        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setOnAction(e -> cerrarOverlay());

        Button btnAnular = new Button(IdiomaUtil.obtener("ctrl.btn.anular_mesa"));
        btnAnular.getStyleClass().add("btn-eliminar-luxury");
        btnAnular.setOnAction(e -> {
            cerrarOverlay();
            anularMesa();
        });

        botones.getChildren().addAll(btnCancelar, btnAnular);
        contenido.getChildren().addAll(titulo, mensaje, botones);

        // Agregar contenido al modal
        modalRoot.getChildren().add(contenido);

        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
    }

    /**
     * Anula la mesa (elimina todas las líneas y cierra la mesa).
     */
    private void anularMesa() {
        if (mesa == null || mesa.getIdMesa() == null) {
            logger.warn("No se puede anular mesa: ID es null");
            return;
        }

        // Limpiar líneas localmente
        lineasPedido.clear();
        renderizarPedido();
        actualizarTotalPedido();

        // Llamar al backend para anular la mesa
        mesaServicio.anularMesaAsync(mesa.getIdMesa())
                .thenRunAsync(() -> {
                    logger.info("Mesa anulada exitosamente");
                    if (txtNumeroMesa != null && txtNumeroMesa.getScene() != null) {
                        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                        NotificacionUtil.mostrarExito(stage, IdiomaUtil.obtener("ctrl.mesa.anulada"));
                    }
                    // Volver a la vista de mesas activas
                    volverAtras();
                }, Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error anulando mesa", ex);
                    Platform.runLater(() -> {
                        if (txtNumeroMesa != null && txtNumeroMesa.getScene() != null) {
                            Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                            NotificacionUtil.mostrarError(stage, "Error al anular mesa: " + ex.getCause().getMessage());
                        }
                    });
                    return null;
                });
    }

    // ============================================
    // PANEL DE PAGO - GESTIÓN DE COBRO Y PROPINAS
    // ============================================

    // Componentes del panel de pago (definidos en mesa-detalle.fxml)
    @FXML
    private StackPane contenedorPrincipal;
    @FXML
    private VBox panelCategoriasProductos;
    @FXML
    private StackPane panelPago;
   
    // Panel resumen simple (vista inicial)
    @FXML
    private VBox panelResumenSimple;
    @FXML
    private Label lblSubtotalResumen;
    @FXML
    private Label lblImpoconsumoResumen;
    @FXML
    private Label lblTotalSinPropinaResumen;
    @FXML
    private Label lblPropinaResumen;
    @FXML
    private Label lblTotalFinalResumen;
    @FXML
    private Button btnPorcentajePropina;
    
    // Variable para el porcentaje de propina seleccionado
    private double porcentajePropina = 0.10; // 10% por defecto
    
    // Panel métodos de pago detallado (vista con grid completo)
    @FXML
    private VBox panelMetodosPagoDetalle;
    @FXML
    private Label lblSubtotalPago;  // Subtotal sin impuestos
    @FXML
    private Label lblImpoconsumo;  // Impoconsumo 8%
    @FXML
    private Label lblTotalSinPropina;  // Total sin propina (Subtotal + Impoconsumo)
    @FXML
    private Label lblTotalPago;  // Total con propina incluida
    @FXML
    private Label lblPropinaTotalHeader;  // Muestra el total acumulado de propinas en el header
    @FXML
    private Label lblTextoPropinaHeader;  // Label del texto "PROPINA (X%)" en el header del grid
    @FXML
    private Button btnConfirmarPago;  // Botón de confirmar pago que muestra el faltante
    @FXML
    private HBox contenedorBotonesMetodosPago;  // Contenedor dinámico para botones de métodos de pago
    @FXML
    private GridPane gridResumenPagos;  // Grid unificado con resumen y pagos
    // btnEliminarUltimoPago removido - ahora cada fila tiene su botón borrar

    // Lista de botones de métodos de pago generados dinámicamente
    private List<Button> botonesMetodosPago = new ArrayList<>();

    // Estado del panel de pago
    private BigDecimal totalConImpuestos = BigDecimal.ZERO;  // Total actual de productos (CON impuestos incluidos)
    private BigDecimal subtotal = BigDecimal.ZERO;  // Subtotal sin impuestos (92.59% del total)
    private BigDecimal impoconsumo = BigDecimal.ZERO;  // Impoconsumo 8%
    private BigDecimal propinaActual = BigDecimal.ZERO;  // Total de propina acumulado
    private BigDecimal propinaMinima = BigDecimal.ZERO;  // Propina mínima del 10%
    private BigDecimal totalFinal = BigDecimal.ZERO;  // Total + propina
    private BigDecimal faltante = BigDecimal.ZERO;  // Lo que queda por pagar
    
    // Lista de pagos registrados (para pagos múltiples)
    private List<PagoRegistrado> pagosRegistrados = new ArrayList<>();
    
    /**
     * Clase interna para representar un pago registrado.
     */
    private static class PagoRegistrado {
        Long idMetodoPago;
        String metodoPago;
        BigDecimal monto;
        TextField campoMonto;
        TextField campoPropina;
        int filaGrid;
        
        PagoRegistrado(Long idMetodoPago, String metodoPago, BigDecimal monto, TextField campoMonto, TextField campoPropina, int filaGrid) {
            this.idMetodoPago = idMetodoPago;
            this.metodoPago = metodoPago;
            this.monto = monto;
            this.campoMonto = campoMonto;
            this.campoPropina = campoPropina;
            this.filaGrid = filaGrid;
        }
    }

    // ==================== F1: PANEL DE OPCIONES Y VENTAS ====================

    private StackPane panelOpciones;
    private StackPane panelVentas;
    private VBox contenedorProductosVenta;
    private VBox contenedorDetalleVenta;
    private VBox listaVentasContenedor;
    private List<VentaDto> ventasCargadas = new ArrayList<>();
    private HBox ventaSeleccionadaCard;

    /**
     * Muestra el panel de pago y oculta las categorías/productos.
     * Calcula el subtotal (sin impuestos), impoconsumo 8%, propina y faltante.
     * IMPORTANTE: Los productos ya tienen el impoconsumo 8% incluido en su precio.
     * También puede activarse con F2 del teclado.
     */
    @FXML
    private void mostrarPanelPago() {
        logger.info("Mostrando panel de pago");

        // Validar que hay líneas en el pedido
        if (lineasPedido.isEmpty()) {
            Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
            NotificacionUtil.mostrarAdvertencia(stage, IdiomaUtil.obtener("ctrl.pago.sin_productos"));
            return;
        }

        // Total actual (productos con impuestos incluidos)
        totalConImpuestos = calcularSubtotal();  // Este método suma los precios que YA tienen impuestos
        
        // Calcular subtotal sin impuestos (inverso del 8%)
        // Si precio con impuesto = 108, entonces sin impuesto = 100
        // Fórmula: subtotal = total / 1.08
        subtotal = totalConImpuestos.divide(new BigDecimal("1.08"), 0, RoundingMode.HALF_UP);
        
        // Calcular impoconsumo como la diferencia
        impoconsumo = totalConImpuestos.subtract(subtotal);

        // Calcular propina según el porcentaje seleccionado sobre el total con impuestos
        propinaMinima = totalConImpuestos.multiply(BigDecimal.valueOf(porcentajePropina)).setScale(0, RoundingMode.HALF_UP);
        
        // Inicialmente la propina acumulada es según el porcentaje seleccionado
        propinaActual = propinaMinima;
        
        // Calcular total final (total con impuestos + propina mínima)
        totalFinal = totalConImpuestos.add(propinaMinima);
        
        // Inicialmente, el faltante es el total final (nada pagado aún)
        faltante = totalFinal;

        // Actualizar labels del panel de pago detallado (grid)
        lblSubtotalPago.setText(formatearPesosColombianos(subtotal));
        lblImpoconsumo.setText(formatearPesosColombianos(impoconsumo));
        lblTotalSinPropina.setText(formatearPesosColombianos(totalConImpuestos));
        lblPropinaTotalHeader.setText(formatearPesosColombianos(propinaActual));
        lblTotalPago.setText(formatearPesosColombianos(totalFinal));
        
        // Actualizar botón de pagar y texto de propina en header del grid
        actualizarBotonPagar();
        actualizarTextoPropinaHeader();
        
        // Actualizar labels del resumen simple (vista inicial)
        lblSubtotalResumen.setText(formatearPesosColombianos(subtotal));
        lblImpoconsumoResumen.setText(formatearPesosColombianos(impoconsumo));
        lblTotalSinPropinaResumen.setText(formatearPesosColombianos(totalConImpuestos));
        lblPropinaResumen.setText(formatearPesosColombianos(propinaActual));
        lblTotalFinalResumen.setText(formatearPesosColombianos(totalFinal));
        
        // Setear el campo de texto de propina con el valor por defecto calculado
        // NOTA: El campo de propina ahora se crea dinámicamente en la primera fila de pago,
        // ya no existe en el header del grid
        // inputPropinaPersonalizada.setText(propinaActual.toString());

        // Limpiar pagos anteriores del GridPane (remover filas 1+)
        pagosRegistrados.clear();
        limpiarPagosDelGrid();
        limpiarSeleccionMetodoPago();

        // Alternar visibilidad de los paneles
        // Ocultar panel de categorías y productos
        panelCategoriasProductos.setVisible(false);
        panelCategoriasProductos.setManaged(false);
        
        // Mostrar panel de pago con el resumen simple visible primero
        panelPago.setVisible(true);
        panelPago.setManaged(true);
        panelResumenSimple.setVisible(true);
        panelResumenSimple.setManaged(true);
        panelMetodosPagoDetalle.setVisible(false);
        panelMetodosPagoDetalle.setManaged(false);

        // Ocultar barra de acciones inferior para dar más espacio
        barraAcciones.setVisible(false);
        barraAcciones.setManaged(false);
        separadorBarraAcciones.setVisible(false);
        separadorBarraAcciones.setManaged(false);

        // Cargar métodos de pago dinámicos desde el servidor
        cargarMetodosPagoDinamicos();

        logger.info("Panel de pago activo - Total con impuestos: {}, Subtotal: {}, Impoconsumo 8%: {}, Propina (10%): {}, Total final: {}, Faltante: {}",
                totalConImpuestos, subtotal, impoconsumo, propinaActual, totalFinal, faltante);
    }

    @FXML
    private void cerrarPanelActual() {
        if (panelVentas != null && panelVentas.isVisible()) {
            ocultarPanelVentas();
        } else if (panelOpciones != null && panelOpciones.isVisible()) {
            ocultarPanelOpciones();
        } else if (panelMetodosPagoDetalle != null && panelMetodosPagoDetalle.isVisible()) {
            volverAResumenSimple();
        } else {
            volverACategorias();
        }
    }

    /**
     * Muestra el panel detallado de métodos de pago cuando el usuario hace clic en "PROCEDER AL PAGO".
     * Oculta el resumen simple y muestra los botones de métodos de pago y el grid completo.
     * IMPORTANTE: Sincroniza todos los valores del panel 2 con los del panel 1,
     * ya que el usuario pudo haber cambiado el porcentaje de propina en el resumen simple.
     */
    @FXML
    private void irAPanelMetodosPago() {
        // Ocultar resumen simple
        panelResumenSimple.setVisible(false);
        panelResumenSimple.setManaged(false);
        
        // Mostrar panel detallado con métodos de pago y grid
        panelMetodosPagoDetalle.setVisible(true);
        panelMetodosPagoDetalle.setManaged(true);
        
        // Mostrar título flotante "Método de Pago"
        lblTituloMetodoPagoFlotante.setVisible(true);
        lblTituloMetodoPagoFlotante.setManaged(true);
        
        // Sincronizar TODOS los labels del panel 2 con los valores actuales
        // (el usuario pudo haber cambiado el % de propina en el panel 1)
        lblSubtotalPago.setText(formatearPesosColombianos(subtotal));
        lblImpoconsumo.setText(formatearPesosColombianos(impoconsumo));
        lblTotalSinPropina.setText(formatearPesosColombianos(totalConImpuestos));
        lblPropinaTotalHeader.setText(formatearPesosColombianos(propinaActual));
        lblTotalPago.setText(formatearPesosColombianos(totalFinal));
        
        // Actualizar texto del porcentaje de propina en el header del grid
        actualizarTextoPropinaHeader();
        
        // Recalcular faltante y actualizar estado del botón PAGAR
        recalcularTotalYFaltante();
        
        logger.info("Usuario accedió al panel detallado de métodos de pago - Propina actual: {}, Total final: {}, Faltante: {}", 
                propinaActual, totalFinal, faltante);
    }

    /**
     * Vuelve al resumen simple de pago desde el panel detallado de métodos de pago.
     * Útil si el usuario quiere revisar el resumen antes de proceder con el pago.
     */
    @FXML
    private void volverAResumenSimple() {
        // Ocultar panel detallado
        panelMetodosPagoDetalle.setVisible(false);
        panelMetodosPagoDetalle.setManaged(false);
        
        // Ocultar título flotante
        lblTituloMetodoPagoFlotante.setVisible(false);
        lblTituloMetodoPagoFlotante.setManaged(false);
        
        // Mostrar resumen simple
        panelResumenSimple.setVisible(true);
        panelResumenSimple.setManaged(true);
        
        logger.info("Usuario volvió al resumen simple de pago");
    }

    /**
     * Vuelve a la vista de categorías y productos, ocultando el panel de pago.
     */
    @FXML
    private void volverACategorias() {
        logger.info("Volviendo a vista de categorías/productos");

        panelPago.setVisible(false);
        panelPago.setManaged(false);
        panelCategoriasProductos.setVisible(true);
        panelCategoriasProductos.setManaged(true);

        // Restaurar barra de acciones inferior
        barraAcciones.setVisible(true);
        barraAcciones.setManaged(true);
        separadorBarraAcciones.setVisible(true);
        separadorBarraAcciones.setManaged(true);
    }

    /** Muestra el panel de opciones F1 (solo si no hay otro panel activo). */
    private void mostrarPanelOpciones() {
        if (panelPago != null && panelPago.isVisible()) return;
        if (panelVentas != null && panelVentas.isVisible()) return;
        if (panelOpciones != null && panelOpciones.isVisible()) {
            ocultarPanelOpciones();
            return;
        }

        if (panelOpciones == null) construirPanelOpciones();

        panelCategoriasProductos.setVisible(false);
        panelCategoriasProductos.setManaged(false);
        panelOpciones.setVisible(true);
        panelOpciones.setManaged(true);
        barraAcciones.setVisible(false);
        barraAcciones.setManaged(false);
        separadorBarraAcciones.setVisible(false);
        separadorBarraAcciones.setManaged(false);
        logger.info("Panel de opciones F1 abierto");
    }

    private void ocultarPanelOpciones() {
        if (panelOpciones != null) {
            panelOpciones.setVisible(false);
            panelOpciones.setManaged(false);
        }
        panelCategoriasProductos.setVisible(true);
        panelCategoriasProductos.setManaged(true);
        barraAcciones.setVisible(true);
        barraAcciones.setManaged(true);
        separadorBarraAcciones.setVisible(true);
        separadorBarraAcciones.setManaged(true);
    }

    private void construirPanelOpciones() {
        VBox cardOpciones = new VBox(20);
        cardOpciones.setAlignment(Pos.CENTER);
        cardOpciones.setMaxWidth(500);
        cardOpciones.setMaxHeight(400);
        cardOpciones.setStyle(
            "-fx-background-color: rgba(26, 26, 26, 0.98);" +
            "-fx-background-radius: 16px;" +
            "-fx-border-color: rgba(212, 175, 55, 0.3);" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 16px;" +
            "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 24, 0, 0, 0);"
        );
        cardOpciones.setPadding(new Insets(40, 50, 40, 50));

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.opciones.titulo"));
        titulo.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 24px; -fx-font-weight: 700;");

        Separator sep = new Separator();
        sep.setMaxWidth(200);

        String estiloBase = "-fx-background-color: linear-gradient(to bottom, #d4af37, #b8984e);" +
            "-fx-text-fill: #0a0a0a; -fx-font-size: 18px; -fx-font-weight: 700;" +
            "-fx-background-radius: 8px; -fx-cursor: hand;";
        String estiloHover = "-fx-background-color: linear-gradient(to bottom, #e0c04a, #c9a961);" +
            "-fx-text-fill: #0a0a0a; -fx-font-size: 18px; -fx-font-weight: 700;" +
            "-fx-background-radius: 8px; -fx-cursor: hand;";

        Button btnVentas = new Button(IdiomaUtil.obtener("ctrl.opciones.ventas"));
        btnVentas.setPrefWidth(400);
        btnVentas.setPrefHeight(60);
        btnVentas.setStyle(estiloBase);
        btnVentas.setOnMouseEntered(e -> btnVentas.setStyle(estiloHover));
        btnVentas.setOnMouseExited(e -> btnVentas.setStyle(estiloBase));
        btnVentas.setOnAction(e -> {
            panelOpciones.setVisible(false);
            panelOpciones.setManaged(false);
            mostrarPanelVentas();
        });

        cardOpciones.getChildren().addAll(titulo, sep, btnVentas);

        panelOpciones = new StackPane(cardOpciones);
        panelOpciones.setStyle("-fx-background-color: rgba(10, 10, 10, 0.85);");
        panelOpciones.setVisible(false);
        panelOpciones.setManaged(false);

        Button btnCerrar = crearBotonCerrarFlotante(() -> ocultarPanelOpciones());
        panelOpciones.getChildren().add(btnCerrar);
        contenedorPrincipal.getChildren().add(panelOpciones);
    }

    /** Muestra el panel de listado de ventas de la sesión. */
    private void mostrarPanelVentas() {
        if (panelVentas == null) construirPanelVentas();

        panelCategoriasProductos.setVisible(false);
        panelCategoriasProductos.setManaged(false);
        panelVentas.setVisible(true);
        panelVentas.setManaged(true);
        barraAcciones.setVisible(false);
        barraAcciones.setManaged(false);
        separadorBarraAcciones.setVisible(false);
        separadorBarraAcciones.setManaged(false);

        cargarVentasSesion();
        logger.info("Panel de ventas abierto");
    }

    private void ocultarPanelVentas() {
        if (panelVentas != null) {
            panelVentas.setVisible(false);
            panelVentas.setManaged(false);
        }
        panelCategoriasProductos.setVisible(true);
        panelCategoriasProductos.setManaged(true);
        barraAcciones.setVisible(true);
        barraAcciones.setManaged(true);
        separadorBarraAcciones.setVisible(true);
        separadorBarraAcciones.setManaged(true);
    }

    private void construirPanelVentas() {
        VBox principal = new VBox(0);
        principal.setStyle("-fx-background-color: #121212;");

        // Título
        HBox titleBar = new HBox(15);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(20, 24, 15, 24));
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.ventas.titulo"));
        titulo.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 22px; -fx-font-weight: 700;");
        titleBar.getChildren().add(titulo);

        Separator sepTitulo = new Separator();
        sepTitulo.setStyle("-fx-background-color: rgba(212, 175, 55, 0.3);");

        // Contenido central: izquierda (lista) + derecha (productos)
        HBox contenido = new HBox(0);
        VBox.setVgrow(contenido, Priority.ALWAYS);

        // Panel izquierdo: lista de ventas
        VBox panelIzq = new VBox(0);
        panelIzq.setPrefWidth(350);
        panelIzq.setMinWidth(300);
        panelIzq.setMaxWidth(400);
        panelIzq.setStyle("-fx-background-color: #1a1a1a;");

        HBox headerIzq = new HBox(0);
        headerIzq.setPadding(new Insets(10, 15, 10, 15));
        headerIzq.setAlignment(Pos.CENTER_LEFT);
        headerIzq.setStyle("-fx-background-color: #222222; -fx-border-color: transparent transparent rgba(212,175,55,0.3) transparent; -fx-border-width: 0 0 1 0;");

        Label colFactura = new Label(IdiomaUtil.obtener("ctrl.ventas.col.num_factura"));
        colFactura.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        colFactura.setPrefWidth(80);
        Label colHora = new Label(IdiomaUtil.obtener("ctrl.ventas.col.hora"));
        colHora.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        colHora.setPrefWidth(80);
        Label colTotal = new Label(IdiomaUtil.obtener("ctrl.ventas.col.total_pagado"));
        colTotal.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        colTotal.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(colTotal, Priority.ALWAYS);
        colTotal.setMaxWidth(Double.MAX_VALUE);
        headerIzq.getChildren().addAll(colFactura, colHora, colTotal);

        listaVentasContenedor = new VBox(2);
        listaVentasContenedor.setPadding(new Insets(5));
        ScrollPane scrollVentas = new ScrollPane(listaVentasContenedor);
        scrollVentas.setFitToWidth(true);
        scrollVentas.getStyleClass().add("scroll-productos-pedido");
        VBox.setVgrow(scrollVentas, Priority.ALWAYS);
        panelIzq.getChildren().addAll(headerIzq, scrollVentas);

        // Separador vertical
        Region separadorV = new Region();
        separadorV.setPrefWidth(1);
        separadorV.setMinWidth(1);
        separadorV.setMaxWidth(1);
        separadorV.setStyle("-fx-background-color: rgba(212, 175, 55, 0.3);");

        // Panel derecho: productos de la venta seleccionada
        VBox panelDer = new VBox(0);
        HBox.setHgrow(panelDer, Priority.ALWAYS);
        panelDer.setStyle("-fx-background-color: #121212;");

        Label tituloProd = new Label(IdiomaUtil.obtener("ctrl.ventas.productos_titulo"));
        tituloProd.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 16px; -fx-font-weight: 600;");
        tituloProd.setPadding(new Insets(10, 20, 10, 20));

        contenedorProductosVenta = new VBox(0);
        ScrollPane scrollProductos = new ScrollPane(contenedorProductosVenta);
        scrollProductos.setFitToWidth(true);
        scrollProductos.getStyleClass().add("scroll-productos-pedido");
        VBox.setVgrow(scrollProductos, Priority.ALWAYS);
        panelDer.getChildren().addAll(tituloProd, scrollProductos);

        contenido.getChildren().addAll(panelIzq, separadorV, panelDer);

        // Panel inferior: detalle de venta seleccionada
        contenedorDetalleVenta = new VBox(8);
        contenedorDetalleVenta.setPadding(new Insets(15, 24, 15, 24));
        contenedorDetalleVenta.setStyle(
            "-fx-background-color: #1a1a1a;" +
            "-fx-border-color: rgba(212, 175, 55, 0.3) transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );
        contenedorDetalleVenta.setVisible(false);
        contenedorDetalleVenta.setManaged(false);

        principal.getChildren().addAll(titleBar, sepTitulo, contenido, contenedorDetalleVenta);

        panelVentas = new StackPane(principal);
        panelVentas.setVisible(false);
        panelVentas.setManaged(false);

        Button btnCerrar = crearBotonCerrarFlotante(() -> ocultarPanelVentas());
        panelVentas.getChildren().add(btnCerrar);
        contenedorPrincipal.getChildren().add(panelVentas);
    }

    /** Crea un botón X flotante en la esquina superior derecha. */
    private Button crearBotonCerrarFlotante(Runnable accion) {
        Button btn = new Button("\u2715");
        String estiloNormal = "-fx-background-color: transparent; -fx-text-fill: #999999;" +
            "-fx-font-size: 22px; -fx-cursor: hand; -fx-padding: 5 10;";
        String estiloHover = "-fx-background-color: transparent; -fx-text-fill: #f5f5f5;" +
            "-fx-font-size: 22px; -fx-cursor: hand; -fx-padding: 5 10;";
        btn.setStyle(estiloNormal);
        btn.setOnMouseEntered(e -> btn.setStyle(estiloHover));
        btn.setOnMouseExited(e -> btn.setStyle(estiloNormal));
        btn.setOnAction(e -> accion.run());
        StackPane.setAlignment(btn, Pos.TOP_RIGHT);
        StackPane.setMargin(btn, new Insets(12, 16, 0, 0));
        return btn;
    }

    /** Carga las ventas del día actual desde el servidor. */
    private void cargarVentasSesion() {
        LocalDateTime hoy = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        ventaServicio.listarVentasDesdeAsync(hoy)
            .thenAccept(ventas -> Platform.runLater(() -> {
                ventasCargadas = ventas;
                renderizarListaVentas();
            }))
            .exceptionally(error -> {
                logger.error("Error al cargar ventas", error);
                Platform.runLater(() -> {
                    if (txtNumeroMesa.getScene() != null) {
                        Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                        NotificacionUtil.mostrarError(stage, IdiomaUtil.obtener("ctrl.ventas.error_cargar"));
                    }
                });
                return null;
            });
    }

    private void renderizarListaVentas() {
        listaVentasContenedor.getChildren().clear();
        contenedorProductosVenta.getChildren().clear();
        contenedorDetalleVenta.getChildren().clear();
        contenedorDetalleVenta.setVisible(false);
        contenedorDetalleVenta.setManaged(false);
        ventaSeleccionadaCard = null;

        if (ventasCargadas == null || ventasCargadas.isEmpty()) {
            Label sinVentas = new Label(IdiomaUtil.obtener("ctrl.ventas.sin_ventas"));
            sinVentas.setStyle("-fx-text-fill: #999999; -fx-font-size: 14px;");
            sinVentas.setPadding(new Insets(20));
            listaVentasContenedor.getChildren().add(sinVentas);
            return;
        }

        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm");
        for (VentaDto venta : ventasCargadas) {
            HBox fila = crearFilaVenta(venta, formatoHora);
            listaVentasContenedor.getChildren().add(fila);
        }
    }

    private HBox crearFilaVenta(VentaDto venta, DateTimeFormatter formatoHora) {
        HBox fila = new HBox(0);
        fila.setPadding(new Insets(12, 15, 12, 15));
        fila.setAlignment(Pos.CENTER_LEFT);
        String estiloNormal = "-fx-background-color: #1e1e1e; -fx-background-radius: 6px; -fx-cursor: hand;";
        String estiloHover = "-fx-background-color: #2a2a2a; -fx-background-radius: 6px; -fx-cursor: hand;";
        String estiloSeleccionado = "-fx-background-color: #2a2a2a; -fx-background-radius: 6px; -fx-cursor: hand;" +
            "-fx-border-color: rgba(212, 175, 55, 0.5); -fx-border-width: 0 0 0 3; -fx-border-radius: 6px;";
        fila.setStyle(estiloNormal);

        Label lblFactura = new Label("#" + venta.getIdVenta());
        lblFactura.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 14px; -fx-font-weight: 600;");
        lblFactura.setPrefWidth(80);

        String hora = venta.getFechaCreacion() != null
            ? venta.getFechaCreacion().format(formatoHora) : "--:--";
        Label lblHora = new Label(hora);
        lblHora.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 14px;");
        lblHora.setPrefWidth(80);

        Label lblTotal = new Label(formatearPesosColombianos(venta.getTotal()));
        lblTotal.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 14px; -fx-font-weight: 600;");
        lblTotal.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(lblTotal, Priority.ALWAYS);
        lblTotal.setMaxWidth(Double.MAX_VALUE);

        fila.getChildren().addAll(lblFactura, lblHora, lblTotal);

        fila.setOnMouseEntered(e -> {
            if (fila != ventaSeleccionadaCard) fila.setStyle(estiloHover);
        });
        fila.setOnMouseExited(e -> {
            if (fila != ventaSeleccionadaCard) fila.setStyle(estiloNormal);
        });
        fila.setOnMouseClicked(e -> {
            if (ventaSeleccionadaCard != null) {
                ventaSeleccionadaCard.setStyle(estiloNormal);
            }
            ventaSeleccionadaCard = fila;
            fila.setStyle(estiloSeleccionado);
            renderizarProductosVenta(venta);
            renderizarDetalleVenta(venta);
        });

        return fila;
    }

    private void renderizarProductosVenta(VentaDto venta) {
        contenedorProductosVenta.getChildren().clear();

        if (venta.getLineas() == null || venta.getLineas().isEmpty()) {
            Label sinProd = new Label(IdiomaUtil.obtener("ctrl.ventas.sin_productos"));
            sinProd.setStyle("-fx-text-fill: #999999; -fx-font-size: 14px;");
            sinProd.setPadding(new Insets(20));
            contenedorProductosVenta.getChildren().add(sinProd);
            return;
        }

        // Header de productos
        HBox header = new HBox(0);
        header.setPadding(new Insets(10, 20, 10, 20));
        header.setStyle("-fx-background-color: #222222; -fx-border-color: transparent transparent rgba(212,175,55,0.3) transparent; -fx-border-width: 0 0 1 0;");

        Label hProd = new Label(IdiomaUtil.obtener("ctrl.ventas.col.producto"));
        hProd.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        HBox.setHgrow(hProd, Priority.ALWAYS);
        hProd.setMaxWidth(Double.MAX_VALUE);
        Label hCant = new Label(IdiomaUtil.obtener("ctrl.ventas.col.cantidad"));
        hCant.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        hCant.setPrefWidth(60);
        hCant.setAlignment(Pos.CENTER);
        Label hPrecio = new Label(IdiomaUtil.obtener("ctrl.ventas.col.precio_unitario"));
        hPrecio.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        hPrecio.setPrefWidth(110);
        hPrecio.setAlignment(Pos.CENTER_RIGHT);
        Label hSub = new Label(IdiomaUtil.obtener("ctrl.ventas.col.subtotal_linea"));
        hSub.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 13px; -fx-font-weight: 600;");
        hSub.setPrefWidth(110);
        hSub.setAlignment(Pos.CENTER_RIGHT);
        header.getChildren().addAll(hProd, hCant, hPrecio, hSub);
        contenedorProductosVenta.getChildren().add(header);

        boolean alternar = false;
        for (LineaVentaDto linea : venta.getLineas()) {
            HBox row = new HBox(0);
            row.setPadding(new Insets(10, 20, 10, 20));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: " + (alternar ? "#1a1a1a" : "#161616") + ";");

            Label prod = new Label(linea.getNombreProducto());
            prod.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 14px;");
            HBox.setHgrow(prod, Priority.ALWAYS);
            prod.setMaxWidth(Double.MAX_VALUE);

            Label cant = new Label(String.valueOf(linea.getCantidad()));
            cant.setStyle("-fx-text-fill: #e8e8e8; -fx-font-size: 14px;");
            cant.setPrefWidth(60);
            cant.setAlignment(Pos.CENTER);

            Label precio = new Label(formatearPesosColombianos(linea.getPrecioUnitario()));
            precio.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 14px;");
            precio.setPrefWidth(110);
            precio.setAlignment(Pos.CENTER_RIGHT);

            BigDecimal subtotalLinea = linea.getPrecioUnitario().multiply(BigDecimal.valueOf(linea.getCantidad()));
            Label sub = new Label(formatearPesosColombianos(subtotalLinea));
            sub.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 14px; -fx-font-weight: 600;");
            sub.setPrefWidth(110);
            sub.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(prod, cant, precio, sub);
            contenedorProductosVenta.getChildren().add(row);
            alternar = !alternar;
        }
    }

    private void renderizarDetalleVenta(VentaDto venta) {
        contenedorDetalleVenta.getChildren().clear();
        contenedorDetalleVenta.setVisible(true);
        contenedorDetalleVenta.setManaged(true);

        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Fila 1: Mesa, Mesero, Hora de cierre, Cajero
        HBox fila1 = new HBox(30);
        fila1.setAlignment(Pos.CENTER_LEFT);
        fila1.getChildren().addAll(
            crearDetallePar(IdiomaUtil.obtener("ctrl.ventas.detalle.mesa"), venta.getNumeroMesa()),
            crearDetallePar(IdiomaUtil.obtener("ctrl.ventas.detalle.mesero"), venta.getNombreMesero()),
            crearDetallePar(IdiomaUtil.obtener("ctrl.ventas.detalle.hora_cierre"),
                venta.getFechaCreacion() != null ? venta.getFechaCreacion().format(formatoHora) : "--:--:--"),
            crearDetallePar(IdiomaUtil.obtener("ctrl.ventas.detalle.cajero"), venta.getNombreCajero())
        );

        // Fila 2: Valor sin propina, Propina, Total
        HBox fila2 = new HBox(30);
        fila2.setAlignment(Pos.CENTER_LEFT);
        BigDecimal propina = venta.getPropina() != null ? venta.getPropina() : BigDecimal.ZERO;
        BigDecimal valorSinPropina = venta.getTotal().subtract(propina);
        fila2.getChildren().addAll(
            crearDetallePar(IdiomaUtil.obtener("ctrl.ventas.detalle.valor_sin_propina"),
                formatearPesosColombianos(valorSinPropina)),
            crearDetallePar(IdiomaUtil.obtener("ctrl.ventas.detalle.propina"),
                formatearPesosColombianos(propina)),
            crearDetalleParDorado(IdiomaUtil.obtener("ctrl.ventas.detalle.total"),
                formatearPesosColombianos(venta.getTotal()))
        );

        contenedorDetalleVenta.getChildren().addAll(fila1, fila2);
    }

    private HBox crearDetallePar(String etiqueta, String valor) {
        HBox par = new HBox(8);
        par.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(etiqueta + ":");
        lbl.setStyle("-fx-text-fill: #999999; -fx-font-size: 13px;");
        Label val = new Label(valor != null ? valor : "-");
        val.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 13px; -fx-font-weight: 600;");
        par.getChildren().addAll(lbl, val);
        return par;
    }

    private HBox crearDetalleParDorado(String etiqueta, String valor) {
        HBox par = new HBox(8);
        par.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(etiqueta + ":");
        lbl.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 14px; -fx-font-weight: 600;");
        Label val = new Label(valor != null ? valor : "-");
        val.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 700;");
        par.getChildren().addAll(lbl, val);
        return par;
    }

    /**
     * Calcula el subtotal del pedido actual sumando todas las líneas.
     * @return Subtotal en BigDecimal
     */
    private BigDecimal calcularSubtotal() {
        return lineasPedido.stream()
                .map(LineaPedido::getPrecioUnitario)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Actualiza la propina en tiempo real cuando el usuario edita el campo de texto.
     * NOTA: El TextField de propina se crea dinámicamente en la primera fila de pagos (columna 3),
     * ya no está en el header del grid.
     * Se ejecuta con cada tecla presionada (onKeyReleased).
     * Recalcula el total final y el faltante automáticamente.
     * Si el valor ingresado es inválido, mantiene la propina actual.
     */
    @FXML
    private void actualizarPropinaDesdeInput() {
        // El TextField de propina ya no está en el header, se crea dinámicamente
        // Este método se vincula al campo cuando se crea el primer pago
        if (pagosRegistrados.isEmpty()) {
            return;
        }
        
        // Buscar el TextField de propina en la primera fila de pagos (columna 3)
        TextField campoPropina = null;
        for (javafx.scene.Node node : gridResumenPagos.getChildren()) {
            if (GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) == 1 &&
                GridPane.getColumnIndex(node) != null && GridPane.getColumnIndex(node) == 3 &&
                node instanceof VBox) {
                VBox celda = (VBox) node;
                if (!celda.getChildren().isEmpty() && celda.getChildren().get(0) instanceof TextField) {
                    campoPropina = (TextField) celda.getChildren().get(0);
                    break;
                }
            }
        }
        
        if (campoPropina == null) {
            return;
        }
        
        String textoInput = campoPropina.getText().trim();
        
        // Si el campo está vacío, setear propina a 0
        if (textoInput.isEmpty()) {
            propinaActual = BigDecimal.ZERO;
            recalcularTotalYFaltante();
            logger.info("Propina eliminada, total actualizado");
            return;
        }

        try {
            // Intentar parsear el valor como número
            BigDecimal nuevaPropina = new BigDecimal(textoInput);
            
            // Validar que no sea negativa
            if (nuevaPropina.compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Propina negativa ingresada: {}. Manteniendo propina actual.", nuevaPropina);
                return;
            }

            // Actualizar propina actual
            propinaActual = nuevaPropina.setScale(0, RoundingMode.HALF_UP);
            
            // Recalcular total final y faltante
            recalcularTotalYFaltante();
            
            logger.info("Propina actualizada: {} - Total final: {} - Faltante: {}", 
                    propinaActual, totalFinal, faltante);
            
        } catch (NumberFormatException e) {
            // Si no es un número válido, ignorar y mantener valor anterior
            logger.debug("Valor no numérico ingresado: {}. Esperando entrada completa.", textoInput);
        }
    }
    
    /**
     * Actualiza el total de propinas sumando todos los campos de propina de los métodos de pago.
     * Recorre todos los pagos registrados, suma las propinas y actualiza el label del header.
     * Si la propina acumulada no llega al mínimo (10%), el próximo método de pago añadido
     * tendrá un campo de propina para completar.
     */
    private void actualizarPropinasTotales() {
        // Sumar todas las propinas de todos los métodos de pago
        BigDecimal totalPropinas = BigDecimal.ZERO;
        
        for (PagoRegistrado pago : pagosRegistrados) {
            if (pago.campoPropina != null) {
                String textoPropina = pago.campoPropina.getText().trim();
                BigDecimal propinaPago = parsearMiles(textoPropina);
                totalPropinas = totalPropinas.add(propinaPago);
            }
        }
        
        // Actualizar propina actual
        propinaActual = totalPropinas;
        
        // Actualizar label del header
        lblPropinaTotalHeader.setText(formatearPesosColombianos(propinaActual));
        
        // Recalcular total final y faltante
        recalcularTotalYFaltante();
        
        logger.info("Propinas actualizadas - Total propinas: {} - Propina mínima: {} - Faltante: {}", 
                propinaActual, propinaMinima, faltante);
    }
    
    /**
     * Recalcula el total final y el faltante cuando cambia la propina.
     * Total final = Total con impuestos + Propina
     * Faltante = Total final - Suma de pagos registrados
     */
    private void recalcularTotalYFaltante() {
        // Recalcular total final
        totalFinal = totalConImpuestos.add(propinaActual);
        lblTotalPago.setText(formatearPesosColombianos(totalFinal));
        
        // Recalcular faltante (restar lo ya pagado)
        BigDecimal totalPagado = pagosRegistrados.stream()
                .map(p -> p.monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        faltante = totalFinal.subtract(totalPagado);
        
        // Actualizar botón de pagar con el faltante
        actualizarBotonPagar();
    }
    
    /**
     * Actualiza el botón de pagar según el faltante:
     * - Si faltante > 0: deshabilita y muestra "FALTA: $X" (gris)
     * - Si faltante < 0: deshabilita y muestra "SOBRA: $X" (rojo sutil)
     * - Si faltante == 0: habilita y muestra "PAGAR" (dorado principal)
     * 
     * Solo se permite facturar cuando el monto pagado es EXACTAMENTE igual al total.
     */
    private void actualizarBotonPagar() {
        if (faltante.compareTo(BigDecimal.ZERO) > 0) {
            // Aún falta dinero por pagar
            btnConfirmarPago.setDisable(true);
            btnConfirmarPago.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.btn.falta"), formatearPesosColombianos(faltante)));
            btnConfirmarPago.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: 700; " +
                    "-fx-background-color: #2a2a2a; -fx-text-fill: #888888; " +
                    "-fx-opacity: 0.7; -fx-alignment: center; " +
                    "-fx-border-color: #404040; -fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; -fx-background-radius: 8px;");
        } else if (faltante.compareTo(BigDecimal.ZERO) < 0) {
            // El monto pagado excede el total — sobrante
            BigDecimal sobrante = faltante.abs();
            btnConfirmarPago.setDisable(true);
            btnConfirmarPago.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.btn.sobra"), formatearPesosColombianos(sobrante)));
            btnConfirmarPago.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: 700; " +
                    "-fx-background-color: rgba(139, 0, 0, 0.25); -fx-text-fill: #ff6b6b; " +
                    "-fx-opacity: 0.85; -fx-alignment: center; " +
                    "-fx-border-color: #8b0000; -fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; -fx-background-radius: 8px;");
        } else {
            // El faltante es exactamente 0 — se puede pagar
            btnConfirmarPago.setDisable(false);
            btnConfirmarPago.setText(IdiomaUtil.obtener("ctrl.btn.pagar"));
            // Remover estilos inline y aplicar estilo dorado principal centrado
            btnConfirmarPago.setStyle(
                    "-fx-font-size: 18px; -fx-font-weight: 700; -fx-alignment: center;");
            btnConfirmarPago.getStyleClass().clear();
            btnConfirmarPago.getStyleClass().add("boton-principal");
        }
    }
    
    /**
     * Actualiza el texto del label de propina en el header del grid según el porcentaje seleccionado.
     */
    private void actualizarTextoPropinaHeader() {
        int porcentajeEntero = (int) (porcentajePropina * 100);
        lblTextoPropinaHeader.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.pago.propina"), porcentajeEntero));
    }

    /**
     * Selecciona un método de pago dinámico y agrega un renglón de pago.
     * Si el faltante es 0, no se puede agregar más pagos.
     * Por defecto, el monto del pago es el total faltante.
     * @param metodo Nombre del método (EFECTIVO, DEBITO, etc.)
     * @param boton Botón que fue clickeado
     */
    private void seleccionarMetodoPago(Long idMetodo, String metodo, Button boton) {
        logger.info("Método de pago seleccionado: {} (ID: {})", metodo, idMetodo);
        
        // Validar que haya faltante por pagar (no permitir si ya está cubierta o excedida)
        if (faltante.compareTo(BigDecimal.ZERO) <= 0) {
            Stage stage = (Stage) boton.getScene().getWindow();
            NotificacionUtil.mostrarAdvertencia(stage, 
                    faltante.compareTo(BigDecimal.ZERO) == 0 
                            ? IdiomaUtil.obtener("ctrl.pago.cuenta_cubierta") 
                            : IdiomaUtil.obtener("ctrl.pago.excede_total"));
            return;
        }
        
        // Agregar el pago con el monto faltante
        agregarPago(idMetodo, metodo, faltante);
        
        // Limpiar selección visual (los botones no quedan seleccionados)
        limpiarSeleccionMetodoPago();
    }
    
    /**
     * Formatea un número BigDecimal con separador de miles (punto).
     * Formato colombiano: 1.000, 10.000, 100.000
     * @param numero Número a formatear
     * @return String formateado con puntos como separador de miles
     */
    private String formatearMiles(BigDecimal numero) {
        if (numero == null) {
            return "0";
        }
        long valor = numero.longValue();
        return String.format("%,d", valor).replace(',', '.');
    }
    
    /**
     * Parsea un texto con separador de miles (punto) a BigDecimal.
     * Ejemplo: "1.000" → 1000, "10.000" → 10000
     * @param texto Texto a parsear
     * @return BigDecimal parseado o ZERO si no es válido
     */
    private BigDecimal parsearMiles(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            // Remover puntos (separador de miles) y parsear
            String limpio = texto.replace(".", "").trim();
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Aplica formato de miles automático a un TextField.
     * Mientras el usuario escribe, formatea: 1 → 10 → 100 → 1.000 → 10.000
     * @param textField Campo de texto al que aplicar el formato
     */
    private void aplicarFormatoMiles(TextField textField) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                return;
            }
            
            // Si el usuario está escribiendo, reformatear
            // Remover puntos y validar que solo sean dígitos
            String sinPuntos = newValue.replace(".", "");
            
            if (!sinPuntos.matches("\\d*")) {
                // Si hay caracteres no numéricos, restaurar valor anterior
                textField.setText(oldValue);
                return;
            }
            
            // Si el valor sin puntos es diferente al valor actual formateado, reformatear
            if (!sinPuntos.isEmpty()) {
                try {
                    long numero = Long.parseLong(sinPuntos);
                    String formateado = String.format("%,d", numero).replace(',', '.');
                    
                    // Solo actualizar si es diferente para evitar loop infinito
                    if (!formateado.equals(newValue)) {
                        textField.setText(formateado);
                        textField.positionCaret(formateado.length());
                    }
                } catch (NumberFormatException e) {
                    // Ignorar
                }
            }
        });
    }
    
    /**
     * Agrega un pago a la lista de pagos registrados y crea las celdas visuales en el GridPane.
     * Las celdas se alinean con las columnas del header:
     * - Columna 0: Nombre del método de pago (se alinea con SUBTOTAL del header)
     * - Columna 1: Vacía (se alinea con IMPOCONSUMO)
     * - Columna 2: Vacía (se alinea con TOTAL SIN PROPINA)
     * - Columna 3: Campo de propina (se alinea con PROPINA)
     * - Columna 4: Monto a pagar (se alinea con TOTAL)
     * - Columna 5: Botón borrar (se alinea con el botón PAGAR del header)
     * @param metodoPago Método de pago utilizado
     * @param monto Monto del pago
     */
    private void agregarPago(Long idMetodoPago, String metodoPago, BigDecimal monto) {
        // Calcular el índice de fila para el nuevo pago (fila 1 + cantidad de pagos existentes)
        int filaDestino = 1 + pagosRegistrados.size();
        boolean esPrimerPago = pagosRegistrados.isEmpty();
        
        // Calcular cuánta propina falta por cubrir
        BigDecimal propinaFaltante = propinaMinima.subtract(propinaActual);
        // El primer método de pago SIEMPRE tiene input de propina, los demás solo si falta propina
        boolean necesitaPropina = esPrimerPago || propinaFaltante.compareTo(BigDecimal.ZERO) > 0;
        
        // COLUMNA 0: Nombre del método de pago
        Label lblMetodo = new Label(metodoPago);
        lblMetodo.setStyle("-fx-font-size: 16px; -fx-text-fill: #f5f5f5; -fx-font-weight: 600; -fx-alignment: center;");
        lblMetodo.setMaxWidth(Double.MAX_VALUE);
        lblMetodo.setAlignment(javafx.geometry.Pos.CENTER);
        VBox celdaMetodo = new VBox(lblMetodo);
        celdaMetodo.setAlignment(javafx.geometry.Pos.CENTER);
        celdaMetodo.getStyleClass().add("fila-pago-grid");
        celdaMetodo.setMaxWidth(Double.MAX_VALUE);
        celdaMetodo.setPrefHeight(70);
        GridPane.setConstraints(celdaMetodo, 0, filaDestino);
        GridPane.setHgrow(celdaMetodo, javafx.scene.layout.Priority.ALWAYS);
        
        // COLUMNA 1: Vacía
        VBox celdaVacia1 = new VBox();
        celdaVacia1.getStyleClass().add("fila-pago-grid");
        celdaVacia1.setMaxWidth(Double.MAX_VALUE);
        celdaVacia1.setPrefHeight(70);
        GridPane.setConstraints(celdaVacia1, 1, filaDestino);
        GridPane.setHgrow(celdaVacia1, javafx.scene.layout.Priority.ALWAYS);
        
        // COLUMNA 2: Vacía
        VBox celdaVacia2 = new VBox();
        celdaVacia2.getStyleClass().add("fila-pago-grid");
        celdaVacia2.setMaxWidth(Double.MAX_VALUE);
        celdaVacia2.setPrefHeight(70);
        GridPane.setConstraints(celdaVacia2, 2, filaDestino);
        GridPane.setHgrow(celdaVacia2, javafx.scene.layout.Priority.ALWAYS);
        
        // COLUMNA 3: Campo de propina (si aún falta propina por cubrir)
        VBox celdaPropina = new VBox();
        celdaPropina.getStyleClass().add("fila-pago-grid");
        celdaPropina.setMaxWidth(Double.MAX_VALUE);
        celdaPropina.setPrefHeight(70);
        celdaPropina.setAlignment(javafx.geometry.Pos.CENTER);
        
        TextField campoPropina = null;
        if (necesitaPropina) {
            // Crear TextField de propina para este método de pago
            // En el primer método mostrar el 10% completo, en los demás mostrar la propina faltante
            BigDecimal valorPropinaMostrar = esPrimerPago ? propinaMinima : propinaFaltante;
            campoPropina = new TextField(formatearMiles(valorPropinaMostrar));
            campoPropina.getStyleClass().add("campo-propina-grid");
            campoPropina.setPrefWidth(150);
            campoPropina.setPrefHeight(45);
            
            // Aplicar formato de miles automático
            aplicarFormatoMiles(campoPropina);
            
            // Listener para actualizar propina total al editar
            int indicePago = pagosRegistrados.size();
            campoPropina.setOnKeyReleased(event -> actualizarPropinasTotales());
            
            celdaPropina.getChildren().add(campoPropina);
        }
        GridPane.setConstraints(celdaPropina, 3, filaDestino);
        GridPane.setHgrow(celdaPropina, javafx.scene.layout.Priority.ALWAYS);
        
        // COLUMNA 4: Monto a pagar (TextField editable con formato de miles)
        TextField txtMonto = new TextField(formatearMiles(monto));
        txtMonto.getStyleClass().add("campo-monto-pago");
        txtMonto.setPrefWidth(150);
        txtMonto.setPrefHeight(45);
        
        // Aplicar formato de miles automático
        aplicarFormatoMiles(txtMonto);
        
        int indicePago = pagosRegistrados.size(); // Índice antes de agregarlo
        txtMonto.setOnKeyReleased(event -> actualizarMontoPago(indicePago, txtMonto));
        
        VBox celdaMonto = new VBox(txtMonto);
        celdaMonto.setAlignment(javafx.geometry.Pos.CENTER);
        celdaMonto.getStyleClass().add("fila-pago-grid");
        celdaMonto.setMaxWidth(Double.MAX_VALUE);
        celdaMonto.setPrefHeight(70);
        GridPane.setConstraints(celdaMonto, 4, filaDestino);
        GridPane.setHgrow(celdaMonto, javafx.scene.layout.Priority.ALWAYS);
        
        // COLUMNA 5: Botón borrar método de pago
        Button btnBorrar = new Button(IdiomaUtil.obtener("ctrl.btn.borrar"));
        btnBorrar.setStyle("-fx-font-size: 14px; -fx-font-weight: 600;");
        btnBorrar.getStyleClass().add("boton-secundario");
        btnBorrar.setPrefWidth(140);
        btnBorrar.setPrefHeight(50);
        btnBorrar.setOnAction(event -> eliminarPago(indicePago));
        
        VBox celdaAccion = new VBox(btnBorrar);
        celdaAccion.setAlignment(javafx.geometry.Pos.CENTER);
        celdaAccion.getStyleClass().add("fila-pago-grid");
        celdaAccion.setMaxWidth(Double.MAX_VALUE);
        celdaAccion.setPrefHeight(70);
        GridPane.setConstraints(celdaAccion, 5, filaDestino);
        GridPane.setHgrow(celdaAccion, javafx.scene.layout.Priority.ALWAYS);
        
        // Agregar todas las celdas al GridPane (6 columnas en total)
        gridResumenPagos.getChildren().addAll(
            celdaMetodo, celdaVacia1, celdaVacia2, celdaPropina,
            celdaMonto, celdaAccion
        );
        
        // Crear objeto de pago registrado (con referencia al TextField de propina si existe)
        PagoRegistrado pago = new PagoRegistrado(idMetodoPago, metodoPago, monto, txtMonto, campoPropina, filaDestino);
        pagosRegistrados.add(pago);
        
        // Recalcular faltante
        recalcularTotalYFaltante();
        
        logger.info("Pago agregado: {} - Monto: {} - Fila: {} - Tiene propina: {} - Faltante: {}", 
                metodoPago, monto, filaDestino, (campoPropina != null), faltante);
    }
    
    /**
     * Actualiza el monto de un pago cuando se edita el TextField.
     * Parsea el texto con formato de miles (puntos) y recalcula el faltante en tiempo real.
     * @param indicePago Índice del pago en la lista pagosRegistrados
     * @param campoMonto TextField que contiene el monto (con formato de miles)
     */
    private void actualizarMontoPago(int indicePago, TextField campoMonto) {
        String textoMonto = campoMonto.getText().trim();
        
        // Parsear monto con separador de miles (punto)
        BigDecimal nuevoMonto = parsearMiles(textoMonto);
        
        // Validar que no sea negativo
        if (nuevoMonto.compareTo(BigDecimal.ZERO) < 0) {
            logger.warn("Monto negativo ingresado: {}. Ignorando.", nuevoMonto);
            return;
        }
        
        // Validar que el total de pagos no exceda el total final
        BigDecimal sumaPagos = BigDecimal.ZERO;
        for (int i = 0; i < pagosRegistrados.size(); i++) {
            if (i == indicePago) {
                sumaPagos = sumaPagos.add(nuevoMonto);
            } else {
                sumaPagos = sumaPagos.add(pagosRegistrados.get(i).monto);
            }
        }
        
        if (sumaPagos.compareTo(totalFinal) > 0) {
            logger.warn("Suma de pagos excede el total final. Total: {}, Suma pagos: {}", 
                    totalFinal, sumaPagos);
            // Ajustar al máximo posible (total final - otros pagos)
            BigDecimal otrosPagos = pagosRegistrados.stream()
                    .filter(p -> !p.equals(pagosRegistrados.get(indicePago)))
                    .map(p -> p.monto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal montoMaximo = totalFinal.subtract(otrosPagos);
            campoMonto.setText(formatearMiles(montoMaximo));
            pagosRegistrados.get(indicePago).monto = montoMaximo;
        } else {
            // Actualizar el monto en el objeto
            pagosRegistrados.get(indicePago).monto = nuevoMonto;
        }
        
        // Recalcular faltante
        recalcularTotalYFaltante();
        
        logger.info("Monto de pago actualizado - Índice: {}, Nuevo monto: {}, Faltante: {}", 
                indicePago, nuevoMonto, faltante);
    }
    
    /**
     * Limpia todos los renglones de pagos del GridPane (filas 1 en adelante).
     */
    private void limpiarPagosDelGrid() {
        // Remover todos los nodos del GridPane que estén en filas >= 1
        gridResumenPagos.getChildren().removeIf(node -> {
            Integer rowIndex = GridPane.getRowIndex(node);
            return rowIndex != null && rowIndex >= 1;
        });
    }
    
    /**
     * Elimina un pago específico de la lista y reconstruye el grid.
     * @param indicePago Índice del pago a eliminar
     */
    private void eliminarPago(int indicePago) {
        if (indicePago < 0 || indicePago >= pagosRegistrados.size()) {
            logger.warn("Intento de eliminar pago con índice inválido: {}", indicePago);
            return;
        }
        
        // Remover el pago de la lista
        PagoRegistrado pagoEliminado = pagosRegistrados.remove(indicePago);
        logger.info("Eliminando pago: {} - Monto: {}", pagoEliminado.metodoPago, pagoEliminado.monto);
        
        // Limpiar todos los pagos del grid
        limpiarPagosDelGrid();
        
        // Re-agregar todos los pagos restantes (esto actualiza los índices correctamente)
        List<PagoRegistrado> pagosTemp = new ArrayList<>(pagosRegistrados);
        pagosRegistrados.clear();
        
        for (PagoRegistrado pago : pagosTemp) {
            agregarPago(pago.idMetodoPago, pago.metodoPago, pago.monto);
        }
        
        // Recalcular faltante
        recalcularTotalYFaltante();
    }

    /**
     * Limpia la selección visual de todos los botones de método de pago dinámicos.
     */
    private void limpiarSeleccionMetodoPago() {
        for (Button btn : botonesMetodosPago) {
            btn.getStyleClass().remove("selected");
        }
    }

    /**
     * Carga los métodos de pago desde el servidor y genera los botones dinámicamente.
     * Se ejecuta al inicializar el panel de pago.
     * Los métodos se muestran como botones en el contenedor horizontal.
     * Si el usuario es ADMIN, se muestra además el botón de gestión.
     */
    private void cargarMetodosPagoDinamicos() {
        logger.info("Cargando métodos de pago desde el servidor");
        
        metodoPagoServicio.listarMetodosPagoAsync()
                .thenAccept(metodos -> {
                    Platform.runLater(() -> {
                        metodosPagoCargados = metodos != null ? metodos : new ArrayList<>();
                        generarBotonesMetodosPago();
                        
                        logger.info("Métodos de pago cargados: {}", metodosPagoCargados.size());
                    });
                })
                .exceptionally(error -> {
                    logger.error("Error al cargar métodos de pago desde el servidor", error);
                    Platform.runLater(() -> {
                        // Fallback: crear solo EFECTIVO como método por defecto local
                        metodosPagoCargados = new ArrayList<>();
                        MetodoPagoDto efectivo = MetodoPagoDto.builder()
                                .nombre("EFECTIVO")
                                .esPredeterminado(true)
                                .build();
                        metodosPagoCargados.add(efectivo);
                        generarBotonesMetodosPago();
                        
                        logger.warn("Se cargó EFECTIVO como fallback por error de conexión");
                    });
                    return null;
                });
    }

    /**
     * Genera los botones de métodos de pago en el contenedor horizontal.
     * Cada botón tiene el styleClass 'btn-metodo-pago' y ejecuta seleccionarMetodoPago
     * al hacer clic. Los botones se adaptan al nombre del método.
     */
    private void generarBotonesMetodosPago() {
        // Limpiar botones anteriores
        contenedorBotonesMetodosPago.getChildren().clear();
        botonesMetodosPago.clear();
        
        for (MetodoPagoDto metodo : metodosPagoCargados) {
            Button btnMetodo = new Button();
            btnMetodo.getStyleClass().add("btn-metodo-pago");
            btnMetodo.setPrefWidth(150);
            btnMetodo.setPrefHeight(100);
            btnMetodo.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);

            // Código arriba en dorado
            Label lblCodigo = new Label(metodo.getCodigo() != null ? metodo.getCodigo() : "");
            lblCodigo.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 700;");
            lblCodigo.setMaxWidth(Double.MAX_VALUE);
            lblCodigo.setAlignment(Pos.CENTER);

            // Nombre debajo
            Label lblNombre = new Label(metodo.getNombre());
            lblNombre.setStyle("-fx-text-fill: #f5f5f5; -fx-font-size: 13px; -fx-font-weight: 600;");
            lblNombre.setMaxWidth(Double.MAX_VALUE);
            lblNombre.setAlignment(Pos.CENTER);

            VBox contenido = new VBox(4, lblCodigo, lblNombre);
            contenido.setAlignment(Pos.CENTER);
            contenido.setMaxWidth(Double.MAX_VALUE);
            btnMetodo.setGraphic(contenido);
            
            // Capturar referencia para el lambda
            String nombreMetodo = metodo.getNombre();
            Long idMetodo = metodo.getIdMetodoPago();
            btnMetodo.setOnAction(event -> seleccionarMetodoPago(idMetodo, nombreMetodo, btnMetodo));
            
            botonesMetodosPago.add(btnMetodo);
            contenedorBotonesMetodosPago.getChildren().add(btnMetodo);
        }
        
        logger.debug("Generados {} botones de métodos de pago", botonesMetodosPago.size());
    }

    /**
     * Confirma el pago y cierra la mesa.
     * Valida que el faltante sea 0 (todo pagado) y que haya al menos un pago registrado.
     * Registra la venta en el backend, lo que también elimina la mesa (hard delete).
     */
    @FXML
    private void confirmarPago() {
        logger.info("Confirmando pago");

        // Validar que haya al menos un pago registrado
        if (pagosRegistrados.isEmpty()) {
            if (panelPago != null && panelPago.getScene() != null) {
                Stage stage = (Stage) panelPago.getScene().getWindow();
                NotificacionUtil.mostrarAdvertencia(stage, IdiomaUtil.obtener("ctrl.pago.registrar_metodo"));
            }
            return;
        }

        // Validar que el faltante sea exactamente 0 (monto pagado == total)
        if (faltante.compareTo(BigDecimal.ZERO) > 0) {
            if (panelPago != null && panelPago.getScene() != null) {
                Stage stage = (Stage) panelPago.getScene().getWindow();
                NotificacionUtil.mostrarAdvertencia(stage, 
                    String.format("Aún falta pagar: %s\nDebe cubrir el total exacto antes de confirmar.", 
                        formatearPesosColombianos(faltante)));
            }
            return;
        }
        if (faltante.compareTo(BigDecimal.ZERO) < 0) {
            if (panelPago != null && panelPago.getScene() != null) {
                Stage stage = (Stage) panelPago.getScene().getWindow();
                NotificacionUtil.mostrarAdvertencia(stage, 
                    String.format("El monto pagado excede el total en: %s\nAjuste los pagos para que coincidan exactamente.", 
                        formatearPesosColombianos(faltante.abs())));
            }
            return;
        }

        logger.info("=== CONFIRMACIÓN DE PAGO ===");
        logger.info("Mesa: {}, Subtotal: {}, Impoconsumo: {}, Propina: {}, Total: {}",
                mesa.getNombre(), subtotal, impoconsumo, propinaActual, totalFinal);

        // Construir lista de pagos DTO
        List<PagoDto> pagosDto = new ArrayList<>();
        for (PagoRegistrado pago : pagosRegistrados) {
            BigDecimal propinaPago = BigDecimal.ZERO;
            if (pago.campoPropina != null) {
                propinaPago = parsearMiles(pago.campoPropina.getText());
            }
            pagosDto.add(PagoDto.builder()
                    .idMetodoPago(pago.idMetodoPago)
                    .nombreMetodoPago(pago.metodoPago)
                    .monto(pago.monto)
                    .propina(propinaPago)
                    .build());
        }

        // Construir líneas de venta DTO
        List<LineaVentaDto> lineasDto = new ArrayList<>();
        for (LineaPedido linea : lineasPedido) {
            lineasDto.add(LineaVentaDto.builder()
                    .idProducto(linea.getIdProducto())
                    .nombreProducto(linea.getNombreProducto())
                    .precioUnitario(linea.getPrecioUnitario())
                    .cantidad(1)
                    .build());
        }

        // Construir DTO de registro de venta
        RegistrarVentaDto ventaDto = RegistrarVentaDto.builder()
                .idMesa(mesa.getIdMesa())
                .numeroMesa(mesa.getNombre())
                .idMesero(mesa.getMeseroId())
                .nombreMesero(mesa.getMeseroNombre())
                .subtotal(subtotal)
                .impoconsumo(impoconsumo)
                .propina(propinaActual)
                .total(totalFinal)
                .pagos(pagosDto)
                .lineas(lineasDto)
                .build();

        // Obtener datos del cajero
        AuthRespuestaDto usuario = NavegacionUtil.getUsuarioActual();

        // Enviar al backend
        ventaServicio.registrarVentaAsync(ventaDto, usuario.getIdUsuario(), usuario.getNombreCompleto())
                .thenAccept(ventaRegistrada -> {
                    Platform.runLater(() -> {
                        logger.info("Venta registrada exitosamente con ID: {}", ventaRegistrada.getIdVenta());

                        StringBuilder metodosStr = new StringBuilder();
                        for (PagoRegistrado pago : pagosRegistrados) {
                            metodosStr.append(String.format("%s: %s\n",
                                    pago.metodoPago, formatearPesosColombianos(pago.monto)));
                        }

                        if (panelPago != null && panelPago.getScene() != null) {
                            Stage stage = (Stage) panelPago.getScene().getWindow();
                            NotificacionUtil.mostrarExito(stage,
                                    String.format("Venta #%d registrada\nTotal: %s\n\n%s",
                                            ventaRegistrada.getIdVenta(),
                                            formatearPesosColombianos(totalFinal),
                                            metodosStr.toString()));
                        }

                        volverAtras();
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        logger.error("Error al registrar venta en backend", ex);
                        if (panelPago != null && panelPago.getScene() != null) {
                            Stage stage = (Stage) panelPago.getScene().getWindow();
                            NotificacionUtil.mostrarError(stage,
                                    "Error al registrar la venta: " + ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    /**
     * Configura el atajo de teclado para abrir el panel de pago con la tecla F2.
     * También configura ESC para volver a categorías desde el panel de pago.
     * Este método debe llamarse después de que la escena esté disponible.
     */
    public void configurarAtajoTecladoPago() {
        Platform.runLater(() -> {
            if (txtNumeroMesa != null && txtNumeroMesa.getScene() != null) {
                txtNumeroMesa.getScene().setOnKeyPressed(event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.F1) {
                        logger.info("Atajo de teclado 'F1' detectado, abriendo opciones");
                        mostrarPanelOpciones();
                        event.consume();
                        return;
                    }
                    if (event.getCode() == javafx.scene.input.KeyCode.F2) {
                        logger.info("Atajo de teclado 'F2' detectado, abriendo panel de pago");
                        mostrarPanelPago();
                        event.consume();
                        return;
                    }
                    if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        // Navegación contextual: Ventas/Opciones/Panel2 → Panel1 → Categorías → Facturación
                        if (panelVentas != null && panelVentas.isVisible()) {
                            logger.info("ESC: Ventas → Categorías");
                            ocultarPanelVentas();
                        } else if (panelOpciones != null && panelOpciones.isVisible()) {
                            logger.info("ESC: Opciones → Categorías");
                            ocultarPanelOpciones();
                        } else if (panelPago != null && panelPago.isVisible()) {
                            if (panelMetodosPagoDetalle != null && panelMetodosPagoDetalle.isVisible()) {
                                logger.info("ESC: Panel 2 → Panel 1");
                                volverAResumenSimple();
                            } else {
                                logger.info("ESC: Panel 1 → Categorías");
                                volverACategorias();
                            }
                        } else {
                            logger.info("ESC: Mesa → Facturación");
                            volverAtras();
                        }
                        event.consume();
                        return;
                    }

                    // Detector de código de producto por teclado numérico
                    // Se desactiva si el foco está en un campo de texto
                    if (txtNumeroMesa.getScene() == null) return;
                    javafx.scene.Node focoActual = txtNumeroMesa.getScene().getFocusOwner();
                    if (focoActual instanceof TextField || focoActual instanceof TextArea) {
                        return;
                    }

                    String tecla = event.getText();
                    if (tecla != null && tecla.length() == 1 && Character.isDigit(tecla.charAt(0))) {
                        bufferCodigoTeclado.append(tecla.charAt(0));
                        event.consume();

                        // Reiniciar temporizador de limpieza (800ms sin tecla → limpiar buffer)
                        if (temporizadorCodigo != null) temporizadorCodigo.stop();
                        temporizadorCodigo = new PauseTransition(javafx.util.Duration.millis(800));
                        temporizadorCodigo.setOnFinished(ev -> {
                            logger.debug("Buffer de código limpiado por timeout: {}", bufferCodigoTeclado);
                            bufferCodigoTeclado.setLength(0);
                        });
                        temporizadorCodigo.play();

                        // Panel de pago visible → 2 dígitos busca método de pago
                        if (panelPago != null && panelPago.isVisible()
                                && panelMetodosPagoDetalle != null && panelMetodosPagoDetalle.isVisible()) {
                            if (bufferCodigoTeclado.length() == 2) {
                                String codigoBuscado = bufferCodigoTeclado.toString();
                                bufferCodigoTeclado.setLength(0);
                                if (temporizadorCodigo != null) temporizadorCodigo.stop();
                                buscarYSeleccionarMetodoPorCodigo(codigoBuscado);
                            }
                        } else {
                            // Vista de productos → 3 dígitos busca producto
                            if (bufferCodigoTeclado.length() == 3) {
                                String codigoBuscado = bufferCodigoTeclado.toString();
                                bufferCodigoTeclado.setLength(0);
                                if (temporizadorCodigo != null) temporizadorCodigo.stop();
                                buscarYAgregarPorCodigo(codigoBuscado);
                            }
                        }
                    }
                });
                logger.info("Atajos de teclado configurados: F1 (opciones), F2 (pago), ESC (volver), numérico (código producto)");
            }
        });
    }

    /** Carga todos los productos activos en cache para búsqueda por código con teclado. */
    private void cargarCacheProductos() {
        productoServicio.listarTodosAsync()
                .thenAccept(productos -> {
                    todosLosProductosCache = productos;
                    logger.info("Cache de productos cargado: {} productos", productos.size());
                })
                .exceptionally(ex -> {
                    logger.error("Error al cargar cache de productos para búsqueda por código", ex);
                    return null;
                });
    }

    /**
     * Busca un producto por código en el cache global y lo agrega al pedido.
     * El código se compara sin importar categoría.
     */
    private void buscarYAgregarPorCodigo(String codigo) {
        logger.info("Buscando producto por código de teclado: {}", codigo);

        ProductoDto encontrado = todosLosProductosCache.stream()
                .filter(p -> p.getCodigo() != null && p.getCodigo().equalsIgnoreCase(codigo))
                .findFirst()
                .orElse(null);

        if (encontrado != null) {
            logger.info("Producto encontrado por código {}: {}", codigo, encontrado.getNombre());
            agregarProductoAlPedido(encontrado);
            if (txtNumeroMesa.getScene() != null) {
                Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                NotificacionUtil.mostrarExito(stage, encontrado.getCodigo() + " - " + encontrado.getNombre());
            }
        } else {
            logger.warn("No se encontró producto con código: {}", codigo);
            if (txtNumeroMesa.getScene() != null) {
                Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                NotificacionUtil.mostrarError(stage,
                        IdiomaUtil.obtener("ctrl.facturacion.codigo_no_encontrado") + ": " + codigo);
            }
        }
    }

    /**
     * Busca un método de pago por código de 2 dígitos y lo selecciona automáticamente.
     */
    private void buscarYSeleccionarMetodoPorCodigo(String codigo) {
        logger.info("Buscando método de pago por código de teclado: {}", codigo);

        MetodoPagoDto encontrado = metodosPagoCargados.stream()
                .filter(m -> m.getCodigo() != null && m.getCodigo().equalsIgnoreCase(codigo))
                .findFirst()
                .orElse(null);

        if (encontrado != null) {
            logger.info("Método de pago encontrado por código {}: {}", codigo, encontrado.getNombre());
            String nombre = encontrado.getNombre();
            Long idMetodo = encontrado.getIdMetodoPago();
            for (Button btn : botonesMetodosPago) {
                javafx.scene.Node grafico = btn.getGraphic();
                if (grafico instanceof VBox vbox) {
                    for (javafx.scene.Node hijo : vbox.getChildren()) {
                        if (hijo instanceof Label lbl && lbl.getText().equals(nombre)) {
                            seleccionarMetodoPago(idMetodo, nombre, btn);
                            return;
                        }
                    }
                }
            }
        } else {
            logger.warn("No se encontró método de pago con código: {}", codigo);
            if (txtNumeroMesa.getScene() != null) {
                Stage stage = (Stage) txtNumeroMesa.getScene().getWindow();
                NotificacionUtil.mostrarError(stage,
                        IdiomaUtil.obtener("ctrl.facturacion.metodo_pago_no_encontrado") + ": " + codigo);
            }
        }
    }
    
    /**
     * Muestra un selector para cambiar el porcentaje de propina.
     * Permite elegir entre 0%, 5%, 10%, 15%, 20% o un porcentaje personalizado.
     */
    @FXML
    private void seleccionarPorcentajePropina() {
        logger.info("Abriendo selector de porcentaje de propina");
        
        StackPane modalRoot = crearContenedorModalLuxury(350, 450);
        VBox contenedor = new VBox(15);
        contenedor.setPadding(new Insets(30));
        contenedor.setAlignment(Pos.CENTER);
        
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.pago.seleccionar_propina"));
        titulo.setStyle("-fx-font-size: 22px; -fx-text-fill: #d4af37; -fx-font-weight: 700;");
        
        VBox botonesContainer = new VBox(10);
        botonesContainer.setAlignment(Pos.CENTER);
        
        // Opciones de porcentaje predefinidas
        double[] porcentajes = {0.0, 0.05, 0.10, 0.15, 0.20};
        String[] etiquetas = {IdiomaUtil.obtener("ctrl.pago.sin_propina"), "5%", "10%", "15%", "20%"};
        
        for (int i = 0; i < porcentajes.length; i++) {
            double porcentaje = porcentajes[i];
            String etiqueta = etiquetas[i];
            
            Button btnPorcentaje = new Button(etiqueta);
            btnPorcentaje.setPrefWidth(280);
            btnPorcentaje.setPrefHeight(50);
            
            // Marcar el porcentaje actual
            if (Math.abs(porcentajePropina - porcentaje) < 0.001) {
                btnPorcentaje.setStyle("-fx-background-color: rgba(212, 175, 55, 0.18); -fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 700; -fx-border-color: #d4af37; -fx-border-width: 1.5px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
            } else {
                btnPorcentaje.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #f5f5f5; -fx-font-size: 16px; -fx-font-weight: 600; -fx-border-color: #404040; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
            }
            
            btnPorcentaje.setOnMouseEntered(e -> {
                if (Math.abs(porcentajePropina - porcentaje) >= 0.001) {
                    btnPorcentaje.setStyle("-fx-background-color: rgba(212, 175, 55, 0.1); -fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 600; -fx-border-color: rgba(212, 175, 55, 0.5); -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
                }
            });
            
            btnPorcentaje.setOnMouseExited(e -> {
                if (Math.abs(porcentajePropina - porcentaje) >= 0.001) {
                    btnPorcentaje.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #f5f5f5; -fx-font-size: 16px; -fx-font-weight: 600; -fx-border-color: #404040; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
                }
            });
            
            btnPorcentaje.setOnAction(e -> {
                porcentajePropina = porcentaje;
                actualizarPorcentajePropina();
                cerrarOverlay();
            });
            
            botonesContainer.getChildren().add(btnPorcentaje);
        }
        
        contenedor.getChildren().addAll(titulo, botonesContainer);
        modalRoot.getChildren().add(contenedor);
        
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
    }
    
    /**
     * Actualiza el texto del botón de porcentaje y recalcula el resumen.
     */
    private void actualizarPorcentajePropina() {
        int porcentajeEntero = (int) (porcentajePropina * 100);
        btnPorcentajePropina.setText("(" + porcentajeEntero + "%)");
        logger.info("Porcentaje de propina actualizado a: {}%", porcentajeEntero);
        
        // Recalcular los valores del resumen con el nuevo porcentaje
        if (totalConImpuestos != null && totalConImpuestos.compareTo(BigDecimal.ZERO) > 0) {
            // Recalcular propina
            propinaMinima = totalConImpuestos.multiply(BigDecimal.valueOf(porcentajePropina)).setScale(0, RoundingMode.HALF_UP);
            propinaActual = propinaMinima;
            
            // Recalcular total final
            totalFinal = totalConImpuestos.add(propinaActual);
            faltante = totalFinal;
            
            // Actualizar labels del resumen simple
            lblPropinaResumen.setText(formatearPesosColombianos(propinaActual));
            lblTotalFinalResumen.setText(formatearPesosColombianos(totalFinal));
            
            // Actualizar labels del panel detallado si está visible
            if (panelMetodosPagoDetalle.isVisible()) {
                lblPropinaTotalHeader.setText(formatearPesosColombianos(propinaActual));
                lblTotalPago.setText(formatearPesosColombianos(totalFinal));
                
                // Recalcular faltante y actualizar botón
                recalcularTotalYFaltante();
                
                // Actualizar texto del porcentaje en header del grid
                actualizarTextoPropinaHeader();
            }
            
            logger.info("Propina recalculada: {}, Total final: {}", propinaActual, totalFinal);
        }
    }
    
    /**
     * Efecto hover para el botón de porcentaje.
     */
    @FXML
    private void onHoverPorcentaje(MouseEvent event) {
        btnPorcentajePropina.setStyle("-fx-background-color: transparent; -fx-text-fill: #c9a961; -fx-font-size: 16px; -fx-font-weight: 700; -fx-cursor: hand; -fx-underline: true; -fx-padding: 0; -fx-border-width: 0;");
    }
    
    /**
     * Efecto de salida del hover para el botón de porcentaje.
     */
    @FXML
    private void onExitPorcentaje(MouseEvent event) {
        btnPorcentajePropina.setStyle("-fx-background-color: transparent; -fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: 600; -fx-cursor: hand; -fx-underline: true; -fx-padding: 0; -fx-border-width: 0;");
    }
}
