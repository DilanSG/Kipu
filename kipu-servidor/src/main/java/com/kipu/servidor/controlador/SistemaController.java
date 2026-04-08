/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.controlador;

import com.kipu.common.dto.ClienteConectadoDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.servidor.servicio.RegistroClientesServicio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/sistema")
@RequiredArgsConstructor
public class SistemaController {

    private static final Logger logger = LoggerFactory.getLogger(SistemaController.class);

    private final RegistroClientesServicio registroClientesServicio;

    @GetMapping("/clientes-conectados")
    public ResponseEntity<RespuestaApi<List<ClienteConectadoDto>>> obtenerClientesConectados() {
        logger.debug("Consultando clientes conectados");
        List<ClienteConectadoDto> clientes = registroClientesServicio.obtenerClientesActivos();
        return ResponseEntity.ok(
                RespuestaApi.exitosa(clientes, "Clientes conectados: " + clientes.size()));
    }
}
