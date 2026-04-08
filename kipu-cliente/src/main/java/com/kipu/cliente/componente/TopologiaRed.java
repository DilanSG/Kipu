/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente.componente;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.utilidad.MonitorConexion;
import com.kipu.common.dto.ClienteConectadoDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Componente reutilizable de topología de red visual para Kipu.
 *
 * Dibuja un diagrama animado de la red LAN con nodos descriptivos:
 * - Clientes (izquierda): monitores verdes, cargados dinámicamente, alternando arriba/abajo
 * - Host (centro-izquierda): monitor destacado dorado
 * - Servidor (centro-derecha): 3 unidades de rack apiladas, dorado
 * - PostgreSQL (derecha): forma de cilindro, azul
 * - Nube (arriba del backbone servidor↔DB): forma SVG cloud, gris (sin internet por diseño LAN)
 *
 * Los clientes se posicionan a la izquierda del HOST: el primero alineado horizontalmente,
 * el segundo arriba, el tercero abajo, alternando para mantener equilibrio visual.
 * Cada cliente se conecta al HOST mediante un nodo invisible que crea un ángulo de 90°.
 *
 * Todas las conexiones usan líneas ortogonales (solo horizontales y verticales)
 * con animaciones de flujo de datos y flechas bidireccionales.
 *
 * Este componente gestiona su propio ciclo de vida de animaciones y puede usarse
 * desde cualquier parte del programa: herramientas de configuración, popup rápido
 * en el footer, vista de login, etc.
 *
 * Uso básico:
 * <pre>
 *     TopologiaRed topologia = new TopologiaRed();
 *     Pane diagrama = topologia.construir();
 *     contenedor.getChildren().add(diagrama);
 *     // Al cerrar: topologia.detenerAnimaciones();
 * </pre>
 *
 * Popup rápido (desde cualquier pantalla):
 * <pre>
 *     TopologiaRed.mostrarPopupRapido(contenedorRaiz, eventoMouse);
 * </pre>
 *
 * @see MonitorConexion
 */
public class TopologiaRed {

    private static final Logger logger = LoggerFactory.getLogger(TopologiaRed.class);

    // ==================== ANIMACIONES ====================

    /** Lista de animaciones activas en la topología actual */
    private final List<Animation> animaciones = new ArrayList<>();
    private final boolean animarActivo = !MotorAnimaciones.instancia().isDesactivadas();

    /** Animación principal (flujo servidor-host), referencia separada para control */
    private Timeline animacionPrincipal;

    // ==================== API PÚBLICA ====================

