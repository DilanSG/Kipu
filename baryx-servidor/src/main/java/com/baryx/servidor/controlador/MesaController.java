/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.controlador;

import com.baryx.common.dto.MesaActivaDto;
import com.baryx.common.dto.MesaConPedidoDto;
import com.baryx.common.dto.PedidoDto;
import com.baryx.common.dto.RespuestaApi;
import com.baryx.servidor.servicio.MesaServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/* Controlador REST para gestión de mesas y pedidos asociados.
 * Endpoints disponibles:
 * - GET /api/mesas/{idMesa}/pedido: Obtener pedido de una mesa
 * - POST /api/mesas/{numeroMesa}/pedido: Guardar pedido de una mesa
 * - GET /api/mesas/activas: Obtener mesas activas
 * - POST /api/mesas: Crear o recuperar mesa
 * - GET /api/mesas/numero/{numero}: Buscar mesa por número
 * - DELETE /api/mesas/{idMesa}: Eliminar mesa */
@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class MesaController {
    
    private final MesaServicio mesaServicio;
    
    @GetMapping("/{idMesa}/pedido")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<MesaConPedidoDto>> obtenerMesaConPedido(@PathVariable Long idMesa) {
        MesaConPedidoDto mesa = mesaServicio.obtenerMesaConPedido(idMesa);
        return ResponseEntity.ok(RespuestaApi.exitosa(mesa, "Pedido recuperado exitosamente"));
    }
    
    @PostMapping("/{numeroMesa}/pedido")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<MesaConPedidoDto>> guardarPedido(
            @PathVariable String numeroMesa,
            @RequestParam(required = false) Long idMesero,
            @Valid @RequestBody PedidoDto pedidoDto) {
        
        MesaConPedidoDto mesa = mesaServicio.guardarPedido(numeroMesa, idMesero, pedidoDto);
        return ResponseEntity.ok(RespuestaApi.exitosa(mesa, "Pedido guardado exitosamente"));
    }
    
    @GetMapping("/activas")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<List<MesaActivaDto>>> obtenerMesasActivas(
            @RequestParam(required = false) Long idMesero) {
        
        List<MesaActivaDto> mesas = mesaServicio.obtenerMesasActivas(idMesero);
        return ResponseEntity.ok(RespuestaApi.exitosa(mesas, "Mesas activas recuperadas exitosamente"));
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<MesaConPedidoDto>> crearOObtenerMesa(@RequestBody com.baryx.common.dto.CreacionMesaDto dto) {
        MesaConPedidoDto mesa = mesaServicio.crearOObtenerMesa(dto.getNumeroMesa(), dto.getIdMesero());
        return ResponseEntity.ok(RespuestaApi.exitosa(mesa, "Mesa recuperada/creada exitosamente"));
    }
    
    @GetMapping("/numero/{numero}")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<MesaConPedidoDto>> buscarPorNumero(@PathVariable String numero) {
        MesaConPedidoDto mesa = mesaServicio.buscarMesaPorNumero(numero);
        if (mesa != null) {
            return ResponseEntity.ok(RespuestaApi.exitosa(mesa, "Mesa encontrada"));
        } else {
            return ResponseEntity.status(404).body(RespuestaApi.error("NO_ENCONTRADA", "Mesa no encontrada"));
        }
    }

    @DeleteMapping("/{idMesa}")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<Void>> eliminarMesa(@PathVariable Long idMesa) {
        mesaServicio.anularMesa(idMesa);
        return ResponseEntity.ok(RespuestaApi.exitosa(null, "Mesa eliminada exitosamente"));
    }
}
