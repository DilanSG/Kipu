/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.controlador;

import com.kipu.common.dto.ConfiguracionSistemaDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.servidor.servicio.ConfiguracionSistemaServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para gestión de configuraciones globales del sistema.
 *
 * Endpoints disponibles:
 * - GET /api/sistema/configuracion/{clave}: Obtener configuración (cualquier rol autenticado)
 * - PUT /api/sistema/configuracion/{clave}: Actualizar configuración (solo ADMIN)
 *
 * Las configuraciones son pares clave-valor compartidos entre todos los clientes.
 * Ejemplo: idioma del sistema, moneda, formato de fecha.
 *
 * @see ConfiguracionSistemaServicio
 */
@RestController
@RequestMapping("/api/sistema/configuracion")
@RequiredArgsConstructor
public class ConfiguracionSistemaController {

    private final ConfiguracionSistemaServicio configuracionServicio;

    /**
     * Obtiene una configuración del sistema por su clave.
     * Accesible por cualquier rol autenticado.
     *
     * @param clave Clave de la configuración (ej: "idioma")
     * @return Configuración encontrada
     */
    @GetMapping("/{clave}")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<ConfiguracionSistemaDto>> obtenerPorClave(
            @PathVariable String clave) {
        ConfiguracionSistemaDto config = configuracionServicio.obtenerPorClave(clave);
        return ResponseEntity.ok(RespuestaApi.exitosa(config, "Configuración obtenida exitosamente"));
    }

    /**
     * Actualiza el valor de una configuración del sistema.
     * Solo accesible por ADMIN.
     *
     * @param clave Clave de la configuración a actualizar
     * @param dto DTO con el nuevo valor
     * @return Configuración actualizada
     */
    @PutMapping("/{clave}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<ConfiguracionSistemaDto>> actualizar(
            @PathVariable String clave,
            @RequestBody ConfiguracionSistemaDto dto) {
        ConfiguracionSistemaDto actualizada = configuracionServicio.actualizar(clave, dto.getValor());
        return ResponseEntity.ok(RespuestaApi.exitosa(actualizada, "Configuración actualizada exitosamente"));
    }
}
