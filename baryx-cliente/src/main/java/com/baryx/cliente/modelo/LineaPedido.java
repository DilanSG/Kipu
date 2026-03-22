/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa una línea de pedido en una mesa.
 * Cada click en un producto crea una nueva línea.
 */
public class LineaPedido {
    private Long idProducto;
    private String nombreProducto;
    private BigDecimal precioUnitario;
    private LocalDateTime timestamp; // Para mantener orden cronológico
    
    public LineaPedido(Long idProducto, String nombreProducto, BigDecimal precioUnitario, LocalDateTime timestamp) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.precioUnitario = precioUnitario;
        this.timestamp = timestamp;
    }
    
    public Long getIdProducto() {
        return idProducto;
    }
    
    public String getNombreProducto() {
        return nombreProducto;
    }
    
    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
