package com.baryx.common.utilidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests de MonetarioUtil")
class MonetarioUtilTest {

    @Test
    @DisplayName("Debe redondear correctamente a 2 decimales")
    void debeRedondearCorrectamente() {
        assertEquals(new BigDecimal("10.13"), MonetarioUtil.redondear(new BigDecimal("10.125")));
        assertEquals(new BigDecimal("10.12"), MonetarioUtil.redondear(new BigDecimal("10.124")));
    }

    @Test
    @DisplayName("Debe sumar correctamente")
    void debeSumarCorrectamente() {
        BigDecimal a = new BigDecimal("10.50");
        BigDecimal b = new BigDecimal("5.25");
        assertEquals(new BigDecimal("15.75"), MonetarioUtil.sumar(a, b));
    }

    @Test
    @DisplayName("Debe calcular porcentaje correctamente")
    void debeCalcularPorcentaje() {
        BigDecimal total = new BigDecimal("100.00");
        BigDecimal porcentaje = new BigDecimal("10.00");
        assertEquals(new BigDecimal("10.00"), MonetarioUtil.calcularPorcentaje(total, porcentaje));
    }

    @Test
    @DisplayName("Debe formatear con símbolo correctamente")
    void debeFormatearConSimbolo() {
        BigDecimal valor = new BigDecimal("10.50");
        assertEquals("$10.50", MonetarioUtil.formatearConSimbolo(valor, "$"));
    }

    @Test
    @DisplayName("Debe detectar valores positivos, negativos y cero")
    void debeDetectarSignos() {
        assertTrue(MonetarioUtil.esPositivo(new BigDecimal("0.01")));
        assertTrue(MonetarioUtil.esNegativo(new BigDecimal("-0.01")));
        assertTrue(MonetarioUtil.esCero(new BigDecimal("0.00")));
    }
}
