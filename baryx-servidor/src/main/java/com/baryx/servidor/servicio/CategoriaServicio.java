/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.servicio;

import com.baryx.common.dto.CategoriaDto;
import java.util.List;

// Interfaz para el servicio de gestión de categorías.
public interface CategoriaServicio {
    List<CategoriaDto> listarActivas();
    CategoriaDto buscarPorId(Long id);
    CategoriaDto crear(CategoriaDto dto);
    CategoriaDto actualizar(Long id, CategoriaDto dto);
    void eliminar(Long id);
}
