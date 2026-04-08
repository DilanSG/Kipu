/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.*/
package com.kipu.cliente.controlador;

import com.kipu.common.dto.UsuarioDto;
import com.kipu.cliente.servicio.UsuarioServicio;
import com.kipu.common.constantes.Constantes;
import com.kipu.common.utilidad.ValidacionUtil;
import com.kipu.common.utilidad.FechaUtil;
import com.kipu.common.excepcion.ConexionException;
import com.kipu.common.excepcion.ValidacionException;
import com.kipu.cliente.utilidad.TecladoVirtualSimple;
import com.kipu.cliente.utilidad.IdiomaUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.kipu.cliente.componente.BordeInteractivoModal;
import com.kipu.cliente.componente.MotorAnimaciones;
import com.kipu.cliente.utilidad.NotificacionUtil;
import javafx.animation.FadeTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Duration;

/*Controlador que proporciona CRUD completo de usuarios con soporte para:
 * - Usuarios ADMIN con usuario/contraseña
 * - Usuarios CAJERO/MESERO con código de empleado y PIN
 * - Búsqueda y filtrado de usuarios
 * - Activación/desactivación de usuarios
 * Solo accesible por usuarios con rol ADMIN. */
public class UsuariosController implements SubvistaController {
    
    private static final Logger logger = LoggerFactory.getLogger(UsuariosController.class);
    private MenuPrincipalController menuPrincipal;
    private final UsuarioServicio usuarioServicio;
    private StackPane currentModalRoot;
    private BordeInteractivoModal bordeModal;
    private ObservableList<UsuarioDto> listaUsuarios;
    private UsuarioDto usuarioEnEdicion;
        
    @FXML private TableView<UsuarioDto> tablaUsuarios;
    @FXML private TableColumn<UsuarioDto, String> columnaCodigo;
    @FXML private TableColumn<UsuarioDto, String> columnaNombreCompleto;
    @FXML private TableColumn<UsuarioDto, String> columnaNombreUsuario;
    @FXML private TableColumn<UsuarioDto, String> columnaRol;
    @FXML private TableColumn<UsuarioDto, String> columnaActivo;
    @FXML private TableColumn<UsuarioDto, String> columnaFechaCreacion;
    @FXML private TableColumn<UsuarioDto, Void> columnaAcciones;
    @FXML private TextField campoBusqueda;
    @FXML private ComboBox<String> comboFiltroRol;
    @FXML private Button botonNuevoUsuario;
    @FXML private VBox vistaLista;
    @FXML private VBox vistaFormulario;
    @FXML private StackPane contenedorModal;
    @FXML private StackPane contenidoModalInterno;
    @FXML private Text tituloFormulario;
    @FXML private TextField campoNombreCompleto;
    @FXML private ComboBox<String> comboGenero;
    @FXML private ComboBox<String> comboRol;
    @FXML private VBox contenedorCodigo;
    @FXML private TextField campoCodigo;
    @FXML private VBox contenedorPin;
    @FXML private PasswordField campoPin;
    @FXML private VBox contenedorNombreUsuario;
    @FXML private TextField campoNombreUsuario;
    @FXML private VBox contenedorContrasena;
    @FXML private PasswordField campoContrasena;
    @FXML private CheckBox checkActivo;
    @FXML private Button botonGuardar;
    @FXML private VBox contenedorErrorForm;
    @FXML private Label labelErrorForm;
    @FXML private HBox contenedorCargaForm;
    @FXML private Label labelCargaForm;
    
    // Constructor que inicializa el servicio de usuarios y la lista observable para la tabla.
    public UsuariosController() {
        this.usuarioServicio = new UsuarioServicio();
        this.listaUsuarios = FXCollections.observableArrayList();
    }
    
