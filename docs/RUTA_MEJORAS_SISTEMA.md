# ROADMAP DE MEJORAS - SISTEMA BARYX

**Versión:** 1.0  
**Fecha:** 02 de febrero de 2026  
**Estado Actual:** Prototipo funcional - Requiere mejoras críticas para producción

---

## RESUMEN EJECUTIVO

Este documento define la ruta de desarrollo necesaria para transformar Baryx de un prototipo funcional a un sistema de producción listo para operar en bares y restaurantes, cumpliendo con:

- Requisitos técnicos de seguridad y auditoría  
- Normativa DIAN (Colombia) para sistemas POS  
- Estándares contables y fiscales  
- Trazabilidad completa de operaciones  
- Inmutabilidad de registros críticos  

---

## ANÁLISIS DEL ESTADO ACTUAL

### Lo que YA está implementado

#### Backend (Spring Boot)
- Autenticación JWT con BCrypt
- Control de roles (ADMIN, CAJERO, MESERO)
- Bloqueo por 3 intentos fallidos
- CRUD completo: Usuarios, Productos, Categorías, Mesas
- Gestión de pedidos con líneas de detalle
- Timestamps automáticos (fecha_creacion, fecha_actualizacion)
- Soft delete (campo `activo`)
- Migraciones Flyway (V1__crear_tablas_base.sql)
- API REST consistente con RespuestaApi<T>
- Validaciones en servicios

#### Frontend (JavaFX)
- Login con usuario/contraseña y PIN
- Menú principal modular con subvistas
- CRUD de usuarios, productos, categorías
- Gestión de mesas (vista de mesas activas)
- Interfaz táctil optimizada
- Tema luxury/premium con dorado y negro
- Teclado virtual para pantallas táctiles

#### Base de Datos
- PostgreSQL con schema definido
- Tablas: usuarios, categorias, productos, mesas, pedidos, lineas_pedido, cajas
- Relaciones bien definidas con FKs
- Índices en campos clave

### Lo que FALTA CRÍTICO para Producción


|      Módulo        |        Descripción                          | Prioridad |
|--------------------|---------------------------------------------|-----------|
| Ventas/PLU         | Sistema de facturación y registro de ventas | CRÍTICA |
| Auditoría          | Log inmutable de todas las operaciones      | CRÍTICA |
| Caja               | Apertura, cierre, arqueo, cuadre de caja    | CRÍTICA |
| Facturación DIAN   | Consecutivos, impuestos, factura electrónica| CRÍTICA |
| Reportes           |  Ventas, inventario, auditoría, cajeros     | ALTA |
| Seguridad Avanzada | IP tracking, sesiones, firmas digitales     | ALTA |
| Inventario         | Movimientos, ajustes, kardex                | ALTA |
| Backup/Restore     | Respaldo automático y recuperación          | ALTA |
| Contabilidad       | Doble partida, cuentas contables            | MEDIA |
| Propinas           | Gestión separada de propinas por DIAN       | MEDIA |

---

## FASE 1: MÓDULOS CRÍTICOS (OBLIGATORIOS)

### 1.1 Sistema de Ventas y PLU

**Estado:** NO IMPLEMENTADO  
**Prioridad:** CRÍTICA  
**Tiempo estimado:** 2-3 semanas

#### Descripción del Problema
El sistema actual permite crear pedidos pero NO los convierte en ventas. No hay registro de transacciones monetarias, métodos de pago, ni PLU (Price Look-Up / venta procesada).

#### Componentes Requeridos

**Entidad Venta**
- Numeración consecutiva obligatoria (DIAN)
- Referencia inmutable al pedido original
- Relación con cajero y caja
- Desglose de montos: subtotal, IVA, impoconsumo, propina, descuento, total
- Métodos de pago múltiples por venta
- Timestamp preciso de facturación
- Estado de venta (completada, anulada)
- Hash de seguridad SHA-256 para inmutabilidad
- Datos de anulación si aplica

**Entidad PagoVenta**
- Relación con venta
- Método de pago (efectivo, tarjeta crédito/débito, transferencia, QR)
- Monto del pago
- Referencia de pago
- Datos de tarjeta si aplica (últimos 4 dígitos, franquicia)
- Timestamp del pago

**Sistema de Consecutivos**
- Tabla de control de consecutivos por tipo de documento
- Función SQL thread-safe para obtener siguiente número
- Validación de ausencia de saltos
- Soporte para prefijos y separación por año/mes

**Servicio de Ventas**
- Validación de caja abierta antes de procesar venta
- Cálculo automático de impuestos desagregados
- Obtención de consecutivo único
- Validación de suma de pagos = total
- Generación de hash de seguridad
- Actualización de estado de pedido a "facturado"
- Liberación de mesa
- Registro en auditoría

**Vista Frontend de Facturación**
- Grid de métodos de pago
- Cálculo de cambio en efectivo
- Input de propina opcional
- Resumen con desglose de IVA
- Botón de procesar venta prominente
- Impresión de ticket/factura

#### Checklist de Implementación
- Crear entidades Venta y PagoVenta
- Crear enums EstadoVenta y MetodoPago
- Crear tabla consecutivos con función SQL
- Implementar VentaServicio con procesarVenta()
- Crear VentaController con endpoint POST /api/ventas
- Implementar cálculo de IVA (19%) e impoconsumo (8%)
- Implementar generación de hash SHA-256
- Crear vista facturacion-cobro.fxml
- Integrar impresión de ticket (opcional Fase 1)
- Tests unitarios de VentaServicio
- Validar inmutabilidad de pedidos facturados

---

### 1.2 Sistema de Auditoría Inmutable

**Estado:** NO IMPLEMENTADO  
**Prioridad:** CRÍTICA  
**Tiempo estimado:** 1-2 semanas

#### Descripción del Problema
No existe registro de auditoría de las operaciones. No hay trazabilidad de quién hizo qué y cuándo. Sistema vulnerable a manipulación de datos sin detección.

#### Componentes Requeridos

**Entidad RegistroAuditoria**
- Usuario que realizó la acción (inmutable)
- Tipo de operación (crear, actualizar, eliminar, login, logout, venta, anulación)
- Tabla o módulo afectado
- ID del registro afectado
- Descripción de la operación
- Datos antes y después en formato JSON
- Información de sesión: IP cliente, dispositivo, navegador
- Timestamp preciso de la operación
- Hash SHA-256 del registro (inmutabilidad)
- Hash del registro anterior (encadenamiento blockchain-like)

**Servicio de Auditoría**
- Registro de operaciones con propagación REQUIRES_NEW
- Generación de hash del registro actual
- Encadenamiento con hash del registro anterior
- Verificación de integridad de la cadena completa
- Detección de manipulaciones

**Aspecto AOP para Auditoría Automática**
- Interceptor de métodos anotados con @Auditable
- Captura de datos antes de la operación (para updates)
- Ejecución de operación
- Registro automático en auditoría con contexto completo

**Anotación @Auditable**
- Tipo de operaciónSD
- Tabla afectada
- Descripción opcional
- Aplicación en servicios críticos

#### Checklist de Implementación
- Crear entidad RegistroAuditoria con índices
- Crear enum TipoOperacion
- Crear AuditoriaServicio con registrar()
- Implementar generación de hash y encadenamiento
- Crear AuditoriaAspect con AOP
- Crear anotación @Auditable
- Aplicar @Auditable en Venta, Usuario, Producto, Pedido
- Crear endpoint GET /api/auditoria (solo ADMIN)
- Implementar verificarIntegridad()
- Vista frontend de logs (solo ADMIN)
- Filtros por usuario, fecha, tipo
- Exportación a CSV/PDF

---

### 1.3 Sistema de Gestión de Caja

**Estado:** PARCIAL (tabla existe, lógica NO)  
**Prioridad:** CRÍTICA  
**Tiempo estimado:** 1-2 semanas

#### Descripción del Problema
La tabla `cajas` existe en la base de datos pero no hay lógica implementada. No se puede abrir, cerrar ni hacer arqueo de caja. No hay control de efectivo.

#### Componentes Requeridos

**Entidad Caja**
- Número de caja único por terminal
- Usuario de apertura y cierre
- Fechas de apertura y cierre
- Fondo inicial
- Totales por método de pago al cierre
- Efectivo contado físicamente (arqueo)
- Diferencia calculada automáticamente
- Estado (abierta, cerrada)
- Observaciones

**Entidad MovimientoCaja**
- Relación con caja
- Tipo (ingreso/egreso)
- Concepto del movimiento
- Monto
- Usuario responsable
- Timestamp del movimiento
- Observaciones
- Movimientos no relacionados con ventas (retiros, gastos menores, etc.)

**Servicio de Caja**
- Apertura con validación de una sola caja abierta por terminal
- Cierre con cálculo automático de totales de ventas
- Cálculo de diferencia entre efectivo esperado y contado
- Registro de movimientos de caja
- Alertas de diferencias significativas
- Auditoría completa de operaciones

**Vistas Frontend**
- Apertura: número de caja, fondo inicial
- Cierre: resumen de ventas, desglose por método, arqueo físico, cálculo de diferencia
- Movimientos: lista de ingresos/egresos, nuevo movimiento

#### Checklist de Implementación
- Completar entidad Caja
- Crear entidad MovimientoCaja
- Crear enums EstadoCaja, TipoMovimiento
- Implementar CajaServicio (abrir, cerrar, movimientos)
- Crear CajaController
- Crear vistas: apertura-caja, cierre-caja, movimientos-caja
- Validar una sola caja abierta por terminal
- Validar ventas solo con caja abierta
- Generar reporte PDF de cierre
- Tests unitarios

---

### 1.4 Numeración Consecutiva y Requisitos DIAN

