package com.kipu.servidor.servicio;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.CategoriaDto;
import com.kipu.common.excepcion.RecursoNoEncontradoException;
import com.kipu.common.excepcion.ValidacionException;
import com.kipu.servidor.mapeo.CategoriaMapper;
import com.kipu.servidor.modelo.entidad.Categoria;
import com.kipu.servidor.repositorio.CategoriaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para CategoriaServicio
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests de CategoriaServicio")
class CategoriaServicioTest {

    @Mock
    private CategoriaRepositorio categoriaRepositorio;

    @Mock
    private CategoriaMapper categoriaMapper;

    @InjectMocks
    private CategoriaServicioImpl categoriaServicio;

    private CategoriaDto categoriaDto;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoriaDto = CategoriaDto.builder()
                .nombre("Bebidas")
                .descripcion("Bebidas variadas")
                .orden(1)
                .build();

        categoria = Categoria.builder()
                .idCategoria(1L)
                .nombre("Bebidas")
                .descripcion("Bebidas variadas")
                .orden(1)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("Debe listar categorías activas")
    void debeListarCategoriasActivas() {
        // Given
        List<Categoria> categorias = Arrays.asList(categoria);
        List<CategoriaDto> dtos = Arrays.asList(categoriaDto);
        when(categoriaRepositorio.findByActivoTrueOrderByOrdenAsc()).thenReturn(categorias);
        when(categoriaMapper.aListaDto(categorias)).thenReturn(dtos);

        // When
        List<CategoriaDto> resultado = categoriaServicio.listarActivas();

        // Then
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("Bebidas", resultado.get(0).getNombre());
        verify(categoriaRepositorio).findByActivoTrueOrderByOrdenAsc();
    }

    @Test
    @DisplayName("Debe buscar categoría por ID correctamente")
    void debeObtenerCategoriaPorId() {
        // Given
        when(categoriaRepositorio.findById(1L)).thenReturn(Optional.of(categoria));
        when(categoriaMapper.aDto(categoria)).thenReturn(categoriaDto);

        // When
        CategoriaDto resultado = categoriaServicio.buscarPorId(1L);

        // Then
        assertNotNull(resultado);
        assertEquals("Bebidas", resultado.getNombre());
        verify(categoriaRepositorio).findById(1L);
    }

    @Test
    @DisplayName("Debe crear una categoría correctamente")
    void debeCrearCategoriaCorrectamente() {
        // Given
        when(categoriaRepositorio.findByNombreAndActivoTrue(anyString())).thenReturn(Optional.empty());
        when(categoriaMapper.aEntidad(categoriaDto)).thenReturn(categoria);
        when(categoriaRepositorio.save(any(Categoria.class))).thenReturn(categoria);
        when(categoriaMapper.aDto(categoria)).thenReturn(categoriaDto);

        // When
        CategoriaDto resultado = categoriaServicio.crear(categoriaDto);

        // Then
        assertNotNull(resultado);
        assertEquals("Bebidas", resultado.getNombre());
        verify(categoriaRepositorio).save(any(Categoria.class));
    }

    @Test
    @DisplayName("Debe actualizar categoría correctamente")
    void debeActualizarCategoriaCorrectamente() {
        // Given
        when(categoriaRepositorio.findById(1L)).thenReturn(Optional.of(categoria));
        when(categoriaRepositorio.save(any(Categoria.class))).thenReturn(categoria);
        when(categoriaMapper.aDto(categoria)).thenReturn(categoriaDto);

        // When
        CategoriaDto resultado = categoriaServicio.actualizar(1L, categoriaDto);

        // Then
        assertNotNull(resultado);
        verify(categoriaRepositorio).save(any(Categoria.class));
    }

    @Test
    @DisplayName("Debe eliminar categoría correctamente")
    void debeEliminarCategoriaCorrectamente() {
        // Given
        when(categoriaRepositorio.findById(1L)).thenReturn(Optional.of(categoria));

        // When
        categoriaServicio.eliminar(1L);

        // Then
        assertFalse(categoria.getActivo());
        verify(categoriaRepositorio).save(categoria);
    }
}
