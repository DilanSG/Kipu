/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.cliente.controlador.configuracion.herramientas;

import com.baryx.cliente.controlador.configuracion.GestorModales;
import com.baryx.cliente.controlador.configuracion.ModalHerramienta;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Handler para la herramienta "Actualizaciones" en el menú de configuración.
 * Muestra un modal con el estado de actualización del sistema, versión actual,
 * fecha de compilación, última verificación y servidor de actualizaciones.
 * El diseño es plano con colores oscuros, diferente al estilo luxury estándar.
 * El botón de búsqueda de actualizaciones muestra una notificación informativa
 * indicando que la función estará disponible próximamente vía www.baryx.org/update. */
public class ActualizacionesHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(ActualizacionesHandler.class);
    private final GestorModales gestor;

    public ActualizacionesHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Abriendo gestor de Actualizaciones");

        VBox modal = new VBox(16);
        modal.setMaxWidth(520);
        modal.setMaxHeight(430);
        modal.setPadding(new Insets(24));
        modal.setStyle("-fx-background-color: #222; -fx-border-color: #444; " +
            "-fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.titulo"));
        titulo.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #e0e0e0;");
        Region espHeader = new Region();
        HBox.setHgrow(espHeader, Priority.ALWAYS);
        Label cerrar = new Label("✕");
        cerrar.setStyle("-fx-font-size: 15px; -fx-text-fill: #666; -fx-cursor: hand;");
        cerrar.setOnMouseClicked(e -> gestor.cerrarModalActual());
        cerrar.setOnMouseEntered(e ->
            cerrar.setStyle("-fx-font-size: 15px; -fx-text-fill: #e0e0e0; -fx-cursor: hand;"));
        cerrar.setOnMouseExited(e ->
            cerrar.setStyle("-fx-font-size: 15px; -fx-text-fill: #666; -fx-cursor: hand;"));
        HBox header = new HBox(10, titulo, espHeader, cerrar);
        header.setAlignment(Pos.CENTER_LEFT);

        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #3a3a3a;");

        VBox estadoBox = new VBox(8);
        estadoBox.setAlignment(Pos.CENTER);
        estadoBox.setPadding(new Insets(20, 0, 20, 0));

        Circle checkCircle = new Circle(28);
        checkCircle.setFill(Color.web("#2d5016"));
        checkCircle.setStroke(Color.web("#5a9e2f"));
        checkCircle.setStrokeWidth(2);
        Label iconCheck = new Label("✓");
        iconCheck.setStyle("-fx-font-size: 26px; -fx-text-fill: #7cc33f; -fx-font-weight: 700;");
        StackPane iconStack = new StackPane(checkCircle, iconCheck);

        Label lblActualizado = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.actualizado"));
        lblActualizado.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #c0c0c0;");
        estadoBox.getChildren().addAll(iconStack, lblActualizado);

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(16);
        infoGrid.setVgap(6);
        infoGrid.setPadding(new Insets(0, 16, 0, 16));

        Label lV = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.version_instalada"));
        lV.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        Label vV = new Label("v1.0.0");
        vV.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label lF = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.fecha"));
        lF.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        Label vF = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.fecha.valor"));
        vF.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label lU = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.ultima"));
        lU.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        Label vU = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.ultima.valor"));
        vU.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label lS = new Label(IdiomaUtil.obtener("ctrl.actualizaciones.servidor"));
        lS.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
        Label vS = new Label("www.baryx.org/update");
        vS.setStyle("-fx-text-fill: #7799cc; -fx-font-size: 12px;");

        infoGrid.addRow(0, lV, vV);
        infoGrid.addRow(1, lF, vF);
        infoGrid.addRow(2, lU, vU);
        infoGrid.addRow(3, lS, vS);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button btnBuscar = new Button(IdiomaUtil.obtener("ctrl.actualizaciones.btn.buscar"));
        btnBuscar.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #ccc; -fx-font-size: 12px; " +
            "-fx-border-color: #555; -fx-border-radius: 4; -fx-background-radius: 4; " +
            "-fx-cursor: hand; -fx-padding: 8 16;");
        btnBuscar.setOnAction(e ->
            NotificacionUtil.mostrarInfo(gestor.obtenerStage(),
                IdiomaUtil.obtener("ctrl.actualizaciones.proximamente")));

        HBox botones = new HBox(12, btnBuscar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        modal.getChildren().addAll(header, sep, estadoBox, infoGrid, spacer, botones);
        gestor.mostrarModal(modal);
    }
}