**Estado:** NO IMPLEMENTADO  
**Prioridad:** CRÍTICA  
**Tiempo estimado:** 1 semana

#### Descripción del Problema
No hay sistema de numeración consecutiva obligatoria por DIAN. Las ventas deben tener un consecutivo único sin saltos. Falta separación correcta de impuestos y propinas.

#### Requisitos DIAN Críticos

**Consecutivos**
- Numeración única y sin saltos por tipo de documento
- Función SQL con lock para thread-safety
- Soporte para prefijos (FV, NC, etc.)
- Separación por año y opcionalmente por mes
- Validación de integridad

**Separación de Impuestos y Propinas**
- Subtotal (base gravable)
- IVA 19% en Colombia
- Impoconsumo 8% en licores
- Propina SEPARADA (no es base gravable)
- Descuentos aplicados ANTES de impuestos
- Total calculado correctamente

**Impuestos por Producto**
- Tabla de relación producto-impuesto
- Configuración de qué impuestos aplica cada producto
- IVA, Impoconsumo, o ninguno
- Porcentajes configurables
- Estado activo/inactivo

**Cálculo de Impuestos**
- Método de cálculo de IVA por producto
- Método de cálculo de Impoconsumo por producto
- Suma de impuestos por venta
- Redondeo apropiado
- Validación de montos

#### Checklist de Implementación
- Implementar tabla consecutivos
- Crear función SQL thread-safe
- Validar ausencia de saltos
- Crear entidad ProductoImpuesto
- Asignar impuestos a productos
- Implementar cálculo correcto en VentaServicio
- Separar propinas del total gravable
- Mostrar desglose en ticket
- Exportar para contabilidad

---

## FASE 2: MÓDULOS DE ALTA PRIORIDAD

### 2.1 Seguridad Técnica Avanzada

**Estado:** PARCIAL  
**Prioridad:** ALTA  
**Tiempo estimado:** 1-2 semanas

#### Mejoras Requeridas

**Registro de Sesiones**
- Entidad SesionUsuario con control completo
- Token JWT almacenado
- IP cliente y dispositivo
- Navegador
- Fechas: inicio, último acceso, cierre
- Estado de sesión
- Actualización en cada request

**Bloqueo Avanzado**
- Bloqueo temporal (15 minutos) vs permanente
- Desbloqueo automático tras timeout
- Notificación al admin de bloqueos
- Opción de desbloqueo manual desde admin
- Fecha y tipo de bloqueo

**Firmas Digitales Administrativas**
- Confirmación con PIN admin en operaciones críticas
- Anulación de ventas
- Modificación de precios
- Eliminación de registros
- Cierre de caja con diferencia significativa

#### Checklist de Implementación
- Crear entidad SesionUsuario
- Registrar sesión en login
- Actualizar último acceso en requests
- Implementar logout con invalidación
- Bloqueo temporal con auto-desbloqueo
- Vista de sesiones activas (admin)
- Forzar cierre de sesión
- Confirmación admin en operaciones críticas
- Log de cambios sensibles

---

### 2.2 Sistema de Reportes

**Estado:** NO IMPLEMENTADO  
**Prioridad:** ALTA  
**Tiempo estimado:** 2 semanas

#### Reportes Críticos

**Reporte de Ventas Diarias**
- Total vendido por período
- Desglose por método de pago
- Desglose por categoría
- Productos más vendidos
- Ventas por hora (picos)

**Reporte de Caja**
- Aperturas y cierres
- Diferencias en arqueos
- Movimientos no relacionados con ventas

**Reporte de Inventario**
- Stock actual vs mínimo
- Productos con stock bajo
- Movimientos de inventario

**Reporte de Auditoría**
- Operaciones por usuario
- Operaciones por tipo
- Timeline de eventos

**Reporte de Rendimiento**
- Ventas por cajero
- Propinas por mesero
- Errores/anulaciones por usuario

