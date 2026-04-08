/* Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.*/
package com.kipu.cliente.componente;

import com.kipu.cliente.utilidad.IdiomaUtil;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import java.util.ArrayList;
import java.util.List;

/* Tutorial interactivo para la conexión al servidor y configuración de nube.
 * Card flotante con flechas que señalan los elementos relevantes del panel.*/
public class TutorialNube {

    public enum Evento {
        SERVIDOR_INICIANDO,
        SERVIDOR_CONECTADO,
        SERVIDOR_YA_ACTIVO,
        SERVIDOR_ERROR,
        NUBE_CONFIG_ABIERTA,
        NUBE_CONFIGURADA,
        NUBE_ERROR
    }

    private int pasoActual = 0;
    private boolean activo = false;
    private boolean nubeConfigurada = false;
    private boolean servidorConectado = false;
    private StackPane contenedorOverlay;
    private VBox cardFlotante;
    private VBox contenidoCard;
    private HBox indicadorProgreso;
    private HBox barraNavegacion;
    private Button botonSiguiente;
    private Button botonVolver;
    private final List<Group> gruposFlechas = new ArrayList<>();

    // Nodos objetivo
    private Node nodoCardLan;
    private Node nodoCardNube;
    private Node nodoBotonConectar;
    private Node nodoIpClientes;
    private Node nodoBotonApagar;
    private Node nodoBotonConfigurar;
    private Node nodoCampoDbNombre;
    private Node nodoCampoBusinessId;
    private Node nodoOrigenFlecha; // Nodo interno de la card desde donde salen las flechas (tipBox amarillo)
    private static final int TOTAL_PASOS = 7;
    private static final double CARD_ANCHO = 540;

    private String ipLocal = "";

    /* Registra los nodos del panel para posicionamiento y flechas.*/
    public void registrarNodos(Node cardLan, Node cardNube, Node botonConectar, Node ipClientes, Node botonApagar, Node botonConfigurar, Node campoDbNombre, Node campoBusinessId) {
        this.nodoCardLan = cardLan;
        this.nodoCardNube = cardNube;
        this.nodoBotonConectar = botonConectar;
        this.nodoIpClientes = ipClientes;
        this.nodoBotonApagar = botonApagar;
        this.nodoBotonConfigurar = botonConfigurar;
        this.nodoCampoDbNombre = campoDbNombre;
        this.nodoCampoBusinessId = campoBusinessId;
    }

    public void setIpLocal(String ip) { this.ipLocal = ip != null ? ip : ""; }
    public void setNubeConfigurada(boolean valor) { this.nubeConfigurada = valor; }

    // Muestra el tutorial como card flotante dentro del overlay dado.
    public void iniciar(StackPane overlay) {
        this.contenedorOverlay = overlay;
        this.activo = true;
        this.pasoActual = 0;

        construirCard();
        mostrarPaso(0);

        cardFlotante.setTranslateY(-12);
        cardFlotante.setOpacity(0);
        MotorAnimaciones.fadeYDeslizar(cardFlotante, 0, 1, -12, 0, 150, null);
    }

    public void detener() {
        activo = false;
        if (cardFlotante != null && contenedorOverlay != null) {
            MotorAnimaciones.fade(cardFlotante, 1, 0, 120, () -> {
                limpiarFlechas();
                contenedorOverlay.getChildren().remove(cardFlotante);
            });
        }
    }

    public boolean isActivo() { return activo; }

    // Recibe un evento del panel de conexión y avanza si corresponde.
    public void notificarEvento(Evento evento) {
        if (!activo) return;

        switch (evento) {
            case SERVIDOR_INICIANDO -> {
                if (pasoActual == 1) avanzarA(2);
            }
            case SERVIDOR_CONECTADO, SERVIDOR_YA_ACTIVO -> {
                servidorConectado = true;
                if (pasoActual == 1 || pasoActual == 2) avanzarA(3);
            }
            case SERVIDOR_ERROR -> {
                if (pasoActual == 1 || pasoActual == 2) mostrarErrorServidor();
            }
            case NUBE_CONFIG_ABIERTA -> {
                if (pasoActual == 4) avanzarA(5);
            }
            case NUBE_CONFIGURADA -> {
                nubeConfigurada = true;
                if (pasoActual == 5) avanzarA(6);
            }
            case NUBE_ERROR -> {
                if (pasoActual == 5) mostrarErrorNube();
            }
        }
    }

