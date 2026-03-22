/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.servidor.seguridad;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/*Utilidad para generar y validar tokens JWT (JSON Web Tokens).
 *Componentes del JWT:
 * 1. Header: Tipo de token y algoritmo de firma (HS256)
 * 2. Payload: Claims (datos) como nombreUsuario, rol, fechas de expiración
 * 3. Signature: Firma digital usando la SECRET_KEY para prevenir manipulación
 *Flujo:
 * 1. Usuario hace login con credenciales
 * 2. AutenticacionServicio valida y llama a generarToken()
 * 3. Token se envía al cliente en la respuesta
 * 4. Cliente incluye token en header Authorization: Bearer <token>
 * 5. JwtFiltroAutenticacion valida el token en cada petición
 * 6. Si es válido, establece el SecurityContext con los datos del usuario
 * Seguridad:
 * - La SECRET_KEY debe ser fuerte (mínimo 32 caracteres)
 * - La expiración del token es configurable pero siempre se recomienda no exceder 24 horas
 * - Si la SECRET_KEY cambia, todos los tokens existentes se invalidan  */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiracion}")
    private Long expiracion;

    @Value("${jwt.issuer}")
    private String issuer;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generarToken(String nombreUsuario, String rol) {
        Date ahora = new Date();
        Date fechaExpiracion = new Date(ahora.getTime() + expiracion);

        return Jwts.builder()
                .subject(nombreUsuario)
                .claim("rol", rol)
                .issuer(issuer)
                .issuedAt(ahora)
                .expiration(fechaExpiracion)
                .signWith(getSigningKey())
                .compact();
    }

    public String extraerNombreUsuario(String token) {
        return extraerClaims(token).getSubject();
    }

    public String extraerRol(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    public boolean validarToken(String token, String nombreUsuario) {
        try {
            String tokenUsuario = extraerNombreUsuario(token);
            return tokenUsuario.equals(nombreUsuario) && !tokenExpirado(token);
        } catch (Exception e) {
            logger.error("Error validando token: {}", e.getMessage());
            return false;
        }
    }

    private boolean tokenExpirado(String token) {
        return extraerClaims(token).getExpiration().before(new Date());
    }

    private Claims extraerClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            logger.error("Token expirado: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            logger.error("Error parseando token: {}", e.getMessage());
            throw e;
        }
    }
}
