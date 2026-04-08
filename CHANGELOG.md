# Changelog

Todos los cambios notables de este proyecto se documentarán aquí.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere al [Versionado Semántico](https://semver.org/lang/es/).

## [0.0.5] — 2026-04-04

### Agregado

#### Sistema de Resolución Adaptativa
- Detección automática de resolución, DPI y escala del sistema operativo al iniciar la aplicación
- 11 perfiles de resolución predefinidos (desde 1024×768 hasta 3840×2160) cubriendo ratios 4:3, 5:4, 16:9, 16:10, 21:9 y 32:9
- Escalado dinámico con tolerancia del 8% para minimizar barras negras en pantallas no estándar
- +700 reglas CSS de resolución y ratio (`res-small`, `res-tablet`, `res-hd`, `res-qhd`, `res-4k`, `ratio-classic`, `ratio-ultrawide`, etc.)
- Panel de configuración de resolución (`PantallaResolucionHandler`) para selección manual de perfil
- Documentación técnica completa del sistema (`docs/PLAN_RESOLUCION_ADAPTATIVA.md`)

#### Panel de Actualizaciones (rediseño completo)
- `ActualizacionesHandler` reescrito desde cero — interfaz rediseñada con información detallada de versiones
- `VerificacionActualizacionServicio` reescrito — detección automática de nuevas versiones con reintentos y manejo robusto de errores
- Soporte para notas de versión, checksums SHA-256 y enlaces de descarga por plataforma

#### Infraestructura del Proyecto
- 6 archivos de definición de agentes y habilidades del proyecto (`.github/agents/`, `.github/skills/`)
- Documentación de perfilado de rendimiento, diagnóstico de concurrencia, auditoría visual, detección de fugas de memoria y análisis de queries

#### Internacionalización
- +58 claves i18n nuevas en cada archivo de idioma (ES/EN/PT) para los módulos de resolución, actualizaciones y diagnóstico de red

### Cambiado

#### Renombramiento a Español (convención del proyecto)
- Controladores: `UpdateBannerController` → `BannerActualizacionController`, `RenewalBannerController` → `BannerRenovacionController`, `LicenseDialogController` → `LicenciaDialogoController`
- Servicios: `LicenseServicio` → `LicenciaServicio`, `UpdateCheckServicio` → `VerificacionActualizacionServicio`
- Controlador servidor: `SyncControlador` → `SincronizacionController`
- Vistas FXML: `update-banner` → `banner-actualizacion`, `renewal-banner` → `banner-renovacion`, `license-dialog` → `licencia-dialogo`
- Componente: `TutorialWeb` → `TutorialNube`

#### Mejoras en Módulos Existentes
- `AsistenteBaseDatos` — mejoras significativas en el asistente de configuración de base de datos
- `DiagnosticoRedHandler` — expansión del panel de diagnóstico de red con más pruebas y métricas
- Múltiples handlers de configuración refinados (conexión red, respaldo de datos, gráficos, licencia)
- Actualización de POMs y scripts de empaquetado para todas las plataformas

#### CSS
- `estilos.css` expandido significativamente (+2500 líneas) con el sistema completo de resolución adaptativa

### Eliminado
- `VentasListadoController` y su FXML correspondiente (código no utilizado)
- `package.json` y `package-lock.json` de la raíz del proyecto (no eran necesarios)

---

## [0.0.3] — 2026-03-27

### Cambiado
- Actualización de dependencias Maven del proyecto

---

## [0.0.2] — 2026-03-26

### Agregado

#### Autenticación y Seguridad
- Login dual: administradores (usuario/contraseña) y empleados (código de 2 dígitos + PIN numérico)
- Autenticación JWT HS256 con tokens de 24h y refresh tokens de 7 días
- Bloqueo automático de cuenta tras intentos fallidos de login
- Filtro de autenticación JWT para todas las rutas protegidas
- Configuración CORS para comunicación cliente-servidor en LAN

#### Gestión de Usuarios
- CRUD completo de usuarios con roles (MESERO, CAJERO, ADMIN) y género (MASCULINO, FEMENINO)
- Búsqueda y filtrado de usuarios, activar/desactivar cuentas
- Contraseñas encriptadas con BCrypt

#### Catálogo de Productos
- CRUD de productos con código único, nombre, descripción, precio y categoría
- Control de stock (stock actual, stock mínimo, flag `requiere_stock`)
- CRUD de categorías con nombre único, color, orden configurable y reordenamiento drag-and-drop
- Vista de inventario con control de stock

#### POS / Facturación
- Flujo completo: selección de mesero → vista de mesas activas → POS con grid de productos + carrito + panel de pago
- Mesa se crea dinámicamente al agregar el primer producto, se elimina al anular o facturar
- Líneas de pedido con eliminación en cascada
- Detalle de mesa con resumen de pedido y total

#### Ventas y Pagos
- Modelo de ventas con líneas de venta, pagos y métodos de pago
- Registro de ventas desde pedidos facturados
- 6 métodos de pago seed: EFECTIVO (inborrable), DÉBITO, CRÉDITO, TRANSFERENCIA, QR, MIXTO
- CRUD dinámico de métodos de pago configurable por ADMIN

#### Vista de Meseros
- Tarjetas visuales con código, nombre completo y género del mesero

#### Logs Críticos del Sistema
- Registro automático de errores críticos con nivel, origen, mensaje y detalle
- 3 estados de gestión: NOTIFICACIÓN_ERROR → EN_REVISIÓN → RESUELTO
- Modal de detalle con copiar al portapapeles
- Filtros por estado y paginación

#### Configuración del Sistema
- Panel de herramientas de configuración modular con los siguientes módulos:
  - **Idioma**: Cambio de idioma del sistema (ES/EN/PT)
  - **Licencia**: Gestión de licencia de software con diálogo dedicado
  - **Actualizaciones**: Verificación de nuevas versiones
  - **Base de datos**: Asistente de configuración, backup y restauración
  - **Conexión de red**: Monitor de conexión LAN, panel de diagnóstico, topología de red
  - **Respaldo de datos**: Herramienta de backup/restore
  - **Gráficos**: Configuración de rendimiento visual
  - **Acerca de**: Información del sistema y créditos
  - **Logs críticos**: Visor integrado en configuración
  - **Métodos de pago**: Gestión desde configuración

#### Internacionalización (i18n)
- Sistema completo con `IdiomaUtil` para 3 idiomas: Español, English, Português
- 620+ claves de traducción en `mensajes.properties`, `mensajes_en.properties`, `mensajes_pt.properties`
- Sincronización de idioma entre servidor y clientes conectados

#### Shell y Navegación
- Shell principal con sidebar, header (logo + usuario + logout) y footer (fecha/hora + versión)
- Menú principal post-login con accesos a todos los módulos según rol
- Navegación fullscreen en todas las vistas post-login
- Subvistas cargadas dinámicamente dentro del shell

#### Licenciamiento y Actualizaciones
- Diálogo de licencia con activación y estado
- Banner de renovación de licencia
- Banner de actualización disponible
- Servicio de verificación automática de nuevas versiones

#### Conexión LAN
- Monitor de conexión al servidor con reintentos automáticos
- Panel de diagnóstico de red con pruebas de conectividad
- Visualización de topología de red (servidor + clientes conectados)
- Registro de clientes conectados en el servidor

#### Componentes UI
- `FondoAnimado` — fondo visual animado para la pantalla de login
- `ToggleSwitch` — interruptor on/off estilizado
- `BordeInteractivoModal` — bordes interactivos para modales
- `LineaDivisoriaInteractiva` — separadores visuales con interacción
- `PanelConexionRed` — indicador visual de estado de red
- `TopologiaRed` — diagrama de topología de red
- `TutorialNube` — tutorial de configuración de sincronización en la nube
- `MotorAnimaciones` — motor de animaciones reutilizable optimizado para hardware bajo

#### Infraestructura
- Arquitectura Maven multi-módulo: `kipu-common`, `kipu-servidor`, `kipu-cliente`
- Servidor embebido Spring Boot iniciado desde el cliente (`ServidorEmbebido`)
- 14 tablas PostgreSQL con 25 índices, migración Flyway única (`V1__esquema_completo.sql`)
- Sincronización con nube via patrón Outbox + CDC (Change Data Capture) con triggers PostgreSQL
- Cifrado de datos para sincronización en la nube (`CifradoNube`)
- Detección automática de dependencias del sistema (`DetectorDependencias`)
- Manejo global de excepciones con `@ControllerAdvice` y respuestas API estandarizadas
- MapStruct para mapeo Entity↔DTO (7 mappers)
- Empaquetado multiplataforma: `.deb` (Linux), `.exe` (Windows), `.dmg` (macOS) via GitHub Actions
- Workflow de release automatizado con generación de licencia trial, checksums SHA-256 y registro en backend

#### Diseño Visual
- Tema luxury/premium oscuro con acentos dorados (#d4af37) optimizado para locales nocturnos
- 6900+ líneas de CSS con soporte táctil (targets mínimos 44×44px)
- Tipografía Roboto/Open Sans con contraste WCAG AA
- Animaciones limitadas a 200-300ms para rendimiento en hardware gama baja

---

## [0.0.1] — 2026-03-26

### Agregado
- Commit inicial del proyecto con estructura base, configuración de CI/CD y primer workflow de release
- Configuración de licenciamiento y conexión con backend en la nube

---

[0.0.5]: https://github.com/DilanSG/Kipu/compare/v0.0.3...v0.0.5
[0.0.3]: https://github.com/DilanSG/Kipu/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/DilanSG/Kipu/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/DilanSG/Kipu/releases/tag/v0.0.1
