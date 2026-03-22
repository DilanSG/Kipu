/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.repositorio;

import com.baryx.servidor.modelo.entidad.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepositorio extends JpaRepository<Categoria, Long> {
    
    List<Categoria> findByActivoTrueOrderByOrdenAsc();
    
    Optional<Categoria> findByNombreAndActivoTrue(String nombre);
    
    boolean existsByNombre(String nombre);
}
