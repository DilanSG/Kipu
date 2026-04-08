/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.KipuClienteApplication;
import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.utilidad.DetectorPantalla;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NotificacionUtil;
import com.kipu.cliente.utilidad.ResolucionPerfil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

/**
 * Handler para la herramienta de Pantalla y Resolución.
 * Permite al ADMIN seleccionar manualmente el perfil de resolución
 * o dejarlo en automático, y ajustar la escala de interfaz con un slider.
 */
public class PantallaResolucionHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(PantallaResolucionHandler.class);

    private final GestorModales gestor;

    public PantallaResolucionHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Abriendo configuración de Pantalla y Resolución");

        DetectorPantalla detector = KipuClienteApplication.getDetectorPantalla();
        if (detector == null) {
            NotificacionUtil.mostrarError(gestor.obtenerStage(),
                    IdiomaUtil.obtener("ctrl.resolucion.error_detector"));
            return;
        }

        VBox modal = new VBox(14);
        modal.setMaxWidth(540);
        modal.setMaxHeight(520);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header ───
        HBox header = gestor.crearHeaderModal(
                IdiomaUtil.obtener("ctrl.resolucion.titulo"), "icono-cfg-graficos");

        // ─── Info de pantalla detectada ───
        VBox cardInfo = construirCardInfo(detector);

        // ─── Selector de perfil ───
        VBox cardPerfil = construirCardPerfil(detector);

        // ─── Slider de escala ───
        VBox cardEscala = construirCardEscala(detector);

        // ─── Nota ───
        Label nota = new Label(IdiomaUtil.obtener("ctrl.resolucion.requiere_reinicio"));
        nota.getStyleClass().add("texto-hint");
        nota.setStyle("-fx-padding: 4 0 0 0;");
        nota.setWrapText(true);

        modal.getChildren().addAll(
                header, gestor.crearSeparador(),
                cardInfo, cardPerfil, cardEscala,
                gestor.crearSeparador(), nota);

        gestor.mostrarModal(modal);
    }

    /** Construye la card con información de la pantalla detectada */
    private VBox construirCardInfo(DetectorPantalla detector) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.resolucion.info_titulo"));
        titulo.getStyleClass().add("panel-seccion-titulo-sm");

        // Resolución detectada
        String textoResolucion = MessageFormat.format(
                IdiomaUtil.obtener("ctrl.resolucion.detectada"),
                String.valueOf((int) detector.getAnchoDetectado()),
                String.valueOf((int) detector.getAltoDetectado()),
                String.format("%.2f", detector.getRatioDetectado()));

        Label labelResolucion = new Label(textoResolucion);
        labelResolucion.getStyleClass().add("texto-secundario-sm");
        labelResolucion.setStyle("-fx-text-fill: #e8e8e8;");

        // DPI y escala OS
        String textoDpi = MessageFormat.format(
                IdiomaUtil.obtener("ctrl.resolucion.dpi"),
                String.valueOf((int) detector.getDpiDetectado()),
                String.valueOf((int) (detector.getEscalaOsDetectada() * 100)));

        Label labelDpi = new Label(textoDpi);
        labelDpi.getStyleClass().add("texto-hint-sm");
        labelDpi.setStyle("-fx-text-fill: #888;");

        // Perfil activo
        Label labelPerfil = new Label(
                IdiomaUtil.obtener("ctrl.resolucion.perfil_activo") + ": " +
                        detector.getPerfilActivo().getNombreLocalizado());
        labelPerfil.getStyleClass().add("texto-hint-sm");
        labelPerfil.setStyle("-fx-text-fill: #a8b991; -fx-font-weight: 600;");

        card.getChildren().addAll(titulo, labelResolucion, labelDpi, labelPerfil);
        return card;
    }

    /** Construye la card con el selector de perfil */
    private VBox construirCardPerfil(DetectorPantalla detector) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.resolucion.perfil_label"));
        titulo.getStyleClass().add("panel-seccion-titulo-sm");

        ComboBox<String> comboPerfil = new ComboBox<>();
        comboPerfil.getStyleClass().add("combo-formulario");
        comboPerfil.setMaxWidth(Double.MAX_VALUE);

        // Opción "Automático"
        comboPerfil.getItems().add("AUTO");
        // Todos los perfiles
        for (ResolucionPerfil perfil : ResolucionPerfil.values()) {
            comboPerfil.getItems().add(perfil.name());
        }

        // Seleccionar el actual
        String perfilActual = ConfiguracionCliente.getPerfilPantalla();
        comboPerfil.setValue(perfilActual);

        // Cell factory para mostrar nombres localizados
        comboPerfil.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if ("AUTO".equals(item)) {
                    setText(IdiomaUtil.obtener("ctrl.resolucion.auto"));
                } else {
                    try {
                        setText(ResolucionPerfil.valueOf(item).getNombreLocalizado());
                    } catch (IllegalArgumentException e) {
                        setText(item);
                    }
                }
            }
        });

        comboPerfil.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if ("AUTO".equals(item)) {
                    setText(IdiomaUtil.obtener("ctrl.resolucion.auto"));
                } else {
                    try {
                        setText(ResolucionPerfil.valueOf(item).getNombreLocalizado());
                    } catch (IllegalArgumentException e) {
                        setText(item);
                    }
                }
            }
        });

        // Listener de cambio
        comboPerfil.setOnAction(e -> {
            String seleccion = comboPerfil.getValue();
            if ("AUTO".equals(seleccion)) {
                detector.setPerfil(null);
            } else {
                try {
                    detector.setPerfil(ResolucionPerfil.valueOf(seleccion));
                } catch (IllegalArgumentException ex) {
                    logger.warn("Perfil no válido: {}", seleccion);
                }
            }
            logger.info("Perfil de pantalla cambiado a: {}", seleccion);
            NotificacionUtil.mostrarInfo(gestor.obtenerStage(),
                    IdiomaUtil.obtener("ctrl.resolucion.requiere_reinicio"));
        });

        card.getChildren().addAll(titulo, comboPerfil);
        return card;
    }

    /** Construye la card con el slider de escala manual */
    private VBox construirCardEscala(DetectorPantalla detector) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.resolucion.escala_label"));
        titulo.getStyleClass().add("panel-seccion-titulo-sm");

        // Label del valor actual
        Label labelValor = new Label(String.format("%.0f%%", detector.getEscalaManual() * 100));
        labelValor.getStyleClass().add("panel-seccion-titulo");
        labelValor.setStyle("-fx-text-fill: #e8e8e8;");

        Slider sliderEscala = new Slider(50, 200, detector.getEscalaManual() * 100);
        sliderEscala.setMajorTickUnit(25);
        sliderEscala.setMinorTickCount(4);
        sliderEscala.setShowTickLabels(true);
        sliderEscala.setShowTickMarks(true);
        sliderEscala.setBlockIncrement(5);
        sliderEscala.setSnapToTicks(true);
        sliderEscala.getStyleClass().add("slider-escala");

        // Fila: título + valor
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox filaTitulo = new HBox(8, titulo, espacio, labelValor);
        filaTitulo.setAlignment(Pos.CENTER_LEFT);

        // Fila: min/ max labels
        Label labelMin = new Label("50%");
        labelMin.getStyleClass().add("texto-hint");
        labelMin.setStyle("-fx-text-fill: #666;");
        Label labelMax = new Label("200%");
        labelMax.getStyleClass().add("texto-hint");
        labelMax.setStyle("-fx-text-fill: #666;");
        Region espacioSlider = new Region();
        HBox.setHgrow(espacioSlider, Priority.ALWAYS);
        HBox filaLabels = new HBox(labelMin, espacioSlider, labelMax);

        // Botones Aplicar y Restaurar
        HBox filaBotones = construirBotonesEscala(sliderEscala, labelValor, detector);

        // Listener en tiempo real
        sliderEscala.valueProperty().addListener((obs, old, nuevo) ->
                labelValor.setText(String.format("%.0f%%", nuevo.doubleValue())));

        card.getChildren().addAll(filaTitulo, sliderEscala, filaBotones);
        return card;
    }

    /** Construye los botones Aplicar y Restaurar para el slider */
    private HBox construirBotonesEscala(Slider slider, Label labelValor, DetectorPantalla detector) {
        javafx.scene.control.Button btnAplicar = new javafx.scene.control.Button(
                IdiomaUtil.obtener("ctrl.resolucion.aplicar"));
        btnAplicar.getStyleClass().add("herramienta-btn");
        btnAplicar.setOnAction(e -> {
            double nuevaEscala = slider.getValue() / 100.0;
            detector.setEscalaManual(nuevaEscala);
            logger.info("Escala manual aplicada: {}%", (int) (nuevaEscala * 100));
            NotificacionUtil.mostrarInfo(gestor.obtenerStage(),
                    IdiomaUtil.obtener("ctrl.resolucion.requiere_reinicio"));
        });

        javafx.scene.control.Button btnRestaurar = new javafx.scene.control.Button(
                IdiomaUtil.obtener("ctrl.resolucion.restaurar"));
        btnRestaurar.getStyleClass().add("herramienta-btn-secundario");
        btnRestaurar.setOnAction(e -> {
            slider.setValue(100);
            labelValor.setText("100%");
            detector.setEscalaManual(1.0);
            logger.info("Escala manual restaurada a 100%");
        });

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        HBox fila = new HBox(10, espacio, btnRestaurar, btnAplicar);
        fila.setAlignment(Pos.CENTER_RIGHT);
        fila.setPadding(new Insets(4, 0, 0, 0));
        return fila;
    }
}
