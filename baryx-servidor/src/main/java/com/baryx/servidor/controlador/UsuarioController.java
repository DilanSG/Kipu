/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.controlador;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.LoginPinDto;
import com.baryx.common.dto.UsuarioDto;
import com.baryx.common.dto.VerificarCodigoDto;
import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.RespuestaApi;
import com.baryx.servidor.seguridad.JwtUtil;
import com.baryx.servidor.servicio.UsuarioServicio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    
    private static final Logger logger = LoggerFactory.getLogger(UsuarioController.class);
    
    private final UsuarioServicio usuarioServicio;
    
    @GetMapping("/verificar-codigo/{codigo}")
    public ResponseEntity<VerificarCodigoDto> verificarCodigo(@PathVariable String codigo) {
        logger.info("Petición de verificación de código: {}", codigo);
        
        VerificarCodigoDto resultado = usuarioServicio.verificarCodigo(codigo);
        
        if (resultado.isExiste() && resultado.isActivo()) {
            return ResponseEntity.ok(resultado);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resultado);
        }
    }
    
    @PostMapping("/login-pin")
    public ResponseEntity<AuthRespuestaDto> loginConPin(@RequestBody LoginPinDto loginDto) {
        logger.info("Intento de login con código: {}", loginDto.getCodigo());
        
        try {
            AuthRespuestaDto respuesta = usuarioServicio.loginConPin(loginDto);
            logger.info("Login exitoso para código: {}", loginDto.getCodigo());
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            logger.error("Error en login con PIN: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CAJERO')")
    public ResponseEntity<RespuestaApi<List<UsuarioDto>>> listarUsuarios() {
        logger.info("Listando todos los usuarios");
        
        List<UsuarioDto> usuarios = usuarioServicio.listarUsuarios();
        
        return ResponseEntity.ok(
            RespuestaApi.<List<UsuarioDto>>builder()
                .exito(true)
                .datos(usuarios)
                .mensaje(Constantes.Mensajes.USUARIOS_OBTENIDOS)
                .build()
        );
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<UsuarioDto>> obtenerUsuario(@PathVariable Long id) {
        logger.info("Obteniendo usuario ID: {}", id);
        
        UsuarioDto usuario = usuarioServicio.obtenerUsuarioPorId(id);
        
        return ResponseEntity.ok(
            RespuestaApi.<UsuarioDto>builder()
                .exito(true)
                .datos(usuario)
                .mensaje(Constantes.Mensajes.USUARIO_ENCONTRADO)
                .build()
        );
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<UsuarioDto>> crearUsuario(@RequestBody UsuarioDto usuarioDto) {
        logger.info("Creando nuevo usuario: {}", usuarioDto.getNombreCompleto());
        
        UsuarioDto usuarioCreado = usuarioServicio.crearUsuario(usuarioDto);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RespuestaApi.<UsuarioDto>builder()
                    .exito(true)
                    .datos(usuarioCreado)
                    .mensaje(Constantes.Mensajes.USUARIO_CREADO)
                    .build()
                );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<UsuarioDto>> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody UsuarioDto usuarioDto) {
        logger.info("Actualizando usuario ID: {}", id);
        
        UsuarioDto usuarioActualizado = usuarioServicio.actualizarUsuario(id, usuarioDto);
        
        return ResponseEntity.ok(
            RespuestaApi.<UsuarioDto>builder()
                .exito(true)
                .datos(usuarioActualizado)
                .mensaje(Constantes.Mensajes.USUARIO_ACTUALIZADO)
                .build()
        );
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Void>> eliminarUsuario(@PathVariable Long id) {
        logger.info("Eliminando usuario ID: {}", id);
        
        usuarioServicio.eliminarUsuario(id);
        
        return ResponseEntity.ok(
            RespuestaApi.<Void>builder()
                .exito(true)
                .mensaje(Constantes.Mensajes.USUARIO_ELIMINADO)
                .build()
        );
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAJERO')")
    public ResponseEntity<RespuestaApi<List<UsuarioDto>>> buscarUsuarios(
            @RequestParam String termino) {
        logger.info("Buscando usuarios con término: {}", termino);
        
        List<UsuarioDto> usuarios = usuarioServicio.buscarUsuarios(termino);
        
        return ResponseEntity.ok(
            RespuestaApi.<List<UsuarioDto>>builder()
                .exito(true)
                .datos(usuarios)
                .mensaje(Constantes.Mensajes.BUSQUEDA_COMPLETADA)
                .build()
        );
    }
    
    @GetMapping("/por-rol/{rol}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAJERO')")
    public ResponseEntity<RespuestaApi<List<UsuarioDto>>> listarPorRol(@PathVariable String rol) {
        logger.info("Listando usuarios por rol: {}", rol);
        
        List<UsuarioDto> usuarios = usuarioServicio.listarPorRol(rol);
        
        return ResponseEntity.ok(
            RespuestaApi.<List<UsuarioDto>>builder()
                .exito(true)
                .datos(usuarios)
                .mensaje(Constantes.Mensajes.USUARIOS_OBTENIDOS)
                .build()
        );
    }
}
