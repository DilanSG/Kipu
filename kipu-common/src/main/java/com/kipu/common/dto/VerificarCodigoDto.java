/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.*;

/*DTO para verificar la existencia de un código de usuario (mesero/cajero).
Campos:
- existe: Indica si el código existe en la base de datos
- nombreCompleto: Nombre completo del usuario asociado al código (si existe)
- activo: Indica si el usuario asociado al código está activo o inactivo
Uso:
- El cliente envía un código al servidor para verificar su existencia.
- El servidor responde con este DTO indicando si el código existe, el nombre del usuario y su estado (activo/inactivo). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificarCodigoDto {
    
    private boolean existe;
    private String nombreCompleto;
    private boolean activo;
}
