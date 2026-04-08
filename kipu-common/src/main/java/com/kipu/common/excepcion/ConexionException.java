/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.excepcion;

import com.kipu.common.constantes.Constantes;

/* Excepción lanzada cuando hay errores de conexión con el servidor.
 * Se usa para encapsular problemas de red, timeouts o servidor no disponible.
 * Código HTTP asociado: 503 SERVICE UNAVAILABLE
 * Subtipos:
 * - TimeoutException: cuando la conexión excede el tiempo límite establecido
 * - ServidorNoDisponibleException: cuando el servidor no responde o está caído */
public class ConexionException extends KipuException {
    
    // Constructor con mensaje y causa original para mantener el stack trace completo y facilitar la depuración de errores de conexión, especialmente cuando se envuelven excepciones técnicas (IOException, SocketTimeoutException) en esta excepción de dominio con significado específico.
    public ConexionException(String mensaje, Throwable causa) {
        super(Constantes.CodigosError.ERROR_CONEXION, mensaje, causa);
    }
    
    // Constructor con mensaje para casos donde no se tiene una causa original específica, pero se quiere indicar un error de conexión genérico.
    public ConexionException(String mensaje) {
        super(Constantes.CodigosError.ERROR_CONEXION, mensaje);
    }
    
    // Factory method para timeout de conexión. Permite crear una instancia de ConexionException con un mensaje predefinido para este caso específico.
    public static ConexionException timeout() {
        return new ConexionException("Tiempo de espera agotado. El servidor no responde.");
    }
    
    // Factory method para servidor no disponible.
    public static ConexionException servidorNoDisponible() {
        return new ConexionException("No se puede conectar con el servidor. Verifique que esté ejecutándose.");
    }
}
