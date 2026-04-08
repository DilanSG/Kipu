/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.componente.TopologiaRed;
import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.utilidad.IdiomaUtil;
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

    /** Referencia al grid de info de red para reinicio (hidden, solo para compat) */
    private GridPane gridInfoRed;

    /** Referencia al panel dual para reconstrucción */
    private HBox panelInfoDual;

    /** Indicador visual de estado de conexión (círculo coloreado) */
    private Circle indicadorConexion;

    /** Label descriptivo del estado de conexión */
    private Label labelEstadoConexion;

    /** Componente de topología activo (gestiona sus propias animaciones) */
    private TopologiaRed topologiaActual;

    // ── Labels async de latencia y nube ──
    private Label valLatencia;
    private Label valNubeEstado;
    private Label valNubeCluster;
    private Label valNubeDb;
    private Label valNubeBusinessId;

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

        // ─── Header (título centrado) ───
        HBox header = gestor.crearHeaderModal(IdiomaUtil.obtener("ctrl.red.titulo"), "icono-cfg-red");
        header.setAlignment(Pos.CENTER);

        // ─── Estado de conexión ───
        indicadorConexion = new Circle(5, Color.web("#666"));
        labelEstadoConexion = new Label(IdiomaUtil.obtener("ctrl.red.verificando"));
        labelEstadoConexion.getStyleClass().add("texto-info");
        labelEstadoConexion.setStyle("-fx-text-fill: #888;");
        HBox filaEstado = new HBox(8, indicadorConexion, labelEstadoConexion);
        filaEstado.setAlignment(Pos.CENTER_LEFT);

        // ─── Info de red en dos columnas: Local (izq) + Nube (der) ───
        HBox panelInfoDual = construirPanelInfoDual();

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
            gestor.crearSeparador(), filaEstado, panelInfoDual,
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
        verificarConexionAsync(indicadorConexion, labelEstadoConexion, valLatencia);

        // Cargar info de nube async
        cargarInfoNubeAsync();
    }

    // ==================== PANEL DUAL DE INFORMACIÓN ====================

    /**
     * Construye un panel con dos columnas: servidor local (izq) y nube (der).
     * Los títulos de sección son un 20% más grandes que los labels normales.
     *
     * @return HBox con las dos columnas de información
     */
    private HBox construirPanelInfoDual() {
        // ── Columna izquierda: Servidor Local ──
        VBox colLocal = construirColumnaLocal();
        HBox.setHgrow(colLocal, Priority.ALWAYS);

        // ── Separador vertical ──
        Region separadorVertical = new Region();
        separadorVertical.setMinWidth(1);
        separadorVertical.setMaxWidth(1);
        separadorVertical.setStyle("-fx-background-color: rgba(212, 175, 55, 0.15);");

        // ── Columna derecha: Nube ──
        VBox colNube = construirColumnaNube();
        HBox.setHgrow(colNube, Priority.ALWAYS);

        // Wrap ambas en un GridPane interno para lookups
        gridInfoRed = new GridPane();
        gridInfoRed.setManaged(false);
        gridInfoRed.setVisible(false);

        HBox panel = new HBox(16, colLocal, separadorVertical, colNube);
        panel.setPadding(new Insets(4, 0, 4, 0));
        return panel;
    }

    /**
     * Construye la columna izquierda con info del servidor local.
     */
    private VBox construirColumnaLocal() {
        VBox col = new VBox(4);

        // Título de sección
        Label tituloLocal = new Label(IdiomaUtil.obtener("ctrl.red.seccion.local"));
        tituloLocal.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: 700; -fx-font-size: 13px;");
        col.getChildren().add(tituloLocal);
        col.getChildren().add(crearEspaciador(4));

        // Datos de red
        String ipLocal = obtenerIPLocal();
        String[] datosRed = obtenerDatosRedDetallados();
        String interfazNombre = datosRed[0];
        String mac = datosRed[1];
        String mascara = datosRed[2];
        String gateway = obtenerGateway();
        String nombreEquipo = obtenerNombreEquipo();

        Label valLatenciaLocal = gestor.crearInfoValor(IdiomaUtil.obtener("ctrl.red.midiendo"));
        valLatenciaLocal.setId("valLatencia");
        this.valLatencia = valLatenciaLocal;

        col.getChildren().addAll(
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.servidor"), ConfiguracionCliente.getUrlServidor()),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.ip_local"), ipLocal),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.gateway"), gateway),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.mascara"), mascara),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.interfaz"), interfazNombre),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.mac"), mac),
            crearFilaInfoConLabel(IdiomaUtil.obtener("ctrl.red.label.latencia"), valLatenciaLocal),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.puerto"),
                String.valueOf(ConfiguracionCliente.getPuertoServidor())),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.modo_host"),
                ConfiguracionCliente.isHostMode()
                    ? IdiomaUtil.obtener("ctrl.red.host.si") : IdiomaUtil.obtener("ctrl.red.host.no")),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.equipo"), nombreEquipo),
            crearFilaInfo(IdiomaUtil.obtener("ctrl.red.label.sistema"),
                System.getProperty("os.name") + " " + System.getProperty("os.arch"))
        );

        return col;
    }

    /**
     * Construye la columna derecha con info de la nube (se llena async).
     */
    private VBox construirColumnaNube() {
        VBox col = new VBox(4);

        // Título de sección
        Label tituloNube = new Label(IdiomaUtil.obtener("ctrl.red.seccion.nube"));
        tituloNube.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: 700; -fx-font-size: 13px;");
        col.getChildren().add(tituloNube);
        col.getChildren().add(crearEspaciador(4));

        // Labels async (se llenan en cargarInfoNubeAsync)
        valNubeEstado = gestor.crearInfoValor(IdiomaUtil.obtener("ctrl.red.midiendo"));
        valNubeEstado.setId("valNubeEstado");
        valNubeEstado.getStyleClass().add("texto-secundario-sm");

        valNubeCluster = gestor.crearInfoValor("—");
        valNubeCluster.setId("valNubeCluster");

        valNubeDb = gestor.crearInfoValor("—");
        valNubeDb.setId("valNubeDb");

        valNubeBusinessId = gestor.crearInfoValor("—");
        valNubeBusinessId.setId("valNubeBusinessId");

        col.getChildren().addAll(
            crearFilaInfoConLabel(IdiomaUtil.obtener("ctrl.red.label.nube_estado"), valNubeEstado),
            crearFilaInfoConLabel(IdiomaUtil.obtener("ctrl.red.label.nube_cluster"), valNubeCluster),
            crearFilaInfoConLabel(IdiomaUtil.obtener("ctrl.red.label.nube_db"), valNubeDb),
            crearFilaInfoConLabel(IdiomaUtil.obtener("ctrl.red.label.nube_business_id"), valNubeBusinessId)
        );

        return col;
    }

    /**
     * Crea una fila de información con label de título (20% más grande) y valor.
     */
    private HBox crearFilaInfo(String titulo, String valor) {
        return crearFilaInfoConLabel(titulo, gestor.crearInfoValor(valor));
    }

    /**
     * Crea una fila de información con label de título y un Label pre-construido como valor.
     */
    private HBox crearFilaInfoConLabel(String titulo, Label valorLabel) {
        Label labelTitulo = gestor.crearInfoLabel(titulo);
        labelTitulo.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");
        labelTitulo.setMinWidth(100);
        HBox fila = new HBox(8, labelTitulo, valorLabel);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    /** Crea un espaciador vertical de la altura indicada. */
    private Region crearEspaciador(double alto) {
        Region r = new Region();
        r.setMinHeight(alto);
        r.setPrefHeight(alto);
        r.setMaxHeight(alto);
        return r;
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
        labelEstadoConexion.setStyle("-fx-text-fill: #888;");

        // Re-verificar conexión
        if (valLatencia != null) valLatencia.setText(IdiomaUtil.obtener("ctrl.red.midiendo"));
        verificarConexionAsync(indicadorConexion, labelEstadoConexion, valLatencia);

        // Re-cargar info de nube
        cargarInfoNubeAsync();
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
                labelEstado.setStyle("-fx-text-fill: #a8b991;");
                valLatencia.setText(r[1] + " ms");
            } else {
                indicador.setFill(Color.web("#cc4444"));
                labelEstado.setText(IdiomaUtil.obtener("ctrl.red.sin_conexion"));
                labelEstado.setStyle("-fx-text-fill: #cc4444;");
                valLatencia.setText("—");
            }
        }));
    }

    // ==================== ESTADO NUBE ====================

    /**
     * Consulta /api/sync/estado y llena los labels de nube.
     */
    private void cargarInfoNubeAsync() {
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
            if (estado == null) {
                if (valNubeEstado != null) {
                    valNubeEstado.setText(IdiomaUtil.obtener("ctrl.red.no_disponible"));
                    valNubeEstado.setStyle("-fx-text-fill: #555;");
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

            if (valNubeCluster != null) valNubeCluster.setText(cluster.isEmpty() ? "—" : cluster);
            if (valNubeDb != null) valNubeDb.setText(baseDatos);
            if (valNubeBusinessId != null) valNubeBusinessId.setText(businessId);

            if (valNubeEstado != null) {
                if (habilitado && conectado) {
                    valNubeEstado.setText(IdiomaUtil.obtener("ctrl.red.nube.conectada"));
                    valNubeEstado.setStyle("-fx-text-fill: #a8b991;");
                } else if (habilitado) {
                    valNubeEstado.setText(IdiomaUtil.obtener("ctrl.red.nube.sin_conexion"));
                    valNubeEstado.setStyle("-fx-text-fill: #daa520;");
                } else {
                    valNubeEstado.setText(IdiomaUtil.obtener("ctrl.red.nube.deshabilitada"));
                    valNubeEstado.setStyle("-fx-text-fill: #555;");
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
