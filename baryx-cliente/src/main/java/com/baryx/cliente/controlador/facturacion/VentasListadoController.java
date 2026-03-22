/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.facturacion;

import com.baryx.common.dto.LineaVentaDto;
import com.baryx.common.dto.PagoDto;
import com.baryx.common.dto.VentaDto;
import com.baryx.cliente.controlador.MenuPrincipalController;
import com.baryx.cliente.controlador.SubvistaController;
import com.baryx.cliente.servicio.VentaServicio;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.cliente.configuracion.ConfiguracionCliente;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Controlador para el listado de ventas de la sesión actual.
 * Muestra las ventas con sus productos, métodos de pago y totales acumulados.
 */
public class VentasListadoController implements SubvistaController {

    private static final Logger logger = LoggerFactory.getLogger(VentasListadoController.class);
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Tabla de ventas (master)
    @FXML private Text tituloVentas;
    @FXML private Label labelResumen;
    @FXML private TableView<VentaDto> tablaVentas;
    @FXML private TableColumn<VentaDto, String> colId;
    @FXML private TableColumn<VentaDto, String> colHora;
    @FXML private TableColumn<VentaDto, String> colMesa;
    @FXML private TableColumn<VentaDto, String> colMesero;
    @FXML private TableColumn<VentaDto, String> colCajero;
    @FXML private TableColumn<VentaDto, String> colSubtotal;
    @FXML private TableColumn<VentaDto, String> colImpoconsumo;
    @FXML private TableColumn<VentaDto, String> colPropina;
    @FXML private TableColumn<VentaDto, String> colTotal;
    @FXML private TableColumn<VentaDto, String> colMetodosPago;

    // Panel detalle (productos de venta seleccionada)
    @FXML private VBox panelDetalle;
    @FXML private Label labelDetalleVenta;
    @FXML private Label labelDetallePagos;
    @FXML private TableView<LineaVentaDto> tablaProductos;
    @FXML private TableColumn<LineaVentaDto, String> colProdNombre;
    @FXML private TableColumn<LineaVentaDto, String> colProdCantidad;
    @FXML private TableColumn<LineaVentaDto, String> colProdPrecio;
    @FXML private TableColumn<LineaVentaDto, String> colProdSubtotal;

    // Totales de sesión
    @FXML private Label labelTotalVentas;
    @FXML private Label labelTotalSubtotal;
    @FXML private Label labelTotalImpoconsumo;
    @FXML private Label labelTotalPropina;
    @FXML private Label labelTotalIngreso;

    private MenuPrincipalController menuPrincipal;
    private VentaServicio ventaServicio;
    private final ObservableList<VentaDto> ventas = FXCollections.observableArrayList();
    private LocalDateTime inicioSesion;

    @Override
    public void setMenuPrincipal(MenuPrincipalController menuPrincipal) {
        this.menuPrincipal = menuPrincipal;
    }

