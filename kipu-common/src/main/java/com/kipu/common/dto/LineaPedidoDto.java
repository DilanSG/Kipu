/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*DTO que representa una línea de pedido.
 *Campos:
 *-idProducto: Identificador del producto
 *-nombreProducto: Nombre del producto
 *-precioUnitario: Precio unitario del producto
 *-timestamp: Fecha y hora de creación de la línea de pedido */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineaPedidoDto {
    private Long idProducto;
    private String nombreProducto;
    private BigDecimal precioUnitario;
    private LocalDateTime timestamp;
}
