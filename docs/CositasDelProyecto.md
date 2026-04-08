# Arquitectura - Diagrama Visual

┌─────────────────────────────────────────────────────────────────────────────┐
│                    SISTEMA KIPU - ARQUITECTURA COMMON                      │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                            KIPU-COMMON (Compartido)                        │
│                    Dependencia de cliente Y servidor                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  dto/                     constantes/               excepcion/              │
│  ├─ ProductoDto           ├─ Endpoints              ├─ KipuException       │
│  ├─ UsuarioDto            ├─ Http                   ├─ RecursoNoEncontrado  │
│  ├─ CategoriaDto          ├─ Jwt                    ├─ ValidacionException  │
│  ├─ RespuestaApi          ├─ Formatos               ├─ AutenticacionEx.     │
│  └─ ...                   ├─ Validaciones           └─ ConexionException    │
│                           ├─ Ui                                             │
│  enums/                   ├─ Mensajes               utilidad/               │
│  ├─ Rol                   └─ CodigosError           ├─ ValidacionUtil       │
│  ├─ EstadoPedido                                    ├─ FechaUtil            │
│  ├─ MetodoPago                                      └─ MonetarioUtil        │
│  └─ Genero                                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       │ depende de
                    ┌──────────────────┴──────────────────┐
                    │                                     │
                    ▼                                     ▼

