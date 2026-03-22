/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.baryx.cliente.utilidad;

import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.common.dto.AuthRespuestaDto;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad para navegación entre vistas (pantallas) de la aplicación JavaFX.
 * 
 * Esta clase facilita el cambio de una vista a otra sin duplicar código.
 * 
 * Conceptos clave:
 * - Vista: Archivo .fxml con la definición XML de la interfaz
 * - Controlador: Clase Java que maneja los eventos de esa vista
 * - Scene: El contenedor visual que muestra la vista
 * - Stage: La ventana donde se muestra la Scene
 * 
 * Flujo de navegación:
 * 1. Usuario hace clic en un botón (ej: "Iniciar Sesión")
 * 2. Controlador llama a NavegacionUtil.cambiarEscena()
 * 3. Se carga el archivo FXML de la nueva vista
 * 4. Se reemplaza la Scene actual con la nueva
 * 5. Usuario ve la nueva pantalla
 * 
 * Ejemplo de navegación en el sistema:
 * 
 * Login → Vista según rol:
 * - MESERO → principal-mesero.fxml (comandera)
 * - CAJERO → principal-cajero.fxml (punto de venta)
 * - ADMIN → principal-admin.fxml (panel administrativo)
 * 
 * Uso en el código:
 * 
 * // Desde un controlador, después de login exitoso
 * Stage stage = (Stage) btnLogin.getScene().getWindow();
 * 
 * if (rol.equals("MESERO")) {
 *     NavegacionUtil.cambiarEscena(
 *         stage,
 *         "/vista/principal-mesero.fxml",
 *         "Baryx - Comandera"
 *     );
 * } else if (rol.equals("CAJERO")) {
 *     NavegacionUtil.cambiarEscena(
 *         stage,
 *         "/vista/principal-cajero.fxml",
 *         "Baryx - Punto de Venta"
 *     );
 * } else if (rol.equals("ADMIN")) {
 *     NavegacionUtil.cambiarEscena(
 *         stage,
 *         "/vista/principal-admin.fxml",
 *         "Baryx - Administración"
 *     );
 * }
 * 
 * Parámetros:
 * - stage: La ventana actual (Stage) que se va a modificar
 * - rutaFxml: Ruta al archivo FXML (relativa a resources)
 * - titulo: Nuevo título de la ventana
 * 
 * Manejo de errores:
 * - Si el archivo FXML no existe: lanza IOException
 * - Si el FXML tiene errores de sintaxis: lanza LoadException
 * - Estos errores deben manejarse en el controlador que llama a esta utilidad
 * 
 * Ventajas de centralizar la navegación:
 * - Código más limpio en los controladores
 * - Fácil añadir transiciones animadas en el futuro
 * - Fácil agregar logs de navegación (auditoría)
 * - Manejo consistente de errores
 */
public class NavegacionUtil {

    private static final Logger logger = LoggerFactory.getLogger(NavegacionUtil.class);

    /**
     * Usuario actualmente autenticado en el sistema.
     * Se almacena aquí después del login exitoso para acceso global.
     */
    private static AuthRespuestaDto usuarioActual;
    
    /**
     * Historial de navegación para breadcrumbs.
     * Almacena las últimas vistas visitadas.
     */
    private static final List<Breadcrumb> historialNavegacion = new ArrayList<>();
    
    /**
     * Clase interna para representar un breadcrumb (miga de pan).
     */
    public static class Breadcrumb {
        private final String titulo;
        private final String rutaFxml;
        
        public Breadcrumb(String titulo, String rutaFxml) {
            this.titulo = titulo;
            this.rutaFxml = rutaFxml;
        }
        
        public String getTitulo() {
            return titulo;
        }
        
        public String getRutaFxml() {
            return rutaFxml;
        }
    }

