/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.controlador;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.CategoriaDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.servidor.servicio.CategoriaServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/*Controlador REST para gestión de categorías de productos.
 *Endpoints disponibles:
 *-GET /api/categorias: Listar categorías activas (ordenadas)
 *-GET /api/categorias/{id}: Obtener una categoría por ID
 *-POST /api/categorias: Crear nueva categoría (solo ADMIN)
 *-PUT /api/categorias/{id}: Actualizar categoría (solo ADMIN)
 *-DELETE /api/categorias/{id}: Eliminar categoría lógicamente (solo ADMIN) */
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {
    
    private final CategoriaServicio categoriaServicio;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<List<CategoriaDto>>> listar() {
        List<CategoriaDto> categorias = categoriaServicio.listarActivas();
        return ResponseEntity.ok(RespuestaApi.exitosa(categorias, Constantes.Mensajes.CATEGORIAS_OBTENIDAS));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MESERO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<RespuestaApi<CategoriaDto>> buscarPorId(@PathVariable Long id) {
        CategoriaDto categoria = categoriaServicio.buscarPorId(id);
        return ResponseEntity.ok(RespuestaApi.exitosa(categoria, Constantes.Mensajes.CATEGORIA_ENCONTRADA));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<CategoriaDto>> crear(@Valid @RequestBody CategoriaDto categoriaDto) {
        CategoriaDto nueva = categoriaServicio.crear(categoriaDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespuestaApi.exitosa(nueva, Constantes.Mensajes.CATEGORIA_CREADA));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<CategoriaDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaDto categoriaDto) {
        CategoriaDto actualizada = categoriaServicio.actualizar(id, categoriaDto);
        return ResponseEntity.ok(RespuestaApi.exitosa(actualizada, Constantes.Mensajes.CATEGORIA_ACTUALIZADA));
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RespuestaApi<Void>> eliminar(@PathVariable Long id) {
        categoriaServicio.eliminar(id);
        return ResponseEntity.ok(RespuestaApi.exitosa(null, Constantes.Mensajes.CATEGORIA_ELIMINADA));
    }
}
