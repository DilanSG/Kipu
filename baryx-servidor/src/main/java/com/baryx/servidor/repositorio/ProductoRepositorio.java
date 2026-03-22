/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.repositorio;

import com.baryx.servidor.modelo.entidad.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepositorio extends JpaRepository<Producto, Long> {
    
    List<Producto> findByActivoTrue();
    
    Optional<Producto> findByCodigo(String codigo);
    
    List<Producto> findByCategoria_IdCategoriaAndActivoTrue(Long idCategoria);
    
    List<Producto> findByNombreContainingIgnoreCaseAndActivoTrue(String nombre);

    @Query("SELECT p.codigo FROM Producto p WHERE p.codigo IS NOT NULL")
    List<String> obtenerTodosLosCodigos();

    boolean existsByCodigo(String codigo);
}
