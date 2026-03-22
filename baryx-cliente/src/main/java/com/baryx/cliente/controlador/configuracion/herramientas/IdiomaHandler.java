/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.configuracion.herramientas;

import com.baryx.cliente.configuracion.ConfiguracionCliente;
import com.baryx.cliente.controlador.configuracion.GestorModales;
import com.baryx.cliente.controlador.configuracion.ModalHerramienta;
import com.baryx.cliente.servicio.ConfiguracionSistemaServicio;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.IdiomaUtil.IdiomaDisponible;
import com.baryx.cliente.utilidad.NotificacionUtil;
import com.baryx.common.dto.ConfiguracionSistemaDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler para la herramienta de Idioma del Sistema.
 *
 * Permite al ADMIN cambiar el idioma de la interfaz del sistema.
 * El cambio se persiste en el servidor (tabla configuracion_sistema)
 * y se aplica localmente de forma inmediata. Los demás clientes
 * aplicarán el cambio al reiniciar o recargar.
 *
 * Idiomas disponibles: Español, English, Português.
 *
 * @see IdiomaUtil
 * @see ConfiguracionSistemaServicio
 */
public class IdiomaHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(IdiomaHandler.class);

    /** Gestor de modales para mostrar/cerrar el panel */
    private final GestorModales gestor;

    /** Servicio para persistir el idioma en el servidor */
    private final ConfiguracionSistemaServicio configServicio;

    /**
     * Crea el handler de idioma vinculado al gestor de modales.
     *
     * @param gestor Gestor centralizado de modales
     */
    public IdiomaHandler(GestorModales gestor) {
        this.gestor = gestor;
        this.configServicio = new ConfiguracionSistemaServicio();
    }

    /**
     * Abre el modal de configuración de idioma.
     * Muestra un ComboBox con los idiomas disponibles y aplica el cambio
     * al seleccionar uno diferente.
     */
    @Override
    public void abrir() {
        logger.info("Abriendo configuración de Idioma");

        VBox modal = new VBox(14);
        modal.setMaxWidth(480);
        modal.setMaxHeight(360);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header ───
        HBox header = gestor.crearHeaderModal(
                IdiomaUtil.obtener("idioma.titulo"), "icono-cfg-idioma");

        // ─── Descripción ───
        Label descripcion = new Label(IdiomaUtil.obtener("idioma.descripcion"));
        descripcion.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        descripcion.setWrapText(true);

        // ─── Card: Selector de idioma ───
        VBox cardIdioma = construirCardIdioma();

        modal.getChildren().addAll(
                header, gestor.crearSeparador(),
                descripcion, cardIdioma);

        gestor.mostrarModal(modal);
    }

    /**
     * Construye la card con el selector de idioma.
     * Incluye ComboBox con los idiomas disponibles y label de estado.
     *
     * @return VBox con la card de idioma
     */
    private VBox construirCardIdioma() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        // Título de sección
        Label titulo = new Label(IdiomaUtil.obtener("idioma.selector"));
        titulo.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #d4af37;");

        // ComboBox con idiomas disponibles
        ComboBox<IdiomaDisponible> comboIdioma = new ComboBox<>();
        comboIdioma.getItems().addAll(IdiomaUtil.getIdiomasDisponibles());
        comboIdioma.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: #e8e8e8; " +
                "-fx-font-size: 13px; -fx-pref-width: 280; -fx-pref-height: 38; " +
                "-fx-background-radius: 6; -fx-border-color: #404040; -fx-border-radius: 6;");

        // Renderizar cada opción con nombre del idioma
        comboIdioma.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(IdiomaDisponible item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getNombre());
                }
                setStyle("-fx-text-fill: #e8e8e8; -fx-background-color: #2a2a2a; -fx-font-size: 13px;");
            }
        });
        comboIdioma.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(IdiomaDisponible item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getNombre());
                setStyle("-fx-text-fill: #e8e8e8; -fx-font-size: 13px;");
            }
        });

        // Seleccionar el idioma actual
        String codigoActual = ConfiguracionCliente.getIdioma();
        for (IdiomaDisponible idioma : IdiomaUtil.getIdiomasDisponibles()) {
            if (idioma.getCodigo().equals(codigoActual)) {
                comboIdioma.getSelectionModel().select(idioma);
                break;
            }
        }

        // Estado actual
        Label estadoActual = new Label("● " + obtenerNombreIdiomaActual());
        estadoActual.setStyle("-fx-text-fill: #a8b991; -fx-font-size: 11px; -fx-font-weight: 600;");

        // Listener al cambiar idioma
        comboIdioma.setOnAction(e -> {
            IdiomaDisponible seleccionado = comboIdioma.getSelectionModel().getSelectedItem();
            if (seleccionado == null) return;

            String codigoAnterior = ConfiguracionCliente.getIdioma();
            if (seleccionado.getCodigo().equals(codigoAnterior)) return;

            // Aplicar cambio local inmediatamente
            IdiomaUtil.cambiarIdioma(seleccionado.getCodigo());
            ConfiguracionCliente.setIdioma(seleccionado.getCodigo());

            // Actualizar estado visual
            estadoActual.setText("● " + seleccionado.getNombre());

            // Persistir en el servidor (async en hilo separado para no bloquear UI)
            new Thread(() -> {
                try {
                    configServicio.actualizarConfiguracion("idioma", seleccionado.getCodigo());
                    logger.info("Idioma actualizado en servidor: {}", seleccionado.getCodigo());
                } catch (Exception ex) {
                    logger.warn("No se pudo persistir idioma en servidor: {}", ex.getMessage());
                }
            }).start();

            // Notificar al usuario
            Stage stage = gestor.obtenerStage();
            NotificacionUtil.mostrarInfo(stage,
                    IdiomaUtil.obtener("idioma.cambiado", seleccionado.getNombre()));
        });

        // Fila: label + combo
        HBox filaSelector = new HBox(12, comboIdioma);
        filaSelector.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(titulo, filaSelector, estadoActual);
        return card;
    }

    // Obtiene el nombre del idioma activo en su propio idioma
    private String obtenerNombreIdiomaActual() {
        String codigo = ConfiguracionCliente.getIdioma();
        for (IdiomaDisponible idioma : IdiomaUtil.getIdiomasDisponibles()) {
            if (idioma.getCodigo().equals(codigo)) {
                return idioma.getNombre();
            }
        }
        return "Español";
    }
}
