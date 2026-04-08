/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.controlador;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.ProductoDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.servidor.servicio.ProductoServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*Controlador REST para gestión de productos del inventario.
 *Endpoints disponibles:
 * - GET /api/productos: Listar todos los productos activos
 * - GET /api/productos/{id}: Obtener un producto por ID
 * - GET /api/productos/categoria/{idCategoria}: Productos de una categoría
 * - POST /api/productos: Crear nuevo producto (solo ADMIN)
 * - PUT /api/productos/{id}: Actualizar producto (solo ADMIN)
 * - DELETE /api/productos/{id}: Eliminar producto lógicamente (solo ADMIN) */
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoServicio productoServicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<List<ProductoDto>>> listar() {
        List<ProductoDto> productos = productoServicio.listarActivos();
        return ResponseEntity.ok(RespuestaApi.exitosa(productos, Constantes.Mensajes.PRODUCTOS_OBTENIDOS));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<ProductoDto>> buscarPorId(@PathVariable Long id) {
        ProductoDto producto = productoServicio.buscarPorId(id);
        return ResponseEntity.ok(RespuestaApi.exitosa(producto, Constantes.Mensajes.PRODUCTO_ENCONTRADO));
    }

    @GetMapping("/categoria/{idCategoria}")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<List<ProductoDto>>> buscarPorCategoria(@PathVariable Long idCategoria) {
        List<ProductoDto> productos = productoServicio.buscarPorCategoria(idCategoria);
        return ResponseEntity.ok(RespuestaApi.exitosa(productos, Constantes.Mensajes.PRODUCTOS_OBTENIDOS));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<ProductoDto>> crear(@Valid @RequestBody ProductoDto productoDto) {
        ProductoDto nuevo = productoServicio.crear(productoDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespuestaApi.exitosa(nuevo, Constantes.Mensajes.PRODUCTO_CREADO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<ProductoDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoDto productoDto) {
        ProductoDto actualizado = productoServicio.actualizar(id, productoDto);
        return ResponseEntity.ok(RespuestaApi.exitosa(actualizado, Constantes.Mensajes.PRODUCTO_ACTUALIZADO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Void>> eliminar(@PathVariable Long id) {
        productoServicio.eliminar(id);
        return ResponseEntity.ok(RespuestaApi.exitosa(null, Constantes.Mensajes.PRODUCTO_ELIMINADO));
    }
}
