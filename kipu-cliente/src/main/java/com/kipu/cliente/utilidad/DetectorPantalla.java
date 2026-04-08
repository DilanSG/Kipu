/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.*/
package com.kipu.cliente.utilidad;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*Detecta las características de la pantalla y aplica el perfil de resolución óptimo al shell de la aplicación. Gestiona el escalado dinámico y las clases CSS de resolución y ratio.
 Flujo:
 *Detecta resolución real, DPI y escala del OS
 *Selecciona el perfil más cercano (o el manual si el usuario lo configuró)
 *Aplica la resolución de diseño del perfil al shell
 *Configura el Scale transform para cubrir la diferencia residual
 *Aplica clases CSS de resolución y ratio al contenedor raíz
 *Ajusta el font-size global según el factor de texto del perfil */
public class DetectorPantalla {

    private static final Logger logger = LoggerFactory.getLogger(DetectorPantalla.class);

    /** Tolerancia máxima de estiramiento por eje (8%) para reducir barras negras */
    private static final double TOLERANCIA_ESCALADO = 0.08;

    /** Tamaño de fuente base del diseño en píxeles */
    private static final double FONT_SIZE_BASE = 14.0;

    /** Factor de escala manual configurado por el usuario (por defecto 1.0) */
    private double escalaManual = 1.0;

    /** Perfil activo actualmente */
    private ResolucionPerfil perfilActivo;

    /** Transform de escalado aplicado al nodo raíz */
    private Scale escalaTransform;

    // Información de pantalla detectada (disponible para UI de configuración)
    private double anchoDetectado;
    private double altoDetectado;
    private double dpiDetectado;
    private double escalaOsDetectada;

    /**
     * Detecta la pantalla primaria y selecciona el perfil óptimo.
     * Debe llamarse desde el JavaFX Application Thread después de que el Stage
     * sea visible para obtener métricas precisas.
     *
     * @return El perfil seleccionado (automático o manual según configuración)
     */
    public ResolucionPerfil detectarYSeleccionar() {
        Screen pantalla = Screen.getPrimary();
        Rectangle2D limites = pantalla.getVisualBounds();
        anchoDetectado = limites.getWidth();
        altoDetectado = limites.getHeight();
        dpiDetectado = pantalla.getDpi();
        escalaOsDetectada = pantalla.getOutputScaleX();

        logger.info("[Pantalla] Detectada: {}×{} | DPI: {} | Escala OS: {}x | Ratio: {}",
                (int) anchoDetectado, (int) altoDetectado,
                (int) dpiDetectado, escalaOsDetectada,
                String.format("%.2f", anchoDetectado / altoDetectado));

        // Cargar configuración del usuario
        escalaManual = ConfiguracionCliente.getEscalaManual();
        String perfilManual = ConfiguracionCliente.getPerfilPantalla();

        if (!"AUTO".equals(perfilManual)) {
            // El usuario eligió un perfil específico
            try {
                perfilActivo = ResolucionPerfil.valueOf(perfilManual);
                logger.info("[Pantalla] Perfil manual seleccionado: {} ({})",
                        perfilActivo.name(), perfilActivo.getNombreLocalizado());
                return perfilActivo;
            } catch (IllegalArgumentException e) {
                logger.warn("[Pantalla] Perfil '{}' no válido, usando detección automática", perfilManual);
            }
        }

        // Detección automática: encontrar el perfil más cercano
        perfilActivo = seleccionarPerfilAutomatico(anchoDetectado, altoDetectado);
        logger.info("[Pantalla] Perfil automático: {} ({}) | Factor texto: {}",
                perfilActivo.name(), perfilActivo.getNombreLocalizado(), perfilActivo.getFactorTexto());
        return perfilActivo;
    }

