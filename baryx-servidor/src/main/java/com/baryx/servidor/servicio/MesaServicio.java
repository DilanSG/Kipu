/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.dto.MesaActivaDto;
import com.baryx.common.dto.MesaConPedidoDto;
import com.baryx.common.dto.PedidoDto;

import java.util.List;

// Interfaz para el servicio de gestión de mesas y pedidos.
public interface MesaServicio {
    
    MesaConPedidoDto obtenerMesaConPedido(Long idMesa);
    MesaConPedidoDto guardarPedido(String numeroMesa, Long idMesero, PedidoDto pedidoDto);
    List<MesaActivaDto> obtenerMesasActivas(Long idMesero);
    MesaConPedidoDto crearOObtenerMesa(String numeroMesa, Long idMesero);
    MesaConPedidoDto buscarMesaPorNumero(String numeroMesa);
    void anularMesa(Long idMesa);
}
