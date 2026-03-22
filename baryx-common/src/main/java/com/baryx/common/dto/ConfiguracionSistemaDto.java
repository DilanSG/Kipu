/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular. */
package com.baryx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para transferir configuraciones globales del sistema.
 *
 * Representa un par clave-valor de configuración que se comparte
 * entre todos los clientes conectados al servidor.
 *
 * Ejemplo de uso:
 * <pre>
 *   ConfiguracionSistemaDto config = ConfiguracionSistemaDto.builder()
 *       .clave("idioma")
 *       .valor("en")
 *       .build();
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionSistemaDto {

    /** Identificador único de la configuración */
    private Long idConfiguracion;

    /** Clave de la configuración (ej: "idioma") */
    private String clave;

    /** Valor actual de la configuración (ej: "es") */
    private String valor;

    /** Descripción legible de la configuración */
    private String descripcion;
}