    /**
     * Selecciona el perfil con menor distancia euclidiana a la resolución real.
     */
    private ResolucionPerfil seleccionarPerfilAutomatico(double anchoReal, double altoReal) {
        ResolucionPerfil mejor = ResolucionPerfil.FHD_16_9; // fallback
        double menorDistancia = Double.MAX_VALUE;

        for (ResolucionPerfil perfil : ResolucionPerfil.values()) {
            double distancia = perfil.calcularDistancia(anchoReal, altoReal);
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                mejor = perfil;
            }
        }
        return mejor;
    }

    /**
     * Aplica el perfil de resolución al shell de la aplicación.
     * Configura las dimensiones del shell, el transform de escalado,
     * las clases CSS y el font-size global.
     *
     * @param contenedorShell StackPane raíz del shell (donde se renderizan las vistas)
     * @param contenedorEscalado StackPane exterior que envuelve al Group+shell
     * @param escena Scene principal de la aplicación
     */
    public void aplicar(StackPane contenedorShell, StackPane contenedorEscalado, Scene escena) {
        if (perfilActivo == null) {
            detectarYSeleccionar();
        }

        double anchoDiseno = perfilActivo.getAnchoDiseno();
        double altoDiseno = perfilActivo.getAltoDiseno();

        // 1. Configurar dimensiones del shell al perfil seleccionado
        contenedorShell.setPrefWidth(anchoDiseno);
        contenedorShell.setPrefHeight(altoDiseno);
        contenedorShell.setMinWidth(anchoDiseno);
        contenedorShell.setMinHeight(altoDiseno);
        contenedorShell.setMaxWidth(anchoDiseno);
        contenedorShell.setMaxHeight(altoDiseno);

        // 2. Aplicar escalado uniforme con tolerancia
        aplicarEscalado(contenedorShell, escena, anchoDiseno, altoDiseno);

        // 3. Clases CSS de resolución y ratio
        aplicarClasesCSS(contenedorEscalado, escena);

        // 4. Font-size global dinámico
        aplicarFontSizeGlobal(contenedorEscalado);

        logger.info("[Pantalla] Perfil aplicado: {} | Diseño: {}×{} | Texto base: {}px | Escala manual: {}x",
                perfilActivo.name(), (int) anchoDiseno, (int) altoDiseno,
                calcularFontSizeEfectivo(), escalaManual);
    }

    /**
     * Escala la UI para adaptarse a la pantalla real.
     * Usa escalado uniforme con tolerancia del 8% para reducir barras negras.
     */
    private void aplicarEscalado(StackPane raiz, Scene escena,
                                  double anchoDiseno, double altoDiseno) {
        escalaTransform = new Scale(1, 1);
        raiz.getTransforms().clear();
        raiz.getTransforms().add(escalaTransform);

        Runnable recalcular = () -> {
            double anchoReal = escena.getWidth();
            double altoReal = escena.getHeight();
            if (anchoReal <= 0 || altoReal <= 0) return;

            double factorX = anchoReal / anchoDiseno;
            double factorY = altoReal / altoDiseno;
            double factorBase = Math.min(factorX, factorY);

            // Permitir leve stretch (hasta TOLERANCIA_ESCALADO) para reducir barras negras
            double escalaFinalX = Math.min(factorX, factorBase * (1 + TOLERANCIA_ESCALADO));
            double escalaFinalY = Math.min(factorY, factorBase * (1 + TOLERANCIA_ESCALADO));

            // Aplicar escala manual del usuario
            escalaTransform.setX(escalaFinalX * escalaManual);
            escalaTransform.setY(escalaFinalY * escalaManual);
        };

        escena.widthProperty().addListener((obs, old, nuevo) -> recalcular.run());
        escena.heightProperty().addListener((obs, old, nuevo) -> recalcular.run());
        Platform.runLater(recalcular);
    }

    /**
     * Agrega clases CSS al contenedor según resolución y ratio del perfil activo.
     * También detecta clases adicionales de ratio para proporciones extremas.
     */
    private void aplicarClasesCSS(StackPane contenedor, Scene escena) {
        Runnable actualizar = () -> {
            double ancho = escena.getWidth();
            double alto = escena.getHeight();
            if (ancho <= 0 || alto <= 0) return;

            contenedor.getStyleClass().removeIf(c -> c.startsWith("res-") || c.startsWith("ratio-"));

            // Clase de resolución del perfil
            contenedor.getStyleClass().add(perfilActivo.getClaseResolucion());

            // Clase de ratio detectada dinámicamente (más precisa que la del perfil)
            double ratio = ancho / alto;
            if (ratio >= 2.2)       contenedor.getStyleClass().add("ratio-superwide");
            else if (ratio >= 1.9)  contenedor.getStyleClass().add("ratio-ultrawide");
            else if (ratio >= 1.7)  contenedor.getStyleClass().add("ratio-wide");
            else if (ratio >= 1.5)  contenedor.getStyleClass().add("ratio-standard");
            else if (ratio >= 1.2)  contenedor.getStyleClass().add("ratio-classic");
            else                    contenedor.getStyleClass().add("ratio-tall");
        };

        escena.widthProperty().addListener((obs, old, nuevo) -> actualizar.run());
        escena.heightProperty().addListener((obs, old, nuevo) -> actualizar.run());
        Platform.runLater(actualizar);
    }

    /**
     * Aplica el font-size base global usando el factor del perfil + escala manual.
     * JavaFX hereda -fx-font-size de .root a todos los nodos hijos.
     */
    private void aplicarFontSizeGlobal(StackPane contenedor) {
        double fontSize = calcularFontSizeEfectivo();
        contenedor.setStyle(contenedor.getStyle()
                + "; -fx-font-size: " + String.format("%.1f", fontSize) + "px;");
    }

    /** Calcula el font-size efectivo combinando perfil + escala manual + DPI */
    private double calcularFontSizeEfectivo() {
        double factorDpi = 1.0;
        // Compensar DPI alto (>120) para que no se vea demasiado pequeño
        if (dpiDetectado > 120 && escalaOsDetectada <= 1.0) {
            factorDpi = Math.min(dpiDetectado / 96.0, 2.0);
        }
        return FONT_SIZE_BASE * perfilActivo.getFactorTexto() * escalaManual * factorDpi;
    }

    // ==================== GETTERS (para UI de configuración) ====================

    public ResolucionPerfil getPerfilActivo() {
        return perfilActivo;
    }

    public double getAnchoDetectado() {
        return anchoDetectado;
    }

    public double getAltoDetectado() {
        return altoDetectado;
    }

    public double getDpiDetectado() {
        return dpiDetectado;
    }

    public double getEscalaOsDetectada() {
        return escalaOsDetectada;
    }

    public double getRatioDetectado() {
        return altoDetectado > 0 ? anchoDetectado / altoDetectado : 1.78;
    }

    public double getEscalaManual() {
        return escalaManual;
    }

    /**
     * Cambia la escala manual y la persiste en la configuración.
     * La escala se aplica inmediatamente al transform activo.
     *
     * @param nuevaEscala Factor entre 0.5 y 2.0
     */
    public void setEscalaManual(double nuevaEscala) {
        this.escalaManual = Math.max(0.5, Math.min(2.0, nuevaEscala));
        ConfiguracionCliente.setEscalaManual(this.escalaManual);

        // Recalcular el transform si ya se aplicó
        if (escalaTransform != null) {
            logger.info("[Pantalla] Escala manual cambiada a {}x", this.escalaManual);
        }
    }

    /**
     * Cambia el perfil activo y lo persiste. Requiere reinicio o recarga
     * del shell para aplicar completamente (el cambio de resolución base
     * afecta el layout de todas las vistas).
     *
     * @param perfil Nuevo perfil, o null para "AUTO"
     */
    public void setPerfil(ResolucionPerfil perfil) {
        if (perfil == null) {
            ConfiguracionCliente.setPerfilPantalla("AUTO");
            perfilActivo = seleccionarPerfilAutomatico(anchoDetectado, altoDetectado);
        } else {
            ConfiguracionCliente.setPerfilPantalla(perfil.name());
            perfilActivo = perfil;
        }
        logger.info("[Pantalla] Perfil cambiado a: {}", perfilActivo.name());
    }
}
