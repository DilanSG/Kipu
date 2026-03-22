/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.mapeo;

import com.baryx.common.dto.LogCriticoDto;
import com.baryx.common.enums.EstadoLog;
import com.baryx.servidor.modelo.entidad.LogCritico;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Mapper MapStruct para convertir entre {@link LogCritico} y {@link LogCriticoDto}.
 *
 * La fecha de creación se ignora en la conversión a entidad porque
 * se establece automáticamente vía {@code @PrePersist}.
 * El campo estado se convierte entre EstadoLog (enum) y String.
 */
@Mapper(componentModel = "spring")
public interface LogCriticoMapper {

    @Mapping(target = "estado", source = "estado", qualifiedByName = "estadoAString")
    LogCriticoDto aDto(LogCritico logCritico);

    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "estado", ignore = true)
    LogCritico aEntidad(LogCriticoDto dto);

    List<LogCriticoDto> aListaDto(List<LogCritico> logs);

    /** Convierte el enum EstadoLog a su representación String */
    @Named("estadoAString")
    default String estadoAString(EstadoLog estado) {
        return estado != null ? estado.name() : null;
    }
}
