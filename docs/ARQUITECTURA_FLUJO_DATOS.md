# Kipu - Documentacion de Arquitectura y Flujo de Datos

**Version**: 1.0.0  
**Ultima actualizacion**: Enero 2026  
**Tipo de documento**: Referencia tecnica interna

---

## Tabla de Contenidos

1. [Vision General del Sistema](#1-vision-general-del-sistema)
2. [Stack Tecnologico](#2-stack-tecnologico)
3. [Estructura de Modulos](#3-estructura-de-modulos)
4. [Modelo de Datos](#4-modelo-de-datos)
5. [Capa de Seguridad y Autenticacion](#5-capa-de-seguridad-y-autenticacion)
6. [Flujo de Datos Completo](#6-flujo-de-datos-completo)
7. [API REST - Endpoints y Contratos](#7-api-rest---endpoints-y-contratos)
8. [Capa del Servidor](#8-capa-del-servidor)
9. [Capa del Cliente](#9-capa-del-cliente)
10. [Migraciones de Base de Datos](#10-migraciones-de-base-de-datos)
11. [Manejo de Errores](#11-manejo-de-errores)
12. [Patrones y Decisiones de Diseno](#12-patrones-y-decisiones-de-diseno)
13. [Observaciones Tecnicas](#13-observaciones-tecnicas)

---

## 1. Vision General del Sistema

Kipu es un sistema de gestion para bares y locales nocturnos que opera bajo una arquitectura cliente-servidor en red local (LAN). El sistema esta diseñado para funcionar sin conexion a internet y optimizado para hardware de gama baja.

### Modelo de Operacion

```
+-------------------+       +-------------------+       +-------------------+
|  Cliente JavaFX   |       |  Cliente JavaFX   |       |  Cliente JavaFX   |
|  (Cajero / POS)   |       |  (Mesero/Comand.) |       |  (Administrador)  |
+---------+---------+       +---------+---------+       +---------+---------+
          |                           |                           |
          |       HTTP REST (JSON)    |                           |
          +---------------------------+---------------------------+
                                      |
                            +---------+---------+
                            |  Servidor Spring  |
                            |  Boot (Puerto     |
                            |      8080)        |
                            +---------+---------+
                                      |
                              +-------+-------+
                              |  PostgreSQL   |
                              |  (kipu_db)   |
                              +---------------+
```

Cada bar opera con un unico servidor local. Multiples clientes (terminales POS, comanderas de meseros, panel administrativo) se conectan al servidor mediante API REST. Los clientes nunca se comunican entre si directamente; toda la informacion fluye exclusivamente a traves del servidor.

### Roles del Sistema

| Rol | Metodo de Autenticacion | Alcance |
|-----|------------------------|---------|
| **ADMIN** | Usuario + Contrasena | Control total: usuarios, productos, categorias, reportes, configuracion |
| **CAJERO** | Codigo (2 digitos) + PIN (4 digitos) | Procesamiento de ventas, cobro, cierre de caja |
| **MESERO** | Codigo (2 digitos) + PIN (4 digitos) | Creacion y gestion de pedidos en mesas asignadas |

---

## 2. Stack Tecnologico

### Servidor (kipu-servidor)

| Tecnologia | Version | Proposito |
|-----------|---------|-----------|
| Java | 21 | Lenguaje base |
| Spring Boot | 3.2.1 | Framework del servidor REST |
| Spring Data JPA | (gestionado por Spring Boot) | Abstraccion de acceso a datos sobre Hibernate |
| Spring Security | (gestionado por Spring Boot) | Autenticacion y autorizacion |
| Spring Validation | (gestionado por Spring Boot) | Validacion de entrada con Bean Validation |
| PostgreSQL | (runtime) | Motor de base de datos relacional |
| HikariCP | (integrado en Spring Boot) | Pool de conexiones a base de datos |
| Flyway | 10.4.1 | Migraciones versionadas de esquema de base de datos |
| jjwt | 0.12.3 | Generacion y validacion de tokens JWT (HMAC-SHA) |
| MapStruct | 1.5.5 | Mapeo compilado entre entidades JPA y DTOs |
| Lombok | 1.18.30 | Reduccion de codigo repetitivo (getters, setters, builders) |
| Logback | (integrado) | Logging estructurado |

### Cliente (kipu-cliente)

| Tecnologia | Version | Proposito |
|-----------|---------|-----------|
| Java | 21 | Lenguaje base |
| JavaFX | 21.0.5 | Framework de interfaz grafica |
| FXML | (parte de JavaFX) | Declaracion de vistas en XML |
| OkHttp | 4.12.0 | Cliente HTTP para consumo de API (servicios legacy) |
| java.net.http.HttpClient | (JDK 21) | Cliente HTTP nativo para consumo de API (servicios nuevos) |
| Jackson | 2.16.1 | Serializacion/deserializacion JSON |
| Logback | 1.4.14 | Logging |
| Lombok | 1.18.30 | Reduccion de codigo repetitivo |

### Modulo Compartido (kipu-common)

| Tecnologia | Version | Proposito |
|-----------|---------|-----------|
| Java | 21 | Lenguaje base |
| Lombok | 1.18.32 | Generacion de codigo |
| Jackson Annotations | 2.16.1 | Anotaciones JSON en DTOs |
| Jakarta Validation API | 3.0.2 | Anotaciones de validacion en DTOs |

### Herramientas de Construccion

| Herramienta | Proposito |
|------------|-----------|
| Maven | Gestion de dependencias y ciclo de vida de compilacion |
| Maven Shade Plugin | Empaquetado del cliente como JAR ejecutable |
| Maven Compiler Plugin | Compilacion con Java 21 |
| JavaFX Maven Plugin | Ejecucion del cliente con configuracion de JVM (G1GC, 512MB-1024MB heap) |

---

## 3. Estructura de Modulos

El proyecto se compone de tres modulos Maven independientes (sin POM padre). El modulo `kipu-common` se instala en el repositorio Maven local y es consumido como dependencia por los otros dos modulos.

```
Kipu/
|
+-- kipu-common/          Modulo compartido: DTOs, constantes, excepciones, enums
|   +-- dto/               Objetos de transferencia de datos
|   +-- constantes/        Constantes del sistema (endpoints, mensajes, validaciones)
|   +-- excepcion/         Excepciones personalizadas con jerarquia
|   +-- enums/             Enumeraciones (Rol, Genero)
|
+-- kipu-servidor/        Modulo servidor: API REST + acceso a datos
|   +-- controlador/       Endpoints REST (@RestController)
|   +-- servicio/          Logica de negocio (interfaces + implementaciones)
|   +-- repositorio/       Acceso a datos (Spring Data JPA)
|   +-- modelo/
|   |   +-- entidad/       Entidades JPA mapeadas a tablas PostgreSQL
|   +-- mapeo/             Mappers MapStruct (entidad <-> DTO)
|   +-- seguridad/         Configuracion de seguridad, filtro JWT, utilidad JWT
|   +-- configuracion/     Configuracion de Spring (CORS, JPA Auditing)
|   +-- excepcion/         Manejador global de excepciones (@ControllerAdvice)
|   +-- resources/
|       +-- db/migration/  Scripts SQL de Flyway (V1, V2, V3)
|       +-- application.yml
|
+-- kipu-cliente/         Modulo cliente: Interfaz grafica JavaFX
|   +-- controlador/       Controladores FXML (ligados a vistas)
|   |   +-- facturacion/   Controladores del modulo de facturacion
|   |   +-- productos/     Controladores del modulo de productos
|   |   +-- meseros/       Controladores del modulo de meseros
|   +-- servicio/          Clientes HTTP que consumen la API REST
|   +-- modelo/            Modelos locales del cliente (Mesa, LineaPedido)
|   +-- componente/        Componentes visuales reutilizables (FondoAnimado, ToggleSwitch)
|   +-- utilidad/          Utilidades (navegacion, alertas, notificaciones, teclado virtual)
|   +-- resources/
|       +-- vista/         Archivos FXML
|       +-- css/           Hojas de estilo
|       +-- imagenes/      Recursos graficos (logo, fondos)
|
+-- database/              Scripts SQL independientes (setup, reset)
+-- scripts/               Scripts de compilacion y ejecucion (bash, PowerShell)
+-- docs/                  Documentacion del proyecto
```

### Dependencia entre Modulos

```
kipu-common (v1.0.0)
    ^               ^
    |               |
kipu-servidor      kipu-cliente
```

Ambos modulos (servidor y cliente) dependen de `kipu-common`. No existe dependencia entre el servidor y el cliente. El modulo comun se instala con `mvn install` para que quede disponible en el repositorio Maven local antes de compilar los otros dos modulos.

---

## 4. Modelo de Datos

### Diagrama de Entidades

```
+------------------+       +------------------+       +------------------+
|   EntidadBase    |       |     Usuario      |       |    Categoria     |
|  (MappedSupercl) |       +------------------+       +------------------+
+------------------+       | idUsuario (PK)   |       | idCategoria (PK) |
| fechaCreacion    |       | nombreUsuario     |       | nombre (unique)  |
| fechaActualizac. |       | contrasena        |       | descripcion      |
| activo           |       | codigo (unique,2) |       | orden            |
+------------------+       | pin               |       +--------+---------+
        ^                  | nombreCompleto    |                |
        |                  | email             |                |
   (Heredan todas          | rol (enum)        |                |
    las entidades)         | genero (enum)     |       +--------+---------+
                           | bloqueado         |       |    Producto      |
                           | intentosFallidos  |       +------------------+
                           +--------+---------+       | idProducto (PK)  |
                                    |                  | codigo (unique)  |
                                    |                  | nombre           |
                                    |                  | descripcion      |
                           +--------+---------+       | precio           |
                           |      Mesa        |       | stockActual      |
                           +------------------+       | stockMinimo      |
                           | idMesa (PK)      |       | requiereStock    |
                           | numeroMesa (uniq)|       | idCategoria (FK) |
                           | estado           |       +--------+---------+
                           | idMesero (FK)    |                |
                           +--------+---------+                |
                                    |                          |
                              OneToOne                         |
                                    |                          |
                           +--------+---------+                |
                           |     Pedido       |                |
                           +------------------+                |
                           | idPedido (PK)    |                |
                           | total            |                |
                           | estado           |                |
                           | fechaCreacion    |                |
                           | idMesa (FK)      |                |
                           +--------+---------+                |
                                    |                          |
                              OneToMany                        |
                                    |                          |
                           +--------+----------+               |
                           |   LineaPedido     |               |
                           +-------------------+               |
                           | idLineaPedido(PK) |               |
                           | nombreProducto    |               |
                           | precioUnitario    |       ManyToOne|
                           | timestamp         +---------------+
                           | idPedido (FK)     |
                           | idProducto (FK)   |
                           +-------------------+

+------------------+
|   MetodoPago     |
+------------------+
| idMetodoPago(PK) |
| nombre (unique)  |
| descripcion      |
| orden            |
| esPredeterminado |
+------------------+
```

### Clase Base: EntidadBase

Todas las entidades del sistema heredan de `EntidadBase`, una clase abstracta anotada con `@MappedSuperclass` que provee tres campos comunes:

- `fechaCreacion` (LocalDateTime): Asignado automaticamente por JPA Auditing (`@CreatedDate`).
- `fechaActualizacion` (LocalDateTime): Actualizado automaticamente por JPA Auditing (`@LastModifiedDate`).
- `activo` (Boolean, default `true`): FLag para eliminacion logica.

JPA Auditing esta habilitado globalmente mediante `@EnableJpaAuditing` en la clase principal del servidor y `@EntityListeners(AuditingEntityListener.class)` en `EntidadBase`.

### Detalle de Entidades

#### Usuario
- **Tabla**: `usuarios`
- **Autenticacion dual**: Los usuarios ADMIN se autentican con `nombreUsuario` + `contrasena` (BCrypt). Los usuarios CAJERO y MESERO se autentican con `codigo` (2 digitos) + `pin` (4 digitos).
- **Seguridad**: El campo `bloqueado` se activa automaticamente tras 3 intentos fallidos de login (`intentosFallidos`).
- **Constraint de base de datos**: `CHECK (rol IN ('MESERO','CAJERO','ADMIN'))` y `CHECK (genero IN ('MASCULINO','FEMENINO'))`.

#### Mesa
- **Tabla**: `mesas`
- **Estados**: DISPONIBLE, OCUPADA, RESERVADA.
- **Relaciones**: `ManyToOne` hacia `Usuario` (el mesero asignado), `OneToOne` con `Pedido` (un pedido activo por mesa, con cascade ALL y orphanRemoval).

#### Pedido
- **Tabla**: `pedidos`
- **Estados**: ACTIVO, FACTURADO, CANCELADO.
- **Modelo de lineas individual**: Cada adicion de un producto genera una linea independiente (no se agrupa por cantidad). Si se agregan 3 unidades del mismo producto, se crean 3 registros en `LineaPedido`.
- **Calculo de total**: El metodo `calcularTotal()` suma los `precioUnitario` de todas las lineas activas.

#### LineaPedido
- **Tabla**: `lineas_pedido`
- **Carga del producto**: La relacion con `Producto` usa `FetchType.EAGER` para disponer del nombre y precio al momento de construir la linea.
- **Desnormalizacion intencional**: Se almacena `nombreProducto` y `precioUnitario` directamente en la linea para preservar el dato historico incluso si el producto cambia de precio o nombre en el futuro.

#### MetodoPago
- **Tabla**: `metodos_pago`
- **Dinamismo**: Los metodos de pago se gestionan desde la interfaz. El metodo EFECTIVO esta marcado como `esPredeterminado = true` y no puede ser eliminado.
- **Orden**: El campo `orden` determina la posicion de visualizacion en la interfaz.

---

## 5. Capa de Seguridad y Autenticacion

### Arquitectura de Seguridad

El sistema utiliza autenticacion stateless basada en JWT (JSON Web Tokens) con Spring Security.

```
+-----------+       +------------------+       +------------------+       +------------------+
|  Cliente  | ----> | JwtFiltroAutent. | ----> | SecurityContext  | ----> |   Controller     |
| (Request  |       | (OncePerRequest) |       | (en memoria)     |       | (@PreAuthorize)  |
|  + Bearer |       +------------------+       +------------------+       +------------------+
|   Token)  |             |
+-----------+             |
                          v
                   +------------------+
                   |     JwtUtil      |
                   | (validarToken,   |
                   |  extraerRol)     |
                   +------------------+
```

### Flujo de Autenticacion

#### Login Administrativo (ADMIN)

```
1. Cliente envia POST /api/usuarios/login
   Body: { "nombreUsuario": "admin", "contrasena": "admin123" }

2. AutenticacionServicioImpl.login():
   a. Busca usuario por nombreUsuario con activo=true
   b. Verifica que no este bloqueado
   c. Valida contrasena con BCryptPasswordEncoder.matches()
   d. Si falla: incrementa intentosFallidos, bloquea al llegar a 3
   e. Si exitoso: resetea intentosFallidos a 0

3. JwtUtil.generarToken(nombreUsuario, rol):
   a. Crea claims con subject=nombreUsuario, "rol"=rol
   b. Firma con HMAC-SHA usando clave secreta de jwt.secret
   c. Establece expiracion a 24 horas

4. Respuesta: AuthRespuestaDto
   { token, nombreUsuario, nombreCompleto, rol, genero, idUsuario }
```

#### Login por PIN (CAJERO, MESERO)

```
1. Cliente envia GET /api/usuarios/verificar-codigo/{codigo}
   Respuesta: { existe, nombreCompleto, activo }

2. Si existe y esta activo, el cliente muestra formulario de PIN

3. Cliente envia POST /api/usuarios/login-pin
   Body: { "codigo": "01", "pin": "1234" }

4. UsuarioServicioImpl.loginConPin():
   a. Busca usuario por codigo con activo=true
   b. Valida PIN con BCryptPasswordEncoder.matches()
   c. Genera JWT igual que en login administrativo

5. Respuesta: AuthRespuestaDto (misma estructura)
```

### Filtro JWT (JwtFiltroAutenticacion)

Cada request HTTP pasa por este filtro que extiende `OncePerRequestFilter`:

1. Extrae el header `Authorization` y verifica que comience con `Bearer `.
2. Delega a `JwtUtil.validarToken()` para verificar firma y expiracion.
3. Extrae `nombreUsuario` y `rol` del token.
4. Crea un `UsernamePasswordAuthenticationToken` con authority `ROLE_` + rol.
5. Establece la autenticacion en el `SecurityContextHolder`.

Si el token es invalido o no esta presente, el request continua sin autenticacion y Spring Security maneja el rechazo (401/403).

### Configuracion de Rutas

Definida en `ConfiguracionSeguridad`:

| Ruta | Acceso |
|------|--------|
| `/api/usuarios/login` | Publico |
| `/api/usuarios/login-pin` | Publico |
| `/api/usuarios/verificar-codigo/**` | Publico |
| `/api/admin/**` | Solo ADMIN |
| `/api/cajero/**` | CAJERO, ADMIN |
| `/api/mesero/**` | MESERO, CAJERO, ADMIN |
| Resto de rutas | Autenticado (cualquier rol) |

Adicionalmente, cada endpoint tiene anotaciones `@PreAuthorize` a nivel de metodo para control granular. Esto esta habilitado mediante `@EnableMethodSecurity` en la configuracion de seguridad.

### CORS

Configurado en `ConfiguracionCors` como WebMvcConfigurer:
- Origenes: `*` (todos, para permitir cualquier cliente en la LAN).
- Metodos: GET, POST, PUT, DELETE, OPTIONS.
- Headers: Todos permitidos.
- Credenciales: habilitadas.
- Aplica a `/api/**`.

---

## 6. Flujo de Datos Completo

### Flujo General: Solicitud del Cliente al Servidor y Regreso

```
+------------------+     HTTP Request      +------------------+
|                  | --------------------> |                  |
|   CLIENTE        |   (JSON + JWT)        |   SERVIDOR       |
|   JavaFX         |                       |   Spring Boot    |
|                  | <-------------------- |                  |
+------------------+     HTTP Response     +------------------+
                         (RespuestaApi)
```

#### Ida (Request)

```
[Controlador FXML]
     |
     | Invoca metodo del servicio cliente
     v
[Servicio Cliente] (ej: CategoriaServicio, MesaServicio)
     |
     | 1. Construye URL: ConfiguracionCliente.getUrlServidor() + Constantes.Endpoints.XXX
     | 2. Agrega header Authorization: Bearer <token>
     | 3. Serializa body con Jackson ObjectMapper
     | 4. Envia HTTP request (OkHttp o java.net.http.HttpClient)
     v
[Red LAN]
     |
     v
[JwtFiltroAutenticacion]
     |
     | Valida token JWT, establece SecurityContext
     v
[Controller REST] (ej: @RestController @RequestMapping("/api/categorias"))
     |
     | @PreAuthorize verifica rol del usuario
     | @Valid valida el DTO de entrada
     v
[Servicio del Servidor] (ej: CategoriaServicioImpl)
     |
     | Ejecuta logica de negocio
     | Aplica validaciones (nombre unico, precio > 0, etc.)
     v
[Repositorio JPA] (ej: CategoriaRepositorio extends JpaRepository)
     |
     | Spring Data JPA genera la query SQL automaticamente
     | o ejecuta @Query JPQL personalizada
     v
[PostgreSQL]
```

#### Vuelta (Response)

```
[PostgreSQL]
     |
     | Retorna filas como entidades JPA (via Hibernate)
     v
[Repositorio JPA]
     |
     | Retorna entidad(es) JPA al servicio
     v
[Servicio del Servidor]
     |
     | Convierte entidad a DTO usando MapStruct
     | ej: categoriaMapper.aDto(entidad) -> CategoriaDto
     v
[Controller REST]
     |
     | Envuelve en RespuestaApi<T>:
     |   { exito: true, datos: <DTO>, mensaje: "..." }
     | Retorna ResponseEntity con codigo HTTP apropiado
     v
[Red LAN]
     |
     v
[Servicio Cliente]
     |
     | Deserializa JSON con Jackson
     | Extrae 'datos' del RespuestaApi
     | Maneja errores (IOException, status != 200)
     v
[Controlador FXML]
     |
     | Actualiza la interfaz en el hilo JavaFX
     | Platform.runLater() para operaciones asincronas
     v
[Vista FXML / Interfaz de Usuario]
```

### Contrato de Respuesta Estandar

Toda respuesta del servidor sigue la estructura `RespuestaApi<T>`:

**Respuesta exitosa:**
```json
{
  "exito": true,
  "datos": { ... },
  "mensaje": "Operacion realizada exitosamente"
}
```

**Respuesta de error:**
```json
{
  "exito": false,
  "error": "RECURSO_NO_ENCONTRADO",
  "mensaje": "El recurso solicitado no fue encontrado",
  "detalles": { ... }
}
```

En el cliente, la clase `RespuestaApi<T>` provee metodos de fabricacion estaticos: `exitosa(datos, mensaje)` y `error(error, mensaje)`.

---

## 7. API REST - Endpoints y Contratos

### Autenticacion

| Metodo | Ruta | Acceso | Entrada | Salida |
|--------|------|--------|---------|--------|
| POST | `/api/usuarios/login` | Publico | `LoginDto` | `RespuestaApi<AuthRespuestaDto>` |
| POST | `/api/usuarios/login-pin` | Publico | `LoginPinDto` | `AuthRespuestaDto` |
| GET | `/api/usuarios/verificar-codigo/{codigo}` | Publico | Path variable | `RespuestaApi<VerificarCodigoDto>` |
| GET | `/api/usuarios/health` | Publico | - | `RespuestaApi<String>` |

### Usuarios

| Metodo | Ruta | Acceso | Entrada | Salida |
|--------|------|--------|---------|--------|
| GET | `/api/usuarios` | ADMIN, CAJERO | - | `RespuestaApi<List<UsuarioDto>>` |
| GET | `/api/usuarios/{id}` | ADMIN | Path variable | `RespuestaApi<UsuarioDto>` |
| POST | `/api/usuarios` | ADMIN | `UsuarioDto` | `RespuestaApi<UsuarioDto>` |
| PUT | `/api/usuarios/{id}` | ADMIN | `UsuarioDto` | `RespuestaApi<UsuarioDto>` |
| DELETE | `/api/usuarios/{id}` | ADMIN | Path variable | `RespuestaApi<Void>` |

### Categorias

| Metodo | Ruta | Acceso | Entrada | Salida |
|--------|------|--------|---------|--------|
| GET | `/api/categorias` | MESERO, CAJERO, ADMIN | - | `RespuestaApi<List<CategoriaDto>>` |
| GET | `/api/categorias/{id}` | MESERO, CAJERO, ADMIN | Path variable | `RespuestaApi<CategoriaDto>` |
| POST | `/api/categorias` | ADMIN | `CategoriaDto` | `RespuestaApi<CategoriaDto>` |
| PUT | `/api/categorias/{id}` | ADMIN | `CategoriaDto` | `RespuestaApi<CategoriaDto>` |
| DELETE | `/api/categorias/{id}` | ADMIN | Path variable | `RespuestaApi<Void>` |

### Productos

| Metodo | Ruta | Acceso | Entrada | Salida |
|--------|------|--------|---------|--------|
| GET | `/api/productos` | MESERO, CAJERO, ADMIN | - | `RespuestaApi<List<ProductoDto>>` |
| GET | `/api/productos/{id}` | MESERO, CAJERO, ADMIN | Path variable | `RespuestaApi<ProductoDto>` |
| GET | `/api/productos/categoria/{id}` | MESERO, CAJERO, ADMIN | Path variable | `RespuestaApi<List<ProductoDto>>` |
| POST | `/api/productos` | ADMIN | `ProductoDto` | `RespuestaApi<ProductoDto>` |
| PUT | `/api/productos/{id}` | ADMIN | `ProductoDto` | `RespuestaApi<ProductoDto>` |
| DELETE | `/api/productos/{id}` | ADMIN | Path variable | `RespuestaApi<Void>` |

### Mesas y Pedidos

| Metodo | Ruta | Acceso | Entrada | Salida |
|--------|------|--------|---------|--------|
| GET | `/api/mesas/{id}/pedido` | MESERO, CAJERO, ADMIN | Path variable | `RespuestaApi<MesaConPedidoDto>` |
| POST | `/api/mesas/{numMesa}/pedido?idMesero` | MESERO, CAJERO, ADMIN | Path + Query + `PedidoDto` | `RespuestaApi<MesaConPedidoDto>` |
| GET | `/api/mesas/activas?idMesero` | MESERO, CAJERO, ADMIN | Query param (opcional) | `RespuestaApi<List<MesaActivaDto>>` |
| POST | `/api/mesas` | MESERO, CAJERO, ADMIN | `CreacionMesaDto` | `RespuestaApi<MesaConPedidoDto>` |
| GET | `/api/mesas/numero/{num}` | MESERO, CAJERO, ADMIN | Path variable | `RespuestaApi<MesaConPedidoDto>` |
| DELETE | `/api/mesas/{id}` | MESERO, CAJERO, ADMIN | Path variable | `RespuestaApi<Void>` |

### Metodos de Pago

| Metodo | Ruta | Acceso | Entrada | Salida |
|--------|------|--------|---------|--------|
| GET | `/api/metodos-pago` | MESERO, CAJERO, ADMIN | - | `RespuestaApi<List<MetodoPagoDto>>` |
| POST | `/api/metodos-pago` | ADMIN | `MetodoPagoDto` | `RespuestaApi<MetodoPagoDto>` |
| DELETE | `/api/metodos-pago/{id}` | ADMIN | Path variable | `RespuestaApi<Void>` |

---

## 8. Capa del Servidor

### Arquitectura en Capas

El servidor sigue una arquitectura de tres capas estricta. Las dependencias fluyen en una sola direccion:

```
Controller --> Service (Interface) --> Repository --> Database
                  |
              ServiceImpl
                  |
              Mapper (MapStruct)
```

### Controllers (Controladores REST)

Responsabilidades:
- Recibir requests HTTP y deserializar el body JSON en DTOs.
- Delegar la logica al servicio correspondiente.
- Envolver la respuesta en `RespuestaApi<T>` con el codigo HTTP apropiado.
- No contienen logica de negocio.

Cada controller esta anotado con `@RestController` y `@RequestMapping` para definir la ruta base. Los metodos individuales usan `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` con `@PreAuthorize` para control de acceso.

### Services (Servicios de Negocio)

Cada servicio se define como una interfaz y una implementacion:
- **Interfaz**: Define el contrato de metodos (ej: `CategoriaServicio`).
- **Implementacion**: Contiene toda la logica de negocio, validaciones y transformaciones (ej: `CategoriaServicioImpl`).

La implementacion esta anotada con `@Service` y `@Transactional` para gestion automatica de transacciones. Las validaciones incluyen:
- Unicidad de nombres (categorias, productos, metodos de pago).
- Integridad de datos (precio mayor a cero, categoria obligatoria para productos).
- Reglas de negocio (no eliminar metodo de pago predeterminado, bloqueo tras 3 intentos fallidos).

### Repositories (Repositorios JPA)

Extienden `JpaRepository<Entidad, Long>` de Spring Data JPA. Se definen metodos personalizados mediante:
- **Derived Query Methods**: Spring genera la query SQL automaticamente a partir del nombre del metodo (ej: `findByActivoTrueOrderByOrdenAsc()`).
- **JPQL Queries**: Para consultas complejas, se usa `@Query` con JPQL (ej: `findMesasActivas` con LEFT JOIN FETCH y filtro condicional por mesero).

### Mappers (MapStruct)

Cada mapper es una interfaz anotada con `@Mapper(componentModel = "spring")` que MapStruct implementa en tiempo de compilacion. Esto genera clases concretas con mappings tipo-seguro y eficientes, sin uso de reflexion en runtime.

Configuraciones comunes:
- Los campos de auditoria (`fechaCreacion`, `fechaActualizacion`) se ignoran al convertir DTO a entidad.
- Los campos sensibles (contrasena, PIN, bloqueado) se ignoran en ciertos mappings de Usuario.
- Las relaciones complejas (ej: `categoria.nombre` -> `nombreCategoria` en ProductoDto) se definen con `@Mapping(source, target)`.

### Patron de Mapeo de Datos

```
[Entidad JPA]                     [DTO]
     |                               ^
     |   mapper.aDto(entidad)        |
     +------------------------------>+
     |                               |
     +<------------------------------+
     |   mapper.aEntidad(dto)        |
     |                               |

Metodos estandar por mapper:
  - aDto(Entidad)           -> DTO
  - aEntidad(DTO)           -> Entidad
  - aListaDto(List<Entidad>) -> List<DTO>
```

### Pool de Conexiones (HikariCP)

Configurado en `application.yml`:
- `maximum-pool-size`: 10 conexiones
- `minimum-idle`: 2 conexiones inactivas
- `connection-timeout`: 30 segundos
- `idle-timeout`: 10 minutos
- `max-lifetime`: 30 minutos

Estos valores estan dimensionados para operacion en LAN con hardware limitado.

---

## 9. Capa del Cliente

### Punto de Entrada

La clase `KipuClienteApplication` extiende `javafx.application.Application`. Al iniciar:
1. Carga la vista `login-pin.fxml` como escena inicial.
2. Aplica la hoja de estilos `estilos.css`.
3. Configura la ventana en pantalla completa con la tecla ESC deshabilitada para salida (se reasigna a navegacion).
4. Delega el manejo de la tecla ESC a `NavegacionUtil`.

### Arquitectura de Navegacion

La navegacion del cliente se gestiona centralizadamente mediante `NavegacionUtil`, una clase de utilidades estaticas que provee:

- **Sesion de usuario**: Almacena el `AuthRespuestaDto` del usuario autenticado en memoria.
- **Cambio de escena**: Carga un nuevo FXML, configura la ventana en pantalla completa y actualiza el titulo.
- **Historial de vistas**: Mantiene un stack de breadcrumbs para navegacion regresiva.
- **Limpieza de recursos**: Recorre el scene graph recursivamente para liberar listeners, timelines y recursos asociados a la escena anterior.
- **Manejo de ESC**: Permite registrar handlers personalizados por vista que se ejecutan al presionar ESC.

#### Flujo de Navegacion

```
Login PIN/Admin
     |
     | (autenticacion exitosa)
     v
Menu Principal (MenuPrincipalController)
     |
     | Carga subvistas en StackPane segun Vista enum:
     |   INICIO, FACTURACION, USUARIOS, PRODUCTOS, etc.
     |
     +---> Facturacion
     |       +---> Mesas Activas
     |       +---> Mesa Detalle (pedido, pago)
     |       +---> Seleccion de Mesero
     |
     +---> Usuarios (CRUD completo)
     |
     +---> Productos
             +---> Categoria Crear
             +---> Categoria Listado
             +---> Producto Crear
             +---> Producto Listado
```

El `MenuPrincipalController` utiliza un enum `Vista` y un `StackPane` central. Al seleccionar una opcion del menu, carga dinamicamente el FXML correspondiente usando `FXMLLoader`, lo agrega al StackPane y actualiza los breadcrumbs.

### Servicios Cliente (Consumo de API)

ºLos servicios del cliente encapsulan toda la comunicacion HTTP con el servidor. Todos utilizan una unica implementacion basada en `java.net.http.HttpClient` (nativo del JDK 21) con operaciones **completamente asincronas**.

#### Arquitectura Unificada: java.net.http.HttpClient

Todos los servicios (`AutenticacionServicio`, `UsuarioServicio`, `CategoriaServicio`, `ProductoServicio`, `MesaServicio`, `MetodoPagoServicio`) comparten las siguientes caracteristicas:

- Usan `HttpClient` nativo del JDK con `connectTimeout` de 10 segundos.
- Retornan `CompletableFuture<T>` para **todas** las operaciones HTTP.
- Construyen URLs dinamicamente mediante `ConfiguracionCliente.getUrlServidor()` + `Constantes.Endpoints.*`.
- Deserializan la respuesta usando `ObjectMapper` con `JavaTimeModule` y `TypeReference` para genericos.
- Manejan el token JWT obtenido de `ConfiguracionCliente.getTokenJwt()` en cada request.
- Los controladores consumen los futuros con `.thenAcceptAsync(callback, Platform::runLater)` para actualizar la UI en el hilo de JavaFX.

**Nota**: La clase legacy `ClienteApiBase` (OkHttp) fue eliminada. Ya no existe herencia ni dependencia de OkHttp en el cliente.

Ejemplo simplificado del flujo asincrono:

```
Controlador FXML llama servicio.listarCategoriasAsync()
     |
     v
HttpClient.sendAsync(request, BodyHandlers.ofString())
     |
     | (hilo del HttpClient)
     v
Parsea JSON -> RespuestaApi<List<CategoriaDto>>
     |
     | CompletableFuture.thenAccept()
     v
Platform.runLater(() -> {
    // Actualiza la interfaz en el hilo de JavaFX
    gridCategorias.getChildren().setAll(...)
})
```

### Controladores FXML

Cada controlador FXML implementa la logica de una vista. Se conecta al archivo FXML mediante la convencion de `fx:controller` y los campos `@FXML`. Los controladores siguen este patron:

1. **initialize()**: Metodo invocado automaticamente tras la carga del FXML. Aqui se:
   - Carga la informacion del usuario actual desde `NavegacionUtil.getUsuarioActual()`.
   - Inicializa el reloj del footer (Timeline con ciclo de 1 segundo).
   - Configura listeners y bindings.
   - Dispara la carga inicial de datos desde el servidor.

2. **Metodos de accion** (anotados con `@FXML`): Vinculados a eventos de la interfaz (clicks de botones, seleccion de items).

3. **Metodos auxiliares privados**: Logica de transformacion, validacion local, construccion dinamica de nodos.

### Componentes Visuales Reutilizables

| Componente | Funcion |
|-----------|---------|
| **FondoAnimado** | Canvas con 90 particulas doradas animadas a 30 FPS. Reaccionan al mouse en un radio de 150px. Se pausa automaticamente cuando no esta visible. |
| **ToggleSwitch** | Interruptor personalizado con animacion de transicion de 200ms. Estados: apagado (#2a2a2a) y encendido (#d4af37). |
| **LineaDivisoriaInteractiva** | Linea divisoria que brilla con tono dorado cuando el mouse se acerca a 120px de distancia. |

### Utilidades del Cliente

| Utilidad | Proposito |
|---------|-----------|
| **NavegacionUtil** | Gestion centralizada de navegacion, sesion, historial y limpieza de escenas. |
| **AlertaUtil** | Dialogos modales estandar (error, informacion, advertencia, exito, confirmacion) con estilos del tema. |
| **NotificacionUtil** | Notificaciones tipo toast no bloqueantes con animacion fade-in + slide-down, auto-cierre tras 3 segundos. |
| **TecladoVirtualSimple** | Teclado QWERTY programatico de 650px de ancho. Arrastrable. Se muestra automaticamente al enfocar campos de texto. |

---

## 10. Migraciones de Base de Datos

Las migraciones se gestionan con Flyway y se ejecutan automaticamente al iniciar el servidor. Estan ubicadas en `kipu-servidor/src/main/resources/db/migration/`.

### V1__crear_tablas_base.sql

Crea la estructura fundamental de 7 tablas:

| Tabla | Columnas Principales | Constraints |
|-------|---------------------|-------------|
| `usuarios` | id_usuario, nombre_usuario, contrasena, codigo, pin, nombre_completo, email, rol, genero, bloqueado, intentos_fallidos, activo, fecha_creacion, fecha_actualizacion | PK, UNIQUE(nombre_usuario, codigo), CHECK(rol), CHECK(genero) |
| `categorias` | id_categoria, nombre, descripcion, orden, activo, fecha_creacion, fecha_actualizacion | PK, UNIQUE(nombre) |
| `productos` | id_producto, codigo, nombre, descripcion, precio, stock_actual, stock_minimo, requiere_stock, id_categoria, activo, fecha_creacion, fecha_actualizacion | PK, UNIQUE(codigo), FK(categorias) |
| `mesas` | id_mesa, numero_mesa, estado, id_mesero, activo, fecha_creacion, fecha_actualizacion | PK, UNIQUE(numero_mesa), FK(usuarios), CHECK(estado) |
| `pedidos` | id_pedido, id_mesa, total, estado, activo, fecha_creacion, fecha_actualizacion | PK, UNIQUE(id_mesa), FK(mesas) CASCADE |
| `lineas_pedido` | id_linea_pedido, id_pedido, id_producto, nombre_producto, precio_unitario, timestamp, activo, fecha_creacion, fecha_actualizacion | PK, FK(pedidos) CASCADE, FK(productos) |
| `cajas` | (estructura para cierres de caja) | Reservada para implementacion futura |

Incluye indices en columnas de clave foranea y campos de consulta frecuente.

### V2__datos_iniciales.sql

Inserta datos semilla para operacion inmediata:
- 1 usuario ADMIN (admin / admin123).
- 1 usuario CAJERO (codigo 01).
- 8 usuarios MESERO (codigos 02 a 09).
- 8 categorias de productos (AGUARDIENTES, TEQUILAS, WHISKYS, PASANTES, COCTELES, CERVEZAS, BEBIDAS SIN ALCOHOL, COMIDAS).
- 40 productos (5 por categoria) con precios y stock iniciales.

Las contrasenas y PINs estan almacenados como hashes BCrypt.

### V3__crear_metodos_pago.sql

Crea la tabla `metodos_pago` e inserta 6 metodos iniciales:
- EFECTIVO (predeterminado, no eliminable).
- DEBITO, CREDITO, TRANSFERENCIA, QR, MIXTO.

---

## 11. Manejo de Errores

### Jerarquia de Excepciones

Definida en `kipu-common/excepcion/`:

```
RuntimeException
  +-- KipuException (abstracta)
        +-- RecursoNoEncontradoException  (codigo: RECURSO_NO_ENCONTRADO)
        +-- ValidacionException           (codigo: VALIDACION_ERROR)
        +-- AutenticacionException        (codigo: personalizado)
        +-- ConexionException             (codigo: ERROR_CONEXION)
```

Todas las excepciones llevan un `codigoError` estandarizado definido en `Constantes.CodigosError`. Algunas proveen metodos de fabricacion estaticos para casos comunes (ej: `RecursoNoEncontradoException.porId("Producto", 42)`).

### Manejador Global (Servidor)

La clase `ManejadorGlobalExcepciones` anotada con `@RestControllerAdvice` intercepta todas las excepciones no capturadas y las transforma en respuestas HTTP estandarizadas:

| Excepcion | Codigo HTTP | Respuesta |
|----------|-------------|-----------|
| `RecursoNoEncontradoException` | 404 Not Found | `RespuestaApi.error(codigoError, mensaje)` |
| `ValidacionException` | 400 Bad Request | `RespuestaApi.error(codigoError, mensaje)` |
| `MethodArgumentNotValidException` | 400 Bad Request | `RespuestaApi.error("VALIDACION_ERROR", errores por campo)` |
| `BadCredentialsException` | 401 Unauthorized | `RespuestaApi.error("CREDENCIALES_INVALIDAS", mensaje)` |
| `Exception` (generica) | 500 Internal Server Error | `RespuestaApi.error("ERROR_INTERNO", mensaje generico)` |

### Manejo de Errores en el Cliente

Los servicios del cliente manejan errores a dos niveles:

1. **Errores HTTP**: Verifican el codigo de respuesta. Si no es 2xx, extraen el mensaje de error del body JSON.
2. **Errores de conexion**: Capturan `IOException`, `ConnectException` y similares para mostrar mensajes de conectividad al usuario.

Los controladores FXML utilizan `AlertaUtil` para mostrar dialogos modales de error y `NotificacionUtil` para notificaciones no invasivas tipo toast.

---

## 12. Patrones y Decisiones de Diseno

### Patrones Implementados

| Patron | Ubicacion | Uso |
|--------|----------|-----|
| **Repository** | `kipu-servidor/repositorio/` | Abstraccion de acceso a datos mediante Spring Data JPA |
| **Service Layer** | `kipu-servidor/servicio/` | Separacion de logica de negocio en interfaces e implementaciones |
| **DTO** | `kipu-common/dto/` | Objetos de transferencia que desacoplan la capa de presentacion del modelo de datos |
| **Mapper** | `kipu-servidor/mapeo/` | Conversion compilada entre entidades y DTOs via MapStruct |
| **Builder** | Todas las entidades y DTOs | Construccion fluida de objetos complejos via Lombok `@Builder`/`@SuperBuilder` |
| **Filter Chain** | `seguridad/JwtFiltroAutenticacion` | Procesamiento de seguridad en cadena antes de alcanzar el controller |
| **Template Method** | Servicios HTTP del cliente | Construccion uniforme de requests HTTP con `HttpClient` nativo |
| **Observer** | Componentes JavaFX | Properties observables, listeners de foco, bindings |
| **Enum Strategy** | `Vista` en MenuPrincipalController | Enum que define las subvistas y su metadata para carga dinamica |
| **Factory Method** | `RespuestaApi`, excepciones | Metodos estaticos de creacion para respuestas y excepciones |

### Eliminacion Logica vs. Fisica

El sistema implementa dos estrategias de eliminacion:

- **Eliminacion logica** (campo `activo = false`): Categorias, Productos, Metodos de Pago. Los registros permanecen en la base de datos pero no aparecen en consultas de listado.
- **Eliminacion fisica** (DELETE SQL): Usuarios, Mesas, Pedidos. Los registros se eliminan permanentemente de la base de datos.

### Modelo de Concurrencia

- El servidor utiliza el modelo de hilos de Spring Boot (Tomcat embedded) con un pool de hilos para atender requests concurrentes.
- Las transacciones JPA (`@Transactional`) garantizan consistencia en operaciones de escritura.
- HikariCP gestiona el pool de conexiones a la base de datos, evitando la creacion y destruccion de conexiones en cada request.
- En el cliente, las operaciones asincronas (`CompletableFuture`) se ejecutan en hilos del HttpClient. La actualizacion de la interfaz se despacha al hilo de JavaFX mediante `Platform.runLater()`.

### Configuracion Externalizada

La configuracion del servidor utiliza `application.yml` con soporte para variables de entorno:

| Propiedad | Variable de Entorno | Default |
|----------|--------------------|---------| 
| Datasource URL | `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost:5432/kipu_db` |
| Datasource User | `DB_USER` | `kipu_admin` |
| Datasource Password | `DB_PASSWORD` | (configurado) |
| Server Port | `SERVER_PORT` | `8080` |
| JWT Secret | `JWT_SECRET` | (clave por defecto para desarrollo) |

El perfil `application-dev.yml` habilita SQL visible, formato de queries, DevTools (restart + livereload) y logging a nivel DEBUG para los paquetes de Spring y Kipu.

---

## 13. Observaciones Tecnicas

### Inconsistencias Conocidas

1. ~~**Clientes HTTP mixtos**~~: **RESUELTO** — Todos los servicios del cliente ahora utilizan `java.net.http.HttpClient` con operaciones asincronas (`CompletableFuture`). La clase legacy `ClienteApiBase` (OkHttp) fue eliminada.

2. ~~**URLs parcialmente hardcodeadas**~~: **RESUELTO** — Todos los servicios construyen URLs mediante `ConfiguracionCliente.getUrlServidor()` + constantes de endpoint. No hay URLs hardcodeadas en el codigo.

3. **Estrategia de eliminacion no uniforme**: Categorias y Productos usan eliminacion logica; Usuarios y Mesas usan eliminacion fisica. Esto es intencional: los datos transaccionales (productos, categorias) se preservan para integridad referencial, mientras que los datos operativos (mesas temporales) se eliminan fisicamente.

### Modulos Pendientes

Los siguientes modulos estan referenciados en la estructura pero no tienen implementacion completa:
- **Pedidos**: Directorio de controladores vacio (la logica actual reside en `MesaServicio`).
- **Reportes**: Directorio de controladores vacio.
- **Caja**: La tabla `cajas` existe en V1 pero no tiene entidad, repositorio ni servicio asociado.

### Rendimiento

- La JVM del cliente esta configurada con G1GC, heap de 512MB a 1024MB y pausa maxima de GC de 50ms para mantener fluidez en la interfaz.
- Las animaciones de particulas (`FondoAnimado`) operan a 30 FPS con pausado inteligente cuando el componente no esta visible.
- Las consultas JPA con relaciones usan `LEFT JOIN FETCH` para evitar el problema N+1 en queries que necesitan datos relacionados.
- El JPA `open-in-view` esta deshabilitado (`false`) para evitar lazy loading accidental fuera de transacciones.

---

*Documento generado como referencia tecnica del sistema Kipu v1.0.0.*
