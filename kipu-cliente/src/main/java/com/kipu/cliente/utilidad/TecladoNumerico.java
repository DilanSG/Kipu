/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Teclado Numérico (NumPad) para campos que solo aceptan dígitos.
 * 
 * Diseñado específicamente para la pantalla de login PIN donde los campos
 * de código de empleado (2 dígitos) y PIN (4 dígitos) solo necesitan números.
 * 
 * Diferencias con TecladoVirtualSimple:
 * - Solo muestra dígitos 0-9, borrar (←) y aceptar (✓)
 * - Se abre automáticamente al recibir foco el campo (NO requiere botón icono)
 * - Se cierra automáticamente al perder foco el campo
 * - Layout de calculadora compacto (4 filas x 3 columnas)
 * - Más pequeño y ligero que el teclado QWERTY completo
 * 
 * Uso:
 * <pre>
 * {@code
 * // En el initialize() del controlador:
 * Platform.runLater(() -> TecladoNumerico.activar(nodoReferencia, campoCodigo, campoPin));
 * }
 * </pre>
 * 
 * @see TecladoVirtualSimple para el teclado QWERTY completo
 */
public class TecladoNumerico {

    private static final Logger logger = LoggerFactory.getLogger(TecladoNumerico.class);

    /** Contenedor visual principal del numpad */
    private final VBox contenedorNumpad;

    /** Campo de texto actualmente vinculado al numpad */
    private TextInputControl campoActual;

    /** Lista de campos registrados para navegación con Enter (✓) */
    private final java.util.List<TextInputControl> camposRegistrados = new java.util.ArrayList<>();

    /** Contenedor StackPane overlay para controlar mouseTransparent */
    private StackPane contenedorStackPane;

    /** Contenedor raíz de la vista para buscar el botón principal (defaultButton) */
    private Parent contenedorPrincipal;

    /** Variables para funcionalidad de arrastre */
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    /**
     * Constructor que crea el teclado numérico programáticamente.
     * El numpad se crea oculto y se muestra al recibir foco un campo registrado.
     */
    public TecladoNumerico() {
        contenedorNumpad = new VBox(8);
        contenedorNumpad.setAlignment(Pos.CENTER);
        contenedorNumpad.setPadding(new Insets(16, 16, 16, 16));
        contenedorNumpad.getStyleClass().add("teclado-numerico-root");

        // Limitar tamaño del numpad: compacto como calculadora
        contenedorNumpad.setMaxWidth(280);
        contenedorNumpad.setPrefWidth(280);
        contenedorNumpad.setMaxHeight(Region.USE_PREF_SIZE);
        contenedorNumpad.setPrefHeight(Region.USE_COMPUTED_SIZE);

        // Evitar que el numpad capture clics fuera de su área
        contenedorNumpad.setPickOnBounds(false);

        // Estilo luxury: fondo oscuro con gradiente, sombra elevada
        contenedorNumpad.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #1a1a1a 0%, #0f0f0f 100%);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #2a2a2a;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 20, 0.3, 0, 8);"
        );

        // Iniciar oculto
        contenedorNumpad.setVisible(false);
        contenedorNumpad.setManaged(false);

        crearNumpad();

