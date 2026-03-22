/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.dto.LineaPedidoDto;
import com.baryx.common.dto.MesaActivaDto;
import com.baryx.common.dto.MesaConPedidoDto;
import com.baryx.common.dto.PedidoDto;
import com.baryx.common.excepcion.RecursoNoEncontradoException;
import com.baryx.servidor.mapeo.MesaMapper;
import com.baryx.servidor.modelo.entidad.LineaPedido;
import com.baryx.servidor.modelo.entidad.Mesa;
import com.baryx.servidor.modelo.entidad.Pedido;
import com.baryx.servidor.modelo.entidad.Producto;
import com.baryx.servidor.modelo.entidad.Usuario;
import com.baryx.servidor.repositorio.MesaRepositorio;
import com.baryx.servidor.repositorio.PedidoRepositorio;
import com.baryx.servidor.repositorio.ProductoRepositorio;
import com.baryx.servidor.repositorio.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/*Este servicio maneja toda la lógica relacionada con las mesas y sus pedidos asociados.
*Funcionalidades principales:
*-Obtener mesa con pedido por ID de mesa
*-Guardar pedido para una mesa (crear o actualizar)
*-Obtener mesas activas por ID de mesero
*-Crear o obtener mesa por número de mesa y ID de mesero (sin crear en BD hasta que se agregue el primer producto)
*-Buscar mesa por número de mesa (sin crearla mesa si no existe)
*-Anular mesa eliminando completamente de BD (hard delete)*/
@Service
@Transactional
@RequiredArgsConstructor
public class MesaServicioImpl implements MesaServicio {
    
    private static final Logger logger = LoggerFactory.getLogger(MesaServicioImpl.class);
    
    private final MesaRepositorio mesaRepositorio;
    private final PedidoRepositorio pedidoRepositorio;
    private final ProductoRepositorio productoRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final MesaMapper mesaMapper;
    
    @Override
    @Transactional(readOnly = true)
    public MesaConPedidoDto obtenerMesaConPedido(Long idMesa) {
        logger.debug("Obteniendo mesa con pedido, ID: {}", idMesa);
        
        Mesa mesa = mesaRepositorio.findByIdWithPedido(idMesa)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Mesa", idMesa));
        
        return mesaMapper.mesaADto(mesa);
    }
    
    @Override
    public MesaConPedidoDto guardarPedido(String numeroMesa, Long idMesero, PedidoDto pedidoDto) {
        logger.info("Guardando pedido para mesa {} (mesero ID: {})", numeroMesa, idMesero);
        
        Optional<Mesa> mesaOpt = mesaRepositorio.findByNumeroMesa(numeroMesa);
        Mesa mesa;
        
        if (mesaOpt.isPresent()) {
            mesa = mesaOpt.get();
            logger.info("Mesa {} encontrada en BD con mesero: {}", numeroMesa, 
                    mesa.getMesero() != null ? mesa.getMesero().getNombreCompleto() : "N/A");
        } else {
            if (idMesero == null) {
                throw new IllegalArgumentException("El ID del mesero es requerido para crear una mesa nueva");
            }
            
            logger.info("Creando nueva mesa {} en BD", numeroMesa);
            
            Usuario mesero = usuarioRepositorio.findById(idMesero)
                    .orElseThrow(() -> RecursoNoEncontradoException.porId("Usuario", idMesero));
            
            mesa = Mesa.builder()
                    .numeroMesa(numeroMesa)
                    .mesero(mesero)
                    .estado("OCUPADA")
                    .build();
        }

        Pedido pedido = mesa.getPedidoActual();
        if (pedido == null) {
            logger.info("Creando nuevo pedido para mesa {}", mesa.getNumeroMesa());
            pedido = Pedido.builder()
                    .mesa(mesa)
                    .fechaCreacion(LocalDateTime.now())
                    .estado("ACTIVO")
                    .build();
            mesa.setPedidoActual(pedido);
        }

        pedido.getLineas().clear();
        
        for (LineaPedidoDto lineaDto : pedidoDto.getLineas()) {
            Producto producto = productoRepositorio.findById(lineaDto.getIdProducto())
                    .orElseThrow(() -> RecursoNoEncontradoException.porId("Producto", lineaDto.getIdProducto()));
            
            LineaPedido linea = LineaPedido.builder()
                    .producto(producto)
                    .nombreProducto(lineaDto.getNombreProducto())
                    .precioUnitario(lineaDto.getPrecioUnitario())
                    .timestamp(lineaDto.getTimestamp())
                    .build();
            
            pedido.agregarLinea(linea);
        }

        pedido.calcularTotal();

        if (pedidoDto.getLineas().size() == 1) {
            pedido.setFechaCreacion(pedidoDto.getLineas().get(0).getTimestamp());
        }

        mesa.setEstado("OCUPADA");
        
        mesa = mesaRepositorio.save(mesa);
        
        logger.info("Pedido guardado exitosamente para mesa {}", mesa.getNumeroMesa());
        
        return mesaMapper.mesaADto(mesa);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<MesaActivaDto> obtenerMesasActivas(Long idMesero) {
        logger.debug("Obteniendo mesas activas. Mesero ID: {}", idMesero);
        
        List<Mesa> mesas = mesaRepositorio.findMesasActivas(idMesero);
        
        return mesaMapper.mesasAMesasActivasDto(mesas);
    }
    
    @Override
    public MesaConPedidoDto crearOObtenerMesa(String numeroMesa, Long idMesero) {
        logger.info("Buscando mesa {} para mesero ID {}", numeroMesa, idMesero);
        
        Optional<Mesa> mesaOpt = mesaRepositorio.findByNumeroMesa(numeroMesa);
        
        if (mesaOpt.isPresent()) {
            Mesa mesa = mesaOpt.get();

            if ("OCUPADA".equals(mesa.getEstado())) {

                if (!mesa.getMesero().getIdUsuario().equals(idMesero)) {
                    logger.warn("Mesa {} ya está ocupada por otro mesero", numeroMesa);
                    throw new IllegalStateException("Mesa ocupada por otro mesero");
                }
                logger.info("Mesa {} encontrada con pedido activo", numeroMesa);
                return mesaMapper.mesaADto(mesa);
            }
        }
        
        logger.info("Mesa {} no existe en BD - devolviendo DTO vacío", numeroMesa);
        
        MesaConPedidoDto dto = new MesaConPedidoDto();
        dto.setNumeroMesa(numeroMesa);
        dto.setIdMesero(idMesero);
        dto.setPedido(null);
        return dto;
    }
    @Override
    @Transactional(readOnly = true)
    public MesaConPedidoDto buscarMesaPorNumero(String numeroMesa) {
        logger.debug("Buscando mesa por número: {}", numeroMesa);
        
        return mesaRepositorio.findByNumeroMesa(numeroMesa)
                .map(mesaMapper::mesaADto)
                .orElse(null);
    }

    @Override
    @Transactional
    public void anularMesa(Long idMesa) {
        logger.info("Eliminando mesa ID: {} completamente de BD", idMesa);
        
        Mesa mesa = mesaRepositorio.findById(idMesa)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Mesa", idMesa));
        
        String numeroMesa = mesa.getNumeroMesa();
        
        mesaRepositorio.delete(mesa);
        
        logger.info("Mesa {} eliminada exitosamente de BD", numeroMesa);
    }
}
