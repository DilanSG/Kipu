/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.mapeo;

import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.UsuarioDto;
import com.kipu.servidor.modelo.entidad.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

/*Mapeador para la entidad Usuario y su DTO.
 * Utiliza MapStruct que lo que hace es generar automáticamente el código de mapeo en tiempo de compilación, lo que mejora el rendimiento y reduce errores.
 * Se definen métodos para convertir entre Usuario y UsuarioDto, así como para convertir listas de ambos tipos.
 * Además, se incluye un método específico para convertir un Usuario a AuthRespuestaDto, que es el DTO utilizado para la respuesta de autenticación, incluyendo el token generado.
 * Se ignoran campos sensibles como contrasena y pin al convertir a DTO, y campos de auditoría y técnicos al convertir a entidad, para evitar sobreescritura accidental. */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    // Convierte una entidad Usuario a UsuarioDto.
    UsuarioDto aDto(Usuario usuario);

    // Convierte un UsuarioDto a la entidad Usuario, ignorando campos sensibles y de auditoría.
    @Mapping(target = "contrasena", ignore = true)
    @Mapping(target = "pin", ignore = true)
    @Mapping(target = "bloqueado", ignore = true)
    @Mapping(target = "intentosFallidos", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    Usuario aEntidad(UsuarioDto dto);

    // Convierte una lista de entidades a una lista de DTOs.
    List<UsuarioDto> aListaDto(List<Usuario> usuarios);

    // Convierte un Usuario a AuthRespuestaDto, incluyendo el token generado.
    @Mapping(target = "token", source = "token")
    @Mapping(target = "nombreUsuario", source = "usuario.nombreUsuario")
    AuthRespuestaDto aAuthRespuestaDto(Usuario usuario, String token);
}
