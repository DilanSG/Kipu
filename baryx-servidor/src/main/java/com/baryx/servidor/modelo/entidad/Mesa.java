/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

// Entidad que representa una mesa del establcimiento, con su estado y relación con el mesero y el pedido actual
@Entity
@Table(name = "mesas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Mesa extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mesa")
    private Long idMesa;

    @Column(name = "numero_mesa", nullable = false, unique = true, length = 10)
    private String numeroMesa;

    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = "DISPONIBLE"; // DISPONIBLE, OCUPADA, RESERVADA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_mesero")
    private Usuario mesero;

    @OneToOne(mappedBy = "mesa", cascade = CascadeType.ALL, orphanRemoval = true)
    private Pedido pedidoActual;
}
