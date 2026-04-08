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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*Componente para el fondo animado de la aplicación, utiliza el MotorAnimaciones para renderizar partículas de polvo dorado que se mueven y reaccionan a la posición del mouse.
- El fondo se compone de un gradiente radial que puede ser personalizado a través de propiedades de color (centro, intermedio, borde) para permitir ajustes desde la configuración o temas.*/
public class FondoAnimado extends Region implements Animable {
    private final Canvas canvas = new Canvas();
    private final List<Particula> particulas = new ArrayList<>();
    private final Random random = new Random();
    private static final int NUM_PARTICULAS = 90;
    private volatile boolean detenido = false;

    private static final Color[] COLORES_PARTICULAS = {
        Color.web("#d4af37", 0.50), 
        Color.web("#d4af37", 0.40), 
        Color.web("#d4af37", 0.30), 
        Color.web("#d4af37", 0.25)  
    };
    
    private static final double RADIO_INTERACCION = 150.0;
    
    private double mouseX = -1000;
    private double mouseY = -1000;

    // Handler para capturar movimiento del mouse en toda la escena
    private final javafx.event.EventHandler<javafx.scene.input.MouseEvent> mouseSceneHandler = event -> {
        javafx.geometry.Point2D localPoint = this.sceneToLocal(event.getSceneX(), event.getSceneY());
        if (localPoint != null) {
            mouseX = localPoint.getX();
            mouseY = localPoint.getY();
        }
    };

    // Propiedades configurables para el fondo (usando JavaFX Properties para compatibilidad con FXML)
    private final ObjectProperty<Color> colorCentro = new SimpleObjectProperty<>(Color.web("#1c1c1c"));
    private final ObjectProperty<Color> colorIntermedio = new SimpleObjectProperty<>(Color.web("#0f0f0f"));
    private final ObjectProperty<Color> colorBorde = new SimpleObjectProperty<>(Color.web("#0a0a0a"));
    private final javafx.beans.property.BooleanProperty mostrarFondo = new javafx.beans.property.SimpleBooleanProperty(true);
    
    // Variables de estado para dimensionamiento eficiente
    private double cacheW = -1;
    private double cacheH = -1;

    public Color getColorCentro() { return colorCentro.get(); }
    public void setColorCentro(Color color) { this.colorCentro.set(color); }
    public ObjectProperty<Color> colorCentroProperty() { return colorCentro; }

    public Color getColorIntermedio() { return colorIntermedio.get(); }
    public void setColorIntermedio(Color color) { this.colorIntermedio.set(color); }
    public ObjectProperty<Color> colorIntermedioProperty() { return colorIntermedio; }

    public Color getColorBorde() { return colorBorde.get(); }
    public void setColorBorde(Color color) { this.colorBorde.set(color); }
    public ObjectProperty<Color> colorBordeProperty() { return colorBorde; }
    
    public boolean isMostrarFondo() { return mostrarFondo.get(); }
    public void setMostrarFondo(boolean value) { this.mostrarFondo.set(value); }
    public javafx.beans.property.BooleanProperty mostrarFondoProperty() { return mostrarFondo; }