┌────────────────────────────────┐    ┌────────────────────────────────┐
│      KIPU-CLIENTE (JavaFX)    │    │   KIPU-SERVIDOR (Spring Boot) │
│         Frontend Táctil        │    │        Backend REST API        │
├────────────────────────────────┤    ├────────────────────────────────┤
│                                │    │                                │
│ Controladores FXML             │    │ Controllers REST               │
│  ├─ LoginController            │    │  ├─ UsuarioController          │
│  ├─ MenuPrincipalController    │    │  ├─ ProductoController         │
│  ├─ UsuariosController         │    │  ├─ CategoriaController        │
│  └─ ...                        │    │  └─ ...                        │
│                                │    │                                │
│ Servicios API (mejorados)      │    │ Servicios de Negocio           │
│  ├─ ClienteApiBase             │    │  ├─ UsuarioServicio            │
│  │   └─ Métodos HTTP helper    │    │  ├─ ProductoServicio           │
│  ├─ UsuarioServicio            │    │  ├─ CategoriaServicio          │
│  │   └─ extends ClienteApiBase │    │  └─ ...                        │
│  ├─ ProductoServicio           │    │                                │
│  │   └─ extends ClienteApiBase │    │ Repositorios JPA               │
│  └─ CategoriaServicio          │    │  ├─ UsuarioRepositorio         │
│      └─ extends ClienteApiBase │    │  ├─ ProductoRepositorio        │
│                                │    │  └─ ...                        │
│ Utilidades                     │    │                                │
│  ├─ NavegacionUtil             │    │ Seguridad                      │
│  ├─ AlertaUtil                 │    │  ├─ JwtUtil                    │
│  ├─ NotificacionUtil           │    │  ├─ JwtFiltroAutenticacion     │
│  └─ TecladoVirtualUtil         │    │  └─ ConfiguracionSeguridad     │
│                                │    │                                │
│ Configuración                  │    │ Configuración                  │
│  └─ ConfiguracionCliente       │    │  ├─ application.yml            │
│      ├─ URL servidor           │    │  └─ ConfiguracionCors          │
│      ├─ Timeout                │    │                                │
│      └─ Token JWT              │    │ Base de Datos                  │
│                                │    │  └─ PostgreSQL                 │
│ Vistas FXML + CSS              │    │                                │
│  ├─ login-admin.fxml           │    │ Migraciones                    │
│  ├─ menu-principal.fxml        │    │  ├─ Flyway                     │
│  └─ usuarios.fxml              │    │  └─ db/migration/*.sql         │
│                                │    │                                │
└────────────────────────────────┘    └────────────────────────────────┘
                │                                     │
                │  HTTP REST                          │
                │  (JSON over HTTP)                   │
                │    v/ Authorization: Bearer JWT     │
                └─────────────────┬───────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │   RED LOCAL (LAN)       │
                    │   Sin internet          │
                    │   192.168.x.x:8080      │
                    └─────────────────────────┘

## Flujo de Comunicación

## Login de Usuario

┌──────────┐         ┌─────────────┐         ┌──────────────┐
│ Usuario  │         │   Cliente   │         │   Servidor   │
└────┬─────┘         └──────┬──────┘         └──────┬───────┘
     │                      │                       │
     │ 1. Ingresa usuario   │                       │
     │   y contraseña       │                       │
     ├─────────────────────>│                       │
     │                      │                       │
     │                      │ 2. POST /api/usuarios/login
     │                      │    Body: { usuario, password }
     │                      ├──────────────────────>│
     │                      │                       │
     │                      │                       │ 3. Valida credenciales
     │                      │                       │    con BCrypt
     │                      │                       │
     │                      │ 4. RespuestaApi<AuthDto>
     │                      │    { token, rol, ... }
     │                      │<──────────────────────┤
     │                      │                       │
     │ 5. Guarda token      │                       │
     │    en memoria        │                       │
     │<─────────────────────┤                       │
     │                      │                       │
     │ 6. Redirige a        │                       │
     │    menu principal    │                       │
     │<─────────────────────┤                       │
     │                      │                       │

## Operación CRUD (listar productos)


┌──────────┐         ┌─────────────┐         ┌──────────────┐
│ Usuario  │         │   Cliente   │         │   Servidor   │
└────┬─────┘         └──────┬──────┘         └──────┬───────┘
     │                      │                       │
     │ 1. Clic en           │                       │
     │    "Productos"       │                       │
     ├─────────────────────>│                       │
     │                      │                       │
     │                      │ 2. productoServicio.listarTodos()
     │                      │    └─> ClienteApiBase.ejecutarGet()
     │                      │                       │
     │                      │ 3. GET /api/productos │
     │                      │    Headers:           │
     │                      │    - Authorization: Bearer <JWT>
     │                      │    - Content-Type: application/json
     │                      ├──────────────────────>│
     │                      │                       │
     │                      │                       │ 4. Valida JWT
     │                      │                       │    (JwtFiltro)
     │                      │                       │
     │                      │                       │ 5. Controller →
     │                      │                       │    Service →
     │                      │                       │    Repository
     │                      │                       │
     │                      │ 6. RespuestaApi<List<ProductoDto>>
     │                      │    {
     │                      │      exito: true,
     │                      │      datos: [productos],
     │                      │      mensaje: "OK"
     │                      │    }
     │                      │<──────────────────────┤
     │                      │                       │
     │ 7. Renderiza         │                       │
     │    productos en UI   │                       │
     │<─────────────────────┤                       │
     │                      │                       │


##  Jerarquía de Excepciones

KipuException (abstracta)
│
├─ RecursoNoEncontradoException (404)
│  └─ Uso: Usuario/Producto/Pedido no encontrado
│
├─ ValidacionException (400)
│  └─ Uso: Datos de entrada inválidos
│
├─ AutenticacionException (401)
│  └─ Uso: Credenciales incorrectas, token inválido
│
└─ ConexionException (red - solo cliente)
   └─ Uso: Servidor no responde, timeout

Todas incluyen:
  - codigoError: String (estandarizado)
  - mensaje: String (descriptivo)
  - causa: Throwable (opcional, chaining)

## Lógica de Gestión de Mesas

-Flujo de Creación de Mesa

Usuario ingresa nombre/número en input de facturación
         ↓
¿Mesa ya existe?
    ↓ SÍ → Abrir detalle de mesa
    ↓ NO
         ↓
    ¿Qué rol tiene el usuario?
         ↓
    ├─ MESERO → Crear mesa automáticamente con su nombre
    │            ↓
    │         Abrir detalle de mesa
    │
    └─ CAJERO/ADMIN → Cargar subvista seleccion-mesero.fxml
                       ↓
                   Mostrar nombre de mesa a crear (ej: "Mesa: 10")
                       ↓
                   Mostrar grid de cards de meseros 
                       ↓
                   Usuario hace clic en un card de mesero
                       ↓
                   Crear mesa con mesero seleccionado
                       ↓
                   Abrir detalle de mesa

#### Servicios a Crear
- `MesaServicio.crear(MesaDto)`
- `MesaServicio.obtenerActivas()`
- `MesaServicio.obtenerPorNombre(String)`
- `MeseroServicio.obtenerTodos()`
- `CajaServicio.cerrarCaja()` → Desactivar todas las mesas


### 2. Cierre de Caja

#### Lógica
```java
public void cerrarCaja() {
    // 1. Obtener todas las mesas activas
    List<Mesa> mesasActivas = mesaServicio.obtenerActivas();
    
    // 2. Desactivar todas
    for (Mesa mesa : mesasActivas) {
        mesa.setActiva(false);
        mesa.setFechaCierre(LocalDateTime.now());
        mesaServicio.actualizar(mesa);
    }
    
    // 3. Registrar cierre en tabla cierres_caja
    cierreServicio.registrarCierre(/* datos del cierre */);
    
    // 4. Limpiar Map en memoria
    mesasActivas.clear();
}
```

---

### 3. Sincronización en Tiempo Real

#### Opciones
- **WebSockets**: Para notificaciones de mesas creadas/actualizadas
- **Polling**: Recargar mesas cada X segundos
- **Server-Sent Events (SSE)**: Para actualizaciones push


### 4. Validaciones Adicionales

-  Verificar que el mesero seleccionado esté activo
-  Validar que el mesero no tenga un máximo de mesas asignadas
-  Impedir duplicados de nombres de mesa (case-insensitive)
-  Registrar auditoría de quién creó cada mesa

### 5. Mejoras de UX

- **Autocompletar**: Sugerir nombres de mesas mientras se escribe
- **Búsqueda por mesero**: Filtrar mesas de un mesero específico
- **Estadísticas**: Mostrar número de mesas activas por mesero
- **Indicadores visuales**: Colores según tiempo de ocupación




                                                       
                                                        
                                                         ┌─────────────────┐         
                                                         │ Nube(icono nube)│         
                                                         └───────┬─────────┘ 
                                                                 ∆
┌──────────────┐                                                 │
│   CLIENTE N1 │────────────┐  Este seria mas o menos el centro  │
└──────┬───────┘            │            del diagrama total      │
                            │                  │                 │
                            ▼                  ▼                 │         
┌──────────────┐       ┌────────────┐        ┌──────────────┐    ▼     ┌───────────┐
│   CLIENTE N0 │──────>│    HOST    │───────>│   Servidor   │<────────>│ PostgreSQL│
└──────────────┘       └────────────┘        └──────────────┘          └───────────┘
                            ∆
                            │
┌──────────────┐            │          
│   CLIENTE N2 │────────────()Nuevo nodo invisible que hace la conexion a 90º         
└──────────────┘            del cliente, se veria tal como cliente-->nodo-->host, los clientes
                            se crearan a la izq primero alineado con el host, el siguiente arriba
                            de ese el tercero debe equilibrar por lo que se crearia abajo y asi
                            de nuevo el cuarto arriba, quinto abajo, etc.