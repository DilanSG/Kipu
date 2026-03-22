/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/* Entidad que representa un método de pago configurado en el sistema.
 * Los métodos de pago son dinámicos: solo el administrador puede crear y eliminar
 * métodos según las necesidades. Sin embargo, el método "EFECTIVO"
 * es obligatorio, es el método por defecto del sistema.
 * @see EntidadBase para campos heredados (fechaCreacion, fechaActualizacion, activo) */
@Entity
@Table(name = "metodos_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class MetodoPago extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Long idMetodoPago;

    @Column(name = "codigo", length = 2)
    private String codigo;

    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    // Orden de visualización en la interfaz de pago, menor número = aparece primero. el metodo de pago predeterminado siempre debe ser orden 0.
    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Column(name = "es_predeterminado", nullable = false)
    private Boolean esPredeterminado = false;
}
