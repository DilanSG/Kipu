# Copilot Instructions - Kipu

## Proyecto

**Kipu** — Sistema POS + gestión de pedidos para bares/pubs nocturnos.

| Aspecto | Detalle |
|---------|---------|
| Operación | 100% LAN, sin internet |
| Arquitectura | Cliente-servidor (1 servidor por bar, N clientes) |
| Clientes | POS (cajeros), comanderas (meseros), panel admin |
| Comunicación | Solo REST API (sin peer-to-peer) |
| Hardware | Optimizado para equipos gama baja/antiguos || Licencia | Source-available (basada en Elastic License 2.0, ley colombiana) |
| Titular | Dilan Acuña / Kipu |
---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|------|-----------|--------|
| Lenguaje | Java | 21 |
| Frontend | JavaFX + FXML + CSS (tema oscuro, táctil) | 21.0.5 |
| Backend | Spring Boot / Spring Security + JWT | 3.2.1 |
| API | REST JSON sobre HTTP | — |
| BD | PostgreSQL / JPA-Hibernate (validate) / Flyway | 15+ / 9.22.3 |
| Mapeo DTOs | MapStruct | 1.5.5 |
| HTTP cliente | java.net.http.HttpClient + OkHttp | — |
| Build | Maven multi-módulo | — |
| Boilerplate | Lombok | 1.18.30 |
| JSON | Jackson | 2.16.0 |
| Capas backend | Controller → Service → Repository | — |

---

## Diseño Visual (OBLIGATORIO: Luxury/Premium)

Interfaz elegante para locales nocturnos. Alineada con logo KIPU en `imagenes/LOGOPNG.png`.

### Paleta de Colores

| Rol | Colores | Uso |
|-----|---------|-----|
| Fondo principal | Imagen `BackGround.png` via `.contenedor-centro` | TODAS las vistas principales |
| Negro profundo | `#0a0a0a` `#121212` `#1a1a1a` | Fondos secundarios, tarjetas |
| Dorado metálico | `#d4af37` `#c9a961` `#b8984e` | Acentos, botones principales, activos, títulos |
| Grises oscuros | `#2a2a2a` `#333333` `#404040` | Fondos terciarios, bordes |
| Texto principal | `#f5f5f5` `#e8e8e8` | Cuerpo de texto |
| Texto secundario | `#b0b0b0` `#999999` | Labels, subtextos |
| Éxito | `#a8b991` | Con tono verde sutil |
| Advertencia | `#daa520` | Ámbar dorado |
| Error | `#8b0000` + borde dorado | Alertas críticas |

**Regla del dorado**: Solo para acentos (<15% del área). SÍ en botones principales, iconos activos, bordes focus, títulos. NO en fondos, texto largo, elementos deshabilitados.

### Tipografía

| Nivel | Fuente | Tamaño | Color | Peso |
|-------|--------|--------|-------|------|
| Títulos | Roboto, Segoe UI, sans-serif | 18-32px | Blanco/dorado | 500-700 |
| Texto principal | Open Sans, Helvetica Neue, sans-serif | 14-16px | `#f5f5f5` | 400 |
| Secundario | Igual que principal | 12-14px | `#b0b0b0` | 300 |

- Contraste mínimo 4.5:1 (WCAG AA). NO fuentes decorativas. Mínimo: texto 14px, botones 16px, labels 12px.

### Botones Táctiles

| Tipo | Tamaño mínimo | Estilo |
|------|---------------|--------|
| Principal | 60x120px | Gradiente dorado, texto negro, sombra |
| Secundario | 48x100px | Borde dorado, fondo transparente |
| Grid (productos/mesas) | 80x80px | Según contexto |
| Touch target | 44x44px mínimo | Estándar táctil |

- Espaciado entre botones: 8px mínimo
- Estados: normal → hover (elevar -2px) → active (bajar) → focused (borde dorado 2-3px) → disabled (gris, opacity 0.5) → loading (spinner dorado)
- Clases CSS: `.boton-principal`, `.boton-secundario`, `.boton:disabled` (ver `estilos.css`)