#### Checklist de Implementación
- Crear servicio ReporteServicio
- Implementar reportes con queries optimizadas
- Endpoints REST /api/reportes/*
- Vista frontend con filtros
- Exportar a PDF
- Exportar a Excel/CSV
- Gráficos visuales (JavaFX Charts)

---

### 2.3 Sistema de Inventario Completo

**Estado:** BÁSICO (solo stock actual)  
**Prioridad:** ALTA  
**Tiempo estimado:** 2 semanas

#### Mejoras Requeridas

**Movimientos de Inventario**
- Entidad MovimientoInventario
- Tipos: compra, venta, ajuste, merma, devolución
- Cantidad (positivo/negativo)
- Stock anterior y nuevo
- Usuario responsable
- Timestamp
- Referencia a venta si aplica

**Descuento Automático**
- Al procesar venta, descontar stock
- Validar stock disponible antes de vender
- Registro automático de movimiento
- Manejo de productos sin control de stock

**Alertas de Stock Bajo**
- Job programado diario
- Query de productos con stock <= mínimo
- Notificación al admin
- Vista de alertas en dashboard

**Kardex**
- Reporte detallado por producto
- Movimientos en período específico
- Stock inicial y final
- Tipos de movimiento
- Usuario responsable

#### Checklist de Implementación
- Crear entidad MovimientoInventario
- Crear enum TipoMovimientoInventario
- Descuento automático al vender
- Validar stock antes de venta
- Servicio de ajustes (compras, mermas)
- Alertas de stock bajo
- Vista de movimientos
- Reporte Kardex
- Dashboard de stock bajo

---

### 2.4 Backup y Restore Automático

**Estado:** NO IMPLEMENTADO  
**Prioridad:** ALTA  
**Tiempo estimado:** 1 semana

#### Solución Requerida

**Backup Automático**
- Script bash para pg_dump
- Compresión con gzip
- Programación con cron (diario a las 3 AM)
- Limpieza de backups antiguos (mantener 30 días)
- Almacenamiento en directorio seguro

**Restore desde Interfaz**
- Servicio BackupServicio en backend
- Listado de backups disponibles
- Carga de archivo .dump
- Ejecución de pg_restore
- Validación de permisos admin
- Vista frontend de gestión

#### Checklist de Implementación
- Crear script backup-automatico.sh
- Configurar cron job
- Crear servicio BackupServicio
- Endpoint POST /api/backups/restaurar
- Endpoint GET /api/backups/listar
- Vista frontend (solo ADMIN)
- Descargar backup manualmente
- Restaurar backup seleccionado
- Validar permisos

---

## FASE 3: MÓDULOS OPCIONALES (PRIORIDAD MEDIA)

### 3.1 Contabilidad Doble Partida

**Estado:** NO IMPLEMENTADO  
**Prioridad:** MEDIA  
**Tiempo estimado:** 3-4 semanas

**Descripción:** Sistema contable completo con catálogo de cuentas, asientos automáticos, libros diario y mayor, balance general y estado de resultados. Puede delegarse a software externo mediante exportación.

---

### 3.2 Integración con Facturación Electrónica DIAN

**Estado:** NO IMPLEMENTADO  
**Prioridad:** MEDIA (si factura electrónicamente)  
**Tiempo estimado:** 4-6 semanas

**Descripción:** Integración con proveedores (Dataico, Siigo, Alegra). Requiere certificado digital, generación de XML, firma digital, envío a proveedor, recepción de CUFE. Puede dejarse para fase posterior si no es requerido.

---

### 3.3 Gestión de Comandas de Cocina

**Estado:** NO IMPLEMENTADO  
**Prioridad:** MEDIA  
**Tiempo estimado:** 2 semanas

**Descripción:** Impresión automática en cocina/barra, estados de preparación, notificaciones a meseros, panel de cocina con pedidos activos.

---

### 3.4 Gestión de Clientes y Fidelización

**Estado:** NO IMPLEMENTADO  
**Prioridad:** BAJA  
**Tiempo estimado:** 2-3 semanas

**Descripción:** CRUD de clientes, historial de compras, programa de puntos, descuentos personalizados, promociones.

---

## CRONOGRAMA SUGERIDO

### Sprint 1 (Semanas 1-2): Módulos Críticos Base
- **Semana 1**: Sistema de Ventas (entidades, consecutivos, servicio)
- **Semana 2**: Sistema de Auditoría (entidad, AOP, verificación)

### Sprint 2 (Semanas 3-4): Caja y Requisitos DIAN
- **Semana 3**: Sistema de Caja (apertura, cierre, movimientos)
- **Semana 4**: Requisitos DIAN (impuestos, propinas, frontend)

### Sprint 3 (Semanas 5-6): Seguridad y Reportes
- **Semana 5**: Seguridad avanzada (sesiones, bloqueos, firmas)
- **Semana 6**: Sistema de Reportes (ventas, caja, auditoría)

### Sprint 4 (Semanas 7-8): Inventario y Backup
- **Semana 7**: Inventario completo (movimientos, alertas, kardex)
- **Semana 8**: Backup/Restore y testing integral

### Sprint 5+ (Semanas 9+): Módulos Opcionales
- Contabilidad doble partida (si requerido)
- Facturación electrónica DIAN (si requerido)
- Comandas de cocina
- Gestión de clientes

---

## CHECKLIST GENERAL ANTES DE PRODUCCIÓN

### Backend
- Todos los módulos críticos implementados
- Tests unitarios con cobertura mínima 70%
- Tests de integración en endpoints críticos
- Manejo de errores consistente
- Logging configurado (INFO producción, DEBUG desarrollo)
- Variables de entorno para configuración sensible
- JWT con SECRET fuerte (mínimo 256 bits)
- HTTPS en producción
- CORS configurado
- Backup automático

### Frontend
- Todas las vistas críticas implementadas
- Tema luxury/premium consistente
- Teclado virtual funcional
- Manejo de errores con AlertaUtil
- Loading spinners
- Validación de inputs
- Confirmaciones en operaciones destructivas
- Impresión de tickets

### Base de Datos
- Migraciones Flyway aplicadas
- Índices en columnas frecuentes
- Foreign keys con ON DELETE apropiado
- Constraints de validación
- Backup probado
- Restore probado

### Seguridad
- Contraseñas hasheadas con BCrypt
- JWT con expiración apropiada (24h)
- Control de roles funcionando
- Bloqueo por intentos fallidos
- Auditoría en operaciones críticas
- Hashes de seguridad
- Sesiones rastreadas
- IP y dispositivo registrados

### Cumplimiento DIAN
- Consecutivos sin saltos
- Desglose correcto de impuestos
- Propinas separadas
- Tickets con datos requeridos
- Exportación para contabilidad
- Cierres de caja con arqueo
- Anulaciones con justificación

### Rendimiento
- Queries optimizadas
- Paginación en listados grandes
- Lazy loading JPA
- Connection pool configurado
- Frontend optimizado para hardware limitado

### Documentación
- README.md actualizado
- QUICKSTART.md actualizado
- Copilot-instructions.md actualizado
- Diagramas de arquitectura
- Guía de usuario básica

---

## RIESGOS Y MITIGACIONES

| Riesgo | Impacto | Probabilidad | Mitigación |
|--------|---------|--------------|------------|
| Pérdida de datos por hardware | CRÍTICO | MEDIA | Backup diario automático + offsite |
| Manipulación de registros | CRÍTICO | BAJA | Hashes de seguridad + auditoría inmutable |
| Inconsistencia en consecutivos | CRÍTICO | MEDIA | Función SQL thread-safe con lock |
| Diferencias en arqueo | ALTO | ALTA | Validaciones estrictas + logs |
| Errores en cálculo de impuestos | CRÍTICO | MEDIA | Tests exhaustivos + validación contable |
| Stock negativo por concurrencia | ALTO | MEDIA | Transacciones con lock pesimista |
| Sesiones no cerradas | MEDIO | ALTA | Timeout automático + cleanup job |
| Rendimiento lento | ALTO | ALTA | Optimizaciones CSS + índices DB |

---

## SOPORTE Y MANTENIMIENTO

### Tareas Continuas

**Diarias:**
- Verificar backups automáticos
- Revisar logs de errores críticos
- Verificar diferencias en cierres de caja

**Semanales:**
- Verificar integridad de auditoría
- Revisar usuarios bloqueados
- Analizar reportes de ventas

**Mensuales:**
- Actualizar dependencias de seguridad
- Revisar rendimiento de queries
- Limpieza de logs antiguos
- Actualizar documentación

**Anuales:**
- Actualizar porcentajes de impuestos
- Renovar certificados SSL/TLS
- Auditoría de seguridad completa
- Backup completo offsite

---

## FORMACIÓN DE USUARIOS

### Cajeros
- Apertura de caja con fondo inicial
- Procesamiento de ventas (múltiples métodos de pago)
- Propinas y descuentos
- Cierre de caja con arqueo
- Anulación de ventas con aprobación

### Meseros
- Login con PIN rápido
- Asignación de mesas
- Creación y modificación de pedidos
- Envío de pedidos a caja

### Administradores
- Gestión de usuarios y roles
- Gestión de productos y categorías
- Configuración de impuestos
- Reportes y análisis
- Auditoría y seguridad
- Backup y restore
- Desbloqueo de usuarios

---

## CONCLUSIÓN

Este roadmap define una ruta clara y priorizada para transformar Baryx en un sistema de producción completo, seguro y conforme con los requisitos legales de Colombia (DIAN).

**Prioridad inmediata:** Implementar los 4 módulos críticos de la Fase 1:
1. Sistema de Ventas y PLU
2. Sistema de Auditoría Inmutable
3. Sistema de Gestión de Caja
4. Numeración Consecutiva y Requisitos DIAN

Una vez completada la Fase 1, el sistema será funcional para uso en producción con las medidas de seguridad y trazabilidad necesarias.

Las fases posteriores (2 y 3) añaden funcionalidades avanzadas y mejoras de calidad, pero no son bloqueantes para el lanzamiento inicial.

---

**Última actualización:** 02 de febrero de 2026  
**Próxima revisión:** Al completar cada Sprint

El sistema actual permite crear pedidos pero NO los convierte en ventas. No hay registro de transacciones monetarias, métodos de pago, ni PLU (Price Look-Up / venta procesada).

#### Solución Requerida

**1.1.1 Entidad `Venta` (entidad inmutable)**
```java
@Entity
@Table(name = "ventas")
public class Venta extends EntidadBase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenta;
    
    // Numeración consecutiva OBLIGATORIA (DIAN)
    @Column(name = "numero_consecutivo", nullable = false, unique = true)
    private Long numeroConsecutivo;
    
    // Referencia al pedido original (inmutable después de crear)
    @ManyToOne
    @JoinColumn(name = "id_pedido", nullable = false, updatable = false)
    private Pedido pedido;
    
    @ManyToOne
    @JoinColumn(name = "id_cajero", nullable = false, updatable = false)
    private Usuario cajero;
    
    @ManyToOne
    @JoinColumn(name = "id_caja", nullable = false, updatable = false)
    private Caja caja;
    
    // Montos desagregados (requerido por DIAN)
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "iva", nullable = false, precision = 10, scale = 2)
    private BigDecimal iva; // 19% en Colombia (2026)
    
    @Column(name = "impoconsumo", precision = 10, scale = 2)
    private BigDecimal impoconsumo; // 8% en licores (si aplica)
    
    @Column(name = "propina", precision = 10, scale = 2)
    private BigDecimal propina; // Separado por DIAN
    
    @Column(name = "descuento", precision = 10, scale = 2)
    private BigDecimal descuento;
    
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    
    // Métodos de pago
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    private List<PagoVenta> pagos = new ArrayList<>();
    
    // Timestamp preciso al momento de facturar
    @Column(name = "fecha_venta", nullable = false, updatable = false)
    private LocalDateTime fechaVenta;
    
    // Estado de la venta (normal, anulada, nota crédito)
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoVenta estado; // COMPLETADA, ANULADA
    
    // Hash de seguridad (SHA-256 de datos críticos)
    @Column(name = "hash_seguridad", nullable = false, length = 64)
    private String hashSeguridad;
    
    // Razón de anulación (si aplica, con usuario que anuló)
    @Column(name = "razon_anulacion", length = 500)
    private String razonAnulacion;
    
    @ManyToOne
    @JoinColumn(name = "id_usuario_anulacion")
    private Usuario usuarioAnulacion;
    
    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;
}
```

**1.1.2 Entidad `PagoVenta` (relación muchos-a-uno con Venta)**
```java
@Entity
@Table(name = "pagos_venta")
public class PagoVenta {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPago;
    
    @ManyToOne
    @JoinColumn(name = "id_venta", nullable = false, updatable = false)
    private Venta venta;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false)
    private MetodoPago metodoPago; // EFECTIVO, TARJETA_CREDITO, TARJETA_DEBITO, TRANSFERENCIA, QR
    
    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
    
    // Datos específicos según método de pago
    @Column(name = "referencia_pago", length = 100)
    private String referenciaPago; // Número de transacción, voucher, etc.
    
    @Column(name = "ultimos_digitos_tarjeta", length = 4)
    private String ultimosDigitosTarjeta;
    
    @Column(name = "franquicia", length = 30)
    private String franquicia; // Visa, Mastercard, etc.
    
    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;
}
```

**1.1.3 Tabla de Numeración Consecutiva (control DIAN)**
```sql
CREATE TABLE consecutivos (
    id_consecutivo BIGSERIAL PRIMARY KEY,
    tipo_documento VARCHAR(50) NOT NULL, -- 'VENTA', 'FACTURA', 'NOTA_CREDITO'
    prefijo VARCHAR(10), -- Ej: 'FV', 'NC'
    numero_actual BIGINT NOT NULL DEFAULT 0,
    anio INTEGER NOT NULL,
    mes INTEGER,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_consecutivo UNIQUE (tipo_documento, prefijo, anio, mes)
);

-- Función para obtener siguiente consecutivo (thread-safe)
CREATE OR REPLACE FUNCTION obtener_siguiente_consecutivo(
    p_tipo_documento VARCHAR(50),
    p_prefijo VARCHAR(10),
    p_anio INTEGER,
    p_mes INTEGER DEFAULT NULL
)
RETURNS BIGINT AS $$
DECLARE
    v_siguiente BIGINT;
BEGIN
    -- Lock explícito para evitar race conditions
    UPDATE consecutivos
    SET numero_actual = numero_actual + 1
    WHERE tipo_documento = p_tipo_documento
      AND prefijo = p_prefijo
      AND anio = p_anio
      AND (mes = p_mes OR (mes IS NULL AND p_mes IS NULL))
      AND activo = TRUE
    RETURNING numero_actual INTO v_siguiente;
    
    IF v_siguiente IS NULL THEN
        -- Crear nuevo consecutivo si no existe
        INSERT INTO consecutivos (tipo_documento, prefijo, numero_actual, anio, mes)
        VALUES (p_tipo_documento, p_prefijo, 1, p_anio, p_mes)
        RETURNING numero_actual INTO v_siguiente;
    END IF;
    
    RETURN v_siguiente;
END;
$$ LANGUAGE plpgsql;
```

**1.1.4 Servicio `VentaServicio`**
```java
@Service
@Transactional
public class VentaServicioImpl implements VentaServicio {
    
    @Autowired
    private VentaRepositorio ventaRepositorio;
    
    @Autowired
    private PedidoRepositorio pedidoRepositorio;
    
    @Autowired
    private CajaRepositorio cajaRepositorio;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Procesa un pedido y genera una venta (PLU).
     * Esta operación es INMUTABLE: una vez creada, no se puede modificar.
     * Para correcciones se debe generar una Nota Crédito.
     */
    public VentaDto procesarVenta(CrearVentaDto dto) {
        // 1. Validar que la caja esté abierta
        Caja cajaAbierta = validarCajaAbierta(dto.getIdCaja());
        
        // 2. Obtener pedido y validar que esté activo
        Pedido pedido = pedidoRepositorio.findById(dto.getIdPedido())
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado"));
        
        if (!"ACTIVO".equals(pedido.getEstado())) {
            throw new ValidacionException("El pedido ya fue procesado");
        }
        
        // 3. Calcular montos desagregados
        BigDecimal subtotal = calcularSubtotal(pedido);
        BigDecimal iva = calcularIVA(subtotal);
        BigDecimal impoconsumo = calcularImpoconsumo(pedido);
        BigDecimal total = subtotal.add(iva).add(impoconsumo).add(dto.getPropina());
        
        // 4. Obtener consecutivo (thread-safe mediante función SQL)
        Long consecutivo = obtenerSiguienteConsecutivo("VENTA", "V", LocalDate.now().getYear());
        
        // 5. Crear entidad Venta
        Venta venta = Venta.builder()
            .numeroConsecutivo(consecutivo)
            .pedido(pedido)
            .cajero(cajaAbierta.getUsuarioApertura())
            .caja(cajaAbierta)
            .subtotal(subtotal)
            .iva(iva)
            .impoconsumo(impoconsumo)
            .propina(dto.getPropina())
            .descuento(dto.getDescuento())
            .total(total)
            .fechaVenta(LocalDateTime.now())
            .estado(EstadoVenta.COMPLETADA)
            .build();
        
        // 6. Agregar pagos
        for (PagoDto pagoDto : dto.getPagos()) {
            PagoVenta pago = new PagoVenta();
            pago.setVenta(venta);
            pago.setMetodoPago(pagoDto.getMetodoPago());
            pago.setMonto(pagoDto.getMonto());
            pago.setReferenciaPago(pagoDto.getReferencia());
            pago.setFechaPago(LocalDateTime.now());
            venta.getPagos().add(pago);
        }
        
        // 7. Validar que la suma de pagos = total
        BigDecimal sumaPagos = venta.getPagos().stream()
            .map(PagoVenta::getMonto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (sumaPagos.compareTo(total) != 0) {
            throw new ValidacionException("La suma de pagos no coincide con el total");
        }
        
        // 8. Generar hash de seguridad
        venta.setHashSeguridad(generarHashSeguridad(venta));
        
        // 9. Guardar venta
        venta = ventaRepositorio.save(venta);
        
        // 10. Actualizar pedido como facturado
        pedido.setEstado("FACTURADO");
        pedidoRepositorio.save(pedido);
        
        // 11. Actualizar mesa como disponible
        Mesa mesa = pedido.getMesa();
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        mesa.setIdMesero(null);
        
        // 12. Registrar en auditoría
        registrarAuditoria("VENTA_CREADA", venta);
        
        return mapearADto(venta);
    }
    
    /**
     * Genera hash SHA-256 de datos críticos de la venta.
     * Permite verificar que no hubo manipulación posterior.
     */
    private String generarHashSeguridad(Venta venta) {
        String datos = String.format("%d|%s|%s|%s|%s",
            venta.getNumeroConsecutivo(),
            venta.getFechaVenta().toString(),
            venta.getTotal().toString(),
            venta.getCajero().getIdUsuario(),
            venta.getPedido().getIdPedido());
        
        return DigestUtils.sha256Hex(datos);
    }
    
    private Long obtenerSiguienteConsecutivo(String tipo, String prefijo, int anio) {
        return jdbcTemplate.queryForObject(
            "SELECT obtener_siguiente_consecutivo(?, ?, ?, NULL)",
            Long.class,
            tipo, prefijo, anio
        );
    }
}
```

**1.1.5 Frontend: Vista de Facturación (Cajero)**

Crear vista `facturacion-cobro.fxml`:
- Grid de métodos de pago (Efectivo, Tarjeta, Mixto)
- Input de monto recibido con cálculo de cambio
- Input de propina (opcional)
- Resumen de cuenta con desglose IVA
- Botón "PROCESAR VENTA" (dorado, prominente)
- Impresión de ticket o factura

**Checklist Módulo Ventas:**
- [ ] Crear entidades: Venta, PagoVenta
- [ ] Crear enum EstadoVenta, MetodoPago
- [ ] Crear tabla consecutivos + función SQL
- [ ] Crear VentaServicio con método procesarVenta()
- [ ] Crear VentaController con endpoint POST /api/ventas
- [ ] Implementar cálculo de IVA (19%) e impoconsumo (8% licores)
- [ ] Implementar generación de hash de seguridad (SHA-256)
- [ ] Crear vista frontend facturacion-cobro.fxml
- [ ] Integrar impresión de ticket (opcional en Fase 1)
- [ ] Tests unitarios de VentaServicio
- [ ] Validar que pedidos facturados no se puedan modificar

---

### 1.2 📝 Sistema de Auditoría Inmutable

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🔴 CRÍTICA  
**Tiempo estimado:** 1-2 semanas

#### Problema
No existe registro de auditoría de las operaciones. No hay trazabilidad de quién hizo qué y cuándo.

#### Solución Requerida

**1.2.1 Entidad `RegistroAuditoria` (INMUTABLE)**
```java
@Entity
@Table(name = "auditoria", indexes = {
    @Index(name = "idx_auditoria_usuario", columnList = "id_usuario"),
    @Index(name = "idx_auditoria_fecha", columnList = "fecha_hora"),
    @Index(name = "idx_auditoria_tipo", columnList = "tipo_operacion"),
    @Index(name = "idx_auditoria_tabla", columnList = "tabla_afectada")
})
public class RegistroAuditoria {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAuditoria;
    
