/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.controlador;

import com.kipu.common.dto.RegistrarVentaDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.common.dto.VentaDto;
import com.kipu.servidor.servicio.VentaServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Controlador REST para registro de ventas (facturación). */
@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaServicio ventaServicio;

    @PostMapping
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<VentaDto>> registrarVenta(
            @Valid @RequestBody RegistrarVentaDto dto,
            @RequestParam Long idCajero,
            @RequestParam String nombreCajero) {

        VentaDto venta = ventaServicio.registrarVenta(dto, idCajero, nombreCajero);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespuestaApi.exitosa(venta, "Venta registrada exitosamente"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<List<VentaDto>>> listarVentas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde) {

        List<VentaDto> ventas = ventaServicio.listarVentasDesde(desde);
        return ResponseEntity.ok(RespuestaApi.exitosa(ventas,
                String.format("%d ventas encontradas", ventas.size())));
    }
}
