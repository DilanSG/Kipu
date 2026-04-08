/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/*DTO para transferir información de usuarios
 * Campos:
 * - idUsuario: Identificador único del usuario
 * - nombreCompleto: Nombre completo del usuario
 * - nombreUsuario: Nombre de usuario para login (solo para ADMIN)
 * - codigo: Código numérico para CAJERO/MESERO (ej: "01", "02", ..., "99")
 * - pin: PIN de 4 dígitos para CAJERO/MESERO (solo en creación/actualización)
 * - contrasena: Contraseña para ADMIN (solo en creación/actualización)
 * - rol: Rol del usuario (ADMIN, CAJERO, MESERO)
 * - genero: Género del usuario (MASCULINO, FEMENINO) - para mensajes responsivos
 * - email: Correo electrónico del usuario
 * - activo: Si el usuario está activo o deshabilitado
 * - fechaCreacion: Fecha y hora de creación del usuario
 * - fechaActualizacion: Fecha y hora de la última actualización del usuario */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioDto {
    
    private Long idUsuario;
    private String nombreCompleto;
    private String nombreUsuario;
    private String codigo;
    private String pin;
    private String contrasena;
    private String rol;
    private String genero;
    private String email;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
