/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.componente;

/**
 * Componente con renderizado continuo (Canvas + AnimationTimer).
 * Se registra en {@link MotorAnimaciones} para compartir un único timer global.
 */
public interface Animable {
    /** Renderiza un frame en el Canvas del componente. */
    void renderizar();

    /** @return true si el componente está visible y debe incluirse en el ciclo de render. */
    boolean esVisible();

    /** Degrada el componente a una representación estática (CSS fallback). */
    void degradarAEstatico();

    /** Restaura el componente a su modo animado. */
    void restaurarAnimacion();
}
