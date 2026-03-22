/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*DTO que representa un cliente Baryx conectado al servidor.
 *Contiene información sobre la conexión y el usuario autenticado.
 *Campos:
    *-ip: Dirección IP del cliente en la red LAN
    *-nombreUsuario: Nombre del usuario autenticado en ese cliente
    *-rol: Rol del usuario (ADMIN, CAJERO, MESERO)
    *-ultimaActividad: Timestamp (epoch millis) de la última actividad registrada
    *-esHost: Indica si es el mismo equipo que el servidor (modo host) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteConectadoDto {

    private String ip;
    private String nombreUsuario;
    private String rol;
    private long ultimaActividad;
    private boolean esHost;
    private String nombreCliente;
}
