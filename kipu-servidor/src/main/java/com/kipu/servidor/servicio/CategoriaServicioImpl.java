/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.servicio;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.excepcion.RecursoNoEncontradoException;
import com.kipu.common.excepcion.ValidacionException;
import com.kipu.common.dto.CategoriaDto;
import com.kipu.servidor.modelo.entidad.Categoria;
import com.kipu.servidor.repositorio.CategoriaRepositorio;
import com.kipu.servidor.repositorio.CategoriaRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/*Este servicio maneja toda la lógica de negocio relacionada con categorías:
 * - Lista categorías activas (ordenadas por campo 'orden')
 * - Crea nuevas categorías (validando nombre único)
 * - Actualiza categorías existentes
 * - Eliminación lógica (marcar como inactivo)
 * - Validación de datos (nombre requerido, orden no negativo)
 * - Manejo de excepciones específicas para casos de no encontrado y validación
 * Permisos:
 * - Listar categorías: Todos los roles
 * - Crear/Editar/Eliminar: Solo ADMIN */
@Service
@Transactional
@RequiredArgsConstructor
public class CategoriaServicioImpl implements CategoriaServicio {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoriaServicioImpl.class);
    
    private final CategoriaRepositorio categoriaRepositorio;
    private final com.kipu.servidor.mapeo.CategoriaMapper categoriaMapper;
    
    @Override
    @Transactional(readOnly = true)
    public List<CategoriaDto> listarActivas() {
        logger.debug("Listando categorías activas");
        return categoriaMapper.aListaDto(categoriaRepositorio.findByActivoTrueOrderByOrdenAsc());
    }
    
    @Override
    @Transactional(readOnly = true)
    public CategoriaDto buscarPorId(Long id) {
        logger.debug("Buscando categoría con ID: {}", id);
        Categoria categoria = categoriaRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Categoría", id));
        return categoriaMapper.aDto(categoria);
    }
    
    @Override
    public CategoriaDto crear(CategoriaDto dto) {
        logger.info("Creando nueva categoría: {}", dto.getNombre());
        
        validarCategoria(dto);
        
        if (categoriaRepositorio.findByNombreAndActivoTrue(dto.getNombre()).isPresent()) {
            throw new ValidacionException(Constantes.Mensajes.NOMBRE_CATEGORIA_EXISTS);
        }
        
        Categoria categoria = categoriaMapper.aEntidad(dto);
        categoria.setActivo(true);
        
        categoria = categoriaRepositorio.save(categoria);
        logger.info("Categoría creada exitosamente con ID: {}", categoria.getIdCategoria());
        
        return categoriaMapper.aDto(categoria);
    }
    
    @Override
    public CategoriaDto actualizar(Long id, CategoriaDto dto) {
        logger.info("Actualizando categoría ID: {}", id);
        
        Categoria categoria = categoriaRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Categoría", id));
        
        validarCategoria(dto);
        
        categoriaRepositorio.findByNombreAndActivoTrue(dto.getNombre())
                .ifPresent(c -> {
                    if (!c.getIdCategoria().equals(id)) {
                        throw new ValidacionException(Constantes.Mensajes.NOMBRE_CATEGORIA_EXISTS);
                    }
                });
        
        categoria.setNombre(dto.getNombre());
        categoria.setDescripcion(dto.getDescripcion());
        categoria.setOrden(dto.getOrden());
        if (dto.getColor() != null) {
            categoria.setColor(dto.getColor());
        }
        
        categoria = categoriaRepositorio.save(categoria);
        logger.info("Categoría ID: {} actualizada exitosamente", id);
        
        return categoriaMapper.aDto(categoria);
    }
    
    @Override
    public void eliminar(Long id) {
        logger.info("Eliminando categoría ID: {}", id);
        
        Categoria categoria = categoriaRepositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Categoría", id));
        
        categoria.setActivo(false);
        categoriaRepositorio.save(categoria);
        
        logger.info("Categoría ID: {} seteada a false", id);
    }
    
    private void validarCategoria(CategoriaDto dto) {
        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            throw new ValidacionException(Constantes.Mensajes.NOMBRE_CATEGORIA_EXISTS.contains("nombre") ? Constantes.Mensajes.NOMBRE_REQUERIDO : "Nombre requerido");
        }
        
        if (dto.getOrden() != null && dto.getOrden() < 0) {
            throw new ValidacionException(Constantes.Mensajes.ORDEN_NEGATIVO);
        }
    }
}
