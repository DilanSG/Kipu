/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*Este DTO se retorna cuando un usuario hace login exitosamente.
 *Contiene toda la información que el cliente necesita para operar:
 *-Token JWT para autenticar futuras peticiones
 *-Datos del usuario para mostrar en la interfaz
 *Flujo:
 * 1. Cliente envía POST /api/usuarios/login con {nombreUsuario, contrasena}
 * 2. AutenticacionServicio valida las credenciales
 * 3. Si son correctas, genera este DTO con:
 *   -token: JWT firmado con 24h de expiración
 *   -nombreUsuario: Para mostrar "Bienvenido, [usuario]"
 *   -nombreCompleto: Nombre real del usuario
 *   -rol: ADMIN, CAJERO o MESERO (determina qué puede hacer)
 *   -genero: MASCULINO o FEMENINO - para mensajes responsivos
 *   -idUsuario: ID numérico para consultas posteriores
 * 4. Cliente almacena el token en memoria (ConfiguracionCliente)
 * 5. Cliente incluye el token en todas las peticiones futuras:
 *    Header: Authorization: Bearer <token>
 * Ejemplo de respuesta JSON:
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "nombreUsuario": "admin",
 *   "nombreCompleto": "Administrador del Sistema",
 *   "rol": "ADMIN",
 *   "genero": "MASCULINO",
 *   "idUsuario": 1
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRespuestaDto {
    private String token;
    private String nombreUsuario;
    private String nombreCompleto;
    private String rol;
    private String genero;  //Para mensajes responsivos (ej: "Bienvenido, [usuario]" vs "Bienvenida, [usuario]")
    private Long idUsuario;
}