    /**
     * Cambia la vista actual cargando un nuevo FXML dentro del shell raíz.
     * No crea una nueva Scene — reutiliza la Scene única de la aplicación.
     * Agrega la vista al historial de navegación para breadcrumbs.
     * La limpieza del contenido anterior la realiza el ShellController.
     */
    public static void cambiarEscena(Stage stage, String rutaFxml, String titulo) throws IOException {
        // Agregar al historial de navegación
        agregarAlHistorial(titulo, rutaFxml);

        // Cargar la vista dentro del shell (limpia contenido anterior internamente)
        com.baryx.cliente.controlador.ShellController.getInstancia().cargarVista(rutaFxml);

        stage.setTitle(titulo);
    }
    
    /**
     * Limpia recursivamente un nodo y sus hijos.
     * 
     * ENFOQUE: Limpieza conservadora que no interfiere con JavaFX internals.
     * 
     * QUÉ LIMPIA:
     * - Detiene animaciones (FondoAnimado, etc.) mediante reflection
     * - Remueve event handlers de mouse/teclado
     * - Limpia userData (referencias de usuario)
     * - Procesa hijos recursivamente
     * 
     * QUÉ NO TOCA:
     * - Estructura de la escena (Parent.children, Scene.root)
     * - CSS properties (JavaFX los necesita hasta el cambio)
     * - Propiedades internas de JavaFX
     */
    public static void limpiarNodo(javafx.scene.Node nodo) {
        if (nodo == null) {
            return;
        }
        
        try {
            // 1. DETENER ANIMACIONES: Si el nodo tiene método detener(), llamarlo
            // Esto cubre FondoAnimado y otros componentes con AnimationTimer
            try {
                java.lang.reflect.Method metodoDetener = nodo.getClass().getMethod("detener");
                metodoDetener.invoke(nodo);
                logger.debug("Llamado detener() en {}", nodo.getClass().getSimpleName());
            } catch (NoSuchMethodException e) {
                // No todos los nodos tienen método detener(), es normal
            } catch (Exception e) {
                logger.trace("Error al invocar detener() en {}: {}", 
                    nodo.getClass().getSimpleName(), e.getMessage());
            }
            
            // 2. LIMPIAR HIJOS RECURSIVAMENTE
            if (nodo instanceof javafx.scene.Parent) {
                javafx.scene.Parent parent = (javafx.scene.Parent) nodo;
                // Usar getChildrenUnmodifiable() directamente sin copiar
                // (más eficiente y seguro)
                for (javafx.scene.Node hijo : parent.getChildrenUnmodifiable()) {
                    limpiarNodo(hijo);
                }
            }
            
            // 3. REMOVER EVENT HANDLERS (prevenir memory leaks por listeners)
            nodo.setOnMouseClicked(null);
            nodo.setOnMousePressed(null);
            nodo.setOnMouseReleased(null);
            nodo.setOnMouseMoved(null);
            nodo.setOnMouseEntered(null);
            nodo.setOnMouseExited(null);
            nodo.setOnKeyPressed(null);
            nodo.setOnKeyReleased(null);
            
            // Limpiar userData (puede contener referencias circulares)
            nodo.setUserData(null);
            
        } catch (Exception e) {
            // No fallar por errores en la limpieza de nodos individuales
            logger.trace("Error al limpiar nodo {}: {}", 
                nodo.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Obtiene el Stage actual desde cualquier nodo
     */
    public static Stage obtenerStage(javafx.scene.Node nodo) {
        return (Stage) nodo.getScene().getWindow();
    }
    
    // ============================================
    // GESTIÓN DEL USUARIO AUTENTICADO
    // ============================================
    
    /**
     * Almacena el usuario actual después del login exitoso.
     * Esto permite que todas las vistas accedan a la información del usuario.
     * 
     * @param usuario Datos del usuario autenticado (token, rol, nombre, etc.)
     */
    public static void setUsuarioActual(AuthRespuestaDto usuario) {
        usuarioActual = usuario;
    }
    
    /**
     * Obtiene el usuario actualmente autenticado.
     * 
     * @return AuthRespuestaDto con los datos del usuario, o null si no hay sesión
     */
    public static AuthRespuestaDto getUsuarioActual() {
        return usuarioActual;
    }
    
    /**
     * Limpia el usuario actual (para logout).
     */
    public static void limpiarUsuario() {
        usuarioActual = null;
        limpiarHistorial(); // También limpiar el historial al cerrar sesión
    }
    
    // ============================================
    // GESTIÓN DE BREADCRUMBS
    // ============================================
    
    /**
     * Agrega una vista al historial de navegación.
     * Evita duplicados consecutivos.
     */
    private static void agregarAlHistorial(String titulo, String rutaFxml) {
        // No agregar si es la misma vista que la última
        if (!historialNavegacion.isEmpty()) {
            Breadcrumb ultimo = historialNavegacion.get(historialNavegacion.size() - 1);
            if (ultimo.getRutaFxml().equals(rutaFxml)) {
                return;
            }
        }
        
        historialNavegacion.add(new Breadcrumb(titulo, rutaFxml));
    }
    
    /**
     * Obtiene el historial de navegación completo.
     * 
     * @return Lista de breadcrumbs
     */
    public static List<Breadcrumb> getHistorialNavegacion() {
        return new ArrayList<>(historialNavegacion);
    }
    
    /**
     * Navega hacia atrás eliminando la última entrada del historial.
     * 
     * @param stage Stage actual
     * @throws IOException si hay error al cargar la vista
     */
    public static void navegarAtras(Stage stage) throws IOException {
        if (historialNavegacion.size() > 1) {
            // Remover la vista actual
            historialNavegacion.remove(historialNavegacion.size() - 1);
            
            // Obtener la vista anterior
            Breadcrumb anterior = historialNavegacion.get(historialNavegacion.size() - 1);
            
            // Remover también la anterior para que cambiarEscena no la duplique
            historialNavegacion.remove(historialNavegacion.size() - 1);
            
            // Navegar a la vista anterior
            cambiarEscena(stage, anterior.getRutaFxml(), anterior.getTitulo());
        }
    }
    
    /**
     * Navega a un breadcrumb específico en el historial.
     * 
     * @param stage Stage actual
     * @param indice Índice del breadcrumb en el historial
     * @throws IOException si hay error al cargar la vista
     */
    public static void navegarABreadcrumb(Stage stage, int indice) throws IOException {
        if (indice >= 0 && indice < historialNavegacion.size()) {
            Breadcrumb destino = historialNavegacion.get(indice);
            
            // Remover todas las entradas después del índice seleccionado
            while (historialNavegacion.size() > indice) {
                historialNavegacion.remove(historialNavegacion.size() - 1);
            }
            
            // Navegar al breadcrumb seleccionado
            cambiarEscena(stage, destino.getRutaFxml(), destino.getTitulo());
        }
    }
    
    /**
     * Limpia todo el historial de navegación.
     */
    public static void limpiarHistorial() {
        historialNavegacion.clear();
    }
    
    /**
     * Genera una cadena de breadcrumbs separados por " > ".
     * 
     * @return String con el path completo de breadcrumbs
     */
    public static String getBreadcrumbsPath() {
        if (historialNavegacion.isEmpty()) {
            return "";
        }
        
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < historialNavegacion.size(); i++) {
            if (i > 0) {
                path.append(" > ");
            }
            path.append(historialNavegacion.get(i).getTitulo());
        }
        return path.toString();
    }
    
    // ============================================
    // MÉTODOS DE NAVEGACIÓN RÁPIDA
    // ============================================
    
    /**
     * Navega a la vista de login principal (PIN).
     * 
     * Este es el login primario del sistema donde los cajeros y meseros
     * ingresan con código de 2 dígitos + PIN de 4 dígitos.
     * 
     * Usado para:
     * - Logout (cerrar sesión)
     * - Cuando no hay sesión válida
     * - Volver desde el login de administrador
     * 
     * La vista cargada es login-pin.fxml (LoginPinController).
     */
    public static void irALogin() {
        try {
            // Obtener el stage actual (cualquier ventana abierta)
            Stage stage = Stage.getWindows().stream()
                    .filter(w -> w instanceof Stage)
                    .map(w -> (Stage) w)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No hay ventana activa"));
            
            // Navegar al login principal con PIN
            cambiarEscena(stage, "/vista/login-pin.fxml", IdiomaUtil.obtener("ctrl.nav.titulo_login"));
        } catch (IOException e) {
            logger.error("Error al cargar la vista de login", e);
            AlertaUtil.mostrarError(IdiomaUtil.obtener("ctrl.menu.sesion_invalida"), IdiomaUtil.obtener("ctrl.nav.error_cargar_login"));
        }
    }
    
    /**
     * Navega al menú principal después del login exitoso.
     * Limpia el historial para que no aparezca "Iniciar Sesión" en los breadcrumbs.
     * 
     * @param stage La ventana actual
     */
    public static void irAMenuPrincipal(Stage stage) {
        try {
            // Limpiar el historial para que los breadcrumbs empiecen desde el menú principal
            limpiarHistorial();
            cambiarEscena(stage, "/vista/menu-principal.fxml", IdiomaUtil.obtener("ctrl.nav.titulo_menu"));
        } catch (IOException e) {
            logger.error("Error al cargar el menú principal", e);
            AlertaUtil.mostrarError(IdiomaUtil.obtener("ctrl.menu.sesion_invalida"), IdiomaUtil.obtener("ctrl.nav.error_cargar_menu"));
        }
    }
    // ============================================
    // MANEJO DE RETROCESO PERSONALIZADO
    // ============================================
    
    private static Runnable manejadorAtrasPersonalizado;

    /**
     * Establece un manejador personalizado para la acción de "Atrás" (ESC).
     * Esto es útil para controladores que manejan su propia navegación interna
     * (como MenuPrincipalController con sus subvistas).
     * 
     * @param manejador Runnable a ejecutar al presionar ESC, o null para limpiar.
     */
    public static void setManejadorAtras(Runnable manejador) {
        manejadorAtrasPersonalizado = manejador;
    }

    /**
     * Maneja la pulsación de la tecla ESC a nivel global.
     * Define la lógica de "Atrás" o "Salir" según la vista actual y el historial.
     * 
     * Lógica:
     * 1. Si hay un Manejador Personalizado -> Ejecutarlo y salir (prioridad máxima)
     * 2. Login -> Cerrar aplicación
     * 3. Si hay historial > 1 -> Volver a la vista anterior (Breadcrumb Atrás)
     * 4. Si estamos en raíz (historial=1):
     *    - Menú Principal -> Cerrar sesión y ir a Login
     *    - Otro -> Forzar ir a Menú Principal (fallback)
     */
    public static void manejarTeclaEsc(Stage stage) {
        // Prioridad 1: Manejador personalizado (Subvistas internas)
        if (manejadorAtrasPersonalizado != null) {
            logger.info("ESC -> Ejecutando manejador personalizado");
            manejadorAtrasPersonalizado.run();
            return;
        }

        String tituloActual = stage.getTitle();
        
        // Caso 1: Estamos en el Login (o título inicial) -> SALIR APP
        if (tituloActual == null || tituloActual.contains("Login") || tituloActual.contains("Iniciar Sesión") || tituloActual.contains("Sistema de Gestión de Bares")) {
            logger.info("ESC en Login -> Cerrando aplicación");
            javafx.application.Platform.exit();
            System.exit(0);
            return;
        }
        
        // Caso 2: Navegación por Historial (Breadcrumbs)
        // Si hay más de 1 elemento, significa que podemos volver atrás
        if (historialNavegacion.size() > 1) {
            try {
                logger.info("ESC -> Volviendo atrás en historial");
                navegarAtras(stage);
                return;
            } catch (IOException e) {
                logger.error("Error al navegar atrás con ESC", e);
                // Si falla volver atrás, caemos al fallback (ir al menú)
            }
        }
        
        // Caso 3: Estamos en la raíz (Historial <= 1)
        
        // Si es Menú Principal -> LOGOUT
        if (tituloActual != null && tituloActual.contains("Menú Principal")) {
            logger.info("ESC en Menú Principal -> Cerrando sesión");
            limpiarUsuario();
            irALogin();
            return;
        }
        
        // Fallback: Si estamos perdidos o sin historial, volver al Menú Principal
        logger.info("ESC en vista sin historial -> Volviendo al menú principal");
        irAMenuPrincipal(stage);
    }
}
