/*
 * Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
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

/**
 * Controlador raíz (shell) de la aplicación.
 * Mantiene una única Scene durante toda la vida de la aplicación y
 * carga las vistas principales (login, menú) como hijos dentro del contenedor.
 * Esto elimina los cambios de Scene que causan parpadeos visuales.
 */
public class ShellController {

    private static final Logger logger = LoggerFactory.getLogger(ShellController.class);

    private static ShellController instancia;

    @FXML private StackPane contenedorRaiz;
    @FXML private VBox splash;

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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/update-banner.fxml"));
            javafx.scene.layout.HBox banner = loader.load();

            UpdateBannerController ctrl = loader.getController();
            ctrl.inicializar(info.versionRemota(), info.versionLocal(), info.urlDescarga(), hostServices);

            // Anclar al top del StackPane raíz, encima de todo el contenido
            StackPane.setAlignment(banner, javafx.geometry.Pos.TOP_CENTER);
            contenedorRaiz.getChildren().add(banner);

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
        javafx.scene.layout.HBox banner = new javafx.scene.layout.HBox(12);
        banner.setAlignment(javafx.geometry.Pos.CENTER);
        banner.setStyle("-fx-background-color: linear-gradient(to right, #8b6914, #d4af37); "
                + "-fx-padding: 10 20; -fx-background-radius: 0 0 8 8;");
        banner.setMaxHeight(44);

        javafx.scene.control.Label icono = new javafx.scene.control.Label("\u26A0");
        icono.setStyle("-fx-text-fill: #000; -fx-font-size: 16px;");

        String textoAviso = java.text.MessageFormat.format(
                com.baryx.cliente.utilidad.IdiomaUtil.obtener("ctrl.licencia.aviso_renovacion"),
                diasRestantes);
        javafx.scene.control.Label texto = new javafx.scene.control.Label(textoAviso);
        texto.setStyle("-fx-text-fill: #000; -fx-font-size: 13px; -fx-font-weight: 600;");

        javafx.scene.control.Button btnRenovar = new javafx.scene.control.Button(
                com.baryx.cliente.utilidad.IdiomaUtil.obtener("ctrl.licencia.dialog.renovar"));
        btnRenovar.setStyle("-fx-background-color: #000; -fx-text-fill: #d4af37; "
                + "-fx-font-size: 12px; -fx-font-weight: 700; -fx-background-radius: 4; -fx-cursor: hand;");
        btnRenovar.setOnAction(e -> {
            if (hostServices != null) {
                try { hostServices.showDocument(LicenseServicio.getUrlTienda()); }
                catch (Exception ex) { logger.warn("[License] No se pudo abrir navegador"); }
            }
        });

        javafx.scene.control.Button btnCerrar = new javafx.scene.control.Button("\u2715");
        btnCerrar.setStyle("-fx-background-color: transparent; -fx-text-fill: #000; "
                + "-fx-font-size: 14px; -fx-cursor: hand;");
        btnCerrar.setOnAction(e -> contenedorRaiz.getChildren().remove(banner));

        banner.getChildren().addAll(icono, texto, btnRenovar, btnCerrar);
        StackPane.setAlignment(banner, javafx.geometry.Pos.TOP_CENTER);
        contenedorRaiz.getChildren().add(banner);
        logger.info("[License] Banner de renovación mostrado — {} días restantes", diasRestantes);
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
