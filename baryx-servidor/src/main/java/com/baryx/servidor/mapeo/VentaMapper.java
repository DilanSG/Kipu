/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.servidor.mapeo;

import com.baryx.common.dto.LineaVentaDto;
import com.baryx.common.dto.PagoDto;
import com.baryx.common.dto.VentaDto;
import com.baryx.servidor.modelo.entidad.LineaVenta;
import com.baryx.servidor.modelo.entidad.Pago;
import com.baryx.servidor.modelo.entidad.Venta;
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
