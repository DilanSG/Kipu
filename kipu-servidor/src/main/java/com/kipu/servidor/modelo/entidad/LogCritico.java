/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.modelo.entidad;

import com.kipu.common.enums.EstadoLog;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA para logs críticos del sistema.
 *
 * Almacena errores importantes que generaron crashes, errores de renderizado
 * u otros fallos que requieren atención para mejoras del sistema.
 *
 * No extiende {@link EntidadBase} porque tiene su propio ciclo de vida:
 * no usa soft-delete ni fecha_actualizacion (los logs son inmutables
 * excepto por el campo {@code estado}).
 *
 * @see com.kipu.common.dto.LogCriticoDto
 */
@Entity
@Table(name = "logs_criticos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogCritico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_log")
    private Long idLog;

    /** Nivel de severidad: CRITICO o ERROR */
    @Column(name = "nivel", nullable = false, length = 20)
    private String nivel;

    /** Clase o módulo donde ocurrió el error */
    @Column(name = "origen", nullable = false, length = 100)
    private String origen;

    /** Descripción breve del error */
    @Column(name = "mensaje", nullable = false, length = 500)
    private String mensaje;

    /** Stack trace o información adicional */
    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    /** Usuario logueado al momento del error */
    @Column(name = "usuario", length = 100)
    private String usuario;

    /** IP del cliente que reportó */
    @Column(name = "ip_cliente", length = 45)
    private String ipCliente;

    /** Nombre del equipo cliente */
    @Column(name = "nombre_cliente", length = 100)
    private String nombreCliente;

    /** Fecha y hora del error */
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** Estado del log: NOTIFICACION_ERROR, EN_REVISION, RESUELTO */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoLog estado = EstadoLog.NOTIFICACION_ERROR;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
