/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.excepcion;

import com.baryx.common.constantes.Constantes;

/* Excepción lanzada cuando hay errores de validación de datos.
 * Se usa para indicar que los datos de entrada no cumplen con las reglas de negocio o formato esperado.
 * Código HTTP asociado: 400 BAD REQUEST
 * Subtipos:
 * - CampoRequeridoException: cuando falta un campo obligatorio
 * - LongitudInvalidaException: cuando un campo tiene una longitud fuera del rango permitido
 * - FormatoInvalidoException: cuando un campo no tiene el formato correcto (ej: email, fecha) */
public class ValidacionException extends BaryxException {
    
    // Constructor con mensaje para indicar qué error de validación ocurrió. El mensaje debe ser claro y específico para facilitar la comprensión del error por parte del usuario o desarrollador.
    public ValidacionException(String mensaje) {
        super(Constantes.CodigosError.VALIDACION_ERROR, mensaje);
    }
    
    // Constructor con mensaje y causa original para mantener el stack trace completo y facilitar la depuración de errores de validación, especialmente cuando se envuelven excepciones técnicas (IllegalArgumentException, ParseException) en esta excepción de dominio con significado específico.
    public ValidacionException(String mensaje, Throwable causa) {
        super(Constantes.CodigosError.VALIDACION_ERROR, mensaje, causa);
    }
    
    // Factory method para campo requerido. Permite crear una instancia de ValidacionException con un mensaje predefinido para este caso específico, indicando claramente qué campo es obligatorio.
    public static ValidacionException campoRequerido(String nombreCampo) {
        return new ValidacionException(
            String.format("El campo '%s' es requerido", nombreCampo)
        );
    }
    
    /* Factory method para longitud inválida. Permite crear una instancia de ValidacionException con un mensaje predefinido para este caso específico, indicando claramente qué campo tiene una longitud fuera del rango permitido y cuál es la longitud actual, lo que facilita la identificación del error. */
    public static ValidacionException longitudInvalida(String nombreCampo, 
                                                       int longitudActual,
                                                       int longitudMinima, 
                                                       int longitudMaxima) {
        return new ValidacionException(
            String.format("El campo '%s' debe tener entre %d y %d caracteres (actual: %d)", nombreCampo, longitudMinima, longitudMaxima, longitudActual)
        );
    }
}
