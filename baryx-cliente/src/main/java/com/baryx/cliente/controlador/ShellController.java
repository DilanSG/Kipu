/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular. */
package com.baryx.cliente.controlador;

import com.baryx.cliente.utilidad.IdiomaUtil;
import com.baryx.cliente.utilidad.NavegacionUtil;
import com.baryx.cliente.servicio.LicenseServicio;
import com.baryx.cliente.servicio.UpdateCheckServicio;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

// Controlador raíz (shell) de la aplicación. Mantiene una única Scene durante toda la vida de la aplicación y carga las vistas principales como hijos dentro del contenedor.
public class ShellController {

    private static final Logger logger = LoggerFactory.getLogger(ShellController.class);

    private static ShellController instancia;

    @FXML private StackPane contenedorRaiz;
    @FXML private VBox splash;

    // Referencias a banners activos para evitar duplicados
    private javafx.scene.Node bannerActualizacionActual;
    private javafx.scene.Node bannerRenovacionActual;

    @FXML
    public void initialize() {
        instancia = this;
        logger.info("ShellController inicializado — splash visible");
    }

    public static ShellController getInstancia() {
        return instancia;
    }

    /**
     * Carga una vista FXML dentro del contenedor raíz, reemplazando el contenido anterior.
     * Limpia los nodos existentes antes de insertar la nueva vista.
     */
    public void cargarVista(String rutaFxml) throws IOException {
        // Limpiar contenido anterior (excepto splash, que se oculta)
        limpiarContenidoActual();

        FXMLLoader cargador = new FXMLLoader(getClass().getResource(rutaFxml));
        cargador.setResources(IdiomaUtil.obtenerBundle());
        Parent vista = cargador.load();

        // Ocultar splash si aún es visible
        if (splash != null) {
            splash.setVisible(false);
            splash.setManaged(false);
        }

        contenedorRaiz.getChildren().add(vista);
        logger.info("Vista cargada en shell: {}", rutaFxml);
    }

    /**
     * Limpia todos los nodos del contenedor raíz excepto el splash.
     * Llama a {@link NavegacionUtil#limpiarNodo} para detener animaciones y liberar recursos.
     */
    private void limpiarContenidoActual() {
        var nodosARemover = new java.util.ArrayList<javafx.scene.Node>();
        for (javafx.scene.Node nodo : contenedorRaiz.getChildren()) {
            if (nodo != splash) {
                NavegacionUtil.limpiarNodo(nodo);
                nodosARemover.add(nodo);
            }
        }
        contenedorRaiz.getChildren().removeAll(nodosARemover);
    }

    public StackPane getContenedorRaiz() {
        return contenedorRaiz;
    }

    // ------------------------------------------------------------------
    // Update banner: overlay no-intrusivo en la parte superior
    // ------------------------------------------------------------------

    /**
     * Muestra el banner de actualización disponible sobre la interfaz.
     * Se llama desde el hilo de JavaFX (Platform.runLater).
     *
     * @param info         Información de la actualización disponible
     * @param hostServices Para abrir el navegador (del sistema)
     */
    public void mostrarBannerActualizacion(UpdateCheckServicio.UpdateInfo info, HostServices hostServices) {
        try {
            // Remover banner anterior si existe (evitar duplicados)
            if (bannerActualizacionActual != null) {
                contenedorRaiz.getChildren().remove(bannerActualizacionActual);
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/update-banner.fxml"));
            javafx.scene.layout.HBox banner = loader.load();

            UpdateBannerController ctrl = loader.getController();
            ctrl.inicializar(info.versionRemota(), info.versionLocal(), info.urlDescarga(), hostServices);

            // Anclar al top del StackPane raíz, encima de todo el contenido
            StackPane.setAlignment(banner, javafx.geometry.Pos.TOP_CENTER);
            contenedorRaiz.getChildren().add(banner);
            bannerActualizacionActual = banner;

            logger.info("[Update] Banner mostrado — version remota: {}", info.versionRemota());
        } catch (IOException e) {
            logger.warn("[Update] No se pudo cargar update-banner.fxml: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // License dialog: overlay bloqueante
    // ------------------------------------------------------------------

    /**
     * Muestra el diálogo de licencia como overlay bloqueante.
     * Para estados EXPIRED/REVOKED no permite continuar.
     * Para NO_KEY muestra campo para ingresar la key.
     *
     * @param resultado    Resultado de la validación de licencia
     * @param hostServices Para abrir el navegador
     * @param onCerrar     Callback cuando el usuario cierra el diálogo
     */
    public void mostrarDialogoLicencia(LicenseServicio.ResultadoValidacion resultado,
                                       HostServices hostServices,
                                       Runnable onCerrar) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/license-dialog.fxml"));
            javafx.scene.layout.StackPane dialogo = loader.load();

            LicenseDialogController ctrl = loader.getController();
            ctrl.inicializar(resultado, hostServices, onCerrar);

            // Overlay encima de todo el contenido existente
            contenedorRaiz.getChildren().add(dialogo);

            logger.info("[License] Diálogo mostrado — estado: {}", resultado.estado());
        } catch (IOException e) {
            logger.warn("[License] No se pudo cargar license-dialog.fxml: {}", e.getMessage());
            // Si falla el diálogo, ejecutar el callback para no bloquear la app
            if (onCerrar != null) onCerrar.run();
        }
    }

    // ------------------------------------------------------------------
    // Renewal warning banner: aviso no-bloqueante de renovación próxima
    // ------------------------------------------------------------------

    /**
     * Muestra un banner de aviso cuando faltan pocos días para la expiración.
     * No bloquea la app, solo informa al usuario.
     */
    public void mostrarBannerRenovacion(int diasRestantes, HostServices hostServices) {
        try {
            // Remover banner anterior si existe (evitar duplicados)
            if (bannerRenovacionActual != null) {
                contenedorRaiz.getChildren().remove(bannerRenovacionActual);
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/renewal-banner.fxml"));
            javafx.scene.layout.HBox banner = loader.load();

            RenewalBannerController ctrl = loader.getController();
            ctrl.inicializar(diasRestantes, hostServices);

            StackPane.setAlignment(banner, javafx.geometry.Pos.TOP_CENTER);
            contenedorRaiz.getChildren().add(banner);
            bannerRenovacionActual = banner;

            logger.info("[License] Banner de renovación mostrado — {} días restantes", diasRestantes);
        } catch (IOException e) {
            logger.warn("[License] No se pudo cargar renewal-banner.fxml: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Asistente PostgreSQL: wizard de configuración de BD para host mode
    // ------------------------------------------------------------------

    /**
     * Muestra el asistente de configuración de PostgreSQL como overlay bloqueante.
     * Se invoca al inicio si el host necesita setup, o cuando falla la conexión a BD.
     *
     * @param callbackCompletado Se ejecuta con true si el setup fue exitoso, false si se canceló
     */
    public void mostrarAsistenteBaseDatos(java.util.function.Consumer<Boolean> callbackCompletado) {
        var asistente = new com.baryx.cliente.controlador.configuracion.AsistenteBaseDatos(
                contenedorRaiz, callbackCompletado);
        asistente.mostrar();
        logger.info("[Setup] Asistente de base de datos abierto");
    }
}
