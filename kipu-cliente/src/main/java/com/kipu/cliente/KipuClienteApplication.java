/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente;

import com.kipu.cliente.configuracion.ConfiguracionCliente;
import com.kipu.cliente.controlador.ShellController;
import com.kipu.cliente.servicio.LicenciaServicio;
import com.kipu.cliente.servicio.LogCriticoServicio;
import com.kipu.cliente.servicio.VerificacionActualizacionServicio;
import com.kipu.cliente.utilidad.DetectorPantalla;
import com.kipu.cliente.utilidad.IdiomaUtil;
import com.kipu.cliente.utilidad.NavegacionUtil;
import com.kipu.cliente.utilidad.ServidorEmbebido;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LogCriticoDto;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Clase principal de la aplicación cliente Kipu.
 Se encarga de iniciar la interfaz gráfica, manejar el modo host (servidor embebido) y configurar el entorno gráfico.
 También instala un handler global para detectar errores específicos de JavaFX relacionados con texturas GPU, y maneja la navegación personalizada con la tecla ESC.
 El servidor embebido se inicia en un hilo separado para no bloquear la interfaz, y se muestra una pantalla de splash con progreso durante el arranque.
 Al cerrar la ventana, solo se desconecta del servidor pero no se detiene, permitiendo que otros terminales sigan conectados o que este terminal se reconecte rápidamente.
 */
