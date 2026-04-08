/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.mapeo;

import com.kipu.common.dto.MetodoPagoDto;
import com.kipu.servidor.modelo.entidad.MetodoPago;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

// Mapeador para la entidad MetodoPago y su DTO.
@Mapper(componentModel = "spring")
public interface MetodoPagoMapper {

    // Convierte una entidad MetodoPago a MetodoPagoDto.
    MetodoPagoDto aDto(MetodoPago metodoPago);

    // Convierte un MetodoPagoDto a la entidad MetodoPago.
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    MetodoPago aEntidad(MetodoPagoDto dto);

    // Convierte una lista de entidades a una lista de DTOs.
    List<MetodoPagoDto> aListaDto(List<MetodoPago> metodosPago);
}
