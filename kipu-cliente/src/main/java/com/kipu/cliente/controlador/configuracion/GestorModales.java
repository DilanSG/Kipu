/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestor centralizado de la infraestructura de modales del panel de configuración.
 *
 * Responsabilidades:
 * - Mostrar y cerrar modales overlay sobre el contenedor raíz
 * - Proveer componentes visuales reutilizables (header, separador, labels info)
 * - Gestionar el ciclo de vida de animaciones asociadas a modales
 *
 * Los handlers de herramientas ({@link ModalHerramienta}) reciben una instancia
 * de esta clase para construir y mostrar sus modales sin acoplar la lógica visual.
 *
 * @see ModalHerramienta
 * @see ConfiguracionHerramientasController
 */
public class GestorModales {

    private static final Logger logger = LoggerFactory.getLogger(GestorModales.class);

    /** Estilo inline base para modales luxury (fondo oscuro semi-transparente, borde dorado). */
    public static final String ESTILO_MODAL_LUXURY =
        "-fx-background-color: rgba(18, 18, 18, 0.92); -fx-border-color: rgba(212,175,55,0.4); " +
        "-fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12;";

    // ==================== ESTADO ====================

    /** Contenedor raíz del StackPane sobre el cual se muestran modales */
    private final StackPane contenedorRaiz;

    /** Modal overlay actualmente visible (null si no hay ninguno) */
    private StackPane modalActual;

    /** Lista de todas las animaciones secundarias del modal (topología, etc.) */
    private final List<Animation> animacionesTopologia = new ArrayList<>();

    // ==================== CONSTRUCTOR ====================

    /**
     * Crea un gestor de modales vinculado al contenedor raíz de la vista.
     *
     * @param contenedorRaiz StackPane raíz sobre el cual se montan los overlays
     */
    public GestorModales(StackPane contenedorRaiz) {
        this.contenedorRaiz = contenedorRaiz;
    }

    /** Retorna el StackPane raíz para overlays personalizados. */
    public StackPane getContenedorRaiz() {
        return contenedorRaiz;
    }

    // ==================== MODAL: MOSTRAR / CERRAR ====================

    /**
     * Muestra un modal overlay centrado sobre la vista de configuración.
     * Solo puede haber un modal activo a la vez. Al hacer clic fuera del modal se cierra.
     *
     * @param contenido Panel VBox con el contenido del modal (ya debe tener su estilo)
     */
    public void mostrarModal(VBox contenido) {
        if (modalActual != null) cerrarModalActual();

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75);");
        overlay.setAlignment(Pos.CENTER);
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) cerrarModalActual();
        });

        overlay.getChildren().add(contenido);
        contenedorRaiz.getChildren().add(overlay);
        modalActual = overlay;

        FadeTransition entrada = new FadeTransition(Duration.millis(200), overlay);
        entrada.setFromValue(0);
        entrada.setToValue(1);
        entrada.play();
    }

    /**
     * Cierra el modal activo con animación de salida.
     * Detiene todas las animaciones asociadas al modal.
     */
    public void cerrarModalActual() {
        if (modalActual == null) return;
        // Detener todas las animaciones de topología
        animacionesTopologia.forEach(Animation::stop);
        animacionesTopologia.clear();

        StackPane modal = modalActual;
        modalActual = null;
        FadeTransition salida = new FadeTransition(Duration.millis(150), modal);
        salida.setFromValue(1);
        salida.setToValue(0);
        salida.setOnFinished(e -> contenedorRaiz.getChildren().remove(modal));
        salida.play();
    }

    /**
     * Muestra un overlay interno sobre el modal actual.
     * Útil para sub-modales de confirmación o edición dentro de un modal existente.
     *
     * @param overlay StackPane del overlay a mostrar
     */
    public void mostrarOverlayInterno(StackPane overlay) {
        if (modalActual != null) {
            modalActual.getChildren().add(overlay);
        }
    }

    /**
     * Cierra un overlay interno específico del modal actual.
     *
     * @param overlay StackPane del overlay a cerrar
     */
    public void cerrarOverlayInterno(StackPane overlay) {
        if (modalActual != null) {
            modalActual.getChildren().remove(overlay);
        }
    }

    // ==================== COMPONENTES REUTILIZABLES ====================

    /**
     * Crea el header estándar para un modal de herramienta.
     * Incluye icono SVG, título dorado y botón de cierre ✕.
     *
     * @param titulo     Texto del título
     * @param claseIcono Clase CSS del icono SVG
     * @return HBox configurado como header del modal
     */
    public HBox crearHeaderModal(String titulo, String claseIcono) {
        Region icono = new Region();
        icono.getStyleClass().addAll("config-tile-icono-svg", claseIcono);
        icono.setMinSize(18, 18);
        icono.setPrefSize(18, 18);
        icono.setMaxSize(18, 18);

        Label label = new Label(titulo);
        label.getStyleClass().add("modal-titulo-dorado");

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        Label cerrar = new Label("✕");
        cerrar.getStyleClass().add("panel-cerrar");
        cerrar.setStyle("-fx-padding: 0 4;");
        cerrar.setOnMouseClicked(e -> cerrarModalActual());

        HBox header = new HBox(10, icono, label, espacio, cerrar);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** Crea una línea separadora horizontal dorada sutil. */
    public Region crearSeparador() {
        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: rgba(212, 175, 55, 0.15);");
        return sep;
    }

    /** Crea un label de clave (gris) para grillas de información. */
    public Label crearInfoLabel(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dato-label");
        l.setStyle("-fx-text-fill: #888;");
        l.setMinWidth(120);
        return l;
    }

    /** Crea un label de valor (blanco, bold) para grillas de información. */
    public Label crearInfoValor(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dato-valor");
        l.setStyle("-fx-text-fill: #e8e8e8;");
        return l;
    }

    // ==================== UTILIDADES ====================

    /**
     * Obtiene el Stage principal a partir del contenedor raíz.
     * Útil para handlers que necesitan mostrar notificaciones.
     *
     * @return Stage de la ventana principal
     */
    public Stage obtenerStage() {
        return (Stage) contenedorRaiz.getScene().getWindow();
    }
}
