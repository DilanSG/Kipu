/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/ 
package com.baryx.servidor.modelo.entidad;

import com.baryx.common.enums.Genero;
import com.baryx.common.enums.Rol;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Usuario extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", unique = true, length = 50)
    private String nombreUsuario;

    @Column(name = "contrasena")
    private String contrasena;
    
    @Column(name = "codigo", unique = true, length = 2)
    private String codigo;
    
    @Column(name = "pin", length = 255)
    private String pin;

    @Column(name = "nombre_completo", nullable = false, length = 100)
    private String nombreCompleto;

    @Column(name = "email", length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "genero", nullable = false, length = 20)
    private Genero genero;

    @Builder.Default
    @Column(name = "bloqueado", nullable = false)
    private Boolean bloqueado = false;

    @Builder.Default
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;
}