    @FXML
    public void initialize() {
        logger.info("Inicializando VentasListadoController");

        ventaServicio = new VentaServicio(ConfiguracionCliente.getTokenJwt());

        // La sesión de caja se considera desde el inicio del día actual
        inicioSesion = LocalDateTime.now().toLocalDate().atStartOfDay();

        configurarColumnas();
        configurarColumnasDetalle();
        configurarSeleccion();
        cargarVentas();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getIdVenta())));
        colHora.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFechaCreacion() != null
                        ? c.getValue().getFechaCreacion().format(FORMATO_HORA) : ""));
        colMesa.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNumeroMesa() != null ? c.getValue().getNumeroMesa() : ""));
        colMesero.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNombreMesero() != null ? c.getValue().getNombreMesero() : ""));
        colCajero.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNombreCajero() != null ? c.getValue().getNombreCajero() : ""));
        colSubtotal.setCellValueFactory(c -> new SimpleStringProperty(
                formatear(c.getValue().getSubtotal())));
        colImpoconsumo.setCellValueFactory(c -> new SimpleStringProperty(
                formatear(c.getValue().getImpoconsumo())));
        colPropina.setCellValueFactory(c -> new SimpleStringProperty(
                formatear(c.getValue().getPropina())));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(
                formatear(c.getValue().getTotal())));
        colMetodosPago.setCellValueFactory(c -> {
            List<PagoDto> pagos = c.getValue().getPagos();
            if (pagos == null || pagos.isEmpty()) return new SimpleStringProperty("");
            String resumen = pagos.stream()
                    .map(p -> p.getNombreMetodoPago() + " " + formatear(p.getMonto()))
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(resumen);
        });

        tablaVentas.setItems(ventas);
    }

    private void configurarColumnasDetalle() {
        colProdNombre.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNombreProducto() != null ? c.getValue().getNombreProducto() : ""));
        colProdCantidad.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCantidad() != null ? String.valueOf(c.getValue().getCantidad()) : "1"));
        colProdPrecio.setCellValueFactory(c -> new SimpleStringProperty(
                formatear(c.getValue().getPrecioUnitario())));
        colProdSubtotal.setCellValueFactory(c -> {
            BigDecimal precio = c.getValue().getPrecioUnitario() != null ? c.getValue().getPrecioUnitario() : BigDecimal.ZERO;
            int cantidad = c.getValue().getCantidad() != null ? c.getValue().getCantidad() : 1;
            return new SimpleStringProperty(formatear(precio.multiply(BigDecimal.valueOf(cantidad))));
        });
    }

    private void configurarSeleccion() {
        tablaVentas.getSelectionModel().selectedItemProperty().addListener((obs, anterior, seleccionada) -> {
            if (seleccionada != null) {
                mostrarDetalle(seleccionada);
            } else {
                ocultarDetalle();
            }
        });
    }

    private void mostrarDetalle(VentaDto venta) {
        panelDetalle.setVisible(true);
        panelDetalle.setManaged(true);

        // Título del detalle
        String hora = venta.getFechaCreacion() != null ? venta.getFechaCreacion().format(FORMATO_HORA) : "";
        labelDetalleVenta.setText(String.format(
                IdiomaUtil.obtener("ctrl.ventas.detalle.titulo"),
                venta.getIdVenta(), venta.getNumeroMesa(), hora));

        // Resumen de pagos
        if (venta.getPagos() != null && !venta.getPagos().isEmpty()) {
            String pagosStr = venta.getPagos().stream()
                    .map(p -> {
                        String base = p.getNombreMetodoPago() + ": " + formatear(p.getMonto());
                        if (p.getPropina() != null && p.getPropina().compareTo(BigDecimal.ZERO) > 0) {
                            base += " (" + IdiomaUtil.obtener("ctrl.ventas.propina_label") + ": " + formatear(p.getPropina()) + ")";
                        }
                        return base;
                    })
                    .collect(Collectors.joining("  |  "));
            labelDetallePagos.setText(pagosStr);
        } else {
            labelDetallePagos.setText("");
        }

        // Productos
        if (venta.getLineas() != null) {
            tablaProductos.setItems(FXCollections.observableArrayList(venta.getLineas()));
        } else {
            tablaProductos.setItems(FXCollections.observableArrayList());
        }
    }

    private void ocultarDetalle() {
        panelDetalle.setVisible(false);
        panelDetalle.setManaged(false);
    }

    private void cargarVentas() {
        ventaServicio.listarVentasDesdeAsync(inicioSesion)
                .thenAccept(lista -> Platform.runLater(() -> {
                    ventas.setAll(lista);
                    actualizarTotales(lista);
                    logger.info("Ventas cargadas: {}", lista.size());
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        logger.error("Error al cargar ventas", ex);
                        if (tablaVentas.getScene() != null) {
                            Stage stage = (Stage) tablaVentas.getScene().getWindow();
                            NotificacionUtil.mostrarError(stage, IdiomaUtil.obtener("ctrl.ventas.error_cargar"));
                        }
                    });
                    return null;
                });
    }

    private void actualizarTotales(List<VentaDto> lista) {
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        BigDecimal totalImpoconsumo = BigDecimal.ZERO;
        BigDecimal totalPropina = BigDecimal.ZERO;
        BigDecimal totalIngreso = BigDecimal.ZERO;

        for (VentaDto v : lista) {
            if (v.getSubtotal() != null) totalSubtotal = totalSubtotal.add(v.getSubtotal());
            if (v.getImpoconsumo() != null) totalImpoconsumo = totalImpoconsumo.add(v.getImpoconsumo());
            if (v.getPropina() != null) totalPropina = totalPropina.add(v.getPropina());
            if (v.getTotal() != null) totalIngreso = totalIngreso.add(v.getTotal());
        }

        labelTotalVentas.setText(String.valueOf(lista.size()));
        labelTotalSubtotal.setText(formatear(totalSubtotal));
        labelTotalImpoconsumo.setText(formatear(totalImpoconsumo));
        labelTotalPropina.setText(formatear(totalPropina));
        labelTotalIngreso.setText(formatear(totalIngreso));

        labelResumen.setText(String.format(
                IdiomaUtil.obtener("ctrl.ventas.resumen"),
                lista.size(), formatear(totalIngreso)));
    }

    private String formatear(BigDecimal valor) {
        if (valor == null) return "$0";
        NumberFormat nf = NumberFormat.getInstance(Locale.of("es", "CO"));
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        nf.setGroupingUsed(true);
        return "$" + nf.format(valor);
    }
}
