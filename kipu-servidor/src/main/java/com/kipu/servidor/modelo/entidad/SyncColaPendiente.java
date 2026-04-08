/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Registro en la cola de sincronización con MongoDB Atlas.
 * Capturado automáticamente por triggers CDC de PostgreSQL.
 *
 * No extiende {@link EntidadBase} — tiene ciclo de vida propio
 * (se crea por triggers, se marca sincronizado por el scheduler,
 * y se elimina por limpieza automática).
 */
@Entity
@Table(name = "sync_cola_pendiente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncColaPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tabla_origen", nullable = false, length = 100)
    private String tablaOrigen;

    @Column(name = "operacion", nullable = false, length = 10)
    private String operacion;

    @Column(name = "id_registro", nullable = false)
    private Long idRegistro;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_json", nullable = false, columnDefinition = "JSONB")
    private String datosJson;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Builder.Default
    @Column(name = "sincronizado", nullable = false)
    private Boolean sincronizado = false;

    @Column(name = "fecha_sincronizacion")
    private LocalDateTime fechaSincronizacion;

    @Builder.Default
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Builder.Default
    @Column(name = "error", nullable = false)
    private Boolean error = false;

    @Column(name = "detalle_error", length = 500)
    private String detalleError;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
