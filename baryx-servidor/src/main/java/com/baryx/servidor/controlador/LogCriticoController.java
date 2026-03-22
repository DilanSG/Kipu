/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.controlador;

import com.baryx.common.dto.LogCriticoDto;
import com.baryx.common.dto.RespuestaApi;
import com.baryx.servidor.servicio.LogCriticoServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para logs críticos del sistema.
 *
 * Endpoints:
 * - GET  /api/logs-criticos          → Listar todos (ADMIN)
 * - GET  /api/logs-criticos/nivel/{nivel} → Filtrar por nivel (ADMIN)
 * - GET  /api/logs-criticos/pendientes → Solo no resueltos (ADMIN)
 * - GET  /api/logs-criticos/conteo   → Conteo de pendientes (ADMIN)
 * - POST /api/logs-criticos          → Registrar nuevo (cualquier autenticado)
 * - PUT  /api/logs-criticos/{id}/resolver → Marcar resuelto (ADMIN)
 * - PUT  /api/logs-criticos/{id}/estado   → Cambiar estado (ADMIN)
 */
@RestController
@RequestMapping("/api/logs-criticos")
@RequiredArgsConstructor
public class LogCriticoController {

    private final LogCriticoServicio logCriticoServicio;

    /**
     * Lista todos los logs críticos, ordenados por fecha descendente.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<List<LogCriticoDto>>> listar() {
        List<LogCriticoDto> logs = logCriticoServicio.listarTodos();
        return ResponseEntity.ok(RespuestaApi.exitosa(logs, "Logs críticos obtenidos exitosamente"));
    }

    /**
     * Filtra logs por nivel de severidad (CRITICO, ERROR).
     */
    @GetMapping("/nivel/{nivel}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<List<LogCriticoDto>>> listarPorNivel(@PathVariable String nivel) {
        List<LogCriticoDto> logs = logCriticoServicio.listarPorNivel(nivel.toUpperCase());
        return ResponseEntity.ok(RespuestaApi.exitosa(logs, "Logs filtrados por nivel: " + nivel));
    }

    /**
     * Lista solo logs no resueltos.
     */
    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<List<LogCriticoDto>>> listarPendientes() {
        List<LogCriticoDto> logs = logCriticoServicio.listarNoResueltos();
        return ResponseEntity.ok(RespuestaApi.exitosa(logs, "Logs pendientes obtenidos"));
    }

    /**
     * Conteo de logs no resueltos (para badges de notificación).
     */
    @GetMapping("/conteo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Long>> contarPendientes() {
        long conteo = logCriticoServicio.contarNoResueltos();
        return ResponseEntity.ok(RespuestaApi.exitosa(conteo, "Conteo de logs pendientes"));
    }

    /**
     * Registra un nuevo log crítico.
     * Accesible por cualquier usuario autenticado (los clientes reportan errores automáticamente).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<LogCriticoDto>> registrar(@RequestBody LogCriticoDto dto) {
        LogCriticoDto registrado = logCriticoServicio.registrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespuestaApi.exitosa(registrado, "Log crítico registrado"));
    }

    /**
     * Marca un log como resuelto (solo ADMIN). Mantiene compatibilidad.
     */
    @PutMapping("/{id}/resolver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<LogCriticoDto>> marcarResuelto(@PathVariable Long id) {
        LogCriticoDto actualizado = logCriticoServicio.cambiarEstado(id, "RESUELTO");
        return ResponseEntity.ok(RespuestaApi.exitosa(actualizado, "Log marcado como resuelto"));
    }

    /**
     * Cambia el estado de un log (solo ADMIN).
     * Estados válidos: NOTIFICACION_ERROR, EN_REVISION, RESUELTO
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<LogCriticoDto>> cambiarEstado(
            @PathVariable Long id, @RequestParam String estado) {
        LogCriticoDto actualizado = logCriticoServicio.cambiarEstado(id, estado.toUpperCase());
        return ResponseEntity.ok(RespuestaApi.exitosa(actualizado, "Estado del log actualizado"));
    }
}
