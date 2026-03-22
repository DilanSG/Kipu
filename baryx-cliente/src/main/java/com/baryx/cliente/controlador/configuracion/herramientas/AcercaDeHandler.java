/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.cliente.controlador.configuracion.herramientas;

import com.baryx.cliente.controlador.configuracion.GestorModales;
import com.baryx.cliente.controlador.configuracion.ModalHerramienta;
import com.baryx.cliente.utilidad.IdiomaUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Handler para la herramienta "Acerca de Baryx" en el menú de configuración.
 *Muestra un modal con información sobre el sistema, versión, tecnologías usadas y créditos.
 *Utiliza el GestorModales para construir y mostrar la interfaz visual. */
public class AcercaDeHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(AcercaDeHandler.class);
    private final GestorModales gestor;

    public AcercaDeHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Mostrando Acerca de Baryx");

        VBox modal = new VBox(10);
        modal.setMaxWidth(480);
        modal.setMaxHeight(560);
        modal.setPadding(new Insets(32, 32, 24, 32));
        modal.setAlignment(Pos.TOP_CENTER);
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        ImageView logo = new ImageView();
        try {
            logo.setImage(new Image(
                getClass().getResourceAsStream("/imagenes/LOGOPNG.png")));
            logo.setFitWidth(180);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
        } catch (Exception e) {
            logger.warn("No se pudo cargar el logo: {}", e.getMessage());
        }

        Label version = new Label("v1.0.0");
        version.setStyle("-fx-font-size: 13px; -fx-text-fill: #d4af37; -fx-font-weight: 600; " +
            "-fx-alignment: center;");
        version.setTextAlignment(TextAlignment.CENTER);
        version.setMaxWidth(Double.MAX_VALUE);

        Label subtitulo = new Label(
            IdiomaUtil.obtener("ctrl.acerca.descripcion"));
        subtitulo.setStyle("-fx-font-size: 12px; -fx-text-fill: #999; -fx-alignment: center;");
        subtitulo.setWrapText(true);
        subtitulo.setTextAlignment(TextAlignment.CENTER);
        subtitulo.setMaxWidth(Double.MAX_VALUE);

        Label operacion = new Label(IdiomaUtil.obtener("ctrl.acerca.operacion"));
        operacion.setStyle("-fx-font-size: 11px; -fx-text-fill: #777; -fx-alignment: center;");
        operacion.setTextAlignment(TextAlignment.CENTER);
        operacion.setMaxWidth(Double.MAX_VALUE);

        Label arquitectura = new Label(IdiomaUtil.obtener("ctrl.acerca.arquitectura"));
        arquitectura.setStyle("-fx-font-size: 11px; -fx-text-fill: #777; -fx-alignment: center;");
        arquitectura.setTextAlignment(TextAlignment.CENTER);
        arquitectura.setMaxWidth(Double.MAX_VALUE);

        Label tTec = new Label(IdiomaUtil.obtener("ctrl.acerca.tecnologias"));
        tTec.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #888; " +
            "-fx-alignment: center;");
        tTec.setTextAlignment(TextAlignment.CENTER);
        tTec.setMaxWidth(Double.MAX_VALUE);

        Label tecnologias = new Label(
            "Java 21  ·  JavaFX 21  ·  Spring Boot 3.2\n" +
            "PostgreSQL 15+  ·  Flyway  ·  MapStruct  ·  Lombok");
        tecnologias.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-alignment: center;");
        tecnologias.setTextAlignment(TextAlignment.CENTER);
        tecnologias.setWrapText(true);
        tecnologias.setMaxWidth(Double.MAX_VALUE);

        Label tCred = new Label(IdiomaUtil.obtener("ctrl.acerca.desarrollado_por"));
        tCred.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #888; " +
            "-fx-alignment: center;");
        tCred.setTextAlignment(TextAlignment.CENTER);
        tCred.setMaxWidth(Double.MAX_VALUE);

        Label autor = new Label("Dilan Acuña");
        autor.setStyle("-fx-font-size: 13px; -fx-text-fill: #e8e8e8; -fx-font-weight: 600; " +
            "-fx-alignment: center;");
        autor.setTextAlignment(TextAlignment.CENTER);
        autor.setMaxWidth(Double.MAX_VALUE);

        Label legal = new Label(
            IdiomaUtil.obtener("ctrl.acerca.derechos"));
        legal.setStyle("-fx-font-size: 10px; -fx-text-fill: #555; -fx-alignment: center;");
        legal.setTextAlignment(TextAlignment.CENTER);
        legal.setWrapText(true);
        legal.setMaxWidth(Double.MAX_VALUE);

        modal.getChildren().addAll(logo, version, subtitulo, gestor.crearSeparador(), operacion, arquitectura, gestor.crearSeparador(), tTec, tecnologias, gestor.crearSeparador(), tCred, autor, gestor.crearSeparador(), legal);
        gestor.mostrarModal(modal);
    }
}
