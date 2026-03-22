/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*DTO para transferir información de categorías 
 * Campos:
 * - idCategoria: Identificador único
 * - nombre: Nombre de la categoría (ej: "Bebidas Alcohólicas")
 * - descripcion: Descripción opcional de la categoría
 * - orden: Orden de visualización (menor = primero)
 * - activo: Si la categoría está activa o deshabilitada */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaDto {
    private Long idCategoria;
    private String nombre;
    private String descripcion;
    private Integer orden;
    private Boolean activo;
    private String color;
}