### Layout y Composición

**Principios**: Simplicidad, máx 3 niveles de jerarquía, grid de 8px, espaciado generoso (16-24px).

**Contenedores**: Fondo `#1a1a1a`/`#2a2a2a`, bordes `#404040`, border-radius 8-12px, sombras sutiles.

| Zona | Spec |
|------|------|
| Header | 64-72px, fondo `#121212`, logo izq, usuario+logout der |
| Sidebar | 240-280px, `#0a0a0a`, items 48px, activo=borde izq dorado |
| Contenido | Fondo `#121212`, padding 24-32px, max-width 1400px |
| Footer | 48px, `#0a0a0a`, texto gris pequeño |

### UX por Rol

| Rol | Prioridad | Layout | Botones |
|-----|-----------|--------|---------|
| **Cajero** (POS) | Velocidad | Grid productos 4-6 cols + carrito der + total grande 32px dorado | 100x100px mín |
| **Mesero** (Comandera) | Simplicidad | Mesas + selector productos + "Enviar" prominente | Grandes, claros |
| **Admin** (Panel) | Información | Dashboard KPIs + tablas paginadas + gráficos | CRUD estándar |

### Componentes CSS Estándar

Todos definidos en `estilos.css`: `.tabla`, `.campo-texto`, `.card`, `.badge`, `.badge-activo`, `.badge-pendiente`, `.badge-error`, `.modal-overlay`, `.modal-contenido`.

### Performance Visual (Hardware Bajo)

- CSS: máx 3 niveles selectores, máx 2 box-shadow, NO filters (blur/saturate)
- JavaFX: CSS sobre efectos programáticos, VirtualFlow para listas largas, <1000 nodos visibles
- Animaciones: 200-300ms máx, solo transform/opacity, easing `ease`. PROHIBIDO: parallax, partículas, 3D, blur animado
- Targets: <16ms/frame (60FPS), inicio <3s, transiciones <500ms

### Logo KIPU

- Archivo: `imagenes/LOGOPNG.png`
- Login: centro superior 200-300px ancho. Header: esquina izq 120x50px
- Proporciones originales, fondo oscuro, sin filtros, spacing 24px

---

## Componentes Estándar Reutilizables (OBLIGATORIO)

**Toda vista nueva incluye header+footer EXCEPTO**: login, splash, modales, vistas explícitamente marcadas "sin header/footer".

### Header Estándar (FXML `<top>`)

```xml
<HBox styleClass="menu-header" alignment="CENTER_LEFT" spacing="24">
    <padding><Insets top="20" right="32" bottom="20" left="32"/></padding>
    <ImageView fitWidth="120" fitHeight="50" preserveRatio="true">
        <image><Image url="@../imagenes/LOGOPNG.png"/></image>
    </ImageView>
    <Region HBox.hgrow="ALWAYS"/>
    <VBox alignment="CENTER_RIGHT" spacing="4">
        <Label fx:id="labelNombreUsuario" styleClass="menu-usuario-nombre"/>
        <Label fx:id="labelRolUsuario" styleClass="menu-usuario-rol"/>
    </VBox>
    <Button onAction="#cerrarSesion" styleClass="boton-logout" prefHeight="44" prefWidth="44">
        <graphic><Region styleClass="icono-logout"/></graphic>
    </Button>
</HBox>
```

### Footer Estándar (FXML `<bottom>`)

```xml
<HBox alignment="CENTER" styleClass="menu-footer" spacing="30">
    <padding><Insets top="16" right="16" bottom="16" left="16"/></padding>
    <HBox alignment="CENTER" spacing="10">
        <Label fx:id="labelFechaFooter" styleClass="menu-footer-text" style="-fx-font-size: 15px;"/>
        <Label text="|" styleClass="menu-footer-separator" style="-fx-font-size: 15px;"/>
        <Label fx:id="labelHoraFooter" styleClass="menu-footer-text" style="-fx-font-size: 15px;"/>
    </HBox>
    <Region HBox.hgrow="ALWAYS"/>
    <Label text="Sistema Kipu v1.0.0 © 2026" styleClass="menu-footer-text" style="-fx-font-size: 15px;"/>
</HBox>
```

