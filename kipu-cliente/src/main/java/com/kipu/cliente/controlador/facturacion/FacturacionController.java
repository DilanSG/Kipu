/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.facturacion;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.cliente.controlador.MenuPrincipalController;
import com.kipu.cliente.modelo.Mesa;
import com.kipu.cliente.utilidad.NavegacionUtil;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.cliente.utilidad.TecladoVirtualSimple;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//Controlador para la subvista de Facturación.
//-Gestiona las tabs de facturación (Mesas Activas, Dinero, Meseros)
//-Controla la visibilidad de tabs según el rol del usuario
//-Gestiona búsqueda y creación de mesas
//-Coordina con otras subvistas (meseros, mesa-detalle)

public class FacturacionController implements com.kipu.cliente.controlador.SubvistaController {

    private static final Logger logger = LoggerFactory.getLogger(FacturacionController.class);

    @FXML
    private VBox panelPrincipal;
    @FXML
    private VBox capaTabsFlotante;
    @FXML
    private Region zonaTriggerTabs;
    @FXML
    private HBox contenedorTabs;
    @FXML
    private Button btnMesasActivas;
    @FXML
    private Button btnDineroMesas;
    @FXML
    private Button btnMeseros;
    @FXML
    private VBox panelBusquedaMesas;
    @FXML
    private TextField txtBuscarMesa;
    @FXML
    private ScrollPane scrollMesas;
    @FXML
    private GridPane gridMesas;
    @FXML
    private StackPane contenedorSubvistas;

    private AuthRespuestaDto usuarioActual;

    private MenuPrincipalController menuPrincipal;

    private MesaDetalleController controladorMesaDetalle;

    private com.kipu.cliente.servicio.MesaServicio mesaServicio;

    /** Indica si las tabs están visibles actualmente */
    private boolean tabsVisibles = false;

    /** Timeline para animar la aparición/desaparición de las tabs */
    private Timeline timelineMostrarTabs;
    private Timeline timelineOcultarTabs;

    /** Pausa antes de ocultar las tabs (evita parpadeo por movimientos rápidos del mouse) */
    private PauseTransition pausaAnteDeOcultar;

