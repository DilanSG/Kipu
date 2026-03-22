/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.servicio;

import com.baryx.common.dto.LineaVentaDto;
import com.baryx.common.dto.PagoDto;
import com.baryx.common.dto.RegistrarVentaDto;
import com.baryx.common.dto.VentaDto;
import com.baryx.common.excepcion.ValidacionException;
import com.baryx.servidor.mapeo.VentaMapper;
import com.baryx.servidor.modelo.entidad.LineaVenta;
import com.baryx.servidor.modelo.entidad.Pago;
import com.baryx.servidor.modelo.entidad.Venta;
import com.baryx.servidor.repositorio.MesaRepositorio;
import com.baryx.servidor.repositorio.VentaRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaServicioImpl implements VentaServicio {

    private static final Logger logger = LoggerFactory.getLogger(VentaServicioImpl.class);

    private final VentaRepositorio ventaRepositorio;
    private final MesaRepositorio mesaRepositorio;
    private final VentaMapper ventaMapper;

    @Override
    @Transactional
    public VentaDto registrarVenta(RegistrarVentaDto dto, Long idCajero, String nombreCajero) {
        logger.info("Registrando venta - Mesa: {}, Total: {}", dto.getNumeroMesa(), dto.getTotal());

        // Validaciones
        if (dto.getPagos() == null || dto.getPagos().isEmpty()) {
            throw new ValidacionException("Debe registrar al menos un pago");
        }
        if (dto.getLineas() == null || dto.getLineas().isEmpty()) {
            throw new ValidacionException("La venta debe tener al menos una línea de producto");
        }

        BigDecimal totalPagos = dto.getPagos().stream()
                .map(PagoDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEsperado = dto.getTotal();

        // Validar que la suma de pagos cubra el total (la propina ya está incluida en el monto)
        if (totalPagos.compareTo(totalEsperado) != 0) {
            throw new ValidacionException(
                    String.format("La suma de pagos (%s) no coincide con el total (%s)",
                            totalPagos, totalEsperado));
        }

        // Crear entidad venta
        Venta venta = Venta.builder()
                .idMesa(dto.getIdMesa())
                .numeroMesa(dto.getNumeroMesa())
                .idMesero(dto.getIdMesero())
                .nombreMesero(dto.getNombreMesero())
                .idCajero(idCajero)
                .nombreCajero(nombreCajero)
                .subtotal(dto.getSubtotal())
                .impoconsumo(dto.getImpoconsumo())
                .propina(dto.getPropina())
                .total(dto.getTotal())
                .estado("COMPLETADA")
                .build();

        // Agregar pagos
        for (PagoDto pagoDto : dto.getPagos()) {
            Pago pago = Pago.builder()
                    .venta(venta)
                    .idMetodoPago(pagoDto.getIdMetodoPago())
                    .nombreMetodoPago(pagoDto.getNombreMetodoPago())
                    .monto(pagoDto.getMonto())
                    .propina(pagoDto.getPropina() != null ? pagoDto.getPropina() : BigDecimal.ZERO)
                    .build();
            venta.getPagos().add(pago);
        }

        // Agregar líneas de venta (snapshot del pedido)
        for (LineaVentaDto lineaDto : dto.getLineas()) {
            LineaVenta linea = LineaVenta.builder()
                    .venta(venta)
                    .idProducto(lineaDto.getIdProducto())
                    .nombreProducto(lineaDto.getNombreProducto())
                    .precioUnitario(lineaDto.getPrecioUnitario())
                    .cantidad(lineaDto.getCantidad() != null ? lineaDto.getCantidad() : 1)
                    .build();
            venta.getLineas().add(linea);
        }

        // Guardar venta (cascade persiste pagos y líneas)
        Venta ventaGuardada = ventaRepositorio.save(venta);
        logger.info("Venta registrada con ID: {}", ventaGuardada.getIdVenta());

        // Eliminar mesa (hard delete como en anularMesa, cascade elimina pedido y líneas pedido)
        if (dto.getIdMesa() != null) {
            mesaRepositorio.findById(dto.getIdMesa()).ifPresent(mesa -> {
                logger.info("Eliminando mesa {} tras facturación", mesa.getNumeroMesa());
                mesaRepositorio.delete(mesa);
            });
        }

        return ventaMapper.aDto(ventaGuardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaDto> listarVentasDesde(LocalDateTime desde) {
        logger.info("Listando ventas desde: {}", desde);
        List<Venta> ventas = ventaRepositorio.findByFechaCreacionGreaterThanEqualOrderByFechaCreacionDesc(desde);
        logger.info("Ventas encontradas: {}", ventas.size());
        return ventaMapper.aListaDto(ventas);
    }
}
