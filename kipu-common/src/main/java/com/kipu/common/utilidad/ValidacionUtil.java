/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.utilidad;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.excepcion.ValidacionException;
import java.math.BigDecimal;

/* Clase de utilidad para validaciones comunes en la aplicación.
 Proporciona métodos para validar nombres de usuario, contraseñas, PINs, emails, precios, cantidades, etc.
 Utiliza las reglas definidas en Constantes.Validaciones para mantener consistencia en toda la aplicación.
 Lanza ValidacionException con mensajes claros cuando una validación falla.
 */
public final class ValidacionUtil {
    
    // Constructor privado para evitar instanciación, ya que esta clase solo contiene métodos estáticos y no tiene estado.
    private ValidacionUtil() {
        throw new UnsupportedOperationException("Clase de utilidad no instanciable");
    }
    
    // Valida que un nombre de usuario sea válido.
    public static boolean esUsuarioValido(String usuario) {
        if (usuario == null || usuario.trim().isEmpty()) {
            return false;
        }
        
        int longitud = usuario.length();
        return longitud >= Constantes.Validaciones.USUARIO_MIN_LENGTH
            && longitud <= Constantes.Validaciones.USUARIO_MAX_LENGTH
            && usuario.matches(Constantes.Validaciones.PATRON_USUARIO);
    }
    
    // Valida que una contraseña sea válida.
    public static boolean esPasswordValido(String password) {
        return password != null 
            && password.length() >= Constantes.Validaciones.PASSWORD_MIN_LENGTH;
    }
    
    // Valida que un PIN sea válido.
    public static boolean esPinValido(String pin) {
        return pin != null 
            && pin.matches(Constantes.Validaciones.PATRON_PIN);
    }
    
    // Valida que un nombre no sea nulo ni vacío.
    public static boolean esTextoVacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }
    
    // Valida que un texto contenga solo dígitos (0-9).
     public static boolean esSoloDigitos(String texto) {
        return texto != null && !texto.isEmpty() && texto.matches("\\d+");
    }
    
    // Valida que un texto tenga al menos una longitud mínima.
    public static boolean esLongitudMinima(String texto, int min) {
        return texto != null && texto.length() >= min;
    }
    
    // Valida que un texto no exceda una longitud máxima.
    public static boolean esLongitudExacta(String texto, int longitud) {
        return texto != null && texto.length() == longitud;
    }
    
    // Valida que un número entero esté dentro de un rango específico (inclusive).
     public static boolean estaEnRango(int valor, int min, int max) {
        return valor >= min && valor <= max;
    }
    
    // Valida que un número decimal esté dentro de un rango específico (inclusive).
    public static boolean esEmailValido(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches(Constantes.Validaciones.PATRON_EMAIL);
    }
    
    // Valida que un precio sea válido (mayor a cero).
    public static boolean esPrecioValido(BigDecimal precio) {
        return precio != null && precio.compareTo(BigDecimal.ZERO) > 0;
    }
    
    // Valida que una cantidad sea válida (mayor a cero).
    public static boolean esCantidadValida(int cantidad) {
        return cantidad > 0;
    }
    
    // Valida que un porcentaje sea válido (entre 0 y 100 inclusive).
    public static boolean esPorcentajeValido(BigDecimal porcentaje) {
        return porcentaje != null 
            && porcentaje.compareTo(BigDecimal.ZERO) >= 0 
            && porcentaje.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
    
    // Valida que un texto requerido no sea nulo ni vacío. Lanza ValidacionException con mensaje específico si falla.
    public static String validarRequerido(String valor, String nombreCampo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw ValidacionException.campoRequerido(nombreCampo);
        }
        return valor.trim();
    }
    
    // Valida que un número requerido no sea nulo. Lanza ValidacionException con mensaje específico si falla.
    public static Long validarRequerido(Long valor, String nombreCampo) {
        if (valor == null) {
            throw ValidacionException.campoRequerido(nombreCampo);
        }
        return valor;
    }
    
    // Valida que un valor monetario requerido no sea nulo y sea mayor a cero. Lanza ValidacionException con mensaje específico si falla.
    public static void validarLongitud(String valor, String nombreCampo, int min, int max) {
        if (valor == null) {
            throw ValidacionException.campoRequerido(nombreCampo);
        }
        
        int longitud = valor.length();
        if (longitud < min || longitud > max) {
            throw ValidacionException.longitudInvalida(nombreCampo, longitud, min, max);
        }
    }
    
    // Valida que un texto cumpla con un patrón regex específico. Lanza ValidacionException con mensaje específico si falla.
    public static void validarPatron(String valor, String patron, String nombreCampo, String mensajeError) {
        if (valor == null || !valor.matches(patron)) {
            throw new ValidacionException(
                String.format("El campo '%s' %s", nombreCampo, mensajeError)
            );
        }
    }
    
    // Valida que un valor monetario esté dentro de un rango específico. Lanza ValidacionException con mensaje específico si falla.
    public static void validarRango(BigDecimal valor, BigDecimal min, BigDecimal max, String nombreCampo) {
        if (valor == null) {
            throw ValidacionException.campoRequerido(nombreCampo);
        }
        
        if (valor.compareTo(min) < 0 || valor.compareTo(max) > 0) {
            throw new ValidacionException(
                String.format("El campo '%s' debe estar entre %s y %s", nombreCampo, min, max)
            );
        }
    }
}
