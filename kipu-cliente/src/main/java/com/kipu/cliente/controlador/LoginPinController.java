/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LoginDto;
import com.kipu.common.dto.LoginPinDto;
import com.kipu.common.dto.VerificarCodigoDto;
import com.kipu.cliente.servicio.AutenticacionServicio;
import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.utilidad.NavegacionUtil;
import com.kipu.cliente.utilidad.MonitorConexion;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.TecladoVirtualSimple;
import com.kipu.cliente.utilidad.TecladoNumerico;
import com.kipu.cliente.componente.FondoAnimado;
import com.kipu.cliente.componente.MotorAnimaciones;
import com.kipu.cliente.componente.PanelConexionRed;
import com.kipu.cliente.componente.ToggleSwitch;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
import javafx.animation.ScaleTransition;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Controlador unificado para la vista de login del sistema Kipu.
 * 
 * Maneja DOS subvistas dentro del mismo FXML (login-pin.fxml):
 * 
 * 1. <b>Login PIN</b> (visible por defecto):
 *    - Para cajeros y meseros
 *    - Flujo de 2 pasos: Código empleado (2 dígitos) → PIN (4 dígitos)
 *    - El código se verifica contra el servidor antes de pedir el PIN
 * 
 * 2. <b>Login Admin</b> (accesible con botón "Login Admin"):
 *    - Para administradores
 *    - Flujo tradicional: usuario + contraseña
 *    - Incluye toggle de Host Mode para servidor embebido
 * 
 * Las vistas se alternan cambiando visibilidad (visible/managed) sin cambiar
 * de escena, evitando problemas de redimensionamiento de ventana.
 * 
 * Elementos compartidos (siempre visibles independientemente de la vista):
 * - Fondo animado (partículas doradas)
 * - Botones admin y cerrar (esquina superior derecha)
 * - Indicador de conexión (esquina inferior derecha)
 * - Footer con versión (centro inferior)
 * 
 * @see AutenticacionServicio para la comunicación con el servidor
 * @see MonitorConexion para el indicador de estado de red
 * @see ConfiguracionCliente para la persistencia de Host Mode
 */
public class LoginPinController {

    private static final Logger logger = LoggerFactory.getLogger(LoginPinController.class);

    // =========================================================================
    // CAMPOS FXML - COMPONENTES COMPARTIDOS
    // =========================================================================

    @FXML private FondoAnimado fondoAnimado;
    @FXML private ImageView logoLogin;
    @FXML private Label labelVersion;
    @FXML private HBox indicadorConexionLogin;

    // =========================================================================
    // CAMPOS FXML - VISTA PIN (código + PIN)
    // =========================================================================

    /** Contenedor raíz de la vista PIN (visible por defecto) */
    @FXML private VBox vistaPIN;
    /** Título mostrado en el paso 1: "Ingrese su código de empleado" */
    @FXML private VBox contenedorTituloCodigo;
    /** Título mostrado en el paso 2: "Bienvenido [nombre], Ingrese su PIN" */
    @FXML private VBox contenedorTituloPin;
    @FXML private javafx.scene.text.Text textoNombreEmpleado;
    /** Formulario del paso 1 con campo de código */
    @FXML private VBox formularioCodigo;
    /** Formulario del paso 2 con campo de PIN */
    @FXML private VBox formularioPin;
    @FXML private TextField campoCodigo;
    @FXML private PasswordField campoPin;
    @FXML private Button botonLoginPin;
    @FXML private Label labelErrorPin;
    @FXML private Label labelCargaPin;
    @FXML private HBox contenedorCargaPin;
    @FXML private HBox contenedorErrorPin;
    @FXML private HBox contenedorExitoPin;
    @FXML private Label labelExitoPin;
    @FXML private ProgressIndicator indicadorCargaPin;

    // =========================================================================
    // CAMPOS FXML - VISTA ADMIN (usuario + contraseña)
    // =========================================================================

    /** Contenedor raíz de la vista Admin (oculta por defecto) */
    @FXML private VBox vistaAdmin;
    @FXML private TextField campoUsuario;
    @FXML private PasswordField campoContrasena;
    @FXML private Button botonLoginAdmin;
    @FXML private Label labelErrorAdmin;
    @FXML private HBox contenedorCargaAdmin;
    @FXML private HBox contenedorErrorAdmin;
    @FXML private HBox contenedorExitoAdmin;
    @FXML private Label labelExitoAdmin;
    @FXML private ProgressIndicator indicadorCargaAdmin;

    // =========================================================================
    // CAMPOS FXML - HOST MODE (toggle en esquina inferior izquierda)
    // =========================================================================

    /** Contenedor del toggle y panel de configuración, solo visible en vistaAdmin */
    @FXML private VBox contenedorToggleHost;
    @FXML private ToggleSwitch toggleHostMode;
    @FXML private Label labelHostMode;

    // =========================================================================
    // CAMPOS FXML - CONFIGURACIÓN DE SERVIDOR REMOTO
    // =========================================================================

    /** Panel con campos IP/Puerto/Nombre, visible cuando Host Mode está desactivado */
    @FXML private VBox panelConfigServidor;
    /** Campo de texto para la IP del servidor remoto */
    @FXML private TextField campoIpServidor;
    /** Campo de texto para el puerto del servidor remoto */
    @FXML private TextField campoPuertoServidor;
    /** Campo de texto para el nombre descriptivo del equipo cliente */
    @FXML private TextField campoNombreCliente;
    /** Botón para probar conexión al servidor remoto */
    @FXML private Button botonProbarConexion;
    /** Label que muestra el resultado de la prueba de conexión */
    @FXML private Label labelResultadoConexion;
    /** Botón icono para expandir el panel cuando está colapsado */
    @FXML private HBox botonExpandirConfig;
    /** Label resumen de la configuración cuando el panel está colapsado */
    @FXML private Label labelConfigResumen;

