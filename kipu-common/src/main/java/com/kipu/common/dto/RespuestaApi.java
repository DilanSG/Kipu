/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*DTO genérico para respuestas de API.
 * Campos:
 * - exito: Indica si la operación fue exitosa o no
 * - datos: Contiene los datos de la respuesta (puede ser cualquier tipo)
 * - mensaje: Mensaje informativo o de error
 * - error: Código o descripción del error (si exito es false)
 * - detalles: Información adicional sobre el error (opcional) */
@Data // Lombok: genera getters, setters, toString, equals, hashCode
@NoArgsConstructor // Lombok: genera constructor sin argumentos (necesario para Jackson)
@AllArgsConstructor // Lombok: genera constructor con todos los argumentos
@Builder // Lombok: permite crear objetos con patrón builder: RespuestaApi.builder().exito(true).build()
public class RespuestaApi<T> {
    
    private Boolean exito;
    private T datos;
    private String mensaje;
    private String error;
    private Object detalles;
    
    public RespuestaApi(Boolean exito, T datos, String mensaje) {
        this.exito = exito;
        this.datos = datos;
        this.mensaje = mensaje;
    }
    
    public static <T> RespuestaApi<T> exitosa(T datos, String mensaje) {
        return new RespuestaApi<>(true, datos, mensaje);
    }
    
    public static <T> RespuestaApi<T> error(String error, String mensaje) {
        RespuestaApi<T> respuesta = new RespuestaApi<>();
        respuesta.setExito(false);
        respuesta.setError(error);
        respuesta.setMensaje(mensaje);
        return respuesta;
    }
}