    /**
     * Construye el diagrama completo de topología de red.
     *
     * Layout (centrado en un panel 700×320):
     * - CLIENTEs (izquierda): monitores verdes, alternando arriba/abajo
     * - HOST (centro-izquierda): monitor dorado
     * - SERVIDOR (centro-derecha) ←→ PostgreSQL (derecha): backbone horizontal
     * - NUBE (arriba del backbone servidor↔DB): SVG cloud, gris
     *
     * @return Pane con la topología visual animada
     */
    public Pane construir() {
        Pane pane = new Pane();
        pane.setPrefSize(700, 320);
        pane.setMinHeight(320);
        pane.setMaxHeight(500);
        pane.setStyle("-fx-background-color: #0a0a0a; -fx-background-radius: 8; " +
            "-fx-border-color: #1e1e1e; -fx-border-radius: 8;");

        // ── Coordenadas del layout horizontal (700×320) ──
        // CLIENTEs(izq) → HOST(centro-izq) → SERVIDOR(centro-der) ↔ PostgreSQL(der)
        //                                        NUBE(arriba del backbone serv↔db)
        double servX = 330, servY = 190;
        double dbX = 460, dbY = 190;
        double hostX = 220, hostY = 190;

        // ═══════════════════════════════════════════════════════
        // ══ NUBE (arriba del backbone servidor↔DB, SVG cloud) ══
        // ═══════════════════════════════════════════════════════
        SVGPath nubeShape = new SVGPath();
        nubeShape.setContent(
            "M 20 16 C 20 11.6 16.4 8 12 8 C 8.6 8 5.7 10 4.5 12.9 " +
            "C 2 13.2 0 15.4 0 18 C 0 20.8 2.2 23 5 23 L 19 23 " +
            "C 21.2 23 23 21.2 23 19 C 23 17 21.7 15.3 20 16 Z");
        nubeShape.setFill(Color.web("#141414"));
        nubeShape.setStroke(Color.web("#444"));
        nubeShape.setStrokeWidth(1);
        nubeShape.setScaleX(1.8);
        nubeShape.setScaleY(1.8);
        // nubeX/nubeY se calculan después de conocer el backbone

        Label lNube = new Label("NUBE");
        lNube.setStyle("-fx-text-fill: #444; -fx-font-size: 8px; -fx-font-weight: 700;");

        // Label secundario para info de nube (cluster/barId, se llena async)
        Label lNubeInfo = new Label("");
        lNubeInfo.setStyle("-fx-text-fill: #555; -fx-font-size: 6px;");

        // ═══════════════════════════════════════════════════
        // ══ PostgreSQL DB (derecha, forma de cilindro) ══
        // ═══════════════════════════════════════════════════
        Group grupoDb = crearFormaBaseDatos(dbX, dbY, "#5b9bd5");

        Label lDb = new Label("PostgreSQL");
        lDb.setStyle("-fx-text-fill: #5b9bd5; -fx-font-size: 8px; -fx-font-weight: 600;");
        lDb.setLayoutX(dbX - 22);
        lDb.setLayoutY(dbY + 26);

        // ═══════════════════════════════════════════════
        // ══ SERVIDOR (izquierda-medio, rack apilado) ══
        // ═══════════════════════════════════════════════
        double rackW = 40, rackH = 11, rackGap = 2;
        double rackStartX = servX - rackW / 2;
        double rackStartY = servY - (rackH * 3 + rackGap * 2) / 2;

        Group grupoServidor = new Group();
        for (int i = 0; i < 3; i++) {
            double ry = rackStartY + i * (rackH + rackGap);
            Rectangle rack = new Rectangle(rackStartX, ry, rackW, rackH);
            rack.setArcWidth(4);
            rack.setArcHeight(4);
            rack.setFill(Color.web("#141414"));
            rack.setStroke(Color.web("#d4af37"));
            rack.setStrokeWidth(1.1);

            Circle led = new Circle(rackStartX + 5, ry + rackH / 2, 1.8);
            led.setFill(Color.web("#d4af37"));

            Line linea1 = new Line(rackStartX + 12, ry + rackH / 2,
                rackStartX + rackW - 5, ry + rackH / 2);
            linea1.setStroke(Color.web("#2a2a2a"));
            linea1.setStrokeWidth(0.5);

            grupoServidor.getChildren().addAll(rack, led, linea1);
        }

        Label lServ = new Label("SERVIDOR");
        lServ.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 8px; -fx-font-weight: 700;");
        lServ.setLayoutX(servX - 22);
        lServ.setLayoutY(servY + 26);

        // Pulso suave en servidor
        FadeTransition pulsoServ = null;
        if (animarActivo) {
            pulsoServ = new FadeTransition(Duration.seconds(1.5), grupoServidor);
            pulsoServ.setFromValue(0.75);
            pulsoServ.setToValue(1.0);
            pulsoServ.setCycleCount(Animation.INDEFINITE);
            pulsoServ.setAutoReverse(true);
            pulsoServ.play();
            animaciones.add(pulsoServ);
        }

        // ═══════════════════════════════════════════════
        // ══ HOST (debajo del servidor, monitor dorado) ══
        // ═══════════════════════════════════════════════
        Group grupoHost = crearFormaMonitor(hostX, hostY, "#d4af37");

        // Nombre del host: si es este equipo en modo host, mostrar nombre configurado
        String textoHost = "HOST";
        if (ConfiguracionCliente.isHostMode()) {
            String nombre = ConfiguracionCliente.getNombreCliente();
            textoHost = (nombre != null && !nombre.isEmpty()) ? nombre : "HOST";
        }
        Label lHost = new Label(textoHost);
        lHost.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 9px; -fx-font-weight: 700;");
        lHost.setLayoutX(hostX - textoHost.length() * 3);
        lHost.setLayoutY(hostY + 28);

        // IP del host debajo del nombre
        Label lHostIp = new Label(obtenerIPLocal());
        lHostIp.setStyle("-fx-text-fill: #666; -fx-font-size: 6px;");
        lHostIp.setLayoutX(hostX - 20);
        lHostIp.setLayoutY(hostY + 39);

        // Pulso suave en el host
        if (animarActivo) {
            FadeTransition pulsoHost = new FadeTransition(Duration.seconds(2.0), grupoHost);
            pulsoHost.setFromValue(0.8);
            pulsoHost.setToValue(1.0);
            pulsoHost.setCycleCount(Animation.INDEFINITE);
            pulsoHost.setAutoReverse(true);
            pulsoHost.play();
            animaciones.add(pulsoHost);
        }

        // ═════════════════════════════════════════════════════════════
        // ══ LÍNEAS ORTOGONALES (solo horizontales y verticales) ══
        // ═════════════════════════════════════════════════════════════

        // ── Backbone horizontal: SERVIDOR ↔ PostgreSQL ──
        double backboneIzq = servX + rackW / 2 + 4;
        double backboneDer = dbX - 18;
        Line lineaBackbone = new Line(backboneIzq, servY, backboneDer, servY);
        lineaBackbone.setStroke(Color.web("#5b9bd5"));
        lineaBackbone.setStrokeWidth(1.2);
        lineaBackbone.getStrokeDashArray().addAll(6.0, 8.0);
        lineaBackbone.setStrokeLineCap(StrokeLineCap.ROUND);
        Polygon flechaBackboneDer = crearFlecha(backboneIzq, servY, backboneDer, servY, "#5b9bd5");
        Polygon flechaBackboneIzq = crearFlecha(backboneDer, servY, backboneIzq, servY, "#5b9bd5");

        // Animación backbone SERVIDOR ↔ DB
        Timeline animBackbone = null;
        if (animarActivo) {
            animBackbone = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(lineaBackbone.strokeDashOffsetProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(1.2),
                    new KeyValue(lineaBackbone.strokeDashOffsetProperty(), 14, Interpolator.LINEAR))
            );
            animBackbone.setCycleCount(Animation.INDEFINITE);
            animBackbone.play();
            animaciones.add(animBackbone);
        }

        // ── Nube → Backbone (vertical, gris) ──
        // La nube se posiciona en el punto medio del backbone serv↔db
        double nubeX = (backboneIzq + backboneDer) / 2;
        double nubeY = 80;
        nubeShape.setLayoutX(nubeX - 11.5);
        nubeShape.setLayoutY(nubeY - 15.5);
        lNube.setLayoutX(nubeX - 10);
        lNube.setLayoutY(nubeY + 18);
        lNubeInfo.setLayoutX(nubeX - 30);
        lNubeInfo.setLayoutY(nubeY + 28);

        double nubeBottomY = nubeY + 25;
        double nubeTargetY = servY;
        Line lineaNubeBackbone = new Line(nubeX, nubeBottomY, nubeX, nubeTargetY);
        lineaNubeBackbone.setStroke(Color.web("#2a2a2a"));
        lineaNubeBackbone.setStrokeWidth(1);
        lineaNubeBackbone.getStrokeDashArray().addAll(6.0, 8.0);
        lineaNubeBackbone.setStrokeLineCap(StrokeLineCap.ROUND);
        Circle puntoNubeArriba = new Circle(nubeX, nubeBottomY, 2.5, Color.web("#2a2a2a"));
        Circle puntoNubeAbajo = new Circle(nubeX, nubeTargetY, 2.5, Color.web("#2a2a2a"));

