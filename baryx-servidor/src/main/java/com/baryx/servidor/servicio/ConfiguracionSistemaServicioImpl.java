/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.servicio;

import com.baryx.common.dto.ConfiguracionSistemaDto;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.servidor.modelo.entidad.ConfiguracionSistema;
import com.baryx.servidor.repositorio.ConfiguracionSistemaRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de configuración global del sistema.
 *
 * Gestiona las operaciones de lectura y actualización de configuraciones
 * almacenadas en la tabla configuracion_sistema.
 *
 * @see ConfiguracionSistemaServicio
 * @see ConfiguracionSistemaRepositorio
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ConfiguracionSistemaServicioImpl implements ConfiguracionSistemaServicio {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionSistemaServicioImpl.class);

    private final ConfiguracionSistemaRepositorio repositorio;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ConfiguracionSistemaDto obtenerPorClave(String clave) {
        logger.debug("Buscando configuración con clave: {}", clave);

        ConfiguracionSistema config = repositorio.findByClave(clave)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Configuración no encontrada con clave: " + clave));

        return mapearADto(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfiguracionSistemaDto actualizar(String clave, String nuevoValor) {
        logger.info("Actualizando configuración '{}' a valor '{}'", clave, nuevoValor);

        ConfiguracionSistema config = repositorio.findByClave(clave)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Configuración no encontrada con clave: " + clave));

        config.setValor(nuevoValor);
        ConfiguracionSistema guardada = repositorio.save(config);

        logger.info("Configuración '{}' actualizada exitosamente", clave);
        return mapearADto(guardada);
    }

    // Convierte la entidad a DTO
    private ConfiguracionSistemaDto mapearADto(ConfiguracionSistema entidad) {
        return ConfiguracionSistemaDto.builder()
                .idConfiguracion(entidad.getIdConfiguracion())
                .clave(entidad.getClave())
                .valor(entidad.getValor())
                .descripcion(entidad.getDescripcion())
                .build();
    }
}
