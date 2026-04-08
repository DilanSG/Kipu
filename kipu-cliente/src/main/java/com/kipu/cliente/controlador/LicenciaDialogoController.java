/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 */
package com.kipu.cliente.controlador;

import com.kipu.cliente.servicio.LicenciaServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador del diálogo de licencia.
 *
 * <p>Muestra un overlay bloqueante con el estado de la licencia:
 * <ul>
 *   <li><b>TRIAL_EXPIRED</b>: trial de 14 días terminado — ingresar key o comprar.</li>
 *   <li><b>EXPIRED</b>: licencia pagada expirada — renovar la misma key.</li>
 *   <li><b>REVOKED</b>: licencia revocada — contactar soporte o comprar nueva.</li>
 *   <li><b>NO_KEY</b>: campo para ingresar key (no debería verse normalmente con trial).</li>
 *   <li><b>OFFLINE</b>: usando caché, permite continuar.</li>
 *   <li><b>ERROR</b>: error, permite continuar con advertencia.</li>
 * </ul>
 */
public class LicenciaDialogoController {

    private static final Logger logger = LoggerFactory.getLogger(LicenciaDialogoController.class);

    @FXML private StackPane fondoOscuro;
    @FXML private Region iconoEstado;
    @FXML private Label lblTitulo;
    @FXML private Label lblMensaje;
    @FXML private VBox panelIngresarKey;
    @FXML private TextField txtLicenseKey;
    @FXML private Label lblErrorKey;
    @FXML private Button btnAccionPrimaria;
    @FXML private Button btnContinuar;
    @FXML private Button btnComprar;
    @FXML private Label lblOfflineInfo;

    private LicenciaServicio.EstadoLicencia estado;
    private HostServices hostServices;
    private Runnable onCerrar;

    // ------------------------------------------------------------------
    // Inicialización
    // ------------------------------------------------------------------

    public void inicializar(LicenciaServicio.ResultadoValidacion resultado,
                            HostServices hostServices,
                            Runnable onCerrar) {
        this.estado = resultado.estado();
        this.hostServices = hostServices;
        this.onCerrar = onCerrar;

        configurarParaEstado(resultado);
    }

    private void configurarParaEstado(LicenciaServicio.ResultadoValidacion resultado) {
        // Ocultar elementos opcionales por defecto
        ocultarElemento(panelIngresarKey);
        ocultarElemento(btnContinuar);
        ocultarElemento(btnComprar);
        ocultarElemento(lblOfflineInfo);

        switch (resultado.estado()) {
            case TRIAL_EXPIRED -> {
                iconoEstado.setStyle("-fx-background-color: #d4af37; -fx-shape: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z';");
                lblTitulo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.trial_expirado_titulo"));
                lblMensaje.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.trial_expirado_msg"));
                // Mostrar campo para ingresar key
                mostrarElemento(panelIngresarKey);
                btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.activar"));
                // Botón secundario para ir a comprar
                mostrarElemento(btnComprar);
                btnComprar.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.comprar"));
            }
            case EXPIRED -> {
                iconoEstado.setStyle("-fx-background-color: #ff6b6b; -fx-shape: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z';");
                lblTitulo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.expirada_titulo"));
                lblMensaje.setText(resultado.mensaje());
                // Campo para reingresar key renovada
                mostrarElemento(panelIngresarKey);
                btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.activar"));
                // Botón para ir a renovar
                mostrarElemento(btnComprar);
                btnComprar.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.renovar"));
            }
            case REVOKED -> {
                iconoEstado.setStyle("-fx-background-color: #ff6b6b; -fx-shape: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 5v6l4.25 2.52.77-1.28-3.52-2.09V7H11z';");
                lblTitulo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.revocada_titulo"));
                lblMensaje.setText(resultado.mensaje());
                btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.ver_planes"));
                mostrarElemento(btnComprar);
                btnComprar.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.comprar"));
            }
            case NO_KEY -> {
                iconoEstado.setStyle("-fx-background-color: #d4af37; -fx-shape: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z';");
                lblTitulo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.activar_titulo"));
                lblMensaje.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.activar_msg"));
                mostrarElemento(panelIngresarKey);
                btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.activar"));
                mostrarElemento(btnComprar);
                btnComprar.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.comprar"));
            }
            case OFFLINE -> {
                iconoEstado.setStyle("-fx-background-color: #d4af37; -fx-shape: 'M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z';");
                lblTitulo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.offline_titulo"));
                lblMensaje.setText(resultado.mensaje());
                btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.reintentar"));
                mostrarElemento(btnContinuar);
                lblOfflineInfo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.offline_info"));
                mostrarElemento(lblOfflineInfo);
            }
            default -> {
                // ERROR u otros — permitir continuar con advertencia
                iconoEstado.setStyle("-fx-background-color: #ff9800; -fx-shape: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z';");
                lblTitulo.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.error_titulo"));
                lblMensaje.setText(resultado.mensaje());
                btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.continuar"));
            }
        }
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    @FXML
    private void accionPrimaria() {
        switch (estado) {
            case TRIAL_EXPIRED, EXPIRED, NO_KEY -> activarConKey();
            case REVOKED -> abrirTienda();
            case OFFLINE -> reintentar();
            default -> cerrar();
        }
    }

    @FXML
    private void continuar() {
        cerrar();
    }

    @FXML
    private void irAComprar() {
        abrirTienda();
    }

    private void abrirTienda() {
        if (hostServices != null) {
            try {
                hostServices.showDocument(LicenciaServicio.getUrlTienda());
            } catch (Exception e) {
                logger.warn("[License] No se pudo abrir navegador: {}", e.getMessage());
            }
        }
    }

    private volatile boolean activandoEnCurso = false;

    private void activarConKey() {
        String key = txtLicenseKey.getText().trim();
        if (key.isBlank()) {
            mostrarErrorKey(IdiomaUtil.obtener("ctrl.licencia.dialog.error_vacia"));
            return;
        }
        if (!key.startsWith("BRX-")) {
            mostrarErrorKey(IdiomaUtil.obtener("ctrl.licencia.dialog.error_formato"));
            return;
        }
        if (activandoEnCurso) return;

        activandoEnCurso = true;
        btnAccionPrimaria.setDisable(true);
        btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.validando"));

        // Activar en hilo de fondo (requiere internet)
        new Thread(() -> {
            var servicio = new LicenciaServicio();
            var resultado = servicio.activarLicencia(key);
            Platform.runLater(() -> {
                activandoEnCurso = false;
                if (resultado.estado() == LicenciaServicio.EstadoLicencia.VALID) {
                    logger.info("[License] Activación exitosa: {}", resultado.estado());
                    cerrar();
                } else {
                    btnAccionPrimaria.setDisable(false);
                    btnAccionPrimaria.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.activar"));
                    mostrarErrorKey(resultado.mensaje());
                }
            });
        }).start();
    }

    private void reintentar() {
        cerrar();
    }

    private void cerrar() {
        if (fondoOscuro.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(fondoOscuro);
        }
        if (onCerrar != null) {
            onCerrar.run();
        }
    }

    private void mostrarErrorKey(String mensaje) {
        lblErrorKey.setText(mensaje);
        mostrarElemento(lblErrorKey);
    }

    private void ocultarElemento(javafx.scene.Node nodo) {
        if (nodo != null) {
            nodo.setVisible(false);
            nodo.setManaged(false);
        }
    }

    private void mostrarElemento(javafx.scene.Node nodo) {
        if (nodo != null) {
            nodo.setVisible(true);
            nodo.setManaged(true);
        }
    }
}
