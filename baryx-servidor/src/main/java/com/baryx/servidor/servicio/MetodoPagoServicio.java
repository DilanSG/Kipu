/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.dto.MetodoPagoDto;
import java.util.List;

// Interfaz para el servicio de gestión de métodos de pago.
public interface MetodoPagoServicio {
    
    List<MetodoPagoDto> listarActivos();
    MetodoPagoDto crear(MetodoPagoDto dto);
    MetodoPagoDto actualizar(Long id, MetodoPagoDto dto);
    void eliminar(Long id);
}
