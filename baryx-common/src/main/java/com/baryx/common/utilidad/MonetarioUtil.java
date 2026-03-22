/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.utilidad;

import java.math.BigDecimal;
import java.math.RoundingMode;

/* Clase de utilidad para operaciones monetarias.
 Proporciona métodos para redondear, sumar, restar, multiplicar, dividir y formatear valores monetarios.
 Utiliza BigDecimal para evitar problemas de precisión con decimales.
 El redondeo se realiza a 2 decimales (centavos) utilizando el modo HALF_UP (redondeo bancario).
 */
public final class MonetarioUtil {
    
     private static final int DECIMALES = 2;
    
    private static final RoundingMode MODO_REDONDEO = RoundingMode.HALF_UP;
    
    // Constructor privado para evitar instanciación, ya que esta clase solo contiene métodos estáticos y no tiene estado.
    private MonetarioUtil() {
        throw new UnsupportedOperationException("Clase de utilidad no instanciable");
    }
    
    // Redondea un valor monetario a 2 decimales utilizando el modo HALF_UP.
    public static BigDecimal redondear(BigDecimal valor) {
        if (valor == null) return BigDecimal.ZERO;
        return valor.setScale(DECIMALES, MODO_REDONDEO);
    }
    
    // Suma dos valores monetarios con redondeo.
    public static BigDecimal sumar(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return redondear(a.add(b));
    }
    
    // Resta dos valores monetarios con redondeo.
    public static BigDecimal restar(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return redondear(a.subtract(b));
    }
    
    // Multiplica un valor monetario por una cantidad entera con redondeo.
     public static BigDecimal multiplicar(BigDecimal precio, int cantidad) {
        if (precio == null) return BigDecimal.ZERO;
        return redondear(precio.multiply(BigDecimal.valueOf(cantidad)));
    }
    
    // Multiplica dos valores monetarios con redondeo.
    public static BigDecimal multiplicar(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return redondear(a.multiply(b));
    }
    
    // Divide un valor monetario entre otro con redondeo. Lanza ArithmeticException si el divisor es cero.
    public static BigDecimal dividir(BigDecimal dividendo, BigDecimal divisor) {
        if (dividendo == null) dividendo = BigDecimal.ZERO;
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("División por cero");
        }
        return dividendo.divide(divisor, DECIMALES, MODO_REDONDEO);
    }
    
    // Calcula el porcentaje de un valor monetario.
    public static BigDecimal calcularPorcentaje(BigDecimal valor, BigDecimal porcentaje) {
        if (valor == null) return BigDecimal.ZERO;
        if (porcentaje == null) return BigDecimal.ZERO;
        
        return redondear(
            valor.multiply(porcentaje).divide(BigDecimal.valueOf(100), DECIMALES, MODO_REDONDEO)
        );
    }
    
    // Aplica un descuento a un valor monetario.
    public static BigDecimal aplicarDescuento(BigDecimal valor, BigDecimal porcentajeDescuento) {
        BigDecimal descuento = calcularPorcentaje(valor, porcentajeDescuento);
        return restar(valor, descuento);
    }
    
    // Calcula la propina basada en un porcentaje del total.
    public static BigDecimal calcularPropina(BigDecimal total, BigDecimal porcentajePropina) {
        return calcularPorcentaje(total, porcentajePropina);
    }
    
    // Suma una lista de valores monetarios con redondeo.
    public static BigDecimal sumarTodos(BigDecimal... valores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            if (valor != null) {
                total = total.add(valor);
            }
        }
        return redondear(total);
    }
    
    // Verifica si un valor es cero.
    public static boolean esCero(BigDecimal valor) {
        if (valor == null) return true;
        return valor.compareTo(BigDecimal.ZERO) == 0;
    }
    
    // Verifica si un valor es positivo (mayor a cero).
    public static boolean esPositivo(BigDecimal valor) {
        if (valor == null) return false;
        return valor.compareTo(BigDecimal.ZERO) > 0;
    }
    
    // Verifica si un valor es negativo (menor a cero).
    public static boolean esNegativo(BigDecimal valor) {
        if (valor == null) return false;
        return valor.compareTo(BigDecimal.ZERO) < 0;
    }
    
    // Compara dos valores monetarios para verificar si son iguales (considerando redondeo a 2 decimales).
    public static boolean sonIguales(BigDecimal valor1, BigDecimal valor2) {
        if (valor1 == null && valor2 == null) return true;
        if (valor1 == null || valor2 == null) return false;
        return valor1.compareTo(valor2) == 0;
    }
    
    // Formatea un valor monetario a string con 2 decimales.
    public static String formatear(BigDecimal valor) {
        if (valor == null) return "0.00";
        return String.format(java.util.Locale.US, "%.2f", valor);
    }
    
    // Formatea un valor monetario a string con un símbolo de moneda (por ejemplo, "$") y 2 decimales.
    public static String formatearConSimbolo(BigDecimal valor, String simbolo) {
        return simbolo + formatear(valor);
    }
}
