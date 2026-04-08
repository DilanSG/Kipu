/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.modelo;

/**
 * Modelo para representar una mesa en el sistema.
 */
public class Mesa {
    
    private Long idMesa;
    private final String nombre;
    private final String meseroNombre;
    private final Long meseroId;
    private String estado;
    
    public Mesa(Long idMesa, String nombre, String meseroNombre, Long meseroId, String estado) {
        this.idMesa = idMesa;
        this.nombre = nombre;
        this.meseroNombre = meseroNombre;
        this.meseroId = meseroId;
        this.estado = estado;
    }
    
    // Constructor de compatibilidad (temporal)
    public Mesa(String nombre, String meseroNombre, Long meseroId, String estado) {
        this(null, nombre, meseroNombre, meseroId, estado);
    }
    
    public Long getIdMesa() { return idMesa; }
    public void setIdMesa(Long idMesa) { this.idMesa = idMesa; }
    
    public String getNombre() { return nombre; }
    
    public String getMeseroNombre() { return meseroNombre; }
    
    public Long getMeseroId() { return meseroId; }
    
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    
    @Override
    public String toString() {
        return "Mesa{" +
                "id=" + idMesa +
                ", nombre='" + nombre + '\'' +
                ", mesero='" + meseroNombre + '\'' +
                ", estado='" + estado + '\'' +
                '}';
    }
}
