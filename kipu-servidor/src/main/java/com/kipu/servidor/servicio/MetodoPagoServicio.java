/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.servicio;

import com.kipu.common.dto.MetodoPagoDto;
import java.util.List;

// Interfaz para el servicio de gestión de métodos de pago.
public interface MetodoPagoServicio {
    
    List<MetodoPagoDto> listarActivos();
    MetodoPagoDto crear(MetodoPagoDto dto);
    MetodoPagoDto actualizar(Long id, MetodoPagoDto dto);
    void eliminar(Long id);
}