### Controlador Header/Footer

Todo controlador con header/footer necesita:

- **Labels FXML**: `labelNombreUsuario`, `labelRolUsuario`, `labelFechaFooter`, `labelHoraFooter`
- **Timeline reloj**: `Timeline` cada 1s actualizando fecha (`"EEEE, dd 'de' MMMM 'de' yyyy"`, Locale es_ES) y hora (`HH:mm:ss`)
- **initialize()**: Cargar usuario desde `NavegacionUtil.getUsuarioActual()`, iniciar reloj
- **cerrarSesion()**: Parar timeline, desactivar fullscreen, `NavegacionUtil.limpiarUsuario()`, `NavegacionUtil.irALogin()`

### Navegación y Pantalla Completa

Todas las vistas post-login en fullscreen. Al navegar: `stage.setFullScreen(true)`, `stage.setFullScreenExitHint("")`. Solo desactivar fullscreen al cerrar sesión.

---

## Roles y Permisos

| Rol | Puede | No puede |
|-----|-------|----------|
| **MESERO** | Crear/modificar pedidos, ver mesas asignadas | Info financiera, admin |
| **CAJERO** | Procesar pedidos→ventas, cobrar, cerrar caja, ver ventas del día | Configuración sistema |
| **ADMIN** | Todo: usuarios, productos, inventario, config, reportes, auditoría | — |

---

## Módulos del Sistema

### Implementados (Marzo 2026)

1. **Autenticación**: Login dual (admin: usuario/contraseña, empleado: código/PIN), JWT HS256, bloqueo tras intentos fallidos
2. **Usuarios**: CRUD completo, roles (MESERO/CAJERO/ADMIN), género, búsqueda, activar/desactivar
3. **Productos**: CRUD, categorías con drag-and-drop reordenar, filtros, código único
4. **Categorías**: CRUD, nombre único, orden configurable
5. **Facturación/POS**: Flujo selección mesero → mesas activas → POS completo (grid productos + carrito + pago)
6. **Mesas/Pedidos**: Modelo hard-delete, mesa se crea al agregar producto, se elimina al anular/facturar, cascada líneas
7. **Métodos de pago**: CRUD dinámico por ADMIN, seed (EFECTIVO inborrable, DÉBITO, CRÉDITO, TRANSFERENCIA, QR, MIXTO)
8. **Meseros**: Vista de tarjetas con código, nombre, género
9. **Logs críticos**: 3 estados (NOTIFICACION_ERROR → EN_REVISION → RESUELTO), modal detalle con copiar al portapapeles, filtros por estado
10. **i18n**: Sistema completo de internacionalización (ES/EN/PT) con `IdiomaUtil`, sincronización de idioma servidor↔cliente

### Pendientes

11. **Ventas/PLU**: Pedido→venta final con ticket (esquema parcial)
12. **Caja**: Apertura con fondo, movimientos, cierre con arqueo (solo tabla en BD, sin entidad)
13. **Reportes**: Ventas por periodo, productos top, rendimiento personal, export PDF/Excel
14. **Auditoría**: Historial de acciones por usuario
15. **Inventario avanzado**: Alertas stock bajo, movimientos (modelo tiene stock_actual/stock_minimo)

---

## Convenciones de Código

### Idioma
TODO en español: variables, clases, métodos, campos BD, comentarios, logs.

### Sistema de Internacionalización (i18n) — OBLIGATORIO en cliente

Todo texto visible al usuario en `kipu-cliente` DEBE usar el sistema i18n. **Nunca hardcodear strings en español** en la UI.

**Uso básico:**
```java
import com.kipu.cliente.utilidad.IdiomaUtil;

// Texto simple
IdiomaUtil.obtener("ctrl.logs.titulo")              // → "Logs del Sistema"

// Texto con parámetros (MessageFormat)
MessageFormat.format(IdiomaUtil.obtener("ctrl.logs.error_cargar"), ex.getMessage())
```