    public FondoAnimado() {
        getChildren().add(canvas);
        
        // El Canvas se posiciona manualmente en layoutChildren() para cubrir todo el fondo, por lo que no debe ser gestionado por el layout de JavaFX.
        canvas.setManaged(false);
        // Inicializar partículas
        for (int i = 0; i < NUM_PARTICULAS; i++) {
            particulas.add(new Particula());
        }
        colorCentro.addListener((obs, old, val) -> actualizarFondo());
        colorIntermedio.addListener((obs, old, val) -> actualizarFondo());
        colorBorde.addListener((obs, old, val) -> actualizarFondo());
        mostrarFondo.addListener((obs, old, val) -> actualizarFondo());

        configurarListenersMotor();
        
        setMinSize(0, 0);
        setPrefSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
    
    private double lastDistW = 0; // Variables para detectar cambios significativos en el tamaño y redistribuir partículas, evitando clustering o problemas visuales al cambiar de vista o hacer Alt-Tab.
    private double lastDistH = 0;

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double w = getWidth();
        double h = getHeight();
        
        if (w > 0 && h > 0) { // Actualizar fondo estático si el tamaño cambia, pero solo si es un cambio significativo para evitar overhead en cambios menores.
             if (w != canvas.getWidth() || h != canvas.getHeight()) {
                cacheW = w;
                cacheH = h;
                actualizarFondo();
                
                if (Math.abs(w - lastDistW) > 50 || Math.abs(h - lastDistH) > 50) { // Si el cambio en tamaño es mayor a 50px en cualquier dimensión, redistribuir partículas para evitar clustering o problemas visuales.
                    lastDistW = w;
                    lastDistH = h;
                }
             }
        }
        
        if (canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas.setWidth(w);
            canvas.setHeight(h);
        }
        canvas.relocate(0, 0);
    }
    
    private void redistribuirParticulas(double w, double h) {
        for (Particula p : particulas) {
            p.distribuir(w, h);
        }
    }

