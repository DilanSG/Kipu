/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.kipu.servidor.servicio.RegistroClientesServicio;
import java.io.IOException;
import java.util.Collections;

/*Filtro de autenticación JWT que intercepta todas las peticiones HTTP.
 * Este filtro es lo principal del sistema de autenticación stateless.
 * Se ejecuta una vez por cada petición HTTP (OncePerRequestFilter) antes de que
 * llegue a los controladores.
 * 1. Intercepta cada petición HTTP entrante
 * 2. Extrae el token JWT del header Authorization (formato: "Bearer <token>")
 * 3. Valida el token usando JwtUtil
 * 4. Si es válido, extrae nombreUsuario y rol del token
 * 5. Crea un Authentication object y lo establece en SecurityContext
 * 6. Spring Security usa este contexto para autorizar el acceso a endpoints
 * 7. Registra la actividad del cliente en RegistroClientesServicio 
 * El SecurityContext establecido aquí es usado por:
 * - @PreAuthorize en los controladores para verificar roles
 * - ConfiguracionSeguridad para aplicar reglas de autorización */
@Component
public class JwtFiltroAutenticacion extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RegistroClientesServicio registroClientesServicio;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                String nombreUsuario = jwtUtil.extraerNombreUsuario(token);
                String rol = jwtUtil.extraerRol(token);
                
                if (nombreUsuario != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    if (jwtUtil.validarToken(token, nombreUsuario)) {
                        UsernamePasswordAuthenticationToken authToken = 
                            new UsernamePasswordAuthenticationToken(
                                nombreUsuario, 
                                null, 
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + rol))
                            );
                        
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        String ipCliente = obtenerIpCliente(request);
                        String nombreCliente = request.getHeader("X-Client-Name");
                        String ipReportada = request.getHeader("X-Client-IP");
                        registroClientesServicio.registrar(ipCliente, nombreUsuario, rol,
                            nombreCliente, ipReportada);
                    }
                }
            } catch (Exception e) {
                logger.error("Error procesando JWT", e);
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
