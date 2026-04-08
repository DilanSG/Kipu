/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

//Clase principal de la aplicación Kipu Servidor
@SpringBootApplication(exclude = {MongoAutoConfiguration.class})
@EnableJpaAuditing
@EnableScheduling
public class KipuServidorApplication {

    public static void main(String[] args) {
        SpringApplication.run(KipuServidorApplication.class, args);
    }
}
