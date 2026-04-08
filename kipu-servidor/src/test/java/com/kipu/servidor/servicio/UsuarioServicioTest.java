package com.kipu.servidor.servicio;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LoginPinDto;
import com.kipu.common.dto.UsuarioDto;
import com.kipu.common.dto.VerificarCodigoDto;
import com.kipu.common.enums.Genero;
import com.kipu.common.enums.Rol;
import com.kipu.common.excepcion.RecursoNoEncontradoException;
import com.kipu.common.excepcion.ValidacionException;
import com.kipu.servidor.mapeo.UsuarioMapper;
import com.kipu.servidor.modelo.entidad.Usuario;
import com.kipu.servidor.repositorio.UsuarioRepositorio;
import com.kipu.servidor.seguridad.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para UsuarioServicio
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests de UsuarioServicio")
class UsuarioServicioTest {

    @Mock
    private UsuarioRepositorio usuarioRepositorio;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioMapper usuarioMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UsuarioServicioImpl usuarioServicio;

    private UsuarioDto usuarioDto;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioDto = UsuarioDto.builder()
                .nombreUsuario("admin")
                .contrasena("password123")
                .nombreCompleto("Administrador")
                .rol(Rol.ADMIN.name())
                .genero(Genero.MASCULINO.name())
                .activo(true)
                .build();

        usuario = Usuario.builder()
                .idUsuario(1L)
                .nombreUsuario("admin")
                .contrasena("encodedPassword")
                .nombreCompleto("Administrador")
                .rol(Rol.ADMIN)
                .genero(Genero.MASCULINO)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("Debe verificar código de usuario activo")
    void debeVerificarCodigoCorrectamente() {
        // Given
        when(usuarioRepositorio.findByCodigoAndActivoTrue("01")).thenReturn(Optional.of(usuario));

        // When
        VerificarCodigoDto resultado = usuarioServicio.verificarCodigo("01");

        // Then
        assertTrue(resultado.isExiste());
        assertEquals("Administrador", resultado.getNombreCompleto());
    }

    @Test
    @DisplayName("Debe crear un usuario correctamente")
    void debeCrearUsuarioCorrectamente() {
        // Given
        when(usuarioMapper.aEntidad(usuarioDto)).thenReturn(usuario);
        when(usuarioRepositorio.existsByNombreUsuario(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(usuarioRepositorio.save(any(Usuario.class))).thenReturn(usuario);
        when(usuarioMapper.aDto(usuario)).thenReturn(usuarioDto);

        // When
        UsuarioDto resultado = usuarioServicio.crearUsuario(usuarioDto);

        // Then
        assertNotNull(resultado);
        verify(usuarioRepositorio).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe obtener usuario por ID correctamente")
    void debeObtenerUsuarioPorId() {
        // Given
        when(usuarioRepositorio.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioMapper.aDto(usuario)).thenReturn(usuarioDto);

        // When
        UsuarioDto resultado = usuarioServicio.obtenerUsuarioPorId(1L);

        // Then
        assertNotNull(resultado);
        verify(usuarioRepositorio).findById(1L);
    }

    @Test
    @DisplayName("Debe lanzar excepción si usuario no existe")
    void debeLanzarExcepcionSiUsuarioNoExiste() {
        // Given
        when(usuarioRepositorio.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RecursoNoEncontradoException.class, () -> usuarioServicio.obtenerUsuarioPorId(999L));
    }

    @Test
    @DisplayName("Debe fallar login con PIN si el usuario no existe")
    void debeFallarLoginSiUsuarioNoExiste() {
        // Given
        LoginPinDto loginDto = new LoginPinDto("99", "1234");
        when(usuarioRepositorio.findByCodigoAndActivoTrue("99")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ValidacionException.class, () -> usuarioServicio.loginConPin(loginDto));
    }
}