    /** Indica si el panel de config está actualmente colapsado (tras conexión exitosa) */
    private boolean panelConfigColapsado = false;

    // =========================================================================
    // ESTADO INTERNO
    // =========================================================================

    /** Código de empleado verificado en el paso 1 */
    private String codigoIngresado;
    /** Nombre del empleado devuelto por la verificación del código */
    private String nombreEmpleado;
    /** Servicio de autenticación reutilizado durante toda la vida del controlador */
    private AutenticacionServicio autenticacionServicio;

    // =========================================================================
    // INICIALIZACIÓN
    // =========================================================================

    /**
     * Punto de entrada del controlador. Inicializa todos los componentes,
     * configura validaciones, efectos visuales y servicios auxiliares.
     */
    @FXML
    public void initialize() {
        logger.info("Inicializando LoginPinController unificado");

        autenticacionServicio = new AutenticacionServicio();

        // Configurar cada sección del login
        inicializarVistaPIN();
        inicializarVistaAdmin();

        // Asegurar que solo PIN esté visible al inicio
        mostrarLoginPin();

        // Efecto hover sutil en el logo (escala 1.0 → 1.08)
        configurarEfectoLogo();

        // Configurar el toggle switch de Host Mode con persistencia
        inicializarHostMode();

        // Arrancar monitor de conexión y colocar indicador visual
        inicializarMonitorConexion();

        // Activar teclado virtual para campos de texto (admin login)
        // Los campos campoCodigo y campoPin tienen sin-teclado-virtual y son ignorados
        Platform.runLater(() -> TecladoVirtualSimple.activar(campoUsuario));

        // Activar teclado numérico (NumPad) para los campos de código y PIN
        // Se abre automáticamente al recibir foco al hacer clic en el campo
        Platform.runLater(() -> {
            TecladoNumerico.activar(campoCodigo, campoCodigo, campoPin);
        });

        logger.info("LoginPinController inicializado correctamente");
    }

    /**
     * Configura el efecto de hover en el logo KIPU.
     * Al pasar el mouse se escala suavemente a 1.08x.
     */
    private void configurarEfectoLogo() {
        if (logoLogin == null) return;

        logoLogin.setOnMouseEntered(e -> MotorAnimaciones.escalar(logoLogin, 1.08, 300));
        logoLogin.setOnMouseExited(e -> MotorAnimaciones.escalar(logoLogin, 1.0, 300));
    }

    /**
     * Inicializa el monitor de conexión y agrega el indicador visual
     * en la esquina inferior derecha del login.
     * 
     * En host mode desconectado, muestra un botón "Conectar" que abre
     * el PanelConexionRed como overlay. En otros casos muestra el indicador
     * de conexión normal con popup de topología al hacer clic.
     */
    private void inicializarMonitorConexion() {
        MonitorConexion monitor = MonitorConexion.getInstancia();
        monitor.iniciar();

        if (indicadorConexionLogin != null) {
            actualizarIndicadorConexion(monitor);

            // Listener reactivo: si el estado cambia, actualizar el indicador
            monitor.estadoProperty().addListener((obs, viejo, nuevo) ->
                Platform.runLater(() -> actualizarIndicadorConexion(monitor)));
        }
    }

