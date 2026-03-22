/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/

package com.baryx.servidor.servicio;

import com.baryx.common.dto.AuthRespuestaDto;
import com.baryx.common.dto.LoginDto;

// Interfaz para el servicio de autenticación.
public interface AutenticacionServicio {
    AuthRespuestaDto login(LoginDto loginDto);
}
