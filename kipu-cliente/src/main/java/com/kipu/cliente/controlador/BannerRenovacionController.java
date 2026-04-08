/* Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.*/
package com.kipu.cliente.controlador;

import com.kipu.cliente.servicio.LicenciaServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.MessageFormat;

// Controlador del banner de renovación de licencia. Muestra un aviso no-bloqueante cuando faltan pocos días para la expiración.
public class BannerRenovacionController {

    private static final Logger logger = LoggerFactory.getLogger(BannerRenovacionController.class);

    @FXML private HBox root;
    @FXML private Label labelTexto;
    @FXML private Button btnRenovar;
    @FXML private Button btnCerrar;

    private HostServices hostServices;

    public void inicializar(int diasRestantes, HostServices hostServices) {
        this.hostServices = hostServices;

        String textoAviso = MessageFormat.format(
                IdiomaUtil.obtener("ctrl.licencia.aviso_renovacion"), diasRestantes);
        labelTexto.setText(textoAviso);
        btnRenovar.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.renovar"));
    }

    @FXML
    private void abrirTienda() {
        if (hostServices != null) {
            try {
                hostServices.showDocument(LicenciaServicio.getUrlTienda());
            } catch (Exception ex) {
                logger.warn("[License] No se pudo abrir navegador");
            }
        }
    }

    @FXML
    private void cerrarBanner() {
        // Usar ShellController para remover el banner del contenedor raíz
        ShellController shell = ShellController.getInstancia();
        if (shell != null) {
            shell.getContenedorRaiz().getChildren().remove(btnCerrar.getParent());
        }
    }
}
