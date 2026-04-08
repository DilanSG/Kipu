/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.meseros;

import com.kipu.common.dto.UsuarioDto;
import com.kipu.cliente.servicio.UsuarioServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.TecladoVirtualSimple;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador para la subvista de Meseros.
 * 
 * Responsabilidades:
 * - Cargar y mostrar todos los meseros registrados en el sistema
 * - Mostrar cards con información de cada mesero (código, nombre, género)
 * - Permitir búsqueda y filtrado de meseros
 * - Adaptar los mensajes al género del mesero (Mesero/Mesera)
 * 
 * Arquitectura:
 * - Usa FlowPane para layout responsivo de cards
 * - Los cards se crean dinámicamente desde el código
 * - Cada card muestra: código, nombre completo, género (con icono)
 * - Filtros por género y búsqueda por texto
 * 
 * @author Sistema Kipu
 * @version 1.0
 */
public class MeserosController {
    
    private static final Logger logger = LoggerFactory.getLogger(MeserosController.class);
    
    // Componentes FXML
    @FXML private TextField campoBusqueda;
    @FXML private Label labelContador;
    @FXML private GridPane gridMeseros;
    @FXML private VBox placeholderVacio;
    
    // Servicios
    private final UsuarioServicio usuarioServicio;
    
    // Datos
    private List<UsuarioDto> listaMeserosCompleta;
    
    /**
     * Constructor del controlador.
     * Inicializa el servicio de usuarios.
     */
    public MeserosController() {
        this.usuarioServicio = new UsuarioServicio();
    }
    
    /**
     * Inicialización del controlador.
     * Configura los filtros y carga los meseros.
     */
    @FXML
    public void initialize() {
        logger.info("Inicializando vista de meseros");
        cargarMeseros();
        
        // Activar teclado virtual para el campo de búsqueda
        Platform.runLater(() -> TecladoVirtualSimple.activar(campoBusqueda));
    }
    