    // Usuario que realizó la acción
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false, updatable = false)
    private Usuario usuario;
    
    // Tipo de operación
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, length = 50, updatable = false)
    private TipoOperacion tipoOperacion; // CREAR, ACTUALIZAR, ELIMINAR, LOGIN, LOGOUT, VENTA, ANULACION, etc.
    
    // Tabla o módulo afectado
    @Column(name = "tabla_afectada", nullable = false, length = 100, updatable = false)
    private String tablaAfectada;
    
    // ID del registro afectado
    @Column(name = "id_registro", updatable = false)
    private Long idRegistro;
    
    // Descripción de la operación
    @Column(name = "descripcion", length = 1000, updatable = false)
    private String descripcion;
    
    // Datos antes y después (JSON)
    @Column(name = "datos_anteriores", columnDefinition = "TEXT", updatable = false)
    private String datosAnteriores;
    
    @Column(name = "datos_nuevos", columnDefinition = "TEXT", updatable = false)
    private String datosNuevos;
    
    // Información de la sesión
    @Column(name = "ip_cliente", length = 45, updatable = false)
    private String ipCliente;
    
    @Column(name = "dispositivo", length = 200, updatable = false)
    private String dispositivo;
    
    // Timestamp preciso
    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;
    
    // Hash de seguridad del registro (inmutabilidad)
    @Column(name = "hash", nullable = false, length = 64, updatable = false)
    private String hash;
    
    // Hash del registro anterior (blockchain-like)
    @Column(name = "hash_anterior", length = 64, updatable = false)
    private String hashAnterior;
}
```

**1.2.2 Servicio `AuditoriaServicio`**
```java
@Service
public class AuditoriaServicioImpl implements AuditoriaServicio {
    
    @Autowired
    private AuditoriaRepositorio auditoriaRepositorio;
    
    @Autowired
    private ObjectMapper objectMapper; // Para serializar a JSON
    
    /**
     * Registra una operación en el log de auditoría.
     * Este método se invoca automáticamente desde AOP o manualmente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(RegistroAuditoriaDto dto) {
        // Obtener último hash para blockchain-like
        String hashAnterior = obtenerUltimoHash();
        
        RegistroAuditoria registro = RegistroAuditoria.builder()
            .usuario(dto.getUsuario())
            .tipoOperacion(dto.getTipoOperacion())
            .tablaAfectada(dto.getTablaAfectada())
            .idRegistro(dto.getIdRegistro())
            .descripcion(dto.getDescripcion())
            .datosAnteriores(serializarJson(dto.getDatosAnteriores()))
            .datosNuevos(serializarJson(dto.getDatosNuevos()))
            .ipCliente(dto.getIpCliente())
            .dispositivo(dto.getDispositivo())
            .fechaHora(LocalDateTime.now())
            .hashAnterior(hashAnterior)
            .build();
        
        // Generar hash del registro actual
        registro.setHash(generarHash(registro));
        
        auditoriaRepositorio.save(registro);
    }
    
    /**
     * Verifica la integridad de la cadena de auditoría.
     * Detecta si algún registro fue manipulado.
     */
    public ResultadoVerificacionDto verificarIntegridad() {
        List<RegistroAuditoria> registros = auditoriaRepositorio.findAllByOrderByIdAuditoriaAsc();
        
        String hashAnterior = null;
        List<Long> registrosCorruptos = new ArrayList<>();
        
        for (RegistroAuditoria registro : registros) {
            // Verificar hash del registro
            String hashCalculado = generarHash(registro);
            if (!hashCalculado.equals(registro.getHash())) {
                registrosCorruptos.add(registro.getIdAuditoria());
            }
            
            // Verificar encadenamiento
            if (hashAnterior != null && !hashAnterior.equals(registro.getHashAnterior())) {
                registrosCorruptos.add(registro.getIdAuditoria());
            }
            
            hashAnterior = registro.getHash();
        }
        
        return ResultadoVerificacionDto.builder()
            .integro(registrosCorruptos.isEmpty())
            .registrosCorruptos(registrosCorruptos)
            .totalRegistros(registros.size())
            .build();
    }
    
