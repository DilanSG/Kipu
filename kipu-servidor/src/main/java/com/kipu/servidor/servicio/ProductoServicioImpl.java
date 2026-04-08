/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.servicio;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.excepcion.RecursoNoEncontradoException;
import com.kipu.common.excepcion.ValidacionException;
import com.kipu.common.dto.ProductoDto;
import com.kipu.servidor.modelo.entidad.Categoria;
import com.kipu.servidor.modelo.entidad.Producto;
import com.kipu.servidor.repositorio.CategoriaRepositorio;
import com.kipu.servidor.repositorio.ProductoRepositorio;
import com.kipu.servidor.mapeo.ProductoMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/*Servicio que maneja toda la lógica de negocio relacionada con productos:
 *-CRUD
 *-Validaciones de negocio (precio positivo, código único, categoría válida)
 *-Consultas especializadas (por categoría, activos, etc.)
 *-Conversión entre entidades JPA y DTOs para la API
 *Características importantes:
 *-Eliminación lógica: Los productos no se borran físicamente, se marcan como inactivos
 *-Control de stock: Se valida que el stock no sea negativo
 *-Categorización: Cada producto debe pertenecer a una categoría existente
 *-Códigos únicos: El código del producto debe ser único en el sistema
 *Permisos:
 * - Listar productos: Todos los roles
 * - Crear/Editar/Eliminar: Solo ADMIN */

@Service
@Transactional
@RequiredArgsConstructor
public class ProductoServicioImpl implements ProductoServicio {

    private static final Logger logger = LoggerFactory.getLogger(ProductoServicioImpl.class);

    private final ProductoRepositorio productoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final ProductoMapper productoMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoDto> listarActivos() {
        logger.debug("Listando productos activos");
        return productoMapper.aListaDto(productoRepositorio.findByActivoTrue());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoDto buscarPorId(Long id) {
        logger.debug("Buscando producto con ID: {}", id);
        Producto producto = productoRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Producto", id));
        return productoMapper.aDto(producto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoDto> buscarPorCategoria(Long idCategoria) {
        logger.debug("Buscando productos de categoría: {}", idCategoria);
        return productoMapper.aListaDto(productoRepositorio.findByCategoria_IdCategoriaAndActivoTrue(idCategoria));
    }

    @Override
    public ProductoDto crear(ProductoDto dto) {
        logger.info("Creando nuevo producto: {}", dto.getNombre());

        validarProducto(dto);

        Categoria categoria = categoriaRepositorio.findById(dto.getIdCategoria())
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Categoría", dto.getIdCategoria()));

        Producto producto = productoMapper.aEntidad(dto);
        producto.setCategoria(categoria);
        producto.setActivo(true);

        // Generar código de 3 dígitos automáticamente (000-999)
        String codigoGenerado = generarSiguienteCodigo();
        producto.setCodigo(codigoGenerado);
        logger.info("Código de producto generado: {}", codigoGenerado);

        producto = productoRepositorio.save(producto);
        logger.info("Producto creado exitosamente con ID: {} y código: {}", 
                producto.getIdProducto(), producto.getCodigo());

        return productoMapper.aDto(producto);
    }

    private String generarSiguienteCodigo() {
        List<String> codigosExistentes = productoRepositorio.obtenerTodosLosCodigos();
        java.util.Set<String> codigosSet = new java.util.HashSet<>(codigosExistentes);
        
        for (int i = 0; i < 1000; i++) {
            String candidato = String.format("%03d", i);
            if (!codigosSet.contains(candidato)) {
                return candidato;
            }
        }
        throw new ValidacionException("No hay códigos de producto disponibles. Máximo: 1000 productos.");
    }

    @Override
    public ProductoDto actualizar(Long id, ProductoDto dto) {
        logger.info("Actualizando producto ID: {}", id);

        Producto producto = productoRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Producto", id));

        validarProducto(dto);

        if (dto.getIdCategoria() != null && !dto.getIdCategoria().equals(producto.getCategoria().getIdCategoria())) {
            Categoria categoria = categoriaRepositorio.findById(dto.getIdCategoria())
                    .orElseThrow(() -> RecursoNoEncontradoException.porId("Categoría", dto.getIdCategoria()));
            producto.setCategoria(categoria);
        }

        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecio());
        producto.setStockActual(dto.getStockActual());
        producto.setStockMinimo(dto.getStockMinimo());
        producto.setRequiereStock(dto.getRequiereStock());

        producto = productoRepositorio.save(producto);
        logger.info("Producto actualizado exitosamente");

        return productoMapper.aDto(producto);
    }

    @Override
    public void eliminar(Long id) {
        logger.info("Eliminando producto ID: {}", id);

        Producto producto = productoRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Producto", id));

        producto.setActivo(false);
        productoRepositorio.save(producto);

        logger.info("Producto ID: {} seteado a false", id);
    }

    private void validarProducto(ProductoDto dto) {
        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            throw new ValidacionException(Constantes.Mensajes.NOMBRE_REQUERIDO);
        }

        if (dto.getPrecio() == null || dto.getPrecio().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ValidacionException(Constantes.Mensajes.PRECIO_MAYOR_CERO);
        }

        if (dto.getIdCategoria() == null) {
            throw new ValidacionException(Constantes.Mensajes.CATEGORIA_REQUERIDA);
        }
    }
}
