/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
/* Clase base abstracta para todas las entidades JPA del sistema.
 * Proporciona campos comunes para auditoría y eliminación lógica.
 * Cada entidad (Usuario, Producto, Venta, etc.) hereda de esta clase para evitar duplicación de código
 * y garantizar consistencia en el manejo de fechas y estado de los registros.
 * Cada instancia de la entidad corresponde a una fila en la tabla.
 * Todos los registros necesitan:
 * - Saber cuándo fueron creados (fechaCreacion)
 * - Saber cuándo fueron modificados por última vez (fechaActualizacion)
 * - Permitir eliminación lógica en lugar de física (activo)
 * - @Getter, @Setter: Lombok genera automáticamente los métodos get/set para los campos.
 * - @NoArgsConstructor, @AllArgsConstructor: Lombok genera constructores sin argumentos y con todos los argumentos.
 * - @SuperBuilder: Lombok genera un builder que funciona con herencia, permitiendo construir objetos de clases hijas de manera fluida.
 * - @Column: Define el mapeo de los campos a las columnas de la tabla, con restricciones como nullable y updatable.
 * - @CreatedDate: Indica que fechaCreacion se establece automáticamente al crear el registro (solo una vez).
 * - @LastModifiedDate: Indica que fechaActualizacion se actualiza automáticamente cada vez que se modifica el registro.
 * - @MappedSuperclass: Indica que esta clase NO es una tabla, pero sus campos se incluyen en las tablas de las clases hijas. Esto evita la necesidad de repetir estos campos en cada entidad y garantiza que todas las entidades tengan estos campos comunes sin duplicación de código.
 * Campos heredados por todas las entidades:
 * - fechaCreacion: Timestamp de cuándo se creó el registro (se establece 1 vez)
 * - fechaActualizacion: Timestamp de la última modificación (se actualiza automáticamente)
 * - activo: Boolean para eliminación lógica (true=existe, false=eliminado)
 * 
 * Eliminación lógica vs física:
 * - Física: DELETE FROM usuarios WHERE id=1 (el registro desaparece para siempre)
 * - Lógica: UPDATE usuarios SET activo=false WHERE id=1 (el registro permanece en BD)
 * 
 * Se utiliza eliminación lógica para:
 * - Mantener el histórico de ventas y auditoría
 * - Permitir recuperar datos eliminados por error
 * - Cumplir con requisitos legales de conservación de datos*/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass 
@EntityListeners(AuditingEntityListener.class) // Habilita auditoría automática de fechas
public abstract class EntidadBase {

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