    private String generarHash(RegistroAuditoria registro) {
        String datos = String.format("%d|%s|%s|%s|%s|%s",
            registro.getUsuario().getIdUsuario(),
            registro.getTipoOperacion(),
            registro.getFechaHora().toString(),
            registro.getDescripcion(),
            registro.getDatosNuevos(),
            registro.getHashAnterior());
        
        return DigestUtils.sha256Hex(datos);
    }
    
    private String obtenerUltimoHash() {
        return auditoriaRepositorio.findTopByOrderByIdAuditoriaDesc()
            .map(RegistroAuditoria::getHash)
            .orElse("GENESIS");
    }
}
```

**1.2.3 Aspect AOP para Auditoría Automática**
```java
@Aspect
@Component
public class AuditoriaAspect {
    
    @Autowired
    private AuditoriaServicio auditoriaServicio;
    
    @Autowired
    private HttpServletRequest request;
    
    /**
     * Intercepta métodos anotados con @Auditable
     */
    @Around("@annotation(auditable)")
    public Object auditarOperacion(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // Obtener usuario autenticado
        Usuario usuario = obtenerUsuarioActual();
        
        // Obtener datos antes de la operación (si es UPDATE)
        Object datosAnteriores = null;
        if (auditable.tipo() == TipoOperacion.ACTUALIZAR) {
            datosAnteriores = obtenerDatosAnteriores(joinPoint);
        }
        
        // Ejecutar operación
        Object resultado = joinPoint.proceed();
        
        // Registrar en auditoría
        RegistroAuditoriaDto dto = RegistroAuditoriaDto.builder()
            .usuario(usuario)
            .tipoOperacion(auditable.tipo())
            .tablaAfectada(auditable.tabla())
            .idRegistro(extraerIdResultado(resultado))
            .descripcion(auditable.descripcion())
            .datosAnteriores(datosAnteriores)
            .datosNuevos(resultado)
            .ipCliente(request.getRemoteAddr())
            .dispositivo(request.getHeader("User-Agent"))
            .build();
        
        auditoriaServicio.registrar(dto);
        
        return resultado;
    }
}
```

**1.2.4 Anotación @Auditable**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    TipoOperacion tipo();
    String tabla();
    String descripcion() default "";
}

// Uso en servicios:
@Auditable(tipo = TipoOperacion.VENTA, tabla = "ventas", descripcion = "Venta procesada")
public VentaDto procesarVenta(CrearVentaDto dto) {
    // ...
}
```

**Checklist Módulo Auditoría:**
- [ ] Crear entidad RegistroAuditoria
- [ ] Crear enum TipoOperacion
- [ ] Crear AuditoriaServicio con método registrar()
- [ ] Implementar generación de hash y encadenamiento (blockchain-like)
- [ ] Crear AuditoriaAspect con AOP para auditoría automática
- [ ] Crear anotación @Auditable
- [ ] Aplicar @Auditable en servicios críticos (Venta, Usuario, Producto, Pedido)
- [ ] Crear endpoint GET /api/auditoria (solo ADMIN)
- [ ] Crear método verificarIntegridad() para detectar manipulaciones
- [ ] Vista frontend de logs de auditoría (solo ADMIN)
- [ ] Filtros por usuario, fecha, tipo de operación
- [ ] Exportar logs a CSV/PDF

---

### 1.3 💰 Sistema de Gestión de Caja

**Estado:** ⚠️ PARCIAL (tabla existe, lógica NO)  
**Prioridad:** 🔴 CRÍTICA  
**Tiempo estimado:** 1-2 semanas

#### Problema
La tabla `cajas` existe en la base de datos pero no hay lógica implementada. No se puede abrir, cerrar ni hacer arqueo de caja.

#### Solución Requerida

**1.3.1 Entidad `Caja` (completar)**
```java
@Entity
@Table(name = "cajas")
public class Caja extends EntidadBase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCaja;
    
    @Column(name = "numero_caja", unique = true, nullable = false)
    private String numeroCaja;
    
    @ManyToOne
    @JoinColumn(name = "id_usuario_apertura", nullable = false)
    private Usuario usuarioApertura;
    
    @ManyToOne
    @JoinColumn(name = "id_usuario_cierre")
    private Usuario usuarioCierre;
    
    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;
    
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
    
    @Column(name = "fondo_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal fondoInicial;
    
    // Montos al cierre
    @Column(name = "total_efectivo", precision = 10, scale = 2)
    private BigDecimal totalEfectivo;
    
    @Column(name = "total_tarjeta", precision = 10, scale = 2)
    private BigDecimal totalTarjeta;
    
    @Column(name = "total_otros", precision = 10, scale = 2)
    private BigDecimal totalOtros;
    
    @Column(name = "total_ventas", precision = 10, scale = 2)
    private BigDecimal totalVentas;
    
    // Arqueo físico
    @Column(name = "efectivo_contado", precision = 10, scale = 2)
    private BigDecimal efectivoContado;
    
    @Column(name = "diferencia", precision = 10, scale = 2)
    private BigDecimal diferencia; // efectivoContado - (fondoInicial + totalEfectivo)
    
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoCaja estado; // ABIERTA, CERRADA
    
    @Column(name = "observaciones", length = 500)
    private String observaciones;
}
```

**1.3.2 Entidad `MovimientoCaja` (ingresos/egresos no relacionados con ventas)**
```java
@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMovimiento;
    
    @ManyToOne
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoMovimiento tipo; // INGRESO, EGRESO
    
    @Column(name = "concepto", nullable = false, length = 200)
    private String concepto;
    
    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
    
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    
    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDateTime fechaMovimiento;
    
    @Column(name = "observaciones", length = 500)
    private String observaciones;
}
```

**1.3.3 Servicio `CajaServicio`**
```java
@Service
@Transactional
public class CajaServicioImpl implements CajaServicio {
    
    /**
     * Abre una caja con fondo inicial.
     * Solo puede haber una caja abierta por terminal.
     */
    @Auditable(tipo = TipoOperacion.APERTURA_CAJA, tabla = "cajas")
    public CajaDto abrirCaja(AperturaCajaDto dto) {
        // Validar que no haya otra caja abierta
        Optional<Caja> cajaAbierta = cajaRepositorio.findByNumeroCajaAndEstado(
            dto.getNumeroCaja(), EstadoCaja.ABIERTA);
        
        if (cajaAbierta.isPresent()) {
            throw new ValidacionException("Ya existe una caja abierta para este terminal");
        }
        
        Caja caja = Caja.builder()
            .numeroCaja(dto.getNumeroCaja())
            .usuarioApertura(obtenerUsuarioActual())
            .fechaApertura(LocalDateTime.now())
            .fondoInicial(dto.getFondoInicial())
            .estado(EstadoCaja.ABIERTA)
            .build();
        
        caja = cajaRepositorio.save(caja);
        
        logger.info("Caja {} abierta por usuario {} con fondo inicial {}",
            caja.getNumeroCaja(), caja.getUsuarioApertura().getNombreUsuario(), caja.getFondoInicial());
        
        return mapearADto(caja);
    }
    
    /**
     * Cierra una caja con arqueo físico.
     * Calcula diferencias y genera reporte.
     */
    @Auditable(tipo = TipoOperacion.CIERRE_CAJA, tabla = "cajas")
    public CajaDto cerrarCaja(CierreCajaDto dto) {
        Caja caja = cajaRepositorio.findById(dto.getIdCaja())
            .orElseThrow(() -> new RecursoNoEncontradoException("Caja no encontrada"));
        
        if (caja.getEstado() == EstadoCaja.CERRADA) {
            throw new ValidacionException("La caja ya está cerrada");
        }
        
        // Calcular totales de ventas en esta caja
        BigDecimal totalEfectivo = calcularTotalPorMetodoPago(caja, MetodoPago.EFECTIVO);
        BigDecimal totalTarjeta = calcularTotalPorMetodoPago(caja, MetodoPago.TARJETA_CREDITO, MetodoPago.TARJETA_DEBITO);
        BigDecimal totalOtros = calcularTotalPorMetodoPago(caja, MetodoPago.TRANSFERENCIA, MetodoPago.QR);
        BigDecimal totalVentas = totalEfectivo.add(totalTarjeta).add(totalOtros);
        
        // Calcular diferencia
        BigDecimal efectivoEsperado = caja.getFondoInicial().add(totalEfectivo);
        BigDecimal diferencia = dto.getEfectivoContado().subtract(efectivoEsperado);
        
        // Actualizar caja
        caja.setUsuarioCierre(obtenerUsuarioActual());
        caja.setFechaCierre(LocalDateTime.now());
        caja.setTotalEfectivo(totalEfectivo);
        caja.setTotalTarjeta(totalTarjeta);
        caja.setTotalOtros(totalOtros);
        caja.setTotalVentas(totalVentas);
        caja.setEfectivoContado(dto.getEfectivoContado());
        caja.setDiferencia(diferencia);
        caja.setObservaciones(dto.getObservaciones());
        caja.setEstado(EstadoCaja.CERRADA);
        
        caja = cajaRepositorio.save(caja);
        
        // Alertar si hay diferencia significativa (> $5000 COP)
        if (Math.abs(diferencia.doubleValue()) > 5000) {
            logger.warn("Caja {} cerrada con diferencia significativa: {}",
                caja.getNumeroCaja(), diferencia);
        }
        
        return mapearADto(caja);
    }
    
    /**
     * Registra un movimiento de ingreso/egreso no relacionado con ventas.
     * Ej: Retiro de efectivo, pago a proveedor, propina extra, etc.
     */
    @Auditable(tipo = TipoOperacion.MOVIMIENTO_CAJA, tabla = "movimientos_caja")
    public MovimientoCajaDto registrarMovimiento(CrearMovimientoCajaDto dto) {
        Caja caja = validarCajaAbierta(dto.getIdCaja());
        
        MovimientoCaja movimiento = MovimientoCaja.builder()
            .caja(caja)
            .tipo(dto.getTipo())
            .concepto(dto.getConcepto())
            .monto(dto.getMonto())
            .usuario(obtenerUsuarioActual())
            .fechaMovimiento(LocalDateTime.now())
            .observaciones(dto.getObservaciones())
            .build();
        
        movimiento = movimientoCajaRepositorio.save(movimiento);
        
        return mapearADto(movimiento);
    }
}
```

