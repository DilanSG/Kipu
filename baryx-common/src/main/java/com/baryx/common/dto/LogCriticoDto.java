/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para logs críticos del sistema.
 *
 * Representa un error importante que requiere atención:
 * crashes, errores de renderizado, fallos de conexión graves, etc.
 *
 * Se usa tanto para enviar logs desde el cliente al servidor
 * como para listar logs existentes en el panel de herramientas.
 *
 * @see com.baryx.servidor.modelo.entidad.LogCritico
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogCriticoDto {

    /** Identificador único del log (null al crear desde cliente) */
    private Long idLog;

    /** Nivel de severidad: CRITICO o ERROR */
    private String nivel;

    /** Clase o módulo donde ocurrió el error */
    private String origen;

    /** Descripción breve del error */
    private String mensaje;

    /** Stack trace o información adicional extendida */
    private String detalle;

    /** Nombre de usuario que estaba logueado al momento del error */
    private String usuario;

    /** IP del cliente que reportó el error */
    private String ipCliente;

    /** Nombre del equipo cliente que reportó */
    private String nombreCliente;

    /** Fecha y hora en que ocurrió el error */
    private LocalDateTime fechaCreacion;

    /** Estado del log: NOTIFICACION_ERROR, EN_REVISION, RESUELTO */
    private String estado;
}
