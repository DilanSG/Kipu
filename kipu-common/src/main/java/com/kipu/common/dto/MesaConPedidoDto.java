/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*DTO que representa una mesa junto con su pedido actual (si existe).
 * Campos:
 * - idMesa: Identificador de la mesa
 * - numeroMesa: Número o nombre de la mesa
 * - estado: Estado actual de la mesa (ej: "ABIERTA", "CERRADA")
 * - idMesero: ID del mesero asignado a la mesa (temporal, para cuando la mesa no existe en BD aún)
 * - mesero: Información del mesero asignado (opcional, puede ser null si no se ha asignado)
 * - pedido: Información del pedido actual asociado a la mesa (puede ser null si no hay pedido) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesaConPedidoDto {
    private Long idMesa;
    private String numeroMesa;
    private String estado;
    private Long idMesero;  // Temporal: para cuando mesa no existe en BD aún
    private MeseroDto mesero;
    private PedidoDto pedido;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeseroDto {
        private Long id;
        private String nombre;
    }
}
