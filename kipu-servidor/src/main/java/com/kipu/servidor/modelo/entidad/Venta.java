/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Registro de una venta completada (mesa facturada). */
@Entity
@Table(name = "ventas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Venta extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Long idVenta;

    @Column(name = "id_mesa")
    private Long idMesa;

    @Column(name = "numero_mesa", nullable = false, length = 10)
    private String numeroMesa;

    @Column(name = "id_mesero")
    private Long idMesero;

    @Column(name = "nombre_mesero", length = 100)
    private String nombreMesero;

    @Column(name = "id_cajero")
    private Long idCajero;

    @Column(name = "nombre_cajero", length = 100)
    private String nombreCajero;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "impoconsumo", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal impoconsumo = BigDecimal.ZERO;

    @Column(name = "propina", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal propina = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = "COMPLETADA";

    @Builder.Default
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pago> pagos = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineaVenta> lineas = new ArrayList<>();
}
