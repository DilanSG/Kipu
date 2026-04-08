/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.servicio;

import com.kipu.common.dto.ClienteConectadoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*Servicio en memoria que registra y rastrea los clientes conectados al servidor en la red LAN.
 *Se basa en la dirección IP del cliente y se actualiza con cada petición autenticada*/
@Service
public class RegistroClientesServicio {
    private static final Logger logger = LoggerFactory.getLogger(RegistroClientesServicio.class);
    private static final long TIMEOUT_INACTIVIDAD_MS = 2 * 60 * 1000;
    private final Map<String, ClienteConectadoDto> clientesActivos = new ConcurrentHashMap<>();
    private String ipLocalServidor;

    /**
     * Registra o actualiza un cliente conectado.
     * Usa el nombreCliente como clave del mapa (en vez de IP) para
     * soportar correctamente escenarios con NAT (ej: VirtualBox)
     * donde múltiples clientes pueden compartir la misma IP visible.
     *
     * @param ipRequest   IP detectada por request.getRemoteAddr()
     * @param nombreUsuario Nombre del usuario autenticado
     * @param rol         Rol del usuario (ADMIN, CAJERO, MESERO)
     * @param nombreCliente Nombre del equipo enviado por X-Client-Name
     * @param ipReportada   IP real del cliente enviada por X-Client-IP (puede ser null)
     */
    public void registrar(String ipRequest, String nombreUsuario, String rol,
                          String nombreCliente, String ipReportada) {
        // Normalizar IP del request: "0:0:0:0:0:0:0:1" → "127.0.0.1"
        if ("0:0:0:0:0:0:0:1".equals(ipRequest) || "::1".equals(ipRequest)) {
            ipRequest = "127.0.0.1";
        }

        // Usar la IP reportada por el cliente (X-Client-IP) para display y detección host.
        // Esto evita problemas con NAT donde request.getRemoteAddr() devuelve 127.0.0.1
        // para clientes remotos conectados a través de VirtualBox u otro NAT.
        String ipDisplay = (ipReportada != null && !ipReportada.isEmpty()) ? ipReportada : ipRequest;

        // Determinar si es host comparando AMBAS IPs
        boolean esHost = esIpLocal(ipDisplay) || esIpLocal(ipRequest);

        // Clave del mapa: nombreCliente si está disponible, sino IP del request.
        // Esto permite rastrear múltiples clientes detrás del mismo NAT (misma IP visible)
        String clave = (nombreCliente != null && !nombreCliente.isEmpty())
                ? nombreCliente : ipRequest;

        clientesActivos.put(clave, ClienteConectadoDto.builder()
                .ip(ipDisplay)
                .nombreUsuario(nombreUsuario)
                .rol(rol)
                .ultimaActividad(System.currentTimeMillis())
                .esHost(esHost)
                .nombreCliente(nombreCliente != null ? nombreCliente : "")
                .build());
    }

    public List<ClienteConectadoDto> obtenerClientesActivos() {
        long ahora = System.currentTimeMillis();

        clientesActivos.entrySet().removeIf(
                entry -> (ahora - entry.getValue().getUltimaActividad()) > TIMEOUT_INACTIVIDAD_MS);

        return clientesActivos.values().stream()
                .sorted((a, b) -> Long.compare(b.getUltimaActividad(), a.getUltimaActividad()))
                .collect(Collectors.toList());
    }

    private boolean esIpLocal(String ip) {
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip)) {
            return true;
        }
        try {
            if (ipLocalServidor == null) {
                ipLocalServidor = InetAddress.getLocalHost().getHostAddress();
            }
            return ip.equals(ipLocalServidor);
        } catch (Exception e) {
            return false;
        }
    }
}
