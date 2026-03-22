/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.common.enums;

/**
 * Estados posibles de un log crítico del sistema.
 *
 * Define el ciclo de vida de un log:
 * NOTIFICACION_ERROR → EN_REVISION → RESUELTO
 */
public enum EstadoLog {
    /** Error nuevo, sin revisar */
    NOTIFICACION_ERROR,
    /** En proceso de revisión por el administrador */
    EN_REVISION,
    /** Error revisado y resuelto */
    RESUELTO
}
