package com.kipu.cliente.servicio;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LoginPinDto;
import com.kipu.common.dto.VerificarCodigoDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del servicio de autenticación del cliente.
 * 
 * Utiliza MockWebServer para simular las respuestas del servidor REST
 * y verifica que los métodos async del servicio funcionen correctamente.
 */
@DisplayName("Tests de AutenticacionServicio (Cliente)")
class AutenticacionServicioTest {

    private MockWebServer mockWebServer;
    private AutenticacionServicio autenticacionServicio;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        // Configuramos la URL base para que apunte al MockWebServer
        String baseUrl = mockWebServer.url("/").toString();
        ConfiguracionCliente.setUrlServidor(baseUrl.substring(0, baseUrl.length() - 1)); // Remove trailing slash
        
        autenticacionServicio = new AutenticacionServicio();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Debe realizar login con PIN exitosamente (async)")
    void debeLoginConPinExitosamente() throws Exception {
        // Given
        AuthRespuestaDto respuestaDto = AuthRespuestaDto.builder()
                .token("mock-token")
                .nombreUsuario("cajero")
                .rol("CAJERO")
                .build();
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(objectMapper.writeValueAsString(respuestaDto)));

        LoginPinDto loginPinDto = new LoginPinDto("01", "1234");

        // When - usar método async y esperar resultado
        AuthRespuestaDto resultado = autenticacionServicio.loginConPinAsync(loginPinDto).get();

        // Then
        assertNotNull(resultado);
        assertEquals("mock-token", resultado.getToken());
        assertEquals("mock-token", ConfiguracionCliente.getTokenJwt());
    }

    @Test
    @DisplayName("Debe verificar código de usuario (async)")
    void debeVerificarCodigo() throws Exception {
        // Given
        VerificarCodigoDto mockRespuesta = VerificarCodigoDto.builder()
                .existe(true)
                .nombreCompleto("Juan Perez")
                .activo(true)
                .build();
        
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(objectMapper.writeValueAsString(mockRespuesta)));

        // When - usar método async y esperar resultado
        VerificarCodigoDto resultado = autenticacionServicio.verificarCodigoAsync("01").get();

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isExiste());
        assertEquals("Juan Perez", resultado.getNombreCompleto());
    }

    @Test
    @DisplayName("Debe completar excepcionalmente si el código no existe (async)")
    void debeLanzarExcepcionSiCodigoNoExiste() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404));

        // When & Then - el CompletableFuture debe completar con excepción
        CompletableFuture<VerificarCodigoDto> futuro = autenticacionServicio.verificarCodigoAsync("99");
        assertThrows(ExecutionException.class, futuro::get);
    }
}
