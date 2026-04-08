/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.controlador;

import com.kipu.common.constantes.Constantes;
import com.kipu.common.dto.AuthRespuestaDto;
import com.kipu.common.dto.LoginDto;
import com.kipu.common.dto.RespuestaApi;
import com.kipu.servidor.servicio.AutenticacionServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*Controlador REST para autenticación de usuarios.
 *Endpoints disponibles:
 *-POST /api/usuarios/login: Autenticación con credenciales
 *-GET /api/usuarios/health: Health check para verificar conectividad */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class AutenticacionController {

    private final AutenticacionServicio autenticacionServicio;

    @PostMapping("/login")
    public ResponseEntity<RespuestaApi<AuthRespuestaDto>> login(@Valid @RequestBody LoginDto loginDto) {
        AuthRespuestaDto respuesta = autenticacionServicio.login(loginDto);
        return ResponseEntity.ok(RespuestaApi.exitosa(respuesta, Constantes.Mensajes.LOGIN_EXITOSO));
    }

    @GetMapping("/health")
    public ResponseEntity<RespuestaApi<String>> health() {
        return ResponseEntity.ok(RespuestaApi.exitosa("OK", Constantes.Mensajes.SERVICIO_FUNCIONANDO));
    }
}
