/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.componente;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/* Clase que aplica un borde dorado interactivo a un modal. 
* El borde se ilumina dinámicamente según la proximidad del mouse a cada lado del modal, creando un efecto visual atractivo y moderno.
* Características:
* - El borde tiene un color dorado con opacidad variable (más brillante al acercarse).
* - El brillo se calcula por separado para cada lado (arriba, derecha, abajo, izquierda) según la distancia del mouse a ese lado.
* - El efecto se activa al mover el mouse sobre el modal y se desactiva al salir del área del modal.
* - El borde tiene un grosor de 1.5px y esquinas redondeadas (14px) para coincidir con el diseño del modal.
* - El rendimiento se optimiza limitando la frecuencia de actualización del borde a aproximadamente 60 FPS y evitando cálculos innecesarios.
* Uso:
* - Crear una instancia de BordeInteractivoModal y llamar a iniciar(nodoModal) al mostrar el modal.
* - Llamar a detener() al cerrar el modal para limpiar los listeners y evitar memory leaks.
* - El nodoModal debe ser un Region (como StackPane) que representa el área del modal al que se le aplicará el borde interactivo.
* - El efecto es completamente independiente del contenido del modal y no requiere modificaciones en el CSS, ya que el borde se genera dinámicamente con la API de JavaFX.
* - El diseño del borde (color, grosor, radio de esquinas) se puede ajustar fácilmente modificando las constantes en la clase.
*/
public class BordeInteractivoModal {

    private static final double OPACIDAD_BASE = 0.3;
    private static final double OPACIDAD_BRILLO = 0.5;
    private static final double DISTANCIA_ACTIVACION = 120.0;
    private static final double RADIO_ESQUINAS = 14.0;
    private static final double GROSOR_BORDE = 1.5;
    private static final long INTERVALO_MIN_NS = 16_000_000L;
    private static final Border BORDE_BASE = crearBorde(OPACIDAD_BASE, OPACIDAD_BASE, OPACIDAD_BASE, OPACIDAD_BASE);
    private Region nodoObjetivo;
    private EventHandler<MouseEvent> handlerMovimiento;
    private EventHandler<MouseEvent> handlerSalida;
    private long ultimaActualizacion = 0;

    // Inicia el efecto interactivo en el nodo modal especificado. Registra los listeners necesarios para actualizar el borde según el movimiento del mouse.
    public void iniciar(Region nodoModal) {
        if (nodoModal == null) return;
        this.nodoObjetivo = nodoModal;

        nodoModal.setBorder(BORDE_BASE);
        if (MotorAnimaciones.instancia().isDesactivadas()) return;

        handlerMovimiento = evento -> {
            long ahora = System.nanoTime();
            if (ahora - ultimaActualizacion < INTERVALO_MIN_NS) return;
            ultimaActualizacion = ahora;
            actualizarBrillo(evento.getX(), evento.getY());
        };

        handlerSalida = evento -> nodoModal.setBorder(BORDE_BASE);
        nodoModal.addEventHandler(MouseEvent.MOUSE_MOVED, handlerMovimiento);
        nodoModal.addEventHandler(MouseEvent.MOUSE_EXITED, handlerSalida);
    }

    /**
     * Detiene el efecto interactivo y limpia los listeners registrados.
     * Debe llamarse al cerrar el modal para evitar memory leaks.
     */
    public void detener() {
        if (nodoObjetivo != null) {
            nodoObjetivo.removeEventHandler(MouseEvent.MOUSE_MOVED, handlerMovimiento);
            nodoObjetivo.removeEventHandler(MouseEvent.MOUSE_EXITED, handlerSalida);
            nodoObjetivo.setBorder(null); // Limpiar; el modal se destruye igual

            nodoObjetivo = null;
        }
        handlerMovimiento = null;
        handlerSalida = null;
    }

    // Actualiza el brillo del borde según la posición del mouse. Calcula la intensidad de cada lado y aplica un nuevo borde con opacidades ajustadas.
    private void actualizarBrillo(double mouseX, double mouseY) {
        if (nodoObjetivo == null) return;
        double w = nodoObjetivo.getWidth();
        double h = nodoObjetivo.getHeight();
        if (w <= 0 || h <= 0) return;

        double iArriba = calcularIntensidad(mouseY);
        double iAbajo = calcularIntensidad(h - mouseY);
        double iIzquierda = calcularIntensidad(mouseX);
        double iDerecha = calcularIntensidad(w - mouseX);
        double opArr = OPACIDAD_BASE + iArriba * OPACIDAD_BRILLO;
        double opDer = OPACIDAD_BASE + iDerecha * OPACIDAD_BRILLO;
        double opAbj = OPACIDAD_BASE + iAbajo * OPACIDAD_BRILLO;
        double opIzq = OPACIDAD_BASE + iIzquierda * OPACIDAD_BRILLO;

        nodoObjetivo.setBorder(crearBorde(opArr, opDer, opAbj, opIzq));
    }

    // Calcula la intensidad de brillo para un lado según la distancia del mouse a ese lado. Retorna un valor entre 0.0 (lejos) y 1.0 (muy cerca).
    private static double calcularIntensidad(double distancia) {
        if (distancia >= DISTANCIA_ACTIVACION) return 0.0;
        if (distancia <= 0) return 1.0;
        double t = 1.0 - (distancia / DISTANCIA_ACTIVACION);
        return t * t; // ease-out cuadrático: brillo aumenta rápido al acercarse
    }

    // Crea un borde con opacidades específicas para cada lado. El color es dorado (RGB 212, 175, 55) con la opacidad dada.
    private static Border crearBorde(double opArr, double opDer, double opAbj, double opIzq) {
        return new Border(new BorderStroke(
                Color.rgb(212, 175, 55, opArr), 
                Color.rgb(212, 175, 55, opDer), 
                Color.rgb(212, 175, 55, opAbj), 
                Color.rgb(212, 175, 55, opIzq), 
                BorderStrokeStyle.SOLID,
                BorderStrokeStyle.SOLID,
                BorderStrokeStyle.SOLID,
                BorderStrokeStyle.SOLID,
                new CornerRadii(RADIO_ESQUINAS),
                new BorderWidths(GROSOR_BORDE),
                Insets.EMPTY
        ));
    }
}
