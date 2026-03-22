/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.mapeo;

import com.baryx.common.dto.CategoriaDto;
import com.baryx.servidor.modelo.entidad.Categoria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

// Mapeador para la entidad Categoria y su DTO.
@Mapper(componentModel = "spring")
public interface CategoriaMapper {

    // Convierte una entidad Categoria a CategoriaDto, mapeando también el nombre de la categoría.
    CategoriaDto aDto(Categoria categoria);

    // Convierte un CategoriaDto a la entidad Categoria, ignorando campos de auditoría para evitar sobreescritura accidental.
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    Categoria aEntidad(CategoriaDto dto);

    // Convierte una lista de entidades a una lista de DTOs.
    List<CategoriaDto> aListaDto(List<Categoria> categorias);
}
