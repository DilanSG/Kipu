/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador.configuracion;

/**
 * Interfaz para las herramientas/modales de configuración del sistema.
 *
 * Cada herramienta implementa esta interfaz para ser invocada por el
 * panel de configuración. El patrón Delegate permite separar la lógica
 * de cada modal en su propia clase, manteniendo el controlador principal ligero.
 *
 * Las herramientas reciben un {@link GestorModales} en su constructor
 * que provee la infraestructura compartida para mostrar/cerrar modales,
 * crear headers, separadores y manejar animaciones.
 *
 * @see GestorModales
 * @see ConfiguracionHerramientasController
 */
@FunctionalInterface
public interface ModalHerramienta {

    /**
     * Abre el modal de la herramienta.
     * Construye el contenido visual y lo muestra usando el GestorModales.
     */
    void abrir();
}