**Archivos de idioma** en `kipu-cliente/src/main/resources/i18n/`:
- `mensajes.properties` — Español (por defecto)
- `mensajes_en.properties` — English
- `mensajes_pt.properties` — Português

**Convención de claves:**
```
{capa}.{modulo}.{elemento}       → ctrl.logs.titulo
{capa}.{modulo}.{accion}         → ctrl.logs.marcar_resuelto
{capa}.{modulo}.{sub}.{elemento} → ctrl.logs.filtro.todos
{capa}.{modulo}.campo.{nombre}   → ctrl.logs.campo.nivel
```
Capas: `ctrl` (controladores), `log` (mensajes servidor), `srv` (servicios), `val` (validaciones), `nav` (navegación).

**Al crear/modificar archivos cliente:** Agregar las claves nuevas en los **3 archivos** de idioma simultáneamente. El servidor (`kipu-servidor`) NO usa i18n — sus mensajes van directo en español.

**Idiomas soportados:** es_ES, en_US, pt_BR. Configurable por ADMIN desde el panel de configuración del sistema.

### Documentación

El código es para devs con bases sólidas en Spring Boot, JavaFX y patrones enterprise. Documentar solo lo que no es obvio.

- **Clases**: Javadoc breve (1-3 líneas) indicando propósito y contexto. Solo si el nombre no lo explica todo.
- **Métodos públicos**: Javadoc con `@param`/`@return`/`@throws` solo en APIs complejas, servicios con lógica de negocio no trivial, o métodos con efectos secundarios no evidentes. Si el método es un CRUD estándar o delegación directa, NO documentar.
- **Métodos privados**: Sin comentario si el nombre es descriptivo. Comentario de 1-2 líneas solo si hay lógica no intuitiva.
- **Inline**: Comentar el "por qué", nunca el "qué". Si el código necesita explicar qué hace, renombrar variables/métodos primero.
- **NO documentar**: getters/setters, delegaciones simples, CRUDs estándar, constructores obvios, imports.

### Nomenclatura

| Contexto | Convención | Ejemplo |
|----------|-----------|---------|
| Clases Java | PascalCase español | `GestorPedidos`, `ServicioVentas` |
| Métodos/variables | camelCase español | `pedidosPendientes`, `calcularTotal()` |
| Constantes | UPPER_SNAKE_CASE | `TIMEOUT_CONEXION`, `FORMATO_FECHA` |
| Packages | lowercase | `com.kipu.servidor.controlador` |
| Tablas BD | snake_case plural | `usuarios`, `lineas_pedido` |
| Columnas BD | snake_case | `id_usuario`, `fecha_creacion` |
| Endpoints REST | kebab-case español, plural | `GET /api/pedidos`, `POST /api/usuarios/login` |

### Estructura del Proyecto

```
com.kipu.common/ (25 archivos)         com.kipu.cliente/ (38 archivos)
├── constantes/    # Constantes global  ├── controlador/  # 13 FXML Controllers
├── dto/ (14)      # DTOs compartidos   │   ├── productos/ (5)  # Catálogo
├── enums/         # Rol, Genero        │   ├── facturacion/ (4) # POS
├── excepcion/     # KipuException+    │   └── meseros/ (1)
└── utilidad/      # Validación, Fecha  ├── servicio/ (7)  # API clients async
                                        ├── modelo/        # Mesa, LineaPedido
com.kipu.servidor/ (44 archivos)       ├── componente/    # FondoAnimado, ToggleSwitch,
├── controlador/ (6) # REST             │                  # BordeInteractivo, LineaDivisoria
├── servicio/ (6+6)  # Interface+Impl   ├── utilidad/ (7)  # Navegación, Alertas, Teclados,
├── repositorio/ (6) # JPA Repos        │                  # Monitor, ServidorEmbebido
├── modelo/entidad/ (7+1) # Entities    ├── configuracion/ # ConfiguracionCliente (singleton)
├── mapeo/ (5)       # MapStruct        └── resources/
├── seguridad/       # JWT, Filtro          ├── vista/ (15 FXML)
├── configuracion/   # CORS, .env          ├── css/estilos.css
└── excepcion/       # Handler global      ├── imagenes/
                                            └── iconos/
```

