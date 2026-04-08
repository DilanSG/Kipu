/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler del modal de Respaldo de Datos.
 *
 * Permite sincronizar TODOS los datos de PostgreSQL a MongoDB Atlas
 * con un solo clic. Muestra estado de conexión, progreso y resultados
 * detallados por tabla.
 *
 * @see GestorModales
 */
public class RespaldoDatosHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(RespaldoDatosHandler.class);

    private final GestorModales gestor;

    public RespaldoDatosHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Abriendo Respaldo de Datos");

        VBox modal = new VBox(10);
        modal.setMaxWidth(480);
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header ───
        HBox header = gestor.crearHeaderModal(
                IdiomaUtil.obtener("ctrl.respaldo.header"), "icono-cfg-backup");

        // ─── Card: Estado de conexión nube (con botón sync integrado) ───
        Circle indNube = new Circle(4, Color.web("#666"));
        Label estadoNube = new Label(IdiomaUtil.obtener("ctrl.respaldo.verificando"));
        estadoNube.getStyleClass().add("texto-secundario-sm");
        estadoNube.setStyle("-fx-text-fill: #888;");

        Button btnRespaldar = new Button(IdiomaUtil.obtener("ctrl.respaldo.boton"));
        btnRespaldar.getStyleClass().addAll("btn-metodo-pago", "texto-hint");
        btnRespaldar.setPrefHeight(30);
        btnRespaldar.setStyle("-fx-padding: 4 16;");
        btnRespaldar.setVisible(false);
        btnRespaldar.setManaged(false);

        // ─── Card: Resultado del respaldo (oculta inicialmente) ───
        VBox cardResultado = new VBox(6);
        cardResultado.setPadding(new Insets(12));
        cardResultado.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");
        cardResultado.setVisible(false);
        cardResultado.setManaged(false);

        VBox cardEstado = construirCardEstado(indNube, estadoNube, btnRespaldar);

        // ─── Card: Intervalo de sincronización automática ───
        VBox cardIntervalo = construirCardIntervalo();

        VBox contenidoModal = new VBox(10,
                gestor.crearSeparador(), cardEstado, cardIntervalo, cardResultado);
        contenidoModal.setPadding(new Insets(0));

        modal.getChildren().addAll(header, contenidoModal);
        gestor.mostrarModal(modal);

        // Verificar estado nube al abrir
        verificarEstadoNube(indNube, estadoNube, btnRespaldar);

        // Acción del botón
        btnRespaldar.setOnAction(ev -> ejecutarRespaldo(btnRespaldar, cardResultado, indNube, estadoNube));
    }

    /**
     * Construye la card de estado de conexión a la nube con botón de sync integrado.
     */
    private VBox construirCardEstado(Circle indNube, Label estadoNube, Button btnRespaldar) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.respaldo.titulo_estado"));
        titulo.getStyleClass().add("texto-secundario-sm");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        HBox filaEstado = new HBox(6, indNube, estadoNube, new Region(), btnRespaldar);
        filaEstado.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filaEstado.getChildren().get(2), Priority.ALWAYS);

        Label desc = new Label(IdiomaUtil.obtener("ctrl.respaldo.descripcion"));
        desc.getStyleClass().add("texto-hint-sm");
        desc.setStyle("-fx-text-fill: #b0b0b0; -fx-wrap-text: true;");
        desc.setWrapText(true);
        desc.setMaxWidth(460);

        card.getChildren().addAll(titulo, filaEstado, desc);
        return card;
    }

    /**
     * Verifica el estado de la conexión nube consultando /api/sync/estado.
     * Muestra registros pendientes y habilita/deshabilita el botón según corresponda.
     */
    private void verificarEstadoNube(Circle indNube, Label estadoNube, Button btnRespaldar) {
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
                        @SuppressWarnings("unchecked")
                        Map<String, Object> d = (Map<String, Object>) datos;
                        return d;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                logger.debug("No se pudo consultar estado nube: {}", e.getMessage());
            }
            return null;
        }).thenAccept(estado -> Platform.runLater(() -> {
            if (estado == null) {
                indNube.setFill(Color.web("#cc4444"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.nube_no_disponible"));
                estadoNube.setStyle("-fx-text-fill: #cc4444;");
                btnRespaldar.setVisible(false);
                btnRespaldar.setManaged(false);
                return;
            }

            boolean habilitado = Boolean.TRUE.equals(estado.get("habilitado"));
            boolean conectado = Boolean.TRUE.equals(estado.get("conexionNube"));
            int pendientes = 0;
            Object pend = estado.get("registrosPendientes");
            if (pend instanceof Number) pendientes = ((Number) pend).intValue();

            if (habilitado && conectado) {
                btnRespaldar.setVisible(true);
                btnRespaldar.setManaged(true);
                btnRespaldar.setDisable(false);
                if (pendientes > 0) {
                    indNube.setFill(Color.web("#daa520"));
                    estadoNube.setText(MessageFormat.format(
                            IdiomaUtil.obtener("ctrl.respaldo.pendientes"), pendientes));
                    estadoNube.setStyle("-fx-text-fill: #daa520;");
                } else {
                    indNube.setFill(Color.web("#a8b991"));
                    estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.todo_subido"));
                    estadoNube.setStyle("-fx-text-fill: #a8b991;");
                }
            } else if (habilitado) {
                indNube.setFill(Color.web("#daa520"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.nube_sin_conexion"));
                estadoNube.setStyle("-fx-text-fill: #daa520;");
                btnRespaldar.setVisible(false);
                btnRespaldar.setManaged(false);
            } else {
                indNube.setFill(Color.web("#555"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.nube_deshabilitada"));
                estadoNube.setStyle("-fx-text-fill: #555;");
                btnRespaldar.setVisible(false);
                btnRespaldar.setManaged(false);
            }
        }));
    }

    /**
     * Ejecuta el respaldo completo via POST /api/sync/respaldo-completo.
     */
    private void ejecutarRespaldo(Button btnRespaldar, VBox cardResultado,
                                   Circle indNube, Label estadoNube) {
        String textoOriginal = btnRespaldar.getText();
        btnRespaldar.setText(IdiomaUtil.obtener("ctrl.respaldo.sincronizando"));
        btnRespaldar.setDisable(true);

        Platform.runLater(() -> {
            indNube.setFill(Color.web("#daa520"));
            estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.en_progreso"));
            estadoNube.setStyle("-fx-text-fill: #daa520;");
        });

        CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(
                        ConfiguracionCliente.getUrlServidor() + "/api/sync/respaldo-completo").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(120000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                String token = ConfiguracionCliente.getTokenJwt();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                conn.setDoOutput(true);
                conn.getOutputStream().close();

                int code = conn.getResponseCode();
                BufferedReader reader;
                if (code >= 200 && code < 400) {
                    reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                } else {
                    reader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                }
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
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultado = (Map<String, Object>) datos;
                    return resultado;
                }
                return null;
            } catch (Exception ex) {
                logger.error("Error al ejecutar respaldo completo: {}", ex.getMessage(), ex);
                return null;
            }
        }).thenAccept(resultado -> Platform.runLater(() -> {
            btnRespaldar.setText(textoOriginal);

            if (resultado == null) {
                indNube.setFill(Color.web("#cc4444"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.error_servidor"));
                estadoNube.setStyle("-fx-text-fill: #cc4444;");
                btnRespaldar.setDisable(false);
                return;
            }

            boolean exito = Boolean.TRUE.equals(resultado.get("exito"));

            if (exito) {
                indNube.setFill(Color.web("#a8b991"));
                estadoNube.setText(IdiomaUtil.obtener("ctrl.respaldo.todo_subido"));
                estadoNube.setStyle("-fx-text-fill: #a8b991;");
                btnRespaldar.setVisible(false);
                btnRespaldar.setManaged(false);
                mostrarResultado(cardResultado, resultado);
            } else {
                String error = resultado.get("error") != null ? resultado.get("error").toString() : "";
                indNube.setFill(Color.web("#cc4444"));
                estadoNube.setText(MessageFormat.format(
                        IdiomaUtil.obtener("ctrl.respaldo.error_respaldo"), error));
                estadoNube.setStyle("-fx-text-fill: #cc4444;");
                btnRespaldar.setDisable(false);
            }
        }));
    }

    /**
     * Muestra la card de resultados del respaldo con detalle por tabla.
     */
    @SuppressWarnings("unchecked")
    private void mostrarResultado(VBox cardResultado, Map<String, Object> resultado) {
        cardResultado.getChildren().clear();

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.respaldo.titulo_resultado"));
        titulo.getStyleClass().add("texto-secundario-sm");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);

        int fila = 0;
        grid.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.respaldo.label.tablas")),
                gestor.crearInfoValor(String.valueOf(resultado.getOrDefault("tablasExitosas", "—"))));
        grid.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.respaldo.label.registros")),
                gestor.crearInfoValor(String.valueOf(resultado.getOrDefault("totalRegistros", "—"))));
        grid.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.respaldo.label.tiempo")),
                gestor.crearInfoValor(resultado.getOrDefault("tiempoMs", "—") + " ms"));
        grid.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.respaldo.label.base_datos")),
                gestor.crearInfoValor(String.valueOf(resultado.getOrDefault("baseDatos", "—"))));
        grid.addRow(fila++, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.respaldo.label.business_id")),
                gestor.crearInfoValor(String.valueOf(resultado.getOrDefault("businessId", "—"))));

        // Detalle por tabla
        Object detalleObj = resultado.get("detalle");
        VBox detalleBox = new VBox(3);
        if (detalleObj instanceof Map) {
            Map<String, Object> detalle = (Map<String, Object>) detalleObj;

            Label tituloDetalle = new Label(IdiomaUtil.obtener("ctrl.respaldo.detalle_tablas"));
            tituloDetalle.getStyleClass().add("texto-hint-sm");
            tituloDetalle.setStyle("-fx-font-weight: 600; -fx-text-fill: #c9a961;");
            detalleBox.getChildren().add(tituloDetalle);

            for (Map.Entry<String, Object> entry : detalle.entrySet()) {
                String tabla = entry.getKey();
                String valor = String.valueOf(entry.getValue());
                boolean esError = valor.startsWith("ERROR");

                Label lblTabla = new Label("  " + tabla + ": " + valor);
                lblTabla.getStyleClass().add("texto-hint-sm");
                lblTabla.setStyle("-fx-text-fill: " + (esError ? "#cc4444" : "#a8b991")
                        + ";");
                detalleBox.getChildren().add(lblTabla);
            }
        }

        cardResultado.getChildren().addAll(titulo, grid, detalleBox);
        cardResultado.setVisible(true);
        cardResultado.setManaged(true);
    }

    /** Estilo CSS premium para RadioButtons con puntos dorados. */
    private static final String ESTILO_RADIO = "-fx-text-fill: #e8e8e8; " +
            "-fx-mark-color: #d4af37; -fx-color: #2a2a2a; " +
            "-fx-focus-color: rgba(212,175,55,0.5); -fx-faint-focus-color: transparent;";

    private static final String ESTILO_RADIO_SELECTED = ESTILO_RADIO;

    /**
     * Construye la card de configuración del intervalo de sincronización automática.
     * 3 opciones: Registros pendientes, Cada hora, Personalizado (slider 5-500min).
     * El botón guardar solo aparece al cambiar la selección actual.
     */
    private VBox construirCardIntervalo() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
                "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.respaldo.titulo_intervalo"));
        titulo.getStyleClass().add("texto-info");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        // Leer intervalo actual del .env
        final int intervaloActual = leerIntervaloActual();
        final int[] intervaloGuardado = { intervaloActual };

        ToggleGroup grupo = new ToggleGroup();

        RadioButton rbSiempre = crearRadioDorado(
                IdiomaUtil.obtener("ctrl.respaldo.intervalo.siempre"), grupo);
        rbSiempre.setUserData(1);

        RadioButton rbHora = crearRadioDorado(
                IdiomaUtil.obtener("ctrl.respaldo.intervalo.cada_hora"), grupo);
        rbHora.setUserData(60);

        RadioButton rbPersonalizado = crearRadioDorado(
                IdiomaUtil.obtener("ctrl.respaldo.intervalo.personalizado"), grupo);
        rbPersonalizado.setUserData(-1);

        Slider slider = new Slider(5, 500, Math.min(Math.max(intervaloActual, 5), 500));
        slider.setBlockIncrement(5);
        slider.setMajorTickUnit(50);
        slider.setMinorTickCount(4);
        slider.setPrefWidth(360);
        slider.setStyle("-fx-control-inner-background: #2a2a2a; -fx-accent: #d4af37;");

        Label lblSliderValor = new Label(formatearMinutos((int) slider.getValue()));
        lblSliderValor.getStyleClass().add("texto-info");
        lblSliderValor.setStyle("-fx-text-fill: #d4af37; -fx-font-weight: 700;");
        lblSliderValor.setMinWidth(100);

        HBox filaSlider = new HBox(10, slider, lblSliderValor);
        filaSlider.setAlignment(Pos.CENTER_LEFT);
        filaSlider.setPadding(new Insets(2, 0, 0, 22));
        filaSlider.setVisible(false);
        filaSlider.setManaged(false);

        // Botón guardar — oculto por defecto, solo aparece al cambiar
        Button btnGuardar = new Button(IdiomaUtil.obtener("ctrl.respaldo.intervalo.guardar"));
        btnGuardar.getStyleClass().add("btn-metodo-pago");
        btnGuardar.setPrefWidth(200);
        btnGuardar.setPrefHeight(36);
        btnGuardar.setVisible(false);
        btnGuardar.setManaged(false);

        HBox filaBtnGuardar = new HBox(btnGuardar);
        filaBtnGuardar.setAlignment(Pos.CENTER);
        filaBtnGuardar.setPadding(new Insets(4, 0, 0, 0));
        filaBtnGuardar.setVisible(false);
        filaBtnGuardar.setManaged(false);

        Label lblNota = new Label(IdiomaUtil.obtener("ctrl.respaldo.intervalo.nota"));
        lblNota.getStyleClass().add("texto-hint");
        lblNota.setStyle("-fx-text-fill: #555; -fx-wrap-text: true;");
        lblNota.setWrapText(true);
        lblNota.setMaxWidth(460);

        // Seleccionar opción según valor actual
        if (intervaloActual <= 1) {
            rbSiempre.setSelected(true);
        } else if (intervaloActual == 60) {
            rbHora.setSelected(true);
        } else {
            rbPersonalizado.setSelected(true);
            filaSlider.setVisible(true);
            filaSlider.setManaged(true);
            slider.setValue(Math.min(intervaloActual, 500));
            lblSliderValor.setText(formatearMinutos((int) slider.getValue()));
        }

        // Helper para obtener el valor seleccionado actualmente
        Runnable actualizarVisibilidadGuardar = () -> {
            int valorActual;
            Toggle sel = grupo.getSelectedToggle();
            if (sel == rbSiempre) valorActual = 1;
            else if (sel == rbHora) valorActual = 60;
            else valorActual = (int) slider.getValue();

            boolean cambio = valorActual != intervaloGuardado[0];
            filaBtnGuardar.setVisible(cambio);
            filaBtnGuardar.setManaged(cambio);
            btnGuardar.setVisible(cambio);
            btnGuardar.setManaged(cambio);
        };

        slider.valueProperty().addListener((obs, viejo, nuevo) -> {
            int min = nuevo.intValue();
            min = (min / 5) * 5;
            if (min < 5) min = 5;
            slider.setValue(min);
            lblSliderValor.setText(formatearMinutos(min));
            actualizarVisibilidadGuardar.run();
        });

        grupo.selectedToggleProperty().addListener((obs, viejo, nuevo) -> {
            boolean esPers = nuevo == rbPersonalizado;
            filaSlider.setVisible(esPers);
            filaSlider.setManaged(esPers);
            actualizarVisibilidadGuardar.run();
        });

        btnGuardar.setOnAction(ev -> {
            int minutos;
            Toggle seleccion = grupo.getSelectedToggle();
            if (seleccion == rbSiempre) minutos = 1;
            else if (seleccion == rbHora) minutos = 60;
            else minutos = (int) slider.getValue();

            guardarIntervaloEnv(minutos);
            intervaloGuardado[0] = minutos;

            // Ocultar botón inmediatamente
            filaBtnGuardar.setVisible(false);
            filaBtnGuardar.setManaged(false);
            btnGuardar.setVisible(false);
            btnGuardar.setManaged(false);
        });

        card.getChildren().addAll(titulo, rbSiempre, rbHora, rbPersonalizado,
                filaSlider, filaBtnGuardar, lblNota);
        return card;
    }

    /** Crea un RadioButton con estilo dorado premium. */
    private RadioButton crearRadioDorado(String texto, ToggleGroup grupo) {
        RadioButton rb = new RadioButton(texto);
        rb.setToggleGroup(grupo);
        rb.getStyleClass().add("texto-secundario-sm");
        rb.setStyle(ESTILO_RADIO);
        rb.selectedProperty().addListener((obs, viejo, nuevo) ->
                rb.setStyle(nuevo ? ESTILO_RADIO_SELECTED : ESTILO_RADIO));
        return rb;
    }

    /**
     * Formatea minutos a un texto legible: "5 min", "1 hra", "3 hras 20 min".
     */
    private String formatearMinutos(int minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        int horas = minutos / 60;
        int resto = minutos % 60;
        if (resto == 0) {
            return horas + (horas == 1 ? " hra" : " hras");
        }
        return horas + (horas == 1 ? " hra " : " hras ") + resto + " min";
    }

    /** Lee el valor actual de SYNC_INTERVALO desde el .env */
    private int leerIntervaloActual() {
        try {
            Path envPath = Paths.get(System.getProperty("user.home"), ".kipu", ".env");
            if (Files.exists(envPath)) {
                for (String linea : Files.readAllLines(envPath)) {
                    linea = linea.trim();
                    if (linea.startsWith("SYNC_INTERVALO=")) {
                        String valor = linea.substring("SYNC_INTERVALO=".length()).trim()
                                .replace("'", "").replace("\"", "");
                        return Integer.parseInt(valor);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudo leer intervalo: {}", e.getMessage());
        }
        return 5; // default
    }

    /** Guarda el intervalo de sincronización en el .env */
    private void guardarIntervaloEnv(int minutos) {
        Path envDir = Paths.get(System.getProperty("user.home"), ".kipu");
        Path envPath = envDir.resolve(".env");

        try {
            Files.createDirectories(envDir);

            LinkedHashMap<String, String> variables = new LinkedHashMap<>();
            if (Files.exists(envPath)) {
                for (String linea : Files.readAllLines(envPath)) {
                    linea = linea.trim();
                    if (linea.startsWith("#") || linea.isEmpty()) continue;
                    int sep = linea.indexOf('=');
                    if (sep > 0) {
                        variables.put(linea.substring(0, sep).trim(), linea.substring(sep + 1).trim());
                    }
                }
            }

            variables.put("SYNC_INTERVALO", String.valueOf(minutos));

            StringBuilder sb = new StringBuilder();
            sb.append("# Configuración Kipu - Generado automáticamente\n");
            for (var entry : variables.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            Files.writeString(envPath, sb.toString());

            logger.info("Intervalo de sincronización guardado: {} minutos", minutos);
        } catch (IOException e) {
            logger.error("Error guardando intervalo: {}", e.getMessage());
        }
    }
}
