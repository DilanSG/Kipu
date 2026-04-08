/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO que envía el cliente para registrar una venta al facturar una mesa.
 * Contiene toda la información necesaria para crear la venta: mesa, montos, pagos y líneas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrarVentaDto {
    private Long idMesa;
    private String numeroMesa;
    private Long idMesero;
    private String nombreMesero;
    private BigDecimal subtotal;
    private BigDecimal impoconsumo;
    private BigDecimal propina;
    private BigDecimal total;
    private List<PagoDto> pagos;
    private List<LineaVentaDto> lineas;
}