    /**
     * Carga los meseros desde el servidor.
     */
    private void cargarMeseros() {
        logger.info("Cargando meseros del servidor");
        
        labelContador.setText(IdiomaUtil.obtener("meseros.cargando"));
        
        // Cargar de forma asíncrona
        usuarioServicio.listarUsuariosAsync()
                .thenAcceptAsync(todosLosUsuarios -> {
                    List<UsuarioDto> meseros = todosLosUsuarios.stream()
                            .filter(u -> "MESERO".equals(u.getRol()) && Boolean.TRUE.equals(u.getActivo()))
                            .collect(Collectors.toList());
                    
                    listaMeserosCompleta = meseros;
                    mostrarMeseros(meseros);
                    actualizarContador(meseros.size());
                    
                    logger.info("Meseros cargados correctamente: {}", meseros.size());
                }, Platform::runLater)
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        logger.error("Error al cargar meseros", causa);
                        labelContador.setText(IdiomaUtil.obtener("ctrl.meseros.error"));
                    });
                    return null;
                });
    }
    
    /**
     * Muestra los meseros en el grid de cards.
     * Los meseros se ordenan por código de menor a mayor.
     * Grid de 5 columnas fijas.
     */
    private void mostrarMeseros(List<UsuarioDto> meseros) {
        gridMeseros.getChildren().clear();
        
        if (meseros.isEmpty()) {
            placeholderVacio.setVisible(true);
            placeholderVacio.setManaged(true);
            gridMeseros.setVisible(false);
            return;
        }
        
        placeholderVacio.setVisible(false);
        placeholderVacio.setManaged(false);
        gridMeseros.setVisible(true);
        
        // Ordenar meseros por código de menor a mayor
        meseros.sort((m1, m2) -> {
            try {
                int codigo1 = Integer.parseInt(m1.getCodigo());
                int codigo2 = Integer.parseInt(m2.getCodigo());
                return Integer.compare(codigo1, codigo2);
            } catch (NumberFormatException e) {
                // Si no son números, ordenar alfabéticamente
                return m1.getCodigo().compareTo(m2.getCodigo());
            }
        });
        
        // Agregar cards al GridPane (5 columnas fijas)
        int columna = 0;
        int fila = 0;
        final int MAX_COLUMNAS = 5;
        
        for (UsuarioDto mesero : meseros) {
            VBox card = crearCardMesero(mesero);
            gridMeseros.add(card, columna, fila);
            
            columna++;
            if (columna >= MAX_COLUMNAS) {
                columna = 0;
                fila++;
            }
        }
    }
    
    /**
     * Crea un card visual para un mesero.
     * 
     * @param mesero datos del mesero
     * @return VBox con el card configurado
     */
    private VBox crearCardMesero(UsuarioDto mesero) {
        // Contenedor principal del card
        VBox card = new VBox();
        card.getStyleClass().add("card-mesero");
        card.setAlignment(Pos.CENTER);
        card.setSpacing(8);
        card.setPadding(new Insets(20, 16, 20, 16));
        card.setPrefWidth(200);
        card.setMinWidth(200);
        card.setMaxWidth(200);
        card.setPrefHeight(240);
        card.setMinHeight(240);
        card.setMaxHeight(240);

        // Avatar circular con icono SVG según género
        Region avatar = new Region();
        avatar.getStyleClass().add("FEMENINO".equals(mesero.getGenero()) ?
                "card-mesero-avatar-f" : "card-mesero-avatar-m");
        avatar.setPrefSize(56, 56);
        avatar.setMinSize(56, 56);
        avatar.setMaxSize(56, 56);

        // Código del mesero (grande y dorado)
        Label labelCodigo = new Label(mesero.getCodigo());
        labelCodigo.getStyleClass().add("card-mesero-codigo");

        // Nombre completo
        Label labelNombre = new Label(mesero.getNombreCompleto());
        labelNombre.getStyleClass().add("card-mesero-nombre-label");
        labelNombre.setWrapText(true);
        labelNombre.setAlignment(Pos.CENTER);
        labelNombre.setMaxWidth(180);

        // Badge de género (Mesero/Mesera)
        String etiquetaGenero = "FEMENINO".equals(mesero.getGenero()) ? IdiomaUtil.obtener("ctrl.meseros.genero.mesera") : IdiomaUtil.obtener("ctrl.meseros.genero.mesero");
        Label labelGenero = new Label(etiquetaGenero);
        labelGenero.getStyleClass().add("card-mesero-genero");

        // Badge activo
        Label labelActivo = new Label(IdiomaUtil.obtener("ctrl.meseros.activo"));
        labelActivo.getStyleClass().add("card-mesero-badge-activo");

        // Agregar todos los elementos al card
        card.getChildren().addAll(
                avatar,
                labelCodigo,
                labelNombre,
                labelGenero,
                labelActivo
        );

        return card;
    }
    
    /**
     * Actualiza el contador de meseros.
     */
    private void actualizarContador(int total) {
        if (total == 0) {
            labelContador.setText(IdiomaUtil.obtener("ctrl.meseros.vacio"));
        } else if (total == 1) {
            labelContador.setText(IdiomaUtil.obtener("ctrl.meseros.uno"));
        } else {
            labelContador.setText(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.meseros.varios"), total));
        }
    }
    
    /**
     * Busca meseros por término (nombre o código).
     */
    @FXML
    private void buscarMeseros() {
        aplicarFiltros();
    }
    
    /**
     * Aplica el filtro de búsqueda por texto.
     */
    private void aplicarFiltros() {
        if (listaMeserosCompleta == null) return;
        
        String terminoBusqueda = campoBusqueda.getText().toLowerCase().trim();
        
        List<UsuarioDto> meserosFiltrados = listaMeserosCompleta.stream()
                .filter(m -> {
                    // Filtro de búsqueda por nombre o código
                    return terminoBusqueda.isEmpty() ||
                            m.getNombreCompleto().toLowerCase().contains(terminoBusqueda) ||
                            m.getCodigo().toLowerCase().contains(terminoBusqueda);
                })
                .collect(Collectors.toList());
        
        mostrarMeseros(meserosFiltrados);
        actualizarContador(meserosFiltrados.size());
    }
    
    /**
     * Actualiza la lista de meseros.
     */
    @FXML
    private void actualizarLista() {
        logger.info("Actualizando lista de meseros");
        cargarMeseros();
    }
}
