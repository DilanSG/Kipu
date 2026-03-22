/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.excepcion;

import com.baryx.common.constantes.Constantes;

/* Excepción lanzada cuando un recurso solicitado no se encuentra en el sistema.
 * Se usa para indicar que un ID, nombre o referencia a un recurso no existe.
 * Código HTTP asociado: 404 NOT FOUND
 * Subtipos:
 * - UsuarioNoEncontradoException: cuando un usuario con cierto ID o nombre no existe
 * - ProductoNoEncontradoException: cuando un producto con cierto ID o código no existe
 * - PedidoNoEncontradoException: cuando un pedido con cierto ID no existe */
public class RecursoNoEncontradoException extends BaryxException {
    
    // Constructor con mensaje para indicar qué recurso no se encontró. El mensaje debe ser claro y específico para facilitar la identificación del error.
    public RecursoNoEncontradoException(String mensaje) {
        super(Constantes.CodigosError.RECURSO_NO_ENCONTRADO, mensaje);
    }
    
    // Constructor con mensaje y causa original para mantener el stack trace completo y facilitar la depuración de errores relacionados con recursos no encontrados, especialmente cuando se envuelven excepciones técnicas (SQLException, NoSuchElementException) en esta excepción de dominio con significado específico.
    public RecursoNoEncontradoException(String mensaje, Throwable causa) {
        super(Constantes.CodigosError.RECURSO_NO_ENCONTRADO, mensaje, causa);
    }
    
    // Factory method para crear una excepción de recurso no encontrado basada en un ID, con un mensaje formateado que indique claramente qué tipo de recurso y qué ID no se encontró, lo que facilita la identificación del error.
    public static RecursoNoEncontradoException porId(String nombreRecurso, Long id) {
        return new RecursoNoEncontradoException(
            String.format("%s con ID %d no encontrado", nombreRecurso, id)
        );
    }
}
