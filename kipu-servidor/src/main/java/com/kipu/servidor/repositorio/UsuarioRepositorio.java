/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.servidor.repositorio;

import com.kipu.servidor.modelo.entidad.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Usuario.
 * Se proporcionan métodos para acceder a la base de datos
 * sin escribir SQL directamente. Spring Data JPA genera automáticamente la implementación.
 * Herencia de JpaRepository:
 * Con JpaRepository<Usuario, Long>, obtenemos automáticamente métodos como:
 * - save(usuario): Insertar o actualizar un usuario
 * - findById(id): Buscar usuario por ID
 * - findAll(): Obtener todos los usuarios
 * - delete(usuario): Eliminar un usuario
 * - count(): Contar total de usuarios
 * - existsById(id): Verificar si existe un usuario con ese ID
 * Métodos personalizados:
 * 1-findByNombreUsuario(String nombreUsuario):
 *    - Busca un usuario por su nombre de usuario (único)
 *    - Retorna Optional<Usuario> porque puede no existir
 *    - Spring genera automáticamente: SELECT * FROM usuarios WHERE nombre_usuario = ?
 * 2. existsByNombreUsuario(String nombreUsuario):
 *    - Verifica si ya existe un usuario con ese nombre
 *    - Retorna boolean (true si existe, false si no)
 *    - Spring genera: SELECT COUNT(*) > 0 FROM usuarios WHERE nombre_usuario = ?
 * 3. findByNombreUsuarioAndActivoTrue(String nombreUsuario):
 *    - Busca usuario por nombre Y que esté activo (no eliminado lógicamente)
 *    - Retorna Optional<Usuario>
 *    - Spring genera: SELECT * FROM usuarios WHERE nombre_usuario = ? AND activo = true
 *    - Usado en: Login (solo usuarios activos pueden autenticarse)
 * 4. findByCodigo(String codigo):
 *    - Busca un usuario por código de empleado
 *    - Retorna Optional<Usuario>
 *    - Spring genera automáticamente: SELECT * FROM usuarios WHERE codigo = ?
 * 5. existsByCodigo(String codigo):
 *    - Verifica si ya existe un usuario con ese código
 *    - Retorna boolean (true si existe, false si no)
 *    - Spring genera: SELECT COUNT(*) > 0 FROM usuarios WHERE codigo = ?
 * 6. findByCodigoAndActivoTrue(String codigo):
 *    - Busca un usuario activo por código de empleado
 *    - Retorna Optional<Usuario>
 *    - Spring genera: SELECT * FROM usuarios WHERE codigo = ? AND activo = true
 *    - Usado en: Login (solo usuarios activos pueden autenticarse)
 * 7. buscarPorTermino(String termino):
 *    - Busca usuarios por término de búsqueda en nombre completo, nombre de usuario o código
 *    - Retorna List<Usuario>
 *    - Spring genera automáticamente la consulta JPQL definida en @Query
 * 8. findByRolAndActivoTrue(Rol rol):
 *    - Busca usuarios activos por rol
 *    - Retorna List<Usuario>
 *    - Spring genera: SELECT * FROM usuarios WHERE rol = ? AND activo = true
 * Convención de nombres (Query Methods):
 * Spring entiende el nombre del método y genera el SQL automáticamente:
 * - findBy: SELECT
 * - And: condición AND
 * - Or: condición OR
 * - True/False: valores booleanos
 * - OrderBy: ORDER BY
 * 
 * @see Usuario entidad JPA mapeada a la tabla 'usuarios'
 * @see JpaRepository interfaz base de Spring Data JPA
 */
@Repository // Marca esta interfaz como un bean de Spring para inyección de dependencias
public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);
    boolean existsByNombreUsuario(String nombreUsuario);
    Optional<Usuario> findByNombreUsuarioAndActivoTrue(String nombreUsuario);
    Optional<Usuario> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    Optional<Usuario> findByCodigoAndActivoTrue(String codigo);
    @Query("SELECT u FROM Usuario u WHERE " +
           "LOWER(u.nombreCompleto) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(u.nombreUsuario) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "u.codigo LIKE CONCAT('%', :termino, '%')")
    List<Usuario> buscarPorTermino(@Param("termino") String termino);
    List<Usuario> findByRolAndActivoTrue(com.kipu.common.enums.Rol rol);
}