    @Override
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
    }
    
    @FXML
    public void initialize() {
        logger.info("Inicializando vista de usuarios");
        
        configurarTabla();
        configurarFiltros();
        configurarFormulario();
        
        cargarUsuarios();
        
        // Activar teclado virtual para todos los campos de texto
        Platform.runLater(() -> TecladoVirtualSimple.activar(campoBusqueda));
    }
    
    /**
     * Configura las columnas de la tabla de usuarios.
     */
    private void configurarTabla() {
        // Columna Código (centrada)
        columnaCodigo.setCellValueFactory(cellData -> {
            String codigo = cellData.getValue().getCodigo();
            return new SimpleStringProperty(codigo != null ? codigo : "-");
        });
        columnaCodigo.setCellFactory(column -> new TableCell<UsuarioDto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().add("celda-centrada");
                setAlignment(javafx.geometry.Pos.CENTER);
                
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Usar Label dentro de StackPane para garantizar centrado
                    Label label = new Label(item);
                    label.setStyle("-fx-text-fill: #f5f5f5;"); // Asegurar color de texto
                    
                    StackPane container = new StackPane(label);
                    container.setAlignment(Pos.CENTER);
                    container.setPadding(new Insets(2));
                    
                    setGraphic(container);
                    setText(null);
                }
            }
        });
        
        // Columna Nombre Completo
        columnaNombreCompleto.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getNombreCompleto()));
        
        // Columna Nombre de Usuario
        columnaNombreUsuario.setCellValueFactory(cellData -> {
            String nombreUsuario = cellData.getValue().getNombreUsuario();
            return new SimpleStringProperty(nombreUsuario != null ? nombreUsuario : "-");
        });
        
        // Columna Rol con badge de color (centrada)
        columnaRol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getRol()));
        columnaRol.setCellFactory(column -> new TableCell<UsuarioDto, String>() {
            @Override
            protected void updateItem(String rol, boolean empty) {
                super.updateItem(rol, empty);
                // Asegurar limpieza de estilos previos pero mantener alineación
                getStyleClass().add("celda-centrada");
                setAlignment(javafx.geometry.Pos.CENTER);
                
                if (empty || rol == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(rol);
                    badge.getStyleClass().add("badge");
                    
                    // Estilos según rol
                    switch (rol) {
                        case "ADMIN":
                            badge.getStyleClass().add("badge-admin");
                            break;
                        case "CAJERO":
                            badge.getStyleClass().add("badge-cajero");
                            break;
                        case "MESERO":
                            badge.getStyleClass().add("badge-mesero");
                            break;
                    }
                    
                    // Envolver en StackPane para forzar el centrado horizontal
                    StackPane container = new StackPane(badge);
                    container.setAlignment(Pos.CENTER);
                    container.setPadding(new Insets(2)); 
                    
                    setGraphic(container);
                    setText(null);
                }
            }
        });
        
        // Columna Estado (Activo/Inactivo) (centrada)
        columnaActivo.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getActivo() ? IdiomaUtil.obtener("ctrl.usuarios.label_activo") : IdiomaUtil.obtener("ctrl.usuarios.label_inactivo")));
        columnaActivo.setCellFactory(column -> new TableCell<UsuarioDto, String>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                getStyleClass().add("celda-centrada");
                setAlignment(javafx.geometry.Pos.CENTER);
                
                if (empty || estado == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(estado);
                    badge.getStyleClass().add("badge");
                    badge.getStyleClass().add(estado.equals(IdiomaUtil.obtener("ctrl.usuarios.label_activo")) ? "badge-activo" : "badge-inactivo");
                    
                    // Envolver en StackPane para forzar el centrado horizontal
                    StackPane container = new StackPane(badge);
                    container.setAlignment(Pos.CENTER);
                    container.setPadding(new Insets(2));
                    
                    setGraphic(container);
                    setText(null);
                }
            }
        });
        
        // Columna Fecha de Creación (centrada) - Usa FechaUtil para formateo
        columnaFechaCreacion.setCellValueFactory(cellData -> {
            if (cellData.getValue().getFechaCreacion() != null) {
                return new SimpleStringProperty(
                    FechaUtil.formatearFechaHora(cellData.getValue().getFechaCreacion())
                );
            }
            return new SimpleStringProperty("-");
        });
        columnaFechaCreacion.setCellFactory(column -> new TableCell<UsuarioDto, String>() {
            @Override
            protected void updateItem(String fecha, boolean empty) {
                super.updateItem(fecha, empty);
                getStyleClass().add("celda-centrada");
                setAlignment(javafx.geometry.Pos.CENTER);
                if (empty || fecha == null) {
                    setText(null);
                } else {
                    setText(fecha);
                }
            }
        });
        
        // Columna Acciones con botones (centrada)
        columnaAcciones.setCellFactory(column -> new TableCell<UsuarioDto, Void>() {
            private final Button botonEditar = new Button(IdiomaUtil.obtener("ctrl.usuarios.btn_editar"));
            private final Button botonEliminar = new Button(IdiomaUtil.obtener("ctrl.usuarios.btn_eliminar"));
            private final HBox contenedor = new HBox(8, botonEditar, botonEliminar);
            
            {
                contenedor.setAlignment(javafx.geometry.Pos.CENTER);
                botonEditar.getStyleClass().addAll("boton-tabla", "boton-editar");
                botonEliminar.getStyleClass().addAll("boton-tabla", "boton-eliminar");
                
                botonEditar.setOnAction(event -> {
                    UsuarioDto usuario = getTableView().getItems().get(getIndex());
                    editarUsuario(usuario);
                });
                
                botonEliminar.setOnAction(event -> {
                    UsuarioDto usuario = getTableView().getItems().get(getIndex());
                    confirmarEliminarUsuario(usuario);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().add("celda-centrada");
                setAlignment(javafx.geometry.Pos.CENTER);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(contenedor);
                }
            }
        });
        
        // Asignar datos a la tabla
        tablaUsuarios.setItems(listaUsuarios);
    }
    
    /**
     * Configura los combos de filtros.
     */
    private void configurarFiltros() {
        // Filtro por rol
        comboFiltroRol.setItems(FXCollections.observableArrayList(
            IdiomaUtil.obtener("ctrl.usuarios.label_todos"), "ADMIN", "CAJERO", "MESERO"
        ));
        comboFiltroRol.setValue(IdiomaUtil.obtener("ctrl.usuarios.label_todos"));
        
        // Listener para búsqueda en tiempo real
        campoBusqueda.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                cargarUsuarios();
            }
        });
        
        // Listener para cambio de filtros
        comboFiltroRol.valueProperty().addListener((obs, oldVal, newVal) -> cargarUsuarios());
    }
    
    /**
     * Configura el formulario de usuario.
     */
    private void configurarFormulario() {
        // Configurar combo de roles
        comboRol.setItems(FXCollections.observableArrayList("ADMIN", "CAJERO", "MESERO"));
        
        // Configurar combo de género
        comboGenero.setItems(FXCollections.observableArrayList("MASCULINO", "FEMENINO"));
        
        // Validaciones de campos numéricos
        configurarValidacionCodigo();
        configurarValidacionPin();
    }
    
    /**
     * Configura la validación del campo código (solo números, máximo 2 dígitos).
     */
    private void configurarValidacionCodigo() {
        campoCodigo.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                campoCodigo.setText(newValue.replaceAll("[^\\d]", ""));
            }
            if (newValue.length() > 2) {
                campoCodigo.setText(newValue.substring(0, 2));
            }
        });
    }
    
    /**
     * Configura la validación del campo PIN (solo números, máximo 4 dígitos).
     */
    private void configurarValidacionPin() {
        campoPin.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                campoPin.setText(newValue.replaceAll("[^\\d]", ""));
            }
            if (newValue.length() > 4) {
                campoPin.setText(newValue.substring(0, 4));
            }
        });
    }
    
    /**
     * Carga la lista de usuarios desde el servidor.
     */
    /**
     * Carga la lista de usuarios desde el servidor.
     */
    private void cargarUsuarios() {
        logger.info("Cargando usuarios del servidor");
        
        // Cargar de forma asíncrona para no bloquear UI
        usuarioServicio.listarUsuariosAsync()
                .thenAcceptAsync(usuarios -> {
                    listaUsuarios.clear();
                    
                    // Aplicar filtros
                    String filtroRol = comboFiltroRol.getValue();
                    
                    usuarios.stream()
                        .filter(u -> filtroRol == null || filtroRol.equals(IdiomaUtil.obtener("ctrl.usuarios.label_todos")) || u.getRol().equals(filtroRol))
                        .forEach(listaUsuarios::add);
                    
                    logger.info("Cargados {} usuarios", listaUsuarios.size());
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error al cargar usuarios", causa);
                        mostrarAlerta(IdiomaUtil.obtener("ctrl.usuarios.titulo_error"), java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.usuarios.error_cargar"), causa.getMessage()), Alert.AlertType.ERROR);
                    });
                    return null;
                });
    }
    
    /**
     * Busca usuarios según el término ingresado.
     */
    @FXML
    private void buscarUsuarios() {
        String termino = campoBusqueda.getText().trim();
        
        if (termino.isEmpty()) {
            cargarUsuarios();
            return;
        }
        
        logger.info("Buscando usuarios con término: {}", termino);
        
        // Buscar de forma asíncrona
        usuarioServicio.buscarUsuariosAsync(termino)
                .thenAcceptAsync(usuarios -> {
                    listaUsuarios.clear();
                    listaUsuarios.addAll(usuarios);
                    logger.info("Encontrados {} usuarios", usuarios.size());
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error al buscar usuarios", causa);
                        mostrarAlerta(IdiomaUtil.obtener("ctrl.usuarios.titulo_error"), java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.usuarios.error_buscar"), causa.getMessage()), Alert.AlertType.ERROR);
                    });
                    return null;
                });
    }
    
    /**
     * Muestra el formulario para crear un nuevo usuario.
     */
    @FXML
    private void mostrarFormularioNuevo() {
        logger.info("Mostrando formulario de nuevo usuario");
        
        usuarioEnEdicion = null;
        tituloFormulario.setText(IdiomaUtil.obtener("ctrl.usuarios.nuevo_usuario"));
        limpiarFormulario();
        
        // Cambiar a vista de formulario
        vistaLista.setVisible(false);
        vistaLista.setManaged(false);
        vistaFormulario.setVisible(true);
        vistaFormulario.setManaged(true);
        
        Platform.runLater(() -> campoNombreCompleto.requestFocus());
    }
    
    /**
     * Muestra el formulario para editar un usuario existente.
     */
    private void editarUsuario(UsuarioDto usuario) {
        logger.info("Editando usuario: {}", usuario.getIdUsuario());
        
        usuarioEnEdicion = usuario;
        tituloFormulario.setText(IdiomaUtil.obtener("ctrl.usuarios.editar_usuario"));
        
        // Cargar datos en el formulario
        campoNombreCompleto.setText(usuario.getNombreCompleto());
        comboGenero.setValue(usuario.getGenero());
        comboRol.setValue(usuario.getRol());
        checkActivo.setSelected(usuario.getActivo());
        
        // Campos según rol
        cambioRol();
        
        if (usuario.getRol().equals("ADMIN")) {
            campoNombreUsuario.setText(usuario.getNombreUsuario());
            // No cargar contraseña por seguridad
        } else {
            campoCodigo.setText(usuario.getCodigo());
            // No cargar PIN por seguridad
        }
        
        // Cambiar a vista de formulario
        vistaLista.setVisible(false);
        vistaLista.setManaged(false);
        vistaFormulario.setVisible(true);
        vistaFormulario.setManaged(true);
    }
    
    /**
     * Maneja el cambio de rol en el formulario.
     * Muestra/oculta campos según el rol seleccionado.
     */
    @FXML
    private void cambioRol() {
        String rolSeleccionado = comboRol.getValue();
        
        if (rolSeleccionado == null) return;
        
        boolean esAdmin = rolSeleccionado.equals("ADMIN");
        
        // Mostrar/ocultar campos según rol
        contenedorCodigo.setVisible(!esAdmin);
        contenedorCodigo.setManaged(!esAdmin);
        
        contenedorPin.setVisible(!esAdmin);
        contenedorPin.setManaged(!esAdmin);
        
        contenedorNombreUsuario.setVisible(esAdmin);
        contenedorNombreUsuario.setManaged(esAdmin);
        
        contenedorContrasena.setVisible(esAdmin);
        contenedorContrasena.setManaged(esAdmin);
    }
    
    /**
     * Guarda el usuario (crear o actualizar).
     */
    @FXML
    private void guardarUsuario() {
        ocultarErrorForm();
        
        // Validar formulario
        String error = validarFormulario();
        if (error != null) {
            mostrarErrorForm(error);
            return;
        }
        
        // Construir DTO
        UsuarioDto usuario = construirUsuarioDesdeFormulario();
        
        // Deshabilitar botones y mostrar carga
        deshabilitarControlesForm(true);
        labelCargaForm.setText(usuarioEnEdicion == null ? IdiomaUtil.obtener("ctrl.usuarios.creando") : IdiomaUtil.obtener("ctrl.usuarios.actualizando"));
        
        // Guardar de forma asíncrona
        CompletableFuture<UsuarioDto> futureResultado;
        
        if (usuarioEnEdicion == null) {
            // Crear nuevo
            futureResultado = usuarioServicio.crearUsuarioAsync(usuario);
        } else {
            // Actualizar existente
            usuario.setIdUsuario(usuarioEnEdicion.getIdUsuario());
            futureResultado = usuarioServicio.actualizarUsuarioAsync(usuario);
        }
        
        futureResultado
                .thenAcceptAsync(resultado -> {
                    deshabilitarControlesForm(false);
                    cerrarFormulario();
                    cargarUsuarios();
                    Stage stage = (Stage) botonGuardar.getScene().getWindow();
                    NotificacionUtil.mostrarExito(stage, 
                        usuarioEnEdicion == null ? IdiomaUtil.obtener("ctrl.usuarios.creado") : IdiomaUtil.obtener("ctrl.usuarios.actualizado"));
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        deshabilitarControlesForm(false);
                        Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                        logger.error("Error al guardar usuario", causa);
                        mostrarErrorForm(causa.getMessage());
                    });
                    return null;
                });
    }
    
    /**
     * Valida los campos del formulario.
     * OPTIMIZADO: Usa ValidacionUtil para todas las validaciones.
     * 
     * @return mensaje de error si hay validación fallida, null si todo está correcto
     */
    private String validarFormulario() {
        // Nombre completo obligatorio
        try {
            ValidacionUtil.validarRequerido(campoNombreCompleto.getText(), "nombre completo");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        
        // Rol obligatorio
        if (comboRol.getValue() == null) {
            return IdiomaUtil.obtener("ctrl.usuarios.seleccionar_rol");
        }
        
        // Género obligatorio
        if (comboGenero.getValue() == null) {
            return IdiomaUtil.obtener("ctrl.usuarios.seleccionar_genero");
        }
        
        String rol = comboRol.getValue();
        
        if (rol.equals("ADMIN")) {
            // Validaciones para ADMIN con ValidacionUtil
            try {
                ValidacionUtil.validarRequerido(campoNombreUsuario.getText(), "nombre de usuario");
            } catch (IllegalArgumentException e) {
                return IdiomaUtil.obtener("ctrl.usuarios.nombre_usuario_requerido");
            }
            
            // Solo validar contraseña en creación
            String contrasena = campoContrasena.getText();
            if (usuarioEnEdicion == null) {
                try {
                    ValidacionUtil.validarRequerido(contrasena, "contraseña");
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                }
            }
            
            // Validar longitud mínima de contraseña si no está vacía
            if (!ValidacionUtil.esTextoVacio(contrasena)) {
                if (!ValidacionUtil.esLongitudMinima(contrasena, Constantes.Validaciones.LONGITUD_MIN_CONTRASENA)) {
                    return IdiomaUtil.obtener("ctrl.usuarios.contrasena_min");
                }
            }
            
        } else {
            // Validaciones para CAJERO/MESERO con ValidacionUtil
            String codigo = campoCodigo.getText();
            try {
                ValidacionUtil.validarRequerido(codigo, "código de empleado");
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
            
            // Validar longitud del código (debe ser exactamente 2 dígitos)
            if (!ValidacionUtil.esLongitudExacta(codigo, Constantes.Validaciones.LONGITUD_CODIGO)) {
                return IdiomaUtil.obtener("ctrl.usuarios.codigo_longitud");
            }
            
            // Validar que el código sea numérico
            if (!ValidacionUtil.esSoloDigitos(codigo)) {
                return IdiomaUtil.obtener("ctrl.usuarios.codigo_numerico");
            }
            
            int codigoNum = Integer.parseInt(codigo);
            if (!ValidacionUtil.estaEnRango(codigoNum, Constantes.Validaciones.CODIGO_MIN, 
                                            Constantes.Validaciones.CODIGO_MAX)) {
                return IdiomaUtil.obtener("ctrl.usuarios.codigo_rango");
            }
            
            // Solo validar PIN en creación
            String pin = campoPin.getText();
            if (usuarioEnEdicion == null) {
                try {
                    ValidacionUtil.validarRequerido(pin, "PIN");
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                }
            }
            
            // Validar longitud del PIN si no está vacío
            if (!ValidacionUtil.esTextoVacio(pin)) {
                if (!ValidacionUtil.esLongitudExacta(pin, Constantes.Validaciones.LONGITUD_PIN)) {
                    return IdiomaUtil.obtener("ctrl.usuarios.pin_longitud");
                }
                
                if (!ValidacionUtil.esSoloDigitos(pin)) {
                    return IdiomaUtil.obtener("ctrl.usuarios.codigo_numerico");
                }
            }
        }
        
        return null;
    }
    
    /**
     * Construye un UsuarioDto desde los campos del formulario.
     */
    private UsuarioDto construirUsuarioDesdeFormulario() {
        UsuarioDto usuario = new UsuarioDto();
        
        usuario.setNombreCompleto(campoNombreCompleto.getText().trim());
        usuario.setGenero(comboGenero.getValue());
        usuario.setRol(comboRol.getValue());
        usuario.setActivo(checkActivo.isSelected());
        
        if (comboRol.getValue().equals("ADMIN")) {
            usuario.setNombreUsuario(campoNombreUsuario.getText().trim());
            
            // Solo enviar contraseña si se cambió (no vacía)
            if (!campoContrasena.getText().trim().isEmpty()) {
                usuario.setContrasena(campoContrasena.getText().trim());
            }
        } else {
            usuario.setCodigo(campoCodigo.getText().trim());
            
            // Solo enviar PIN si se cambió (no vacío)
            if (!campoPin.getText().trim().isEmpty()) {
                usuario.setPin(campoPin.getText().trim());
            }
        }
        
        return usuario;
    }
    
    /**
     * Confirma la eliminación de un usuario.
     */
    /**
     * Confirma la eliminación de un usuario usando un modal personalizado.
     */
    private void confirmarEliminarUsuario(UsuarioDto usuario) {
        StackPane modalRoot = crearContenedorModalLuxury(450, 300);
        VBox contenido = new VBox(24);
        contenido.setPadding(new Insets(30));
        contenido.setAlignment(Pos.CENTER);
        
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.confirmar.eliminacion"));
        titulo.getStyleClass().add("icono-texto-md");
        titulo.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffaa00;");
        
        Label mensaje = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.confirmar.eliminar_usuario"), usuario.getNombreCompleto()));
        mensaje.setWrapText(true);
        mensaje.getStyleClass().add("modal-mensaje");
        
        HBox botones = new HBox(12);
        botones.setAlignment(Pos.CENTER);
        
        Button btnCancelar = new Button(IdiomaUtil.obtener("ctrl.btn.cancelar"));
        btnCancelar.getStyleClass().add("btn-cancelar");
        btnCancelar.setOnAction(e -> cerrarOverlay());
        
        Button btnEliminar = new Button(IdiomaUtil.obtener("ctrl.btn.eliminar"));
        btnEliminar.getStyleClass().add("btn-eliminar-luxury");
        btnEliminar.setOnAction(e -> {
            cerrarOverlay();
            eliminarUsuario(usuario);
        });
        
        botones.getChildren().addAll(btnCancelar, btnEliminar);
        contenido.getChildren().addAll(titulo, mensaje, botones);
        
        // Agregar contenido directamente al modal (sin FondoAnimado ni Canvas)
        modalRoot.getChildren().add(contenido);
        
        contenidoModalInterno.getChildren().clear();
        contenidoModalInterno.getChildren().add(modalRoot);
        mostrarOverlay();
    }
    
    // ==================== SISTEMA DE MODALES CRISTAL ====================
    
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
    
    /**
     * Elimina un usuario del sistema.
     */
    private void eliminarUsuario(UsuarioDto usuario) {
        logger.info("Eliminando usuario: {}", usuario.getIdUsuario());
        
        usuarioServicio.eliminarUsuarioAsync(usuario.getIdUsuario())
                .thenRunAsync(() -> {
                    cargarUsuarios();
                    // Usar notificación toast en lugar de alerta modal
                    Stage stage = (Stage) botonNuevoUsuario.getScene().getWindow();
                    NotificacionUtil.mostrarExito(stage, IdiomaUtil.obtener("ctrl.usuarios.eliminado"));
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error al eliminar usuario", causa);
                        mostrarAlerta(IdiomaUtil.obtener("ctrl.usuarios.titulo_error"), java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.usuarios.error_eliminar"), causa.getMessage()), Alert.AlertType.ERROR);
                    });
                    return null;
                });
    }
    
    /**
     * Cierra el formulario de usuario y vuelve a la vista de lista.
     */
    @FXML
    private void cerrarFormulario() {
        // Volver a vista de lista
        vistaFormulario.setVisible(false);
        vistaFormulario.setManaged(false);
        vistaLista.setVisible(true);
        vistaLista.setManaged(true);
        
        limpiarFormulario();
        ocultarErrorForm();
    }
    
    /**
     * Limpia todos los campos del formulario.
     */
    private void limpiarFormulario() {
        campoNombreCompleto.clear();
        comboGenero.setValue(null);
        comboRol.setValue(null);
        campoCodigo.clear();
        campoPin.clear();
        campoNombreUsuario.clear();
        campoContrasena.clear();
        checkActivo.setSelected(true);
        
        // Ocultar todos los campos condicionales
        contenedorCodigo.setVisible(false);
        contenedorCodigo.setManaged(false);
        contenedorPin.setVisible(false);
        contenedorPin.setManaged(false);
        contenedorNombreUsuario.setVisible(false);
        contenedorNombreUsuario.setManaged(false);
        contenedorContrasena.setVisible(false);
        contenedorContrasena.setManaged(false);
    }
    
    /**
     * Muestra un mensaje de error en el formulario.
     */
    private void mostrarErrorForm(String mensaje) {
        labelErrorForm.setText(mensaje);
        contenedorErrorForm.setVisible(true);
        contenedorErrorForm.setManaged(true);
    }
    
    /**
     * Oculta el mensaje de error del formulario.
     */
    private void ocultarErrorForm() {
        contenedorErrorForm.setVisible(false);
        contenedorErrorForm.setManaged(false);
    }
    
    /**
     * Habilita o deshabilita los controles del formulario durante operaciones.
     */
    private void deshabilitarControlesForm(boolean deshabilitar) {
        botonGuardar.setDisable(deshabilitar);
        campoNombreCompleto.setDisable(deshabilitar);
        comboRol.setDisable(deshabilitar);
        campoCodigo.setDisable(deshabilitar);
        campoPin.setDisable(deshabilitar);
        campoNombreUsuario.setDisable(deshabilitar);
        campoContrasena.setDisable(deshabilitar);
        checkActivo.setDisable(deshabilitar);
        
        contenedorCargaForm.setVisible(deshabilitar);
        contenedorCargaForm.setManaged(deshabilitar);
    }
    
    /**
     * Muestra una alerta al usuario.
     */
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
