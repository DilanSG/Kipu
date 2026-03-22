/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.facturacion;

import com.baryx.cliente.modelo.Mesa;
import com.baryx.cliente.servicio.MesaServicio;
import com.baryx.cliente.utilidad.NavegacionUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.TecladoVirtualSimple;
import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.MesaActivaDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controlador para la subvista de Mesas Activas.
 * Solo muestra el grid de mesas, sin tabs ni buscadores.
 */
public class MesasActivasController {

    private static final Logger logger = LoggerFactory.getLogger(MesasActivasController.class);

    @FXML private GridPane gridMesas;
    @FXML private StackPane rootPane;
    @FXML private TextField txtBusqueda;

    private FacturacionController controladorPadre;
    private MesaServicio mesaServicio;
    private AuthRespuestaDto usuarioActual;
    private List<MesaActivaDto> todasLasMesas;

    @FXML
    public void initialize() {
        logger.info("Inicializando MesasActivasController");
        
        usuarioActual = NavegacionUtil.getUsuarioActual();
        if (usuarioActual != null) {
            mesaServicio = new MesaServicio(usuarioActual.getToken());
        }
        
        // Configurar búsqueda
        if (txtBusqueda != null) {
            txtBusqueda.textProperty().addListener((obs, oldVal, newVal) -> filtrarMesas(newVal));
            
            // Configurar Enter para abrir primera mesa filtrada
            txtBusqueda.setOnAction(event -> abrirPrimeraMesaFiltrada());
        }
        
        // Configurar ESC para volver
        if (rootPane != null) {
            rootPane.setFocusTraversable(true);
            rootPane.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    volverAFacturacion();
                }
            });
            // Solicitar foco para que funcione el KeyPress
            Platform.runLater(() -> rootPane.requestFocus());
        }
        
        // Activar teclado virtual para el campo de búsqueda
        Platform.runLater(() -> TecladoVirtualSimple.activar(txtBusqueda));
    }
    
    private void volverAFacturacion() {
        if (controladorPadre != null) {
            logger.info("Volviendo a Facturación por ESC");
            controladorPadre.volverDeSubvista();
        }
    }

    public void setControladorPadre(FacturacionController controladorPadre) {
        this.controladorPadre = controladorPadre;
    }

    public void cargarMesas() {
        if (mesaServicio == null || usuarioActual == null) return;

        logger.info("Cargando mesas activas en subvista");
        gridMesas.getChildren().clear();

        Long idMeseroFiltro = Constantes.Roles.MESERO.equals(usuarioActual.getRol()) 
                ? usuarioActual.getIdUsuario() 
                : null;

        mesaServicio.obtenerMesasActivasAsync(idMeseroFiltro)
                .thenAcceptAsync(mesas -> {
                    todasLasMesas = mesas;
                    if (mesas != null && !mesas.isEmpty()) {
                        mostrarMesasEnGrid(mesas);
                    } else {
                        todasLasMesas = new java.util.ArrayList<>();
                        Label placeholder = new Label(IdiomaUtil.obtener("ctrl.mesas.vacio"));
                        placeholder.getStyleClass().add("mesa-placeholder");
                        gridMesas.add(placeholder, 0, 0);
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    logger.error("Error cargando mesas", ex);
                    Platform.runLater(() -> {
                        if (gridMesas != null && gridMesas.getScene() != null) {
                            NotificacionUtil.mostrarError((Stage) gridMesas.getScene().getWindow(), java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.facturacion.error_mesas"), ex.getCause().getMessage()));
                        }
                    });
                    return null;
                });
    }

    private void mostrarMesasEnGrid(List<MesaActivaDto> mesas) {
        int col = 0;
        int row = 0;
        int maxCols = 5;

        for (MesaActivaDto mesaDto : mesas) {
            VBox card = crearCardMesaActiva(mesaDto);
            gridMesas.add(card, col, row);

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }
    }

    private VBox crearCardMesaActiva(MesaActivaDto mesaDto) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-mesa-activa");
                      
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setCursor(javafx.scene.Cursor.HAND);

        // Mesa number
        Label lblNumero = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.mesas.numero"), mesaDto.getNumeroMesa()));
        lblNumero.getStyleClass().add("mesa-numero");
        card.getChildren().add(lblNumero);

        // Mesero
        if (usuarioActual != null && !Constantes.Roles.MESERO.equals(usuarioActual.getRol())) {
            Label lblMesero = new Label(mesaDto.getMeseroNombre());
            lblMesero.getStyleClass().add("mesa-mesero");
            card.getChildren().add(lblMesero);
        }

        // Time
        if (mesaDto.getFechaCreacion() != null) {
            java.time.format.DateTimeFormatter formato = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
            Label lblHora = new Label(mesaDto.getFechaCreacion().format(formato));
            lblHora.getStyleClass().add("mesa-hora");
            card.getChildren().add(lblHora);
        }

        // Total
        java.text.NumberFormat formatCurrency = java.text.NumberFormat.getInstance(java.util.Locale.of("es", "CO"));
        formatCurrency.setMaximumFractionDigits(0);
        Label lblTotal = new Label("$" + formatCurrency.format(mesaDto.getTotal() != null ? mesaDto.getTotal() : BigDecimal.ZERO));
        lblTotal.getStyleClass().add("mesa-total");
        card.getChildren().add(lblTotal);

        // Items count
        int cantidadItems = mesaDto.getCantidadItems() != null ? mesaDto.getCantidadItems() : 0;
        Label lblItems = new Label(java.text.MessageFormat.format(IdiomaUtil.obtener("ctrl.mesas.items"), cantidadItems));
        lblItems.getStyleClass().add("mesa-items");
        card.getChildren().add(lblItems);

        // Click action
        card.setOnMouseClicked(e -> {
            if (controladorPadre != null) {
                Mesa mesaModelo = new Mesa(
                    mesaDto.getIdMesa(),
                    mesaDto.getNumeroMesa(),
                    mesaDto.getMeseroNombre(),
                    mesaDto.getMeseroId(),  // Usar el ID del mesero del DTO
                    "OCUPADA"
                );
                controladorPadre.abrirDetalleMesaObjeto(mesaModelo);
            }
        });

        return card;
    }
    
    private void filtrarMesas(String textoBusqueda) {
        if (todasLasMesas == null) return;
        
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            mostrarMesasEnGrid(todasLasMesas);
            return;
        }
        
        String busqueda = textoBusqueda.toLowerCase().trim();
        List<MesaActivaDto> mesasFiltradas = todasLasMesas.stream()
            .filter(mesa -> {
                // Filtrar por número de mesa
                String numeroMesa = mesa.getNumeroMesa();
                if (numeroMesa != null && numeroMesa.toLowerCase().contains(busqueda)) {
                    return true;
                }
                
                // Filtrar por nombre de mesero
                String nombreMesero = mesa.getMeseroNombre();
                if (nombreMesero != null && nombreMesero.toLowerCase().contains(busqueda)) {
                    return true;
                }
                
                return false;
            })
            .sorted((m1, m2) -> {
                // Ordenar: primero las que coinciden exactamente con el número
                String num1 = m1.getNumeroMesa();
                String num2 = m2.getNumeroMesa();
                
                boolean num1Exacto = num1.equals(busqueda);
                boolean num2Exacto = num2.equals(busqueda);
                
                if (num1Exacto && !num2Exacto) return -1;
                if (!num1Exacto && num2Exacto) return 1;
                
                // Luego las que empiezan con el texto buscado
                boolean num1Empieza = num1.startsWith(busqueda);
                boolean num2Empieza = num2.startsWith(busqueda);
                
                if (num1Empieza && !num2Empieza) return -1;
                if (!num1Empieza && num2Empieza) return 1;
                
                // Por defecto, ordenar por número de mesa (numéricamente)
                try {
                    return Integer.compare(Integer.parseInt(num1), Integer.parseInt(num2));
                } catch (NumberFormatException e) {
                    // Si no son números, ordenar alfabéticamente
                    return num1.compareTo(num2);
                }
            })
            .collect(java.util.stream.Collectors.toList());
        
        mostrarMesasEnGrid(mesasFiltradas);
    }
    
    private void abrirPrimeraMesaFiltrada() {
        if (gridMesas.getChildren().isEmpty()) return;
        
        // Buscar el primer VBox (card) en el grid
        for (javafx.scene.Node node : gridMesas.getChildren()) {
            if (node instanceof VBox) {
                // Simular click en la primera card
                node.fireEvent(new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY,
                    1,
                    false, false, false, false,
                    true, false, false, false, false, false,
                    null
                ));
                break;
            }
        }
    }
}
