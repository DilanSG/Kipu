/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.facturacion;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.UsuarioDto;
import com.kipu.cliente.modelo.Mesa;
import com.kipu.cliente.servicio.UsuarioServicio;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.cliente.utilidad.TecladoVirtualSimple;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Controlador para la subvista de Selección de Mesero.
 * 
 * Responsabilidades:
 * - Mostrar grid de meseros disponibles
 * - Permitir búsqueda y filtrado de meseros
 * - Manejar selección de mesero para asignar a mesa
 * - Comunicar con FacturacionController la selección
 * 
 * Solo accesible por CAJERO y ADMIN.
 * 
 * @author Sistema Kipu
 * @version 1.0
 */
public class SeleccionMeseroController {
    
    private static final Logger logger = LoggerFactory.getLogger(SeleccionMeseroController.class);
    private static final int MAX_COLUMNAS = 5;
    
    // Componentes FXML
    @FXML private Text txtNombreMesa;
    @FXML private TextField txtBuscarMesero;
    @FXML private GridPane gridMeseros;
    
    // Servicios
    private final UsuarioServicio usuarioServicio = new UsuarioServicio();
    
    // Datos
    private List<UsuarioDto> meserosDisponibles;
    private String nombreMesaACrear;
    private FacturacionController controladorPadre;
    
