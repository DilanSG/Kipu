/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.repositorio;

import com.baryx.servidor.modelo.entidad.MetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetodoPagoRepositorio extends JpaRepository<MetodoPago, Long> {
    
    List<MetodoPago> findByActivoTrueOrderByOrdenAsc();
    
    Optional<MetodoPago> findByNombreAndActivoTrue(String nombre);
    
    Optional<MetodoPago> findByCodigoAndActivoTrue(String codigo);
    
    boolean existsByNombre(String nombre);
}
