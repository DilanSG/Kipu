/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.configuracion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*Cargador automático de archivos .env para el servidor Kipu.
 *Este {@link EnvironmentPostProcessor} se ejecuta ANTES de que Spring Boot resuelva
 *los placeholders de propiedades (como {@code ${JWT_SECRET}}), lo que permite que las
 *variables definidas en un archivo .env estén disponibles para la configuración.
 *Esto resuelve el problema de que los ejecutables generados por jpackage
 *no pasan por los scripts wrapper que hacen {@code source .env}, ya que el binario
 *instalado se lanza directamente sin ningún shell que cargue variables de entorno.
 *Ubicaciones de búsqueda (en orden de prioridad)
 *   $HOME/.kipu/.env — Ubicación estándar para instalaciones .deb/.rpm
 *   /etc/kipu/.env — Ubicación global del sistema
 *   ./.env — Directorio de trabajo actual (desarrollo)
 *Se carga el primer archivo encontrado. Si ya existen variables de entorno del sistema
 *con el mismo nombre, estas tienen prioridad sobre las del archivo .env. */
public class CargadorArchivoEnv implements EnvironmentPostProcessor {

    private static final String NOMBRE_FUENTE_PROPIEDADES = "archivoEnvKipu";

    private static final List<Path> UBICACIONES_ENV = List.of(
            Path.of(System.getProperty("user.home"), ".kipu", ".env"),
            Path.of("/etc/kipu/.env"),
            Path.of(".env")
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        if (entorno.containsProperty("JWT_SECRET") && entorno.containsProperty("DB_PASSWORD")) {
            return;
        }

        for (Path ubicacion : UBICACIONES_ENV) {
            if (Files.isReadable(ubicacion)) {
                Map<String, Object> propiedades = cargarArchivoEnv(ubicacion);
                if (!propiedades.isEmpty()) {
                    entorno.getPropertySources()
                            .addLast(new MapPropertySource(NOMBRE_FUENTE_PROPIEDADES, propiedades));
                    System.out.println("[Kipu] Variables cargadas desde: " + ubicacion.toAbsolutePath());
                    return;
                }
            }
        }

        System.err.println("[Kipu] ADVERTENCIA: No se encontró archivo .env en ninguna ubicación conocida.");
        System.err.println("  Ubicaciones buscadas:");
        for (Path ubicacion : UBICACIONES_ENV) {
            System.err.println("    - " + ubicacion.toAbsolutePath());
        }
        System.err.println("  Ejecute 'setup-inicial.sh' (Linux) o 'setup-inicial.bat' (Windows), o cree el archivo manualmente.");
    }

    private Map<String, Object> cargarArchivoEnv(Path ruta) {
        Map<String, Object> propiedades = new HashMap<>();
        try (BufferedReader lector = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                linea = linea.trim();

                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }

                int separador = linea.indexOf('=');
                if (separador <= 0) {
                    continue;
                }

                String clave = linea.substring(0, separador).trim();
                String valor = linea.substring(separador + 1).trim();

                valor = eliminarComillas(valor);

                if (clave.startsWith("export ")) {
                    clave = clave.substring(7).trim();
                }

                if (!clave.isEmpty()) {
                    propiedades.put(clave, valor);
                }
            }
        } catch (IOException e) {
            System.err.println("[Kipu] Error leyendo archivo .env: " + ruta + " - " + e.getMessage());
        }
        return propiedades;
    }

  private String eliminarComillas(String valor) {
        if (valor.length() >= 2) {
            if ((valor.startsWith("\"") && valor.endsWith("\""))
                    || (valor.startsWith("'") && valor.endsWith("'"))) {
                return valor.substring(1, valor.length() - 1);
            }
        }
        return valor;
    }
}
