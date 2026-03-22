/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.configuracion;

import com.baryx.cliente.controlador.MenuPrincipalController;
import com.baryx.cliente.controlador.configuracion.herramientas.*;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;

import java.text.MessageFormat;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador para la vista de Configuraciones y Herramientas.
 *
 * Responsabilidades:
 * - Mostrar un panel estilo Windows 8 con tiles/cards de configuración
 * - Organizar las opciones en secciones temáticas
 * - Delegar la lógica de cada herramienta a su handler específico
 *
 * Arquitectura Delegate:
 * - {@link GestorModales}: infraestructura compartida de modales
 * - {@link ModalHerramienta}: interfaz que implementa cada handler
 * - Handlers en paquete {@code herramientas/}: lógica específica por herramienta
 *
 * Las tiles se agrupan en secciones:
 * - Sistema: Gráficos, idioma, temas, sonidos
 * - Empresa y Locales: Multi-local, compañía, reportes por local
 * - Punto de Venta: Impresora, tickets, métodos de pago, moneda, propinas
 * - Personal: Turnos, permisos, rendimiento
 * - Datos y Reportes: Backup, exportar, auditoría, dashboard
 * - Seguridad: Contraseñas, sesiones, bloqueo
 * - Herramientas: Diagnóstico, actualizaciones, logs, licencia, BD, acerca de
 *
 * Solo accesible por rol ADMIN.
 *
 * @see GestorModales
 * @see ModalHerramienta
 */
