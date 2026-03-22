/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.controlador;

/**
 * Interfaz para controladores de subvistas que requieren referencia al MenuPrincipalController.
 * Elimina la necesidad de reflexión para inyectar la referencia.
 */
public interface SubvistaController {
    void setMenuPrincipal(MenuPrincipalController menuPrincipal);
}
