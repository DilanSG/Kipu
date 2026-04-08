/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.servicio;

import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LoginPinDto;
import com.kipu.common.dto.UsuarioDto;
import com.kipu.common.dto.VerificarCodigoDto;

import java.util.List;

// Interfaz para el servicio de gestión de usuarios.
 
public interface UsuarioServicio {
    VerificarCodigoDto verificarCodigo(String codigo);
    AuthRespuestaDto loginConPin(LoginPinDto loginDto);
    List<UsuarioDto> listarUsuarios();
    UsuarioDto obtenerUsuarioPorId(Long id);
    UsuarioDto crearUsuario(UsuarioDto usuarioDto);
    UsuarioDto actualizarUsuario(Long id, UsuarioDto usuarioDto);
    void eliminarUsuario(Long id);
    List<UsuarioDto> buscarUsuarios(String termino);
    List<UsuarioDto> listarPorRol(String rol);
}
