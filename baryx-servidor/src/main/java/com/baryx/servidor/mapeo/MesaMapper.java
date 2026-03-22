/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.mapeo;

import com.baryx.common.dto.LineaPedidoDto;
import com.baryx.common.dto.MesaActivaDto;
import com.baryx.common.dto.MesaConPedidoDto;
import com.baryx.common.dto.PedidoDto;
import com.baryx.servidor.modelo.entidad.LineaPedido;
import com.baryx.servidor.modelo.entidad.Mesa;
import com.baryx.servidor.modelo.entidad.Pedido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

// Mapeador para las entidades relacionadas con Mesa, incluyendo Pedido y LineaPedido.
@Mapper(componentModel = "spring")
public interface MesaMapper {
    
    // Mapea una LineaPedido a LineaPedidoDto.
    @Mapping(source = "producto.idProducto", target = "idProducto")
    LineaPedidoDto lineaPedidoADto(LineaPedido lineaPedido);
    
    // Mapea un LineaPedidoDto a LineaPedido, ignorando campos sensibles y de auditoría.
    @Mapping(target = "idLineaPedido", ignore = true)
    @Mapping(target = "pedido", ignore = true)
    @Mapping(target = "producto", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "activo", ignore = true)
    LineaPedido lineaPedidoDtoAEntidad(LineaPedidoDto dto);
    
    List<LineaPedidoDto> lineasPedidoADto(List<LineaPedido> lineas);
    List<LineaPedido> lineasPedidoDtoAEntidad(List<LineaPedidoDto> dtos);
    
    // Mapea un Pedido a PedidoDto, incluyendo las líneas del pedido.
    @Mapping(source = "lineas", target = "lineas")
    PedidoDto pedidoADto(Pedido pedido);
    
    @Mapping(target = "idPedido", ignore = true)
    @Mapping(target = "mesa", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "activo", ignore = true)
    Pedido pedidoDtoAEntidad(PedidoDto dto);
    
    // Mapea una Mesa a MesaConPedidoDto, incluyendo información del mesero y el pedido actual si existe.
    @Mapping(source = "mesero.idUsuario", target = "mesero.id")
    @Mapping(source = "mesero.nombreCompleto", target = "mesero.nombre")
    @Mapping(source = "mesero.idUsuario", target = "idMesero")
    @Mapping(source = "pedidoActual", target = "pedido")
    MesaConPedidoDto mesaADto(Mesa mesa);
    
    // Mapea una Mesa a MesaActivaDto, incluyendo información del mesero y el pedido actual si existe, y calculando la cantidad de items en el pedido.
    @Mapping(source = "mesero.idUsuario", target = "meseroId")
    @Mapping(source = "mesero.nombreCompleto", target = "meseroNombre")
    @Mapping(source = "pedidoActual.total", target = "total")
    @Mapping(source = "pedidoActual.fechaCreacion", target = "fechaCreacion")
    @Mapping(target = "cantidadItems", expression = "java(mesa.getPedidoActual() != null ? mesa.getPedidoActual().getLineas().size() : 0)")
    MesaActivaDto mesaAMesaActivaDto(Mesa mesa);
    
    List<MesaActivaDto> mesasAMesasActivasDto(List<Mesa> mesas);
}
