/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.common.excepcion.ValidacionException;
import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.LoginDto;
import com.baryx.servidor.modelo.entidad.Usuario;
import com.baryx.servidor.repositorio.UsuarioRepositorio;
import com.baryx.servidor.seguridad.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Servicio de autenticación de usuarios del sistema.
 * Responsabilidades:
 * - Validar credenciales de usuarios contra la base de datos
 * - Generar tokens JWT para sesiones autenticadas
 * - Controlar intentos fallidos de login y bloquear usuarios sospechosos
 * - Gestionar el ciclo de vida de las sesiones de usuario
 * Seguridad implementada:
 * - Las contraseñas se validan con BCrypt (hash seguro, nunca en texto plano)
 * - Después de 3 intentos fallidos consecutivos, el usuario se bloquea automáticamente
 * - Los tokens JWT expiran en 24 horas (configurable en application.yml)
 * - Solo usuarios activos (activo=true) pueden iniciar sesión
 * Flujo de autenticación:
 * 1. Cliente envía POST /api/usuarios/login con nombreUsuario y contrasena
 * 2. Este servicio valida las credenciales
 * 3. Si es exitoso, genera un token JWT
 * 4. Cliente almacena el token y lo envía en cada petición posterior
 * 5. JwtFiltroAutenticacion valida el token en cada request */
@Service
@Transactional // Todas las operaciones se ejecutan en una transacción de base de datos
@RequiredArgsConstructor
public class AutenticacionServicioImpl implements AutenticacionServicio {

    private static final Logger logger = LoggerFactory.getLogger(AutenticacionServicioImpl.class);
    private static final int MAX_INTENTOS_FALLIDOS = 3;

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.baryx.servidor.mapeo.UsuarioMapper usuarioMapper;

    /* Autentica un usuario y genera un token de sesión JWT.
     * Este es el método principal de autenticación del sistema.
     * Valida las credenciales del usuario y, si son correctas, genera el token que el cliente usará para autenticar todas las peticiones posteriores.
     * Proceso de autenticación:
     * 1. Busca el usuario en la base de datos por nombre de usuario
     * 2. Verifica que el usuario esté activo (activo=true, no eliminado lógicamente)
     * 3. Valida que no esté bloqueado por intentos fallidos previos
     * 4. Compara la contraseña ingresada con el hash BCrypt almacenado
     * 5. Si es exitoso: genera JWT, resetea contador de intentos fallidos, retorna datos
     * 6. Si falla: incrementa contador, bloquea usuario si se pasa de intentos.
     * Medidas de seguridad:
     * - BCrypt compara hashes, nunca contraseñas en texto plano
     * - Bloqueo automático después de 3 intentos fallidos
     * - Log de todos los intentos de login (exitosos y fallidos)
     * - Usuarios inactivos no pueden autenticarse
     *  @param loginDto Objeto con nombreUsuario y contrasena del usuario
     * @return AuthRespuestaDto con el token JWT y datos del usuario (id, nombre, rol)
     * @throws RecursoNoEncontradoException si el usuario no existe o está inactivo
     * @throws ValidacionException si las credenciales son inválidas o el usuario está bloqueado */
    public AuthRespuestaDto login(LoginDto loginDto) {
        logger.info("Intento de login para usuario: {}", loginDto.getNombreUsuario());

        if (loginDto.getNombreUsuario() == null || loginDto.getNombreUsuario().isBlank()) {
            throw new ValidacionException(Constantes.Mensajes.CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = usuarioRepositorio.findByNombreUsuarioAndActivoTrue(loginDto.getNombreUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException(Constantes.Mensajes.USUARIO_NO_ENCONTRADO_O_INACTIVO));
        
        if (usuario.getBloqueado()) {
            logger.warn("Intento de login de usuario bloqueado: {}", loginDto.getNombreUsuario());
            throw new ValidacionException(Constantes.Mensajes.USUARIO_BLOQUEADO_MSG);
        }
        if (!passwordEncoder.matches(loginDto.getContrasena(), usuario.getContrasena())) {
            manejarLoginFallido(usuario);
            throw new ValidacionException(Constantes.Mensajes.CREDENCIALES_INVALIDAS);
        }

        if (usuario.getIntentosFallidos() > 0) {
            usuario.setIntentosFallidos(0);
            usuarioRepositorio.save(usuario);
        }

        String token = jwtUtil.generarToken(usuario.getNombreUsuario(), usuario.getRol().name());

        logger.info("Login exitoso para usuario: {}", loginDto.getNombreUsuario());

        return usuarioMapper.aAuthRespuestaDto(usuario, token);
    }

    /*Maneja los intentos fallidos de login y aplica medidas de seguridad. 
     * Cuando un usuario ingresa credenciales incorrectas, este método:
     * 1. Incrementa el contador de intentos fallidos del usuario
     * 2. Si alcanza los intentos fallidos máximos, bloquea el usuario automáticamente
     * 3. Guarda el estado actualizado en la base de datos
     * 4. Registra el evento en los logs para auditoría
     * El bloqueo es una medida de seguridad contra ataques de fuerza bruta.
     * Un administrador debe desbloquear manualmente al usuario estableciendo
     * bloqueado=false e intentosFallidos=0.
     * @param usuario El usuario que intentó autenticarse con credenciales incorrectas */
    private void manejarLoginFallido(Usuario usuario) {
        usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);

        if (usuario.getIntentosFallidos() >= MAX_INTENTOS_FALLIDOS) {
            usuario.setBloqueado(true);
            logger.warn("Usuario bloqueado por múltiples intentos fallidos: {}", usuario.getNombreUsuario());
        }

        usuarioRepositorio.save(usuario);
        logger.warn("Intento de login fallido para usuario: {}. Intentos: {}", usuario.getNombreUsuario(), usuario.getIntentosFallidos());
    }
}
