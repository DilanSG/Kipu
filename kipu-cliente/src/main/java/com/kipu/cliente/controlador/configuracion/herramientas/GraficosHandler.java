/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.componente.MotorAnimaciones;
import com.kipu.cliente.componente.ToggleSwitch;
import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler para la herramienta de Gráficos y Rendimiento.
 *
 * Permite al usuario configurar opciones visuales del sistema:
 * - Activar/desactivar animaciones (FondoAnimado y LineaDivisoriaInteractiva)
 * - Alternar entre pantalla completa y ventana maximizada
 *
 * Los cambios se aplican de forma inmediata y se persisten en disco
 * a través de {@link ConfiguracionCliente} para que se mantengan al reiniciar.
 *
 * @see ConfiguracionCliente#isAnimacionesActivas()
 * @see ConfiguracionCliente#isPantallaCompleta()
 * @see MotorAnimaciones#setDesactivadas(boolean)
 */
public class GraficosHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(GraficosHandler.class);

    /** Gestor de modales para mostrar/cerrar el panel */
    private final GestorModales gestor;

    /**
     * Crea el handler de gráficos vinculado al gestor de modales.
     *
     * @param gestor Gestor centralizado de modales
     */
    public GraficosHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    /**
     * Abre el modal de Gráficos y Rendimiento.
     * Muestra toggles para animaciones y pantalla completa,
     * con descripciones claras y efecto inmediato al cambiar.
     */
    @Override
    public void abrir() {
        logger.info("Abriendo configuración de Gráficos y Rendimiento");

        VBox modal = new VBox(14);
        modal.setMaxWidth(520);
        modal.setMaxHeight(460);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header ───
        HBox header = gestor.crearHeaderModal(IdiomaUtil.obtener("ctrl.config.graficos.header"), "icono-cfg-graficos");

        // ─── Descripción general ───
        Label descripcion = new Label(
            IdiomaUtil.obtener("ctrl.config.graficos.descripcion"));
        descripcion.getStyleClass().add("texto-hint-sm");
        descripcion.setStyle("-fx-text-fill: #888;");
        descripcion.setWrapText(true);

        // ─── Card: Animaciones ───
        VBox cardAnimaciones = construirCardAnimaciones();

        // ─── Card: Pantalla ───
        VBox cardPantalla = construirCardPantalla();

        // ─── Nota de rendimiento ───
        Label nota = new Label(
            IdiomaUtil.obtener("ctrl.config.graficos.nota"));
        nota.getStyleClass().add("texto-hint");
        nota.setStyle("-fx-padding: 4 0 0 0;");
        nota.setWrapText(true);

        modal.getChildren().addAll(
            header, gestor.crearSeparador(),
            descripcion, cardAnimaciones, cardPantalla,
            gestor.crearSeparador(), nota);

        gestor.mostrarModal(modal);
    }

    /**
     * Construye la card de configuración de animaciones.
     * Incluye toggle switch y descripción de qué elementos se afectan.
     *
     * @return VBox con la card de animaciones
     */
    private VBox construirCardAnimaciones() {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        // Título de sección
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.config.animaciones.titulo"));
        titulo.getStyleClass().add("panel-seccion-titulo-sm");

        // Fila: label + toggle
        Label labelToggle = new Label(IdiomaUtil.obtener("ctrl.config.animaciones.desc"));
        labelToggle.getStyleClass().add("texto-secundario-sm");
        labelToggle.setStyle("-fx-text-fill: #e8e8e8;");

        ToggleSwitch toggleAnimaciones = new ToggleSwitch();
        toggleAnimaciones.setSelected(ConfiguracionCliente.isAnimacionesActivas());

        Region espacioToggle = new Region();
        HBox.setHgrow(espacioToggle, Priority.ALWAYS);

        HBox filaToggle = new HBox(12, labelToggle, espacioToggle, toggleAnimaciones);
        filaToggle.setAlignment(Pos.CENTER_LEFT);

        // Descripción
        Label desc = new Label(
            IdiomaUtil.obtener("ctrl.config.animaciones.info"));
        desc.getStyleClass().add("texto-hint");
        desc.setStyle("-fx-text-fill: #666;");
        desc.setWrapText(true);

        // Estado actual
        Label estadoActual = new Label(
            ConfiguracionCliente.isAnimacionesActivas() ? IdiomaUtil.obtener("ctrl.config.animaciones.activas") : IdiomaUtil.obtener("ctrl.config.animaciones.desactivas"));
        estadoActual.getStyleClass().add("texto-hint-sm");
        estadoActual.setStyle(ConfiguracionCliente.isAnimacionesActivas()
            ? "-fx-text-fill: #a8b991; -fx-font-weight: 600;"
            : "-fx-text-fill: #888; -fx-font-weight: 600;");

        // Listener del toggle
        toggleAnimaciones.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean activas = newVal;
            logger.info("Animaciones {}", activas ? "activadas" : "desactivadas");

            // Persistir configuración
            ConfiguracionCliente.setAnimacionesActivas(activas);

            // Aplicar inmediatamente a los componentes
            MotorAnimaciones.instancia().setDesactivadas(!activas);

            // Actualizar label de estado
            estadoActual.setText(activas ? IdiomaUtil.obtener("ctrl.config.animaciones.activas") : IdiomaUtil.obtener("ctrl.config.animaciones.desactivas"));
            estadoActual.setStyle(activas
                ? "-fx-text-fill: #a8b991; -fx-font-weight: 600;"
                : "-fx-text-fill: #888; -fx-font-weight: 600;");

            // Notificar al usuario que el cambio requiere cambiar de vista para verse
            Stage stage = gestor.obtenerStage();
            NotificacionUtil.mostrarInfo(stage,
                activas ? IdiomaUtil.obtener("ctrl.config.animaciones.activadas")
                        : IdiomaUtil.obtener("ctrl.config.animaciones.desactivadas"));
        });

        card.getChildren().addAll(titulo, filaToggle, desc, estadoActual);
        return card;
    }

    /**
     * Construye la card de configuración de pantalla completa.
     * Incluye toggle switch que alterna entre fullscreen y ventana maximizada.
     *
     * @return VBox con la card de pantalla
     */
    private VBox construirCardPantalla() {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        // Título de sección
        Label titulo = new Label(IdiomaUtil.obtener("ctrl.config.pantalla.titulo"));
        titulo.getStyleClass().add("panel-seccion-titulo-sm");

        // Fila: label + toggle
        Label labelToggle = new Label(IdiomaUtil.obtener("ctrl.config.pantalla.completa"));
        labelToggle.getStyleClass().add("texto-secundario-sm");
        labelToggle.setStyle("-fx-text-fill: #e8e8e8;");

        ToggleSwitch togglePantalla = new ToggleSwitch();
        togglePantalla.setSelected(ConfiguracionCliente.isPantallaCompleta());

        Region espacioToggle = new Region();
        HBox.setHgrow(espacioToggle, Priority.ALWAYS);

        HBox filaToggle = new HBox(12, labelToggle, espacioToggle, togglePantalla);
        filaToggle.setAlignment(Pos.CENTER_LEFT);

        // Descripción
        Label desc = new Label(
            IdiomaUtil.obtener("ctrl.config.pantalla.desc"));
        desc.getStyleClass().add("texto-hint");
        desc.setStyle("-fx-text-fill: #666;");
        desc.setWrapText(true);

        // Estado actual
        Label estadoActual = new Label(
            ConfiguracionCliente.isPantallaCompleta() ? IdiomaUtil.obtener("ctrl.config.pantalla.estado_completa") : IdiomaUtil.obtener("ctrl.config.pantalla.estado_ventana"));
        estadoActual.getStyleClass().add("texto-hint-sm");
        estadoActual.setStyle(ConfiguracionCliente.isPantallaCompleta()
            ? "-fx-text-fill: #a8b991; -fx-font-weight: 600;"
            : "-fx-text-fill: #888; -fx-font-weight: 600;");

        // Listener del toggle
        togglePantalla.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean completa = newVal;
            logger.info("Pantalla completa {}", completa ? "activada" : "desactivada");

            // Persistir configuración
            ConfiguracionCliente.setPantallaCompleta(completa);

            // Aplicar inmediatamente al Stage actual
            Stage stage = gestor.obtenerStage();
            if (completa) {
                stage.setFullScreenExitHint("");
                stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
                stage.setFullScreen(true);
            } else {
                stage.setFullScreen(false);
                stage.setMaximized(true);
            }

            // Actualizar label de estado
            estadoActual.setText(completa ? IdiomaUtil.obtener("ctrl.config.pantalla.estado_completa") : IdiomaUtil.obtener("ctrl.config.pantalla.estado_ventana"));
            estadoActual.setStyle(completa
                ? "-fx-text-fill: #a8b991; -fx-font-weight: 600;"
                : "-fx-text-fill: #888; -fx-font-weight: 600;");
        });

        card.getChildren().addAll(titulo, filaToggle, desc, estadoActual);
        return card;
    }
}
