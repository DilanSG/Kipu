/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.common.excepcion.ValidacionException;
import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.LoginPinDto;
import com.baryx.common.dto.UsuarioDto;
import com.baryx.common.dto.VerificarCodigoDto;
import com.baryx.servidor.modelo.entidad.Usuario;
import com.baryx.common.enums.Genero;
import com.baryx.common.enums.Rol;
import com.baryx.servidor.repositorio.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

// Servicio de gestión de usuarios. Maneja todas las operaciones CRUD y autenticación de usuarios.

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioServicioImpl implements UsuarioServicio {
    
    private static final Logger logger = LoggerFactory.getLogger(UsuarioServicioImpl.class);
    
    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final com.baryx.servidor.mapeo.UsuarioMapper usuarioMapper;
    private final com.baryx.servidor.seguridad.JwtUtil jwtUtil;
    
    @Override
    public VerificarCodigoDto verificarCodigo(String codigo) {
        logger.info("Verificando código: {}", codigo);
        
        Usuario usuario = usuarioRepositorio.findByCodigoAndActivoTrue(codigo)
                .orElse(null);
        
        if (usuario == null) {
            return VerificarCodigoDto.builder()
                    .existe(false)
                    .nombreCompleto(null)
                    .activo(false)
                    .build();
        }
        
        return VerificarCodigoDto.builder()
                .existe(true)
                .nombreCompleto(usuario.getNombreCompleto())
                .activo(usuario.getActivo())
                .build();
    }
    
    @Override
    public AuthRespuestaDto loginConPin(LoginPinDto loginDto) {
        logger.info("Intento de login con código: {}", loginDto.getCodigo());
        
        Usuario usuario = usuarioRepositorio.findByCodigoAndActivoTrue(loginDto.getCodigo())
                .orElseThrow(() -> new ValidacionException(Constantes.Mensajes.CREDENCIALES_PIN_INVALIDAS));
        
        if (!passwordEncoder.matches(loginDto.getPin(), usuario.getPin())) {
            logger.warn("PIN incorrecto para código: {}", loginDto.getCodigo());
            throw new ValidacionException(Constantes.Mensajes.CREDENCIALES_PIN_INVALIDAS);
        }
        
        String token = jwtUtil.generarToken(
            usuario.getCodigo(),
            usuario.getRol().name()
        );
        
        logger.info("Login exitoso para código: {} - Usuario: {}", loginDto.getCodigo(), usuario.getNombreCompleto());
        
        return AuthRespuestaDto.builder()
                .token(token)
                .nombreUsuario(usuario.getNombreUsuario())
                .nombreCompleto(usuario.getNombreCompleto())
                .rol(usuario.getRol().name())
                .genero(usuario.getGenero().name())
                .idUsuario(usuario.getIdUsuario())
                .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDto> listarUsuarios() {
        logger.info("Listando todos los usuarios");
        
        return usuarioMapper.aListaDto(usuarioRepositorio.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDto obtenerUsuarioPorId(Long id) {
        logger.info("Obteniendo usuario por ID: {}", id);
        
        Usuario usuario = usuarioRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Usuario", id));
        
        return usuarioMapper.aDto(usuario);
    }
    
    @Override
    public UsuarioDto crearUsuario(UsuarioDto usuarioDto) {
        logger.info("Creando nuevo usuario: {}", usuarioDto.getNombreCompleto());
        
        validarUsuario(usuarioDto, true);
        
        Usuario usuario = usuarioMapper.aEntidad(usuarioDto);
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos(0);
        usuario.setActivo(usuarioDto.getActivo() != null ? usuarioDto.getActivo() : true);
        
        Rol rol = Rol.valueOf(usuarioDto.getRol());
        
        if (rol == Rol.ADMIN) {
            if (usuarioRepositorio.existsByNombreUsuario(usuarioDto.getNombreUsuario())) {
                throw new ValidacionException(Constantes.Mensajes.NOMBRE_USUARIO_EXISTS);
            }
            
            usuario.setContrasena(passwordEncoder.encode(usuarioDto.getContrasena()));
            
        } else {
             if (usuarioRepositorio.existsByCodigo(usuarioDto.getCodigo())) {
                throw new ValidacionException(Constantes.Mensajes.CODIGO_EMPLEADO_EXISTS);
            }
            
            usuario.setPin(passwordEncoder.encode(usuarioDto.getPin()));
        }
        
        usuario = usuarioRepositorio.save(usuario);
        logger.info("Usuario creado exitosamente con ID: {}", usuario.getIdUsuario());
        
        return usuarioMapper.aDto(usuario);
    }
    
    @Override
    public UsuarioDto actualizarUsuario(Long id, UsuarioDto usuarioDto) {
        logger.info("Actualizando usuario ID: {}", id);
        
        Usuario usuario = usuarioRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Usuario", id));
        
        validarUsuario(usuarioDto, false);
        
        usuario.setNombreCompleto(usuarioDto.getNombreCompleto());
        usuario.setGenero(Genero.valueOf(usuarioDto.getGenero()));
        usuario.setActivo(usuarioDto.getActivo());
        usuario.setRol(Rol.valueOf(usuarioDto.getRol()));
        
        Rol rol = Rol.valueOf(usuarioDto.getRol());
        
        if (rol == Rol.ADMIN) {

            if (usuario.getNombreUsuario() == null || !usuario.getNombreUsuario().equals(usuarioDto.getNombreUsuario())) {
                if (usuarioRepositorio.existsByNombreUsuario(usuarioDto.getNombreUsuario())) {
                    throw new ValidacionException(Constantes.Mensajes.NOMBRE_USUARIO_EXISTS);
                }
                usuario.setNombreUsuario(usuarioDto.getNombreUsuario());
            }
            
            if (usuarioDto.getContrasena() != null && !usuarioDto.getContrasena().isEmpty()) {
                usuario.setContrasena(passwordEncoder.encode(usuarioDto.getContrasena()));
            }
            
        } else {
            if (usuario.getCodigo() == null || !usuario.getCodigo().equals(usuarioDto.getCodigo())) {
                if (usuarioRepositorio.existsByCodigo(usuarioDto.getCodigo())) {
                    throw new ValidacionException(Constantes.Mensajes.CODIGO_EMPLEADO_EXISTS);
                }
                usuario.setCodigo(usuarioDto.getCodigo());
            }
            
            if (usuarioDto.getPin() != null && !usuarioDto.getPin().isEmpty()) {
                usuario.setPin(passwordEncoder.encode(usuarioDto.getPin()));
            }
        }
        
        usuario = usuarioRepositorio.save(usuario);
        logger.info("Usuario actualizado exitosamente: {}", id);
        
        return usuarioMapper.aDto(usuario);
    }
    
    @Override
    public void eliminarUsuario(Long id) {
        logger.info("Eliminando permanentemente usuario ID: {}", id);
        
        Usuario usuario = usuarioRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Usuario", id));
        
        usuarioRepositorio.delete(usuario);
        
        logger.info("Usuario eliminado permanentemente exitosamente: {}", id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDto> buscarUsuarios(String termino) {
        logger.info("Buscando usuarios con término: {}", termino);
        
        return usuarioMapper.aListaDto(usuarioRepositorio.buscarPorTermino(termino));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDto> listarPorRol(String rol) {
        logger.info("Listando usuarios con rol: {}", rol);
        
        try {
            Rol rolEnum = Rol.valueOf(rol.toUpperCase());
            return usuarioMapper.aListaDto(usuarioRepositorio.findByRolAndActivoTrue(rolEnum));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Rol no válido: " + rol + ". Valores permitidos: MESERO, CAJERO, ADMIN");
        }
    }
    
    private void validarUsuario(UsuarioDto usuarioDto, boolean esNuevo) {
        if (usuarioDto.getNombreCompleto() == null || usuarioDto.getNombreCompleto().trim().isEmpty()) {
            throw new ValidacionException(Constantes.Mensajes.NOMBRE_COMPLETO_REQUERIDO);
        }
        
        if (usuarioDto.getRol() == null) {
            throw new ValidacionException(Constantes.Mensajes.ROL_REQUERIDO);
        }
        
        if (usuarioDto.getGenero() == null) {
            throw new ValidacionException(Constantes.Mensajes.GENERO_REQUERIDO);
        }
        
        Rol rol;
        try {
            rol = Rol.valueOf(usuarioDto.getRol());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException(Constantes.Mensajes.ROL_INVALIDO + ": " + usuarioDto.getRol());
        }
        
        try {
            Genero.valueOf(usuarioDto.getGenero());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException(Constantes.Mensajes.GENERO_INVALIDO + ": " + usuarioDto.getGenero());
        }
        
        if (rol == Rol.ADMIN) {
            if (usuarioDto.getNombreUsuario() == null || usuarioDto.getNombreUsuario().trim().isEmpty()) {
                throw new ValidacionException(Constantes.Mensajes.NOMBRE_USUARIO_REQUERIDO);
            }
            
            if (esNuevo && (usuarioDto.getContrasena() == null || usuarioDto.getContrasena().isEmpty())) {
                throw new ValidacionException(Constantes.Mensajes.CONTRASENA_REQUERIDA);
            }
            
            if (usuarioDto.getContrasena() != null && !usuarioDto.getContrasena().isEmpty() 
                && usuarioDto.getContrasena().length() < 6) {
                throw new ValidacionException(Constantes.Mensajes.CONTRASENA_MIN_LENGTH);
            }
            
        } else {
            if (usuarioDto.getCodigo() == null || usuarioDto.getCodigo().trim().isEmpty()) {
                throw new ValidacionException(Constantes.Mensajes.CODIGO_EMPLEADO_REQUERIDO);
            }
            
            if (usuarioDto.getCodigo().length() != 2) {
                throw new ValidacionException(Constantes.Mensajes.CODIGO_LENGTH_INVALID);
            }
            
            try {
                int codigo = Integer.parseInt(usuarioDto.getCodigo());
                if (codigo < 1 || codigo > 99) {
                    throw new ValidacionException(Constantes.Mensajes.CODIGO_RANGE_INVALID);
                }
            } catch (NumberFormatException e) {
                throw new ValidacionException(Constantes.Mensajes.CODIGO_NUMERICO);
            }
            
            if (esNuevo && (usuarioDto.getPin() == null || usuarioDto.getPin().isEmpty())) {
                throw new ValidacionException(Constantes.Mensajes.PIN_REQUERIDO);
            }
            
            if (usuarioDto.getPin() != null && !usuarioDto.getPin().isEmpty() 
                && usuarioDto.getPin().length() != 4) {
                throw new ValidacionException(Constantes.Mensajes.PIN_LENGTH_INVALID);
            }
        }
    }
}
