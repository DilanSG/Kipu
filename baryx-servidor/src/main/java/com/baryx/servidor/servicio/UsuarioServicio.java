/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.LoginPinDto;
import com.baryx.common.dto.UsuarioDto;
import com.baryx.common.dto.VerificarCodigoDto;

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