    private void construirCard() {
        cardFlotante = new VBox(0);
        cardFlotante.setMaxWidth(CARD_ANCHO);
        cardFlotante.setMinWidth(CARD_ANCHO);
        cardFlotante.setMaxHeight(Region.USE_PREF_SIZE);
        cardFlotante.setPickOnBounds(false);
        cardFlotante.setStyle(
            "-fx-background-color: rgba(14,14,14,0.98); -fx-background-radius: 14; " +
            "-fx-border-color: rgba(212,175,55,0.3); -fx-border-radius: 14; -fx-border-width: 1.5; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 4);");

        // ── Header: título + indicadores + botón cerrar ──
        Label labelTitulo = new Label(IdiomaUtil.obtener("tutorial.titulo"));
        labelTitulo.getStyleClass().add("tutorial-titulo");

        indicadorProgreso = new HBox(6);
        indicadorProgreso.setAlignment(Pos.CENTER);
        indicadorProgreso.setPadding(new Insets(0, 12, 0, 12));

        Region spacerHeader = new Region();
        HBox.setHgrow(spacerHeader, Priority.ALWAYS);

        Label botonCerrar = new Label(IdiomaUtil.obtener("tutorial.omitir") + "  \u2715");
        botonCerrar.getStyleClass().add("tutorial-disclaimer");
        botonCerrar.setStyle(
            "-fx-text-fill: #555; -fx-cursor: hand; -fx-padding: 4 8;");
        botonCerrar.setOnMouseEntered(e -> botonCerrar.setStyle(
            "-fx-text-fill: #e8e8e8; -fx-cursor: hand; -fx-padding: 4 8; " +
            "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 6;"));
        botonCerrar.setOnMouseExited(e -> botonCerrar.setStyle(
            "-fx-text-fill: #555; -fx-cursor: hand; -fx-padding: 4 8;"));
        botonCerrar.setOnMouseClicked(e -> detener());

        HBox header = new HBox(8, labelTitulo, indicadorProgreso, spacerHeader, botonCerrar);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 10, 20));
        header.setStyle("-fx-border-color: rgba(255,255,255,0.04); -fx-border-width: 0 0 1 0;");

        // ── Contenido del paso ──
        contenidoCard = new VBox(10);
        contenidoCard.setPadding(new Insets(14, 20, 10, 20));

        // ── Barra de navegación ──
        botonVolver = new Button("\u2190 " + IdiomaUtil.obtener("tutorial.volver"));
        botonVolver.getStyleClass().add("tutorial-disclaimer");
        botonVolver.setStyle(estiloBotonNav());
        botonVolver.setOnAction(e -> {
            if (pasoActual > 0) avanzarA(pasoActual - 1);
        });

        Region spacerNav = new Region();
        HBox.setHgrow(spacerNav, Priority.ALWAYS);

        botonSiguiente = new Button(IdiomaUtil.obtener("tutorial.siguiente") + " \u2192");
        botonSiguiente.getStyleClass().add("tutorial-disclaimer");
        botonSiguiente.setStyle(estiloBotonNavPrimario());

        barraNavegacion = new HBox(8, botonVolver, spacerNav, botonSiguiente);
        barraNavegacion.setAlignment(Pos.CENTER);
        barraNavegacion.setPadding(new Insets(6, 20, 14, 20));
        barraNavegacion.setStyle("-fx-border-color: rgba(255,255,255,0.04); -fx-border-width: 1 0 0 0;");

        cardFlotante.getChildren().addAll(header, contenidoCard, barraNavegacion);

        StackPane.setAlignment(cardFlotante, Pos.CENTER);
        contenedorOverlay.getChildren().add(cardFlotante);
    }

    private void limpiarFlechas() {
        for (Group grupo : gruposFlechas) {
            contenedorOverlay.getChildren().remove(grupo);
        }
        gruposFlechas.clear();
    }

    /**
     * Dibuja una flecha curvada desde el tipBox interno (o borde de card) a un nodo objetivo.
     * Estilo: trazo negro con borde dorado.
     *
     * @param nodoObjetivo nodo destino de la flecha
     * @param fracX        fracción horizontal (0.0=izquierda, 1.0=derecha) del punto destino sobre el nodo
     * @param fracY        fracción vertical (0.0=arriba, 1.0=abajo) del punto destino sobre el nodo
     */
    private void dibujarFlechaANodo(Node nodoObjetivo, double fracX, double fracY) {
        if (nodoObjetivo == null || !nodoObjetivo.isVisible() || contenedorOverlay == null) return;

        Bounds nodoBounds = nodoObjetivo.localToScene(nodoObjetivo.getBoundsInLocal());
        Bounds overlayBounds = contenedorOverlay.localToScene(contenedorOverlay.getBoundsInLocal());

        double objX = nodoBounds.getMinX() - overlayBounds.getMinX();
        double objY = nodoBounds.getMinY() - overlayBounds.getMinY();
        double objW = nodoBounds.getWidth();
        double objH = nodoBounds.getHeight();

        // Punto de destino según fracciones sobre el nodo
        double endX = objX + objW * fracX;
        double endY = objY + objH * fracY;

        // Punto de origen: desde el tipBox amarillo si existe, sino desde borde de card
        double startX, startY;
        Node fuente = (nodoOrigenFlecha != null && nodoOrigenFlecha.getScene() != null)
            ? nodoOrigenFlecha : cardFlotante;

        Bounds fuenteBounds = fuente.localToScene(fuente.getBoundsInLocal());
        double fX = fuenteBounds.getMinX() - overlayBounds.getMinX();
        double fY = fuenteBounds.getMinY() - overlayBounds.getMinY();
        double fW = fuenteBounds.getWidth();
        double fH = fuenteBounds.getHeight();

        // Salir por el borde más cercano al destino
        double fCX = fX + fW / 2;
        double fCY = fY + fH / 2;

        if (fY + fH < endY) {
            startX = fCX;
            startY = fY + fH;
        } else if (fY > endY) {
            startX = fCX;
            startY = fY;
        } else {
            startY = fCY;
            startX = (fX + fW < endX) ? fX + fW : fX;
        }

        dibujarFlechaCurvada(startX, startY, endX, endY);
    }

    private void dibujarFlechaCurvada(double sx, double sy, double ex, double ey) {
        double dx = ex - sx;
        double dy = ey - sy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 1) return;

        // Curva directa (arco simple)
        double curvaturaOffset = Math.min(dist * 0.18, 35);
        double nx = -dy / dist;
        double ny = dx / dist;

        double cx1 = sx + dx * 0.25 + nx * curvaturaOffset;
        double cy1 = sy + dy * 0.25 + ny * curvaturaOffset;
        double cx2 = sx + dx * 0.75 + nx * curvaturaOffset;
        double cy2 = sy + dy * 0.75 + ny * curvaturaOffset;

        // Contorno dorado (más ancho, debajo)
        CubicCurve contorno = new CubicCurve(sx, sy, cx1, cy1, cx2, cy2, ex, ey);
        contorno.setStroke(Color.web("#d4af37", 0.5));
        contorno.setStrokeWidth(5);
        contorno.setStrokeLineCap(StrokeLineCap.ROUND);
        contorno.setFill(Color.TRANSPARENT);

        // Trazo negro principal (mas delgado, encima)
        CubicCurve curva = new CubicCurve(sx, sy, cx1, cy1, cx2, cy2, ex, ey);
        curva.setStroke(Color.web("#1a1a1a"));
        curva.setStrokeWidth(2.5);
        curva.setStrokeLineCap(StrokeLineCap.ROUND);
        curva.setFill(Color.TRANSPARENT);

        // Punta de flecha orientada según la tangente final de la curva
        double angulo = Math.toDegrees(Math.atan2(ey - cy2, ex - cx2));
        Polygon punta = new Polygon(-12, -5, 0, 0, -12, 5);
        punta.setFill(Color.web("#1a1a1a"));
        punta.setStroke(Color.web("#d4af37", 0.5));
        punta.setStrokeWidth(1.5);
        punta.setLayoutX(ex);
        punta.setLayoutY(ey);
        punta.setRotate(angulo);

        Group grupo = new Group(contorno, curva, punta);
        grupo.setManaged(false);
        grupo.setEffect(new DropShadow(4, 1, 2, Color.web("#000", 0.3)));

        gruposFlechas.add(grupo);

        // Insertar debajo de la card pero encima del contenido base
        int cardIndex = contenedorOverlay.getChildren().indexOf(cardFlotante);
        if (cardIndex >= 0) {
            contenedorOverlay.getChildren().add(cardIndex, grupo);
        } else {
            contenedorOverlay.getChildren().add(grupo);
        }
    }

    private void posicionarCardCentro() {
        StackPane.setAlignment(cardFlotante, Pos.CENTER);
        StackPane.setMargin(cardFlotante, Insets.EMPTY);
    }

    private void posicionarCardArribaDeTodo() {
        StackPane.setAlignment(cardFlotante, Pos.TOP_CENTER);
        StackPane.setMargin(cardFlotante, new Insets(40, 0, 0, 0));
    }

    private void posicionarCercaDe(Node nodoObjetivo) {
        if (nodoObjetivo == null || contenedorOverlay == null) {
            posicionarCardCentro();
            return;
        }

        Bounds nodoBounds = nodoObjetivo.localToScene(nodoObjetivo.getBoundsInLocal());
        Bounds overlayBounds = contenedorOverlay.localToScene(contenedorOverlay.getBoundsInLocal());

        double objX = nodoBounds.getMinX() - overlayBounds.getMinX();
        double objY = nodoBounds.getMinY() - overlayBounds.getMinY();
        double objW = nodoBounds.getWidth();
        double overlayW = contenedorOverlay.getWidth();
        double overlayH = contenedorOverlay.getHeight();

        if (overlayW <= 0 || overlayH <= 0) {
            posicionarCardCentro();
            return;
        }

        double cardX = objX + objW + 20;
        if (cardX + CARD_ANCHO > overlayW) {
            cardX = objX - CARD_ANCHO - 20;
            if (cardX < 10) {
                cardX = Math.max(10, (overlayW - CARD_ANCHO) / 2);
            }
        }

        double cardY = Math.max(10, objY - 20);
        cardY = Math.min(cardY, overlayH - 400 - 10);
        cardY = Math.max(10, cardY);

        StackPane.setAlignment(cardFlotante, Pos.TOP_LEFT);
        StackPane.setMargin(cardFlotante, new Insets(cardY, 0, 0, cardX));
    }

    private void posicionarDebajoDeNodo(Node nodoObjetivo) {
        if (nodoObjetivo == null || contenedorOverlay == null) {
            posicionarCardCentro();
            return;
        }

        Bounds nodoBounds = nodoObjetivo.localToScene(nodoObjetivo.getBoundsInLocal());
        Bounds overlayBounds = contenedorOverlay.localToScene(contenedorOverlay.getBoundsInLocal());

        double objX = nodoBounds.getMinX() - overlayBounds.getMinX();
        double objY = nodoBounds.getMinY() - overlayBounds.getMinY();
        double objW = nodoBounds.getWidth();
        double objH = nodoBounds.getHeight();
        double overlayW = contenedorOverlay.getWidth();

        double cardX = objX + (objW - CARD_ANCHO) / 2;
        cardX = Math.max(10, Math.min(cardX, overlayW - CARD_ANCHO - 10));
        double cardY = objY + objH + 15;

        StackPane.setAlignment(cardFlotante, Pos.TOP_LEFT);
        StackPane.setMargin(cardFlotante, new Insets(cardY, 0, 0, cardX));
    }

    private void posicionarSobreNodo(Node nodoObjetivo) {
        if (nodoObjetivo == null || contenedorOverlay == null) {
            posicionarCardCentro();
            return;
        }

        Bounds nodoBounds = nodoObjetivo.localToScene(nodoObjetivo.getBoundsInLocal());
        Bounds overlayBounds = contenedorOverlay.localToScene(contenedorOverlay.getBoundsInLocal());

        double objX = nodoBounds.getMinX() - overlayBounds.getMinX();
        double objY = nodoBounds.getMinY() - overlayBounds.getMinY();
        double objW = nodoBounds.getWidth();
        double overlayW = contenedorOverlay.getWidth();

        double cardX = objX + (objW - CARD_ANCHO) / 2;
        cardX = Math.max(10, Math.min(cardX, overlayW - CARD_ANCHO - 10));
        double cardY = objY + 30;

        StackPane.setAlignment(cardFlotante, Pos.TOP_LEFT);
        StackPane.setMargin(cardFlotante, new Insets(cardY, 0, 0, cardX));
    }

    private void mostrarPaso(int paso) {
        pasoActual = paso;
        actualizarIndicadores();
        limpiarFlechas();

        switch (paso) {
            case 0 -> mostrarBienvenida();
            case 1 -> mostrarPasoConectar();
            case 2 -> mostrarPasoArrancando();
            case 3 -> mostrarPasoConectado();
            case 4 -> mostrarPasoNube();
            case 5 -> mostrarPasoConfigNube();
            case 6 -> mostrarPasoFinal();
        }
    }

    private void avanzarA(int paso) {
        MotorAnimaciones.fade(contenidoCard, 1, 0, 60, () -> {
            mostrarPaso(paso);
            MotorAnimaciones.fade(contenidoCard, 0, 1, 120);
        });
    }

    private void mostrarBienvenida() {
        nodoOrigenFlecha = null;

        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\uD83C\uDF10", IdiomaUtil.obtener("tutorial.web.bienvenida.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.bienvenida.desc")),
            crearTipBox(IdiomaUtil.obtener("tutorial.web.bienvenida.tip"))
        );

        configurarNavegacion(false, true, false, false);
        botonSiguiente.setOnAction(e -> avanzarA(1));
        posicionarCardArribaDeTodo();
    }

    private void mostrarPasoConectar() {
        if (servidorConectado) {
            mostrarPaso(3);
            return;
        }

        VBox tipConectar = crearTipBox(IdiomaUtil.obtener("tutorial.web.conectar.tip"));
        nodoOrigenFlecha = tipConectar;

        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u26A1", IdiomaUtil.obtener("tutorial.web.conectar.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.conectar.desc")),
            tipConectar,
            crearIndicadorEspera(IdiomaUtil.obtener("tutorial.web.conectar.esperando"))
        );

        configurarNavegacion(true, false, true, false);
        botonVolver.setOnAction(e -> avanzarA(0));

        Node boton = (nodoBotonConectar != null && nodoBotonConectar.isVisible())
            ? nodoBotonConectar : nodoCardLan;
        posicionarCercaDe(boton);

        // Flecha desde el tipBox hacia el centro del botón Conectar (apunta al texto)
        javafx.application.Platform.runLater(() -> dibujarFlechaANodo(boton, 0.9, 0.5));
    }

    private void mostrarPasoArrancando() {
        nodoOrigenFlecha = null;

        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u23F3", IdiomaUtil.obtener("tutorial.web.arrancando.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.arrancando.desc")),
            crearTipBox(IdiomaUtil.obtener("tutorial.web.arrancando.tip")),
            crearIndicadorEspera(IdiomaUtil.obtener("tutorial.web.arrancando.esperando"))
        );

        configurarNavegacion(false, false, true, false);
        posicionarDebajoDeNodo(nodoCardLan);
    }

    private void mostrarPasoConectado() {
        VBox ipBox = crearIpInfoBox();
        VBox tipConectado = crearTipBox(IdiomaUtil.obtener("tutorial.web.conectado.tip"));
        nodoOrigenFlecha = tipConectado;

        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u2705", IdiomaUtil.obtener("tutorial.web.conectado.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.conectado.desc")),
            ipBox,
            tipConectado
        );

        configurarNavegacion(false, true, false, true);
        botonSiguiente.setText(IdiomaUtil.obtener("tutorial.siguiente") + " \u2192");
        botonSiguiente.setStyle(estiloBotonNavExito());
        botonSiguiente.setOnAction(e -> avanzarA(4));

        posicionarCercaDe(nodoCardLan);

        javafx.application.Platform.runLater(() -> {
            if (nodoIpClientes != null && nodoIpClientes.isVisible()) {
                dibujarFlechaANodo(nodoIpClientes, 1.0, 0.5);
            }
        });
    }
    private void mostrarPasoNube() {
        VBox tipNube = crearTipBox(IdiomaUtil.obtener("tutorial.web.nube.tip"));
        nodoOrigenFlecha = tipNube;

        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u2601", IdiomaUtil.obtener("tutorial.web.nube.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.nube.desc")),
            tipNube,
            crearIndicadorEspera(IdiomaUtil.obtener("tutorial.web.nube.esperando"))
        );

        configurarNavegacion(true, false, true, false);
        botonVolver.setOnAction(e -> avanzarA(3));

        posicionarCercaDe(nodoCardNube);

        javafx.application.Platform.runLater(() -> {
            if (nodoBotonConfigurar != null && nodoBotonConfigurar.isVisible()) {
                dibujarFlechaANodo(nodoBotonConfigurar, 1.0, 0.5);
            }
        });
    }


    private void mostrarPasoConfigNube() {
        VBox tipConfig = crearTipBox(IdiomaUtil.obtener("tutorial.web.config_nube.tip"));
        nodoOrigenFlecha = tipConfig;

        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u2699", IdiomaUtil.obtener("tutorial.web.config_nube.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.config_nube.desc")),
            crearCampoExplicacion("\uD83D\uDD17", IdiomaUtil.obtener("tutorial.web.config_nube.mongo_uri")),
            crearCampoExplicacion("\uD83C\uDFE2", IdiomaUtil.obtener("tutorial.web.config_nube.business_id")),
            tipConfig,
            crearIndicadorEspera(IdiomaUtil.obtener("tutorial.web.config_nube.esperando"))
        );

        configurarNavegacion(true, true, false, false);
        botonVolver.setOnAction(e -> avanzarA(4));
        botonSiguiente.setText(IdiomaUtil.obtener("tutorial.web.config_nube.saltar"));
        botonSiguiente.setStyle(estiloBotonNav());
        botonSiguiente.setOnAction(e -> avanzarA(6));

        posicionarCercaDe(nodoCardNube);
    }

    // ── Paso 6: Final — diferente según si se configuró la nube ──

    private void mostrarPasoFinal() {
        nodoOrigenFlecha = null;

        if (nubeConfigurada) {
            contenidoCard.getChildren().setAll(
                crearCabeceraConIcono("\uD83C\uDF89", IdiomaUtil.obtener("tutorial.web.final.titulo")),
                crearDescripcion(IdiomaUtil.obtener("tutorial.web.final.con_nube.desc")),
                crearTipBox(IdiomaUtil.obtener("tutorial.web.final.tip"))
            );
        } else {
            contenidoCard.getChildren().setAll(
                crearCabeceraConIcono("\u2705", IdiomaUtil.obtener("tutorial.web.final.titulo_lan")),
                crearDescripcion(IdiomaUtil.obtener("tutorial.web.final.sin_nube.desc")),
                crearAdvertenciaBox(IdiomaUtil.obtener("tutorial.web.final.sin_nube.limitaciones")),
                crearDescripcion(IdiomaUtil.obtener("tutorial.web.final.sin_nube.reiniciar")),
                crearTipBox(IdiomaUtil.obtener("tutorial.web.final.tip"))
            );
        }

        configurarNavegacion(true, true, false, false);
        botonVolver.setOnAction(e -> avanzarA(5));
        botonSiguiente.setText("\u2713 " + IdiomaUtil.obtener("tutorial.entendido"));
        botonSiguiente.setStyle(estiloBotonNavExito());
        botonSiguiente.setOnAction(e -> detener());
        posicionarCardCentro();
    }

    // ══════════════════════════════════════
    // CARDS DE ERROR
    // ══════════════════════════════════════

    private void mostrarErrorServidor() {
        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u274C", IdiomaUtil.obtener("tutorial.web.error_srv.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.error_srv.desc")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_srv.sug1")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_srv.sug2")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_srv.sug3")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_srv.sug4"))
        );

        configurarNavegacion(false, true, false, false);
        botonSiguiente.setText(IdiomaUtil.obtener("tutorial.web.error_srv.reintentar"));
        botonSiguiente.setStyle(estiloBotonNavPrimario());
        botonSiguiente.setOnAction(e -> avanzarA(1));
        posicionarCercaDe(nodoCardLan);
        actualizarIndicadores();
    }

    private void mostrarErrorNube() {
        contenidoCard.getChildren().setAll(
            crearCabeceraConIcono("\u26A0", IdiomaUtil.obtener("tutorial.web.error_nube.titulo")),
            crearDescripcion(IdiomaUtil.obtener("tutorial.web.error_nube.desc")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_nube.sug1")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_nube.sug2")),
            crearSugerenciaError(IdiomaUtil.obtener("tutorial.web.error_nube.sug3"))
        );

        configurarNavegacion(false, true, false, false);
        botonSiguiente.setText(IdiomaUtil.obtener("tutorial.web.config_nube.saltar"));
        botonSiguiente.setStyle(estiloBotonNav());
        botonSiguiente.setOnAction(e -> avanzarA(6));
        posicionarCercaDe(nodoCardNube);
    }

    // ══════════════════════════════════════
    // NAVEGACIÓN
    // ══════════════════════════════════════

    private void configurarNavegacion(boolean mostrarVolver, boolean mostrarSiguiente,
                                       boolean esperandoEvento, boolean esExito) {
        botonVolver.setVisible(mostrarVolver);
        botonVolver.setManaged(mostrarVolver);
        botonVolver.setText("\u2190 " + IdiomaUtil.obtener("tutorial.volver"));
        botonVolver.setStyle(estiloBotonNav());

        botonSiguiente.setVisible(mostrarSiguiente || esperandoEvento);
        botonSiguiente.setManaged(mostrarSiguiente || esperandoEvento);

        if (esperandoEvento) {
            botonSiguiente.setText(IdiomaUtil.obtener("tutorial.web.conectar.btn"));
            botonSiguiente.setStyle(estiloBotonNavDeshabilitado());
            botonSiguiente.setDisable(true);
        } else {
            botonSiguiente.setDisable(false);
            botonSiguiente.setText(IdiomaUtil.obtener("tutorial.siguiente") + " \u2192");
            botonSiguiente.setStyle(esExito ? estiloBotonNavExito() : estiloBotonNavPrimario());
        }

        barraNavegacion.setVisible(mostrarVolver || mostrarSiguiente || esperandoEvento);
        barraNavegacion.setManaged(mostrarVolver || mostrarSiguiente || esperandoEvento);
    }

    // ══════════════════════════════════════
        // COMPONENTES VISUALES
    // ══════════════════════════════════════

    private HBox crearCabeceraConIcono(String emoji, String titulo) {
        Label icono = new Label(emoji);
        icono.getStyleClass().add("icono-texto-md");
        icono.setMinWidth(30);

        Label label = new Label(titulo);
        label.getStyleClass().add("tutorial-seccion-titulo");
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);

        HBox box = new HBox(10, icono, label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 4, 0));
        return box;
    }

    private Label crearDescripcion(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("tutorial-texto");
        label.setStyle("-fx-line-spacing: 4;");
        label.setWrapText(true);
        label.setMaxWidth(CARD_ANCHO - 50);
        return label;
    }

    private VBox crearTipBox(String texto) {
        Label icono = new Label("\uD83D\uDCA1");
        icono.getStyleClass().add("tutorial-nota");
        icono.setMinWidth(20);

        Label label = new Label(texto);
        label.getStyleClass().add("tutorial-nota");
        label.setStyle("-fx-line-spacing: 3;");
        label.setWrapText(true);
        label.setMaxWidth(CARD_ANCHO - 80);

        HBox fila = new HBox(8, icono, label);
        fila.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(label, Priority.ALWAYS);

        VBox box = new VBox(fila);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle(
            "-fx-background-color: rgba(212,175,55,0.06); -fx-background-radius: 8; " +
            "-fx-border-color: rgba(212,175,55,0.15); -fx-border-radius: 8; -fx-border-width: 1;");
        return box;
    }

    private VBox crearIpInfoBox() {
        String ipTexto = ipLocal.isEmpty() ? "127.0.0.1" : ipLocal;
        Label labelIp = new Label("\uD83D\uDDA5  " + IdiomaUtil.obtener("tutorial.web.conectado.ip_info") + " " + ipTexto);
        labelIp.getStyleClass().add("tutorial-dato");
        labelIp.setWrapText(true);

        VBox box = new VBox(labelIp);
        box.setPadding(new Insets(12, 16, 12, 16));
        box.setStyle(
            "-fx-background-color: rgba(212,175,55,0.1); -fx-background-radius: 10; " +
            "-fx-border-color: rgba(212,175,55,0.25); -fx-border-radius: 10; -fx-border-width: 1;");
        return box;
    }

    private HBox crearCampoExplicacion(String emoji, String texto) {
        Label icono = new Label(emoji);
        icono.getStyleClass().add("tutorial-paso");
        icono.setMinWidth(22);

        Label label = new Label(texto);
        label.getStyleClass().add("tutorial-paso");
        label.setStyle("-fx-line-spacing: 3;");
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);

        HBox box = new HBox(8, icono, label);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(6, 12, 6, 12));
        box.setStyle(
            "-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 6; " +
            "-fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 6; -fx-border-width: 1;");
        return box;
    }

    private VBox crearAdvertenciaBox(String texto) {
        Label icono = new Label("\u26A0");
        icono.getStyleClass().add("tutorial-aviso");
        icono.setMinWidth(20);

        Label label = new Label(texto);
        label.getStyleClass().add("tutorial-aviso");
        label.setStyle("-fx-line-spacing: 3;");
        label.setWrapText(true);
        label.setMaxWidth(CARD_ANCHO - 80);

        HBox fila = new HBox(8, icono, label);
        fila.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(label, Priority.ALWAYS);

        VBox box = new VBox(fila);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle(
            "-fx-background-color: rgba(218,165,32,0.08); -fx-background-radius: 8; " +
            "-fx-border-color: rgba(218,165,32,0.2); -fx-border-radius: 8; -fx-border-width: 1;");
        return box;
    }

    private HBox crearIndicadorEspera(String texto) {
        Circle dot = new Circle(4, Color.web("#d4af37"));
        Label label = new Label(texto);
        label.getStyleClass().add("tutorial-disclaimer");
        label.setWrapText(true);
        HBox box = new HBox(8, dot, label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 0, 0));
        return box;
    }

    private HBox crearSugerenciaError(String texto) {
        Label check = new Label("\u2192");
        check.getStyleClass().add("tutorial-paso");
        check.setStyle("-fx-text-fill: #e74c3c;");
        check.setMinWidth(16);

        Label label = new Label(texto);
        label.getStyleClass().add("tutorial-paso");
        label.setStyle("-fx-line-spacing: 2;");
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);

        HBox box = new HBox(8, check, label);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(2, 0, 2, 4));
        return box;
    }

    // ══════════════════════════════════════
    // INDICADORES Y ESTILOS
    // ══════════════════════════════════════

    private void actualizarIndicadores() {
        indicadorProgreso.getChildren().clear();
        for (int i = 0; i < TOTAL_PASOS; i++) {
            final int paso = i;
            Circle dot = new Circle(i == pasoActual ? 5 : 3.5);
            if (i == pasoActual) {
                dot.setFill(Color.web("#d4af37"));
            } else if (i < pasoActual) {
                dot.setFill(Color.web("#8b7730"));
            } else {
                dot.setFill(Color.web("#3a3a3a"));
            }
            dot.setCursor(Cursor.HAND);
            Tooltip.install(dot, new Tooltip(String.valueOf(paso + 1)));
            dot.setOnMouseEntered(e -> dot.setScaleX(1.4));
            dot.setOnMouseExited(e -> dot.setScaleX(1.0));
            dot.setOnMouseClicked(e -> avanzarA(paso));
            indicadorProgreso.getChildren().add(dot);
        }
    }

    private String estiloBotonNav() {
        return "-fx-background-color: transparent; -fx-text-fill: #888; " +
            "-fx-cursor: hand; -fx-padding: 6 14; " +
            "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-background-radius: 6;";
    }

    private String estiloBotonNavPrimario() {
        return "-fx-background-color: rgba(212,175,55,0.15); -fx-text-fill: #d4af37; " +
            "-fx-font-weight: 600; -fx-cursor: hand; -fx-padding: 6 16; " +
            "-fx-border-color: rgba(212,175,55,0.4); -fx-border-radius: 6; -fx-background-radius: 6;";
    }

    private String estiloBotonNavExito() {
        return "-fx-background-color: rgba(168,185,145,0.15); -fx-text-fill: #a8b991; " +
            "-fx-font-weight: 600; -fx-cursor: hand; -fx-padding: 6 16; " +
            "-fx-border-color: rgba(168,185,145,0.4); -fx-border-radius: 6; -fx-background-radius: 6;";
    }

    private String estiloBotonNavDeshabilitado() {
        return "-fx-background-color: #1a1a1a; -fx-text-fill: #555; " +
            "-fx-padding: 6 14; -fx-background-radius: 6;";
    }
}
