/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.utilidad;

import com.baryx.common.constantes.Constantes;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/* Clase de utilidad para manejo de fechas y horas en la aplicación.
 Proporciona métodos para formatear fechas, obtener la fecha actual, comparar fechas, etc.
 Utiliza los formatos definidos en Constantes.Formatos para mantener consistencia en toda la aplicación.
 */
public final class FechaUtil {
    
    // Constructor privado para evitar instanciación, ya que esta clase solo contiene métodos estáticos y no tiene estado.
    private FechaUtil() {
        throw new UnsupportedOperationException("Clase de utilidad no instanciable");
    }
    // Formatea una fecha y hora completa: dd/MM/yyyy HH:mm:ss
    // Ejemplo: "10/01/2026 14:30:45"
    public static String formatearFechaHora(LocalDateTime fecha) {
        if (fecha == null) return "";
        return fecha.format(Constantes.Formatos.FECHA_HORA);
    }
    
    // Formatea solo la fecha: dd/MM/yyyy
    // Ejemplo: "10/01/2026"
    public static String formatearFecha(LocalDateTime fecha) {
        if (fecha == null) return "";
        return fecha.format(Constantes.Formatos.FECHA);
    }
    
    // Sobrecarga para formatear solo la fecha a partir de un LocalDate, ignorando la hora.
    public static String formatearFecha(LocalDate fecha) {
        if (fecha == null) return "";
        return fecha.format(Constantes.Formatos.FECHA);
    }
    
    // Formatea solo la hora: HH:mm:ss
    // Ejemplo: "14:30:45"
    public static String formatearHora(LocalDateTime fecha) {
        if (fecha == null) return "";
        return fecha.format(Constantes.Formatos.HORA);
    }
    
    // Formatea fecha en formato completo para reportes o logs: EEEE, dd 'de' MMMM 'de' yyyy HH:mm:ss
    // Ejemplo: "Viernes, 10 de Enero de 2026 14:30:45"
    public static String formatearFechaCompleta(LocalDateTime fecha) {
        if (fecha == null) return "";
        return fecha.format(Constantes.Formatos.FECHA_COMPLETA);
    }
    
    // Formatea fecha en formato timestamp para almacenamiento o comparación: yyyy-MM-dd HH:mm:ss
    // Ejemplo: "2026-01-10 14:30:45"
    public static String formatearTimestamp(LocalDateTime fecha) {
        if (fecha == null) return "";
        return fecha.format(Constantes.Formatos.TIMESTAMP);
    }
    
    // Obtiene la fecha y hora actual del sistema.
    public static LocalDateTime ahora() {
        return LocalDateTime.now();
    }

    // Obtiene solo la fecha actual del sistema, sin la hora.
    public static LocalDate hoy() {
        return LocalDate.now();
    }

    // Verifica si una fecha y hora es hoy.
    public static boolean esHoy(LocalDateTime fecha) {
        if (fecha == null) return false;
        return fecha.toLocalDate().equals(LocalDate.now());
    }
    
    // Sobrecarga para verificar si una fecha (sin hora) es hoy.
    public static boolean esHoy(LocalDate fecha) {
        if (fecha == null) return false;
        return fecha.equals(LocalDate.now());
    }
    
    // Verifica si una fecha es anterior a otra.
    public static boolean esAnterior(LocalDateTime fecha1, LocalDateTime fecha2) {
        if (fecha1 == null || fecha2 == null) return false;
        return fecha1.isBefore(fecha2);
    }
    
    // Verifica si una fecha es posterior a otra.
    public static boolean esPosterior(LocalDateTime fecha1, LocalDateTime fecha2) {
        if (fecha1 == null || fecha2 == null) return false;
        return fecha1.isAfter(fecha2);
    }
    
    // Calcula la diferencia en minutos entre dos fechas.
    public static long diferenciaEnMinutos(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        if (fechaInicio == null || fechaFin == null) return 0;
        return java.time.Duration.between(fechaInicio, fechaFin).toMinutes();
    }
    
    // Verifica si una fecha está dentro de un rango (inclusive).
    public static boolean estaEnRango(LocalDateTime fecha, LocalDateTime inicio, LocalDateTime fin) {
        if (fecha == null || inicio == null || fin == null) return false;
        return !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }
    
    // Obtiene el inicio del día para una fecha dada (00:00:00).
    public static LocalDateTime inicioDia(LocalDate fecha) {
        if (fecha == null) return null;
        return fecha.atStartOfDay();
    }
    
    // Obtiene el inicio del día de hoy (00:00:00).
    public static LocalDateTime inicioDiaHoy() {
        return LocalDate.now().atStartOfDay();
    }
    
    // Obtiene el final del día para una fecha dada (23:59:59).
    public static LocalDateTime finDia(LocalDate fecha) {
        if (fecha == null) return null;
        return fecha.atTime(23, 59, 59);
    }
    
    // Obtiene el final del día de hoy (23:59:59).
    public static LocalDateTime finDiaHoy() {
        return LocalDate.now().atTime(23, 59, 59);
    }
}