**1.3.4 Frontend: Vistas de Caja**

**Vista 1: Apertura de Caja** (`apertura-caja.fxml`)
- Input de número de caja (terminal)
- Input de fondo inicial (efectivo)
- Botón "ABRIR CAJA" (dorado)

**Vista 2: Cierre de Caja** (`cierre-caja.fxml`)
- Resumen de ventas del día
- Desglose por método de pago
- Input de efectivo contado físicamente
- Cálculo automático de diferencia
- Indicador visual si hay diferencia (rojo/verde)
- Input de observaciones
- Botón "CERRAR CAJA" (requiere confirmación)

**Vista 3: Movimientos de Caja** (`movimientos-caja.fxml`)
- Lista de movimientos del día
- Botón "Nuevo Movimiento" (ingreso/egreso)
- Filtros por tipo y fecha

**Checklist Módulo Caja:**
- [ ] Completar entidad Caja (si falta algo)
- [ ] Crear entidad MovimientoCaja
- [ ] Crear enum EstadoCaja, TipoMovimiento
- [ ] Crear CajaServicio con abrirCaja(), cerrarCaja(), registrarMovimiento()
- [ ] Crear CajaController con endpoints REST
- [ ] Crear vistas frontend: apertura-caja, cierre-caja, movimientos-caja
- [ ] Validar que solo haya una caja abierta por terminal
- [ ] Validar que las ventas solo se procesen si hay caja abierta
- [ ] Generar reporte PDF de cierre de caja
- [ ] Tests unitarios de CajaServicio

---

### 1.4 🧾 Numeración Consecutiva y Requisitos DIAN

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🔴 CRÍTICA  
**Tiempo estimado:** 1 semana

#### Problema
No hay sistema de numeración consecutiva obligatoria por DIAN. Las ventas deben tener un consecutivo único y sin saltos.

#### Solución Requerida

Ya se incluyó en 1.1.3 la tabla `consecutivos` y la función SQL `obtener_siguiente_consecutivo()`.

**Requisitos adicionales DIAN:**

**1.4.1 Separación de Impuestos y Propinas**

Toda venta debe desglosar:
- **Subtotal** (base gravable)
- **IVA** (19% en Colombia, 2026)
- **Impoconsumo** (8% en licores, si aplica)
- **Propina** (SEPARADA, no es base gravable)
- **Descuentos** (aplicar ANTES de impuestos)
- **Total**

Ejemplo:
```
Producto A: $10,000
Producto B (licor): $15,000
----------------------------
Subtotal:        $25,000
IVA (19%):        $4,750
Impoconsumo (8%): $1,200 (solo licor)
Propina (10%):    $2,500
Descuento:           $0
----------------------------
TOTAL:           $33,450
```

**1.4.2 Entidad `ProductoImpuesto` (relación producto-impuesto)**

Algunos productos tienen IVA (19%), otros IVA + Impoconsumo (8%), otros 0%.

```java
@Entity
@Table(name = "productos_impuestos")
public class ProductoImpuesto {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idProductoImpuesto;
    
    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_impuesto", nullable = false)
    private TipoImpuesto tipoImpuesto; // IVA, IMPOCONSUMO
    
    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje; // 19.00 para IVA, 8.00 para impoconsumo
    
    @Column(name = "activo", nullable = false)
    private Boolean activo;
}
```

Cada producto debe tener configurado qué impuestos aplica.

**1.4.3 Método de cálculo de impuestos en `VentaServicio`**

```java
private BigDecimal calcularIVA(Pedido pedido) {
    return pedido.getLineas().stream()
        .map(linea -> {
            Producto producto = linea.getProducto();
            BigDecimal porcentajeIVA = obtenerPorcentajeImpuesto(producto, TipoImpuesto.IVA);
            return linea.getPrecioUnitario()
                .multiply(porcentajeIVA)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}

private BigDecimal calcularImpoconsumo(Pedido pedido) {
    return pedido.getLineas().stream()
        .map(linea -> {
            Producto producto = linea.getProducto();
            BigDecimal porcentajeImpo = obtenerPorcentajeImpuesto(producto, TipoImpuesto.IMPOCONSUMO);
            return linea.getPrecioUnitario()
                .multiply(porcentajeImpo)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

**Checklist Requisitos DIAN:**
- [ ] Implementar tabla consecutivos con función thread-safe
- [ ] Validar que no haya saltos en consecutivos
- [ ] Crear entidad ProductoImpuesto
- [ ] Asignar impuestos a cada producto (IVA, Impoconsumo, ninguno)
- [ ] Implementar cálculo correcto de impuestos en VentaServicio
- [ ] Separar propinas del total gravable
- [ ] Mostrar desglose en ticket/factura impresa
- [ ] Exportar ventas con desglose para contabilidad

---

## 🎯 FASE 2: MÓDULOS DE ALTA PRIORIDAD

### 2.1 🔐 Seguridad Técnica Avanzada

**Estado:** ⚠️ PARCIAL  
**Prioridad:** 🟡 ALTA  
**Tiempo estimado:** 1-2 semanas

#### Mejoras Requeridas

**2.1.1 Registro de IP y Dispositivo**

Ya está parcialmente implementado en auditoría. Ampliar:

```java
@Entity
@Table(name = "sesiones_usuario")
public class SesionUsuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSesion;
    
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    
    @Column(name = "token_jwt", nullable = false, unique = true, length = 500)
    private String tokenJwt;
    
    @Column(name = "ip_cliente", nullable = false)
    private String ipCliente;
    
    @Column(name = "dispositivo")
    private String dispositivo;
    
    @Column(name = "navegador")
    private String navegador;
    
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    
    @Column(name = "fecha_ultimo_acceso")
    private LocalDateTime fechaUltimoAcceso;
    
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoSesion estado; // ACTIVA, EXPIRADA, CERRADA_MANUALMENTE, FORZADA_CIERRE
    
    @Column(name = "activa", nullable = false)
    private Boolean activa;
}
```

Registrar sesión en login y actualizarla en cada request mediante filtro JWT.

**2.1.2 Bloqueo Avanzado por Intentos Fallidos**

Ya implementado (3 intentos), pero añadir:
- Bloqueo temporal (15 minutos) en lugar de bloqueo permanente
- Notificación al admin si un usuario es bloqueado
- Opción de desbloquear desde panel admin

```java
@Column(name = "fecha_bloqueo")
private LocalDateTime fechaBloqueo;

@Column(name = "tipo_bloqueo")
@Enumerated(EnumType.STRING)
private TipoBloqueo tipoBloqueo; // TEMPORAL, PERMANENTE

// En AutenticacionServicio:
if (usuario.getBloqueado()) {
    // Si es temporal y han pasado 15 minutos, desbloquear
    if (usuario.getTipoBloqueo() == TipoBloqueo.TEMPORAL) {
        if (ChronoUnit.MINUTES.between(usuario.getFechaBloqueo(), LocalDateTime.now()) >= 15) {
            usuario.setBloqueado(false);
            usuario.setIntentosFallidos(0);
            usuario.setFechaBloqueo(null);
            usuarioRepositorio.save(usuario);
        } else {
            throw new ValidacionException("Usuario bloqueado temporalmente. Intente en 15 minutos.");
        }
    } else {
        throw new ValidacionException("Usuario bloqueado permanentemente. Contacte al administrador.");
    }
}
```

**2.1.3 Firmas Digitales Internas**

Ya implementado mediante hashes SHA-256 en Venta y Auditoría.

Adicional: Implementar firma digital del administrador en operaciones críticas:
- Anulación de ventas
- Modificación de precios
- Eliminación de registros
- Cierre de caja con diferencia > $10,000

```java
// Requiere PIN o contraseña del admin para confirmar
public void anularVenta(Long idVenta, String pinAdmin, String razon) {
    // Validar PIN del admin
    Usuario admin = validarPinAdmin(pinAdmin);
    
    // Proceder con anulación...
}
```

**Checklist Seguridad Avanzada:**
- [ ] Crear entidad SesionUsuario
- [ ] Registrar sesión en login con IP y dispositivo
- [ ] Actualizar fecha_ultimo_acceso en cada request
- [ ] Implementar logout que invalida sesión
- [ ] Implementar bloqueo temporal (15 min) en lugar de permanente
- [ ] Vista de sesiones activas para admin
- [ ] Opción de forzar cierre de sesión desde admin
- [ ] Requerir confirmación con PIN admin en operaciones críticas
- [ ] Log de todos los cambios de configuración sensible

---

### 2.2 📊 Sistema de Reportes

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🟡 ALTA  
**Tiempo estimado:** 2 semanas

#### Reportes Requeridos

**2.2.1 Reporte de Ventas Diarias**
- Total vendido por día
- Desglose por método de pago
- Desglose por categoría de producto
- Productos más vendidos
- Ventas por hora (picos de actividad)

**2.2.2 Reporte de Caja**
- Detalle de aperturas y cierres
- Diferencias en arqueos
- Movimientos no relacionados con ventas

**2.2.3 Reporte de Inventario**
- Stock actual vs stock mínimo
- Productos con stock bajo (alertas)
- Movimientos de inventario

**2.2.4 Reporte de Auditoría**
- Operaciones por usuario
- Operaciones por tipo
- Timeline de eventos críticos

**2.2.5 Reporte de Rendimiento de Usuarios**
- Ventas por cajero
- Propinas por mesero
- Errores/anulaciones por usuario

**Implementación Sugerida:**

```java
@Service
public class ReporteServicio {
    