    /**
     * Actualiza el indicador de conexión según el estado actual y el modo.
     * Host mode + desconectado → botón "Conectar"
     * Host mode + conectado → indicador normal con topología
     * Cliente mode → indicador normal con topología
     */
    private void actualizarIndicadorConexion(MonitorConexion monitor) {
        indicadorConexionLogin.getChildren().clear();

        boolean esHost = ConfiguracionCliente.isHostMode();
        boolean desconectado = monitor.getEstado() == MonitorConexion.EstadoConexion.DESCONECTADO;

        if (esHost && desconectado) {
            // Botón "Conectar" que abre el panel de conexión
            Button botonConectar = new Button(IdiomaUtil.obtener("panel.lan.conectar"));
            botonConectar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #d4af37, #b8984e); " +
                "-fx-text-fill: #0a0a0a; -fx-font-size: 12px; -fx-font-weight: 700; " +
                "-fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 6 20;");
            botonConectar.setOnAction(e -> abrirPanelConexion());
            indicadorConexionLogin.getChildren().add(botonConectar);
        } else {
            // Indicador normal con popup de topología
            HBox indicador = monitor.crearIndicador(true);
            indicador.setStyle("-fx-cursor: hand;");
            indicador.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                javafx.scene.Parent raiz = indicadorConexionLogin.getScene().getRoot();
                if (raiz instanceof javafx.scene.layout.StackPane raizStack) {
                    com.kipu.cliente.componente.TopologiaRed
                        .mostrarPopupRapido(raizStack, e);
                }
            });
            indicadorConexionLogin.getChildren().add(indicador);
        }
    }

    /**
     * Abre el PanelConexionRed como overlay sobre el login.
     */
    private void abrirPanelConexion() {
        javafx.scene.Parent raiz = indicadorConexionLogin.getScene().getRoot();
        if (!(raiz instanceof javafx.scene.layout.StackPane raizStack)) return;

        PanelConexionRed panel = new PanelConexionRed();
        StackPane overlay = panel.construir(raizStack,
            lanConectada -> {
                // LAN conectada: reiniciar monitor para que detecte el servidor
                reiniciarMonitorConexion();
            },
            cerrado -> {
                // Al cerrar, actualizar indicador
                MonitorConexion monitor = MonitorConexion.getInstancia();
                Platform.runLater(() -> actualizarIndicadorConexion(monitor));
            },
            true // Incluir tutorial al abrir desde login
        );
        raizStack.getChildren().add(overlay);
    }

    /**
     * Inicializa el toggle switch de Host Mode con el valor guardado en configuración.
     * Agrega un listener para persistir cambios automáticamente cuando el usuario
     * alterna el switch. También inicializa el panel de configuración de servidor
     * remoto (IP/Puerto) que se muestra cuando Host Mode está desactivado.
     */
    private void inicializarHostMode() {
        if (toggleHostMode == null) return;

        // Estado inicial desde la configuración persistida
        boolean modoHostActivo = ConfiguracionCliente.isHostMode();
        toggleHostMode.setSelected(modoHostActivo);
        actualizarLabelHostMode(modoHostActivo);

        // Inicializar campos de configuración de servidor remoto
        inicializarPanelConfigServidor(modoHostActivo);

        // Listener: persiste automáticamente al cambiar y actualiza panel de config
        toggleHostMode.selectedProperty().addListener((obs, oldVal, newVal) -> {
            ConfiguracionCliente.setHostMode(newVal);
            actualizarLabelHostMode(newVal);
            actualizarVisibilidadPanelConfig(!newVal);

            if (newVal) {
                int puerto = ConfiguracionCliente.getPuertoServidor();
                ConfiguracionCliente.setUrlServidor("http://localhost:" + puerto);
            }

            // Reiniciar monitor y actualizar indicador inmediatamente
            reiniciarMonitorConexion();
            actualizarIndicadorConexion(MonitorConexion.getInstancia());

            logger.info("Host Mode {}", newVal ? "activado" : "desactivado");
        });
    }

    /**
     * Inicializa el panel de configuración de servidor remoto con los valores
     * guardados en la configuración. Configura validaciones de entrada,
     * carga nombre del cliente, y determina si el panel debe mostrarse
     * expandido o colapsado según el estado de conexión.
     */
    private void inicializarPanelConfigServidor(boolean hostModeActivo) {
        if (panelConfigServidor == null) return;

        // Cargar IP actual desde la configuración (extraer de URL)
        String urlActual = ConfiguracionCliente.getUrlServidor();
        String ipActual = urlActual.replace("http://", "").replaceAll(":\\d+$", "");
        if (!"localhost".equals(ipActual) && !ipActual.isEmpty()) {
            campoIpServidor.setText(ipActual);
        }

        // Cargar puerto actual
        campoPuertoServidor.setText(String.valueOf(ConfiguracionCliente.getPuertoServidor()));

        // Cargar nombre del cliente
        String nombreGuardado = ConfiguracionCliente.getNombreCliente();
        if (nombreGuardado != null && !nombreGuardado.isEmpty()) {
            campoNombreCliente.setText(nombreGuardado);
        }

        // Validar que el campo de puerto solo acepte números (máx 5 dígitos)
        campoPuertoServidor.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d{0,5}")) {
                campoPuertoServidor.setText(oldVal);
            }
        });

        // Validar formato IP: solo dígitos y puntos
        campoIpServidor.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("[\\d.]{0,15}")) {
                campoIpServidor.setText(oldVal);
            }
        });

        // Validar nombre cliente: máx 30 caracteres
        campoNombreCliente.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() > 30) {
                campoNombreCliente.setText(oldVal);
            }
        });

        // Si NO es host mode, decidir si mostrar expandido o colapsado
        if (!hostModeActivo) {
            // Si ya tiene IP configurada, verificar si ya está conectado
            if (!ipActual.isEmpty() && !"localhost".equals(ipActual)) {
                MonitorConexion monitor = MonitorConexion.getInstancia();
                if (monitor.getEstado() == MonitorConexion.EstadoConexion.CONECTADO ||
                    monitor.getEstado() == MonitorConexion.EstadoConexion.LENTO) {
                    // Ya conectado: mostrar colapsado con icono
                    colapsarPanelConfigInterno();
                } else {
                    // No conectado: mostrar expandido para configurar
                    expandirPanelConfigInterno();
                }
            } else {
                // Sin IP configurada: mostrar expandido
                expandirPanelConfigInterno();
            }
        }
    }

    /**
     * Muestra u oculta el panel de configuración de servidor remoto.
     * Cuando se oculta (Host Mode ON), también oculta el botón de expandir.
     * Cuando se muestra (Host Mode OFF), decide si expandir o colapsar
     * según el estado de conexión.
     */
    private void actualizarVisibilidadPanelConfig(boolean mostrar) {
        if (panelConfigServidor == null) return;

        if (!mostrar) {
            // Host Mode ON: ocultar todo
            panelConfigServidor.setVisible(false);
            panelConfigServidor.setManaged(false);
            if (botonExpandirConfig != null) {
                botonExpandirConfig.setVisible(false);
                botonExpandirConfig.setManaged(false);
            }
        } else {
            // Host Mode OFF: verificar estado de conexión
            MonitorConexion monitor = MonitorConexion.getInstancia();
            if (monitor.getEstado() == MonitorConexion.EstadoConexion.CONECTADO ||
                monitor.getEstado() == MonitorConexion.EstadoConexion.LENTO) {
                colapsarPanelConfigInterno();
            } else {
                expandirPanelConfigInterno();
            }
        }
    }

    /**
     * Expande el panel de configuración mostrando los campos de IP/Puerto/Nombre.
     * Oculta el botón icono de expandir.
     */
    private void expandirPanelConfigInterno() {
        panelConfigColapsado = false;
        if (panelConfigServidor != null) {
            panelConfigServidor.setVisible(true);
            panelConfigServidor.setManaged(true);
        }
        if (botonExpandirConfig != null) {
            botonExpandirConfig.setVisible(false);
            botonExpandirConfig.setManaged(false);
        }
    }

    /**
     * Colapsa el panel de configuración ocultándolo y mostrando solo
     * el botón icono ⚙ con un resumen de la conexión actual.
     */
    private void colapsarPanelConfigInterno() {
        panelConfigColapsado = true;
        if (panelConfigServidor != null) {
            panelConfigServidor.setVisible(false);
            panelConfigServidor.setManaged(false);
        }
        if (botonExpandirConfig != null) {
            botonExpandirConfig.setVisible(true);
            botonExpandirConfig.setManaged(true);
            // Mostrar resumen: IP actual
            String ip = campoIpServidor.getText().trim();
            String nombre = campoNombreCliente.getText().trim();
            String resumen = nombre.isEmpty() ? ip : nombre;
            if (labelConfigResumen != null) {
                labelConfigResumen.setText(resumen.isEmpty() ? IdiomaUtil.obtener("ctrl.login.configuracion") : resumen);
            }
        }
    }

    /**
     * Alterna entre expandir y colapsar el panel de configuración.
     * Se invoca al hacer clic en el botón icono ⚙.
     */
    @FXML
    private void togglePanelConfig() {
        if (panelConfigColapsado) {
            expandirPanelConfigInterno();
        } else {
            colapsarPanelConfigInterno();
        }
    }

    /**
     * Colapsa el panel de configuración. Se invoca al hacer clic en ▾.
     */
    @FXML
    private void colapsarPanelConfig() {
        colapsarPanelConfigInterno();
    }

    /**
     * Prueba la conexión al servidor remoto usando la IP y puerto ingresados.
     * Realiza una petición GET al endpoint de health del servidor y muestra
     * el resultado en el label de resultado de conexión.
     */
    @FXML
    private void probarConexionServidor() {
        String ip = campoIpServidor.getText().trim();
        String puertoTexto = campoPuertoServidor.getText().trim();

        // Validar que los campos no estén vacíos
        if (ip.isEmpty()) {
            mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.ingrese_ip"), "#e74c3c");
            return;
        }
        if (puertoTexto.isEmpty()) {
            mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.ingrese_puerto"), "#e74c3c");
            return;
        }

        int puerto;
        try {
            puerto = Integer.parseInt(puertoTexto);
            if (puerto < 1 || puerto > 65535) {
                mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.puerto_invalido"), "#e74c3c");
                return;
            }
        } catch (NumberFormatException e) {
            mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.puerto_invalido"), "#e74c3c");
            return;
        }

        // Deshabilitar botón mientras prueba
        botonProbarConexion.setDisable(true);
        mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.probando"), "#f39c12");

        String urlPrueba = "http://" + ip + ":" + puerto;

        // Probar conexión en hilo separado para no bloquear UI
        CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build();

                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(urlPrueba + "/api/usuarios/health"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> response = httpClient.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        // Conexión exitosa: guardar IP/puerto/nombre y actualizar monitor
                        ConfiguracionCliente.setUrlServidor(urlPrueba);
                        ConfiguracionCliente.setPuertoServidor(puerto);

                        // Guardar nombre del cliente
                        String nombreCli = campoNombreCliente.getText().trim();
                        if (!nombreCli.isEmpty()) {
                            ConfiguracionCliente.setNombreCliente(nombreCli);
                        }

                        mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.conectado"), "#2ecc71");
                        reiniciarMonitorConexion();

                        // Colapsar panel tras conexión exitosa (después de breve pausa visual)
                        PauseTransition pausaColapsar = new PauseTransition(Duration.millis(800));
                        pausaColapsar.setOnFinished(ev -> colapsarPanelConfigInterno());
                        pausaColapsar.play();

                        logger.info("Conexión exitosa al servidor remoto: {} (cliente: {})",
                                urlPrueba, nombreCli);
                    } else {
                        mostrarResultadoConexion(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.login.error_http"), response.statusCode()), "#e74c3c");
                        logger.warn("Servidor respondió con código {}: {}", response.statusCode(), urlPrueba);
                    }
                    botonProbarConexion.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarResultadoConexion(IdiomaUtil.obtener("ctrl.login.sin_conexion"), "#e74c3c");
                    botonProbarConexion.setDisable(false);
                    logger.warn("No se pudo conectar al servidor {}: {}", urlPrueba, e.getMessage());
                });
            }
        });
    }

    /**
     * Muestra un mensaje de resultado en el label de conexión con el color indicado.
     */
    private void mostrarResultadoConexion(String mensaje, String color) {
        if (labelResultadoConexion != null) {
            labelResultadoConexion.setText(mensaje);
            if (!labelResultadoConexion.getStyleClass().contains("login-cfg-resultado")) labelResultadoConexion.getStyleClass().add("login-cfg-resultado");
            labelResultadoConexion.setStyle("-fx-text-fill: " + color + ";");
        }
    }

    /**
     * Reinicia el monitor de conexión para que use la nueva URL configurada.
     * Detiene el monitor actual, espera un momento y lo reinicia para que
     * tome la URL actualizada de ConfiguracionCliente.
     */
    private void reiniciarMonitorConexion() {
        MonitorConexion monitor = MonitorConexion.getInstancia();
        monitor.detener();
        PauseTransition pausa = new PauseTransition(Duration.millis(500));
        pausa.setOnFinished(e -> monitor.iniciar());
        pausa.play();
    }

    /**
     * Actualiza el estilo del label de Host Mode según el estado del toggle.
     * Dorado con checkmark (✓) cuando está activo, gris cuando está inactivo.
     */
    private void actualizarLabelHostMode(boolean activo) {
        if (labelHostMode == null) return;

        if (activo) {
            labelHostMode.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 12px; -fx-font-weight: bold;");
            labelHostMode.setText(IdiomaUtil.obtener("ctrl.config.host_on"));
        } else {
            labelHostMode.setStyle("-fx-text-fill: #666666; -fx-font-size: 12px;");
            labelHostMode.setText(IdiomaUtil.obtener("ctrl.config.host_off"));
        }
    }

    // =========================================================================
    // INICIALIZACIÓN DE VISTAS
    // =========================================================================

    /**
     * Inicializa los componentes de la vista PIN:
     * - Oculta indicadores de carga y error
     * - Configura validaciones de entrada (solo dígitos, longitud máxima)
     * - Vincula Enter en campos a sus acciones correspondientes
     * - Muestra formulario de código (paso 1) por defecto
     */
    private void inicializarVistaPIN() {
        // Ocultar indicadores inicialmente
        ocultarCarga(contenedorCargaPin, indicadorCargaPin);
        ocultarError(contenedorErrorPin);

        // Validaciones de entrada
        configurarValidacionesPIN();

        // Mostrar solo formulario de código inicialmente
        mostrarFormularioCodigo();

        // Focus inicial en código se maneja después de activar el NumPad
        // (ver initialize() → TecladoNumerico.activar + requestFocus)

        // Enter en código → verificar y avanzar a PIN
        campoCodigo.setOnAction(event -> verificarCodigoYMostrarPin());

        // Enter en PIN → ejecutar login
        campoPin.setOnAction(event -> iniciarSesion());
    }

    /**
     * Inicializa los componentes de la vista Admin:
     * - Oculta indicadores de carga y error
     * - Vincula Enter en campos a sus acciones correspondientes
     */
    private void inicializarVistaAdmin() {
        // Ocultar indicadores inicialmente
        ocultarCarga(contenedorCargaAdmin, indicadorCargaAdmin);
        ocultarError(contenedorErrorAdmin);

        // Enter en usuario → avanzar a contraseña
        if (campoUsuario != null) {
            campoUsuario.setOnAction(event -> campoContrasena.requestFocus());
        }

        // Enter en contraseña → ejecutar login admin
        if (campoContrasena != null) {
            campoContrasena.setOnAction(event -> iniciarSesionAdmin());
        }
    }

    /**
     * Configura validaciones de entrada para los campos de código y PIN.
     * - Código: solo dígitos, máximo 2 caracteres
     * - PIN: solo dígitos, máximo 4 caracteres
     */
    private void configurarValidacionesPIN() {
        // Código: máximo 2 dígitos
        campoCodigo.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                campoCodigo.setText(newValue.replaceAll("[^\\d]", ""));
            }
            if (newValue.length() > 2) {
                campoCodigo.setText(newValue.substring(0, 2));
            }
        });

        // PIN: máximo 4 dígitos
        campoPin.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                campoPin.setText(newValue.replaceAll("[^\\d]", ""));
            }
            if (newValue.length() > 4) {
                campoPin.setText(newValue.substring(0, 4));
            }
        });
    }

    // =========================================================================
    // NAVEGACIÓN ENTRE VISTAS (PIN ↔ Admin)
    // =========================================================================

    /**
     * Muestra la vista de login PIN y oculta la vista Admin.
     * Resetea todos los campos y estados a su valor inicial.
     * Oculta el toggle de Host Mode (solo visible en Admin).
     */
    @FXML
    private void mostrarLoginPin() {
        logger.info("Mostrando vista de login PIN");

        // Alternar visibilidad de vistas
        setVistaVisible(vistaPIN, true);
        setVistaVisible(vistaAdmin, false);

        // Ocultar toggle de Host Mode (solo visible en Admin)
        if (contenedorToggleHost != null) {
            contenedorToggleHost.setVisible(false);
        }

        // Resetear a estado inicial
        codigoIngresado = null;
        nombreEmpleado = null;
        limpiarCamposPIN();
        limpiarCamposAdmin();
        ocultarError(contenedorErrorPin);
        ocultarError(contenedorErrorAdmin);
        mostrarFormularioCodigo();

        Platform.runLater(() -> campoCodigo.requestFocus());
    }

    /**
     * Muestra la vista de login Admin y oculta la vista PIN.
     * Limpia campos y muestra el toggle de Host Mode.
     */
    @FXML
    private void mostrarLoginAdmin() {
        logger.info("Mostrando vista de login Admin");

        // Alternar visibilidad de vistas
        setVistaVisible(vistaPIN, false);
        setVistaVisible(vistaAdmin, true);

        // Mostrar toggle de Host Mode
        if (contenedorToggleHost != null) {
            contenedorToggleHost.setVisible(true);
        }

        // Resetear estado
        codigoIngresado = null;
        nombreEmpleado = null;
        limpiarCamposPIN();
        limpiarCamposAdmin();
        ocultarError(contenedorErrorPin);
        ocultarError(contenedorErrorAdmin);

        Platform.runLater(() -> {
            if (campoUsuario != null) {
                campoUsuario.requestFocus();
            }
        });
    }

    // =========================================================================
    // NAVEGACIÓN INTERNA PIN: Código ↔ PIN (pasos 1 y 2)
    // =========================================================================

    /**
     * Muestra el formulario de código (paso 1) y oculta el de PIN (paso 2).
     */
    private void mostrarFormularioCodigo() {
        setElementoVisible(contenedorTituloCodigo, true);
        setElementoVisible(formularioCodigo, true);
        setElementoVisible(contenedorTituloPin, false);
        setElementoVisible(formularioPin, false);
    }

    /**
     * Muestra el formulario de PIN (paso 2) y oculta el de código (paso 1).
     */
    private void mostrarFormularioPin() {
        setElementoVisible(contenedorTituloCodigo, false);
        setElementoVisible(formularioCodigo, false);
        setElementoVisible(contenedorTituloPin, true);
        setElementoVisible(formularioPin, true);
        // Habilitar foco y dar focus al campo PIN después de verificar código
        Platform.runLater(() -> {
            campoPin.setFocusTraversable(true);
            campoPin.requestFocus();
        });
    }

    /**
     * Vuelve al formulario de código desde el formulario de PIN.
     * Limpia el estado del código verificado.
     */
    @FXML
    private void volverACodigo() {
        codigoIngresado = null;
        nombreEmpleado = null;
        limpiarCamposPIN();
        ocultarError(contenedorErrorPin);
        mostrarFormularioCodigo();
        Platform.runLater(() -> campoCodigo.requestFocus());
    }

    // =========================================================================
    // LOGIN PIN - PASO 1: Verificar código de empleado
    // =========================================================================

    /**
     * Verifica el código de empleado ingresado contra el servidor.
     * Si el código es válido y el usuario está activo, avanza al paso 2 (PIN).
     * 
     * Validaciones locales:
     * - El código no puede estar vacío
     * - Debe tener exactamente 2 dígitos
     * - Debe estar entre 01 y 99
     * 
     * Validación remota (asíncrona):
     * - El código debe existir en la base de datos
     * - El usuario asociado debe estar activo
     */
    @FXML
    private void verificarCodigoYMostrarPin() {
        ocultarError(contenedorErrorPin);

        String codigo = campoCodigo.getText().trim();

        // Validar código localmente
        if (codigo.isEmpty() || codigo.length() != 2) {
            mostrarErrorPin("Ingrese su código de empleado (2 dígitos, ej: 01)");
            campoCodigo.requestFocus();
            return;
        }

        int codigoInt;
        try {
            codigoInt = Integer.parseInt(codigo);
        } catch (NumberFormatException e) {
            mostrarErrorPin("El código debe contener solo números (ej: 01, 15, 42)");
            campoCodigo.requestFocus();
            return;
        }

        if (codigoInt < 1 || codigoInt > 99) {
            mostrarErrorPin("El código debe estar entre 01 y 99");
            campoCodigo.requestFocus();
            return;
        }

        // Mostrar carga y verificar en el servidor
        deshabilitarControlesPIN(true);
        if (labelCargaPin != null) {
            labelCargaPin.setText(Constantes.Mensajes.CARGANDO_VERIFICANDO_CODIGO);
        }

        autenticacionServicio.verificarCodigoAsync(codigo)
                .thenAcceptAsync(respuesta -> {
                    if (!respuesta.isExiste() || !respuesta.isActivo()) {
                        deshabilitarControlesPIN(false);
                        mostrarErrorPin("No se encontró un empleado activo con el código ingresado. Verifique e intente de nuevo.");
                        campoCodigo.requestFocus();
                        return;
                    }

                    // Código válido: guardar info y avanzar a paso 2
                    codigoIngresado = codigo;
                    nombreEmpleado = respuesta.getNombreCompleto();

                    if (textoNombreEmpleado != null) {
                        textoNombreEmpleado.setText(Constantes.Mensajes.BIENVENIDO_USUARIO + nombreEmpleado);
                    }

                    deshabilitarControlesPIN(false);
                    // Mostrar mensaje de éxito breve antes de avanzar al PIN
                    mostrarExitoPin(IdiomaUtil.obtener("ctrl.login.codigo_verificado"));
                    mostrarFormularioPin();
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        deshabilitarControlesPIN(false);
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        if (causa instanceof RuntimeException) {
                            logger.warn("Código no válido: {}", codigo);
                            mostrarErrorPin(IdiomaUtil.obtener("ctrl.login.codigo_no_encontrado"));
                        } else {
                            logger.error("Error de conexión al verificar código", causa);
                            mostrarErrorPin(IdiomaUtil.obtener("ctrl.login.error_conexion"));
                        }
                        campoCodigo.requestFocus();
                    });
                    return null;
                });
    }

    // =========================================================================
    // LOGIN PIN - PASO 2: Autenticación con código + PIN
    // =========================================================================

    /**
     * Inicia sesión con el código verificado y el PIN ingresado.
     * El código ya fue verificado en verificarCodigoYMostrarPin().
     * 
     * Validaciones:
     * - PIN no vacío
     * - PIN de exactamente 4 dígitos
     * - Solo caracteres numéricos
     */
    @FXML
    private void iniciarSesion() {
        ocultarError(contenedorErrorPin);
        ocultarMensaje(contenedorExitoPin);

        String codigo = codigoIngresado;
        String pin = campoPin.getText().trim();

        // Validar PIN
        if (pin.isEmpty()) {
            mostrarErrorPin(IdiomaUtil.obtener("ctrl.login.pin_ingrese"));
            campoPin.requestFocus();
            return;
        }

        if (pin.length() != 4) {
            mostrarErrorPin(IdiomaUtil.obtener("ctrl.login.pin_4digitos"));
            campoPin.requestFocus();
            return;
        }

        if (!pin.matches("\\d+")) {
            mostrarErrorPin(IdiomaUtil.obtener("ctrl.login.pin_solo_numeros"));
            campoPin.requestFocus();
            return;
        }

        // Autenticar de forma asíncrona
        deshabilitarControlesPIN(true);
        if (labelCargaPin != null) {
            labelCargaPin.setText(Constantes.Mensajes.CARGANDO_INICIANDO_SESION);
        }

        LoginPinDto loginDto = new LoginPinDto(codigo, pin);

        autenticacionServicio.loginConPinAsync(loginDto)
                .thenAcceptAsync(respuesta -> {
                    deshabilitarControlesPIN(false);
                    logger.info("Login exitoso para usuario: {}", respuesta.getNombreUsuario());
                    navegarAMenuPrincipal(respuesta);
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        deshabilitarControlesPIN(false);
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error en login con PIN: ", causa);
                        // Determinar mensaje informativo según el tipo de error
                        String mensajeError;
                        String causaMsg = causa.getMessage() != null ? causa.getMessage() : "";
                        if (causaMsg.toLowerCase().contains("credenciales") || causaMsg.toLowerCase().contains("pin") 
                                || causaMsg.toLowerCase().contains("incorrec") || causaMsg.contains("401")) {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.pin_incorrecto");
                        } else if (causaMsg.toLowerCase().contains("bloqueado")) {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.cuenta_bloqueada");
                        } else if (causaMsg.toLowerCase().contains("conexi") || causaMsg.toLowerCase().contains("connect") 
                                || causaMsg.toLowerCase().contains("timeout")) {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.error_conexion");
                        } else {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.error_sesion");
                        }
                        mostrarErrorPin(mensajeError);
                        campoPin.clear();
                        campoPin.requestFocus();
                    });
                    return null;
                });
    }

    // =========================================================================
    // LOGIN ADMIN: Autenticación con usuario + contraseña
    // =========================================================================

    /**
     * Inicia sesión como administrador con usuario y contraseña.
     * Autenticación asíncrona para no bloquear la UI.
     */
    @FXML
    private void iniciarSesionAdmin() {
        ocultarError(contenedorErrorAdmin);
        deshabilitarControlesAdmin(true);

        String usuario = campoUsuario.getText().trim();
        String contrasena = campoContrasena.getText().trim();

        // Validar campos
        if (usuario.isEmpty() || contrasena.isEmpty()) {
            deshabilitarControlesAdmin(false);
            mostrarErrorAdmin(IdiomaUtil.obtener("ctrl.login.admin_campos_vacios"));
            return;
        }

        // Autenticar de forma asíncrona
        LoginDto loginDto = new LoginDto(usuario, contrasena);

        autenticacionServicio.loginAsync(loginDto)
                .thenAcceptAsync(respuesta -> {
                    deshabilitarControlesAdmin(false);
                    logger.info("Login admin exitoso para usuario: {}", respuesta.getNombreUsuario());
                    navegarAMenuPrincipal(respuesta);
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        deshabilitarControlesAdmin(false);
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error en login admin: ", causa);
                        // Determinar mensaje informativo según el tipo de error
                        String mensajeError;
                        String causaMsg = causa.getMessage() != null ? causa.getMessage() : "";
                        if (causaMsg.toLowerCase().contains("credenciales") || causaMsg.toLowerCase().contains("incorrec") 
                                || causaMsg.contains("401")) {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.admin_credenciales_incorrectas");
                        } else if (causaMsg.toLowerCase().contains("bloqueado")) {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.admin_cuenta_bloqueada");
                        } else if (causaMsg.toLowerCase().contains("conexi") || causaMsg.toLowerCase().contains("connect") 
                                || causaMsg.toLowerCase().contains("timeout")) {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.error_conexion");
                        } else {
                            mensajeError = IdiomaUtil.obtener("ctrl.login.error_sesion");
                        }
                        mostrarErrorAdmin(mensajeError);
                        limpiarCamposAdmin();
                        campoUsuario.requestFocus();
                    });
                    return null;
                });
    }

    // =========================================================================
    // NAVEGACIÓN POST-LOGIN
    // =========================================================================

    /**
     * Navega al menú principal después de un login exitoso (PIN o Admin).
     * 
     * Proceso:
     * 1. Detiene el fondo animado para liberar recursos (AnimationTimer)
     * 2. Guarda el usuario autenticado en NavegacionUtil (acceso global)
     * 3. Cambia la escena al menú principal
     * 4. Activa pantalla completa
     * 
     * @param usuario datos del usuario autenticado (token, rol, nombre, etc.)
     */
    private void navegarAMenuPrincipal(AuthRespuestaDto usuario) {
        try {
            // Detener el AnimationTimer antes de cambiar de vista
            if (fondoAnimado != null) {
                logger.info("Deteniendo fondo animado antes de navegar al menú principal");
                fondoAnimado.detener();
            }

            NavegacionUtil.setUsuarioActual(usuario);

            // Sincronizar idioma desde el servidor (async, no bloquea la navegación)
            sincronizarIdiomaDesdeServidor(usuario.getToken());

            Stage stage = (Stage) campoCodigo.getScene().getWindow();
            NavegacionUtil.irAMenuPrincipal(stage);

        } catch (Exception e) {
            logger.error("Error navegando a menú principal: ", e);
            mostrarErrorPin(IdiomaUtil.obtener("ctrl.login.error_menu"));
        }
    }

    /**
     * Consulta la configuración de idioma del servidor y actualiza el idioma local
     * si difiere del guardado. Se ejecuta en un hilo secundario para no bloquear la UI.
     */
    private void sincronizarIdiomaDesdeServidor(String token) {
        CompletableFuture.runAsync(() -> {
            try {
                var servicio = new com.kipu.cliente.servicio.ConfiguracionSistemaServicio();
                var config = servicio.obtenerConfiguracion("idioma");
                if (config != null && config.getValor() != null) {
                    String idiomaServidor = config.getValor();
                    String idiomaLocal = ConfiguracionCliente.getIdioma();
                    if (!idiomaServidor.equals(idiomaLocal)) {
                        logger.info("Idioma del servidor ({}) difiere del local ({}), actualizando",
                                idiomaServidor, idiomaLocal);
                        IdiomaUtil.cambiarIdioma(idiomaServidor);
                        ConfiguracionCliente.setIdioma(idiomaServidor);
                    }
                }
            } catch (Exception e) {
                logger.debug("No se pudo sincronizar idioma desde el servidor: {}", e.getMessage());
            }
        });
    }

    // =========================================================================
    // UTILIDADES DE UI
    // =========================================================================

    // ----- Visibilidad -----

    /**
     * Alterna visibilidad y managed de una vista completa (VBox contenedor).
     */
    private void setVistaVisible(VBox vista, boolean visible) {
        if (vista != null) {
            vista.setVisible(visible);
            vista.setManaged(visible);
        }
    }

    /**
     * Alterna visibilidad y managed de un elemento individual (VBox).
     */
    private void setElementoVisible(VBox elemento, boolean visible) {
        if (elemento != null) {
            elemento.setVisible(visible);
            elemento.setManaged(visible);
        }
    }

    // ----- Mensajes de feedback (Error / Éxito) -----

    /**
     * Muestra un mensaje de error en la vista PIN con animación fade-in.
     * El mensaje se oculta automáticamente después de 6 segundos.
     * Si hay un mensaje de éxito visible, lo oculta primero.
     * 
     * @param mensaje Texto descriptivo del error a mostrar
     */
    private void mostrarErrorPin(String mensaje) {
        Platform.runLater(() -> {
            // Ocultar éxito si estaba visible
            ocultarMensaje(contenedorExitoPin);
            mostrarMensajeAnimado(contenedorErrorPin, labelErrorPin, mensaje, 6000);
        });
    }

    /**
     * Muestra un mensaje de éxito en la vista PIN con animación fade-in.
     * El mensaje se oculta automáticamente después de 4 segundos.
     * Si hay un mensaje de error visible, lo oculta primero.
     * 
     * @param mensaje Texto descriptivo del éxito a mostrar
     */
    private void mostrarExitoPin(String mensaje) {
        Platform.runLater(() -> {
            // Ocultar error si estaba visible
            ocultarMensaje(contenedorErrorPin);
            mostrarMensajeAnimado(contenedorExitoPin, labelExitoPin, mensaje, 4000);
        });
    }

    /**
     * Muestra un mensaje de error en la vista Admin con animación fade-in.
     * El mensaje se oculta automáticamente después de 6 segundos.
     * 
     * @param mensaje Texto descriptivo del error a mostrar
     */
    private void mostrarErrorAdmin(String mensaje) {
        Platform.runLater(() -> {
            ocultarMensaje(contenedorExitoAdmin);
            mostrarMensajeAnimado(contenedorErrorAdmin, labelErrorAdmin, mensaje, 6000);
        });
    }

    /**
     * Muestra un mensaje de éxito en la vista Admin con animación fade-in.
     * El mensaje se oculta automáticamente después de 4 segundos.
     * 
     * @param mensaje Texto descriptivo del éxito a mostrar
     */
    private void mostrarExitoAdmin(String mensaje) {
        Platform.runLater(() -> {
            ocultarMensaje(contenedorErrorAdmin);
            mostrarMensajeAnimado(contenedorExitoAdmin, labelExitoAdmin, mensaje, 4000);
        });
    }

    /**
     * Muestra un contenedor de mensaje con animación fade-in y auto-dismiss.
     * 
     * El contenedor aparece con una transición de opacidad (0 → 1) de 250ms,
     * permanece visible durante el tiempo indicado, y luego se oculta con
     * una transición fade-out de 300ms.
     * 
     * @param contenedor HBox contenedor del mensaje (error/éxito)
     * @param label Label donde se establece el texto del mensaje
     * @param mensaje Texto del mensaje a mostrar
     * @param duracionMs Milisegundos que el mensaje permanece visible antes de desaparecer
     */
    private void mostrarMensajeAnimado(HBox contenedor, Label label, String mensaje, int duracionMs) {
        if (contenedor == null || label == null) return;
        
        label.setText(mensaje);
        contenedor.setOpacity(0);
        contenedor.setVisible(true);
        contenedor.setManaged(true);
        
        // Animación fade-in (aparecer suavemente)
        MotorAnimaciones.fade(contenedor, 0, 1, 250);
        
        // Auto-ocultar después del tiempo indicado con fade-out
        PauseTransition pausa = new PauseTransition(Duration.millis(duracionMs));
        pausa.setOnFinished(e -> {
            MotorAnimaciones.fade(contenedor, 1, 0, 300, () -> {
                contenedor.setVisible(false);
                contenedor.setManaged(false);
            });
        });
        pausa.play();
    }

    /**
     * Oculta inmediatamente un contenedor de mensaje (sin animación).
     * Se usa para limpiar mensajes previos antes de mostrar uno nuevo.
     * 
     * @param contenedor HBox contenedor del mensaje a ocultar
     */
    private void ocultarMensaje(HBox contenedor) {
        if (contenedor != null) {
            contenedor.setVisible(false);
            contenedor.setManaged(false);
            contenedor.setOpacity(0);
        }
    }

    /**
     * Oculta un contenedor de error (compatible con VBox legacy).
     * Usado por los métodos de flujo de login para limpiar errores previos.
     */
    private void ocultarError(HBox contenedorError) {
        ocultarMensaje(contenedorError);
    }

    private void ocultarCarga(HBox contenedorCarga, ProgressIndicator indicador) {
        if (indicador != null) {
            indicador.setVisible(false);
        }
        if (contenedorCarga != null) {
            contenedorCarga.setVisible(false);
            contenedorCarga.setManaged(false);
        }
    }

    // ----- Controles -----

    private void deshabilitarControlesPIN(boolean deshabilitar) {
        campoCodigo.setDisable(deshabilitar);
        campoPin.setDisable(deshabilitar);
        if (botonLoginPin != null) {
            botonLoginPin.setDisable(deshabilitar);
        }

        if (contenedorCargaPin != null && indicadorCargaPin != null) {
            contenedorCargaPin.setVisible(deshabilitar);
            contenedorCargaPin.setManaged(deshabilitar);
            indicadorCargaPin.setVisible(deshabilitar);
        }
    }

    private void deshabilitarControlesAdmin(boolean deshabilitar) {
        if (campoUsuario != null) campoUsuario.setDisable(deshabilitar);
        if (campoContrasena != null) campoContrasena.setDisable(deshabilitar);
        if (botonLoginAdmin != null) botonLoginAdmin.setDisable(deshabilitar);

        if (contenedorCargaAdmin != null && indicadorCargaAdmin != null) {
            contenedorCargaAdmin.setVisible(deshabilitar);
            contenedorCargaAdmin.setManaged(deshabilitar);
            indicadorCargaAdmin.setVisible(deshabilitar);
        }
    }

    // ----- Limpieza de campos -----

    private void limpiarCamposPIN() {
        campoCodigo.clear();
        campoPin.clear();
    }

    private void limpiarCamposAdmin() {
        if (campoUsuario != null) campoUsuario.clear();
        if (campoContrasena != null) campoContrasena.clear();
    }

    // =========================================================================
    // CIERRE DE APLICACIÓN
    // =========================================================================

    /**
     * Cierra la aplicación completamente.
     * Detiene el monitor de conexión y el fondo animado antes de salir.
     */
    @FXML
    private void cerrarAplicacion() {
        logger.info("Cerrando aplicación desde botón en Login");
        MonitorConexion.getInstancia().detener();
        if (fondoAnimado != null) {
            fondoAnimado.detener();
        }
        Platform.exit();
        System.exit(0);
    }
}
