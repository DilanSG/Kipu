/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.controlador;

import com.baryx.common.dto.RespuestaApi;
import com.baryx.servidor.servicio.SincronizacionNubeServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador REST para monitoreo de la sincronización con MongoDB Atlas.
 *
 * Endpoints:
 * - GET /api/sync/estado → Estado completo de la sincronización (solo ADMIN)
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncControlador {

    private final SincronizacionNubeServicio sincronizacionServicio;

    /**
     * Retorna el estado actual de la sincronización con la nube.
     * Incluye: habilitado, última sincronización, pendientes, errores, conectividad.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Map<String, Object>>> obtenerEstado() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("habilitado", sincronizacionServicio.estaHabilitado());
        estado.put("ultimaSincronizacion", sincronizacionServicio.getUltimaSincronizacionExitosa());
        estado.put("registrosPendientes", sincronizacionServicio.contarPendientes());
        estado.put("registrosConError", sincronizacionServicio.contarErrores());
        estado.put("ultimoError", sincronizacionServicio.getUltimoError());
        estado.put("conexionNube", sincronizacionServicio.verificarConexionNube());
        estado.put("businessId", sincronizacionServicio.getBusinessId());
        estado.put("mongoUri", sincronizacionServicio.getMongoUriEnmascarada());
        estado.put("baseDatos", sincronizacionServicio.getNombreBaseDatos());
        estado.put("consultadoEn", LocalDateTime.now());

        return ResponseEntity.ok(RespuestaApi.exitosa(estado, "Estado de sincronización obtenido"));
    }

    /**
     * Activa o desactiva la sincronización con la nube en runtime.
     */
    @PostMapping("/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Map<String, Object>>> toggleSync(
            @RequestParam boolean habilitado) {
        sincronizacionServicio.setHabilitado(habilitado);
        Map<String, Object> datos = Map.of(
                "habilitado", habilitado,
                "actualizadoEn", LocalDateTime.now());
        String mensaje = habilitado ? "Sincronización habilitada" : "Sincronización deshabilitada";
        return ResponseEntity.ok(RespuestaApi.exitosa(datos, mensaje));
    }

    /**
     * Fuerza una verificación real de conexión a MongoDB Atlas.
     * Cierra el cliente existente, reconecta y hace ping con logging detallado.
     */
    @PostMapping("/verificar-conexion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Map<String, Object>>> verificarConexion() {
        Map<String, Object> resultado = sincronizacionServicio.forzarVerificacionConexion();
        boolean exito = Boolean.TRUE.equals(resultado.get("exito"));
        String mensaje = exito ? "Conexión a MongoDB Atlas verificada exitosamente"
                : "No se pudo conectar a MongoDB Atlas: " + resultado.get("error");
        return ResponseEntity.ok(RespuestaApi.exitosa(resultado, mensaje));
    }

    /**
     * Respalda TODOS los datos de PostgreSQL a MongoDB Atlas.
     * Cada tabla se convierte en una colección espejo.
     */
    @PostMapping("/respaldo-completo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Map<String, Object>>> respaldoCompleto() {
        Map<String, Object> resultado = sincronizacionServicio.respaldarTodosLosDatos();
        boolean exito = Boolean.TRUE.equals(resultado.get("exito"));
        String mensaje = exito
                ? "Respaldo completo realizado: " + resultado.get("totalRegistros") + " registros en " + resultado.get("tablasExitosas") + " tablas"
                : "Error en respaldo: " + resultado.get("error");
        return ResponseEntity.ok(RespuestaApi.exitosa(resultado, mensaje));
    }
}