    public ReporteVentasDto generarReporteVentas(LocalDate fechaInicio, LocalDate fechaFin) {
        // Query agregado de ventas
        List<Venta> ventas = ventaRepositorio.findByFechaVentaBetween(
            fechaInicio.atStartOfDay(), 
            fechaFin.atTime(23, 59, 59));
        
        BigDecimal totalVentas = ventas.stream()
            .map(Venta::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Agrupar por método de pago
        Map<MetodoPago, BigDecimal> ventasPorMetodo = // ...
        
        // Productos más vendidos
        List<ProductoVentaDto> productosMasVendidos = // ...
        
        return ReporteVentasDto.builder()
            .fechaInicio(fechaInicio)
            .fechaFin(fechaFin)
            .totalVentas(totalVentas)
            .cantidadVentas(ventas.size())
            .ventasPorMetodo(ventasPorMetodo)
            .productosMasVendidos(productosMasVendidos)
            .build();
    }
    
    /**
     * Exporta reporte a PDF
     */
    public byte[] exportarReportePDF(ReporteVentasDto reporte) {
        // Usar iText o Apache PDFBox
        // ...
    }
}
```

**Checklist Reportes:**
- [ ] Crear servicio ReporteServicio
- [ ] Implementar reporte de ventas diarias con queries optimizadas
- [ ] Implementar reporte de caja
- [ ] Implementar reporte de inventario
- [ ] Implementar reporte de auditoría
- [ ] Crear endpoints REST /api/reportes/*
- [ ] Vista frontend de reportes con filtros
- [ ] Exportar reportes a PDF
- [ ] Exportar reportes a Excel (CSV)
- [ ] Gráficos visuales (barras, líneas) usando JavaFX Charts

---

### 2.3 📦 Sistema de Inventario Completo

**Estado:** ⚠️ BÁSICO (solo stock actual)  
**Prioridad:** 🟡 ALTA  
**Tiempo estimado:** 2 semanas

#### Mejoras Requeridas

**2.3.1 Movimientos de Inventario**

```java
@Entity
@Table(name = "movimientos_inventario")
public class MovimientoInventario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMovimiento;
    
    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false)
    private TipoMovimientoInventario tipo; // COMPRA, VENTA, AJUSTE, MERMA, DEVOLUCION
    
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad; // Positivo para entradas, negativo para salidas
    
    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;
    
    @Column(name = "stock_nuevo", nullable = false)
    private Integer stockNuevo;
    
    @Column(name = "precio_unitario", precision = 10, scale = 2)
    private BigDecimal precioUnitario; // Para compras
    
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    
    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDateTime fechaMovimiento;
    
    @Column(name = "observaciones", length = 500)
    private String observaciones;
    
    // Referencia a venta (si el movimiento es por venta)
    @ManyToOne
    @JoinColumn(name = "id_venta")
    private Venta venta;
}
```

**2.3.2 Descuento automático de stock al vender**

```java
// En VentaServicio.procesarVenta():
for (LineaPedido linea : pedido.getLineas()) {
    Producto producto = linea.getProducto();
    
    if (producto.getRequiereStock()) {
        // Descontar del inventario
        int stockAnterior = producto.getStockActual();
        int stockNuevo = stockAnterior - 1; // Asumiendo cantidad = 1 por línea
        
        if (stockNuevo < 0) {
            throw new ValidacionException("Stock insuficiente para producto: " + producto.getNombre());
        }
        
        producto.setStockActual(stockNuevo);
        productoRepositorio.save(producto);
        
        // Registrar movimiento de inventario
        registrarMovimientoInventario(producto, -1, stockAnterior, stockNuevo, 
            TipoMovimientoInventario.VENTA, venta, usuario);
    }
}
```

**2.3.3 Alertas de Stock Bajo**

```java
@Scheduled(cron = "0 0 8 * * *") // Ejecutar diariamente a las 8 AM
public void verificarStockBajo() {
    List<Producto> productosConStockBajo = productoRepositorio.findByStockBajo();
    
    if (!productosConStockBajo.isEmpty()) {
        // Enviar notificación al admin
        notificarStockBajo(productosConStockBajo);
    }
}

// En ProductoRepositorio:
@Query("SELECT p FROM Producto p WHERE p.requiereStock = true AND p.stockActual <= p.stockMinimo AND p.activo = true")
List<Producto> findByStockBajo();
```

**2.3.4 Kardex de Inventario**

Reporte detallado de movimientos de un producto específico:

```java
public KardexDto generarKardex(Long idProducto, LocalDate fechaInicio, LocalDate fechaFin) {
    Producto producto = productoRepositorio.findById(idProducto)
        .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado"));
    
    List<MovimientoInventario> movimientos = movimientoInventarioRepositorio
        .findByProductoAndFechaMovimientoBetween(producto, fechaInicio.atStartOfDay(), fechaFin.atTime(23, 59, 59));
    
    return KardexDto.builder()
        .producto(producto)
        .movimientos(movimientos)
        .stockInicial(calcularStockInicial(producto, fechaInicio))
        .stockFinal(producto.getStockActual())
        .build();
}
```

**Checklist Inventario:**
- [ ] Crear entidad MovimientoInventario
- [ ] Crear enum TipoMovimientoInventario
- [ ] Implementar descuento automático de stock al vender
- [ ] Validar stock antes de procesar venta
- [ ] Crear servicio de ajustes de inventario (compras, mermas, devoluciones)
- [ ] Implementar alertas de stock bajo
- [ ] Crear vista de movimientos de inventario
- [ ] Generar reporte Kardex por producto
- [ ] Vista de productos con stock bajo (dashboard admin)

---

### 2.4 💾 Backup y Restore Automático

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🟡 ALTA  
**Tiempo estimado:** 1 semana

#### Solución Requerida

**2.4.1 Backup Automático Diario**

```bash
#!/bin/bash
# backup-automatico.sh

FECHA=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/var/baryx/backups"
DB_NAME="baryx_bar1"
DB_USER="baryx_user"

# Crear directorio de backups si no existe
mkdir -p $BACKUP_DIR

# Ejecutar backup con pg_dump
pg_dump -U $DB_USER -d $DB_NAME -F c -f "$BACKUP_DIR/baryx_backup_$FECHA.dump"

# Comprimir backup
gzip "$BACKUP_DIR/baryx_backup_$FECHA.dump"

# Eliminar backups antiguos (mantener últimos 30 días)
find $BACKUP_DIR -name "baryx_backup_*.dump.gz" -mtime +30 -delete

echo "Backup completado: baryx_backup_$FECHA.dump.gz"
```

Configurar en crontab:
```
# Backup diario a las 3 AM
0 3 * * * /path/to/backup-automatico.sh
```

**2.4.2 Restore desde Interfaz Admin**

```java
@Service
public class BackupServicio {
    
