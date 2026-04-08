/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/*DTO para transferir información de productos.
 * Campos:
 * - idProducto: Identificador único del producto
 * - codigo: Código interno del producto (ej: "COKE500")
 * - nombre: Nombre del producto (ej: "Coca-Cola 500ml")
 * - descripcion: Descripción opcional del producto
 * - precio: Precio unitario del producto
 * - idCategoria: Identificador de la categoría a la que pertenece el producto
 * - nombreCategoria: Nombre de la categoría (para facilitar visualización)
 * - stockActual: Cantidad actual en stock (si requiereStock = true)
 * - stockMinimo: Cantidad mínima para alertar sobre bajo stock
 * - requiereStock: Indica si el producto requiere control de stock
 * - activo: Si el producto está activo o deshabilitado */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoDto {
    private Long idProducto;
    private String codigo;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Long idCategoria;
    private String nombreCategoria;
    private Integer stockActual;
    private Integer stockMinimo;
    private Boolean requiereStock;
    private Boolean activo;
}
