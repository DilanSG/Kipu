/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.controlador;

import com.kipu.common.dto.MetodoPagoDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.servidor.servicio.MetodoPagoServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/* Controlador REST para gestión de métodos de pago.
 * Endpoints disponibles:
 * - GET /api/metodos-pago: Listar métodos de pago activos
 * - POST /api/metodos-pago: Crear nuevo método de pago (solo ADMIN)
 * - PUT /api/metodos-pago/{id}: Actualizar método de pago (solo ADMIN)
 * - DELETE /api/metodos-pago/{id}: Eliminar método de pago (solo ADMIN) */
@RestController
@RequestMapping("/api/metodos-pago")
@RequiredArgsConstructor
public class MetodoPagoController {
    
    private final MetodoPagoServicio metodoPagoServicio;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<List<MetodoPagoDto>>> listar() {
        List<MetodoPagoDto> metodosPago = metodoPagoServicio.listarActivos();
        return ResponseEntity.ok(RespuestaApi.exitosa(metodosPago, "Métodos de pago obtenidos exitosamente"));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<MetodoPagoDto>> crear(@Valid @RequestBody MetodoPagoDto metodoPagoDto) {
        MetodoPagoDto nuevo = metodoPagoServicio.crear(metodoPagoDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespuestaApi.exitosa(nuevo, "Método de pago creado exitosamente"));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<MetodoPagoDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody MetodoPagoDto metodoPagoDto) {
        MetodoPagoDto actualizado = metodoPagoServicio.actualizar(id, metodoPagoDto);
        return ResponseEntity.ok(RespuestaApi.exitosa(actualizado, "Método de pago actualizado exitosamente"));
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Void>> eliminar(@PathVariable Long id) {
        metodoPagoServicio.eliminar(id);
        return ResponseEntity.ok(RespuestaApi.exitosa(null, "Método de pago eliminado exitosamente"));
    }
}