    private void configurarListenersMotor() {
        MotorAnimaciones motor = MotorAnimaciones.instancia();

        visibleProperty().addListener((obs, oldVal, newVal) -> motor.notificarCambioVisibilidad());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouseSceneHandler);
                motor.desregistrar(this);
            }
            if (newScene == null) return;

            newScene.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouseSceneHandler);
            motor.registrar(this);
            if (motor.isDesactivadas() || motor.isCanvasCorrupto()) {
                degradarAEstatico();
            }
            newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                if (newWin != null) {
                    newWin.showingProperty().addListener((obsShow, oldShow, isShowing) ->
                        motor.notificarCambioVisibilidad());
                    motor.notificarCambioVisibilidad();
                }
            });
        });
    }

    private boolean inicializado = false;
    private double lastW = 0;
    private double lastH = 0;
    private final long tiempoInicio = System.currentTimeMillis();
    
    private void actualizarFondo() {
        if (cacheW <= 0 || cacheH <= 0) return;
        
        if (mostrarFondo.get()) {
            RadialGradient gradiente = new RadialGradient(
                0, 0, cacheW / 2, cacheH / 2, Math.max(cacheW, cacheH) * 0.8, false, CycleMethod.NO_CYCLE,
                new Stop(0, colorCentro.get()),
                new Stop(0.5, colorIntermedio.get()),
                new Stop(1, colorBorde.get())
            );
            
            this.setBackground(new javafx.scene.layout.Background(
                new javafx.scene.layout.BackgroundFill(gradiente, null, null)
            ));
        } else {
            this.setBackground(javafx.scene.layout.Background.EMPTY);
        }
    }

    @Override
    public void renderizar() {
        if (detenido) return;
        try {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            
            if (w <= 0 || h <= 0 || !canvas.isVisible()) return;
            
            gc.clearRect(0, 0, w, h); // Limpiar el canvas antes de renderizar cada frame
            
            for (Particula p : particulas) {
                double factorInteraccion = calcularFactorInteraccion(p);
                p.actualizar(w, h);
                double opacidadFinal = p.opacidad + (factorInteraccion * 0.5); 
                
                if (opacidadFinal > 1.0) opacidadFinal = 1.0;
                
                double tamanoFinal = p.tamano * (1.0 + factorInteraccion * 0.5);
                
                gc.setGlobalAlpha(opacidadFinal);
                gc.setFill(p.color);
                gc.fillOval(p.x - tamanoFinal, p.y - tamanoFinal, tamanoFinal * 2, tamanoFinal * 2);
            }
            gc.setGlobalAlpha(1.0);
        } catch (Exception e) {
            MotorAnimaciones.instancia().marcarCanvasCorrupto();
        }
    }
    
    // Calcula la intensidad de brillo para un lado según la distancia del mouse a ese lado. Retorna un valor entre 0.0 (lejos) y 1.0 (muy cerca).
    private double calcularFactorInteraccion(Particula p) {
        double dx = p.x - mouseX;
        double dy = p.y - mouseY;
        double distanciaCuadrada = dx * dx + dy * dy;
        double radioCuadrado = RADIO_INTERACCION * RADIO_INTERACCION;
        
        if (distanciaCuadrada < radioCuadrado) {
            double distRatio = distanciaCuadrada / radioCuadrado;
            return 1.0 - distRatio;
        }
        return 0.0;
    }

    @Override
    public boolean esVisible() {
        if (detenido || !isVisible() || getScene() == null) return false;
        return getScene().getWindow() == null || getScene().getWindow().isShowing();
    }

    @Override
    public void degradarAEstatico() {
        canvas.setVisible(false);
    }

    @Override
    public void restaurarAnimacion() {
        if (!detenido) canvas.setVisible(true);
    }

    private class Particula {
        double x, y;
        double vx, vy;
        double tamano;
        double opacidad;
        double velocidadOpacidad;
        Color color;
        Particula() {
            reset(true);
        }

        void distribuir(double w, double h) {
            x = random.nextDouble() * w;
            y = random.nextDouble() * h;
        }

        void reset(boolean inicial) {
            double w = canvas.getWidth(); // Default inicial basado en pantalla para evitar clustering en 0x0
            double h = canvas.getHeight();
            // Si el canvas no tiene tamaño válido (posiblemente porque aún no se ha layout), usar un tamaño grande basado en la pantalla para distribuir las partículas, evitando que todas aparezcan amontonadas en la esquina superior izquierda.
            if (w <= 0 || h <= 0) {
                try {
                    javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
                    if (screen != null) {
                        w = screen.getVisualBounds().getWidth();
                        h = screen.getVisualBounds().getHeight();
                    } else {
                        w = 1920; h = 1080;
                    }
                } catch (Exception e) {
                    w = 1920; h = 1080;
                }
            }
            
            x = random.nextDouble() * w;
            y = random.nextDouble() * h;
            
            if (!inicial) {
                if (random.nextBoolean()) { // Aparecer desde un borde aleatorio 
                    x = random.nextBoolean() ? -10 : w + 10;
                } else {
                    y = random.nextBoolean() ? -10 : h + 10;
                }
            }

            vx = (random.nextDouble() - 0.5) * 0.5;
            vy = (random.nextDouble() - 0.5) * 0.5;
            tamano = 1 + random.nextDouble() * 3;
            opacidad = 0.3 + random.nextDouble() * 0.4; // Opacidad AUMENTADA (Base más visible)
            velocidadOpacidad = 0.005 + random.nextDouble() * 0.01;
            
            color = COLORES_PARTICULAS[random.nextInt(COLORES_PARTICULAS.length)];
        }

        void actualizar(double w, double h) {
            x += vx;
            y += vy;
            
            opacidad += velocidadOpacidad; // Pulsación de opacidad
            if (opacidad > 0.8 || opacidad < 0.3) { // Límites ajustados para mayor visibilidad
                velocidadOpacidad *= -1;
            }

            double margin = 100; // Reposicionar si sale de la pantalla (usar margen para suavidad)
            if (x < -margin) x = w + margin;
            if (x > w + margin) x = -margin;
            if (y < -margin) y = h + margin;
            if (y > h + margin) y = -margin;
        }
    }
    
    public void detener() {
        detenido = true;
        canvas.setVisible(false); // Ocultar ANTES de limpiar para evitar que Prism intente renderizar durante la limpieza
        MotorAnimaciones.instancia().desregistrar(this);

        if (getScene() != null) {
            getScene().removeEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouseSceneHandler);
        }
        
        if (particulas != null) {
            particulas.clear();
        }
        
        getChildren().clear(); // Remover canvas del scene graph para liberar recursos GPU
    }
}
