/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.utilidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;

/**
 * Utilidad central de internacionalización (i18n) del cliente Kipu.
 *
 * Carga y administra un {@link ResourceBundle} con los textos de la interfaz
 * según el {@link Locale} configurado. Soporta español (por defecto), inglés y portugués.
 *
 * Uso:
 * <pre>
 *   // Inicializar con el locale deseado (normalmente al arrancar la app)
 *   IdiomaUtil.inicializar(Locale.of("es", "ES"));
 *
 *   // Obtener un texto traducido
 *   String texto = IdiomaUtil.obtener("login.bienvenido");
 *
 *   // Texto con parámetros (MessageFormat)
 *   String msg = IdiomaUtil.obtener("idioma.cambiado", "Español");
 *   // → "Idioma cambiado a Español. Los demás clientes..."
 *
 *   // Inyectar en FXMLLoader para que funcione la sintaxis %clave en FXML
 *   cargador.setResources(IdiomaUtil.obtenerBundle());
 * </pre>
 *
 * @see java.util.ResourceBundle
 * @see java.text.MessageFormat
 */
public final class IdiomaUtil {

    private static final Logger logger = LoggerFactory.getLogger(IdiomaUtil.class);

    /** Nombre base del ResourceBundle (ruta relativa sin extensión) */
    private static final String BUNDLE_BASE = "i18n.mensajes";

    /** Locale por defecto del sistema: español */
    private static final Locale LOCALE_POR_DEFECTO = Locale.of("es", "ES");

    /** Locale activo del sistema */
    private static volatile Locale localeActual = LOCALE_POR_DEFECTO;

    /** ResourceBundle activo cargado */
    private static volatile ResourceBundle bundleActual;

    /** Idiomas soportados por el sistema */
    private static final List<IdiomaDisponible> IDIOMAS_DISPONIBLES = List.of(
        new IdiomaDisponible("es", "Español", Locale.of("es", "ES")),
        new IdiomaDisponible("en", "English", Locale.of("en", "US")),
        new IdiomaDisponible("pt", "Português", Locale.of("pt", "BR"))
    );

    // Constructor privado — clase de utilidad
    private IdiomaUtil() {
        throw new UnsupportedOperationException("Clase de utilidad no instanciable");
    }

    // Carga inicial con locale por defecto
    static {
        inicializar(LOCALE_POR_DEFECTO);
    }

    /**
     * Inicializa el sistema de idioma con el locale indicado.
     * Carga el ResourceBundle correspondiente desde i18n/mensajes_{lang}.properties.
     *
     * @param locale Locale a cargar (ej: Locale.of("en", "US"))
     */
    public static void inicializar(Locale locale) {
        if (locale == null) {
            locale = LOCALE_POR_DEFECTO;
        }
        try {
            bundleActual = ResourceBundle.getBundle(BUNDLE_BASE, locale);
            localeActual = locale;
            logger.info("Idioma inicializado: {} ({})", locale.getDisplayLanguage(), locale);
        } catch (MissingResourceException e) {
            logger.warn("No se encontró bundle para locale {}, usando español por defecto", locale);
            bundleActual = ResourceBundle.getBundle(BUNDLE_BASE, LOCALE_POR_DEFECTO);
            localeActual = LOCALE_POR_DEFECTO;
        }
    }

    /**
     * Obtiene el texto traducido para la clave indicada.
     *
     * @param clave Clave del mensaje (ej: "login.bienvenido")
     * @return Texto traducido, o la clave entre corchetes si no existe (ej: "[clave.no.existe]")
     */
    public static String obtener(String clave) {
        if (clave == null || clave.isEmpty()) {
            return "";
        }
        try {
            return bundleActual.getString(clave);
        } catch (MissingResourceException e) {
            logger.trace("Clave de idioma no encontrada: {}", clave);
            return "[" + clave + "]";
        }
    }

    /**
     * Obtiene el texto traducido con parámetros sustituidos.
     * Usa {@link MessageFormat} para reemplazar placeholders {0}, {1}, etc.
     *
     * @param clave Clave del mensaje (ej: "idioma.cambiado")
     * @param args  Valores para sustituir en los placeholders
     * @return Texto formateado, o la clave entre corchetes si no existe
     */
    public static String obtener(String clave, Object... args) {
        String patron = obtener(clave);
        if (patron.startsWith("[") && patron.endsWith("]")) {
            return patron;
        }
        try {
            return MessageFormat.format(patron, args);
        } catch (IllegalArgumentException e) {
            logger.warn("Error formateando mensaje '{}': {}", clave, e.getMessage());
            return patron;
        }
    }

    /**
     * Retorna el ResourceBundle activo, para inyectar en FXMLLoader.
     *
     * @return ResourceBundle actual con los textos del idioma activo
     */
    public static ResourceBundle obtenerBundle() {
        return bundleActual;
    }

    /**
     * Cambia el idioma del sistema. Recarga el ResourceBundle con el nuevo locale.
     *
     * @param nuevoLocale Nuevo locale a aplicar
     */
    public static void cambiarIdioma(Locale nuevoLocale) {
        // Limpiar caché de ResourceBundle para forzar recarga
        ResourceBundle.clearCache();
        inicializar(nuevoLocale);
    }

    /**
     * Cambia el idioma a partir del código de idioma (ej: "es", "en", "pt").
     *
     * @param codigoIdioma Código ISO 639-1 del idioma
     */
    public static void cambiarIdioma(String codigoIdioma) {
        if (codigoIdioma == null || codigoIdioma.isEmpty()) {
            return;
        }
        for (IdiomaDisponible idioma : IDIOMAS_DISPONIBLES) {
            if (idioma.getCodigo().equalsIgnoreCase(codigoIdioma)) {
                cambiarIdioma(idioma.getLocale());
                return;
            }
        }
        logger.warn("Código de idioma no soportado: {}", codigoIdioma);
    }

    /**
     * Retorna el locale activo del sistema.
     *
     * @return Locale actualmente configurado
     */
    public static Locale getLocaleActual() {
        return localeActual;
    }

    /**
     * Retorna la lista de idiomas disponibles en el sistema.
     *
     * @return Lista inmutable de idiomas soportados
     */
    public static List<IdiomaDisponible> getIdiomasDisponibles() {
        return IDIOMAS_DISPONIBLES;
    }

    /**
     * Representa un idioma disponible en el sistema.
     */
    public static final class IdiomaDisponible {
        private final String codigo;
        private final String nombre;
        private final Locale locale;

        public IdiomaDisponible(String codigo, String nombre, Locale locale) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.locale = locale;
        }

        /** Código ISO 639-1 (ej: "es", "en", "pt") */
        public String getCodigo() { return codigo; }

        /** Nombre del idioma en su propio idioma (ej: "Español", "English") */
        public String getNombre() { return nombre; }

        /** Locale Java correspondiente */
        public Locale getLocale() { return locale; }

        @Override
        public String toString() { return nombre; }
    }
}
