/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*DTO simplificado para mostrar mesas activas.
 * Campos:
 * - idMesa: Identificador de la mesa
 * - numeroMesa: Número de la mesa
 * - meseroNombre: Nombre del mesero asignado
 * - meseroId: Identificador del mesero
 * - total: Total de la cuenta
 * - fechaCreacion: Fecha de creación de la mesa
 * - cantidadItems: Cantidad de items en la mesa */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesaActivaDto {
    private Long idMesa;
    private String numeroMesa;
    private String meseroNombre;
    private Long meseroId;
    private BigDecimal total;
    private LocalDateTime fechaCreacion;
    private Integer cantidadItems;
}
