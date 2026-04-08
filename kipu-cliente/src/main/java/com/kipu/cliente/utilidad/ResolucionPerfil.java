/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

/**
 * Perfiles de resolución predefinidos para adaptar la UI a cada tipo de pantalla.
 * Cada perfil define la resolución lógica de diseño, factor de escala de texto
 * y clases CSS que se aplican al contenedor raíz.
 */
public enum ResolucionPerfil {

    // 4:3 y 5:4 — Monitores cuadrados / CRT / cajas registradoras antiguas
    SMALL_4_3(
            "ctrl.resolucion.small_4_3",
            1024, 768, 0.85,
            "res-small"
    ),
    SXGA_5_4(
            "ctrl.resolucion.sxga_5_4",
            1280, 1024, 0.88,
            "res-tablet"
    ),

    // 16:9 — Resoluciones widescreen estándar
    HD_16_9(
            "ctrl.resolucion.hd_16_9",
            1366, 768, 0.90,
            "res-hd"
    ),
    FHD_16_9(
            "ctrl.resolucion.fhd_16_9",
            1920, 1080, 1.00,
            "res-fhd"
    ),
    QHD_16_9(
            "ctrl.resolucion.qhd_16_9",
            2560, 1440, 1.25,
            "res-qhd"
    ),
    UHD_16_9(
            "ctrl.resolucion.uhd_16_9",
            3840, 2160, 1.80,
            "res-4k"
    ),

    // 16:10 — MacBooks, monitores profesionales
    HD_PLUS_16_10(
            "ctrl.resolucion.hd_plus_16_10",
            1440, 900, 0.92,
            "res-hd"
    ),
    FHD_16_10(
            "ctrl.resolucion.fhd_16_10",
            1920, 1200, 1.00,
            "res-fhd"
    ),

    // 21:9 — Ultrawide
    ULTRAWIDE_21_9(
            "ctrl.resolucion.ultrawide_21_9",
            2560, 1080, 1.00,
            "res-fhd"
    ),
    ULTRAWIDE_QHD(
            "ctrl.resolucion.ultrawide_qhd",
            3440, 1440, 1.15,
            "res-qhd"
    );

    private final String claveI18n;
    private final double anchoDiseno;
    private final double altoDiseno;
    private final double factorTexto;
    private final String claseResolucion;

    ResolucionPerfil(String claveI18n, double anchoDiseno, double altoDiseno,
                     double factorTexto, String claseResolucion) {
        this.claveI18n = claveI18n;
        this.anchoDiseno = anchoDiseno;
        this.altoDiseno = altoDiseno;
        this.factorTexto = factorTexto;
        this.claseResolucion = claseResolucion;
    }

    public String getClaveI18n() {
        return claveI18n;
    }

    /** Nombre localizado del perfil (ej: "Full HD (1920×1080)") */
    public String getNombreLocalizado() {
        return IdiomaUtil.obtener(claveI18n);
    }

    /** Ancho de la resolución lógica de diseño en píxeles */
    public double getAnchoDiseno() {
        return anchoDiseno;
    }

    /** Alto de la resolución lógica de diseño en píxeles */
    public double getAltoDiseno() {
        return altoDiseno;
    }

    /**
     * Multiplicador de -fx-font-size global respecto al diseño base (14px).
     * Permite que toda la tipografía escale proporcionalmente al perfil.
     */
    public double getFactorTexto() {
        return factorTexto;
    }

    /** Ratio de aspecto del perfil (ancho/alto) */
    public double getRatioAspecto() {
        return anchoDiseno / altoDiseno;
    }

    /** Clase CSS de breakpoint por ancho (res-fhd, res-hd, etc.) */
    public String getClaseResolucion() {
        return claseResolucion;
    }

    /**
     * Calcula la distancia de este perfil hacia una resolución real.
     * Combina diferencia de resolución + diferencia de ratio para seleccionar
     * el perfil más adecuado ante detección automática.
     *
     * @param anchoReal Ancho lógico de la pantalla (ya ajustado por escala del OS)
     * @param altoReal  Alto lógico de la pantalla
     * @return Puntuación de distancia — menor = mejor match
     */
    public double calcularDistancia(double anchoReal, double altoReal) {
        double ratioReal = anchoReal / altoReal;
        double deltaAncho = (anchoReal - anchoDiseno) / 100.0;
        double deltaAlto = (altoReal - altoDiseno) / 100.0;
        double deltaRatio = (ratioReal - getRatioAspecto()) * 10.0;
        return Math.sqrt(deltaAncho * deltaAncho + deltaAlto * deltaAlto + deltaRatio * deltaRatio);
    }
}
