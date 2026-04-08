/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.cliente;

/* Clase launcher para el cliente JavaFX. Este es el punto de entrada del fat JAR.
 Se encarga de configurar propiedades del sistema para desactivar el escalado DPI automático de Windows,
 lo cual es crucial para que la interfaz gráfica se muestre correctamente en monitores con escalado 125% o 150%.
 Sin estas propiedades, JavaFX escala toda la UI multiplicando tamaños de fuente, botones y layouts, rompiendo el diseño pensado para 1920x1080 al 100%.

 Propiedades clave:
 - glass.win.uiScale=1.0: Fuerza el factor de escala del toolkit Glass a 1x
 - prism.allowHiDPIScaling=false: Desactiva el escalado HiDPI del renderizador Prism 
 - Estandariza la aparencia diseñada para resoluciones 1920x1080 al 100% */
 
public class KipuClienteLauncher {

    /* Método main que se ejecuta al iniciar el cliente. Configura las propiedades del sistema para desactivar el escalado automático de Windows y luego lanza la aplicación JavaFX principal (KipuClienteApplication).
     Es importante establecer estas propiedades antes de inicializar cualquier componente gráfico para asegurar que la UI se renderice con las dimensiones correctas en monitores con escalado. */
    public static void main(String[] args) {
        System.setProperty("glass.win.uiScale", "1.0");
        System.setProperty("prism.allowHiDPIScaling", "false");
        
        KipuClienteApplication.main(args);
    }
}