        logger.info("Teclado numérico creado (oculto por defecto)");
    }

    /**
     * Crea el layout del numpad con estilo de calculadora.
     * 
     * Distribución:
     * ┌─────┬─────┬─────┐
     * │  7  │  8  │  9  │
     * ├─────┼─────┼─────┤
     * │  4  │  5  │  6  │
     * ├─────┼─────┼─────┤
     * │  1  │  2  │  3  │
     * ├─────┼─────┼─────┤
     * │  ←  │  0  │  ✓  │
     * └─────┴─────┴─────┘
     */
    private void crearNumpad() {
        // Header de arrastre
        javafx.scene.layout.HBox header = new javafx.scene.layout.HBox();
        header.setAlignment(Pos.CENTER);
        header.setPrefHeight(16);
        header.setMaxHeight(16);
        header.setStyle(
            "-fx-background-color: rgba(212, 175, 55, 0.06);" +
            "-fx-background-radius: 8 8 0 0;" +
            "-fx-cursor: open-hand;"
        );

        Region indicador = new Region();
        indicador.setPrefSize(32, 3);
        indicador.setStyle(
            "-fx-background-color: rgba(212, 175, 55, 0.5);" +
            "-fx-background-radius: 2;"
        );
        header.getChildren().add(indicador);
        header.setPadding(new Insets(6, 0, 6, 0));

        contenedorNumpad.getChildren().add(header);

        // Grid de botones numéricos
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);

        // Fila 1: 7, 8, 9
        grid.add(crearBotonNumero("7"), 0, 0);
        grid.add(crearBotonNumero("8"), 1, 0);
        grid.add(crearBotonNumero("9"), 2, 0);

        // Fila 2: 4, 5, 6
        grid.add(crearBotonNumero("4"), 0, 1);
        grid.add(crearBotonNumero("5"), 1, 1);
        grid.add(crearBotonNumero("6"), 2, 1);

        // Fila 3: 1, 2, 3
        grid.add(crearBotonNumero("1"), 0, 2);
        grid.add(crearBotonNumero("2"), 1, 2);
        grid.add(crearBotonNumero("3"), 2, 2);

        // Fila 4: ←, 0, ✓
        grid.add(crearBotonEspecial("←", "numpad-borrar", e -> borrar()), 0, 3);
        grid.add(crearBotonNumero("0"), 1, 3);
        grid.add(crearBotonEspecial("✓", "numpad-aceptar", e -> aceptar()), 2, 3);

        contenedorNumpad.getChildren().add(grid);

        // Botón de cerrar (X) debajo del grid
        Button botonCerrar = crearBotonEspecial("X", "numpad-cerrar", e -> ocultar());
        botonCerrar.setMaxWidth(Double.MAX_VALUE);
        botonCerrar.setPrefWidth(244); // Ancho completo del grid (3*76 + 2*8)
        botonCerrar.setPrefHeight(48);
        botonCerrar.setMinHeight(48);
        contenedorNumpad.getChildren().add(botonCerrar);

        // Hacer arrastrable
        configurarArrastre();
    }

    /**
     * Crea un botón de dígito con estilo metálico luxury.
     * 
     * @param digito El dígito a mostrar ("0"-"9")
     * @return Botón configurado con estilos y acción de escritura
     */
    private Button crearBotonNumero(String digito) {
        Button boton = new Button(digito);
        boton.getStyleClass().add("numpad-digito");
        boton.setFocusTraversable(false);

        // Tamaño grande y cuadrado para uso táctil cómodo
        double tamano = 76;
        boton.setMinSize(tamano, tamano);
        boton.setPrefSize(tamano, tamano);
        boton.setMaxSize(tamano, tamano);

        // Acción: escribir el dígito en el campo activo
        boton.setOnAction(e -> escribir(digito));

        // Estilo base metálico
        String estiloBase =
            "-fx-background-radius: 10; " +
            "-fx-border-radius: 10; " +
            "-fx-font-size: 28px; " +
            "-fx-font-weight: 700; " +
            "-fx-cursor: hand; ";

        String gradienteMetalico =
            "linear-gradient(to bottom, #3a3a3a 0%, #2d2d2d 40%, #252525 60%, #1a1a1a 100%)";

        boton.setStyle(estiloBase +
            "-fx-background-color: " + gradienteMetalico + "; " +
            "-fx-text-fill: #f5f5f5; " +
            "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);");

        // Hover: más claro, ligeramente elevado
        boton.setOnMouseEntered(e -> boton.setStyle(estiloBase +
            "-fx-background-color: linear-gradient(to bottom, #4a4a4a 0%, #3d3d3d 40%, #303030 60%, #252525 100%); " +
            "-fx-text-fill: #ffffff; " +
            "-fx-border-color: linear-gradient(to bottom, #5a5a5a 0%, #3a3a3a 100%); " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 8, 0.5, 0, 4); " +
            "-fx-scale-y: 1.03; -fx-scale-x: 1.03;"));

        // Exit: restaurar estilo base
        boton.setOnMouseExited(e -> boton.setStyle(estiloBase +
            "-fx-background-color: " + gradienteMetalico + "; " +
            "-fx-text-fill: #f5f5f5; " +
            "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);"));

        // Pressed: invertido con acento dorado cristal
        boton.setOnMousePressed(e -> boton.setStyle(estiloBase +
            "-fx-background-color: rgba(212, 175, 55, 0.12); " +
            "-fx-text-fill: #d4af37; " +
            "-fx-border-color: rgba(212, 175, 55, 0.5); " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.2), 4, 0.6, 0, 1); " +
            "-fx-translate-y: 2;"));

        // Released: restaurar
        boton.setOnMouseReleased(e -> boton.setStyle(estiloBase +
            "-fx-background-color: " + gradienteMetalico + "; " +
            "-fx-text-fill: #f5f5f5; " +
            "-fx-border-color: linear-gradient(to bottom, #4a4a4a 0%, #2a2a2a 100%); " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.7), 6, 0.4, 0, 3);"));

        return boton;
    }

    /**
     * Crea un botón especial (borrar o aceptar) con estilo diferenciado.
     * 
     * @param texto Texto del botón ("←" para borrar, "✓" para aceptar)
     * @param tipoCss Clase CSS del tipo ("numpad-borrar" o "numpad-aceptar")
     * @param accion Manejador de evento al presionar
     * @return Botón configurado
     */
    private Button crearBotonEspecial(String texto, String tipoCss, javafx.event.EventHandler<javafx.event.ActionEvent> accion) {
        Button boton = new Button(texto);
        boton.getStyleClass().add(tipoCss);
        boton.setFocusTraversable(false);
        boton.setOnAction(accion);

        // Mismo tamaño que los botones de dígitos
        double tamano = 76;
        boton.setMinSize(tamano, tamano);
        boton.setPrefSize(tamano, tamano);
        boton.setMaxSize(tamano, tamano);

        String estiloBase =
            "-fx-background-radius: 10; " +
            "-fx-border-radius: 10; " +
            "-fx-font-size: 24px; " +
            "-fx-font-weight: 700; " +
            "-fx-cursor: hand; ";

        if ("numpad-aceptar".equals(tipoCss)) {
            // Botón aceptar glassmorphism dorado - cristal tintado amarillo
            boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.1); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-size: 28px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 8, 0, 0, 0);");

            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.2); " +
                "-fx-text-fill: #e0c455; " +
                "-fx-border-color: rgba(212, 175, 55, 0.75); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-size: 28px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.3), 12, 0, 0, 0); " +
                "-fx-scale-y: 1.03; -fx-scale-x: 1.03;"));

            boton.setOnMouseExited(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.1); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-size: 28px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 8, 0, 0, 0);"));

            boton.setOnMousePressed(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.28); " +
                "-fx-text-fill: #f5e6c8; " +
                "-fx-border-color: #d4af37; " +
                "-fx-border-width: 2px; " +
                "-fx-font-size: 28px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.35), 4, 0, 0, 0); " +
                "-fx-translate-y: 2;"));

            boton.setOnMouseReleased(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.1); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.5); " +
                "-fx-border-width: 1.5px; " +
                "-fx-font-size: 28px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.15), 8, 0, 0, 0);"));
        } else {
            // Botón borrar - estilo glassmorphism dorado discreto
            boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);");

            boton.setOnMouseEntered(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.12); " +
                "-fx-text-fill: #e0c455; " +
                "-fx-border-color: rgba(212, 175, 55, 0.55); " +
                "-fx-border-width: 1.5px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.18), 8, 0, 0, 0);"));

            boton.setOnMouseExited(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);"));

            boton.setOnMousePressed(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.2); " +
                "-fx-text-fill: #f5e6c8; " +
                "-fx-border-color: rgba(212, 175, 55, 0.7); " +
                "-fx-border-width: 1.5px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.25), 4, 0, 0, 0); " +
                "-fx-translate-y: 2;"));

            boton.setOnMouseReleased(e -> boton.setStyle(estiloBase +
                "-fx-background-color: rgba(212, 175, 55, 0.05); " +
                "-fx-text-fill: #d4af37; " +
                "-fx-border-color: rgba(212, 175, 55, 0.3); " +
                "-fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.08), 6, 0, 0, 0);"));
        }

        return boton;
    }

    // =========================================================================
    // ACCIONES DEL NUMPAD
    // =========================================================================

    /**
     * Escribe un dígito al final del texto del campo activo.
     * 
     * @param digito Carácter a insertar ("0"-"9")
     */
    private void escribir(String digito) {
        if (campoActual == null) {
            logger.warn("No hay campo activo para escribir dígito");
            return;
        }

        String textoActual = campoActual.getText();
        String nuevoTexto = textoActual + digito;
        campoActual.setText(nuevoTexto);
        campoActual.positionCaret(nuevoTexto.length());

        logger.debug("Dígito '{}' insertado. Texto: '{}'", digito, nuevoTexto);
    }

    /**
     * Borra el último carácter del campo activo (backspace).
     */
    private void borrar() {
        if (campoActual == null) {
            logger.warn("No hay campo activo para borrar");
            return;
        }

        String textoActual = campoActual.getText();
        if (textoActual.length() > 0) {
            String nuevoTexto = textoActual.substring(0, textoActual.length() - 1);
            campoActual.setText(nuevoTexto);
            campoActual.positionCaret(nuevoTexto.length());
            logger.debug("Último dígito borrado. Texto: '{}'", nuevoTexto);
        }
    }

    /**
     * Acción del botón ✓ (Enter/Aceptar).
     * 
     * Comportamiento:
     * Dispara el evento onAction del campo activo, simulando que el usuario
     * presionó Enter en el campo. Esto permite que la lógica del controlador
     * (por ejemplo, verificar código → mostrar PIN, o enviar login) se ejecute
     * sin que el numpad necesite conocer el flujo de la vista.
     * 
     * Si el campo no tiene onAction configurado, intenta avanzar al siguiente
     * campo registrado o presionar el botón principal (defaultButton).
     */
    private void aceptar() {
        if (campoActual == null) {
            logger.warn("No hay campo activo en numpad");
            ocultar();
            return;
        }

        logger.info("Enter presionado en numpad, campo: {}", campoActual.getId());

        // Disparar el evento onAction del campo actual si es un TextField/PasswordField
        // Esto ejecuta el handler configurado en el controlador (ej: verificarCodigo, iniciarSesion)
        if (campoActual instanceof javafx.scene.control.TextField) {
            javafx.scene.control.TextField campoTexto = (javafx.scene.control.TextField) campoActual;
            if (campoTexto.getOnAction() != null) {
                logger.info("Disparando onAction del campo: {}", campoActual.getId());
                ocultar(); // Cerrar numpad antes de ejecutar la acción
                campoTexto.getOnAction().handle(new javafx.event.ActionEvent(campoActual, campoActual));
                return;
            }
        }

        // Sin onAction: intentar avanzar al siguiente campo o presionar botón principal
        int indiceActual = camposRegistrados.indexOf(campoActual);

        if (indiceActual >= 0 && indiceActual < camposRegistrados.size() - 1) {
            TextInputControl siguienteCampo = camposRegistrados.get(indiceActual + 1);
            logger.info("Numpad avanzando a campo: {}", siguienteCampo.getId());
            this.campoActual = siguienteCampo;
            ocultar(); // Cerrar numpad al avanzar
            javafx.application.Platform.runLater(() -> siguienteCampo.requestFocus());
        } else {
            logger.info("Último campo del numpad, buscando botón principal...");
            ocultar(); // Cerrar numpad
            javafx.application.Platform.runLater(() -> {
                Button botonPrincipal = buscarBotonPrincipal();
                if (botonPrincipal != null) {
                    logger.info("Numpad presionando botón: {}", botonPrincipal.getText());
                    ocultar();
                    botonPrincipal.fire();
                } else {
                    ocultar();
                }
            });
        }
    }

    /**
     * Busca el botón con defaultButton=true en el contenedor principal.
     * 
     * @return El botón principal o null si no existe
     */
    private Button buscarBotonPrincipal() {
        if (contenedorPrincipal == null) return null;

        for (var node : contenedorPrincipal.lookupAll(".button")) {
            if (node instanceof Button) {
                Button boton = (Button) node;
                if (boton.isDefaultButton()) {
                    return boton;
                }
            }
        }
        return null;
    }

    // =========================================================================
    // MOSTRAR / OCULTAR
    // =========================================================================

    /**
     * Muestra el numpad vinculado al campo especificado.
     * Se ejecuta automáticamente cuando un campo registrado recibe foco.
     * 
     * @param campo Campo de texto al que vincular el numpad
     */
    public void mostrar(TextInputControl campo) {
        this.campoActual = campo;

        contenedorNumpad.setVisible(true);
        contenedorNumpad.setManaged(true);
        contenedorNumpad.setOpacity(1.0);
        contenedorNumpad.toFront();

        if (contenedorStackPane != null) {
            contenedorStackPane.setMouseTransparent(false);
            contenedorStackPane.setVisible(true);
            contenedorStackPane.setOpacity(1.0);
            contenedorStackPane.toFront();
        }

        logger.info("Numpad MOSTRADO para: {}", 
            campo.getId() != null ? campo.getId() : "sin-id");
    }

    /**
     * Oculta el numpad y limpia el campo activo.
     * Se ejecuta automáticamente cuando el foco sale de todos los campos registrados.
     */
    public void ocultar() {
        contenedorNumpad.setVisible(false);
        contenedorNumpad.setManaged(false);

        if (contenedorStackPane != null) {
            contenedorStackPane.setMouseTransparent(true);
        }

        this.campoActual = null;

        logger.info("Numpad OCULTO");
    }

    /**
     * Obtiene el contenedor visual del numpad.
     * 
     * @return VBox contenedor del numpad
     */
    public VBox getContenedor() {
        return contenedorNumpad;
    }

    // =========================================================================
    // ARRASTRE
    // =========================================================================

    /**
     * Configura la funcionalidad de arrastre para mover el numpad por la pantalla.
     */
    private void configurarArrastre() {
        contenedorNumpad.setOnMousePressed(event -> {
            if (event.getTarget() == contenedorNumpad ||
                event.getTarget().toString().contains("VBox") ||
                event.getTarget().toString().contains("HBox") ||
                event.getTarget().toString().contains("GridPane")) {

                dragOffsetX = event.getSceneX() - contenedorNumpad.getTranslateX();
                dragOffsetY = event.getSceneY() - contenedorNumpad.getTranslateY();
                contenedorNumpad.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        contenedorNumpad.setOnMouseDragged(event -> {
            if (contenedorNumpad.getCursor() == javafx.scene.Cursor.CLOSED_HAND) {
                contenedorNumpad.setTranslateX(event.getSceneX() - dragOffsetX);
                contenedorNumpad.setTranslateY(event.getSceneY() - dragOffsetY);
                event.consume();
            }
        });

        contenedorNumpad.setOnMouseReleased(event -> {
            if (contenedorNumpad.getCursor() == javafx.scene.Cursor.CLOSED_HAND) {
                contenedorNumpad.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });

        contenedorNumpad.setOnMouseEntered(event -> {
            if (event.getTarget() == contenedorNumpad ||
                event.getTarget().toString().contains("VBox")) {
                contenedorNumpad.setCursor(javafx.scene.Cursor.OPEN_HAND);
            }
        });

        contenedorNumpad.setOnMouseExited(event -> {
            if (contenedorNumpad.getCursor() != javafx.scene.Cursor.CLOSED_HAND) {
                contenedorNumpad.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
    }

    // =========================================================================
    // ACTIVACIÓN ESTÁTICA
    // =========================================================================

    /**
     * Activa el numpad para un conjunto de campos de texto numéricos.
     * 
     * El numpad se mostrará automáticamente al recibir foco cualquiera de los
     * campos especificados, y se ocultará cuando el foco salga de todos ellos.
     * 
     * NO agrega botón icono - la activación es 100% por foco.
     * 
     * IMPORTANTE: Debe llamarse dentro de Platform.runLater() para que la
     * escena esté completamente cargada.
     * 
     * Ejemplo:
     * <pre>
     * {@code
     * Platform.runLater(() -> TecladoNumerico.activar(campoCodigo, campoCodigo, campoPin));
     * }
     * </pre>
     * 
     * @param nodoReferencia Cualquier nodo @FXML de la vista (para encontrar el StackPane raíz)
     * @param campos Campos de texto que usarán el numpad (en orden de navegación Enter)
     */
    public static void activar(Node nodoReferencia, TextInputControl... campos) {
        try {
            logger.info("Iniciando activación de teclado numérico...");

            if (nodoReferencia == null || nodoReferencia.getScene() == null) {
                logger.error("Nodo de referencia null o sin escena");
                return;
            }

            if (campos == null || campos.length == 0) {
                logger.warn("No se proporcionaron campos para el numpad");
                return;
            }

            TecladoNumerico numpad = new TecladoNumerico();

            // Encontrar StackPane raíz como overlay
            StackPane contenedorOverlay = encontrarContenedorOverlay(nodoReferencia);
            if (contenedorOverlay == null) {
                logger.error("No se encontró StackPane para overlay del numpad");
                return;
            }

            // Guardar referencia para buscar botón principal
            numpad.contenedorPrincipal = contenedorOverlay;

            // Crear contenedor overlay transparente
            StackPane contenedorNumpadStack = new StackPane();
            contenedorNumpadStack.setPickOnBounds(false);
            contenedorNumpadStack.setMouseTransparent(true);
            contenedorNumpadStack.setStyle("-fx-background-color: transparent;");

            numpad.contenedorStackPane = contenedorNumpadStack;

            // Posicionar pegado a la derecha, centrado verticalmente
            contenedorNumpadStack.getChildren().add(numpad.getContenedor());
            StackPane.setAlignment(numpad.getContenedor(), Pos.CENTER_RIGHT);
            StackPane.setMargin(numpad.getContenedor(), new Insets(0, 40, 0, 0));

            // Agregar al overlay
            contenedorOverlay.getChildren().add(contenedorNumpadStack);

            // Registrar campos y agregar listeners de clic
            // Se usa evento de mouse en vez de foco porque los campos tienen
            // focusTraversable=false para evitar auto-focus al cargar la vista
            for (TextInputControl campo : campos) {
                if (campo == null) continue;

                numpad.camposRegistrados.add(campo);

                // Al hacer clic en el campo: darle foco manualmente y mostrar numpad
                campo.setOnMouseClicked(event -> {
                    campo.setFocusTraversable(true);
                    campo.requestFocus();
                    numpad.mostrar(campo);
                });

                logger.info("Numpad registrado para campo: {} ({})", 
                    campo.getId() != null ? campo.getId() : "sin-id",
                    campo.getClass().getSimpleName());
            }

            logger.info("=== TECLADO NUMÉRICO ACTIVADO ===");
            logger.info("Campos registrados: {}", campos.length);
            logger.info("=================================");

        } catch (Exception e) {
            logger.error("Error activando teclado numérico: ", e);
        }
    }

    /**
     * Busca el StackPane más alto en la jerarquía de padres.
     * Se usa como contenedor overlay para posicionar el numpad flotante.
     * 
     * @param nodo Nodo desde el cual buscar hacia arriba
     * @return StackPane más alto encontrado, o null
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
}
