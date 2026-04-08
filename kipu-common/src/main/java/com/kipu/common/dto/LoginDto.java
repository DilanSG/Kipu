/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*DTO para transferir los datos de login de un usuario.
 * Campos:
 * - nombreUsuario: El nombre de usuario para autenticarse
 * - contrasena: La contraseña del usuario (en texto plano, se recomienda usar HTTPS para proteger esta información en tránsito)
 * Uso:
 * - El cliente envía este DTO al servidor para intentar iniciar sesión.
 * - El servidor valida las credenciales y responde con un token de autenticación si son correctas, o con un error si no lo son. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginDto {
    private String nombreUsuario;
    private String contrasena;
}
