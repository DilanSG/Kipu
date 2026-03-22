/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.configuracion.herramientas;

import com.baryx.cliente.componente.PanelConexionRed;
import com.baryx.cliente.controlador.configuracion.GestorModales;
import com.baryx.cliente.controlador.configuracion.ModalHerramienta;
import javafx.scene.layout.StackPane;

/**
 * Handler que abre el panel de conexión LAN/Nube como overlay
 * desde la vista de configuración.
 */
public class ConexionRedHandler implements ModalHerramienta {

    private final GestorModales gestor;

    public ConexionRedHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        gestor.cerrarModalActual();

        PanelConexionRed panel = new PanelConexionRed();
        StackPane contenedorRaiz = gestor.getContenedorRaiz();
        StackPane overlay = panel.construir(contenedorRaiz, exito -> {}, cerrado -> {});
        contenedorRaiz.getChildren().add(overlay);
    }
}
