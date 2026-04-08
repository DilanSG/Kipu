/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import com.kipu.cliente.componente.MotorAnimaciones;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Teclado Virtual Simple para pantallas táctiles.
 * 
 * Implementación programática (sin FXML) de un teclado virtual que aparece
 * mediante un flujo basado en foco:
 * 1. Al hacer foco en un campo de texto, aparece un pequeño icono de teclado
 *    DENTRO del campo (alineado a la derecha) con animación fade-in.
 * 2. Al tocar/clicar el icono, se muestra el teclado virtual completo.
 * 3. Si el teclado ya está abierto y se cambia de campo, se auto-vincula al nuevo.
 * 4. Al perder foco (sin teclado activo), el icono desaparece con fade-out.
 * 
 * Características:
 * - Creado completamente en código Java (sin FXML)
 * - Activación por foco + icono inline (flujo de dos pasos)
 * - Botones grandes para uso táctil
 * - Layout QWERTY estándar
 * - Diseño luxury con colores negro + dorado
 * - Soporte para mayúsculas/minúsculas
 * - Números, letras y símbolos comunes
 * - Arrastrable a cualquier posición
 * 
 * Uso:
 * <pre>
 * {@code
 * // En el initialize() de cualquier controlador con campos de texto:
 * Platform.runLater(() -> TecladoVirtualSimple.activar(cualquierCampoFxml));
 * }
 * </pre>
 */
public class TecladoVirtualSimple {
    
    private static final Logger logger = LoggerFactory.getLogger(TecladoVirtualSimple.class);
    
    private final VBox contenedorTeclado;
    private TextInputControl campoActual;
    private boolean mayusculasActivas = false;
    private Button botonMayusculas;
    private final java.util.List<Button> botonesLetras = new java.util.ArrayList<>();  // Para actualizar texto mayúsculas/minúsculas
    private final java.util.List<TextInputControl> camposDeTexto = new java.util.ArrayList<>();  // Para navegación Enter
    private Parent contenedorPrincipal;  // Para buscar el botón principal
    private StackPane contenedorStackPane;  // Para controlar mouseTransparent cuando está oculto
    
