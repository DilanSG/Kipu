package com.baryx.servidor.servicio;

import com.baryx.common.constantes.Constantes;
import com.baryx.common.dto.ProductoDto;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.common.excepcion.ValidacionException;
import com.baryx.servidor.mapeo.ProductoMapper;
import com.baryx.servidor.modelo.entidad.Categoria;
import com.baryx.servidor.modelo.entidad.Producto;
import com.baryx.servidor.repositorio.CategoriaRepositorio;
import com.baryx.servidor.repositorio.ProductoRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ProductoServicio
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests de ProductoServicio")
class ProductoServicioTest {

    @Mock
    private ProductoRepositorio productoRepositorio;

    @Mock
    private CategoriaRepositorio categoriaRepositorio;

    @Mock
    private ProductoMapper productoMapper;

    @InjectMocks
    private ProductoServicioImpl productoServicio;

    private ProductoDto productoDto;
    private Producto producto;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder()
                .idCategoria(1L)
                .nombre("Bebidas")
                .descripcion("Bebidas variadas")
                .build();

        productoDto = ProductoDto.builder()
                .nombre("Coca Cola")
                .descripcion("Bebida gaseosa")
                .precio(new BigDecimal("2.50"))
                .idCategoria(1L)
                .build();

        producto = Producto.builder()
                .idProducto(1L)
                .nombre("Coca Cola")
                .descripcion("Bebida gaseosa")
                .precio(new BigDecimal("2.50"))
                .categoria(categoria)
                .stockActual(100)
                .stockMinimo(10)
                .requiereStock(true)
                .build();
    }

    @Test
    @DisplayName("Debe listar productos activos")
    void debeListarProductosActivos() {
        // Given
        List<Producto> productos = Arrays.asList(producto);
        List<ProductoDto> dtos = Arrays.asList(productoDto);
        when(productoRepositorio.findByActivoTrue()).thenReturn(productos);
        when(productoMapper.aListaDto(productos)).thenReturn(dtos);

        // When
        List<ProductoDto> resultado = productoServicio.listarActivos();

        // Then
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("Coca Cola", resultado.get(0).getNombre());
        verify(productoRepositorio).findByActivoTrue();
    }

    @Test
    @DisplayName("Debe buscar un producto por ID")
    void debeBuscarProductoPorId() {
        // Given
        when(productoRepositorio.findById(1L)).thenReturn(Optional.of(producto));
        when(productoMapper.aDto(producto)).thenReturn(productoDto);

        // When
        ProductoDto resultado = productoServicio.buscarPorId(1L);

        // Then
        assertNotNull(resultado);
        assertEquals("Coca Cola", resultado.getNombre());
        verify(productoRepositorio).findById(1L);
    }

    @Test
    @DisplayName("Debe lanzar excepción si el producto no existe al buscar por ID")
    void debeLanzarExcepcionSiProductoNoExiste() {
        // Given
        when(productoRepositorio.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RecursoNoEncontradoException.class, () -> productoServicio.buscarPorId(1L));
    }

    @Test
    @DisplayName("Debe crear un producto correctamente")
    void debeCrearProductoCorrectamente() {
        // Given
        when(categoriaRepositorio.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoMapper.aEntidad(productoDto)).thenReturn(producto);
        when(productoRepositorio.save(any(Producto.class))).thenReturn(producto);
        when(productoMapper.aDto(producto)).thenReturn(productoDto);

        // When
        ProductoDto resultado = productoServicio.crear(productoDto);

        // Then
        assertNotNull(resultado);
        assertEquals("Coca Cola", resultado.getNombre());
        verify(productoRepositorio).save(any(Producto.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si la categoría no existe al crear")
    void debeLanzarExcepcionSiCategoriaNoExisteAlCrear() {
        // Given
        when(categoriaRepositorio.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RecursoNoEncontradoException.class, () -> productoServicio.crear(productoDto));
        verify(productoRepositorio, never()).save(any(Producto.class));
    }

    @Test
    @DisplayName("Debe actualizar un producto correctamente")
    void debeActualizarProductoCorrectamente() {
        // Given
        when(productoRepositorio.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepositorio.save(any(Producto.class))).thenReturn(producto);
        when(productoMapper.aDto(producto)).thenReturn(productoDto);

        // When
        ProductoDto resultado = productoServicio.actualizar(1L, productoDto);

        // Then
        assertNotNull(resultado);
        verify(productoRepositorio).save(any(Producto.class));
    }

    @Test
    @DisplayName("Debe eliminar un producto correctamente")
    void debeEliminarProductoCorrectamente() {
        // Given
        when(productoRepositorio.findById(1L)).thenReturn(Optional.of(producto));

        // When
        productoServicio.eliminar(1L);

        // Then
        assertFalse(producto.getActivo());
        verify(productoRepositorio).save(producto);
    }

    @Test
    @DisplayName("Debe validar que el nombre sea requerido")
    void debeValidarNombreRequerido() {
        // Given
        ProductoDto dtoSinNombre = ProductoDto.builder()
                .nombre("")
                .idCategoria(1L)
                .precio(new BigDecimal("1.00"))
                .build();

        // When & Then
        ValidacionException ex = assertThrows(ValidacionException.class, 
                () -> productoServicio.crear(dtoSinNombre));
        assertEquals(Constantes.Mensajes.NOMBRE_REQUERIDO, ex.getMessage());
    }
}
