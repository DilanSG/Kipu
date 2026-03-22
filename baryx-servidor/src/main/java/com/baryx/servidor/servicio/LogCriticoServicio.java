/* Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular. */
package com.baryx.servidor.servicio;

import com.baryx.common.dto.LogCriticoDto;

import java.util.List;

/**
 * Interfaz para el servicio de gestión de logs críticos del sistema.
 * Soporta 3 estados: NOTIFICACION_ERROR → EN_REVISION → RESUELTO.
 */
public interface LogCriticoServicio {

    List<LogCriticoDto> listarTodos();
    List<LogCriticoDto> listarPorNivel(String nivel);
    List<LogCriticoDto> listarNoResueltos();
    LogCriticoDto registrar(LogCriticoDto dto);
    LogCriticoDto cambiarEstado(Long idLog, String estado);
    long contarNoResueltos();
}
