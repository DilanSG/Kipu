/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.repositorio;

import com.kipu.servidor.modelo.entidad.SyncColaPendiente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio JPA para la cola de sincronización con MongoDB Atlas.
 */
@Repository
public interface SyncColaRepositorio extends JpaRepository<SyncColaPendiente, Long> {

    /**
     * Obtiene registros pendientes de sincronización (no sincronizados, sin error),
     * ordenados por fecha de creación ascendente (FIFO).
     */
    List<SyncColaPendiente> findBySincronizadoFalseAndErrorFalseOrderByFechaCreacionAsc(Pageable pageable);

    /**
     * Elimina registros ya sincronizados anteriores a la fecha indicada.
     * Usado por la limpieza automática para evitar crecimiento indefinido de la cola.
     */
    long deleteBySincronizadoTrueAndFechaSincronizacionBefore(LocalDateTime fecha);

    /**
     * Cuenta registros pendientes de sincronización (para monitoreo).
     */
    long countBySincronizadoFalseAndErrorFalse();

    /**
     * Cuenta registros con error permanente (para alertas).
     */
    long countByErrorTrue();
}
