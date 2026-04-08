/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.repositorio;

import com.kipu.servidor.modelo.entidad.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepositorio extends JpaRepository<Mesa, Long> {
    
    Optional<Mesa> findByNumeroMesa(String numeroMesa);
    
    @Query("SELECT m FROM Mesa m WHERE m.estado = 'OCUPADA' " +
           "AND (:idMesero IS NULL OR m.mesero.idUsuario = :idMesero)")
    List<Mesa> findMesasActivas(@Param("idMesero") Long idMesero);
    
    @Query("SELECT m FROM Mesa m LEFT JOIN FETCH m.pedidoActual p " +
           "LEFT JOIN FETCH p.lineas l WHERE m.idMesa = :idMesa")
    Optional<Mesa> findByIdWithPedido(@Param("idMesa") Long idMesa);
}
