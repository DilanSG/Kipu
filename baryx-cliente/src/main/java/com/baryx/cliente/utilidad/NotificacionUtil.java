/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.utilidad;

import com.baryx.cliente.componente.MotorAnimaciones;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilidad para mostrar notificaciones tipo toast/snackbar en pantalla completa.
 * 
 * Características:
 * - No interrumpen la experiencia del usuario (no son modales)
 * - Aparecen como overlay en la parte superior de la ventana
 * - Se auto-ocultan después de unos segundos
 * - Animaciones suaves de entrada y salida
 * - Estilo luxury: fondo oscuro con bordes dorados
 * - Diferentes tipos: éxito, error, advertencia, información
 * 
 * Uso:
 * ```java
 * NotificacionUtil.mostrarExito(stage, "Mesa creada exitosamente");
 * NotificacionUtil.mostrarError(stage, "Error al procesar el pedido");
 * NotificacionUtil.mostrarAdvertencia(stage, "La mesa ya existe");
 * NotificacionUtil.mostrarInfo(stage, "Producto agregado al pedido");
 * ```
 */
public class NotificacionUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificacionUtil.class);
    
    // Duraciones de animación
    private static final Duration DURACION_FADE_IN = Duration.millis(300);
    private static final Duration DURACION_MOSTRAR = Duration.seconds(3);
    private static final Duration DURACION_FADE_OUT = Duration.millis(300);
    
    /**
     * Tipo de notificación que determina el icono a mostrar.
     * Todas comparten el mismo diseño luxury (negro + dorado).
     */
    public enum TipoNotificacion {
        EXITO("notificacion-exito", "✓"),
        ERROR("notificacion-error", "✕"),
        ADVERTENCIA("notificacion-advertencia", "!"),
        INFO("notificacion-info", "ℹ");
        
        private final String estiloCSS;
        private final String icono;
        
        TipoNotificacion(String estiloCSS, String icono) {
            this.estiloCSS = estiloCSS;
            this.icono = icono;
        }
        
        public String getEstiloCSS() {
            return estiloCSS;
        }
        
        public String getIcono() {
            return icono;
        }
    }
    
    /**
     * Muestra una notificación de éxito.
     * 
     * @param stage Stage donde se mostrará la notificación
     * @param mensaje Mensaje a mostrar
     */
    public static void mostrarExito(Stage stage, String mensaje) {
        mostrar(stage, mensaje, TipoNotificacion.EXITO);
    }
    
    /**
     * Muestra una notificación de error.
     * 
     * @param stage Stage donde se mostrará la notificación
     * @param mensaje Mensaje a mostrar
     */
    public static void mostrarError(Stage stage, String mensaje) {
        mostrar(stage, mensaje, TipoNotificacion.ERROR);
    }
    
    /**
     * Muestra una notificación de advertencia.
     * 
     * @param stage Stage donde se mostrará la notificación
     * @param mensaje Mensaje a mostrar
     */
    public static void mostrarAdvertencia(Stage stage, String mensaje) {
        mostrar(stage, mensaje, TipoNotificacion.ADVERTENCIA);
    }
    
    /**
     * Muestra una notificación informativa.
     * 
     * @param stage Stage donde se mostrará la notificación
     * @param mensaje Mensaje a mostrar
     */
    public static void mostrarInfo(Stage stage, String mensaje) {
        mostrar(stage, mensaje, TipoNotificacion.INFO);
    }
    
    /**
     * Muestra una notificación en pantalla con animación.
     * 
     * La notificación aparece en la parte superior central de la ventana,
     * se muestra durante unos segundos y luego desaparece automáticamente.
     * 
     * @param stage Stage donde se mostrará la notificación
     * @param mensaje Mensaje a mostrar
     * @param tipo Tipo de notificación (éxito, error, advertencia, info)
     */
    private static void mostrar(Stage stage, String mensaje, TipoNotificacion tipo) {
        if (stage == null || stage.getScene() == null) {
            logger.warn("No se puede mostrar notificación: stage o scene es null");
            return;
        }
        
        try {
            Scene scene = stage.getScene();
            
            // Obtener el root de la escena
            if (!(scene.getRoot() instanceof Pane)) {
                logger.warn("El root de la escena no es un Pane, no se puede agregar notificación");
                return;
            }
            
            Pane rootPane = (Pane) scene.getRoot();
            
            // Crear un StackPane overlay si no existe
            StackPane overlayPane = obtenerOCrearOverlay(rootPane);
            
            if (overlayPane == null) {
                logger.warn("No se pudo crear el overlay para notificaciones");
                return;
            }
            
            // Crear la notificación
            HBox notificacion = crearNotificacion(mensaje, tipo);
            
            // Configurar para que no bloquee la UI
            notificacion.setMouseTransparent(false); // La notificación sí puede recibir clicks
            notificacion.setPickOnBounds(false); // Solo el contenido real es clickeable
            
            // Agregar al overlay
            overlayPane.getChildren().add(notificacion);
            StackPane.setAlignment(notificacion, Pos.TOP_CENTER);
            StackPane.setMargin(notificacion, new Insets(20, 20, 0, 20));
            
            // Asegurar que el overlay no bloquee la interacción
            overlayPane.setPickOnBounds(false);
            overlayPane.setMouseTransparent(false);
            
            // Animar entrada
            animarEntrada(notificacion, () -> {
                // Después de mostrar, esperar y animar salida
                PauseTransition pausa = new PauseTransition(DURACION_MOSTRAR);
                pausa.setOnFinished(event -> {
                    animarSalida(notificacion, () -> {
                        // Eliminar del contenedor
                        overlayPane.getChildren().remove(notificacion);
                    });
                });
                pausa.play();
            });
            
            logger.debug("Notificación mostrada: {} - {}", tipo, mensaje);
            
        } catch (Exception e) {
            logger.error("Error al mostrar notificación: ", e);
        }
    }
    
    /**
     * Obtiene o crea un StackPane overlay para las notificaciones.
     * 
     * @param rootPane Pane raíz de la escena
     * @return StackPane overlay o null si no se puede crear
     */
    private static StackPane obtenerOCrearOverlay(Pane rootPane) {
        // Si el root ya es un StackPane, usarlo
        if (rootPane instanceof StackPane) {
            return (StackPane) rootPane;
        }
        
        // Si es un BorderPane, buscar o crear overlay en el centro
        if (rootPane instanceof BorderPane) {
            BorderPane borderPane = (BorderPane) rootPane;
            
            // Verificar si el centro es un StackPane
            if (borderPane.getCenter() instanceof StackPane) {
                return (StackPane) borderPane.getCenter();
            }
            
            // Si el centro es otro tipo de nodo, envolverlo en un StackPane
            if (borderPane.getCenter() != null) {
                Region centerNode = (Region) borderPane.getCenter();
                StackPane wrapper = new StackPane(centerNode);
                borderPane.setCenter(wrapper);
                return wrapper;
            }
        }
        
        return null;
    }
    
    /**
     * Busca un StackPane en la jerarquía de nodos.
     * 
     * @param root Nodo raíz donde buscar
     * @return StackPane encontrado o null
     */
    private static StackPane buscarStackPane(Pane root) {
        if (root instanceof StackPane) {
            return (StackPane) root;
        }
        
        // Buscar en hijos si es un Pane con hijos
        if (root instanceof BorderPane) {
            BorderPane borderPane = (BorderPane) root;
            if (borderPane.getCenter() instanceof StackPane) {
                return (StackPane) borderPane.getCenter();
            }
        }
        
        return null;
    }
    
    /**
     * Crea el nodo visual de la notificación.
     * 
     * @param mensaje Mensaje a mostrar
     * @param tipo Tipo de notificación
     * @return HBox con la notificación completa
     */
    private static HBox crearNotificacion(String mensaje, TipoNotificacion tipo) {
        HBox notificacion = new HBox(15);
        notificacion.setAlignment(Pos.CENTER_LEFT);
        
        // Aplicar estilos base + tipo específico (padding viene del CSS)
        notificacion.getStyleClass().addAll("notificacion", tipo.getEstiloCSS());
        
        // Icono
        Label lblIcono = new Label(tipo.getIcono());
        lblIcono.getStyleClass().add("notificacion-icono");
        lblIcono.setMinWidth(30);
        lblIcono.setMaxWidth(30);
        lblIcono.setAlignment(Pos.CENTER);
        
        // Mensaje
        Label lblMensaje = new Label(mensaje);
        lblMensaje.getStyleClass().add("notificacion-mensaje");
        lblMensaje.setWrapText(true);
        
        notificacion.getChildren().addAll(lblIcono, lblMensaje);
        
        // Iniciar invisible para animación
        notificacion.setOpacity(0);
        notificacion.setTranslateY(-30);
        
        // Asegurar que el mouse atraviese áreas vacías (no bloquear interacción)
        notificacion.setPickOnBounds(false);
        
        return notificacion;
    }
    
    /**
     * Anima la entrada de la notificación (fade in + slide down).
     * 
     * @param notificacion Nodo a animar
     * @param onFinished Callback al finalizar la animación
     */
    private static void animarEntrada(HBox notificacion, Runnable onFinished) {
        MotorAnimaciones.fadeYDeslizar(notificacion, 0, 1, -30, 0,
            DURACION_FADE_IN.toMillis(), onFinished);
    }
    
    /**
     * Anima la salida de la notificación (fade out + slide up).
     * 
     * @param notificacion Nodo a animar
     * @param onFinished Callback al finalizar la animación
     */
    private static void animarSalida(HBox notificacion, Runnable onFinished) {
        MotorAnimaciones.fadeYDeslizar(notificacion, 1, 0, 0, -30,
            DURACION_FADE_OUT.toMillis(), onFinished);
    }
    
    /**
     * Muestra una notificación con duración personalizada.
     * 
     * @param stage Stage donde se mostrará la notificación
     * @param mensaje Mensaje a mostrar
     * @param tipo Tipo de notificación
     * @param duracionSegundos Duración en segundos que se mostrará la notificación
     */
    public static void mostrarConDuracion(Stage stage, String mensaje, TipoNotificacion tipo, double duracionSegundos) {
        // Similar a mostrar() pero con duración personalizada
        // Por ahora usar el método principal con duración fija
        // TODO: Implementar si se requiere duración variable
        mostrar(stage, mensaje, tipo);
    }
}
