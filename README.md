# Kipu — Software POS
Sistema POS (Point of Sale) moderno para gestión integral de bares, restaurantes y locales. Operación 100% en red local (LAN), sin dependencia de internet. Interfaz táctil con estética dorado + negro.
> **Licencia**: Source-available (basada en Elastic License 2.0). Ver [LICENSE](LICENSE).  
> **Titular**: Dilan Acuña / Kipu, 2026.
---

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Stack Tecnológico](#stack-tecnológico)
- [Arquitectura del Sistema](#arquitectura-del-sistema)
- [Módulos Implementados](#módulos-implementados)
- [Modelo de Datos](#modelo-de-datos)
- [Seguridad](#seguridad)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Módulos Pendientes](#módulos-pendientes)
- [Instalación y Uso](#instalación-y-uso)

---

## Descripción General

Kipu es un sistema de punto de venta diseñado para la industria de alimentos y bebidas, con énfasis en bares y locales nocturnos. Implementa una arquitectura cliente-servidor que permite múltiples terminales conectadas a un servidor central en LAN.

### Características Principales

- **Login dual**: Administrador (usuario/contraseña) y empleados (código/PIN numérico)
- **Gestión completa de mesas y pedidos** con modelo hard-delete (mesas solo existen en BD si tienen productos)
- **Catálogo de productos** organizado por categorías con drag-and-drop para reordenar
- **Interfaz POS completa** con grid de productos, carrito y panel de pago
- **Métodos de pago dinámicos** configurables por el administrador
- **Gestión de usuarios** con roles (MESERO, CAJERO, ADMIN)
- **Monitor de conexión** al servidor en tiempo real
- **Teclado virtual** QWERTY y numérico para pantallas táctiles
- **Servidor embebido** (el cliente puede levantar el servidor automáticamente)
- **Diseño luxury** dorado + negro optimizado para ambientes nocturnos

### Principios de Diseño

- **SOLID** estrictamente
- **Separación de responsabilidades**: Capas Controller → Service → Repository
- **DTOs**: Transferencia entre capas, nunca se exponen entidades directamente
- **MapStruct**: Mapeo objeto-objeto sin boilerplate
- **Validación en múltiples capas**: Cliente + servidor
- **Todo en español**: Variables, clases, métodos, BD, comentarios, mensajes

---

## Stack Tecnológico

|       Capa       |           Tecnología         | Versión |
|------------------|------------------------------|---------|
| **Lenguaje**     | Java                         |    21   |
| **Frontend**     | JavaFX + FXML + CSS          |  21.0.5 |
| **Backend**      | Spring Boot                  |  3.2.1  |
| **Seguridad**    | Spring Security + JWT (jjwt) |  0.12.3 |
| **BD**           | PostgreSQL                   |   15+   |
| **ORM**          | JPA / Hibernate (validate)   |    —    |
| **Migraciones**  | Flyway                       |  9.22.3 |
| **Mapeo DTOs**   | MapStruct                    |  1.5.5  |
| **HTTP cliente** | java.net.http.HttpClient     |    —    |
| **Build**        | Maven (multi-módulo)         |    —    |
| **Logging**      | SLF4J + Logback              |    —    |
| **Boilerplate**  | Lombok                       | 1.18.30 |
| **JSON**         | Jackson                      | 2.16.0  |

---

## Arquitectura del Sistema

### Modelo Cliente-Servidor

```
┌─────────────────┐       HTTP/JSON        ┌──────────────────┐       JDBC       ┌────────────┐
│  kipu-cliente  │ ◄────────────────────► │  kipu-servidor  │ ◄──────────────► │ PostgreSQL │
│  (JavaFX 21)    │      REST API          │  (Spring Boot)   │                  │  kipu_db  │
│  N instancias   │       + JWT            │  1 por bar       │     Flyway       │  1 por bar │
└─────────────────┘                        └──────────────────┘                  └────────────┘
         │                                          │                                   |
         └──────────── kipu-common (DTOs, excepciones, constantes, utilidades) ────────┘
```

- **Protocolo**: HTTP (LAN), JSON
- **Autenticación**: JWT en header `Authorization: Bearer <token>`
- **Sesión**: Stateless (sin estado en servidor)
- **1 servidor por establecimiento**, N clientes conectados simultáneamente

### Arquitectura Modular de Subvistas (Cliente)

El menú principal carga subvistas dinámicamente en un `StackPane` central:

```
MenuPrincipalController (contenedor con sidebar + header + footer)
    │
    ├── Módulo Productos (ProductosController)
    │   ├── Listado de productos (ProductoListadoController)
    │   ├── Crear/editar producto (ProductoCrearController)
    │   ├── Listado de categorías (CategoriaListadoController) — con drag-and-drop
    │   └── Crear categoría (CategoriaCrearController)
    │
    ├── Módulo Facturación (FacturacionController)
    │   ├── Selección de mesero (SeleccionMeseroController)
    │   ├── Mesas activas (MesasActivasController)
    │   └── Detalle de mesa / POS (MesaDetalleController) — 2,778 líneas
    │
    ├── Módulo Meseros (MeserosController)
    │   └── Grid de tarjetas de meseros
    │
    └── Módulo Usuarios (UsuariosController)
        └── CRUD con tabla + modal crear/editar
```

---

## Módulos Implementados

### 1. Autenticación (Login Dual)

**Flujo**:
1. Login administrador: usuario + contraseña → JWT
2. Login empleado: código (2 dígitos) + PIN (4 dígitos) → JWT
3. Verificación de código previo al PIN
4. Bloqueo tras intentos fallidos

| Capa      |             Archivo                |                      Descripción                           |
|-----------|------------------------------------|------------------------------------------------------------|
| Cliente   | `LoginPinController.java`(937 lín.)| Vista dual: ToggleSwitch, FondoAnimado, teclados virtuales |
| Cliente   | `AutenticacionServicio.java`       | HTTP client para login/PIN/verificación                    |
| Servidor  | `AutenticacionController.java`     | `POST /api/usuarios/login` (público)                       |
| Servidor  | `AutenticacionServicioImpl.java`   | Validación BCrypt, generación JWT, control de bloqueo      |
| Seguridad | `JwtUtil.java`                     | Generación/validación HS256, claims (user, rol, id)        |
| Seguridad | `JwtFiltroAutenticacion.java`      | Filtro que extrae JWT del header → SecurityContext         |
| Seguridad | `ConfiguracionSeguridad.java`      | Filter chain, BCrypt encoder, sesiones stateless           |

### 2. Gestión de Usuarios

CRUD completo con tabla + modal crear/editar. Roles: MESERO, CAJERO, ADMIN.

|   Capa   |            Archivo                  |                   Descripción                    |
|----------|-------------------------------------|--------------------------------------------------|
| Cliente  | `UsuariosController.java`(932 lín.) | Vista CRUD con tabla, formulario modal, búsqueda |
| Cliente  | `UsuarioServicio.java`              | HTTP client async (`CompletableFuture`)          |
| Servidor | `UsuarioController.java` (268 lín.) | Endpoints CRUD + login PIN + verificar código    |
| Servidor | `UsuarioServicioImpl.java`(378 lín.)| Lógica con BCrypt para contraseña y PIN          |
| Servidor | `UsuarioMapper.java`                | MapStruct (ignora campos sensibles)              |

### 3. Productos y Categorías

Catálogo organizado por categorías con drag-and-drop para reordenar.

|   Capa   |             Archivo               |                    Descripción                   |
|----------|-----------------------------------|--------------------------------------------------|
| Cliente  | `ProductosController.java`        | Contenedor del módulo productos                  |
| Cliente  | `ProductoListadoController.java`  | Grid de tarjetas con filtros                     |
| Cliente  | `ProductoCrearController.java`    | Formulario de creación/edición                   |
| Cliente  | `CategoriaListadoController.java` | Listado con drag-and-drop                        |
| Cliente  | `CategoriaCrearController.java`   | Formulario de categorías                         |
| Servidor | `ProductoController.java`         | CRUD REST (`@PreAuthorize` ADMIN para mutaciones)|
| Servidor | `CategoriaController.java`        | CRUD REST con validación de nombre único         |

### 4. Facturación / POS (Mesas y Pedidos)

Interfaz POS completa con flujo: selección mesero → mesas activas → detalle mesa con grid de productos.

```
SeleccionMesero → MesasActivas → MesaDetalle (POS completo)
    │                  │                ├── Grid de productos por categoría
    │                  │                ├── Carrito/líneas de pedido
    │                  │                └── Panel de pago (métodos dinámicos)
    │                  └── Grid de mesas ocupadas con total
    └── Grid de tarjetas de meseros
```

**Modelo Hard-Delete de Mesas**: Mesa sin productos = NO existe en BD. Se crea al agregar primer producto (OCUPADA). Se elimina (DELETE) al anular o facturar. Cascada: mesa → pedido → líneas.

|   Capa   |               Archivo                   |                        Descripción                       |
|----------|-----------------------------------------|----------------------------------------------------------|
| Cliente  | `MesaDetalleController.java`(2,778 lín.)| Interfaz POS completa                                    |
| Cliente  | `FacturacionController.java`            | Navegación entre sub-pasos                               |
| Cliente  | `MesasActivasController.java`           | Grid de mesas ocupadas                                   |
| Cliente  | `SeleccionMeseroController.java`        | Selección de mesero                                      |
| Servidor | `MesaController.java`                   | REST: crear/obtener mesa, guardar pedido, listar activas |
| Servidor | `MesaServicioImpl.java`                 | Orquestación mesa/pedido/líneas                          |

### 5. Métodos de Pago

Métodos dinámicos configurables por ADMIN. Seed: EFECTIVO (inborrable), DÉBITO, CRÉDITO, TRANSFERENCIA, QR, MIXTO.

### 6. Meseros

Vista de tarjetas mostrando código, nombre y género de cada mesero (`MeserosController.java`).

---

## Modelo de Datos

### Esquema de BD (7 tablas + 1 preparada)

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│  categorias │────►│  productos  │     │   usuarios   │
└─────────────┘     └──────┬──────┘     └───────┬──────┘
                           │                    │
                    ┌──────▼───────┐      ┌─────▼──────┐
                    │ lineas_pedido│◄─────│   mesas    │
                    └──────┬───────┘      └─────┬──────┘
                           │                    │
                    ┌──────▼───────┐            │
                    │   pedidos    │◄───────────┘
                    └──────────────┘
┌──────────────┐    ┌──────────────┐
│ metodos_pago │    │    cajas     │← esquema creado, entidad pendiente
└──────────────┘    └──────────────┘
```
|      Tabla    |                                                    Columnas clave                                                      |
|---------------|------------------------------------------------------------------------------------------------------------------------|
|`usuarios`     |id_usuario, nombre_usuario, contrasena, codigo, pin (BCrypt), nombre_completo, rol, genero, bloqueado, intentos_fallidos|
|`categorias`   |id_categoria, nombre, descripcion, orden                                                                                |
|`productos`    |id_producto, codigo, nombre, precio, id_categoria FK, stock_actual, stock_minimo, requiere_stock                        |
|`mesas`        |id_mesa, numero_mesa, estado, id_mesero FK                                                                              |
|`pedidos`      |id_pedido, id_mesa FK (cascade), total, estado                                                                          |
|`lineas_pedido`|id_linea_pedido, id_pedido FK (cascade), id_producto FK, nombre_producto, precio_unitario, timestamp                    |
|`metodos_pago` |id_metodo_pago, nombre, descripcion, orden, es_predeterminado                                                           |
|`cajas`        |id_caja, numero_caja, usuario apertura/cierre FKs, fondo_inicial, totales, diferencia, estado                           |
> Todas las tablas incluyen: `activo` (soft delete), `fecha_creacion`, `fecha_actualizacion` (JPA auditing vía `EntidadBase`).

### Migraciones Flyway

|          Migración         |                         Descripción                            |
|----------------------------|----------------------------------------------------------------|
|`V1__crear_tablas_base.sql` |7 tablas con índices y FK constraints                           |
|`V2__datos_iniciales.sql`   |Seed: 1 admin + 1 cajero + 8 meseros, 8 categorías, 40 productos|
|`V3__crear_metodos_pago.sql`|Tabla `metodos_pago` + 6 métodos seed                           |
|`V4__corregir_pin_hash.sql` |Corrección de hashes BCrypt de PINs                             |

---

## Seguridad

|        Aspecto         |               Detalle                   |
|------------------------|-----------------------------------------|
|Algoritmo JWT           |HS256, expiración 24h (configurable)     |
|Claims                  |sub (username), rol, idUsuario           |
|Contraseñas/PINs        |BCrypt                                   |
|Almacenamiento token    |En memoria del cliente (nunca persistido)|
|Protección SQL Injection|JPA/Hibernate (prepared statements)      |
|CORS                    |Configurado para clientes JavaFX en LAN  |
|Bloqueo de cuenta       |Tras intentos fallidos consecutivos      |

|   Rol  |                Puede                   |          No puede           |
|--------|----------------------------------------|-----------------------------|
| MESERO |Crear/modificar pedidos (solo sus mesas)|Información financiera, admin|
| CAJERO |Procesar pedidos→ventas, cobrar         |Configuración del sistema    |
| ADMIN  |Todo                                    |             —               |

---

## Estructura del Proyecto

```
Kipu/
├── LICENSE                              # Source-available (Elastic License 2.0, español, ley colombiana)
├── README.md
├── pom.xml                              # POM padre Maven multi-módulo
│
├── kipu-common/                        # Módulo compartido (25 Java)
│   └── com/kipu/common/
│       ├── constantes/Constantes.java   # Endpoints, HTTP, JWT, formatos, mensajes
│       ├── dto/ (14 DTOs)               # RespuestaApi, Auth, Login, Usuario, Producto,
│       │                                # Categoria, MetodoPago, Pedido, LineaPedido,
│       │                                # MesaActiva, MesaConPedido, CreacionMesa
│       ├── enums/                       # Rol, Genero
│       ├── excepcion/                   # KipuException, Autenticacion, Conexion,
│       │                                # Validacion, RecursoNoEncontrado
│       └── utilidad/                    # ValidacionUtil, MonetarioUtil, FechaUtil
│
├── kipu-servidor/                      # Backend Spring Boot (44 Java)
│   ├── controlador/ (6)                 # Autenticacion, Usuario, Producto, Categoria,
│   │                                    # MetodoPago, Mesa
│   ├── servicio/ (6+6)                  # Interfaces + implementaciones
│   ├── repositorio/ (6)                 # JPA Repositories
│   ├── modelo/entidad/ (7+1)           # Entidades JPA + EntidadBase
│   ├── mapeo/ (5)                       # MapStruct Mappers
│   ├── seguridad/                       # JwtUtil, JwtFiltro, ConfiguracionSeguridad
│   ├── configuracion/                   # CORS, CargadorArchivoEnv
│   ├── excepcion/                       # ManejadorGlobalExcepciones
│   └── resources/
│       ├── application.yml / -dev.yml
│       └── db/migration/ (V1-V4)
│
├── kipu-cliente/                       # Frontend JavaFX (38 Java, 15 FXML)
│   ├── controlador/ (13)                # Login, Menu, Usuarios, Teclado,
│   │   ├── productos/ (5)               # Listado, Crear, CategoriaListado, CategoriaCrea
│   │   ├── meseros/ (1)                 # MeserosController
│   │   └── facturacion/ (4)             # Facturacion, SeleccionMesero, MesasActivas,
│   │                                    # MesaDetalle (POS, 2778 lín.)
│   ├── servicio/ (7)                    # API clients async (CompletableFuture)
│   ├── modelo/                          # Mesa, LineaPedido (client-side)
│   ├── componente/                      # FondoAnimado, ToggleSwitch, bordes interactivos
│   ├── utilidad/ (7)                    # Navegacion, Alertas, Teclados, Monitor, Servidor
│   ├── configuracion/                   # ConfiguracionCliente (singleton)
│   └── resources/
│       ├── vista/ (15 FXML)
│       ├── css/estilos.css              # Tema luxury dorado+negro
│       ├── imagenes/                    # Logo, fondo, avatares
│       └── iconos/
│
├── database/                            # setup-database.sql, reset, limpiar-todo.sql
├── scripts/                             # build, package, start-servidor/cliente, add-license
├── docs/ (7)                            # Arquitectura, empaquetado, PostgreSQL, pago, LAN, mejoras
└── packaging/
```
### Estadísticas

|      Métrica     |                   Cantidad                   |
|------------------|----------------------------------------------|
|Archivos Java     |**107** (44 servidor + 38 cliente + 25 common)|
|Archivos FXML     |**15**                                        |
|REST Controllers  |**6**                                         |
|JPA Entities      |**7** + EntidadBase                           |
|MapStruct Mappers |**5**                                         |
|DTOs compartidos  |**14**                                        | 
|Migraciones Flyway|**4**                                         |
|Tablas BD         |**7** (+1 preparada)                          |
---
## Componentes Reutilizables del Cliente

|         Componente        |                          Descripción                          |
|---------------------------|---------------------------------------------------------------|
|`FondoAnimado`             |Canvas "Golden Nebula" con partículas doradas animadas (60 FPS)|
|`ToggleSwitch`             |Switch dorado para alternar login admin/PIN                    |
|`LineaDivisoriaInteractiva`|Divisor con brillo dorado que sigue el mouse                   |
|`BordeInteractivoModal`    |Borde dorado interactivo para modales (~60 FPS)                |
|`TecladoVirtualSimple`     |Teclado QWERTY programático con ícono en campos                |
|`TecladoNumerico`          |Teclado numérico para PINs (auto-open on focus)                |  
|`NotificacionUtil`         |Toasts/snackbars animados (éxito, error, warning, info)        |
|`MonitorConexion`          |Indicador de health check periódico al servidor                |
|`ServidorEmbebido`         |Lanza kipu-servidor como subproceso si es necesario           |
---
## Módulos Pendientes

| Módulo | Estado | Descripción |
|--------|--------|-------------|
| **Ventas/PLU** | Esquema parcial | Pedido → venta con métodos de pago, tickets, descuentos |
| **Caja** | Solo tabla en BD | Apertura con fondo, movimientos, cierre con arqueo, cuadre |
| **Reportes** | No iniciado | Ventas por periodo, productos top, rendimiento, export PDF/Excel |
| **Auditoría** | No iniciado | Historial de acciones por usuario |
| **Inventario avanzado** | Parcial (stock en modelo) | Alertas stock bajo, movimientos de inventario |
---
## Glosario
|     Término    |                   Significado                   |
|----------------|-------------------------------------------------|
|PLU             |Price Look-Up (venta procesada)                  |
|Comandera       |Terminal de pedidos (meseros)                    |
|POS             |Point of Sale (terminal caja)                    |
|Arqueo          |Conteo de dinero al cierre                       |
|Cuadre          |Conciliación ventas vs dinero en caja            |
|Hard Delete     |Eliminación física del registro en BD            | 
|Source-available|Código fuente visible, pero uso requiere licencia|
---
**Kipu — Sistema POS para Establecimientos Nocturnos**  
Versión 1.0.0 | Marzo 2026  
Copyright (c) 2026 Dilan Acuña / Kipu. Todos los derechos reservados.  
Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
