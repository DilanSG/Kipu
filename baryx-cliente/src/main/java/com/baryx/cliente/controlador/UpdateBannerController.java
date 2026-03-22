/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 */
package com.baryx.cliente.controlador;

import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador del banner de actualización disponible.
 *
 * <p>Se muestra en la parte superior del shell como overlay no-intrusivo.
 * El usuario puede ir directamente a la página de descargas o cerrarlo.
 */
public class UpdateBannerController {

    private static final Logger logger = LoggerFactory.getLogger(UpdateBannerController.class);

    @FXML private HBox bannerRaiz;
    @FXML private Label lblMensaje;
    @FXML private Button btnDescargar;

    private String urlDescarga;
    private HostServices hostServices;

    // ------------------------------------------------------------------
    // Inicialización externa (llamado por ShellController después de cargar el FXML)
    // ------------------------------------------------------------------

    /**
     * Configura el banner con la información de la actualización.
     *
     * @param versionRemota  Versión nueva disponible
     * @param versionLocal   Versión instalada actualmente
     * @param urlDescarga    URL de la página de descargas
     * @param hostServices   Para abrir el navegador
     */
    public void inicializar(String versionRemota, String versionLocal,
                            String urlDescarga, HostServices hostServices) {
        this.urlDescarga = urlDescarga;
        this.hostServices = hostServices;

        lblMensaje.setText(
            String.format("Nueva versión disponible: v%s  (instalada: v%s)", versionRemota, versionLocal)
        );
        btnDescargar.setText("Descargar v" + versionRemota);
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    @FXML
    private void abrirDescarga() {
        if (urlDescarga != null && hostServices != null) {
            try {
                hostServices.showDocument(urlDescarga);
                logger.info("[Update] Abriendo pagina de descargas: {}", urlDescarga);
            } catch (Exception e) {
                logger.warn("[Update] No se pudo abrir el navegador: {}", e.getMessage());
            }
        }
    }

    @FXML
    private void cerrar() {
        // Quitar el banner del contenedor padre
        if (bannerRaiz.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(bannerRaiz);
        }
        logger.debug("[Update] Banner cerrado por el usuario");
    }
}
