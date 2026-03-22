/* Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.*/
package com.baryx.cliente.componente;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.util.Duration;
import java.util.HashSet;
import java.util.Set;

/*Motor centralizado para manejar animaciones en toda la aplicación.
- Permite pausar/reanudar animaciones globalmente (útil para modo ahorro de energía).
- Detecta si el canvas de JavaFX está corrupto y degrada a modo estático para evitar bloqueos.
- Provee métodos helper para transiciones comunes (fade, slide, scale) que respetan el estado global de animaciones. */
public final class MotorAnimaciones {

    private static final MotorAnimaciones INSTANCIA = new MotorAnimaciones();
    private static final long NANOS_POR_FRAME = 33_333_333L; // ~30 FPS
    private final Set<Animable> registrados = new HashSet<>();
    private final AnimationTimer timer;
    private long ultimoFrameNanos = 0;
    private volatile boolean desactivadas = false;
    private volatile boolean canvasCorrupto = false;
    private volatile long ultimoErrorGpuMs = 0;

    private MotorAnimaciones() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (canvasCorrupto || desactivadas) {
                    stop();
                    return;
                }
                if (now - ultimoFrameNanos < NANOS_POR_FRAME) return;
                ultimoFrameNanos = now;

                for (Animable a : registrados.toArray(new Animable[0])) {
                    try {
                        if (a.esVisible()) {
                            a.renderizar();
                        }
                    } catch (Exception e) {
                        marcarCanvasCorrupto();
                        return;
                    }
                }
            }
        };
    }

    public static MotorAnimaciones instancia() { return INSTANCIA; }

    // ── Registro de componentes Canvas ──

    public void registrar(Animable a) {
        registrados.add(a);
        verificarEstadoTimer();
    }

    public void desregistrar(Animable a) {
        registrados.remove(a);
        verificarEstadoTimer();
    }

    /** Re-evalúa si el timer debe correr. Llamar cuando cambia la visibilidad de un componente. */
    public void notificarCambioVisibilidad() {
        verificarEstadoTimer();
    }

    private void verificarEstadoTimer() {
        if (desactivadas || canvasCorrupto || registrados.isEmpty()) {
            timer.stop();
            return;
        }
        boolean hayVisibles = false;
        for (Animable a : registrados) {
            if (a.esVisible()) { hayVisibles = true; break; }
        }
        if (hayVisibles) timer.start(); else timer.stop();
    }

    // ── Estado global ──

    public void setDesactivadas(boolean valor) {
        this.desactivadas = valor;
        if (valor) {
            timer.stop();
            for (Animable a : registrados.toArray(new Animable[0])) {
                a.degradarAEstatico();
            }
        } else {
            for (Animable a : registrados.toArray(new Animable[0])) {
                a.restaurarAnimacion();
            }
            verificarEstadoTimer();
        }
    }

    public boolean isDesactivadas() { return desactivadas; }

    /** Thread-safe: puede invocarse desde el hilo de render de Prism. */
    public void marcarCanvasCorrupto() {
        if (canvasCorrupto) return; // Ya procesado, evitar degradaciones duplicadas
        this.canvasCorrupto = true;
        this.ultimoErrorGpuMs = System.currentTimeMillis();
        Platform.runLater(() -> {
            timer.stop();
            for (Animable a : registrados.toArray(new Animable[0])) {
                a.degradarAEstatico();
            }
        });
    }

    public boolean isCanvasCorrupto() { return canvasCorrupto; }

    /** @return true si es el primer error GPU (no duplicado). Para evitar spam de logs. */
    public boolean esPrimerErrorGpu() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoErrorGpuMs > 2000) {
            ultimoErrorGpuMs = ahora;
            return true;
        }
        return false;
    }

    // ── Helpers para transiciones UI ──

    /** Fade con callback. Si las animaciones están desactivadas, aplica el valor final instantáneamente. */
    public static void fade(Node nodo, double desde, double hasta, double ms, Runnable alFinalizar) {
        if (INSTANCIA.desactivadas) {
            nodo.setOpacity(hasta);
            if (alFinalizar != null) Platform.runLater(alFinalizar);
            return;
        }
        FadeTransition ft = new FadeTransition(Duration.millis(ms), nodo);
        ft.setFromValue(desde);
        ft.setToValue(hasta);
        if (alFinalizar != null) ft.setOnFinished(e -> alFinalizar.run());
        ft.play();
    }

    /** Fade sin callback. */
    public static void fade(Node nodo, double desde, double hasta, double ms) {
        fade(nodo, desde, hasta, ms, null);
    }

    /** Fade + slide vertical en paralelo. */
    public static void fadeYDeslizar(Node nodo, double desdeOp, double hastaOp,
                                      double desdeY, double hastaY, double ms, Runnable alFinalizar) {
        if (INSTANCIA.desactivadas) {
            nodo.setOpacity(hastaOp);
            nodo.setTranslateY(hastaY);
            if (alFinalizar != null) Platform.runLater(alFinalizar);
            return;
        }
        FadeTransition ft = new FadeTransition(Duration.millis(ms), nodo);
        ft.setFromValue(desdeOp);
        ft.setToValue(hastaOp);
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), nodo);
        tt.setFromY(desdeY);
        tt.setToY(hastaY);
        ParallelTransition pt = new ParallelTransition(ft, tt);
        if (alFinalizar != null) pt.setOnFinished(e -> alFinalizar.run());
        pt.play();
    }

    /** Scale uniforme (X/Y) sin callback. */
    public static void escalar(Node nodo, double hasta, double ms) {
        if (INSTANCIA.desactivadas) {
            nodo.setScaleX(hasta);
            nodo.setScaleY(hasta);
            return;
        }
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), nodo);
        st.setToX(hasta);
        st.setToY(hasta);
        st.playFromStart();
    }
}
