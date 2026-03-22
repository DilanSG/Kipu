/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.cliente.componente;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

/* Componente de toggle switch personalizado con animación suave.
 Permite al usuario activar o desactivar una opción con un clic, mostrando un cambio visual claro entre los estados ON y OFF.
 El fondo cambia de color (gris oscuro para OFF, dorado para ON) y el círculo se desliza entre la posición izquierda (OFF) y derecha (ON).
 La animación se realiza con TranslateTransition para un movimiento fluido del círculo.
 El estado del toggle se puede vincular a través de la propiedad 'selected' para integrarlo fácilmente con la lógica de la aplicación.*/
public class ToggleSwitch extends StackPane {
    
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Circle circle;
    private final Rectangle background;
    private final TranslateTransition transition;
    private static final double WIDTH = 40;
    private static final double HEIGHT = 20;
    private static final double CIRCLE_RADIUS = 8;
    private static final Color COLOR_OFF = Color.web("#2a2a2a");
    private static final Color COLOR_ON = Color.web("#d4af37");
    private static final double POS_OFF = -WIDTH / 2 + CIRCLE_RADIUS + 4;
    private static final double POS_ON = WIDTH / 2 - CIRCLE_RADIUS - 4;
    
    public ToggleSwitch() {
        background = new Rectangle(WIDTH, HEIGHT);
        background.setArcWidth(HEIGHT);
        background.setArcHeight(HEIGHT);
        background.setFill(COLOR_OFF);
        background.setStroke(Color.web("#555555"));
        background.setStrokeWidth(1);
        circle = new Circle(CIRCLE_RADIUS);
        circle.setFill(Color.web("#f5f5f5"));
        circle.setTranslateX(POS_OFF);
        transition = new TranslateTransition(Duration.millis(200), circle);
        getChildren().addAll(background, circle);
        setOnMouseClicked(event -> { // Click handler para cambiar estado
            selected.set(!selected.get());
            animarToggle();
        });
        
        setStyle("-fx-cursor: hand;");
    }
    
    // Método para animar el toggle al cambiar de estado. Cambia el color de fondo y la posición del círculo con una transición suave.
    private void animarToggle() {
        if (MotorAnimaciones.instancia().isDesactivadas()) {
            posicionarSinAnimacion();
            return;
        }
        if (selected.get()) {
            background.setFill(COLOR_ON);
            background.setStroke(Color.web("#d4af37"));
            transition.setToX(POS_ON);
        } else {
            background.setFill(COLOR_OFF);
            background.setStroke(Color.web("#555555"));
            transition.setToX(POS_OFF);
        }
        transition.play();
    }
    
    // Método para posicionar el toggle sin animación, utilizado al establecer el estado programáticamente para evitar la animación en ese caso.
    private void posicionarSinAnimacion() {
        if (selected.get()) {
            background.setFill(COLOR_ON);
            background.setStroke(Color.web("#d4af37"));
            circle.setTranslateX(POS_ON);
        } else {
            background.setFill(COLOR_OFF);
            background.setStroke(Color.web("#555555"));
            circle.setTranslateX(POS_OFF);
        }
    }
    
    public BooleanProperty selectedProperty() {
        return selected;
    }
    
    public boolean isSelected() {
        return selected.get();
    }
    
    public void setSelected(boolean value) {
        selected.set(value);
        posicionarSinAnimacion();
    }
}
