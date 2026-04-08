/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
package com.kipu.cliente.controlador.configuracion.herramientas;

import com.kipu.cliente.controlador.configuracion.GestorModales;
import com.kipu.cliente.controlador.configuracion.ModalHerramienta;
import com.kipu.cliente.servicio.LicenciaServicio;
import com.kipu.cliente.utilidad.IdiomaUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

/**
 * Handler del modal de Licencia del sistema.
 *
 * Muestra el estado de licencia real consultando LicenciaServicio
 * (tipo, plan, validez, equipo vinculado, días restantes)
 * y el texto completo de la licencia de uso del software Kipu.
 */
public class LicenciaHandler implements ModalHerramienta {

    private static final Logger logger = LoggerFactory.getLogger(LicenciaHandler.class);

    private final GestorModales gestor;

    public LicenciaHandler(GestorModales gestor) {
        this.gestor = gestor;
    }

    @Override
    public void abrir() {
        logger.info("Abriendo información de Licencia");

        // Obtener estado real de la licencia (desde caché, no bloquea)
        var servicio = new LicenciaServicio();
        var resultado = servicio.leerCacheLocal();

        VBox modal = new VBox(12);
        modal.setMaxWidth(650);
        modal.setMaxHeight(580);
        modal.setPadding(new Insets(24));
        modal.setStyle(GestorModales.ESTILO_MODAL_LUXURY);

        // ─── Header ───
        HBox header = gestor.crearHeaderModal(IdiomaUtil.obtener("ctrl.licencia.header"), "icono-cfg-licencia");

        // ─── Card: Estado de Licencia (dinámico) ───
        VBox cardLicencia = new VBox(6);
        cardLicencia.setPadding(new Insets(12));
        cardLicencia.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 8;");

        Label tEstado = new Label(IdiomaUtil.obtener("ctrl.licencia.estado"));
        tEstado.getStyleClass().add("estado-texto-bold");
        tEstado.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        GridPane gridLic = new GridPane();
        gridLic.setHgap(16);
        gridLic.setVgap(4);

        // Tipo/Plan dinámico
        String tipoPlan = obtenerTipoPlan(resultado);
        gridLic.addRow(0, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.licencia.tipo")),
            gestor.crearInfoValor(tipoPlan));

        // Estado dinámico con color
        String estadoTexto = obtenerEstadoTexto(resultado);
        Label valEstado = gestor.crearInfoValor(estadoTexto);
        valEstado.getStyleClass().add("texto-secundario-sm");
        valEstado.setStyle("-fx-font-weight: 600; -fx-text-fill: "
            + obtenerColorEstado(resultado) + ";");
        gridLic.addRow(1, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.licencia.estado_label")), valEstado);

        // Válida hasta
        String validaHasta = obtenerValidaHasta(resultado);
        gridLic.addRow(2, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.licencia.valida")),
            gestor.crearInfoValor(validaHasta));

        // Días restantes
        if (resultado.diasRestantes() >= 0) {
            String diasTexto = MessageFormat.format(
                    IdiomaUtil.obtener("ctrl.licencia.dias_restantes_valor"),
                    resultado.diasRestantes());
            Label valDias = gestor.crearInfoValor(diasTexto);
            if (resultado.diasRestantes() <= LicenciaServicio.DIAS_AVISO_RENOVACION) {
                valDias.getStyleClass().add("texto-secundario-sm");
                valDias.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: 600;");
            }
            gridLic.addRow(3, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.licencia.dias_restantes")), valDias);
        }

        // Clave
        String claveTexto = resultado.licenseKey() != null
            ? enmascararKey(resultado.licenseKey())
            : IdiomaUtil.obtener("ctrl.licencia.clave.sin_key");
        gridLic.addRow(4, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.licencia.clave")),
            gestor.crearInfoValor(claveTexto));

        // Equipo vinculado (device hash)
        String deviceHash = servicio.obtenerDeviceHash();
        String deviceCorto = deviceHash.length() > 12
            ? deviceHash.substring(0, 12) + "..."
            : deviceHash;
        gridLic.addRow(5, gestor.crearInfoLabel(IdiomaUtil.obtener("ctrl.licencia.equipo")),
            gestor.crearInfoValor(deviceCorto));

        // Nota contextual
        String nota = obtenerNotaContextual(resultado);
        Label notaLic = new Label(nota);
        notaLic.getStyleClass().add("texto-hint");
        notaLic.setStyle("-fx-text-fill: #666; -fx-padding: 4 0 0 0;");
        notaLic.setWrapText(true);

        cardLicencia.getChildren().addAll(tEstado, gridLic, notaLic);

        // ─── Sección: Ingresar / Cambiar clave ───
        VBox seccionActivar = crearSeccionActivarKey(servicio, modal);

        // ─── Texto de la licencia ───
        Label tTexto = new Label(IdiomaUtil.obtener("ctrl.licencia.texto"));
        tTexto.getStyleClass().add("estado-texto-bold");
        tTexto.setStyle("-fx-text-fill: #999;");

        TextArea areaLicencia = new TextArea(cargarTextoLicencia());
        areaLicencia.setEditable(false);
        areaLicencia.setWrapText(true);
        areaLicencia.getStyleClass().add("texto-hint-sm");
        areaLicencia.setStyle("-fx-control-inner-background: #0e0e0e; -fx-text-fill: #b0b0b0; " +
            "-fx-font-family: 'Roboto Mono', 'Consolas', monospace;");
        areaLicencia.setPrefHeight(200);
        VBox.setVgrow(areaLicencia, Priority.ALWAYS);

        modal.getChildren().addAll(
            header, gestor.crearSeparador(), cardLicencia, seccionActivar, tTexto, areaLicencia);
        gestor.mostrarModal(modal);
    }

    // ==================== SECCIÓN ACTIVAR KEY ====================

    private VBox crearSeccionActivarKey(LicenciaServicio servicio, VBox modal) {
        VBox seccion = new VBox(8);
        seccion.setPadding(new Insets(8, 0, 0, 0));

        Label titulo = new Label(IdiomaUtil.obtener("ctrl.licencia.activar.titulo"));
        titulo.getStyleClass().add("estado-texto-bold");
        titulo.setStyle("-fx-font-weight: 700; -fx-text-fill: #d4af37;");

        HBox fila = new HBox(8);
        fila.setAlignment(Pos.CENTER_LEFT);

        TextField campoKey = new TextField();
        campoKey.setPromptText(IdiomaUtil.obtener("ctrl.licencia.activar.placeholder"));
        campoKey.getStyleClass().add("texto-secundario-sm");
        campoKey.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: #f5f5f5; " +
            "-fx-border-color: #404040; -fx-border-radius: 6; -fx-background-radius: 6; " +
            "-fx-prompt-text-fill: #666;");
        campoKey.setPrefWidth(380);
        campoKey.setMaxWidth(380);
        HBox.setHgrow(campoKey, Priority.ALWAYS);

        Button btnActivar = new Button(IdiomaUtil.obtener("ctrl.licencia.activar.boton"));
        btnActivar.getStyleClass().add("texto-secundario-sm");
        btnActivar.setStyle("-fx-background-color: linear-gradient(to bottom, #d4af37, #b8984e); " +
            "-fx-text-fill: #0a0a0a; -fx-font-weight: 700; " +
            "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 16;");
        btnActivar.setMinWidth(90);

        Label lblError = new Label();
        lblError.getStyleClass().add("texto-hint-sm");
        lblError.setStyle("-fx-text-fill: #ff6b6b;");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        Label lblExito = new Label();
        lblExito.getStyleClass().add("texto-hint-sm");
        lblExito.setStyle("-fx-text-fill: #a8b991;");
        lblExito.setWrapText(true);
        lblExito.setVisible(false);
        lblExito.setManaged(false);

        btnActivar.setOnAction(e -> {
            String key = campoKey.getText().trim();
            if (key.isBlank()) {
                mostrarMensaje(lblError, lblExito, IdiomaUtil.obtener("ctrl.licencia.dialog.error_vacia"));
                return;
            }
            if (!key.startsWith("BRX-")) {
                mostrarMensaje(lblError, lblExito, IdiomaUtil.obtener("ctrl.licencia.dialog.error_formato"));
                return;
            }

            btnActivar.setDisable(true);
            btnActivar.setText(IdiomaUtil.obtener("ctrl.licencia.dialog.validando"));
            lblError.setVisible(false);
            lblError.setManaged(false);
            lblExito.setVisible(false);
            lblExito.setManaged(false);

            new Thread(() -> {
                var resultado = servicio.activarLicencia(key);
                Platform.runLater(() -> {
                    btnActivar.setDisable(false);
                    btnActivar.setText(IdiomaUtil.obtener("ctrl.licencia.activar.boton"));

                    if (resultado.estado() == LicenciaServicio.EstadoLicencia.VALID
                            || resultado.estado() == LicenciaServicio.EstadoLicencia.TRIAL) {
                        lblExito.setText(IdiomaUtil.obtener("ctrl.licencia.activar.exito"));
                        lblExito.setVisible(true);
                        lblExito.setManaged(true);
                        lblError.setVisible(false);
                        lblError.setManaged(false);
                        // Reabrir el modal para refrescar datos
                        gestor.cerrarModalActual();
                        abrir();
                    } else {
                        mostrarMensaje(lblError, lblExito, resultado.mensaje());
                    }
                });
            }).start();
        });

        fila.getChildren().addAll(campoKey, btnActivar);
        seccion.getChildren().addAll(titulo, fila, lblError, lblExito);
        return seccion;
    }

    private void mostrarMensaje(Label lblError, Label lblExito, String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
        lblExito.setVisible(false);
        lblExito.setManaged(false);
    }

    // ==================== HELPERS PARA PRESENTACIÓN ====================

    private String obtenerTipoPlan(LicenciaServicio.ResultadoValidacion resultado) {
        if (resultado.estado() == LicenciaServicio.EstadoLicencia.TRIAL
                || resultado.estado() == LicenciaServicio.EstadoLicencia.TRIAL_EXPIRED) {
            return IdiomaUtil.obtener("ctrl.licencia.tipo.trial");
        }
        if (resultado.plan() != null) {
            return switch (resultado.plan().toLowerCase()) {
                case "starter" -> "Starter";
                case "pro" -> "Pro";
                case "enterprise" -> "Enterprise";
                default -> resultado.plan();
            };
        }
        return IdiomaUtil.obtener("ctrl.licencia.tipo.desconocido");
    }

    private String obtenerEstadoTexto(LicenciaServicio.ResultadoValidacion resultado) {
        return switch (resultado.estado()) {
            case VALID -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.activa");
            case TRIAL -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.trial_activo");
            case TRIAL_EXPIRED -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.trial_expirado");
            case EXPIRED -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.expirada");
            case REVOKED -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.revocada");
            case OFFLINE -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.offline");
            default -> "\u25CF " + IdiomaUtil.obtener("ctrl.licencia.estado.error");
        };
    }

    private String obtenerColorEstado(LicenciaServicio.ResultadoValidacion resultado) {
        return switch (resultado.estado()) {
            case VALID -> "#a8b991";
            case TRIAL -> "#daa520";
            case TRIAL_EXPIRED, EXPIRED, REVOKED -> "#ff6b6b";
            case OFFLINE -> "#d4af37";
            default -> "#ff9800";
        };
    }

    private String obtenerValidaHasta(LicenciaServicio.ResultadoValidacion resultado) {
        if (resultado.expira() != null && !resultado.expira().isBlank()) {
            try {
                // Intentar formatear fecha ISO
                var instant = java.time.Instant.parse(resultado.expira());
                var fecha = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception e) {
                // Si es un LocalDate string (del trial)
                try {
                    var fecha = java.time.LocalDate.parse(resultado.expira());
                    return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (Exception ignored) {
                    return resultado.expira();
                }
            }
        }
        return IdiomaUtil.obtener("ctrl.licencia.valida.no_disponible");
    }

    private String obtenerNotaContextual(LicenciaServicio.ResultadoValidacion resultado) {
        return switch (resultado.estado()) {
            case TRIAL -> IdiomaUtil.obtener("ctrl.licencia.nota.trial");
            case TRIAL_EXPIRED -> IdiomaUtil.obtener("ctrl.licencia.nota.trial_expirado");
            case EXPIRED -> IdiomaUtil.obtener("ctrl.licencia.nota.expirada");
            case VALID -> IdiomaUtil.obtener("ctrl.licencia.nota.activa");
            default -> IdiomaUtil.obtener("ctrl.licencia.nota");
        };
    }

    private String enmascararKey(String key) {
        if (key.length() <= 8) return key;
        return key.substring(0, 8) + "****" + key.substring(key.length() - 4);
    }

    // ==================== UTILIDADES PRIVADAS ====================

    private String cargarTextoLicencia() {
        String[] rutas = {"LICENSE", "../LICENSE", "../../LICENSE"};
        for (String ruta : rutas) {
            try {
                Path path = Paths.get(ruta);
                if (Files.exists(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                // Intentar siguiente ruta
            }
        }

        try (var input = getClass().getResourceAsStream("/LICENSE")) {
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.debug("Licencia no encontrada en classpath: {}", e.getMessage());
        }

        return "LICENCIA DE USO DE SOFTWARE KIPU\n" +
            "Basada en la Elastic License 2.0\n\n" +
            "Titular de derechos: Dilan Acuña / Kipu\n" +
            "Software: Kipu — Sistema POS para la gestión de comercios.\n" +
            "Año de creación: 2026\n" +
            "República de Colombia\n\n" +
            "Este software tiene el código fuente disponible públicamente (\"source-available\"),\n" +
            "lo que NO equivale a software libre ni de código abierto (\"open source\").\n\n" +
            "Para más información, consulte el archivo LICENSE en la raíz del proyecto.";
    }
}
