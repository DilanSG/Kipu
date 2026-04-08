/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilidad para mostrar diálogos de alerta al usuario en JavaFX.
 * 
 * Esta clase centraliza la creación de ventanas emergentes (alerts) para
 * comunicarse con el usuario de manera consistente en toda la aplicación.
 * 
 * ¿Por qué usar esta utilidad?
 * - Consistencia: Todos los diálogos tienen el mismo estilo
 * - Simplicidad: Un solo método en lugar de 5-6 líneas de código repetidas
 * - Mantenibilidad: Cambios en el estilo se aplican automáticamente en toda la app
 * 
 * Tipos de alertas disponibles:
 * 
 * 1. ERROR:
 *    - Usado para: Errores de validación, errores de red, operaciones fallidas
 *    - Color: Rojo
 *    - Icono: X roja
 *    - Ejemplo: "Error de conexión", "Credenciales inválidas"
 * 
 * 2. INFORMATION:
 *    - Usado para: Confirmaciones, mensajes informativos
 *    - Color: Azul
 *    - Icono: i informativa
 *    - Ejemplo: "Producto creado exitosamente", "Pedido enviado"
 * 
 * 3. WARNING:
 *    - Usado para: Advertencias, acciones que requieren atención
 *    - Color: Amarillo/Naranja
 *    - Icono: Triángulo de advertencia
 *    - Ejemplo: "Stock bajo", "La caja está por cerrar"
 * 
 * 4. CONFIRMATION:
 *    - Usado para: Solicitar confirmación antes de una acción importante
 *    - Botones: OK y Cancelar
 *    - Retorna: true si el usuario presiona OK, false si cancela
 *    - Ejemplo: "¿Está seguro de eliminar este producto?"
 * 
 * Uso en el código:
 * 
 * // Mostrar error
 * AlertaUtil.mostrarError(
 *     "Error de Conexión",
 *     "No se pudo conectar al servidor. Verifique la red."
 * );
 * 
 * // Mostrar información
 * AlertaUtil.mostrarInformacion(
 *     "Operación Exitosa",
 *     "El producto se creó correctamente."
 * );
 * 
 * // Mostrar advertencia
 * AlertaUtil.mostrarAdvertencia(
 *     "Stock Bajo",
 *     "El producto 'Cerveza Corona' tiene solo 5 unidades."
 * );
 * 
 * // Solicitar confirmación
 * boolean confirma = AlertaUtil.mostrarConfirmacion(
 *     "Eliminar Producto",
 *     "¿Está seguro de eliminar el producto 'Pizza Margarita'?"
 * );
 * if (confirma) {
 *     // Usuario confirmó, proceder con eliminación
 * }
 * 
 * Características técnicas:
 * - Bloquean la ventana principal (modales)
 * - El usuario DEBE responder antes de continuar
 * - Se centran automáticamente en la pantalla
 * - Heredan el tema de la aplicación (tema oscuro si está habilitado)
 */
public class AlertaUtil {

    private static final Logger logger = LoggerFactory.getLogger(AlertaUtil.class);

    /**
     * Muestra un diálogo de error
     */
    public static void mostrarError(String titulo, String mensaje) {
        mostrarAlerta(AlertType.ERROR, titulo, mensaje);
    }

    /**
     * Muestra un diálogo de información
     */
    public static void mostrarInformacion(String titulo, String mensaje) {
        mostrarAlerta(AlertType.INFORMATION, titulo, mensaje);
    }

    /**
     * Muestra un diálogo de advertencia
     */
    public static void mostrarAdvertencia(String titulo, String mensaje) {
        mostrarAlerta(AlertType.WARNING, titulo, mensaje);
    }

    /**
     * Muestra un diálogo de éxito
     */
    public static void mostrarExito(String titulo, String mensaje) {
        mostrarAlerta(AlertType.INFORMATION, titulo, mensaje);
    }

    /**
     * Muestra un diálogo de confirmación.
     * Retorna true si el usuario acepta, false si cancela.
     */
    public static boolean mostrarConfirmacion(String titulo, String mensaje) {
        Alert alerta = new Alert(AlertType.CONFIRMATION);
        estilizarAlerta(alerta);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        
        java.util.Optional<javafx.scene.control.ButtonType> resultado = alerta.showAndWait();
        return resultado.isPresent() && resultado.get() == javafx.scene.control.ButtonType.OK;
    }

    /**
     * Método auxiliar para mostrar alertas
     */
    private static void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        estilizarAlerta(alerta);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
    
    /**
     * Aplica los estilos CSS a la alerta para mantener consistencia visual.
     */
    private static void estilizarAlerta(Alert alerta) {
        try {
            javafx.scene.control.DialogPane dialogPane = alerta.getDialogPane();
            dialogPane.getStylesheets().add(
                AlertaUtil.class.getResource("/css/estilos.css").toExternalForm()
            );
            dialogPane.getStyleClass().add("dialogo-alerta");
        } catch (Exception e) {
            // Si falla la carga del CSS, la alerta se muestra con estilo por defecto
            logger.warn("No se pudo cargar el estilo de la alerta: {}", e.getMessage());
        }
    }

    /**
     * Muestra una alerta de información.
     *
     * @param titulo  El título de la ventana
     * @param mensaje El mensaje a mostrar
     */
    public static void mostrarInfo(String titulo, String mensaje) {
        mostrarInformacion(titulo, mensaje);
    }
}
