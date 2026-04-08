/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.servicio;

import com.kipu.common.dto.RegistrarVentaDto;
import com.kipu.common.dto.VentaDto;

import java.time.LocalDateTime;
import java.util.List;

/** Servicio para registro y consulta de ventas. */
public interface VentaServicio {

    /**
     * Registra una venta: crea la venta con pagos y líneas, marca el pedido como FACTURADO
     * y elimina la mesa (hard delete, igual que anularMesa).
     */
    VentaDto registrarVenta(RegistrarVentaDto dto, Long idCajero, String nombreCajero);

    /** Lista ventas creadas desde la fecha indicada (para sesión de caja). */
    List<VentaDto> listarVentasDesde(LocalDateTime desde);
}
