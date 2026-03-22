/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.excepcion;

/* Excepción lanzada cuando hay errores de autenticación.
 * Se usa cuando las credenciales son inválidas, el token JWT es incorrecto o el token ha expirado.
 *Código HTTP asociado: 401 UNAUTHORIZED
 *Subtipos:
 *-CredencialesInvalidasException: usuario/contraseña incorrectos
 *-TokenInvalidoException: token JWT mal formado o firma inválida
 *-TokenExpiradoException: token JWT expirado
 * Diferencia con AutorizacionException:
 *-AutenticacionException: "¿Quién eres?" → credenciales incorrectas
 *-AutorizacionException: "¿Qué puedes hacer?" → permisos insuficientes
 */
public class AutenticacionException extends BaryxException {
    
    // Constructores para diferentes escenarios de autenticación fallida
    public AutenticacionException(String codigoError, String mensaje) {
        super(codigoError, mensaje);
    }
    
    // Constructor con causa para excepciones anidadas
    public AutenticacionException(String codigoError, String mensaje, Throwable causa) {
        super(codigoError, mensaje, causa);
    }
}
