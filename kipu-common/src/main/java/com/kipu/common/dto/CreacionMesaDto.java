/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/*DTO para crear una nueva mesa.
 *Campos:
 *-numeroMesa: Número o nombre de la mesa (ej: "Mesa 1", "Mesa VIP")
 *-idMesero: ID del mesero asignado a la mesa
 *Uso:
 *-El cliente envía POST /api/mesas con este DTO para crear una nueva mesa.
 *-El servidor valida los datos y crea la mesa en la base de datos.
 *-El servidor retorna el ID de la nueva mesa creada. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreacionMesaDto {
    private String numeroMesa;
    private Long idMesero;
}
