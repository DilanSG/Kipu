/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.repositorio;

import com.baryx.servidor.modelo.entidad.ConfiguracionSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para acceso a datos de configuración del sistema.
 *
 * Proporciona operaciones CRUD sobre la tabla configuracion_sistema.
 * Las configuraciones se identifican por su clave única.
 *
 * @see ConfiguracionSistema
 */
@Repository
public interface ConfiguracionSistemaRepositorio extends JpaRepository<ConfiguracionSistema, Long> {

    /**
     * Busca una configuración por su clave.
     *
     * @param clave Clave de la configuración (ej: "idioma")
     * @return Optional con la configuración si existe
     */
    Optional<ConfiguracionSistema> findByClave(String clave);
}
