/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.controlador;

import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.constantes.Constantes;
import com.kipu.cliente.utilidad.AlertaUtil;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NavegacionUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.cliente.utilidad.MonitorConexion;
import com.kipu.cliente.componente.TopologiaRed;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controlador principal del sistema que maneja todas las subvistas.
 * 
 * Sistema de navegación basado en subvistas:
 * - Panel de Inicio: Grid de opciones del menú
 * - Panel de Facturación: Mesas activas, dinero, meseros
 * - Panel de Pedidos: (futuro)
 * - Panel de Productos: (futuro)
 * - Panel de Reportes: (futuro)
 * 
 * Ventajas de este enfoque:
 * - No se recarga el FXML completo en cada navegación
 * - Header y footer siempre visibles
 * - Breadcrumbs funcionan correctamente
 * - Mejor performance
 * - Estado se mantiene al volver atrás
 */
public class MenuPrincipalController {
    
    private static final Logger logger = LoggerFactory.getLogger(MenuPrincipalController.class);
    
    // ============================================
    // COMPONENTES DEL HEADER
    // ============================================
    
    @FXML private Label labelNombreUsuario;
    @FXML private Label labelRolUsuario;
    @FXML private ImageView logoHeader;
    @FXML private HBox breadcrumbsBar;
    @FXML private Label labelBreadcrumbs;
    @FXML private VBox headerGeneral;
    
    // ============================================
    // PANELES DE SUBVISTAS
    // ============================================
    
    @FXML private StackPane contenedorPrincipal;
    @FXML private com.kipu.cliente.componente.FondoAnimado fondoAnimado;
    @FXML private VBox panelInicio;
    @FXML private VBox panelProductos;
    
    // ============================================
    // OPCIONES DEL PANEL DE INICIO
    // ============================================
    
    @FXML private Text textoBienvenida;
    @FXML private VBox opcionFacturar;
    @FXML private VBox opcionUsuarios;
    @FXML private VBox opcionProductos;
    @FXML private VBox opcionReportes;
    @FXML private VBox opcionConfiguracion;
    
    // ============================================
    // COMPONENTES DEL PANEL DE FACTURACIÓN
    // ============================================
    // Movidos a FacturacionController
    
    // Panel de selección de mesero (aún en FXML de menú principal)
    @FXML private VBox panelSeleccionMesero;
    @FXML private Text txtNombreMesaNueva;
    @FXML private GridPane gridMeseros;
    
    // ============================================
    // COMPONENTES DEL FOOTER
    // ============================================
    
    @FXML private Label labelFechaFooter;
    @FXML private Label labelHoraFooter;
    @FXML private HBox indicadorConexionFooter;
    
    // ============================================
    // ESTADO INTERNO
    // ============================================
    
    private AuthRespuestaDto usuarioActual;
    private Timeline timelineReloj;
    
    // Historial de navegación para función "volver atrás"
    private final java.util.Stack<Vista> historialVistas = new java.util.Stack<>();
    
    /**
     * Enumeración de las vistas disponibles en el sistema
     */
    private enum Vista {
        INICIO,
        FACTURACION,
        FACTURACION_MESAS_ACTIVAS,
        FACTURACION_DINERO_MESAS,
        FACTURACION_MESEROS,
        SELECCION_MESERO,
        USUARIOS,
        PRODUCTOS,
        PRODUCTOS_CREAR_CATEGORIA,
        PRODUCTOS_CREAR_PRODUCTO,
        PRODUCTOS_LISTADO_CATEGORIAS,
        PRODUCTOS_LISTADO_PRODUCTOS,
        PRODUCTOS_CONTROL_INVENTARIO,
        REPORTES,
        CONFIGURACION
    }
    
    private Vista vistaActual = Vista.INICIO;
    
    // ============================================
    // INICIALIZACIÓN
    // ============================================
    