public class ConfiguracionHerramientasController implements com.baryx.cliente.controlador.SubvistaController {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionHerramientasController.class);

    // ==================== COMPONENTES FXML ====================

    @FXML private StackPane contenedorRaiz;
    @FXML private VBox seccionSistema;
    @FXML private VBox seccionEmpresa;
    @FXML private VBox seccionPuntoVenta;
    @FXML private VBox seccionPersonal;
    @FXML private VBox seccionDatos;
    @FXML private VBox seccionSeguridad;
    @FXML private VBox seccionHerramientas;

    // ==================== REFERENCIAS ====================

    private MenuPrincipalController menuPrincipal;

    // ==================== HANDLERS DE HERRAMIENTAS ====================

    /** Gestor centralizado de modales (infraestructura compartida) */
    private GestorModales gestorModales;

    /** Handler: Diagnóstico de Red (topología, info de red, clientes) */
    private ModalHerramienta diagnosticoRedHandler;

    /** Handler: Actualizaciones del sistema */
    private ModalHerramienta actualizacionesHandler;

    /** Handler: Licencia y texto legal */
    private ModalHerramienta licenciaHandler;

    /** Handler: Estado de Base de Datos */
    private ModalHerramienta baseDatosHandler;

    /** Handler: Acerca de Baryx */
    private ModalHerramienta acercaDeHandler;

    /** Handler: Gráficos y Rendimiento (animaciones, pantalla completa) */
    private ModalHerramienta graficosHandler;

    /** Handler: Métodos de Pago (CRUD con códigos 00-99) */
    private ModalHerramienta metodosPagoHandler;

    /** Handler: Logs Críticos del Sistema */
    private ModalHerramienta logsCriticosHandler;

    /** Handler: Idioma del Sistema */
    private ModalHerramienta idiomaHandler;

    /** Handler: Respaldo de Datos */
    private ModalHerramienta respaldoDatosHandler;

    /** Handler: Conexión LAN/Nube */
    private ModalHerramienta conexionRedHandler;

    // ==================== INICIALIZACIÓN ====================

    /**
     * Inicializa la vista, crea el gestor de modales y los handlers,
     * y carga todas las tiles en sus respectivas secciones.
     */
    @FXML
    public void initialize() {
        logger.info("Inicializando ConfiguracionHerramientasController");

        // Crear gestor de modales (se vincula al contenedorRaiz para overlays)
        gestorModales = new GestorModales(contenedorRaiz);

        // Crear handlers de herramientas con inyección del gestor
        diagnosticoRedHandler = new DiagnosticoRedHandler(gestorModales);
        actualizacionesHandler = new ActualizacionesHandler(gestorModales);
        licenciaHandler = new LicenciaHandler(gestorModales);
        baseDatosHandler = new BaseDatosHandler(gestorModales);
        acercaDeHandler = new AcercaDeHandler(gestorModales);
        graficosHandler = new GraficosHandler(gestorModales);
        metodosPagoHandler = new MetodosPagoHandler(gestorModales);
        logsCriticosHandler = new LogsCriticosHandler(gestorModales);
        idiomaHandler = new IdiomaHandler(gestorModales);
        respaldoDatosHandler = new RespaldoDatosHandler(gestorModales);
        conexionRedHandler = new ConexionRedHandler(gestorModales);

        cargarTodasLasTiles();
    }

    /**
     * Establece la referencia al controlador principal del menú.
     *
     * @param menuPrincipal Controlador del menú principal
     */
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
    }

    // ==================== CARGA DE TILES ====================

    /**
     * Carga todas las tiles organizadas por sección.
     * Cada tile se construye programáticamente con icono, título, descripción y estado.
     */
    private void cargarTodasLasTiles() {
        // ─── SISTEMA ────────────────────────
        seccionSistema.getChildren().addAll(
            crearTile("icono-cfg-graficos", IdiomaUtil.obtener("ctrl.config.tiles.graficos"),
                IdiomaUtil.obtener("ctrl.config.tiles.graficos_desc"),
                EstadoTile.DISPONIBLE, graficosHandler::abrir),
            crearTile("icono-cfg-idioma", IdiomaUtil.obtener("ctrl.config.tiles.idioma"),
                IdiomaUtil.obtener("ctrl.config.tiles.idioma_desc"),
                EstadoTile.DISPONIBLE, idiomaHandler::abrir),
            crearTile("icono-cfg-tema", IdiomaUtil.obtener("ctrl.config.tiles.tema"),
                IdiomaUtil.obtener("ctrl.config.tiles.tema_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-sonidos", IdiomaUtil.obtener("ctrl.config.tiles.sonidos"),
                IdiomaUtil.obtener("ctrl.config.tiles.sonidos_desc"),
                EstadoTile.PROXIMO, null)
        );

        // ─── EMPRESA Y LOCALES ────────────────────────
        seccionEmpresa.getChildren().addAll(
            crearTile("icono-cfg-locales", IdiomaUtil.obtener("ctrl.config.tiles.locales"),
                IdiomaUtil.obtener("ctrl.config.tiles.locales_desc"),
                EstadoTile.EN_DESARROLLO, null),
            crearTile("icono-cfg-reportes-local", IdiomaUtil.obtener("ctrl.config.tiles.reportes_local"),
                IdiomaUtil.obtener("ctrl.config.tiles.reportes_local_desc"),
                EstadoTile.EN_DESARROLLO, null),
            crearTile("icono-cfg-negocio", IdiomaUtil.obtener("ctrl.config.tiles.negocio"),
                IdiomaUtil.obtener("ctrl.config.tiles.negocio_desc"),
                EstadoTile.PROXIMO, null)
        );

        // ─── PUNTO DE VENTA ────────────────────────
        seccionPuntoVenta.getChildren().addAll(
            crearTile("icono-cfg-impresora", IdiomaUtil.obtener("ctrl.config.tiles.impresora"),
                IdiomaUtil.obtener("ctrl.config.tiles.impresora_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-ticket", IdiomaUtil.obtener("ctrl.config.tiles.ticket"),
                IdiomaUtil.obtener("ctrl.config.tiles.ticket_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-pago", IdiomaUtil.obtener("ctrl.config.tiles.pago"),
                IdiomaUtil.obtener("ctrl.config.tiles.pago_desc"),
                EstadoTile.DISPONIBLE, metodosPagoHandler::abrir),
            crearTile("icono-cfg-moneda", IdiomaUtil.obtener("ctrl.config.tiles.moneda"),
                IdiomaUtil.obtener("ctrl.config.tiles.moneda_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-propinas", IdiomaUtil.obtener("ctrl.config.tiles.propinas"),
                IdiomaUtil.obtener("ctrl.config.tiles.propinas_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-facturas", IdiomaUtil.obtener("ctrl.config.tiles.facturas"),
                IdiomaUtil.obtener("ctrl.config.tiles.facturas_desc"),
                EstadoTile.PROXIMO, null)
        );

        // ─── PERSONAL ────────────────────────
        seccionPersonal.getChildren().addAll(
            crearTile("icono-cfg-turnos", IdiomaUtil.obtener("ctrl.config.tiles.turnos"),
                IdiomaUtil.obtener("ctrl.config.tiles.turnos_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-permisos", IdiomaUtil.obtener("ctrl.config.tiles.permisos"),
                IdiomaUtil.obtener("ctrl.config.tiles.permisos_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-rendimiento", IdiomaUtil.obtener("ctrl.config.tiles.rendimiento"),
                IdiomaUtil.obtener("ctrl.config.tiles.rendimiento_desc"),
                EstadoTile.EN_DESARROLLO, null)
        );

        // ─── DATOS Y REPORTES ────────────────────────
        seccionDatos.getChildren().addAll(
            crearTile("icono-cfg-backup", IdiomaUtil.obtener("ctrl.config.tiles.backup"),
                IdiomaUtil.obtener("ctrl.config.tiles.backup_desc"),
                EstadoTile.DISPONIBLE, respaldoDatosHandler::abrir),
            crearTile("icono-cfg-exportar", IdiomaUtil.obtener("ctrl.config.tiles.exportar"),
                IdiomaUtil.obtener("ctrl.config.tiles.exportar_desc"),
                EstadoTile.EN_DESARROLLO, null),
            crearTile("icono-cfg-auditoria", IdiomaUtil.obtener("ctrl.config.tiles.auditoria"),
                IdiomaUtil.obtener("ctrl.config.tiles.auditoria_desc"),
                EstadoTile.EN_DESARROLLO, null),
            crearTile("icono-cfg-dashboard", IdiomaUtil.obtener("ctrl.config.tiles.dashboard"),
                IdiomaUtil.obtener("ctrl.config.tiles.dashboard_desc"),
                EstadoTile.EN_DESARROLLO, null)
        );

        // ─── SEGURIDAD ────────────────────────
        seccionSeguridad.getChildren().addAll(
            crearTile("icono-cfg-contrasenas", IdiomaUtil.obtener("ctrl.config.tiles.contrasenas"),
                IdiomaUtil.obtener("ctrl.config.tiles.contrasenas_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-sesiones", IdiomaUtil.obtener("ctrl.config.tiles.sesiones"),
                IdiomaUtil.obtener("ctrl.config.tiles.sesiones_desc"),
                EstadoTile.PROXIMO, null),
            crearTile("icono-cfg-bloqueo", IdiomaUtil.obtener("ctrl.config.tiles.bloqueo"),
                IdiomaUtil.obtener("ctrl.config.tiles.bloqueo_desc"),
                EstadoTile.PROXIMO, null)
        );

        // ─── HERRAMIENTAS ────────────────────────
        seccionHerramientas.getChildren().addAll(
            crearTile("icono-cfg-red", IdiomaUtil.obtener("ctrl.config.tiles.conexion"),
                IdiomaUtil.obtener("ctrl.config.tiles.conexion_desc"),
                EstadoTile.DISPONIBLE, conexionRedHandler::abrir),
            crearTile("icono-cfg-red", IdiomaUtil.obtener("ctrl.config.tiles.red"),
                IdiomaUtil.obtener("ctrl.config.tiles.red_desc"),
                EstadoTile.DISPONIBLE, diagnosticoRedHandler::abrir),
            crearTile("icono-cfg-actualizaciones", IdiomaUtil.obtener("ctrl.config.tiles.actualizaciones"),
                IdiomaUtil.obtener("ctrl.config.tiles.actualizaciones_desc"),
                EstadoTile.DISPONIBLE, actualizacionesHandler::abrir),
            crearTile("icono-cfg-logs", IdiomaUtil.obtener("ctrl.config.tiles.logs"),
                IdiomaUtil.obtener("ctrl.config.tiles.logs_desc"),
                EstadoTile.DISPONIBLE, logsCriticosHandler::abrir),
            crearTile("icono-cfg-licencia", IdiomaUtil.obtener("ctrl.config.tiles.licencia"),
                IdiomaUtil.obtener("ctrl.config.tiles.licencia_desc"),
                EstadoTile.DISPONIBLE, licenciaHandler::abrir),
            crearTile("icono-cfg-basedatos", IdiomaUtil.obtener("ctrl.config.tiles.basedatos"),
                IdiomaUtil.obtener("ctrl.config.tiles.basedatos_desc"),
                EstadoTile.DISPONIBLE, baseDatosHandler::abrir),
            crearTile("icono-cfg-acerca", IdiomaUtil.obtener("ctrl.config.tiles.acerca"),
                IdiomaUtil.obtener("ctrl.config.tiles.acerca_desc"),
                EstadoTile.DISPONIBLE, acercaDeHandler::abrir)
        );

        logger.info("Todas las tiles cargadas exitosamente");
    }

    // ==================== CONSTRUCCIÓN DE TILES ====================

    /**
     * Estado de disponibilidad de cada tile.
     * Determina el indicador visual y si el tile es interactivo.
     */
    private enum EstadoTile {
        /** Funcionalidad implementada y accesible */
        DISPONIBLE,
        /** En desarrollo activo, no funcional aún */
        EN_DESARROLLO,
        /** Planificado para futuras versiones */
        PROXIMO
    }

    /**
     * Crea una tile (card) estilo Windows 8 para el grid de configuración.
     * 
     * Layout de cada tile:
     * - Fila superior: icono SVG (izquierda) + badge de estado (derecha)
     * - Debajo: título con wrap de texto
     * - Debajo: descripción breve con wrap de texto
     * 
     * @param claseIcono  Clase CSS del icono SVG (define la forma fx-shape)
     * @param titulo      Nombre de la configuración
     * @param descripcion Descripción breve de la funcionalidad
     * @param estado      Estado de implementación del tile
     * @param accion      Acción al hacer clic (null si no implementado)
     * @return VBox con el tile construido
     */
    private VBox crearTile(String claseIcono, String titulo, String descripcion,
                           EstadoTile estado, Runnable accion) {

        VBox tile = new VBox(8);
        tile.setAlignment(Pos.TOP_LEFT);
        tile.setPadding(new Insets(14));
        tile.setPrefSize(190, 160);
        tile.setMinSize(190, 160);
        tile.setMaxSize(190, 160);
        tile.getStyleClass().add("config-tile");
        // Evitar que el VBox padre estire la tile cuando hay espacio sobrante
        VBox.setVgrow(tile, Priority.NEVER);

        // ── Fila superior: icono izquierda + badge derecha ──
        Region iconoRegion = new Region();
        iconoRegion.getStyleClass().addAll("config-tile-icono-svg", claseIcono);
        iconoRegion.setMinSize(24, 24);
        iconoRegion.setPrefSize(24, 24);
        iconoRegion.setMaxSize(24, 24);

        Label badge = new Label();
        badge.getStyleClass().add("config-tile-badge");

        switch (estado) {
            case DISPONIBLE:
                tile.getStyleClass().add("config-tile-disponible");
                badge.setText(IdiomaUtil.obtener("ctrl.config.badge.disponible"));
                badge.getStyleClass().add("config-badge-disponible");
                break;
            case EN_DESARROLLO:
                tile.getStyleClass().add("config-tile-desarrollo");
                badge.setText(IdiomaUtil.obtener("ctrl.config.badge.desarrollo"));
                badge.getStyleClass().add("config-badge-desarrollo");
                break;
            case PROXIMO:
                tile.getStyleClass().add("config-tile-proximo");
                badge.setText(IdiomaUtil.obtener("ctrl.config.badge.proximo"));
                badge.getStyleClass().add("config-badge-proximo");
                break;
        }

        Region espaciadorFila = new Region();
        HBox.setHgrow(espaciadorFila, Priority.ALWAYS);

        HBox filaSuperior = new HBox(6);
        filaSuperior.setAlignment(Pos.CENTER_LEFT);
        filaSuperior.getChildren().addAll(iconoRegion, espaciadorFila, badge);

        // ── Título (debajo del icono, con wrap) ──
        Label labelTitulo = new Label(titulo);
        labelTitulo.getStyleClass().add("config-tile-titulo");
        labelTitulo.setWrapText(true);
        labelTitulo.setMaxWidth(160);

        // ── Descripción ──
        Label labelDescripcion = new Label(descripcion);
        labelDescripcion.getStyleClass().add("config-tile-descripcion");
        labelDescripcion.setWrapText(true);
        labelDescripcion.setMaxWidth(160);

        tile.getChildren().addAll(filaSuperior, labelTitulo, labelDescripcion);

        // Acción al hacer clic
        tile.setOnMouseClicked(e -> {
            if (accion != null) {
                accion.run();
            } else {
                // Mostrar mensaje según estado
                Stage stage = (Stage) tile.getScene().getWindow();
                switch (estado) {
                    case EN_DESARROLLO:
                        NotificacionUtil.mostrarInfo(stage,
                            MessageFormat.format(IdiomaUtil.obtener("ctrl.config.tile.en_desarrollo"), titulo));
                        break;
                    case PROXIMO:
                        NotificacionUtil.mostrarInfo(stage,
                            MessageFormat.format(IdiomaUtil.obtener("ctrl.config.tile.proximo"), titulo));
                        break;
                    default:
                        break;
                }
            }
            e.consume();
        });

        return tile;
    }

}