### Archivos con Header de Copyright (OBLIGATORIO)

TODO archivo `.java` nuevo debe incluir este encabezado:

```java
/*
 * Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raíz del proyecto para más información.
 * Queda prohibido el uso, copia o distribución sin autorización expresa del titular.
 */
```

---

## Principios de Diseño

**SOLID** estrictamente. Patrones: DTO, Builder, Factory, Strategy, Repository.

### Manejo de Errores
- Excepciones personalizadas + `@ControllerAdvice` global
- Respuestas consistentes: `{"exito": bool, "datos"/{}, "mensaje": ""}` para éxito; `{"exito": false, "error": "CÓDIGO", "mensaje": "", "detalles": {}}` para error
- Códigos HTTP: 200/201/400/401/403/404/500
- Nunca exponer stack traces en producción

### Seguridad
- Contraseñas: BCrypt. JWT en memoria. Validar TODO input en backend. CORS configurado.

### BD
- **Migración única Flyway**: Todo el esquema vive en `V1__esquema_completo.sql`. NO crear V2, V3, etc. Al agregar/modificar tablas, columnas o índices, editar directamente V1. Después de cambiar V1 se debe resetear la BD (`database/reset-database.sql`) para que Flyway la aplique limpia.
- Índices en columnas frecuentes. Foreign keys. `fecha_creacion` + `fecha_actualizacion` en toda tabla. Soft delete (`activo`). Auditoría con JPA Listeners.

### Performance
- Queries optimizadas, paginación, cache (productos/config), connection pooling, timeouts+reintentos
- Concurrencia: transacciones apropiadas, locking optimista preferible, evitar deadlocks

### Frontend JavaFX
- FXML separado de Controller. CSS externo (`estilos.css`). Responsive. Touch 44x44dp mín. Feedback visual (loading/confirmaciones/errores). Lazy loading + virtualización.

---

## Consideraciones Especiales

| Aspecto | Regla |
|---------|-------|
| Offline | NUNCA requerir internet |
| Hardware | Minimizar memoria, queries eficientes, startup rápido |
| Resiliencia | Reintentos API, timeouts configurables, errores amigables, recovery automático |
| Escala | 1-20 clientes/bar, picos 20:00-03:00, 100+ pedidos simultáneos |

---

## Configuración de Referencia

### application.yml (extracto clave)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:kipu_db}
    username: ${DB_USER:kipu_admin}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET}
  expiracion: 86400000      # 24h
  refresh-expiracion: 604800000  # 7d

kipu:
  caja:
    diferencia-maxima-permitida: 50.00
  pedidos:
    tiempo-expiracion-minutos: 30
```

---

## Glosario

| Término | Significado |
|---------|-------------|
| PLU | Price Look-Up (venta procesada) |
| Comandera | Terminal de pedidos (meseros) |
| POS | Point of Sale (terminal caja) |
| Arqueo | Conteo de dinero al cierre |
| Cuadre | Conciliación ventas vs dinero en caja |

---

## Build y Despliegue

**Script de preparación** (`scripts/preparar.sh`): Limpia, instala common, compila, empaca servidor y despliega JAR. Después solo ejecutar `mvn javafx:run -pl kipu-cliente`.

**Flujo manual:**
```bash
mvn clean install -pl kipu-common -DskipTests -q   # Instalar DTO compartido
mvn clean package -pl kipu-servidor -am -DskipTests # Empacar servidor
sudo cp kipu-servidor/target/kipu-servidor-1.0.0.jar /opt/kipu/servidor/lib/app/
mvn javafx:run -pl kipu-cliente                     # Ejecutar cliente
```

---

## Prioridades de Decisión

1. **Simplicidad** sobre complejidad
2. **Rendimiento** sobre features avanzadas
3. **Estabilidad** sobre novedad
4. **Mantenibilidad** sobre código "inteligente"