    // Variables para funcionalidad de arrastre
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    
    /**
     * Constructor que crea el teclado virtual programáticamente.
     */
    public TecladoVirtualSimple() {
        contenedorTeclado = new VBox(8);  // Espaciado entre filas
        contenedorTeclado.setAlignment(Pos.CENTER);
        contenedorTeclado.setPadding(new Insets(20, 16, 16, 16));  // Padding superior mayor para área de agarre
        contenedorTeclado.getStyleClass().add("teclado-virtual-root");
        
        // Limitar el tamaño del teclado para que se ajuste al contenido
        // Ancho máximo calculado: ~650px (teclas + padding + márgenes)
        contenedorTeclado.setMaxWidth(650);
        contenedorTeclado.setPrefWidth(650);
        
        // Ajustar verticalmente al tamaño del contenido (teclas)
        contenedorTeclado.setMaxHeight(Region.USE_PREF_SIZE);
        contenedorTeclado.setPrefHeight(Region.USE_COMPUTED_SIZE);
        
        // CRÍTICO: Evitar que el teclado capture clics fuera de su área visible
        // Esto permite que los clics en áreas vacías pasen a través y se detecte pérdida de foco
        contenedorTeclado.setPickOnBounds(false);
        
        // Estilo luxury: contenedor compacto, fondo negro con gradiente sutil, sombra elevada
        contenedorTeclado.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #1a1a1a 0%, #0f0f0f 100%);" +  // Gradiente negro sutil
            "-fx-background-radius: 12;" +  // Bordes redondeados premium
            "-fx-border-color: #2a2a2a;" +  // Borde sutil
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 20, 0.3, 0, 8);"  // Sombra profunda y elevada
        );
        
        // Iniciar OCULTO - solo aparece al hacer foco en un campo
        contenedorTeclado.setVisible(false);
        contenedorTeclado.setManaged(false);
        
        crearTeclado();
        
        logger.info("Teclado virtual creado (oculto por defecto)");
    }
    
    /**
     * Crea todas las filas del teclado con sus botones.
     * Los botones de letras inician en minúsculas y cambian al presionar Shift.
     */
    private void crearTeclado() {
        // Header visual para indicar que el teclado es arrastrable
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER);
        header.setPrefHeight(20);
        header.setMaxHeight(20);
        header.setStyle(
            "-fx-background-color: rgba(212, 175, 55, 0.06);" +  // Fondo cristal dorado sutil
            "-fx-background-radius: 8 8 0 0;" +  // Redondeado solo arriba
            "-fx-cursor: open-hand;"
        );
        
        // Indicador visual de arrastre (tres líneas horizontales)
        javafx.scene.layout.Region indicador = new javafx.scene.layout.Region();
        indicador.setPrefSize(40, 4);
        indicador.setStyle(
            "-fx-background-color: rgba(212, 175, 55, 0.5);" +
            "-fx-background-radius: 2;"
        );
        header.getChildren().add(indicador);
        header.setPadding(new Insets(8, 0, 8, 0));
        
        contenedorTeclado.getChildren().add(header);
        
        // Fila 1: Números (anchos ajustados 20% más pequeños)
        HBox fila1 = crearFila();
        String[] numeros = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        for (String num : numeros) {
            fila1.getChildren().add(crearBoton(num, "tecla-numero", this::escribir));
        }
        fila1.getChildren().add(crearBoton("←", "tecla-especial", e -> borrar(), 64));  // 80 * 0.8
        
        // Fila 2: QWERTY (iniciar en minúsculas)
        HBox fila2 = crearFila();
        String[] letras1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
        for (String letra : letras1) {
            Button botonLetra = crearBoton(letra, "tecla-letra", this::escribir);
            botonesLetras.add(botonLetra);  // Guardar referencia
            fila2.getChildren().add(botonLetra);
        }
        
        // Fila 3: ASDFGH (iniciar en minúsculas)
        HBox fila3 = crearFila();
        String[] letras2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"};
        for (String letra : letras2) {
            Button botonLetra = crearBoton(letra, "tecla-letra", this::escribir);
            botonesLetras.add(botonLetra);  // Guardar referencia
            fila3.getChildren().add(botonLetra);
        }
        
        // Fila 4: ZXCVBN (iniciar en minúsculas)
        HBox fila4 = crearFila();
        botonMayusculas = crearBoton("⇧", "tecla-especial", e -> toggleMayusculas(), 64);  // 80 * 0.8
        fila4.getChildren().add(botonMayusculas);
        String[] letras3 = {"z", "x", "c", "v", "b", "n", "m"};
        for (String letra : letras3) {
            Button botonLetra = crearBoton(letra, "tecla-letra", this::escribir);
            botonesLetras.add(botonLetra);  // Guardar referencia
            fila4.getChildren().add(botonLetra);
        }
        fila4.getChildren().add(crearBoton("✓", "tecla-aceptar", e -> aceptar(), 64));  // Funciona como Enter
        
        // Fila 5: Símbolos y espacio
        HBox fila5 = crearFila();
        String[] simbolos = {"@", "_", "-"};
        for (String simbolo : simbolos) {
            fila5.getChildren().add(crearBoton(simbolo, "tecla-simbolo", this::escribir));
        }
        fila5.getChildren().add(crearBoton(" ", "tecla-espacio", this::escribir, 280));  // 350 * 0.8
        String[] simbolos2 = {".", ","};
        for (String simbolo : simbolos2) {
            fila5.getChildren().add(crearBoton(simbolo, "tecla-simbolo", this::escribir));
        }
        fila5.getChildren().add(crearBoton("X", "tecla-cerrar", e -> cerrar(), 64));  // 80 * 0.8
        
        // Agregar todas las filas al contenedor
        contenedorTeclado.getChildren().addAll(fila1, fila2, fila3, fila4, fila5);
        
        // Hacer el teclado arrastrable
        configurarArrastre();
    }
    
    /**
     * Configura la funcionalidad de arrastre para el teclado.
     * El usuario puede hacer clic y arrastrar el teclado a cualquier posición dentro de la ventana.
     * 
     * Comportamiento:
     * - Clic en el fondo del teclado (no en botones) permite arrastrar
     * - El teclado sigue el cursor del mouse
     * - Se mantiene dentro de los límites de la ventana
     */
    private void configurarArrastre() {
        // Al presionar el mouse, guardar la posición inicial
        contenedorTeclado.setOnMousePressed(event -> {
            // Solo permitir arrastre si se hace clic en el fondo (no en botones)
            if (event.getTarget() == contenedorTeclado || 
                event.getTarget().toString().contains("VBox") ||
                event.getTarget().toString().contains("HBox")) {
                
                dragOffsetX = event.getSceneX() - contenedorTeclado.getTranslateX();
                dragOffsetY = event.getSceneY() - contenedorTeclado.getTranslateY();
                
                // Cambiar cursor a mano cerrada
                contenedorTeclado.setCursor(javafx.scene.Cursor.CLOSED_HAND);
                
                logger.debug("Inicio arrastre en: X={}, Y={}", event.getSceneX(), event.getSceneY());
            }
        });
        
        // Al arrastrar, actualizar la posición del teclado
        contenedorTeclado.setOnMouseDragged(event -> {
            if (contenedorTeclado.getCursor() == javafx.scene.Cursor.CLOSED_HAND) {
                double newTranslateX = event.getSceneX() - dragOffsetX;
                double newTranslateY = event.getSceneY() - dragOffsetY;
                
                contenedorTeclado.setTranslateX(newTranslateX);
                contenedorTeclado.setTranslateY(newTranslateY);
                
                event.consume();  // Evitar que el evento se propague
            }
        });
        
        // Al soltar, restaurar cursor
        contenedorTeclado.setOnMouseReleased(event -> {
            if (contenedorTeclado.getCursor() == javafx.scene.Cursor.CLOSED_HAND) {
                contenedorTeclado.setCursor(javafx.scene.Cursor.DEFAULT);
                logger.debug("Fin arrastre en: X={}, Y={}", 
                    contenedorTeclado.getTranslateX(), contenedorTeclado.getTranslateY());
            }
        });
        
        // Cambiar cursor a mano abierta cuando se pasa sobre el fondo
        contenedorTeclado.setOnMouseEntered(event -> {
            if (event.getTarget() == contenedorTeclado || 
                event.getTarget().toString().contains("VBox")) {
                contenedorTeclado.setCursor(javafx.scene.Cursor.OPEN_HAND);
            }
        });
        
        contenedorTeclado.setOnMouseExited(event -> {
            if (contenedorTeclado.getCursor() != javafx.scene.Cursor.CLOSED_HAND) {
                contenedorTeclado.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
    }
    
    /**
     * Crea una fila horizontal para botones.
     */
    private HBox crearFila() {
        HBox fila = new HBox(6);  // Espaciado reducido entre botones (antes 8)
        fila.setAlignment(Pos.CENTER);
        return fila;
    }
    
    /**
     * Crea un botón con el texto y estilo especificados (tamaño estándar).
     */
    private Button crearBoton(String texto, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> accion) {
        return crearBoton(texto, styleClass, accion, 48);  // Tamaño estándar reducido 20%
    }
    
    /**
     * Crea un botón con ancho personalizado.
     * Teclas 20% más pequeñas con efecto metálico sutil.
     */
    private Button crearBoton(String texto, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> accion, double ancho) {
        Button boton = new Button(texto);
        boton.getStyleClass().add(styleClass);
        
        // Tamaños reducidos 20%: de 60px a 48px altura, ancho proporcional
        double anchoReducido = ancho * 0.8;  // 20% más pequeño
        boton.setMinWidth(anchoReducido);
        boton.setMinHeight(48);  // Reducido de 60 a 48
        boton.setPrefWidth(anchoReducido);
        boton.setPrefHeight(48);
        boton.setOnAction(accion);
        
        // CRÍTICO: Evitar que los botones del teclado tomen foco
        // Esto previene que el campo de texto pierda foco al presionar una tecla
        boton.setFocusTraversable(false);
        
        // CSS class para font-size responsive (base 15px)
        boton.getStyleClass().add("tutorial-titulo");
        
        // Estilo base con efecto metálico premium
        String estiloBase = 
            "-fx-background-radius: 8; " +  // Bordes más redondeados
            "-fx-border-radius: 8; " +
            "-fx-font-weight: 600; " +
            "-fx-cursor: hand; ";
        
        // Gradiente metálico luxury para teclas normales
        String gradienteMetalico = 
            "linear-gradient(to bottom, #3a3a3a 0%, #2d2d2d 40%, #252525 60%, #1a1a1a 100%)";
        
        if ("tecla-letra".equals(styleClass) || "tecla-numero".equals(styleClass) || "tecla-simbolo".equals(styleClass)) {
            boton.setStyle(estiloBase + 
                "-fx-background-color: " + gradienteMetalico + "; " +
                "-fx-text-fill: #f5f5f5; " +  // Texto más brillante
                "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +  // Borde con gradiente
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);");  // Sombra más profunda
            
            // Efectos hover y pressed para interactividad luxury
            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: linear-gradient(to bottom, #4a4a4a 0%, #3d3d3d 40%, #303030 60%, #252525 100%); " +
                "-fx-text-fill: #ffffff; " +
                "-fx-border-color: linear-gradient(to bottom, #5a5a5a 0%, #3a3a3a 100%); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 8, 0.5, 0, 4); " +  // Más elevado en hover
                "-fx-scale-y: 1.02; -fx-scale-x: 1.02;"));
            
            boton.setOnMouseExited(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: " + gradienteMetalico + "; " +
                "-fx-text-fill: #f5f5f5; " +
                "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);"));
            
            boton.setOnMousePressed(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.12); " +  // Cristal dorado al presionar
                "-fx-text-fill: #d4af37; " +  // Dorado al presionar
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.2), 4, 0.6, 0, 1); " +  // Sombra dorada
                "-fx-translate-y: 2;"));  // Efecto de presión
            
            boton.setOnMouseReleased(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: " + gradienteMetalico + "; " +
                "-fx-text-fill: #f5f5f5; " +
                "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);"));
        } else if ("tecla-especial".equals(styleClass)) {
            boton.getStyleClass().add("icono-texto-sm");
            boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +  // Dorado cristal
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +  // Borde cristal dorado
                "-fx-border-width: 1px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);");
            
            // Efectos hover y pressed para teclas especiales
            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.12); " +
                "-fx-text-fill: #e0c455; " +
                "-fx-border-color: rgba(212, 175, 55, 0.55); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.18), 8, 0, 0, 0);"));
            
            boton.setOnMouseExited(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);"));
            
            boton.setOnMousePressed(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.2); " +
                "-fx-text-fill: #f5e6c8; " +
                "-fx-border-color: rgba(212, 175, 55, 0.65); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.25), 4, 0, 0, 0); " +
                "-fx-translate-y: 2;"));
            
            boton.setOnMouseReleased(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);"));
        } else if ("tecla-espacio".equals(styleClass)) {
            boton.setStyle(estiloBase + 
                "-fx-background-color: " + gradienteMetalico + "; " +
                "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);");
            
            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: linear-gradient(to bottom, #4a4a4a 0%, #3d3d3d 40%, #303030 60%, #252525 100%); " +
                "-fx-border-color: linear-gradient(to bottom, #5a5a5a 0%, #3a3a3a 100%); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 8, 0.5, 0, 4);"));
            
            boton.setOnMouseExited(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: " + gradienteMetalico + "; " +
                "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);"));
        } else if ("tecla-aceptar".equals(styleClass)) {
            boton.getStyleClass().add("icono-texto-md");
            boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.1); " +  // Cristal dorado
                "-fx-text-fill: #d4af37; " +  // Texto dorado
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 8, 0, 0, 0);");
            
            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.2); " +
                "-fx-text-fill: #e0c455; " +
                "-fx-border-color: rgba(212, 175, 55, 0.75); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.3), 12, 0, 0, 0); " +
                "-fx-scale-y: 1.03; -fx-scale-x: 1.03;"));
            
            boton.setOnMouseExited(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.1); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 8, 0, 0, 0);"));
            
            boton.setOnMousePressed(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.28); " +
                "-fx-text-fill: #f5e6c8; " +
                "-fx-border-color: #d4af37; " +
                "-fx-border-width: 2px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.35), 4, 0, 0, 0); " +
                "-fx-translate-y: 2;"));
            
            boton.setOnMouseReleased(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.1); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 8, 0, 0, 0);"));
        } else if ("tecla-cerrar".equals(styleClass)) {
            boton.getStyleClass().add("icono-texto-md");
            boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +  // Dorado cristal para consistencia
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);");
            
            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.12); " +
                "-fx-text-fill: #e0c455; " +
                "-fx-border-color: rgba(212, 175, 55, 0.55); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.18), 8, 0, 0, 0);"));
            
            boton.setOnMouseExited(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);"));
            
            boton.setOnMousePressed(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.2); " +
                "-fx-text-fill: #f5e6c8; " +
                "-fx-border-color: rgba(212, 175, 55, 0.65); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.25), 4, 0, 0, 0); " +
                "-fx-translate-y: 2;"));
            
            boton.setOnMouseReleased(e -> boton.setStyle(estiloBase + 
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-font-weight: 700; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);"));
        }
        
        return boton;
    }
    
    /**
     * Escribe el carácter del botón presionado en el campo activo.
     * 
     * IMPORTANTE: Siempre inserta al FINAL del texto existente,
     * ya que en pantallas táctiles es el comportamiento más intuitivo.
     * El texto del botón ya refleja el estado de mayúsculas/minúsculas.
     */
    private void escribir(javafx.event.ActionEvent event) {
        if (campoActual == null) {
            logger.warn("No hay campo activo para escribir");
            return;
        }
        
        Button boton = (Button) event.getSource();
        String caracter = boton.getText();
        
        // CRÍTICO: Insertar siempre al FINAL del texto
        // No usar getCaretPosition() porque puede estar en 0 cuando el campo no tiene foco
        String textoActual = campoActual.getText();
        String nuevoTexto = textoActual + caracter;
        
        campoActual.setText(nuevoTexto);
        // Posicionar el cursor al final
        campoActual.positionCaret(nuevoTexto.length());
        
        logger.debug("Carácter '{}' insertado al final. Texto: '{}'", caracter, nuevoTexto);
    }
    
    /**
     * Borra el último carácter del campo activo (backspace).
     * 
     * IMPORTANTE: Siempre borra el ÚLTIMO carácter del texto,
     * ya que es el comportamiento más intuitivo en pantallas táctiles.
     */
    private void borrar() {
        if (campoActual == null) {
            logger.warn("No hay campo activo para borrar");
            return;
        }
        
        String textoActual = campoActual.getText();
        if (textoActual.length() > 0) {
            // Borrar el último carácter
            String nuevoTexto = textoActual.substring(0, textoActual.length() - 1);
            campoActual.setText(nuevoTexto);
            // Posicionar el cursor al final
            campoActual.positionCaret(nuevoTexto.length());
            
            logger.debug("Último carácter borrado. Texto: '{}'", nuevoTexto);
        }
    }
    
    /**
     * Alterna entre mayúsculas y minúsculas.
     * Actualiza visualmente el texto de todas las teclas de letras.
     */
    private void toggleMayusculas() {
        mayusculasActivas = !mayusculasActivas;
        
        // Cambiar estilo del botón de mayúsculas
        if (mayusculasActivas) {
            botonMayusculas.getStyleClass().add("tecla-mayusculas-activa");
        } else {
            botonMayusculas.getStyleClass().remove("tecla-mayusculas-activa");
        }
        
        // Actualizar el texto de todas las teclas de letras
        for (Button botonLetra : botonesLetras) {
            String textoActual = botonLetra.getText();
            if (textoActual.length() == 1 && Character.isLetter(textoActual.charAt(0))) {
                String nuevoTexto = mayusculasActivas ? textoActual.toUpperCase() : textoActual.toLowerCase();
                botonLetra.setText(nuevoTexto);
            }
        }
        
        logger.debug("Mayúsculas: {} - Teclas actualizadas", mayusculasActivas ? "ACTIVAS" : "inactivas");
    }
    
    /**
     * Cierra y oculta el teclado.
     * Este método es llamado por el botón X
     */
    private void cerrar() {
        logger.info("<<< Botón X presionado - Cerrando teclado >>>");
        ocultar();
    }
    
    /**
     * Función Enter profesional - Avanza al siguiente campo o presiona el botón principal.
     * 
     * Comportamiento:
     * 1. Si hay un siguiente campo de texto: le da foco (el teclado permanece visible)
     * 2. Si es el último campo: busca y presiona el botón principal (por ejemplo, "Iniciar Sesión")
     * 3. Si no encuentra botón principal: simplemente cierra el teclado
     * 
     * Esto simula el comportamiento estándar de Enter en formularios profesionales.
     */
    private void aceptar() {
        if (campoActual == null) {
            logger.warn("No hay campo activo");
            cerrar();
            return;
        }
        
        logger.info(">>> Enter presionado en campo: {}", campoActual.getId());
        
        // Buscar el índice del campo actual en la lista
        int indiceActual = camposDeTexto.indexOf(campoActual);
        
        if (indiceActual >= 0 && indiceActual < camposDeTexto.size() - 1) {
            // Hay un siguiente campo: darle foco Y actualizar campoActual
            TextInputControl siguienteCampo = camposDeTexto.get(indiceActual + 1);
            logger.info("Avanzando al siguiente campo: {}", siguienteCampo.getId());
            
            // CRÍTICO: Actualizar campoActual ANTES de cambiar el foco
            // Sin esto, escribir() seguiría insertando caracteres en el campo anterior
            this.campoActual = siguienteCampo;
            
            javafx.application.Platform.runLater(() -> siguienteCampo.requestFocus());
            // El teclado permanece visible - no se cierra ni se reabre
        } else {
            // Es el último campo: buscar y presionar el botón principal
            logger.info("Último campo alcanzado, buscando botón principal...");
            
            // Buscar el botón con defaultButton=true o que sea el botón de login
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Button botonPrincipal = buscarBotonPrincipal();
                
                if (botonPrincipal != null) {
                    logger.info("Presionando botón principal: {}", botonPrincipal.getText());
                    botonPrincipal.fire();  // Simular clic
                    cerrar();  // Cerrar teclado después de enviar
                } else {
                    logger.warn("No se encontró botón principal, cerrando teclado");
                    cerrar();
                }
            });
        }
    }
    
    /**
     * Busca el botón principal en el contenedor (botón con defaultButton=true).
     * 
     * @return El botón principal o null si no se encuentra
     */
    private javafx.scene.control.Button buscarBotonPrincipal() {
        if (contenedorPrincipal == null) {
            return null;
        }
        
        // Buscar todos los botones en el contenedor
        for (var node : contenedorPrincipal.lookupAll(".button")) {
            if (node instanceof javafx.scene.control.Button) {
                javafx.scene.control.Button boton = (javafx.scene.control.Button) node;
                // Buscar botón con defaultButton=true (el botón principal del formulario)
                if (boton.isDefaultButton()) {
                    return boton;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Muestra el teclado y lo vincula al campo especificado.
     * 
     * Este método se ejecuta cuando el usuario toca el icono de teclado
     * junto a un campo de texto. El teclado aparece en la parte inferior
     * de la pantalla y todos los caracteres escritos se insertan en el campo.
     * 
     * @param campo TextField o PasswordField que recibirá los caracteres
     */
    public void mostrar(TextInputControl campo) {
        // Actualizar el campo activo
        this.campoActual = campo;
        
        // Hacer visible el teclado y traerlo al frente
        contenedorTeclado.setVisible(true);
        contenedorTeclado.setManaged(true);
        contenedorTeclado.setOpacity(1.0);
        contenedorTeclado.toFront();
        
        // Permitir que el contenedor overlay reciba eventos de mouse
        if (contenedorStackPane != null) {
            contenedorStackPane.setMouseTransparent(false);
            contenedorStackPane.setVisible(true);
            contenedorStackPane.setOpacity(1.0);
            contenedorStackPane.toFront();
        }
        
        logger.info(">>> TECLADO MOSTRADO para: {} (tipo: {})", 
            campo.getId() != null ? campo.getId() : "sin-id",
            campo.getClass().getSimpleName());
    }
    
    /**
     * Oculta el teclado y limpia el campo activo.
     * 
     * El teclado permanece en el scene graph pero se oculta visualmente.
     * La posición (translateX/translateY) se mantiene para que aparezca
     * en la misma ubicación cuando se muestre de nuevo.
     */
    public void ocultar() {
        // Ocultar el teclado
        contenedorTeclado.setVisible(false);
        contenedorTeclado.setManaged(false);
        
        // Desactivar interacción con el contenedor overlay
        if (contenedorStackPane != null) {
            contenedorStackPane.setMouseTransparent(true);
        }
        
        this.campoActual = null;
        
        logger.info("--- Teclado OCULTO (posición mantenida: X={}, Y={})",
            contenedorTeclado.getTranslateX(), contenedorTeclado.getTranslateY());
    }
    
    /**
     * Obtiene el contenedor del teclado para agregarlo a la interfaz.
     */
    public VBox getContenedor() {
        return contenedorTeclado;
    }
    
    /**
     * Activa el teclado virtual para una vista que contiene campos de texto.
     * 
     * Este método busca todos los campos de texto (TextField, PasswordField, TextArea)
     * dentro de la jerarquía de nodos y configura un icono de teclado inline dentro
     * de cada campo. El icono aparece al hacer foco y al tocarlo se abre el teclado.
     * 
     * El teclado se posiciona flotante en la parte inferior central de la ventana,
     * por encima del contenido, y puede ser arrastrado a cualquier posición.
     * 
     * IMPORTANTE: Debe llamarse dentro de Platform.runLater() o después de que
     * la escena esté completamente cargada para que lookupAll() funcione.
     * 
     * Campos con la clase CSS "sin-teclado-virtual" serán ignorados.
     * 
     * Ejemplo de uso:
     * <pre>
     * {@code
     * Platform.runLater(() -> TecladoVirtualSimple.activar(cualquierCampoFxml));
     * }
     * </pre>
     * 
     * @param nodoReferencia Cualquier nodo @FXML de la vista (se usa para
     *                       encontrar el contenedor raíz automáticamente)
     */
    public static void activar(Node nodoReferencia) {
        try {
            logger.info("Iniciando activación de teclado virtual (modo inline por foco)...");
            
            // Verificar que el nodo esté en la escena
            if (nodoReferencia == null || nodoReferencia.getScene() == null) {
                logger.error("El nodo de referencia es null o no está en la escena");
                return;
            }
            
            TecladoVirtualSimple teclado = new TecladoVirtualSimple();
            
            // Encontrar el StackPane más alto para usar como contenedor del overlay
            StackPane contenedorOverlay = encontrarContenedorOverlay(nodoReferencia);
            if (contenedorOverlay == null) {
                logger.error("No se encontró StackPane para overlay del teclado virtual");
                return;
            }
            
            // Guardar referencia al contenedor para buscar botón principal (Enter)
            teclado.contenedorPrincipal = contenedorOverlay;
            
            // Crear un contenedor transparente intermedio para el teclado
            // Esto permite controlar mouseTransparent sin afectar el contenido principal
            StackPane contenedorTecladoStack = new StackPane();
            contenedorTecladoStack.setPickOnBounds(false);
            contenedorTecladoStack.setMouseTransparent(true);
            contenedorTecladoStack.setStyle("-fx-background-color: transparent;");
            
            teclado.contenedorStackPane = contenedorTecladoStack;
            
            // Posicionar el teclado en la parte inferior central
            contenedorTecladoStack.getChildren().add(teclado.getContenedor());
            StackPane.setAlignment(teclado.getContenedor(), Pos.BOTTOM_CENTER);
            StackPane.setMargin(teclado.getContenedor(), new Insets(0, 0, 30, 0));
            
            // Limpiar overlays de teclados previos para evitar duplicados
            // Esto ocurre cuando se navega entre subvistas y se llama activar() de nuevo
            contenedorOverlay.getChildren().removeIf(child -> 
                child instanceof StackPane && 
                child.getStyle() != null && 
                child.getStyle().contains("-fx-background-color: transparent;") &&
                child != contenedorTecladoStack &&
                child.getStyleClass().contains("overlay-teclado-virtual"));
            
            // Marcar este overlay para identificarlo en futuras limpiezas
            contenedorTecladoStack.getStyleClass().add("overlay-teclado-virtual");
            
            // Agregar el overlay del teclado al contenedor principal
            contenedorOverlay.getChildren().add(contenedorTecladoStack);
            
            logger.info("Teclado agregado como overlay en StackPane");
            
            // Detectar todos los campos de texto y agregar iconos de teclado
            int camposEncontrados = 0;
            for (var node : contenedorOverlay.lookupAll(".text-input")) {
                if (node instanceof TextInputControl) {
                    TextInputControl campo = (TextInputControl) node;
                    
                    // Ignorar campos marcados con "sin-teclado-virtual"
                    if (campo.getStyleClass().contains("sin-teclado-virtual")) {
                        logger.debug("Campo {} ignorado (sin-teclado-virtual)", campo.getId());
                        continue;
                    }
                    
                    // Ignorar campos que YA tienen un teclado virtual asignado
                    // Evita duplicar iconos al re-navegar a la misma vista
                    if (campo.getStyleClass().contains("con-teclado-virtual")) {
                        logger.debug("Campo {} ya tiene teclado asignado, ignorando", campo.getId());
                        continue;
                    }
                    
                    // Agregar a la lista de campos para navegación Enter
                    teclado.camposDeTexto.add(campo);
                    
                    // Marcar el campo como ya configurado para evitar duplicados
                    campo.getStyleClass().add("con-teclado-virtual");
                    
                    // Configurar campo con icono de teclado inline (visible al hacer foco)
                    teclado.configurarCampoConTeclado(campo);
                    camposEncontrados++;
                    
                    logger.info("Campo configurado con teclado inline: {} ({})", 
                        campo.getId() != null ? campo.getId() : "sin-id",
                        campo.getClass().getSimpleName());
                }
            }
            
            logger.info("=== TECLADO VIRTUAL ACTIVADO (MODO INLINE) ===");
            logger.info("Campos con icono inline de teclado: {}", camposEncontrados);
            logger.info("==============================================");
            
        } catch (Exception e) {
            logger.error("Error activando teclado virtual: ", e);
        }
    }
    
    /**
     * Busca el StackPane más alto en la jerarquía de padres del nodo dado.
     * Este StackPane se usa como contenedor para el overlay del teclado virtual.
     * 
     * Se recorre toda la cadena de padres y se retorna el último (más alto)
     * StackPane encontrado, que normalmente es el contenedor principal de la vista
     * (por ejemplo, contenedorPrincipal en menu-principal).
     * 
     * @param nodo Nodo desde el cual iniciar la búsqueda hacia arriba
     * @return El StackPane más alto encontrado, o null si no existe
     */
    private static StackPane encontrarContenedorOverlay(Node nodo) {
        Parent current = nodo instanceof Parent ? (Parent) nodo : nodo.getParent();
        StackPane mejorStackPane = null;
        
        while (current != null) {
            if (current instanceof StackPane) {
                mejorStackPane = (StackPane) current;
            }
            current = current.getParent();
        }
        
        return mejorStackPane;
    }
    
    /**
     * Configura un campo de texto para mostrar un icono de teclado inline al recibir foco.
     * 
     * Nuevo flujo de activación basado en foco:
     * 1. El usuario hace foco en el campo (clic o tab) → aparece un pequeño icono
     *    de teclado DENTRO del campo, alineado a la derecha (animación fade-in)
     * 2. El usuario toca/cliquea el icono → se muestra el teclado virtual completo
     * 3. Si el teclado ya está visible y se hace foco en otro campo, el teclado
     *    se cambia automáticamente al nuevo campo (auto-vinculación)
     * 4. Al perder foco (y el teclado no estar activo para este campo), el icono desaparece
     * 
     * El campo se envuelve en un StackPane para poder superponer el icono sin alterar
     * el layout original. Las propiedades de layout del campo se transfieren al wrapper.
     * 
     * @param campo Campo de texto al que agregar el icono inline de teclado
     */
    private void configurarCampoConTeclado(TextInputControl campo) {
        Parent parent = campo.getParent();
        if (parent == null || !(parent instanceof Pane)) {
            logger.warn("No se pudo configurar teclado inline: padre de {} no es Pane", campo.getId());
            return;
        }
        
        Pane parentPane = (Pane) parent;
        int index = parentPane.getChildren().indexOf(campo);
        if (index < 0) {
            logger.warn("Campo {} no encontrado en su padre", campo.getId());
            return;
        }
        
        // Crear el botón con icono de teclado (solo icono, sin fondo ni borde)
        // Inicia oculto y aparece con animación fade-in al recibir foco el campo
        Button btnTeclado = new Button();
        btnTeclado.getStyleClass().add("boton-teclado-inline");
        btnTeclado.setFocusTraversable(false);
        btnTeclado.setMinSize(20, 20);
        btnTeclado.setMaxSize(20, 20);
        btnTeclado.setPrefSize(20, 20);
        btnTeclado.setVisible(false);
        btnTeclado.setOpacity(0);
        // NOTA: managed=true para que StackPane.setAlignment funcione correctamente
        // La visibilidad controla si se renderiza, el alignment lo posiciona a la derecha
        
        // Icono SVG de teclado (versión inline, más pequeña)
        Region iconoTeclado = new Region();
        iconoTeclado.getStyleClass().addAll("icono-svg-inline", "icono-teclado");
        iconoTeclado.setMinSize(16, 16);
        iconoTeclado.setPrefSize(16, 16);
        iconoTeclado.setMaxSize(16, 16);
        btnTeclado.setGraphic(iconoTeclado);
        
        // Al tocar el icono: mostrar el teclado virtual vinculado a este campo
        TecladoVirtualSimple teclado = this;
        btnTeclado.setOnAction(e -> {
            campo.requestFocus();
            teclado.mostrar(campo);
            e.consume();
        });
        
        // Crear StackPane wrapper para superponer icono sobre el campo
        // El campo ocupa todo el espacio y el icono flota en la esquina derecha
        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().add("wrapper-campo-teclado");
        wrapper.setPickOnBounds(false);
        
        // Transferir propiedades de layout del campo original al wrapper
        // Esto preserva el comportamiento de tamaño y posición en el parent
        transferirPropiedadesLayout(campo, wrapper, parentPane);
        
        // Remover campo de su padre original
        parentPane.getChildren().remove(index);
        
        // Asegurar que el campo llene todo el ancho del wrapper
        campo.setMaxWidth(Double.MAX_VALUE);
        
        // Agregar campo (fondo) + botón icono (superpuesto) al wrapper
        // El campo llena todo el wrapper, el botón flota en la esquina DERECHA
        wrapper.getChildren().addAll(campo, btnTeclado);
        StackPane.setAlignment(campo, Pos.CENTER_LEFT);
        StackPane.setAlignment(btnTeclado, Pos.CENTER_RIGHT);
        StackPane.setMargin(btnTeclado, new Insets(0, 10, 0, 0));
        
        // Insertar wrapper en la misma posición que tenía el campo
        parentPane.getChildren().add(index, wrapper);
        
        // Listener de foco: mostrar/ocultar icono según el estado del campo
        // Este es el CORAZÓN del nuevo flujo: foco → icono → clic → teclado
        campo.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                // Campo recibió foco → mostrar icono de teclado con animación fade-in
                mostrarIconoInline(btnTeclado);
                
                // Si el teclado ya está visible (abierto desde otro campo),
                // auto-cambiar al campo que acaba de recibir foco
                if (contenedorTeclado.isVisible()) {
                    campoActual = campo;
                    logger.debug("Teclado auto-cambiado a campo: {}", 
                        campo.getId() != null ? campo.getId() : "sin-id");
                }
            } else {
                // Campo perdió foco → ocultar icono solo si el teclado no está
                // activo para este campo (evita ocultar mientras se usa el teclado virtual)
                javafx.application.Platform.runLater(() -> {
                    if (campoActual != campo || !contenedorTeclado.isVisible()) {
                        ocultarIconoInline(btnTeclado);
                    }
                });
            }
        });
        
        logger.debug("Campo configurado con icono inline de teclado: {}", 
            campo.getId() != null ? campo.getId() : "sin-id");
    }
    
    /**
     * Transfiere las propiedades de layout del campo original al wrapper StackPane.
     * 
     * Al envolver el campo en un StackPane, el nuevo wrapper debe ocupar el mismo
     * espacio que el campo original en su contenedor padre. Se transfieren:
     * - HBox constraints: hgrow, margin
     * - VBox constraints: vgrow, margin
     * - Tamaño preferido, mínimo y máximo
     * 
     * @param campo   Campo de texto original con las propiedades de layout
     * @param wrapper StackPane wrapper que recibirá las propiedades
     * @param parentPane Contenedor padre donde estaba el campo
     */
    private void transferirPropiedadesLayout(TextInputControl campo, StackPane wrapper, Pane parentPane) {
        // Transferir constraints de HBox (hgrow, margin)
        if (parentPane instanceof HBox) {
            Priority hgrow = HBox.getHgrow(campo);
            if (hgrow != null) {
                // Solo transferir hgrow si el campo original lo tenía asignado
                // NO forzar ALWAYS por defecto: eso haría que inputs con prefWidth
                // fijo (como el de facturación) se expandan al 100% del ancho
                HBox.setHgrow(wrapper, hgrow);
            }
            Insets margin = HBox.getMargin(campo);
            if (margin != null) {
                HBox.setMargin(wrapper, margin);
                HBox.setMargin(campo, null);
            }
        }
        
        // Transferir constraints de VBox (vgrow, margin)
        if (parentPane instanceof VBox) {
            Priority vgrow = VBox.getVgrow(campo);
            if (vgrow != null) {
                VBox.setVgrow(wrapper, vgrow);
            }
            Insets margin = VBox.getMargin(campo);
            if (margin != null) {
                VBox.setMargin(wrapper, margin);
                VBox.setMargin(campo, null);
            }
        }
        
        // Transferir constraints de tamaño preferido/mínimo/máximo
        if (campo.getPrefWidth() > 0) {
            wrapper.setPrefWidth(campo.getPrefWidth());
        }
        if (campo.getPrefHeight() > 0) {
            wrapper.setPrefHeight(campo.getPrefHeight());
        }
        if (campo.getMinWidth() > 0) {
            wrapper.setMinWidth(campo.getMinWidth());
        }
        if (campo.getMaxWidth() > 0 && campo.getMaxWidth() != Double.MAX_VALUE) {
            wrapper.setMaxWidth(campo.getMaxWidth());
        }
    }
    
    /**
     * Muestra el icono de teclado inline con animación fade-in (200ms).
     * 
     * El icono aparece gradualmente para no ser intrusivo visualmente.
     * Si el icono ya está completamente visible, no repite la animación.
     * 
     * @param btnTeclado Botón del icono a mostrar
     */
    private void mostrarIconoInline(Button btnTeclado) {
        // Evitar repetir animación si ya está visible
        if (btnTeclado.isVisible() && btnTeclado.getOpacity() >= 1.0) {
            return;
        }
        
        btnTeclado.setVisible(true);
        
        // Animación fade-in sutil (200ms)
        MotorAnimaciones.fade(btnTeclado, 0, 1, 200);
    }
    
    /**
     * Oculta el icono de teclado inline con animación fade-out (150ms).
     * 
     * El icono desaparece gradualmente y se marca como invisible al completar
     * la animación para no interceptar eventos de mouse en el campo.
     * 
     * @param btnTeclado Botón del icono a ocultar
     */
    private void ocultarIconoInline(Button btnTeclado) {
        // No hacer nada si ya está oculto
        if (!btnTeclado.isVisible()) {
            return;
        }
        
        // Animación fade-out rápida (150ms)
        MotorAnimaciones.fade(btnTeclado, btnTeclado.getOpacity(), 0, 150,
            () -> btnTeclado.setVisible(false));
    }
}
