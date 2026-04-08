/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.componente;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/* Componente de línea divisoria interactiva con efecto de brillo que sigue el mouse.
 Se utiliza para separar secciones en la interfaz con un toque visual atractivo.
 El efecto de brillo se intensifica a medida que el mouse se acerca a la línea y disminuye al alejarse.
 Si el renderizado del Canvas falla (detectado por el handler global de errores), el componente se degrada a una línea estática sin animación para evitar problemas de rendimiento o crashes.
 Además, si el usuario desactiva las animaciones desde la configuración, el componente también muestra una línea estática sin efectos para respetar la preferencia del usuario.
 */
public class LineaDivisoriaInteractiva extends Region implements Animable {

    private final Canvas canvas = new Canvas();
    private double mouseX = -500; 
    private double mouseY = -500;
    private static final double GROSOR_LINEA = 3.0;
    private javafx.geometry.Orientation orientation = javafx.geometry.Orientation.HORIZONTAL;
    private static final Color DORADO_BRILO = Color.web("#d4af37");
    private static final Color DORADO_LINEA_DEFAULT = Color.web("#d4af37", 0.3);

    private final javafx.event.EventHandler<javafx.scene.input.MouseEvent> mouseHandler = e -> {
        mouseX = e.getSceneX();
        mouseY = e.getSceneY();
    };

    private volatile boolean detenido = false;

    public LineaDivisoriaInteractiva() {
        getChildren().add(canvas);
        actualizarDimensiones();
        canvas.setManaged(false);

        MotorAnimaciones motor = MotorAnimaciones.instancia();
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouseHandler);
                motor.desregistrar(this);
            }
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouseHandler);
                motor.registrar(this);
                if (motor.isDesactivadas() || motor.isCanvasCorrupto()) {
                    degradarAEstatico();
                }
            }
        });
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double w = getWidth();
        double h = getHeight();
        if (canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas.setWidth(w);
            canvas.setHeight(h);
        }
        canvas.relocate(0, 0);
    }

    @Override
    public void renderizar() {
        if (detenido) return;
        try {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0 || !canvas.isVisible()) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        gc.setFill(DORADO_LINEA_DEFAULT);
        gc.fillRect(0, 0, w, h);
        double distCentro;
        double localPosCentro;
        
        if (orientation == javafx.geometry.Orientation.HORIZONTAL) {
            double sceneYCentro = localToScene(0, h / 2).getY();
            distCentro = Math.abs(mouseY - sceneYCentro);
            localPosCentro = h / 2;
        } else {
            double sceneXCentro = localToScene(w / 2, 0).getX();
            distCentro = Math.abs(mouseX - sceneXCentro);
            localPosCentro = w / 2;
        }
        
        double umbral = 120.0;
        if (distCentro > umbral) return;
        double intensidad = Math.pow(1.0 - (distCentro / umbral), 2); 
        double radioBrillo = 130.0;
        RadialGradient gradient;
        if (orientation == javafx.geometry.Orientation.HORIZONTAL) {
            double localX = sceneToLocal(mouseX, 0).getX();
            gradient = new RadialGradient(0, 0, localX, localPosCentro, radioBrillo, false, CycleMethod.NO_CYCLE,
                new Stop(0, DORADO_BRILO),
                new Stop(0.6, DORADO_BRILO.deriveColor(0, 1, 1, 0.3)),
                new Stop(1, Color.TRANSPARENT));
        } else {
            double localY = sceneToLocal(0, mouseY).getY();
            gradient = new RadialGradient(0, 0, localPosCentro, localY, radioBrillo, false, CycleMethod.NO_CYCLE,
                new Stop(0, DORADO_BRILO),
                new Stop(0.6, DORADO_BRILO.deriveColor(0, 1, 1, 0.3)),
                new Stop(1, Color.TRANSPARENT));
        }
        gc.setFill(gradient);
        gc.setGlobalAlpha(intensidad * 0.9);
        gc.fillRect(0, 0, w, h);
        gc.setGlobalAlpha(1.0);
        } catch (Exception e) {
            MotorAnimaciones.instancia().marcarCanvasCorrupto();
        }
    }

    @Override
    public boolean esVisible() {
        return isVisible() && getScene() != null && canvas.isVisible();
    }

    @Override
    public void degradarAEstatico() {
        canvas.setVisible(false);
        setStyle("-fx-background-color: rgba(212, 175, 55, 0.3);");
    }

    @Override
    public void restaurarAnimacion() {
        canvas.setVisible(true);
        setStyle("");
    }

    public javafx.geometry.Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(javafx.geometry.Orientation orientation) {
        this.orientation = orientation;
        actualizarDimensiones();
    }

    private void actualizarDimensiones() {
        if (orientation == javafx.geometry.Orientation.HORIZONTAL) {
            setPrefHeight(GROSOR_LINEA);
            setMinHeight(GROSOR_LINEA);
            setMaxHeight(GROSOR_LINEA);
            setPrefWidth(Region.USE_COMPUTED_SIZE);
            setMinWidth(Region.USE_COMPUTED_SIZE);
            setMaxWidth(Double.MAX_VALUE);
        } else {
            setPrefWidth(GROSOR_LINEA);
            setMinWidth(GROSOR_LINEA);
            setMaxWidth(GROSOR_LINEA);
            setPrefHeight(Region.USE_COMPUTED_SIZE);
            setMinHeight(Region.USE_COMPUTED_SIZE);
            setMaxHeight(Double.MAX_VALUE);
        }
    }

    public void detener() {
        detenido = true;
        canvas.setVisible(false);
        MotorAnimaciones.instancia().desregistrar(this);
        if (getScene() != null) {
            getScene().removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouseHandler);
        }
        getChildren().clear();
    }
}
