/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.servicio;

import com.kipu.common.dto.ProductoDto;
import java.util.List;

public interface ProductoServicio {
    List<ProductoDto> listarActivos();
    ProductoDto buscarPorId(Long id);
    List<ProductoDto> buscarPorCategoria(Long idCategoria);
    ProductoDto crear(ProductoDto dto);
    ProductoDto actualizar(Long id, ProductoDto dto);
    void eliminar(Long id);
}
