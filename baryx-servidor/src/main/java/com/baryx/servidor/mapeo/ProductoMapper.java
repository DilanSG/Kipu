/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.mapeo;

import com.baryx.common.dto.ProductoDto;
import com.baryx.servidor.modelo.entidad.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

// Mapeador para la entidad Producto y su DTO. 
@Mapper(componentModel = "spring")
public interface ProductoMapper {

    // Convierte una entidad Producto a ProductoDto, mapeando también los campos de la categoría.
    @Mapping(source = "categoria.idCategoria", target = "idCategoria")
    @Mapping(source = "categoria.nombre", target = "nombreCategoria")
    ProductoDto aDto(Producto producto);

    // Convierte un ProductoDto a la entidad Producto, ignorando el mapeo de la categoría y campos de auditoría para evitar sobreescritura accidental.
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    Producto aEntidad(ProductoDto dto);

    // Convierte una lista de entidades a una lista de DTOs.
    List<ProductoDto> aListaDto(List<Producto> productos);
}
