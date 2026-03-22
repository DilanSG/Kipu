/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.repositorio;

import com.baryx.servidor.modelo.entidad.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepositorio extends JpaRepository<Venta, Long> {

    /** Lista ventas creadas desde la fecha dada, ordenadas por fecha descendente. */
    List<Venta> findByFechaCreacionGreaterThanEqualOrderByFechaCreacionDesc(LocalDateTime desde);
}
