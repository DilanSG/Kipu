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
import java.util.List;

/**
 * DTO para pedido completo de una mesa.
 * Campos:
 * - lineas: Lista de líneas de pedido
 * - total: Total del pedido
 * - fechaCreacion: Fecha de creación del pedido */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDto {
    private List<LineaPedidoDto> lineas;
    private BigDecimal total;
    private LocalDateTime fechaCreacion;
}