public class KipuClienteApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(KipuClienteApplication.class);

    /** Detector y gestor de resolución de pantalla (disponible para la UI de configuración) */
    private static DetectorPantalla detectorPantalla;

    @Override
    public void start(Stage escenarioPrincipal) throws Exception {
        // Instalar handler global para errores del render thread de JavaFX (Prism).
        // Cuando la GPU no puede crear una textura (RTTexture null), el Canvas
        // queda corrupto y genera NullPointerExceptions infinitas en el QuantumRenderer.
        // Este handler detecta ese error específico y activa una flag global
        // para que los componentes con Canvas (FondoAnimado, LineaDivisoriaInteractiva)
        // se desactiven en el siguiente frame.
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
            String mensaje = error.getMessage() != null ? error.getMessage() : "";
            boolean errorPrismCanvas = mensaje.contains("RTTexture") 
                    || mensaje.contains("NGCanvas")
                    || (error instanceof NullPointerException 
                        && error.getStackTrace().length > 0 
                        && error.getStackTrace()[0].getClassName().contains("NGCanvas"));
            
            if (errorPrismCanvas) {
                var motor = com.kipu.cliente.componente.MotorAnimaciones.instancia();
                boolean primerError = motor.esPrimerErrorGpu();
                motor.marcarCanvasCorrupto();
                if (primerError) {
                    logger.warn("Error de textura GPU detectado en hilo {}. "
                            + "Canvas desactivados para evitar loop de errores.", hilo.getName());
                    // Registrar error de renderizado como log crítico en el servidor
                    enviarLogCritico("ERROR", "PrismCanvas",
                            "Error de textura GPU — Canvas corrupto en hilo " + hilo.getName(), mensaje);
                }
            } else {
                // Para otros errores no manejados, solo loggear
                logger.error("Error no capturado en hilo {}: {}", hilo.getName(), error.getMessage(), error);
                // Registrar errores graves (no menores) como log crítico
                enviarLogCritico("CRITICO", error.getClass().getSimpleName(),
                        error.getMessage() != null ? error.getMessage() : "Error sin mensaje",
                        obtenerStackTrace(error));
            }
        });

        // Al cerrar la ventana, solo desconectar del servidor (NO detenerlo)
        // El servidor permanece activo para otros terminales y para reconexión rápida
        escenarioPrincipal.setOnCloseRequest(event -> {
            logger.info("Cerrando aplicación Kipu (el servidor sigue activo)...");
            if (ServidorEmbebido.esHost()) {
                ServidorEmbebido.desconectar();
            }
            Platform.exit();
            System.exit(0);
        });

        // Aplicar configuración de gráficos persistida por el usuario
        if (!ConfiguracionCliente.isAnimacionesActivas()) {
            com.kipu.cliente.componente.MotorAnimaciones.instancia().setDesactivadas(true);
            logger.info("Animaciones desactivadas por configuración del usuario");
        }

        // Inicializar el idioma del sistema según la configuración persistida
        IdiomaUtil.cambiarIdioma(ConfiguracionCliente.getIdioma());
        // Icono de la ventana 
        try {
            escenarioPrincipal.getIcons().add(
                    new Image(getClass().getResourceAsStream("/imagenes/ICON.png")));
        } catch (Exception e) {
            logger.warn("No se pudo cargar el icono de ventana ICON.png");
        }

        if (ConfiguracionCliente.isHostMode()) {
            logger.info("Host Mode activado");
            cargarLoginPrincipal(escenarioPrincipal);

            // Verificar si necesita configuración inicial de PostgreSQL
            if (ConfiguracionCliente.necesitaSetupInicial()) {
                logger.info("[Host] Setup inicial requerido — mostrando asistente PostgreSQL");
                Platform.runLater(() -> {
                    ShellController shell = ShellController.getInstancia();
                    if (shell != null) {
                        shell.mostrarAsistenteBaseDatos(completado -> {
                            if (completado) {
                                logger.info("[Host] Setup completado — iniciando servidor embebido");
                                iniciarServidorHostMode();
                            } else {
                                logger.warn("[Host] Setup cancelado — el servidor no se iniciará");
                            }
                        });
                    }
                });
            } else {
                // Setup ya completado, intentar iniciar servidor
                iniciarServidorHostMode();
            }
        } else {
            logger.info("Modo cliente — conectando a servidor externo: {}",
                    ConfiguracionCliente.getUrlServidor());
            cargarLoginPrincipal(escenarioPrincipal);
        }

        // Verificar licencia y actualizaciones en background (no bloquear el UI thread)
        verificarLicenciaYActualizaciones();
    }

    /**
     * Inicia el servidor embebido en host mode. Si falla por error de BD,
     * muestra el asistente de PostgreSQL para que el usuario reconfigure.
     */
    private void iniciarServidorHostMode() {
        ServidorEmbebido.iniciarServidor(msg ->
            Platform.runLater(() -> logger.debug("[Host] {}", msg))
        ).whenComplete((exito, error) -> {
            if (error != null) {
                String mensajeError = error.getCause() != null
                        ? error.getCause().getMessage() : error.getMessage();
                logger.error("[Host] Error al iniciar servidor: {}", mensajeError);

                // Si el error es de BD, mostrar asistente para reconfigurar
                boolean errorDb = mensajeError != null && (
                        mensajeError.contains("PostgreSQL") ||
                        mensajeError.contains("FATAL") ||
                        mensajeError.contains("Connection refused") ||
                        mensajeError.contains("password authentication") ||
                        mensajeError.contains("does not exist") ||
                        mensajeError.contains("setup-inicial"));

                if (errorDb) {
                    Platform.runLater(() -> {
                        ShellController shell = ShellController.getInstancia();
                        if (shell != null) {
                            logger.info("[Host] Error de BD detectado — mostrando asistente PostgreSQL");
                            shell.mostrarAsistenteBaseDatos(completado -> {
                                if (completado) {
                                    iniciarServidorHostMode(); // Reintentar tras reconfiguración
                                }
                            });
                        }
                    });
                }
            } else {
                logger.info("[Host] Servidor embebido iniciado exitosamente");
            }
        });
    }

    /**
     * Verifica la licencia y busca actualizaciones en un hilo de fondo.
     *
     * <p>Flujo:
     * 1. Valida la licencia (trial local o key contra backend web).
     * 2. Si TRIAL_EXPIRED/EXPIRED/REVOKED/NO_KEY → muestra diálogo bloqueante.
     * 3. Si faltan ≤10 días para expirar → muestra aviso de renovación.
     * 4. En paralelo, verifica si hay una nueva versión disponible.
     */
    private void verificarLicenciaYActualizaciones() {
        // Hilo de fondo separado para no bloquear JavaFX
        Thread bgThread = new Thread(() -> {
            logger.info("[Boot] Verificando licencia...");
            var licenciaServicio = new LicenciaServicio();
            var resultadoLicencia = licenciaServicio.cargarYValidar();
            logger.info("[Boot] Estado de licencia: {} | Días restantes: {}",
                    resultadoLicencia.estado(), resultadoLicencia.diasRestantes());

            boolean licenciaProblematica = switch (resultadoLicencia.estado()) {
                case VALID, TRIAL -> false;
                case OFFLINE -> false;   // Se muestra aviso pero no bloquea totalmente
                default -> true;         // TRIAL_EXPIRED, EXPIRED, REVOKED, NO_KEY, ERROR → mostrar diálogo
            };

            // Verificar si necesita aviso de renovación (≤10 días)
            boolean necesitaAvisoRenovacion = !licenciaProblematica
                    && resultadoLicencia.diasRestantes() >= 0
                    && resultadoLicencia.diasRestantes() <= LicenciaServicio.DIAS_AVISO_RENOVACION
                    && (resultadoLicencia.estado() == LicenciaServicio.EstadoLicencia.VALID
                        || resultadoLicencia.estado() == LicenciaServicio.EstadoLicencia.TRIAL);

            Platform.runLater(() -> {
                ShellController shell = ShellController.getInstancia();
                if (shell == null) return;

                if (licenciaProblematica) {
                    shell.mostrarDialogoLicencia(resultadoLicencia, getHostServices(), null);
                } else if (necesitaAvisoRenovacion) {
                    // Mostrar aviso de renovación no-bloqueante
                    shell.mostrarBannerRenovacion(resultadoLicencia.diasRestantes(), getHostServices());
                } else if (resultadoLicencia.estado() == LicenciaServicio.EstadoLicencia.OFFLINE) {
                    logger.info("[License] Modo offline activo. Razón: {}", resultadoLicencia.mensaje());
                }
            });

            // Verificar actualizaciones (siempre, independiente del estado de licencia)
            logger.info("[Boot] Verificando actualizaciones...");
            var updateServicio = new VerificacionActualizacionServicio();
            updateServicio.verificarActualizacion().ifPresent(updateInfo ->
                Platform.runLater(() -> {
                    ShellController shell = ShellController.getInstancia();
                    if (shell != null) {
                        shell.mostrarBannerActualizacion(updateInfo, getHostServices());
                    }
                })
            );

        }, "kipu-bg-checks");
        bgThread.setDaemon(true); // No impedir que la JVM cierre si solo queda este hilo
        bgThread.start();
    }

    // Carga el shell raíz de la aplicación (una sola Scene para toda la vida de la app)
    // y después carga la vista de login como primera subvista dentro del shell.
    private void cargarLoginPrincipal(Stage escenarioPrincipal) throws Exception {
        FXMLLoader cargador = new FXMLLoader(getClass().getResource("/vista/shell.fxml"));
        cargador.setResources(IdiomaUtil.obtenerBundle());
        Parent raiz = cargador.load();

        // Detectar pantalla y seleccionar perfil de resolución óptimo
        detectorPantalla = new DetectorPantalla();
        detectorPantalla.detectarYSeleccionar();

        // El shell se dimensiona según el perfil detectado (no siempre 1920x1080)
        javafx.scene.layout.StackPane contenedorShell = null;
        if (raiz instanceof javafx.scene.layout.StackPane sp) {
            contenedorShell = sp;
        }

        // Group envuelve al shell para que el Scene no interfiera con su layout interno
        javafx.scene.Group grupo = new javafx.scene.Group(raiz);
        javafx.scene.layout.StackPane contenedorEscalado = new javafx.scene.layout.StackPane(grupo);
        contenedorEscalado.setStyle("-fx-background-color: #0a0a0a;");
        // Centrar el contenido para distribuir barras simétricamente en ratios no nativos
        javafx.scene.layout.StackPane.setAlignment(grupo, javafx.geometry.Pos.CENTER);

        Scene escena = new Scene(contenedorEscalado);
        escena.getStylesheets().add(getClass().getResource("/css/estilos.css").toExternalForm());
        escenarioPrincipal.setTitle(IdiomaUtil.obtener("app.titulo_login"));
        escenarioPrincipal.setScene(escena);
        
        // 1. Configuración de PANTALLA COMPLETA PERSISTENTE
        escenarioPrincipal.setMaximized(true);
        if (ConfiguracionCliente.isPantallaCompleta()) {
            escenarioPrincipal.setFullScreenExitHint(""); // Desactivar mensaje de "presione ESC para salir" 
            escenarioPrincipal.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
            escenarioPrincipal.setFullScreen(true);
        }
        
        // 2. Manejador global para la tecla ESC (Navegación personalizada)
        escenarioPrincipal.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                event.consume();
                com.kipu.cliente.utilidad.NavegacionUtil.manejarTeclaEsc(escenarioPrincipal);
            }
        });
        
        escenarioPrincipal.show();

        // 3. Aplicar perfil de resolución: dimensiones del shell, escalado, clases CSS, font-size
        if (contenedorShell != null) {
            detectorPantalla.aplicar(contenedorShell, contenedorEscalado, escena);
        }

        // Cargar login dentro del shell (reemplaza el splash)
        com.kipu.cliente.controlador.ShellController.getInstancia()
                .cargarVista("/vista/login-pin.fxml");
    }

    /** Obtiene el detector de pantalla para acceso desde la UI de configuración */
    public static DetectorPantalla getDetectorPantalla() {
        return detectorPantalla;
    }

    @Override
    public void stop() throws Exception {
        // Solo desconectar el hilo de logs — el servidor NO se detiene
        if (ServidorEmbebido.esHost()) {
            logger.info("Desconectando del servidor embebido (permanece activo)...");
            ServidorEmbebido.desconectar();
        }
        super.stop();
    }

    /**
     * Envía un log crítico al servidor de forma asíncrona.
     * Solo envía si hay una sesión activa con token JWT.
     * No lanza excepciones para no interferir con el manejo de errores existente.
     */
    private void enviarLogCritico(String nivel, String origen, String mensaje, String detalle) {
        try {
            AuthRespuestaDto usuario = NavegacionUtil.getUsuarioActual();
            if (usuario == null || usuario.getToken() == null) {
                return; // Sin sesión activa, no se puede enviar
            }

            LogCriticoDto dto = LogCriticoDto.builder()
                    .nivel(nivel)
                    .origen(origen)
                    .mensaje(mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje)
                    .detalle(detalle != null && detalle.length() > 4000
                            ? detalle.substring(0, 4000) + "\n... [truncado]" : detalle)
                    .usuario(usuario.getNombreUsuario())
                    .ipCliente(ConfiguracionCliente.getIpLocal())
                    .nombreCliente(ConfiguracionCliente.getNombreCliente())
                    .build();

            new LogCriticoServicio(usuario.getToken())
                    .registrarAsync(dto)
                    .exceptionally(ex -> {
                        logger.debug("No se pudo enviar log crítico al servidor: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            // Silenciar — no agregar más errores al handler de errores
            logger.debug("Error al preparar envío de log crítico: {}", e.getMessage());
        }
    }

    // Convierte el stack trace de un Throwable a String (limitado a 4000 chars)
    private String obtenerStackTrace(Throwable error) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(sw));
            String trace = sw.toString();
            return trace.length() > 4000 ? trace.substring(0, 4000) + "\n... [truncado]" : trace;
        } catch (Exception e) {
            return error.toString();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
