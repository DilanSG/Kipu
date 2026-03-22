/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.cliente.controlador.configuracion.herramientas;

import com.baryx.cliente.componente.TopologiaRed;
import com.baryx.cliente.configuracion.ConfiguracionCliente;
import com.baryx.cliente.controlador.configuracion.GestorModales;
import com.baryx.cliente.controlador.configuracion.ModalHerramienta;
import com.baryx.cliente.utilidad.IdiomaUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler del modal de Diagnóstico de Red.
 *
 * Muestra información detallada de la red local, topología visual animada
 * (delegada al componente {@link TopologiaRed}), estado de conexión y
 * botón de reinicio del diagnóstico.
 *
 * Información mostrada:
 * - IP servidor, IP local, gateway, máscara de subred
 * - Interfaz de red activa, dirección MAC
 * - Latencia al servidor, puerto, modo host
 * - Nombre del equipo, sistema operativo
 *
 * La topología visual se construye mediante el componente reutilizable
 * {@link TopologiaRed}, que puede usarse independientemente desde
 * cualquier parte del programa (login, menú principal, etc.).
 *
 * @see GestorModales
 * @see TopologiaRed
 */
public class DiagnosticoRedHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticoRedHandler.class);

    /** Gestor de modales compartido con la infraestructura visual */
    private final GestorModales gestor;

    // ==================== ESTADO INTERNO ====================

    /** Referencia al contenedor de topología para reinicio del diagnóstico */
    private VBox contenedorTopologia;

    /** Referencia al grid de info de red para reinicio */
    private GridPane gridInfoRed;

    /** Indicador visual de estado de conexión (círculo coloreado) */
    private Circle indicadorConexion;

    /** Label descriptivo del estado de conexión */
    private Label labelEstadoConexion;

    /** Componente de topología activo (gestiona sus propias animaciones) */
    private TopologiaRed topologiaActual;

    // ==================== CONSTRUCTOR ====================

    /**
     * Crea un handler de diagnóstico de red vinculado al gestor de modales.
     *
     * @param gestor Gestor de modales que provee infraestructura visual compartida
     */
    public DiagnosticoRedHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    // ==================== APERTURA DEL MODAL ====================

    /**
     * Abre el modal de Diagnóstico de Red.
     * Construye info completa de red, topología visual con formas descriptivas
     * (nube, servidor tipo rack, clientes tipo monitor), y botón de reinicio.
     */
    @Override
    public void abrir() {
        logger.info("Abriendo Diagnóstico de Red");

        VBox modal = new VBox(10);
        modal.setMaxWidth(750);
        modal.setMaxHeight(650);
        modal.setPadding(new Insets(20));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header ───
        HBox header = gestor.crearHeaderModal(IdiomaUtil.obtener("ctrl.red.titulo"), "icono-cfg-red");

        // ─── Estado de conexión ───
        indicadorConexion = new Circle(5, Color.web("#666"));
        labelEstadoConexion = new Label(IdiomaUtil.obtener("ctrl.red.verificando"));
        labelEstadoConexion.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");
        HBox filaEstado = new HBox(8, indicadorConexion, labelEstadoConexion);
        filaEstado.setAlignment(Pos.CENTER_LEFT);

        // ─── Grid de información de red completa ───
        gridInfoRed = construirGridInfoRed();

        // ─── Topología de red (componente reutilizable) ───
        contenedorTopologia = new VBox();
        topologiaActual = new TopologiaRed();
        Pane topologia = topologiaActual.construir();
        contenedorTopologia.getChildren().add(topologia);

        // ─── Botón reiniciar diagnóstico (centrado, estilo métodos de pago) ───
        Button btnReiniciar = new Button(IdiomaUtil.obtener("ctrl.red.reiniciar"));
        btnReiniciar.getStyleClass().add("btn-metodo-pago");
        btnReiniciar.setPrefWidth(240);
        btnReiniciar.setPrefHeight(40);
        btnReiniciar.setOnAction(e -> reiniciarDiagnosticoRed());
        HBox filaBtnReiniciar = new HBox(btnReiniciar);
        filaBtnReiniciar.setAlignment(Pos.CENTER);

        // ─── ScrollPane para contenido ───
        VBox contenido = new VBox(10,
            gestor.crearSeparador(), filaEstado, gridInfoRed,
            gestor.crearSeparador(), contenedorTopologia,
            gestor.crearSeparador(), filaBtnReiniciar);
        contenido.setPadding(new Insets(0));
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; " +
            "-fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        modal.getChildren().addAll(header, scroll);
        gestor.mostrarModal(modal);

        // Verificar conexión en hilo aparte
        verificarConexionAsync(indicadorConexion, labelEstadoConexion,
            (Label) gridInfoRed.lookup("#valLatencia"));

        // Cargar info de nube async
        cargarInfoNubeAsync(gridInfoRed);
    }

    // ==================== GRID DE INFORMACIÓN ====================

    /**
     * Construye el grid completo con toda la información de red del equipo.
     * Incluye: IP servidor, IP local, gateway, máscara, interfaz, MAC, latencia,
     * modo host, nombre de equipo, puerto servidor y sistema operativo.
     *
     * @return GridPane con toda la información de red
     */
    private GridPane construirGridInfoRed() {
        GridPane info = new GridPane();
        info.setHgap(16);
        info.setVgap(4);

        Label valLatencia = gestor.crearInfoValor(IdiomaUtil.obtener("ctrl.red.midiendo"));
        valLatencia.setId("valLatencia");

        // Obtener datos de red enriquecidos
        String ipLocal = obtenerIPLocal();
        String[] datosRed = obtenerDatosRedDetallados();
        String interfazNombre = datosRed[0];
        String mac = datosRed[1];
        String mascara = datosRed[2];
        String gateway = obtenerGateway();
        String nombreEquipo = obtenerNombreEquipo();

        int fila = 0;
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.servidor")),
            gestor.crearInfoValor(ConfiguracionCliente.getUrlServidor()));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.ip_local")),
            gestor.crearInfoValor(ipLocal));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.gateway")),
            gestor.crearInfoValor(gateway));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.mascara")),
            gestor.crearInfoValor(mascara));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.interfaz")),
            gestor.crearInfoValor(interfazNombre));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.mac")),
            gestor.crearInfoValor(mac));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.latencia")), valLatencia);
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.puerto")),
            gestor.crearInfoValor(String.valueOf(ConfiguracionCliente.getPuertoServidor())));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.modo_host")),
            gestor.crearInfoValor(ConfiguracionCliente.isHostMode()
                ? IdiomaUtil.obtener("ctrl.red.host.si") : IdiomaUtil.obtener("ctrl.red.host.no")));
        info.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.equipo")),
            gestor.crearInfoValor(nombreEquipo));
        info.addRow(fila, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.sistema")),
            gestor.crearInfoValor(System.getProperty("os.name") + " " +
                System.getProperty("os.arch")));

        // ── Sección nube (se llena async) ──
        fila++;
        Label separadorNube = new Label("── " + IdiomaUtil.obtener("ctrl.red.label.nube") + " ──");
        separadorNube.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 10px; -fx-font-weight: 600;");
        info.add(separadorNube, 0, fila, 2, 1);

        fila++;
        Label valNubeEstado = gestor.crearInfoValor(IdiomaUtil.obtener("ctrl.red.midiendo"));
        valNubeEstado.setId("valNubeEstado");
        info.addRow(fila, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.nube_estado")), valNubeEstado);

        fila++;
        Label valNubeCluster = gestor.crearInfoValor("—");
        valNubeCluster.setId("valNubeCluster");
        info.addRow(fila, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.nube_cluster")), valNubeCluster);

        fila++;
        Label valNubeDb = gestor.crearInfoValor("—");
        valNubeDb.setId("valNubeDb");
        info.addRow(fila, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.nube_db")), valNubeDb);

        fila++;
        Label valNubeBusinessId = gestor.crearInfoValor("—");
        valNubeBusinessId.setId("valNubeBusinessId");
        info.addRow(fila, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.red.label.nube_business_id")), valNubeBusinessId);

        return info;
    }

    // ==================== REINICIO DEL DIAGNÓSTICO ====================

    /**
     * Reinicia el diagnóstico de red: detiene animaciones, reconstruye
     * la topología visual y vuelve a verificar la conexión.
     */
    private void reiniciarDiagnosticoRed() {
        logger.info("Reiniciando diagnóstico de red");

        // Detener animaciones del componente de topología anterior
        if (topologiaActual != null) {
            topologiaActual.detenerAnimaciones();
        }

        // Reconstruir topología con una nueva instancia del componente
        contenedorTopologia.getChildren().clear();
        topologiaActual = new TopologiaRed();
        Pane nuevaTopologia = topologiaActual.construir();
        contenedorTopologia.getChildren().add(nuevaTopologia);

        // Resetear indicadores
        indicadorConexion.setFill(Color.web("#666"));
        labelEstadoConexion.setText(IdiomaUtil.obtener("ctrl.red.verificando"));
        labelEstadoConexion.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        // Re-verificar conexión
        Label valLatencia = (Label) gridInfoRed.lookup("#valLatencia");
        if (valLatencia != null) valLatencia.setText(IdiomaUtil.obtener("ctrl.red.midiendo"));
        verificarConexionAsync(indicadorConexion, labelEstadoConexion, valLatencia);

        // Re-cargar info de nube
        cargarInfoNubeAsync(gridInfoRed);
    }

    // ==================== VERIFICACIÓN DE CONEXIÓN ====================

    /**
     * Verifica la conexión al servidor vía health endpoint de forma asíncrona.
     * Actualiza los indicadores visuales con el resultado.
     *
     * @param indicador  Círculo indicador de estado
     * @param labelEstado Label descriptivo del estado
     * @param valLatencia Label donde mostrar la latencia medida
     */
    private void verificarConexionAsync(Circle indicador, Label labelEstado,
                                        Label valLatencia) {
        CompletableFuture.supplyAsync(() -> {
            try {
                long inicio = System.currentTimeMillis();
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/usuarios/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                long latencia = System.currentTimeMillis() - inicio;
                conn.disconnect();
                return new long[]{code, latencia};
            } catch (Exception ex) {
                return new long[]{0, -1};
            }
        }).thenAccept(r -> Platform.runLater(() -> {
            if (r[0] >= 200 && r[0] < 400) {
                indicador.setFill(Color.web("#a8b991"));
                labelEstado.setText(IdiomaUtil.obtener("ctrl.red.conectado"));
                labelEstado.setStyle("-fx-text-fill: #a8b991; -fx-font-size: 13px;");
                valLatencia.setText(r[1] + " ms");
            } else {
                indicador.setFill(Color.web("#cc4444"));
                labelEstado.setText(IdiomaUtil.obtener("ctrl.red.sin_conexion"));
                labelEstado.setStyle("-fx-text-fill: #cc4444; -fx-font-size: 13px;");
                valLatencia.setText("—");
            }
        }));
    }

    // ==================== ESTADO NUBE ====================

    /**
     * Consulta /api/sync/estado y llena los labels de nube en el grid de info.
     */
    private void cargarInfoNubeAsync(GridPane grid) {
        CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/sync/estado").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = reader.readLine()) != null) sb.append(linea);
                    reader.close();
                    conn.disconnect();
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> resp = mapper.readValue(
                        sb.toString(), new TypeReference<Map<String, Object>>() {});
                    Object datos = resp.get("datos");
                    if (datos instanceof Map) {
                        return (Map<String, Object>) datos;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                logger.debug("No se pudo consultar estado nube: {}", e.getMessage());
            }
            return null;
        }).thenAccept(estado -> Platform.runLater(() -> {
            Label valEstado = (Label) grid.lookup("#valNubeEstado");
            Label valCluster = (Label) grid.lookup("#valNubeCluster");
            Label valDb = (Label) grid.lookup("#valNubeDb");
            Label valBusinessId = (Label) grid.lookup("#valNubeBusinessId");

            if (estado == null) {
                if (valEstado != null) {
                    valEstado.setText(IdiomaUtil.obtener("ctrl.red.no_disponible"));
                    valEstado.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");
                }
                return;
            }

            boolean habilitado = Boolean.TRUE.equals(estado.get("habilitado"));
            boolean conectado = Boolean.TRUE.equals(estado.get("conexionNube"));
            String mongoUri = estado.get("mongoUri") != null ? estado.get("mongoUri").toString() : "";
            String businessId = estado.get("businessId") != null ? estado.get("businessId").toString() : "—";
            String baseDatos = estado.get("baseDatos") != null ? estado.get("baseDatos").toString() : "—";

            // Extraer cluster del parámetro appName
            String cluster = "";
            if (!mongoUri.isBlank()) {
                int idx = mongoUri.indexOf("appName=");
                if (idx >= 0) {
                    cluster = mongoUri.substring(idx + "appName=".length());
                    int amp = cluster.indexOf('&');
                    if (amp > 0) cluster = cluster.substring(0, amp);
                }
            }

            if (valCluster != null) valCluster.setText(cluster.isEmpty() ? "—" : cluster);
            if (valDb != null) valDb.setText(baseDatos);
            if (valBusinessId != null) valBusinessId.setText(businessId);

            if (valEstado != null) {
                if (habilitado && conectado) {
                    valEstado.setText(IdiomaUtil.obtener("ctrl.red.nube.conectada"));
                    valEstado.setStyle("-fx-text-fill: #a8b991; -fx-font-size: 12px;");
                } else if (habilitado) {
                    valEstado.setText(IdiomaUtil.obtener("ctrl.red.nube.sin_conexion"));
                    valEstado.setStyle("-fx-text-fill: #daa520; -fx-font-size: 12px;");
                } else {
                    valEstado.setText(IdiomaUtil.obtener("ctrl.red.nube.deshabilitada"));
                    valEstado.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");
                }
            }
        }));
    }

    // ==================== UTILIDADES DE RED ====================

    /**
     * Obtiene la dirección IPv4 local del equipo en la red LAN.
     *
     * @return IP local o "No disponible"
     */
    private String obtenerIPLocal() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> direcciones = iface.getInetAddresses();
                while (direcciones.hasMoreElements()) {
                    InetAddress addr = direcciones.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo obtener IP local: {}", e.getMessage());
        }
        return IdiomaUtil.obtener("ctrl.red.no_disponible");
    }

    /**
     * Obtiene datos detallados de la interfaz de red activa:
     * nombre de interfaz, dirección MAC y máscara de subred.
     *
     * @return Array [nombreInterfaz, mac, máscara]
     */
    private String[] obtenerDatosRedDetallados() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                boolean tieneIpv4 = false;
                short prefixLen = -1;
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        tieneIpv4 = true;
                        for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                            if (ia.getAddress() instanceof Inet4Address) {
                                prefixLen = ia.getNetworkPrefixLength();
                                break;
                            }
                        }
                        break;
                    }
                }
                if (!tieneIpv4) continue;

                String nombre = iface.getDisplayName() != null
                    ? iface.getDisplayName() : iface.getName();

                StringBuilder macSb = new StringBuilder();
                byte[] mac = iface.getHardwareAddress();
                if (mac != null) {
                    for (int i = 0; i < mac.length; i++) {
                        macSb.append(String.format("%02X", mac[i]));
                        if (i < mac.length - 1) macSb.append(":");
                    }
                }

                String mascara = prefixLen >= 0
                    ? prefixToMask(prefixLen) + " (/" + prefixLen + ")"
                    : IdiomaUtil.obtener("ctrl.red.no_disponible");

                return new String[]{
                    nombre,
                    macSb.length() > 0 ? macSb.toString() : IdiomaUtil.obtener("ctrl.red.no_disponible"),
                    mascara
                };
            }
        } catch (Exception e) {
            logger.debug("Error obteniendo datos de red: {}", e.getMessage());
        }
        return new String[]{IdiomaUtil.obtener("ctrl.red.no_disponible"), IdiomaUtil.obtener("ctrl.red.no_disponible"), IdiomaUtil.obtener("ctrl.red.no_disponible")};
    }

    /**
     * Convierte una longitud de prefijo de red a máscara decimal.
     * Ej: 24 → "255.255.255.0"
     *
     * @param prefixLen Longitud del prefijo (0-32)
     * @return Máscara en formato decimal punteado
     */
    private String prefixToMask(int prefixLen) {
        int mask = prefixLen == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLen);
        return ((mask >> 24) & 0xFF) + "." + ((mask >> 16) & 0xFF) + "." +
            ((mask >> 8) & 0xFF) + "." + (mask & 0xFF);
    }

    /**
     * Intenta obtener la dirección del gateway predeterminado.
     * Usa comandos del SO (ip route en Linux, route en Windows).
     *
     * @return IP del gateway o "No disponible"
     */
    private String obtenerGateway() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("linux")) {
                pb = new ProcessBuilder("ip", "route", "show", "default");
            } else if (os.contains("windows")) {
                pb = new ProcessBuilder("cmd", "/c", "route", "print", "0.0.0.0");
            } else {
                return IdiomaUtil.obtener("ctrl.red.no_disponible");
            }
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    if (os.contains("linux") && linea.contains("default via")) {
                        String[] partes = linea.trim().split("\\s+");
                        for (int i = 0; i < partes.length - 1; i++) {
                            if ("via".equals(partes[i])) return partes[i + 1];
                        }
                    }
                    if (os.contains("windows") && linea.trim().startsWith("0.0.0.0")) {
                        String[] partes = linea.trim().split("\\s+");
                        if (partes.length >= 3) return partes[2];
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            logger.debug("No se pudo obtener gateway: {}", e.getMessage());
        }
        return IdiomaUtil.obtener("ctrl.red.no_disponible");
    }

    /**
     * Obtiene el nombre del equipo local.
     *
     * @return Hostname o nombre de usuario como fallback
     */
    private String obtenerNombreEquipo() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getProperty("user.name", IdiomaUtil.obtener("ctrl.red.no_disponible"));
        }
    }
}