        // ── HOST → SERVIDOR (horizontal, dorada) ──
        double hostDerX = hostX + 20;
        double servIzqX = servX - rackW / 2 - 4;
        Line lineaServHost = new Line(hostDerX, hostY, servIzqX, servY);
        lineaServHost.setStroke(Color.web("#d4af37"));
        lineaServHost.setStrokeWidth(1.5);
        lineaServHost.getStrokeDashArray().addAll(8.0, 10.0);
        lineaServHost.setStrokeLineCap(StrokeLineCap.ROUND);
        Polygon flechaHostDerecha = crearFlecha(hostDerX, hostY, servIzqX, servY, "#d4af37");
        Polygon flechaServIzquierda = crearFlecha(servIzqX, servY, hostDerX, hostY, "#d4af37");

        // Animación HOST → SERVIDOR (marching ants van hacia el servidor)
        Timeline animServHost = null;
        if (animarActivo) {
            animServHost = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(lineaServHost.strokeDashOffsetProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(1.0),
                    new KeyValue(lineaServHost.strokeDashOffsetProperty(), -18, Interpolator.LINEAR))
            );
            animServHost.setCycleCount(Animation.INDEFINITE);
            animServHost.play();
            animacionPrincipal = animServHost;
            animaciones.add(animServHost);
        }

        // ── Agregar todos los nodos y conexiones al panel ──
        pane.getChildren().addAll(
            // Líneas primero (quedan detrás de los nodos)
            lineaNubeBackbone, puntoNubeArriba, puntoNubeAbajo,
            lineaBackbone, flechaBackboneDer, flechaBackboneIzq,
            lineaServHost, flechaHostDerecha, flechaServIzquierda,
            // Nodos encima
            nubeShape, grupoDb, grupoServidor, grupoHost,
            // Labels
            lNube, lNubeInfo, lDb, lServ, lHost, lHostIp);

        // ── Verificar conexión al servidor y actualizar estado visual ──
        verificarConexionServidor(
            lineaBackbone, flechaBackboneDer, flechaBackboneIzq, animBackbone,
            lineaServHost, flechaHostDerecha, flechaServIzquierda, animServHost,
            grupoServidor, grupoDb, lServ, lDb, pulsoServ,
            pane, hostX, hostY,
            nubeShape, lNube, lNubeInfo, lineaNubeBackbone, puntoNubeArriba, puntoNubeAbajo);

        return pane;
    }

    /**
     * Verifica la conexión al servidor y actualiza el estado visual de toda la topología.
     * Si desconectado: todo gris excepto el equipo actual. Si conectado: colores normales + carga clientes + nube.
     */
    private void verificarConexionServidor(
            Line lineaBackbone, Polygon flechaBackboneDer, Polygon flechaBackboneIzq, Timeline animBackbone,
            Line lineaServHost, Polygon flechaHostDer, Polygon flechaServIzq, Timeline animServHost,
            Group grupoServidor, Group grupoDb, Label lServ, Label lDb, FadeTransition pulsoServ,
            Pane pane, double hostX, double hostY,
            SVGPath nubeShape, Label lNube, Label lNubeInfo,
            Line lineaNube, Circle puntoArriba, Circle puntoAbajo) {

        CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/usuarios/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                conn.disconnect();
                return code >= 200 && code < 400;
            } catch (Exception e) {
                return false;
            }
        }).thenAccept(conectado -> Platform.runLater(() -> {
            if (!conectado) {
                // ── DESCONECTADO: todo gris, sin animaciones ──
                String gris = "#2a2a2a";
                lineaBackbone.setStroke(Color.web(gris));
                flechaBackboneDer.setFill(Color.web(gris));
                flechaBackboneIzq.setFill(Color.web(gris));
                animBackbone.stop();

                lineaServHost.setStroke(Color.web(gris));
                flechaHostDer.setFill(Color.web(gris));
                flechaServIzq.setFill(Color.web(gris));
                animServHost.stop();

                grupoServidor.setOpacity(0.3);
                grupoDb.setOpacity(0.3);
                pulsoServ.stop();

                lServ.setStyle("-fx-text-fill: #333; -fx-font-size: 8px; -fx-font-weight: 700;");
                lDb.setStyle("-fx-text-fill: #333; -fx-font-size: 8px; -fx-font-weight: 600;");
                // Nube también gris
                lineaNube.setStroke(Color.web(gris));
                puntoArriba.setFill(Color.web(gris));
                puntoAbajo.setFill(Color.web(gris));
            } else {
                // ── CONECTADO: cargar clientes y verificar nube ──
                cargarClientesConectados(pane, hostX, hostY);
                actualizarEstadoNube(nubeShape, lNube, lNubeInfo,
                        lineaNube, puntoArriba, puntoAbajo);
            }
        }));
    }

    /**
     * Consulta el estado de sincronización nube vía /api/sync/estado
     * y actualiza el nodo cloud en la topología con colores y datos reales.
     */
    private void actualizarEstadoNube(SVGPath nubeShape, Label lNube, Label lNubeInfo,
                                       Line lineaNube, Circle puntoArriba, Circle puntoAbajo) {
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
                logger.debug("No se pudo obtener estado nube: {}", e.getMessage());
            }
            return null;
        }).thenAccept(estado -> Platform.runLater(() -> {
            if (estado == null) return;

            boolean habilitado = Boolean.TRUE.equals(estado.get("habilitado"));
            boolean conectado = Boolean.TRUE.equals(estado.get("conexionNube"));
            String businessId = estado.get("businessId") != null ? estado.get("businessId").toString() : "";
            String mongoUri = estado.get("mongoUri") != null ? estado.get("mongoUri").toString() : "";

            if (habilitado && conectado) {
                // Nube activa y conectada: verde
                nubeShape.setStroke(Color.web("#a8b991"));
                nubeShape.setFill(Color.web("#0f1a0c"));
                lNube.setText("ATLAS");
                lNube.setStyle("-fx-text-fill: #a8b991; -fx-font-size: 8px; -fx-font-weight: 700;");
                lineaNube.setStroke(Color.web("#a8b991"));
                puntoArriba.setFill(Color.web("#a8b991"));
                puntoAbajo.setFill(Color.web("#a8b991"));
                // Extraer cluster del URI enmascarado
                String cluster = extraerCluster(mongoUri);
                lNubeInfo.setText(cluster + " · " + businessId);
                lNubeInfo.setStyle("-fx-text-fill: #6a8a55; -fx-font-size: 6px;");
                // Animar línea nube con flujo activo
                if (animarActivo) {
                    Timeline animNube = new Timeline(
                        new KeyFrame(Duration.ZERO,
                            new KeyValue(lineaNube.strokeDashOffsetProperty(), 0, Interpolator.LINEAR)),
                        new KeyFrame(Duration.seconds(1.2),
                            new KeyValue(lineaNube.strokeDashOffsetProperty(), 14, Interpolator.LINEAR))
                    );
                    animNube.setCycleCount(Animation.INDEFINITE);
                    animNube.play();
                    animaciones.add(animNube);
                }
            } else if (habilitado) {
                // Habilitado pero sin conexión: ámbar
                nubeShape.setStroke(Color.web("#daa520"));
                lNube.setText("ATLAS");
                lNube.setStyle("-fx-text-fill: #daa520; -fx-font-size: 8px; -fx-font-weight: 700;");
                lineaNube.setStroke(Color.web("#daa520"));
                puntoArriba.setFill(Color.web("#daa520"));
                puntoAbajo.setFill(Color.web("#daa520"));
                lNubeInfo.setText("Sin conexi\u00f3n");
                lNubeInfo.setStyle("-fx-text-fill: #b8860b; -fx-font-size: 6px;");
            }
            // Si no habilitado: se queda gris (estado por defecto)
        }));
    }

    /** Extrae el nombre del cluster del parámetro appName de la URI MongoDB. */
    private String extraerCluster(String mongoUri) {
        if (mongoUri == null || mongoUri.isBlank()) return "";
        // mongodb+srv://***@host.mongodb.net/?appName=KipuPC → KipuPC
        int idx = mongoUri.indexOf("appName=");
        if (idx >= 0) {
            String valor = mongoUri.substring(idx + "appName=".length());
            int amp = valor.indexOf('&');
            return amp > 0 ? valor.substring(0, amp) : valor;
        }
        return "";
    }

    /**
     * Detiene todas las animaciones activas de la topología y limpia la lista.
     * Debe llamarse al cerrar/destruir la vista que contiene la topología.
     */
    public void detenerAnimaciones() {
        animaciones.forEach(Animation::stop);
        animaciones.clear();
        if (animacionPrincipal != null) {
            animacionPrincipal.stop();
            animacionPrincipal = null;
        }
    }

    /**
     * Obtiene la lista mutable de animaciones activas.
     * Útil para que sistemas externos (como GestorModales) puedan
     * detener las animaciones al cerrar un modal.
     *
     * @return Lista de animaciones activas
     */
    public List<Animation> getAnimaciones() {
        return animaciones;
    }

    /**
     * Obtiene la animación principal de la topología.
     *
     * @return Animación principal (flujo servidor-host) o null
     */
    public Timeline getAnimacionPrincipal() {
        return animacionPrincipal;
    }

    // ==================== POPUP RÁPIDO (ESTÁTICO) ====================

    /**
     * Muestra un popup flotante pequeño con el diagrama de topología de red,
     * posicionado junto al punto de clic (estilo indicador de volumen del SO).
     *
     * El popup es compacto (~420×320px), aparece anclado al clic y contiene
     * la topología en un área arrastrable para explorar las partes ocultas.
     * El título muestra el estado de conexión actual desde {@link MonitorConexion}.
     *
     * Funciona desde cualquier pantalla (login, menú principal, etc.).
     *
     * @param contenedorRaiz StackPane sobre el cual montar el popup
     * @param evento         MouseEvent del clic para calcular la posición de aparición
     */
    public static void mostrarPopupRapido(StackPane contenedorRaiz, MouseEvent evento) {
        logger.info("Mostrando diagrama de red rápido");

        // Crear instancia del componente para esta invocación
        TopologiaRed componente = new TopologiaRed();

        // ─── Overlay transparente (captura clics fuera) ───
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: transparent;");
        overlay.setAlignment(Pos.TOP_LEFT);

        // ─── Popup flotante compacto ───
        VBox popup = new VBox(0);
        popup.setMinWidth(420);
        popup.setPrefWidth(420);
        popup.setMaxWidth(420);
        popup.setPrefHeight(310);
        popup.setMaxHeight(310);
        popup.setPadding(Insets.EMPTY);
        popup.setStyle(
            "-fx-background-color: #111111; " +
            "-fx-border-color: rgba(212,175,55,0.5); " +
            "-fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 20, 0.3, 0, 4);");
        popup.setManaged(false);
        popup.setFillWidth(true);
        // Evitar que eventos del popup se propaguen al overlay
        popup.setOnMousePressed(MouseEvent::consume);
        popup.setOnMouseDragged(MouseEvent::consume);

        // ─── Barra de título estilo ventana ───
        MonitorConexion monitor = MonitorConexion.getInstancia();
        String colorEstado = monitor.getEstado().getColor();

        Circle circuloEstado = new Circle(5, Color.web(colorEstado));

        // Label con latencia en ms (solo visible en este popup)
        long latenciaInicial = monitor.getUltimoTiempoRespuesta();
        String textoMs = latenciaInicial >= 0 ? latenciaInicial + "ms" : "—";
        Label tituloEstado = new Label(textoMs);
        tituloEstado.getStyleClass().add("texto-hint-sm");
        tituloEstado.setStyle("-fx-font-weight: 600; -fx-text-fill: " + colorEstado + ";");
        tituloEstado.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tituloEstado, Priority.ALWAYS);

        // Bind reactivo al estado de conexión (actualiza color y ms)
        monitor.estadoProperty().addListener((obs, viejo, nuevo) -> {
            circuloEstado.setFill(Color.web(nuevo.getColor()));
            long ms = monitor.getUltimoTiempoRespuesta();
            tituloEstado.setText(ms >= 0 ? ms + "ms" : "—");
            tituloEstado.setStyle("-fx-font-weight: 600; -fx-text-fill: " + nuevo.getColor() + ";");
        });
        monitor.textoEstadoProperty().addListener((obs, viejo, nuevo) -> {
            long ms = monitor.getUltimoTiempoRespuesta();
            tituloEstado.setText(ms >= 0 ? ms + "ms" : "—");
        });

        Label cerrar = new Label("✕");
        cerrar.setMinSize(24, 24);
        cerrar.setPrefSize(24, 24);
        cerrar.setAlignment(Pos.CENTER);
        cerrar.getStyleClass().add("texto-info");
        cerrar.setStyle("-fx-text-fill: #666; -fx-cursor: hand;");
        cerrar.setOnMouseEntered(e ->
            cerrar.setStyle("-fx-text-fill: #f5f5f5; -fx-cursor: hand; " +
                "-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 4;"));
        cerrar.setOnMouseExited(e ->
            cerrar.setStyle("-fx-text-fill: #666; -fx-cursor: hand;"));

        HBox barraTitulo = new HBox(8, circuloEstado, tituloEstado, cerrar);
        barraTitulo.setAlignment(Pos.CENTER_LEFT);
        barraTitulo.setPadding(new Insets(8, 8, 8, 14));
        barraTitulo.setMinWidth(400);
        barraTitulo.setPrefWidth(400);
        barraTitulo.setMaxWidth(Double.MAX_VALUE);
        barraTitulo.setStyle(
            "-fx-background-color: #0d0d0d; " +
            "-fx-background-radius: 10 10 0 0; " +
            "-fx-border-color: transparent transparent rgba(212,175,55,0.12) transparent; " +
            "-fx-border-width: 0 0 1 0;");

        // ─── Topología arrastrable ───
        Pane topologia = componente.construir();

        // Fondo fijo grande (4x el contenedor) para que al arrastrar no se vea transparente
        Region fondoFijo = new Region();
        fondoFijo.setPrefSize(1600, 1100);
        fondoFijo.setMinSize(1600, 1100);
        fondoFijo.setStyle("-fx-background-color: #0a0a0a;");
        fondoFijo.setLayoutX(-600);
        fondoFijo.setLayoutY(-400);
        fondoFijo.setMouseTransparent(true);

        // Contenedor con clip para recortar la topología al área visible
        Pane contenedorTopologia = new Pane(fondoFijo, topologia);
        contenedorTopologia.setStyle("-fx-background-color: #0a0a0a;");
        contenedorTopologia.setPrefSize(396, 270);
        contenedorTopologia.setMaxSize(396, 270);
        contenedorTopologia.setMinSize(396, 270);

        // Clip con bordes redondeados solo abajo (la barra de título tiene los de arriba)
        Rectangle clip = new Rectangle(396, 270);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        contenedorTopologia.setClip(clip);

        // Centrar la topología inicialmente (desplazamiento para que el servidor quede visible)
        topologia.setLayoutX(-100);
        topologia.setLayoutY(-10);

        // Arrastre del contenido de la topología (solo mueve el contenido, no el popup)
        final double[] dragAnchor = new double[2];
        final double[] offsetInicial = new double[2];
        topologia.setOnMousePressed(e -> {
            dragAnchor[0] = e.getScreenX();
            dragAnchor[1] = e.getScreenY();
            offsetInicial[0] = topologia.getLayoutX();
            offsetInicial[1] = topologia.getLayoutY();
            topologia.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            e.consume();
        });
        topologia.setOnMouseDragged(e -> {
            double dx = e.getScreenX() - dragAnchor[0];
            double dy = e.getScreenY() - dragAnchor[1];
            topologia.setLayoutX(offsetInicial[0] + dx);
            topologia.setLayoutY(offsetInicial[1] + dy);
            e.consume();
        });
        topologia.setOnMouseReleased(e -> {
            topologia.setCursor(javafx.scene.Cursor.OPEN_HAND);
            e.consume();
        });
        topologia.setCursor(javafx.scene.Cursor.OPEN_HAND);

        // Consumir eventos del contenedor para que no se propaguen al overlay
        contenedorTopologia.setOnMousePressed(MouseEvent::consume);
        contenedorTopologia.setOnMouseDragged(MouseEvent::consume);

        VBox.setVgrow(contenedorTopologia, Priority.ALWAYS);
        popup.getChildren().addAll(barraTitulo, contenedorTopologia);

        // ─── Posicionar popup junto al clic ───
        javafx.geometry.Point2D puntoEnRaiz = contenedorRaiz.screenToLocal(
            evento.getScreenX(), evento.getScreenY());

        double popupW = 420, popupH = 310;
        double rootW = contenedorRaiz.getWidth();
        double rootH = contenedorRaiz.getHeight();

        // Intentar posicionar arriba-izquierda del clic; si no cabe, ajustar
        double posX = puntoEnRaiz.getX() - popupW + 20;
        double posY = puntoEnRaiz.getY() - popupH - 10;

        // Ajustar si se sale de los límites
        if (posX < 8) posX = 8;
        if (posX + popupW > rootW - 8) posX = rootW - popupW - 8;
        if (posY < 8) posY = puntoEnRaiz.getY() + 20;
        if (posY + popupH > rootH - 8) posY = rootH - popupH - 8;

        popup.setLayoutX(posX);
        popup.setLayoutY(posY);

        overlay.getChildren().add(popup);

        // ─── Cerrar al clic fuera o en ✕ ───
        Runnable cerrarAccion = () -> {
            componente.detenerAnimaciones();
            FadeTransition salida = new FadeTransition(Duration.millis(120), popup);
            salida.setFromValue(1);
            salida.setToValue(0);
            salida.setOnFinished(e -> contenedorRaiz.getChildren().remove(overlay));
            salida.play();
        };
        cerrar.setOnMouseClicked(e -> cerrarAccion.run());
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) cerrarAccion.run();
        });

        contenedorRaiz.getChildren().add(overlay);

        // Animación de entrada
        if (!MotorAnimaciones.instancia().isDesactivadas()) {
            popup.setOpacity(0);
            popup.setScaleX(0.92);
            popup.setScaleY(0.92);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), popup);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), popup);
            scaleIn.setFromX(0.92);
            scaleIn.setFromY(0.92);
            scaleIn.setToX(1);
            scaleIn.setToY(1);
            scaleIn.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fadeIn, scaleIn).play();
        }
    }

    // ==================== FORMAS GRÁFICAS ====================

    /**
     * Crea un grupo con forma de cilindro (base de datos).
     * Dibuja: elipse superior + rectángulo + elipse inferior.
     *
     * @param cx    Centro X de la base de datos
     * @param cy    Centro Y de la base de datos
     * @param color Color del borde en formato hex
     * @return Group con la forma de la base de datos
     */
    private Group crearFormaBaseDatos(double cx, double cy, String color) {
        Group grupo = new Group();

        double w = 32, h = 24;
        double elipseH = 6;

        // Cuerpo rectangular
        Rectangle cuerpo = new Rectangle(cx - w / 2, cy - h / 2 + elipseH / 2, w, h - elipseH);
        cuerpo.setFill(Color.web("#0c0c0c"));
        cuerpo.setStroke(Color.TRANSPARENT);

        // Elipse superior
        Ellipse top = new Ellipse(cx, cy - h / 2 + elipseH / 2, w / 2, elipseH / 2);
        top.setFill(Color.web("#0c0c0c"));
        top.setStroke(Color.web(color));
        top.setStrokeWidth(1.2);

        // Elipse inferior
        Ellipse bottom = new Ellipse(cx, cy + h / 2 - elipseH / 2, w / 2, elipseH / 2);
        bottom.setFill(Color.web("#0c0c0c"));
        bottom.setStroke(Color.web(color));
        bottom.setStrokeWidth(1.2);

        // Bordes laterales
        Line izq = new Line(cx - w / 2, cy - h / 2 + elipseH / 2, cx - w / 2, cy + h / 2 - elipseH / 2);
        izq.setStroke(Color.web(color));
        izq.setStrokeWidth(1.2);

        Line der = new Line(cx + w / 2, cy - h / 2 + elipseH / 2, cx + w / 2, cy + h / 2 - elipseH / 2);
        der.setStroke(Color.web(color));
        der.setStrokeWidth(1.2);

        // Línea media decorativa
        Ellipse media = new Ellipse(cx, cy - h / 2 + elipseH / 2 + (h - elipseH) * 0.35, w / 2, elipseH / 3);
        media.setFill(Color.TRANSPARENT);
        media.setStroke(Color.web(color));
        media.setStrokeWidth(0.6);
        media.setOpacity(0.5);

        grupo.getChildren().addAll(cuerpo, izq, der, bottom, media, top);
        return grupo;
    }

    /**
     * Crea un grupo de nodos con forma de monitor/pantalla.
     * Dibuja: pantalla rectangular + soporte vertical + base.
     *
     * @param cx    Centro X del monitor
     * @param cy    Centro Y del monitor
     * @param color Color del borde en formato hex
     * @return Group con la forma del monitor
     */
    private Group crearFormaMonitor(double cx, double cy, String color) {
        Group grupo = new Group();

        // Pantalla
        double pw = 36, ph = 22;
        Rectangle pantalla = new Rectangle(cx - pw / 2, cy - ph / 2 - 4, pw, ph);
        pantalla.setArcWidth(4);
        pantalla.setArcHeight(4);
        pantalla.setFill(Color.web("#0c0c0c"));
        pantalla.setStroke(Color.web(color));
        pantalla.setStrokeWidth(1.3);

        // Soporte (línea vertical)
        Line soporte = new Line(cx, cy + ph / 2 - 4, cx, cy + ph / 2 + 2);
        soporte.setStroke(Color.web(color));
        soporte.setStrokeWidth(1.2);

        // Base
        Rectangle base = new Rectangle(cx - 8, cy + ph / 2 + 2, 16, 3);
        base.setArcWidth(2);
        base.setArcHeight(2);
        base.setFill(Color.web("#141414"));
        base.setStroke(Color.web(color));
        base.setStrokeWidth(0.8);

        // Punto de "encendido" en la pantalla
        Circle power = new Circle(cx, cy - 2, 2);
        power.setFill(Color.web(color));
        power.setOpacity(0.7);

        grupo.getChildren().addAll(pantalla, soporte, base, power);
        return grupo;
    }

    /**
     * Crea una flecha (triángulo) en el extremo final de una línea,
     * apuntando en la dirección desde (x1,y1) hacia (x2,y2).
     *
     * @param x1    Origen X de la línea
     * @param y1    Origen Y de la línea
     * @param x2    Destino X de la línea (donde va la flecha)
     * @param y2    Destino Y de la línea
     * @param color Color hex de la flecha
     * @return Polygon con forma de flecha orientada
     */
    private Polygon crearFlecha(double x1, double y1, double x2, double y2, String color) {
        double angulo = Math.atan2(y2 - y1, x2 - x1);
        double tamano = 7;
        double puntoX = x2;
        double puntoY = y2;

        double baseX1 = puntoX - Math.cos(angulo) * tamano + Math.sin(angulo) * tamano * 0.45;
        double baseY1 = puntoY - Math.sin(angulo) * tamano - Math.cos(angulo) * tamano * 0.45;
        double baseX2 = puntoX - Math.cos(angulo) * tamano - Math.sin(angulo) * tamano * 0.45;
        double baseY2 = puntoY - Math.sin(angulo) * tamano + Math.cos(angulo) * tamano * 0.45;

        Polygon flecha = new Polygon(puntoX, puntoY, baseX1, baseY1, baseX2, baseY2);
        flecha.setFill(Color.web(color));
        return flecha;
    }

    // ==================== CLIENTES CONECTADOS ====================

    /**
     * Consulta al servidor los clientes Kipu conectados y los dibuja
     * a la izquierda del HOST con alternancia arriba/abajo.
     *
     * Patrón de posicionamiento:
     * - N0: alineado horizontalmente con el HOST (conexión directa)
     * - N1: arriba del HOST (conexión con nodo invisible a 90°)
     * - N2: abajo del HOST (equilibrando)
     * - N3: arriba de N1, N4: abajo de N2, etc.
     *
     * Las conexiones usan líneas ortogonales con un nodo invisible
     * en (hostX, clienteY) que crea el ángulo de 90°.
     *
     * @param pane  Panel de topología donde agregar los nodos
     * @param hostX Posición X del HOST
     * @param hostY Posición Y del HOST
     */
    private void cargarClientesConectados(Pane pane, double hostX, double hostY) {
        CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(
                    ConfiguracionCliente.getUrlServidor() + "/api/sistema/clientes-conectados").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                String nombreCliente = ConfiguracionCliente.getNombreCliente();
                if (nombreCliente != null && !nombreCliente.isEmpty()) {
                    conn.setRequestProperty("X-Client-Name", nombreCliente);
                }
                // IP real del equipo para registro correcto a través de NAT
                String ipLocal = ConfiguracionCliente.getIpLocal();
                if (ipLocal != null && !ipLocal.isEmpty()) {
                    conn.setRequestProperty("X-Client-IP", ipLocal);
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = reader.readLine()) != null) {
                        sb.append(linea);
                    }
                    reader.close();
                    conn.disconnect();

                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> respuesta = mapper.readValue(
                        sb.toString(), new TypeReference<Map<String, Object>>() {});
                    Object datos = respuesta.get("datos");
                    if (datos != null) {
                        return mapper.convertValue(datos,
                            new TypeReference<List<ClienteConectadoDto>>() {});
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                logger.debug("No se pudo obtener clientes conectados: {}", e.getMessage());
            }
            return new ArrayList<ClienteConectadoDto>();
        }).thenAccept(clientes -> Platform.runLater(() -> {
            String miIp = obtenerIPLocal();
            String miNombre = ConfiguracionCliente.getNombreCliente();

            // Filtrar: excluir este equipo (por IP o nombre) y los marcados como host
            List<ClienteConectadoDto> clientesRemotos = clientes.stream()
                .filter(c -> !c.isEsHost())
                .filter(c -> {
                    if (c.getIp().equals(miIp) || c.getIp().equals("127.0.0.1")) return false;
                    if (miNombre != null && !miNombre.isEmpty()
                        && miNombre.equals(c.getNombreCliente())) return false;
                    return true;
                })
                .toList();

            if (clientesRemotos.isEmpty()) return;

            // ── Distribución a la izquierda del HOST con alternancia arriba/abajo ──
            // Patrón: N0→alineado con HOST, N1→arriba, N2→abajo, N3→arriba(2), N4→abajo(2)...
            double clienteX = hostX - 120;
            double espacioVertical = 65;

            // Calcular rango Y para ajustar el panel si es necesario
            double minY = hostY, maxY = hostY;
            for (int i = 0; i < clientesRemotos.size(); i++) {
                double offsetY = calcularOffsetY(i, espacioVertical);
                double y = hostY + offsetY;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }

            // Expandir panel si los clientes se salen por abajo
            double alturaRequerida = maxY + 55;
            if (alturaRequerida > pane.getPrefHeight()) {
                pane.setPrefHeight(alturaRequerida);
                pane.setMinHeight(alturaRequerida);
                pane.setMaxHeight(alturaRequerida);
            }

            // ── Dibujar cada cliente con conexión ortogonal al HOST ──
            for (int i = 0; i < clientesRemotos.size(); i++) {
                ClienteConectadoDto cli = clientesRemotos.get(i);
                double offsetY = calcularOffsetY(i, espacioVertical);
                double cliY = hostY + offsetY;

                Group monitorCli = crearFormaMonitor(clienteX, cliY, "#5a8a3c");

                // Label principal: nombre del equipo, o usuario+rol como fallback
                String nombreEquipo = cli.getNombreCliente();
                String textoCliente;
                if (nombreEquipo != null && !nombreEquipo.isEmpty()) {
                    textoCliente = nombreEquipo;
                } else {
                    textoCliente = cli.getNombreUsuario() + " (" + cli.getRol() + ")";
                }
                Label lCliente = new Label(textoCliente);
                lCliente.setStyle("-fx-text-fill: #7aa85d; -fx-font-size: 8px; -fx-font-weight: 600;");
                lCliente.setLayoutX(clienteX - textoCliente.length() * 2.5);
                lCliente.setLayoutY(cliY + 28);

                // Label secundario: IP (y usuario si tiene nombre de equipo)
                String textoSecundario;
                if (nombreEquipo != null && !nombreEquipo.isEmpty()) {
                    textoSecundario = cli.getIp() + " · " + cli.getNombreUsuario();
                } else {
                    textoSecundario = cli.getIp();
                }
                Label lIp = new Label(textoSecundario);
                lIp.setStyle("-fx-text-fill: #555; -fx-font-size: 7px;");
                lIp.setLayoutX(clienteX - textoSecundario.length() * 2);
                lIp.setLayoutY(cliY + 39);

                // Borde derecho del monitor del cliente
                double cliDerX = clienteX + 20;

                if (i == 0) {
                    // ── Cliente N0: conexión horizontal directa al HOST (mismo Y) ──
                    double hostIzqX = hostX - 20;
                    Line lineaDirecta = new Line(cliDerX, cliY, hostIzqX, cliY);
                    lineaDirecta.setStroke(Color.web("#5a8a3c"));
                    lineaDirecta.setStrokeWidth(1);
                    lineaDirecta.getStrokeDashArray().addAll(6.0, 8.0);
                    lineaDirecta.setStrokeLineCap(StrokeLineCap.ROUND);

                    Polygon flechaDer = crearFlecha(cliDerX, cliY, hostIzqX, cliY, "#5a8a3c");
                    Polygon flechaIzq = crearFlecha(hostIzqX, cliY, cliDerX, cliY, "#5a8a3c");

                    if (animarActivo) {
                        Timeline animDir = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                new KeyValue(lineaDirecta.strokeDashOffsetProperty(), 0, Interpolator.LINEAR)),
                            new KeyFrame(Duration.seconds(1.2),
                                new KeyValue(lineaDirecta.strokeDashOffsetProperty(), 14, Interpolator.LINEAR))
                        );
                        animDir.setCycleCount(Animation.INDEFINITE);
                        animDir.play();
                        animaciones.add(animDir);
                    }

                    pane.getChildren().addAll(lineaDirecta, flechaDer, flechaIzq,
                        monitorCli, lCliente, lIp);
                } else {
                    // ── Clientes N1+: conexión ortogonal con nodo invisible a 90° ──
                    // Tramo horizontal: cliente → nodo invisible (en hostX, cliY)
                    double nodoX = hostX;
                    Line lineaHorizontal = new Line(cliDerX, cliY, nodoX, cliY);
                    lineaHorizontal.setStroke(Color.web("#5a8a3c"));
                    lineaHorizontal.setStrokeWidth(1);
                    lineaHorizontal.getStrokeDashArray().addAll(6.0, 8.0);
                    lineaHorizontal.setStrokeLineCap(StrokeLineCap.ROUND);

                    // Tramo vertical: nodo invisible → borde del HOST
                    double vertTargetY = (offsetY < 0) ? hostY - 16 : hostY + 30;
                    Line lineaVertical = new Line(nodoX, cliY, nodoX, vertTargetY);
                    lineaVertical.setStroke(Color.web("#5a8a3c"));
                    lineaVertical.setStrokeWidth(1);
                    lineaVertical.getStrokeDashArray().addAll(6.0, 8.0);
                    lineaVertical.setStrokeLineCap(StrokeLineCap.ROUND);

                    // Flechas en los extremos
                    Polygon flechaH = crearFlecha(cliDerX, cliY, nodoX, cliY, "#5a8a3c");
                    Polygon flechaV = crearFlecha(cliY == vertTargetY ? nodoX : nodoX,
                        cliY, nodoX, vertTargetY, "#5a8a3c");

                    // Animación sincronizada para ambos tramos
                    if (animarActivo) {
                        Timeline animOrt = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                new KeyValue(lineaHorizontal.strokeDashOffsetProperty(), 0, Interpolator.LINEAR),
                                new KeyValue(lineaVertical.strokeDashOffsetProperty(), 0, Interpolator.LINEAR)),
                            new KeyFrame(Duration.seconds(1.2),
                                new KeyValue(lineaHorizontal.strokeDashOffsetProperty(), 14, Interpolator.LINEAR),
                                new KeyValue(lineaVertical.strokeDashOffsetProperty(), 14, Interpolator.LINEAR))
                        );
                        animOrt.setCycleCount(Animation.INDEFINITE);
                        animOrt.play();
                        animaciones.add(animOrt);
                    }

                    pane.getChildren().addAll(
                        lineaHorizontal, lineaVertical, flechaH, flechaV,
                        monitorCli, lCliente, lIp);
                }
            }
        }));
    }

    // ==================== UTILIDADES DE RED ====================

    /**
     * Calcula el desplazamiento Y para un cliente según su índice,
     * alternando arriba y abajo para mantener equilibrio visual.
     *
     * Patrón: 0→centro, 1→arriba, 2→abajo, 3→arriba(2), 4→abajo(2)...
     *
     * @param indice          Índice del cliente (0-based)
     * @param espacioVertical Separación vertical entre niveles
     * @return Offset Y respecto al HOST (negativo=arriba, positivo=abajo)
     */
    private double calcularOffsetY(int indice, double espacioVertical) {
        if (indice == 0) return 0;
        int nivel = (indice + 1) / 2;
        boolean arriba = (indice % 2 == 1);
        return arriba ? -nivel * espacioVertical : nivel * espacioVertical;
    }

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
        return "No disponible";
    }
}
