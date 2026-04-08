/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.servidor.mapeo;

import com.kipu.common.dto.LineaVentaDto;
import com.kipu.common.dto.PagoDto;
import com.kipu.common.dto.VentaDto;
import com.kipu.servidor.modelo.entidad.LineaVenta;
import com.kipu.servidor.modelo.entidad.Pago;
import com.kipu.servidor.modelo.entidad.Venta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface VentaMapper {

    @Mapping(target = "pagos", source = "pagos")
    @Mapping(target = "lineas", source = "lineas")
    VentaDto aDto(Venta venta);

    PagoDto pagoADto(Pago pago);

    LineaVentaDto lineaADto(LineaVenta linea);

    List<VentaDto> aListaDto(List<Venta> ventas);
}