    /**
     * Inicialización del controlador.
     */
    @FXML
    public void initialize() {
        logger.info("Inicializando SeleccionMeseroController");
        
        // Configurar búsqueda en tiempo real
        txtBuscarMesero.textProperty().addListener((observable, oldValue, newValue) -> {
            filtrarMeseros(newValue);
        });
        
        // Establecer foco en el input de búsqueda automáticamente
        Platform.runLater(() -> txtBuscarMesero.requestFocus());
        
        // Activar teclado virtual para el campo de búsqueda
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtBuscarMesero));
        
        // Cargar meseros
        cargarMeseros();
    }
    
    /**
     * Establece el nombre de la mesa a crear.
     * 
     * @param nombreMesa Nombre o número de la mesa
     */
    public void setNombreMesa(String nombreMesa) {
        this.nombreMesaACrear = nombreMesa;
        txtNombreMesa.setText(Constantes.Mensajes.LABEL_MESA + nombreMesa);
        logger.info("Mesa a crear: {}", nombreMesa);
    }
    
    /**
     * Establece referencia al controlador padre.
     * 
     * @param controlador FacturacionController padre
     */
    public void setControladorPadre(FacturacionController controlador) {
        this.controladorPadre = controlador;
    }
    
    /**
     * Carga la lista de meseros desde el servidor.
     */
    private void cargarMeseros() {
        logger.info("Cargando meseros disponibles");
        
        usuarioServicio.listarUsuariosAsync()
                .thenApply(usuarios -> {
                    // Filtrar solo usuarios con rol MESERO y ordenar por código
                    return usuarios.stream()
                            .filter(u -> Constantes.Roles.MESERO.equals(u.getRol()))
                            .sorted(Comparator.comparing(UsuarioDto::getCodigo, Comparator.nullsLast(Comparator.naturalOrder())))
                            .collect(Collectors.toList());
                })
                .thenAcceptAsync(meseros -> {
                    meserosDisponibles = meseros;
                    mostrarMeseros(meseros);
                    logger.info("Cargados {} meseros", meseros.size());
                }, javafx.application.Platform::runLater)
                .exceptionally(error -> {
                    javafx.application.Platform.runLater(() -> {
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error al cargar meseros", causa);
                        Stage stage = (Stage) gridMeseros.getScene().getWindow();
                        NotificacionUtil.mostrarError(stage, Constantes.Mensajes.ERROR_CARGAR_MESEROS);
                    });
                    return null;
                });
    }
    
    /**
     * Muestra los meseros en el grid.
     * 
     * @param meseros Lista de meseros a mostrar
     */
    private void mostrarMeseros(List<UsuarioDto> meseros) {
        gridMeseros.getChildren().clear();
        
        if (meseros.isEmpty()) {
            logger.warn("No hay meseros disponibles");
            return;
        }
        
        int columna = 0;
        int fila = 0;
        
        for (UsuarioDto mesero : meseros) {
            VBox card = crearCardMesero(mesero);
            gridMeseros.add(card, columna, fila);
            
            columna++;
            if (columna >= MAX_COLUMNAS) {
                columna = 0;
                fila++;
            }
        }
        
        logger.info("Mostrando {} meseros en grid", meseros.size());
    }
    
    /**
     * Crea un card de mesero para selección.
     * 
     * @param mesero Datos del mesero
     * @return VBox con el card del mesero
     */
    private VBox crearCardMesero(UsuarioDto mesero) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("card-mesero");
        card.setPrefWidth(200);
        card.setMinWidth(200);
        card.setMaxWidth(200);
        card.setPrefHeight(220);
        card.setMinHeight(220);
        card.setMaxHeight(220);

        // Avatar circular con icono SVG según género
        Region avatar = new Region();
        avatar.getStyleClass().add("FEMENINO".equals(mesero.getGenero()) ?
                "card-mesero-avatar-f" : "card-mesero-avatar-m");
        avatar.setPrefSize(56, 56);
        avatar.setMinSize(56, 56);
        avatar.setMaxSize(56, 56);

        // Código del mesero (grande y dorado)
        Label lblCodigo = new Label(String.valueOf(mesero.getCodigo()));
        lblCodigo.getStyleClass().add("card-mesero-codigo");

        // Nombre completo
        Label lblNombre = new Label(mesero.getNombreCompleto());
        lblNombre.getStyleClass().add("card-mesero-nombre-label");
        lblNombre.setWrapText(true);
        lblNombre.setAlignment(Pos.CENTER);
        lblNombre.setMaxWidth(180);

        // Badge de rol con género
        String labelRol = "FEMENINO".equals(mesero.getGenero()) ? Constantes.Mensajes.LABEL_MESERA : Constantes.Mensajes.LABEL_MESERO;
        Label lblRol = new Label(labelRol);
        lblRol.getStyleClass().add("card-mesero-genero");

        card.getChildren().addAll(avatar, lblCodigo, lblNombre, lblRol);

        // Evento de clic
        card.setOnMouseClicked(event -> seleccionarMesero(mesero));

        return card;
    }
    
    /**
     * Maneja la selección de un mesero para crear la mesa.
     * 
     * @param mesero Mesero seleccionado
     */
    private void seleccionarMesero(UsuarioDto mesero) {
        logger.info("Mesero seleccionado: {} para mesa {}", mesero.getNombreCompleto(), nombreMesaACrear);
        
        if (controladorPadre != null) {
            // Crear objeto Mesa con los datos
            Mesa nuevaMesa = new Mesa(
                    nombreMesaACrear,
                    mesero.getNombreCompleto(),
                    mesero.getIdUsuario(),
                    Constantes.Mensajes.ESTADO_DISPONIBLE
            );
            
            // Notificar al controlador padre
            controladorPadre.meseroSeleccionado(nuevaMesa);
        }
    }
    
    /**
     * Filtra los meseros según el texto de búsqueda.
     * 
     * @param textoBusqueda Texto a buscar
     */
    private void filtrarMeseros(String textoBusqueda) {
        if (meserosDisponibles == null) {
            return;
        }
        
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            mostrarMeseros(meserosDisponibles);
            return;
        }
        
        String busqueda = textoBusqueda.toLowerCase().trim();
        
        List<UsuarioDto> meserosFiltrados = meserosDisponibles.stream()
                .filter(m -> 
                    m.getNombreCompleto().toLowerCase().contains(busqueda) ||
                    String.valueOf(m.getCodigo()).contains(busqueda)
                )
                .collect(Collectors.toList());
        
        mostrarMeseros(meserosFiltrados);
        logger.debug("Filtrados {} meseros con búsqueda: {}", meserosFiltrados.size(), textoBusqueda);
    }
    
    /**
     * Cancela la selección y vuelve a la vista de búsqueda de mesas.
     */
    @FXML
    private void cancelarSeleccion() {
        logger.info("Selección de mesero cancelada");
        
        if (controladorPadre != null) {
            controladorPadre.cancelarCreacionMesa();
        }
    }    
    /**
     * Selecciona un mesero al presionar Enter en el campo de búsqueda.
     * 
     * Lógica:
     * - Si solo hay 1 mesero visible (coincidencia exacta), lo selecciona
     * - Si hay más de 1, selecciona el primero de la lista visible
     * - Si no hay ninguno, no hace nada
     */
    @FXML
    private void seleccionarPorEnter() {
        // Obtener el texto de búsqueda
        String textoBusqueda = txtBuscarMesero.getText().trim();
        
        // Si no hay meseros disponibles, salir
        if (meserosDisponibles == null || meserosDisponibles.isEmpty()) {
            logger.warn("No hay meseros disponibles para seleccionar");
            return;
        }
        
        // Obtener los meseros que coinciden con el criterio de búsqueda actual
        List<UsuarioDto> meserosCoincidentes = meserosDisponibles.stream()
                .filter(mesero -> {
                    if (textoBusqueda.isEmpty()) {
                        return true; // Si no hay búsqueda, todos coinciden
                    }
                    
                    String busqueda = textoBusqueda.toLowerCase();
                    String nombre = mesero.getNombreCompleto() != null ? 
                            mesero.getNombreCompleto().toLowerCase() : "";
                    String codigo = mesero.getCodigo() != null ? 
                            mesero.getCodigo().toLowerCase() : "";
                    
                    return nombre.contains(busqueda) || codigo.contains(busqueda);
                })
                .collect(Collectors.toList());
        
        // Aplicar la lógica de selección según el número de coincidencias
        if (meserosCoincidentes.isEmpty()) {
            logger.info("No hay meseros que coincidan con: {}", textoBusqueda);
            // No hacer nada, solo mantener el foco
        } else if (meserosCoincidentes.size() == 1) {
            // Seleccionar el único mesero que coincide
            UsuarioDto meseroSeleccionado = meserosCoincidentes.get(0);
            logger.info("Seleccionando único mesero coincidente: {}", meseroSeleccionado.getNombreCompleto());
            seleccionarMesero(meseroSeleccionado);
        } else {
            // Seleccionar el primero de la lista de coincidencias
            UsuarioDto primerMesero = meserosCoincidentes.get(0);
            logger.info("Seleccionando primer mesero de {} coincidencias: {}", 
                    meserosCoincidentes.size(), primerMesero.getNombreCompleto());
            seleccionarMesero(primerMesero);
        }
    }}