    @FXML
    public void initialize() {
        logger.info("Inicializando MenuPrincipalController");
        
        // Validar usuario autenticado
        usuarioActual = NavegacionUtil.getUsuarioActual();
        if (usuarioActual == null) {
            AlertaUtil.mostrarError(IdiomaUtil.obtener("ctrl.menu.sesion_invalida"), IdiomaUtil.obtener("ctrl.menu.no_usuario_autenticado"));
            NavegacionUtil.irALogin();
            return;
        }
        
        // Configurar header
        labelNombreUsuario.setText(usuarioActual.getNombreCompleto());
        labelRolUsuario.setText(usuarioActual.getRol());
        textoBienvenida.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.menu.bienvenido"), usuarioActual.getNombreCompleto()));
        
        // Configurar permisos por rol
        configurarPermisosPorRol();
        
        // Configurar breadcrumbsBar
        logger.info("Inicializando componentes del header - logoHeader: {}, breadcrumbsBar: {}", 
            logoHeader != null ? "INYECTADO" : "NULL", 
            breadcrumbsBar != null ? "INYECTADO" : "NULL");
            
        if (breadcrumbsBar != null) {
            breadcrumbsBar.setVisible(false);
            breadcrumbsBar.setManaged(false);
        }
        
        // Inicializar reloj del footer
        inicializarRelojFooter();
        
        // Inicializar monitor de conexión en el footer
        inicializarMonitorConexion();
        
        // Configurar eventos de facturación
        // configurarEventosFacturacion(); // Movido a FacturacionController
        
        // Mostrar panel de inicio por defecto
        mostrarVista(Vista.INICIO);
        
        // Listener para detectar cuando la vista se remueve de la escena (navegación o cierre)
        if (contenedorPrincipal != null) {
            contenedorPrincipal.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null) {
                    logger.info("MenuPrincipalController removido de escena - Ejecutando limpieza");
                    detener();
                }
            });
        }
        
        // Registrar este controlador para manejar la tecla ESC globalmente
        NavegacionUtil.setManejadorAtras(this::manejarNavegacionAtrasGlobal);
        
        logger.info("MenuPrincipalController inicializado correctamente");
    }
    
    /**
     * Detiene todas las animaciones y timers para liberar recursos.
     * Llamado automáticamente cuando la vista sale de la escena.
     */
    public void detener() {
        logger.info("Deteniendo recursos de MenuPrincipalController");
        
        // 1. Detener reloj
        if (timelineReloj != null) {
            timelineReloj.stop();
            timelineReloj = null;
        }
        
        // 2. Detener fondo animado
        if (fondoAnimado != null) {
            fondoAnimado.detener();
        }
        
        // 3. Limpiar subvistas actuales
        limpiarSubvistasDinamicas();
        
        // 4. Liberar el manejador de ESC global
        NavegacionUtil.setManejadorAtras(null);
    }
    
    /**
     * Configura qué opciones del menú son visibles según el rol del usuario.
     * 
     * Permisos por rol:
     * 
     * ADMIN:
     * - ✓ Facturar (puede procesar ventas)
     * - ✓ Usuarios (gestión de usuarios, meseros y roles)
     * - ✓ Productos (gestión completa de inventario)
     * - ✓ Reportes (análisis y estadísticas)
     * 
     * CAJERO:
     * - ✓ Facturar (procesar ventas y cobros)
     * - ✗ Usuarios (sin acceso a gestión de personal)
     * - ✗ Productos (sin acceso a inventario)
     * - ✗ Reportes (sin acceso a información financiera completa)
     * 
     * MESERO:
     * - ✓ Facturar (puede procesar ventas asignadas)
     * - ✗ Usuarios (sin acceso a gestión de personal)
     * - ✗ Productos (sin acceso a inventario)
     * - ✗ Reportes (sin acceso a información financiera)
     * 
     * Nota: Se ocultan las opciones no permitidas (visible=false y managed=false)
     * para que no ocupen espacio en el layout
     */
    private void configurarPermisosPorRol() {
        String rol = usuarioActual.getRol();
        
        switch (rol) {
            case "ADMIN":
                // Admin tiene acceso a todo
                mostrarOpcion(opcionFacturar, true);
                mostrarOpcion(opcionUsuarios, true);
                mostrarOpcion(opcionProductos, true);
                mostrarOpcion(opcionReportes, true);
                mostrarOpcion(opcionConfiguracion, true);
                break;
                
            case "CAJERO", "MESERO":
                // Cajero y mesero solo facturan
                mostrarOpcion(opcionFacturar, true);
                mostrarOpcion(opcionUsuarios, false);
                mostrarOpcion(opcionProductos, false);
                mostrarOpcion(opcionReportes, false);
                mostrarOpcion(opcionConfiguracion, false);
                break;
                
            default:
                // Rol desconocido - ocultar todo por seguridad
                AlertaUtil.mostrarAdvertencia(
                    IdiomaUtil.obtener("ctrl.menu.rol_desconocido"), 
                    java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.menu.rol_invalido"), rol)
                );
                mostrarOpcion(opcionFacturar, false);
                mostrarOpcion(opcionUsuarios, false);
                mostrarOpcion(opcionProductos, false);
                mostrarOpcion(opcionReportes, false);
                mostrarOpcion(opcionConfiguracion, false);
                break;
        }
    }
    
    /**
     * Muestra u oculta una opción del menú.
     * 
     * @param opcion El VBox de la opción a mostrar/ocultar
     * @param mostrar true para mostrar, false para ocultar
     * 
     * Nota: Se usa managed=false para que el elemento oculto no ocupe espacio
     * en el GridPane, permitiendo que las opciones visibles se reorganicen
     */
    private void mostrarOpcion(VBox opcion, boolean mostrar) {
        opcion.setVisible(mostrar);
        opcion.setManaged(mostrar);
    }
    
    // ============================================
    // MÉTODOS DE FACTURACIÓN (MOVIDOS A FacturacionController)
    // ============================================
    // Los siguientes métodos fueron movidos a la subvista de facturación
    // Ver: /controlador/facturacion/FacturacionController.java
    
    /*
    private void configurarEventosFacturacion() { ... }
    private void resetearFacturacion() { ... }
    private void mostrarMesasActivas() { ... }
    private void mostrarDineroMesas() { ... }
    private void mostrarMeseros() { ... }
    private void buscarMesa() { ... }
    private void crearMesaDirecta() { ... }
    private void cargarMesasActivas() { ... }
    */
    
    /**
     * Inicializa el reloj del footer que se actualiza cada segundo.
     * Muestra día, fecha y hora en tiempo real.
     */
    private void inicializarRelojFooter() {
        // Formatters para fecha y hora en español con nombres completos
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        // Actualizar inmediatamente
        actualizarTimestamp(formatoFecha, formatoHora);
        
        // Timeline que se ejecuta cada segundo
        timelineReloj = new Timeline(
            new KeyFrame(Duration.seconds(1), event -> {
                actualizarTimestamp(formatoFecha, formatoHora);
            })
        );
        timelineReloj.setCycleCount(Animation.INDEFINITE);
        timelineReloj.play();
    }
    
    /**
     * Actualiza los labels de fecha y hora con el timestamp actual.
     */
    private void actualizarTimestamp(DateTimeFormatter formatoFecha, DateTimeFormatter formatoHora) {
        LocalDateTime ahora = LocalDateTime.now();
        labelFechaFooter.setText(ahora.format(formatoFecha));
        labelHoraFooter.setText(ahora.format(formatoHora));
    }
    
    /**
     * Inicializa el monitor de conexión y agrega el indicador visual
     * al footer de la aplicación.
     */
    private void inicializarMonitorConexion() {
        MonitorConexion monitor = MonitorConexion.getInstancia();
        monitor.iniciar();
        
        if (indicadorConexionFooter != null) {
            HBox indicador = monitor.crearIndicador(true);
            // Al hacer clic en el indicador de conexión, mostrar diagrama de red
            indicador.setStyle("-fx-cursor: hand;");
            indicador.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                e -> TopologiaRed.mostrarPopupRapido(contenedorPrincipal, e));
            indicadorConexionFooter.getChildren().add(indicador);
        }
    }
    
    // ============================================
    // NAVEGACIÓN ENTRE VISTAS
    // ============================================
    
    /**
     * Navega a una nueva vista y la agrega al historial.
     * 
     * @param vista Vista a la que navegar
     */
    private void navegarA(Vista vista) {
        // Agregar la vista actual al historial antes de cambiar (si no es la misma)
        if (vistaActual != vista && vistaActual != null) {
            historialVistas.push(vistaActual);
            logger.debug("Vista agregada al historial: {}. Historial: {}", vistaActual, historialVistas.size());
        }
        
        // Mostrar la nueva vista
        mostrarVista(vista);
    }
    
    /**
     * Muestra una vista específica ocultando las demás.
     * NO agrega al historial (usa navegarA() para eso).
     * 
     * @param vista Vista a mostrar
     */
    private void mostrarVista(Vista vista) {
        vistaActual = vista;
        
        // Ocultar todos los paneles
        panelInicio.setVisible(false);
        panelInicio.setManaged(false);
        // panelFacturacion.setVisible(false); // Panel eliminado
        // panelFacturacion.setManaged(false); // Panel eliminado
        panelSeleccionMesero.setVisible(false);
        panelSeleccionMesero.setManaged(false);
        panelProductos.setVisible(false);
        panelProductos.setManaged(false);
        
        // Actualizar breadcrumbs
        switch (vista) {
            case INICIO:
                // Limpiar solo subvistas dinámicas, mantener paneles principales
                limpiarSubvistasDinamicas();
                
                panelInicio.setVisible(true);
                panelInicio.setManaged(true);
                break;
                
            case FACTURACION:
                // Limpiar solo subvistas dinámicas
                limpiarSubvistasDinamicas();
                
                // Cargar subvista de facturación dinámicamente
                cargarVistaEnContenedor("/vista/subvistas/facturacion/facturacion.fxml");
                break;
                
            case FACTURACION_MESAS_ACTIVAS:
            case FACTURACION_DINERO_MESAS:
            case FACTURACION_MESEROS:
                // Estas vistas ahora se manejan dentro de la subvista de facturación
                // No hacer nada aquí, la navegación interna la maneja FacturacionController
                break;
                
            case SELECCION_MESERO:
                panelSeleccionMesero.setVisible(true);
                panelSeleccionMesero.setManaged(true);
                break;
                
            case PRODUCTOS:
                // Limpiar solo subvistas dinámicas
                limpiarSubvistasDinamicas();
                
                panelProductos.setVisible(true);
                panelProductos.setManaged(true);
                break;
                
            case PRODUCTOS_LISTADO_CATEGORIAS:
                // Limpiar subvistas dinámicas
                limpiarSubvistasDinamicas();
                
                // Cargar vista de listado de categorías dinámicamente
                cargarVistaEnContenedor("/vista/subvistas/productos/categoria-listado.fxml");
                break;
                
            case PRODUCTOS_LISTADO_PRODUCTOS:
                limpiarSubvistasDinamicas();
                cargarVistaEnContenedor("/vista/subvistas/productos/producto-listado.fxml");
                break;
                
            case PRODUCTOS_CREAR_CATEGORIA:
                limpiarSubvistasDinamicas();
                cargarVistaEnContenedor("/vista/subvistas/productos/categoria-crear.fxml");
                break;
                
            case PRODUCTOS_CREAR_PRODUCTO:
                limpiarSubvistasDinamicas();
                cargarVistaEnContenedor("/vista/subvistas/productos/producto-crear.fxml");
                break;
                
            case PRODUCTOS_CONTROL_INVENTARIO:
                limpiarSubvistasDinamicas();
                cargarVistaEnContenedor("/vista/subvistas/productos/inventario-control.fxml");
                break;
                
            case USUARIOS:
                // Limpiar solo subvistas dinámicas
                limpiarSubvistasDinamicas();
                
                // Cargar vista de usuarios dinámicamente
                cargarVistaEnContenedor("/vista/usuarios.fxml");
                break;
                
            case REPORTES:
                // Futuras implementaciones
                break;
                
            case CONFIGURACION:
                // Limpiar subvistas dinámicas
                limpiarSubvistasDinamicas();
                
                // Cargar vista de configuraciones y herramientas
                cargarVistaEnContenedor("/vista/subvistas/configuracion/configuracion-herramientas.fxml");
                break;
        }
        // Actualizar vista actual
        vistaActual = vista;
        
        // Actualizar breadcrumbs
        actualizarBreadcrumbs();
        
        logger.info("Vista cambiada a: {}. Historial: {} vistas", vista, historialVistas.size());
    }
    
    /**
     * Limpia las subvistas dinámicas del contenedorPrincipal.
     * Solo remueve nodos que no son los paneles principales (panelInicio, 
     * panelSeleccionMesero, panelProductos).
     * 
     * Nota: panelFacturacion ya no existe, ahora es una subvista dinámica.
     */
    private void limpiarSubvistasDinamicas() {
        // Identificar nodos a remover
        List<javafx.scene.Node> nodosARemover = new ArrayList<>();
        
        for (javafx.scene.Node nodo : contenedorPrincipal.getChildren()) {
            if (nodo != panelInicio && 
                nodo != panelSeleccionMesero && 
                nodo != panelProductos &&
                nodo != fondoAnimado) {
                
                nodosARemover.add(nodo);
            }
        }
        
        // Limpiar recursos de cada nodo antes de removerlo
        for (javafx.scene.Node nodo : nodosARemover) {
            // Usar la utilidad de limpieza para detener animaciones internas
            NavegacionUtil.limpiarNodo(nodo);
        }
        
        // Remover del contenedor
        contenedorPrincipal.getChildren().removeAll(nodosARemover);
    }
    
    /**
     * Carga una vista FXML dinámicamente en el contenedor principal.
     * Inyecta MenuPrincipalController via interfaz SubvistaController.
     * 
     * @param rutaFxml ruta del archivo FXML a cargar
     */
    private void cargarVistaEnContenedor(String rutaFxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(rutaFxml));
            loader.setResources(com.kipu.cliente.utilidad.IdiomaUtil.obtenerBundle());
            Parent vista = loader.load();
            
            // Inyectar MenuPrincipalController via interfaz (sin reflexión)
            Object controller = loader.getController();
            if (controller instanceof SubvistaController subvista) {
                subvista.setMenuPrincipal(this);
            }
            
            // Para FacturacionController, guardar referencia en UserData para ESC handling
            if (controller instanceof com.kipu.cliente.controlador.facturacion.FacturacionController) {
                vista.setUserData(controller);
            }
            
            // Agregar al contenedor principal
            contenedorPrincipal.getChildren().add(vista);
            vista.setVisible(true);
            vista.setManaged(true);
            
            logger.info("Vista cargada: {}", rutaFxml);
            
        } catch (IOException e) {
            logger.error("Error al cargar vista: {}", rutaFxml, e);
            Stage stage = (Stage) labelNombreUsuario.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, IdiomaUtil.obtener("ctrl.menu.error_cargar_vista"));
        }
    }
    
    /**
     * Actualiza los breadcrumbs basándose en la vista actual y el historial.
     */
    private void actualizarBreadcrumbs() {
        logger.info("Actualizando breadcrumbs para vistaActual: {}", vistaActual);
        
        // En Inicio mostramos el Logo (Nota: eliminamos FACTURACION del logo si el usuario lo pide implícitamente)
        if (vistaActual == Vista.INICIO) {
            if (breadcrumbsBar != null) {
                breadcrumbsBar.setVisible(false);
                breadcrumbsBar.setManaged(false);
            }
            if (logoHeader != null) {
                logoHeader.setVisible(true);
                logoHeader.setManaged(true);
            }
            logger.debug("Breadcrumbs ocultos - Mostrando Logo (Inicio)");
            return;
        }
        
        // En otras vistas (incluyendo Facturación), ocultamos Logo y mostramos Breadcrumbs
        if (logoHeader != null) {
            logoHeader.setVisible(false);
            logoHeader.setManaged(false);
        }
        
        if (breadcrumbsBar != null) {
            breadcrumbsBar.setVisible(true);
            breadcrumbsBar.setManaged(true);
            
            // Construir breadcrumbs desde el historial
            StringBuilder breadcrumbs = new StringBuilder(IdiomaUtil.obtener("ctrl.menu.breadcrumb.menu_principal"));
            
            // Agregar vistas del historial (solo las principales)
            for (Vista vista : historialVistas) {
                String nombreVista = obtenerNombreVista(vista);
                if (nombreVista != null) {
                    breadcrumbs.append(" > ").append(nombreVista);
                }
            }
            
            // Agregar vista actual
            String nombreVistaActual = obtenerNombreVista(vistaActual);
            if (nombreVistaActual != null) {
                breadcrumbs.append(" > ").append(nombreVistaActual);
            }
            
            labelBreadcrumbs.setText(breadcrumbs.toString());
            
            logger.info("Breadcrumbs actualizados: '{}' (visible: {})", 
                labelBreadcrumbs.getText(), breadcrumbsBar.isVisible());
        } else {
            logger.warn("No se pudo actualizar breadcrumbs: breadcrumbsBar es NULL");
        }
    }
    
    /**
     * Obtiene el nombre legible de una vista para los breadcrumbs.
     * 
     * @param vista Vista
     * @return Nombre de la vista o null si es INICIO
     */
    private String obtenerNombreVista(Vista vista) {
        switch (vista) {
            case FACTURACION:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.facturacion");
            case FACTURACION_MESAS_ACTIVAS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.mesas_activas");
            case FACTURACION_DINERO_MESAS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.dinero_mesas");
            case FACTURACION_MESEROS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.meseros");
            case SELECCION_MESERO:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.crear_mesa");
            case USUARIOS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.usuarios");
            case PRODUCTOS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.productos");
            case PRODUCTOS_CREAR_CATEGORIA:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.crear_categoria");
            case PRODUCTOS_CREAR_PRODUCTO:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.crear_producto");
            case PRODUCTOS_LISTADO_CATEGORIAS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.listado_categorias");
            case PRODUCTOS_LISTADO_PRODUCTOS:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.listado_productos");
            case REPORTES:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.reportes");
            case CONFIGURACION:
                return IdiomaUtil.obtener("ctrl.menu.breadcrumb.configuracion");
            case INICIO:
            default:
                return null;
        }
    }
    
    // resetearFacturacion() - Movido a FacturacionController
    
    /**
     * Navega hacia atrás en el historial de vistas.
     * Si no hay historial, va al inicio.
     */
    @FXML
    private void volverAtras() {
        if (!historialVistas.isEmpty()) {
            // Obtener la vista anterior del historial
            Vista vistaAnterior = historialVistas.pop();
            logger.info("Volviendo a vista anterior: {}. Quedan {} vistas en historial", vistaAnterior, historialVistas.size());
            
            // Mostrar la vista anterior SIN agregarla al historial nuevamente
            mostrarVista(vistaAnterior);
        } else {
            // Si no hay historial, volver al inicio
            logger.info("No hay historial, volviendo al inicio");
            mostrarVista(Vista.INICIO);
        }
    }
    
    /**
     * Método público para que los controladores hijos puedan volver atrás.
     * Este método es llamado desde CategoriaCrearController y ProductoCrearController.
     */
    public void volverAtrasPublico() {
        volverAtras();
    }
    
    /**
     * Oculta el header general de la aplicación.
     * Usado por subvistas que tienen su propio header (ej: mesa-detalle).
     */
    public void ocultarHeaderGeneral() {
        if (headerGeneral != null) {
            headerGeneral.setVisible(false);
            headerGeneral.setManaged(false);
        }
    }
    
    /**
     * Muestra el header general de la aplicación.
     * Usado al volver de subvistas con header propio.
     */
    public void mostrarHeaderGeneral() {
        if (headerGeneral != null) {
            headerGeneral.setVisible(true);
            headerGeneral.setManaged(true);
        }
    }
    
    /**
     * Manejador global para la tecla ESC.
     * Llamado por NavegacionUtil cuando se presiona ESC.
     */
    private void manejarNavegacionAtrasGlobal() {
        // Primero verificar si estamos en facturación con una subvista (mesa-detalle)
        if (vistaActual == Vista.FACTURACION) {
            // Buscar el FacturacionController en el contenedor
            for (javafx.scene.Node node : contenedorPrincipal.getChildren()) {
                if (node.getUserData() instanceof com.kipu.cliente.controlador.facturacion.FacturacionController) {
                    com.kipu.cliente.controlador.facturacion.FacturacionController fc = 
                        (com.kipu.cliente.controlador.facturacion.FacturacionController) node.getUserData();
                    if (fc.manejarEscEnDetalleMesa()) {
                        return; // ESC fue manejado por FacturacionController
                    }
                }
            }
        }
        
        if (!historialVistas.isEmpty()) {
            // Si hay historial interno, volvemos atrás en las subvistas
            volverAtras();
        } else {
             // Si no hay historial interno, verificamos si estamos en Inicio
             if (vistaActual == Vista.INICIO) {
                 logger.info("ESC en Menú Principal (Inicio) -> Cerrando sesión");
                 NavegacionUtil.setManejadorAtras(null); // Evitar bucles
                 NavegacionUtil.limpiarUsuario();
                 NavegacionUtil.irALogin();
             } else {
                 // Fallback: Si por alguna razón no estamos en inicio pero no hay historial
                 logger.warn("Estado inconsistente: Vista {} sin historial. Volviendo a Inicio.", vistaActual);
                 mostrarVista(Vista.INICIO);
             }
        }
    }
    
    // ============================================
    // ACCIONES DEL MENÚ PRINCIPAL
    // ============================================
    
    @FXML
    private void irAFacturar() {
        logger.info("Navegando a Facturación");
        navegarA(Vista.FACTURACION);
    }
    
    /**
     * Navega a la subvista de gestión de usuarios.
     * Solo accesible por usuarios con rol ADMIN.
     */
    @FXML
    private void irAUsuarios() {
        navegarA(Vista.USUARIOS);
    }
    
    /**
     * Navega a la subvista de gestión de productos.
     */
    @FXML
    private void irAProductos() {
        logger.info("Navegando a subvista de Productos");
        navegarA(Vista.PRODUCTOS);
    }
    
    @FXML
    private void irAReportes() {
        Stage stage = (Stage) labelNombreUsuario.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, IdiomaUtil.obtener("ctrl.menu.reportes_desarrollo"));
    }
    
    /**
     * Navega a la vista de Configuraciones y Herramientas.
     * Solo accesible por usuarios con rol ADMIN.
     */
    @FXML
    private void irAConfiguracion() {
        logger.info("Navegando a Configuraciones y Herramientas");
        navegarA(Vista.CONFIGURACION);
    }
    
    // ============================================
    // ACCIONES DE FACTURACIÓN (MOVIDAS A FacturacionController)
    // ============================================
    
    /**
     * Cancela la creación de mesa y vuelve a facturación.
     * Referenciado desde panel de selección de mesero en FXML.
     */
    @FXML
    private void cancelarCreacionMesa() {
        volverAtras();
    }
    
    // ============================================
    // CERRAR SESIÓN
    // ============================================
    
    @FXML
    private void cerrarSesion() {
        // CRÍTICO: Detener el fondo animado para evitar memory leak
        if (fondoAnimado != null) {
            logger.info("Deteniendo fondo animado antes de cerrar sesión");
            fondoAnimado.detener();
        }
        
        // Detener el reloj del footer
        if (timelineReloj != null) {
            timelineReloj.stop();
        }
        
        // Limpiar sesión
        NavegacionUtil.limpiarUsuario();
        NavegacionUtil.irALogin();
        
        logger.info("Sesión cerrada");
    }
    
    // ============================================
    // ACCIONES DE PRODUCTOS
    // ============================================
    
    /**
     * Acción: Crear nueva categoría de productos.
     * Carga la subvista del formulario de creación de categoría.
     */
    @FXML
    private void productoCrearCategoria() {
        logger.info("Navegando a formulario de crear categoría");
        navegarA(Vista.PRODUCTOS_CREAR_CATEGORIA);
    }
    
    /**
     * Acción: Crear nuevo producto.
     * Carga la subvista del formulario de creación de producto.
     */
    @FXML
    private void productoCrearProducto() {
        logger.info("Navegando a formulario de crear producto");
        navegarA(Vista.PRODUCTOS_CREAR_PRODUCTO);
    }
    
    /**
     * Acción: Ver listado de todos los productos.
     */
    @FXML
    private void productoListarProductos() {
        logger.info("Opción seleccionada: Listado de Productos");
        navegarA(Vista.PRODUCTOS_LISTADO_PRODUCTOS);
    }
    
    /**
     * Acción: Ver listado de todas las categorías.
     */
    @FXML
    private void productoListarCategorias() {
        logger.info("Navegando a listado de categorías");
        navegarA(Vista.PRODUCTOS_LISTADO_CATEGORIAS);
    }
    
    /**
     * Acción: Control de inventario.
     */
    @FXML
    private void productoControlInventario() {
        logger.info("Navegando a Control de Inventario");
        navegarA(Vista.PRODUCTOS_CONTROL_INVENTARIO);
    }

    /**
     * Obtiene el usuario actualmente autenticado.
     */
    public com.kipu.common.dto.AuthRespuestaDto getUsuarioActual() {
        return usuarioActual;
    }
}
