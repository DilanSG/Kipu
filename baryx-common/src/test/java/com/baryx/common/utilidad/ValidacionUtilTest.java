package com.baryx.common.utilidad;

import com.baryx.common.excepcion.ValidacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests de ValidacionUtil")
class ValidacionUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"user123", "admin_1", "test_user"})
    @DisplayName("Debe validar nombres de usuario válidos")
    void debeValidarUsuariosValidos(String usuario) {
        assertTrue(ValidacionUtil.esUsuarioValido(usuario));
    }

    @ParameterizedTest
    @ValueSource(strings = {"us", "user!", "esta_es_una_cadena_demasiado_larga_para_ser_un_usuario_valido_de_sistema"})
    @DisplayName("Debe rechazar nombres de usuario inválidos")
    void debeRechazarUsuariosInvalidos(String usuario) {
        assertFalse(ValidacionUtil.esUsuarioValido(usuario));
    }

    @Test
    @DisplayName("Debe validar PIN de 4 dígitos")
    void debeValidarPin() {
        assertTrue(ValidacionUtil.esPinValido("1234"));
        assertFalse(ValidacionUtil.esPinValido("123"));
        assertFalse(ValidacionUtil.esPinValido("12345"));
        assertFalse(ValidacionUtil.esPinValido("abcd"));
    }

    @Test
    @DisplayName("Debe validar email")
    void debeValidarEmail() {
        assertTrue(ValidacionUtil.esEmailValido("test@example.com"));
        assertFalse(ValidacionUtil.esEmailValido("invalid-email"));
    }

    @Test
    @DisplayName("Debe validar precio mayor a cero")
    void debeValidarPrecio() {
        assertTrue(ValidacionUtil.esPrecioValido(new BigDecimal("10.00")));
        assertFalse(ValidacionUtil.esPrecioValido(new BigDecimal("0.00")));
        assertFalse(ValidacionUtil.esPrecioValido(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Debe lanzar excepción en validarRequerido si el valor es nulo")
    void debeLanzarExcepcionEnRequerido() {
        assertThrows(ValidacionException.class, () -> ValidacionUtil.validarRequerido((String)null, "Campo"));
        assertThrows(ValidacionException.class, () -> ValidacionUtil.validarRequerido("", "Campo"));
    }
}
