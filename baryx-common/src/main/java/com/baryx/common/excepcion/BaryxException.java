/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.excepcion;

/* Excepción base personalizada para errores de negocio en Baryx.
 * Permite estandarizar el manejo de errores con códigos específicos y mensajes claros.
 * Todas las excepciones de negocio deben extender esta clase para mantener una jerarquía coherente.
 * Campos:
 * - codigoError: un código de error estandarizado (ej: "RECURSO_NO_ENCONTRADO", "VALIDACION_FALLIDA")
 * - mensaje: una descripción clara del error para el usuario o desarrollador (heredada de RuntimeException)
 * Constructores:
 * - BaryxException(String codigoError, String mensaje): constructor principal con código y mensaje
 * - BaryxException(String codigoError, String mensaje, Throwable causa): constructor con causa para excepciones anidadas (exception chaining)
 * Uso:
 * - Lanzar esta excepción en casos de errores de negocio, validaciones fallidas o situaciones que no deberían ocurrir en condiciones normales.
 * - El código de error ayuda a identificar rápidamente el tipo de error sin necesidad de analizar el mensaje completo.
 * - La causa original (Throwable) puede ser útil para depuración y logging, especialmente cuando se encapsulan excepciones técnicas (SQLException, IOException) en excepciones de dominio con significado de negocio. */
public abstract class BaryxException extends RuntimeException {
    
    private final String codigoError;
    
    // Constructor principal con código de error y mensaje descriptivo
    public BaryxException(String codigoError, String mensaje) {
        super(mensaje);
        this.codigoError = codigoError;
    }
    
    // Constructor con causa original (exception chaining) para mantener el stack trace completo y facilitar la depuración cuando se envuelven excepciones técnicas en excepciones de negocio con significado específico.
    public BaryxException(String codigoError, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigoError = codigoError;
    }
    
    public String getCodigoError() {
        return codigoError;
    }
    
    @Override
    public String toString() {
        return String.format("%s [%s]: %s", 
                           getClass().getSimpleName(), 
                           codigoError, 
                           getMessage());
    }
}