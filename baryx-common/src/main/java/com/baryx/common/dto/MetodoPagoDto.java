/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*DTO para transferir información de métodos de pago
 * Campos:
 * - idMetodoPago: Identificador único
 * - codigo: Código interno del método de pago (ej: "EFECTIVO", "TARJETA")
 * - nombre: Nombre del método de pago (ej: "Efectivo", "Tarjeta de Crédito")
 * - descripcion: Descripción opcional del método de pago
 * - orden: Orden de visualización (menor = primero)
 * - activo: Si el método de pago está activo o deshabilitado
 * - esPredeterminado: Si este método de pago es el predeterminado para nuevas mesas */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetodoPagoDto {
    private Long idMetodoPago;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer orden;
    private Boolean activo;
    private Boolean esPredeterminado;
}
