/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.servicio;

import com.kipu.common.dto.ConfiguracionSistemaDto;

/**
 * Interfaz del servicio de configuración global del sistema.
 *
 * Define las operaciones para consultar y modificar configuraciones
 * globales que se comparten entre todos los clientes conectados.
 *
 * @see com.kipu.servidor.servicio.ConfiguracionSistemaServicioImpl
 */
public interface ConfiguracionSistemaServicio {

    /**
     * Obtiene el valor de una configuración por su clave.
     *
     * @param clave Clave de la configuración (ej: "idioma")
     * @return DTO con la configuración encontrada
     * @throws com.kipu.common.excepcion.RecursoNoEncontradoException si no existe la clave
     */
    ConfiguracionSistemaDto obtenerPorClave(String clave);

    /**
     * Actualiza el valor de una configuración existente.
     * Solo el ADMIN puede invocar esta operación.
     *
     * @param clave Clave de la configuración a actualizar
     * @param nuevoValor Nuevo valor a establecer
     * @return DTO con la configuración actualizada
     * @throws com.kipu.common.excepcion.RecursoNoEncontradoException si no existe la clave
     */
    ConfiguracionSistemaDto actualizar(String clave, String nuevoValor);
}
