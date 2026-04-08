/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/* DTO para login con código PIN 
*Campos:
*- codigo: Código de empleado (2 dígitos: 01-99)
*- pin: PIN de 4 dígitos
*Uso:
*- El cliente envía este DTO al servidor para intentar iniciar sesión con PIN.
*- El servidor valida el código y PIN, y responde con un token de autenticación si son correctos, o con un error si no lo son. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginPinDto {
    
    private String codigo; 
    private String pin;    
}
