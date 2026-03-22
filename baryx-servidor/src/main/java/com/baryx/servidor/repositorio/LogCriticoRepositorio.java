/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.repositorio;

import com.baryx.common.enums.EstadoLog;
import com.baryx.servidor.modelo.entidad.LogCritico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para la entidad {@link LogCritico}.
 *
 * Provee consultas para listar, filtrar y contar logs críticos
 * ordenados por fecha descendente (más recientes primero).
 */
@Repository
public interface LogCriticoRepositorio extends JpaRepository<LogCritico, Long> {

    /**
     * Lista todos los logs ordenados por fecha descendente.
     *
     * @return Lista de logs (más recientes primero)
     */
    List<LogCritico> findAllByOrderByFechaCreacionDesc();

    /**
     * Lista logs por nivel de severidad (CRITICO, ERROR).
     *
     * @param nivel Nivel de severidad
     * @return Lista de logs filtrados por nivel
     */
    List<LogCritico> findByNivelOrderByFechaCreacionDesc(String nivel);

    /**
     * Lista logs por estado.
     *
     * @param estado Estado del log
     * @return Lista de logs filtrados por estado
     */
    List<LogCritico> findByEstadoOrderByFechaCreacionDesc(EstadoLog estado);

    /**
     * Lista logs no resueltos (NOTIFICACION_ERROR o EN_REVISION).
     *
     * @return Lista de logs pendientes de resolución
     */
    List<LogCritico> findByEstadoNotOrderByFechaCreacionDesc(EstadoLog estado);

    /**
     * Cuenta logs no resueltos (para badge de notificación).
     *
     * @return Cantidad de logs pendientes
     */
    long countByEstadoNot(EstadoLog estado);
}