    @FXML
    public void initialize() {
        logger.info("Inicializando vista de facturación");

        usuarioActual = NavegacionUtil.getUsuarioActual();

        if (usuarioActual == null) {
            logger.error("Usuario actual no encontrado");
            NavegacionUtil.irALogin();
            return;
        }

        mesaServicio = new com.kipu.cliente.servicio.MesaServicio(usuarioActual.getToken());

        configurarVisibilidadPorRol();

        scrollMesas.setVisible(false);
        scrollMesas.setManaged(false);
        
        // Configurar animación de tabs auto-ocultables
        configurarAnimacionTabs();
        
        // Activar teclado virtual para el campo de búsqueda
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtBuscarMesa));
    }

    /**
     * Configura la animación de las tabs flotantes para que se oculten automáticamente.
     * Las tabs flotan como overlay (en capaTabsFlotante) sobre el contenido principal,
     * por lo que su aparición/desaparición NO desplaza el input de búsqueda.
     * Solo se animan opacidad y translateY (sin cambiar altura).
     */
    private void configurarAnimacionTabs() {
        // Estado inicial: tabs ocultas (transparentes y desplazadas hacia arriba)
        contenedorTabs.setOpacity(0);
        contenedorTabs.setTranslateY(-15);
        // mouseTransparent para que no bloquee clicks cuando está oculto
        contenedorTabs.setMouseTransparent(true);
        tabsVisibles = false;

        // La capa flotante no debe interceptar clicks en zonas vacías
        capaTabsFlotante.setPickOnBounds(false);

        // Interpolar suave tipo ease-out
        Interpolator suave = Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0);

        // Timeline para MOSTRAR las tabs (deslizar hacia abajo + fade in)
        timelineMostrarTabs = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(contenedorTabs.opacityProperty(), 0),
                new KeyValue(contenedorTabs.translateYProperty(), -15)
            ),
            new KeyFrame(Duration.millis(250),
                new KeyValue(contenedorTabs.opacityProperty(), 1.0, suave),
                new KeyValue(contenedorTabs.translateYProperty(), 0, suave)
            )
        );
        timelineMostrarTabs.setOnFinished(e -> contenedorTabs.setMouseTransparent(false));

        // Timeline para OCULTAR las tabs (deslizar hacia arriba + fade out)
        timelineOcultarTabs = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(contenedorTabs.opacityProperty(), 1.0),
                new KeyValue(contenedorTabs.translateYProperty(), 0)
            ),
            new KeyFrame(Duration.millis(220),
                new KeyValue(contenedorTabs.opacityProperty(), 0, suave),
                new KeyValue(contenedorTabs.translateYProperty(), -15, suave)
            )
        );
        timelineOcultarTabs.setOnFinished(e -> contenedorTabs.setMouseTransparent(true));

        // Pausa de 400ms antes de ocultar, para que el mouse pueda moverse
        // entre el trigger y las tabs sin provocar parpadeo
        pausaAnteDeOcultar = new PauseTransition(Duration.millis(400));
        pausaAnteDeOcultar.setOnFinished(event -> ejecutarOcultarTabs());

        // Al entrar el mouse en la zona trigger → mostrar tabs y cancelar ocultación pendiente
        zonaTriggerTabs.setOnMouseEntered(event -> {
            cancelarOcultacionPendiente();
            mostrarTabs();
        });

        // Al salir de la zona trigger → programar ocultación con delay
        zonaTriggerTabs.setOnMouseExited(event -> programarOcultarTabs());

        // Al entrar el mouse en las tabs → cancelar ocultación y mantener visibles
        contenedorTabs.setOnMouseEntered(event -> {
            cancelarOcultacionPendiente();
            mostrarTabs();
        });

        // Al salir el mouse de las tabs → programar ocultación con delay
        contenedorTabs.setOnMouseExited(event -> programarOcultarTabs());
    }

    /**
     * Muestra las tabs con animación de deslizamiento hacia abajo.
     * Cancela cualquier ocultación pendiente y ejecuta la animación de aparición.
     */
    private void mostrarTabs() {
        if (tabsVisibles) return;
        tabsVisibles = true;
        timelineOcultarTabs.stop();
        timelineMostrarTabs.playFromStart();
    }

    /**
     * Programa la ocultación de las tabs con un delay de 350ms.
     * Esto evita el parpadeo cuando el mouse se mueve entre la zona trigger
     * y los botones de las tabs (hay un pequeño gap de layout entre ambos).
     */
    private void programarOcultarTabs() {
        pausaAnteDeOcultar.playFromStart();
    }

    /**
     * Cancela una ocultación pendiente (si la hay).
     * Se llama cuando el mouse re-entra en la zona trigger o en las tabs.
     */
    private void cancelarOcultacionPendiente() {
        pausaAnteDeOcultar.stop();
    }

    /**
     * Ejecuta la animación de ocultar tabs (llamada tras la pausa).
     * Si las tabs ya están ocultas, no hace nada.
     */
    private void ejecutarOcultarTabs() {
        if (!tabsVisibles) return;
        tabsVisibles = false;
        timelineMostrarTabs.stop();
        timelineOcultarTabs.playFromStart();
    }

    private void configurarVisibilidadPorRol() {
        String rol = usuarioActual.getRol();
        logger.info("Configurando vista para rol: {}", rol);

        if (Constantes.Roles.MESERO.equals(rol)) {
            btnMeseros.setVisible(false);
            btnMeseros.setManaged(false);
            btnMesasActivas.setVisible(true);
            btnMesasActivas.setManaged(true);
            btnDineroMesas.setVisible(true);
            btnDineroMesas.setManaged(true);

        } else {
            btnMesasActivas.setVisible(true);
            btnMesasActivas.setManaged(true);
            btnDineroMesas.setVisible(true);
            btnDineroMesas.setManaged(true);
            btnMeseros.setVisible(true);
            btnMeseros.setManaged(true);
        }
    }

    @FXML
    private void mostrarMesasActivas() {
        Long idMeseroFiltro = Constantes.Roles.MESERO.equals(usuarioActual.getRol()) 
                ? usuarioActual.getIdUsuario() 
                : null;

        mesaServicio.obtenerMesasActivasAsync(idMeseroFiltro)
                .thenAcceptAsync(mesas -> {
                    if (mesas == null || mesas.isEmpty()) {
                        Stage stage = (Stage) btnMesasActivas.getScene().getWindow();
                        NotificacionUtil.mostrarInfo(stage, IdiomaUtil.obtener("ctrl.facturacion.sin_mesas"));
                    } else {
                        cargarSubvistaMesasActivas();
                    }
                }, javafx.application.Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error verificando mesas activas", ex);
                    javafx.application.Platform.runLater(() -> 
                        NotificacionUtil.mostrarError((Stage) btnMesasActivas.getScene().getWindow(), java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.facturacion.error_verificar"), ex.getCause().getMessage()))
                    );
                    return null;
                });
    }
    
    private void cargarSubvistaMesasActivas() {
        try {            
            btnMesasActivas.getStyleClass().add("tab-active");
            btnDineroMesas.getStyleClass().remove("tab-active");
            btnMeseros.getStyleClass().remove("tab-active");
            panelPrincipal.setVisible(false);
            panelPrincipal.setManaged(false);
            contenedorSubvistas.getChildren().clear();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/subvistas/facturacion/mesas-activas.fxml"));
            loader.setResources(com.kipu.cliente.utilidad.IdiomaUtil.obtenerBundle());
            Pane vistaMesas = loader.load();
            MesasActivasController controller = loader.getController();
            controller.setControladorPadre(this);
            controller.cargarMesas();

            contenedorSubvistas.getChildren().add(vistaMesas);
            contenedorSubvistas.setVisible(true);
            contenedorSubvistas.setManaged(true);
            
        } catch (IOException e) {
            logger.error("Error al cargar subvista de mesas activas", e);
            Stage stage = (Stage) btnMesasActivas.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.facturacion.error_vista_mesas"), e.getMessage()));
            panelPrincipal.setVisible(true);
            panelPrincipal.setManaged(true);
        }
    }

    @FXML
    private void mostrarDineroMesas() {
        
        btnMesasActivas.getStyleClass().remove("tab-active");
        btnDineroMesas.getStyleClass().add("tab-active");
        btnMeseros.getStyleClass().remove("tab-active");
        
        contenedorSubvistas.setVisible(false);
        contenedorSubvistas.setManaged(false);
        
        panelPrincipal.setVisible(true);
        panelPrincipal.setManaged(true);
        
        if (scrollMesas != null) {
            scrollMesas.setVisible(false);
            scrollMesas.setManaged(false);
        }
        
        // TODO: Implementar vista de dinero en mesas
        Stage stage = (Stage) btnDineroMesas.getScene().getWindow();
        NotificacionUtil.mostrarInfo(stage, Constantes.Mensajes.CARGANDO_DINERO_MESAS);
    }

    @FXML
    private void mostrarMeseros() {

        panelPrincipal.setVisible(false);
        panelPrincipal.setManaged(false);

        contenedorSubvistas.setVisible(true);
        contenedorSubvistas.setManaged(true);

        cargarSubvistaMeseros();
    }

    @FXML
    private void buscarMesa() {
        String nombreMesa = txtBuscarMesa.getText().trim();

        if (nombreMesa.isEmpty()) {
            Stage stage = (Stage) txtBuscarMesa.getScene().getWindow();
            NotificacionUtil.mostrarAdvertencia(stage, Constantes.Mensajes.INGRESE_NOMBRE_MESA);
            return;
        }

        mesaServicio.buscarMesaPorNumeroAsync(nombreMesa)
                .thenAcceptAsync(mesaExistente -> {
                    if (mesaExistente != null) {
                        // Si el usuario es MESERO, verificar que la mesa le pertenece.
                        // Un mesero NO puede acceder a mesas asignadas a otro mesero.
                        if (Constantes.Roles.MESERO.equals(usuarioActual.getRol())) {
                            Long idMeseroMesa = (mesaExistente.getMesero() != null)
                                    ? mesaExistente.getMesero().getId()
                                    : mesaExistente.getIdMesero();
                            if (idMeseroMesa != null && !idMeseroMesa.equals(usuarioActual.getIdUsuario())) {
                                // La mesa pertenece a otro mesero — bloquear acceso
                                String nombreMesero = (mesaExistente.getMesero() != null)
                                        ? mesaExistente.getMesero().getNombre()
                                        : "otro mesero";
                                Stage stage = (Stage) txtBuscarMesa.getScene().getWindow();
                                NotificacionUtil.mostrarAdvertencia(stage,
                                        "La mesa \"" + nombreMesa + "\" ya está asignada a " + nombreMesero + ".\n" +
                                        "Por favor, utiliza otro nombre de mesa.");
                                logger.warn("Mesero {} intentó acceder a mesa '{}' asignada a mesero ID {}",
                                        usuarioActual.getNombreCompleto(), nombreMesa, idMeseroMesa);
                                return;
                            }
                        }
                        txtBuscarMesa.clear();
                        Mesa mesaModelo = convertirDtoAMesa(mesaExistente);
                        abrirDetalleMesaObjeto(mesaModelo);
                    } else {
                        String rol = usuarioActual.getRol();
                        if (Constantes.Roles.MESERO.equals(rol)) {
                            crearMesaDirecta(nombreMesa);
                        } else {
                            mostrarSeleccionMesero(nombreMesa);
                        }
                    }
                }, javafx.application.Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error buscando mesa", ex);
                    javafx.application.Platform.runLater(() -> {
                        Stage stage = (Stage) txtBuscarMesa.getScene().getWindow();
                        NotificacionUtil.mostrarError(stage, java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.facturacion.error_buscar_mesa"), ex.getCause().getMessage()));
                    });
                    return null;
                });
    }

    private void crearMesaDirecta(String nombreMesa) {
        mesaServicio.crearOObtenerMesaAsync(nombreMesa, usuarioActual.getIdUsuario())
                .thenAcceptAsync(mesaDto -> {
                    logger.info("Mesa {} obtenida/creada: ID {}", mesaDto.getNumeroMesa(), mesaDto.getIdMesa());

                    Mesa mesaModelo = convertirDtoAMesa(mesaDto, usuarioActual.getNombreCompleto());

                    txtBuscarMesa.clear();
                    abrirDetalleMesaObjeto(mesaModelo);
                }, javafx.application.Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error creando mesa", ex);
                    javafx.application.Platform.runLater(() -> 
                        NotificacionUtil.mostrarError((Stage) txtBuscarMesa.getScene().getWindow(),
                                java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.facturacion.error_crear_mesa"), ex.getCause().getMessage())));
                    return null;
                });
    }

    private Mesa convertirDtoAMesa(com.kipu.common.dto.MesaConPedidoDto dto) {
        return convertirDtoAMesa(dto, null);
    }

    private Mesa convertirDtoAMesa(com.kipu.common.dto.MesaConPedidoDto dto, String nombreMeseroFallback) {
        String nombreMesero;
        if (dto.getMesero() != null) {
            nombreMesero = dto.getMesero().getNombre();
        } else if (nombreMeseroFallback != null) {
            nombreMesero = nombreMeseroFallback;
        } else {
            nombreMesero = IdiomaUtil.obtener("ctrl.facturacion.sin_mesero");
        }
        Long idMesero = (dto.getMesero() != null) ? dto.getMesero().getId() : dto.getIdMesero();

        return new Mesa(
                dto.getIdMesa(),
                dto.getNumeroMesa(),
                nombreMesero,
                idMesero,
                dto.getEstado());
    }
    public void meseroSeleccionado(Mesa mesaTemp) {
        mesaServicio.crearOObtenerMesaAsync(mesaTemp.getNombre(), mesaTemp.getMeseroId())
                .thenAcceptAsync(mesaDto -> {
                    Mesa mesaReal = convertirDtoAMesa(mesaDto, mesaTemp.getMeseroNombre());

                    txtBuscarMesa.clear();

                    contenedorSubvistas.setVisible(false);
                    contenedorSubvistas.setManaged(false);
                    panelPrincipal.setVisible(true);
                    panelPrincipal.setManaged(true);

                    abrirDetalleMesaObjeto(mesaReal);
                }, javafx.application.Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error asignando mesa", ex);
                    javafx.application.Platform.runLater(() -> 
                        NotificacionUtil.mostrarError((Stage) panelPrincipal.getScene().getWindow(),
                                java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.facturacion.error_asignar_mesa"), ex.getCause().getMessage())));
                    return null;
                });
    }

    public void abrirDetalleMesaObjeto(Mesa mesa) {
        logger.info("Abriendo detalle de mesa: {}", mesa.getNombre());

        try {
            panelPrincipal.setVisible(false);
            panelPrincipal.setManaged(false);

            contenedorSubvistas.getChildren().clear();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/vista/subvistas/facturacion/mesa-detalle.fxml"));
            loader.setResources(com.kipu.cliente.utilidad.IdiomaUtil.obtenerBundle());
            Pane vistaDetalle = loader.load();

            MesaDetalleController controller = loader.getController();
            controller.setMesa(mesa);
            controller.setControladorPadre(this);
            controller.configurarTeclaEsc();
            controladorMesaDetalle = controller;

            contenedorSubvistas.getChildren().add(vistaDetalle);
            contenedorSubvistas.setVisible(true);
            contenedorSubvistas.setManaged(true);

            if (menuPrincipal != null) {
                menuPrincipal.ocultarHeaderGeneral();
            }

            logger.info("Subvista de detalle de mesa cargada exitosamente");

        } catch (IOException e) {
            logger.error("Error al cargar subvista de detalle de mesa", e);
            Stage stage = (Stage) txtBuscarMesa.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, Constantes.Mensajes.ERROR_CARGAR_DETALLE_MESA);

            panelPrincipal.setVisible(true);
            panelPrincipal.setManaged(true);
        }
    }

    private void cargarSubvistaMeseros() {
        try {
            contenedorSubvistas.getChildren().clear();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/subvistas/meseros/meseros.fxml"));
            loader.setResources(com.kipu.cliente.utilidad.IdiomaUtil.obtenerBundle());
            Pane vistaMeseros = loader.load();

            contenedorSubvistas.getChildren().add(vistaMeseros);

            logger.info("Subvista de meseros cargada exitosamente");

        } catch (IOException e) {
            logger.error("Error al cargar subvista de meseros", e);
            Stage stage = (Stage) btnMeseros.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, Constantes.Mensajes.ERROR_CARGAR_VISTA_MESEROS);
        }
    }

    private void mostrarSeleccionMesero(String nombreMesa) {
        try {
            logger.info("Mostrando selección de mesero para mesa: {}", nombreMesa);

            panelPrincipal.setVisible(false);
            panelPrincipal.setManaged(false);

            contenedorSubvistas.getChildren().clear();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/vista/subvistas/facturacion/seleccion-mesero.fxml"));
            loader.setResources(com.kipu.cliente.utilidad.IdiomaUtil.obtenerBundle());
            Pane vistaSeleccion = loader.load();

            SeleccionMeseroController controller = loader.getController();
            controller.setNombreMesa(nombreMesa);
            controller.setControladorPadre(this);

            contenedorSubvistas.getChildren().add(vistaSeleccion);
            contenedorSubvistas.setVisible(true);
            contenedorSubvistas.setManaged(true);

            logger.info("Subvista de selección de mesero cargada exitosamente");

        } catch (IOException e) {
            logger.error("Error al cargar subvista de selección de mesero", e);
            Stage stage = (Stage) txtBuscarMesa.getScene().getWindow();
            NotificacionUtil.mostrarError(stage, Constantes.Mensajes.ERROR_CARGAR_SELECCION_MESERO);
            
            panelPrincipal.setVisible(true);
            panelPrincipal.setManaged(true);
        }
    }

    public void cancelarCreacionMesa() {
    
        txtBuscarMesa.clear();

        contenedorSubvistas.setVisible(false);
        contenedorSubvistas.setManaged(false);
        panelPrincipal.setVisible(true);
        panelPrincipal.setManaged(true);
    }
    
    public void volverDeSubvista() {
        
        contenedorSubvistas.setVisible(false);
        contenedorSubvistas.setManaged(false);
        contenedorSubvistas.getChildren().clear();
        panelPrincipal.setVisible(true);
        panelPrincipal.setManaged(true);
        
        if (menuPrincipal != null) {
            menuPrincipal.mostrarHeaderGeneral();
        }

        btnMesasActivas.getStyleClass().remove("tab-active");
        btnDineroMesas.getStyleClass().remove("tab-active");
        btnMeseros.getStyleClass().remove("tab-active");
    }

    public void volverDeDetalleMesa() {
        volverDeSubvista();
    }

    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
    }

    public boolean manejarEscEnDetalleMesa() {
        if (contenedorSubvistas.isVisible()) {
            // Delegar primero a MesaDetalleController para navegación entre paneles
            if (controladorMesaDetalle != null && controladorMesaDetalle.manejarEsc()) {
                return true;
            }
            logger.info("ESC detectado en subvista, volviendo a facturación");
            controladorMesaDetalle = null;
            volverDeSubvista();
            return true;
        }
        return false;
    }
}