    public void restaurarBackup(MultipartFile archivoBackup) throws IOException {
        // Validar que sea un archivo .dump válido
        if (!archivoBackup.getOriginalFilename().endsWith(".dump")) {
            throw new ValidacionException("El archivo debe ser un dump de PostgreSQL");
        }
        
        // Guardar temporalmente
        Path tempFile = Files.createTempFile("restore_", ".dump");
        archivoBackup.transferTo(tempFile.toFile());
        
        try {
            // Ejecutar pg_restore
            ProcessBuilder pb = new ProcessBuilder(
                "pg_restore",
                "-U", dbUser,
                "-d", dbName,
                "--clean", // Limpiar base de datos antes de restaurar
                tempFile.toString()
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new RuntimeException("Error al restaurar backup");
            }
            
            logger.info("Backup restaurado exitosamente desde {}", archivoBackup.getOriginalFilename());
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    public List<BackupInfoDto> listarBackupsDisponibles() {
        // Listar archivos .dump.gz en directorio de backups
        File backupDir = new File(backupDirectorio);
        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".dump.gz"));
        
        return Arrays.stream(backups)
            .map(file -> BackupInfoDto.builder()
                .nombre(file.getName())
                .tamanio(file.length())
                .fechaCreacion(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(file.lastModified()),
                    ZoneId.systemDefault()))
                .build())
            .collect(Collectors.toList());
    }
}
```

**Checklist Backup/Restore:**
- [ ] Crear script backup-automatico.sh
- [ ] Configurar cron job para backup diario
- [ ] Crear servicio BackupServicio en backend
- [ ] Crear endpoint POST /api/backups/restaurar
- [ ] Crear endpoint GET /api/backups/listar
- [ ] Vista frontend de gestión de backups (solo ADMIN)
- [ ] Opción de descargar backup manualmente
- [ ] Opción de restaurar backup seleccionado
- [ ] Validar permisos de admin antes de restaurar

---

## 🎯 FASE 3: MÓDULOS OPCIONALES (PRIORIDAD MEDIA)

### 3.1 📚 Contabilidad Doble Partida

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🟢 MEDIA  
**Tiempo estimado:** 3-4 semanas

Implementar sistema contable básico con:
- Catálogo de cuentas contables
- Asientos contables automáticos en cada venta
- Libro diario y libro mayor
- Balance general y estado de resultados

**Nota:** Este módulo es avanzado y puede delegarse a software contable externo mediante exportación de datos.

---

### 3.2 🧾 Integración con Facturación Electrónica DIAN

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🟢 MEDIA (si el bar factura electrónicamente)  
**Tiempo estimado:** 4-6 semanas

Integración con proveedores de facturación electrónica:
- Dataico
- Siigo
- Alegra
- Defontana

Requiere:
- Registro en DIAN como facturador electrónico
- Certificado digital
- API de proveedor de facturación
- Generación de XML según DIAN
- Firma digital del XML
- Envío a proveedor tecnológico
- Recepción de CUFE (Código Único de Factura Electrónica)
- Almacenamiento de XML y PDF

**Nota:** Puede dejarse para una fase posterior si el bar usa POS sin facturación electrónica.

---

### 3.3 🍔 Gestión de Comandas de Cocina

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🟢 MEDIA  
**Tiempo estimado:** 2 semanas

- Impresión automática de pedidos en cocina/barra
- Estados de preparación (pendiente, en preparación, listo)
- Notificaciones al mesero cuando pedido está listo
- Panel de cocina con pedidos activos

---

### 3.4 👥 Gestión de Clientes y Fidelización

**Estado:** ❌ NO IMPLEMENTADO  
**Prioridad:** 🟢 BAJA  
**Tiempo estimado:** 2-3 semanas

- CRUD de clientes con datos de contacto
- Historial de compras por cliente
- Programa de puntos/fidelización
- Descuentos personalizados
- Cumpleaños y promociones

---

## 📅 CRONOGRAMA SUGERIDO

### Sprint 1 (Semanas 1-2): Módulos Críticos Base
- **Semana 1**: Sistema de Ventas (Venta, PagoVenta, consecutivos)
- **Semana 2**: Sistema de Auditoría (RegistroAuditoria, AOP, verificación)

### Sprint 2 (Semanas 3-4): Caja y Seguridad
- **Semana 3**: Sistema de Caja (apertura, cierre, movimientos)
- **Semana 4**: Requisitos DIAN (impuestos, propinas, frontend facturación)

### Sprint 3 (Semanas 5-6): Seguridad y Reportes
- **Semana 5**: Seguridad avanzada (sesiones, bloqueos, firmas)
- **Semana 6**: Sistema de Reportes básico (ventas, caja, auditoría)

### Sprint 4 (Semanas 7-8): Inventario y Backup
- **Semana 7**: Inventario completo (movimientos, alertas, kardex)
- **Semana 8**: Backup/Restore + Testing integral

### Sprint 5+ (Semanas 9+): Módulos Opcionales
- Contabilidad doble partida (si se requiere)
- Facturación electrónica DIAN (si se requiere)
- Comandas de cocina
- Gestión de clientes

---

## ✅ CHECKLIST GENERAL ANTES DE PRODUCCIÓN

### Backend
- [ ] Todos los módulos críticos implementados (Ventas, Auditoría, Caja, DIAN)
- [ ] Tests unitarios con cobertura mínima 70%
- [ ] Tests de integración en endpoints críticos
- [ ] Manejo de errores consistente en todos los servicios
- [ ] Logging configurado (INFO en producción, DEBUG en desarrollo)
- [ ] Variables de entorno para configuración sensible
- [ ] JWT con SECRET fuerte (mínimo 256 bits)
- [ ] HTTPS configurado en producción
- [ ] CORS configurado correctamente
- [ ] Backup automático configurado

### Frontend
- [ ] Todas las vistas críticas implementadas (Login, POS, Admin, Caja)
- [ ] Tema luxury/premium aplicado consistentemente
- [ ] Teclado virtual funcional en dispositivos táctiles
- [ ] Manejo de errores con AlertaUtil
- [ ] Loading spinners en operaciones largas
- [ ] Validación de inputs antes de enviar al servidor
- [ ] Confirmaciones en operaciones destructivas (eliminar, anular)
- [ ] Impresión de tickets configurada

### Base de Datos
- [ ] Migraciones Flyway aplicadas correctamente
- [ ] Índices creados en columnas frecuentemente consultadas
- [ ] Foreign keys definidas con ON DELETE apropiado
- [ ] Constraints de validación (CHECK) en campos críticos
- [ ] Backup automático configurado y probado
- [ ] Restore probado desde backup

### Seguridad
- [ ] Contraseñas hasheadas con BCrypt (NUNCA texto plano)
- [ ] JWT con expiración apropiada (24 horas recomendado)
- [ ] Control de roles funcionando (ADMIN, CAJERO, MESERO)
- [ ] Bloqueo por intentos fallidos funcionando
- [ ] Auditoría registrando todas las operaciones críticas
- [ ] Hashes de seguridad en Ventas y Auditoría
- [ ] Sesiones de usuario rastreadas
- [ ] IP y dispositivo registrados

### Cumplimiento DIAN
- [ ] Consecutivos sin saltos implementados
- [ ] Desglose correcto de impuestos (IVA, Impoconsumo)
- [ ] Propinas separadas del total gravable
- [ ] Tickets/facturas con todos los datos requeridos
- [ ] Posibilidad de exportar ventas para contabilidad
- [ ] Cierres de caja con arqueo
- [ ] Anulaciones de ventas con justificación y aprobación

### Rendimiento
- [ ] Queries optimizadas con índices
- [ ] Paginación en listados grandes
- [ ] Lazy loading de relaciones JPA
- [ ] Connection pool configurado (mínimo 5, máximo 20)
- [ ] Frontend sin efectos pesados (optimizado para hardware limitado)

### Documentación
- [ ] README.md actualizado con instrucciones de instalación
- [ ] QUICKSTART.md con comandos de inicio rápido
- [ ] Copilot-instructions.md actualizado con nuevos módulos
- [ ] Diagramas de arquitectura actualizados
- [ ] Guía de usuario básica (opcional)

---

## 🚨 RIESGOS Y MITIGACIONES

| Riesgo | Impacto | Probabilidad | Mitigación |
|--------|---------|--------------|------------|
| Pérdida de datos por fallo de hardware | 🔴 CRÍTICO | MEDIA | Backup diario automático + offsite backup |
| Manipulación de registros de ventas | 🔴 CRÍTICO | BAJA | Hashes de seguridad + auditoría inmutable |
| Inconsistencia en consecutivos | 🔴 CRÍTICO | MEDIA | Función SQL thread-safe con lock |
| Diferencias en arqueo de caja | 🟡 ALTO | ALTA | Validaciones estrictas + logs detallados |
| Errores en cálculo de impuestos | 🔴 CRÍTICO | MEDIA | Tests exhaustivos + validación contable |
| Stock negativo por concurrencia | 🟡 ALTO | MEDIA | Transacciones con lock pesimista |
| Sesiones no cerradas correctamente | 🟢 MEDIO | ALTA | Timeout automático + cleanup job |
| Rendimiento lento en hardware antiguo | 🟡 ALTO | ALTA | Optimizaciones CSS + virtualización + índices DB |

---

## 📞 SOPORTE Y MANTENIMIENTO

### Tareas de Mantenimiento Continuo

**Diarias:**
- Verificar backups automáticos
- Revisar logs de errores críticos
- Verificar diferencias en cierres de caja

**Semanales:**
- Verificar integridad de auditoría (hashes)
- Revisar usuarios bloqueados
- Analizar reportes de ventas

**Mensuales:**
- Actualizar dependencias de seguridad
- Revisar rendimiento de queries lentas
- Limpieza de logs antiguos (> 90 días)
- Actualizar documentación si hay cambios

**Anuales:**
- Actualizar porcentajes de impuestos (si cambian)
- Renovar certificados SSL/TLS
- Auditoría de seguridad completa
- Backup completo offsite

---

## 🎓 FORMACIÓN DE USUARIOS

### Cajeros
- Apertura de caja con fondo inicial
- Procesamiento de ventas (efectivo, tarjeta, mixto)
- Propinas y descuentos
- Cierre de caja con arqueo
- Anulación de ventas (con aprobación admin)

### Meseros
- Login con PIN rápido
- Asignación de mesas
- Creación de pedidos
- Modificación de pedidos activos
- Envío de pedidos a caja

### Administradores
- Gestión de usuarios y roles
- Gestión de productos y categorías
- Configuración de impuestos
- Reportes y análisis
- Auditoría y seguridad
- Backup y restore
- Desbloqueo de usuarios

---

## 📖 CONCLUSIÓN

Este documento define una ruta clara y priorizada para transformar Baryx en un sistema de producción completo, seguro y conforme con los requisitos legales de Colombia (DIAN).

**Prioridad inmediata:** Implementar los **4 módulos críticos de la Fase 1**:
1. ✅ Sistema de Ventas y PLU
2. ✅ Sistema de Auditoría Inmutable
3. ✅ Sistema de Gestión de Caja
4. ✅ Numeración Consecutiva y Requisitos DIAN

Una vez completada la Fase 1, el sistema será **funcional para uso en producción** con las medidas de seguridad y trazabilidad necesarias.

Las fases posteriores (2 y 3) añaden funcionalidades avanzadas y mejoras de calidad, pero no son bloqueantes para el lanzamiento inicial.

---

**Última actualización:** 02 de febrero de 2026  
**Próxima revisión:** Al completar cada Sprint
