/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.repositorio;

import com.kipu.servidor.modelo.entidad.MetodoPago;
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
