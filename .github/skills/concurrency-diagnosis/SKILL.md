---
name: concurrency-diagnosis
description: "Diagnóstico de problemas de concurrencia en Kipu. Use when: race condition, deadlock, estado corrupto, operaciones no atómicas, datos inconsistentes entre requests, shared mutable state, problemas con CompletableFuture, transacciones concurrentes que fallan, ConcurrentModificationException, problemas de threading JavaFX."
---

# Diagnóstico de Concurrencia — Kipu

Skill para detectar race conditions, deadlocks, estado compartido inseguro y problemas de threading en el stack de Kipu: JavaFX client (multi-thread), Spring Boot server (concurrent requests), y PostgreSQL (transacciones concurrentes).

## Cuándo Usar

- Datos inconsistentes que aparecen intermitentemente
- `ConcurrentModificationException` u otros errores de threading
- Operaciones que "a veces fallan" sin razón obvia
- Dos cajeros/meseros operan sobre la misma mesa simultáneamente
- Deadlocks o timeouts en transacciones de BD
- La UI muestra datos stale o desincronizados

## Modelo de Concurrencia de Kipu

### Servidor (Spring Boot)
```
Request 1 ──┐
Request 2 ──┼──→ Thread Pool (Tomcat) ──→ @Service (stateless) ──→ Repository ──→ PostgreSQL
Request N ──┘
```
- Cada request HTTP = 1 thread del pool Tomcat
- Services son **singletons** — deben ser **stateless** (sin campos mutables)
- Transacciones JPA por request (`@Transactional`)
- Pool HikariCP: 10 conexiones máx

### Cliente (JavaFX)
```
UI Thread (JavaFX Application Thread) ─── único thread para UI
    ↕ Platform.runLater()
Background Threads ─── CompletableFuture + HttpClient async
```
- **UN solo thread** para UI (JavaFX Application Thread)
- Operaciones HTTP en threads del HttpClient pool
- `Platform.runLater()` para devolver resultados al UI thread
- `AtomicBoolean`/`AtomicInteger` para guards de concurrencia

### Base de Datos (PostgreSQL)
- Nivel de aislamiento por defecto: READ COMMITTED
- Locking: row-level por default, escalable a table-level

## Procedimiento

### 1. Race Conditions en Servidor

#### 1.1 Estado Mutable en Singletons

```bash
# Campos mutables en servicios (PELIGRO si no son thread-safe)
grep -rn "private.*=" kipu-servidor/src/main/java/com/kipu/servidor/servicio/ | \
  grep -v "final\|static final\|Repositorio\|Mapeador\|Servicio\|Logger"

# Campos volatile (uso correcto para flags)
grep -rn "volatile" kipu-servidor/src/main/java/com/kipu/servidor/servicio/

# ¿Hay servicios con estado que cambia?
grep -rn "this\.\w* =" kipu-servidor/src/main/java/com/kipu/servidor/servicio/impl/
```

**Patrón peligroso:**
```java
@Service
public class MiServicioImpl {
    // ❌ RACE CONDITION: Campo mutable en singleton compartido por todos los threads
    private int contadorPedidos = 0;

    public void procesarPedido() {
        contadorPedidos++;  // No atómico — lectura + escritura separadas
    }
}

// ✅ FIX: AtomicInteger o delegar a BD
private final AtomicInteger contadorPedidos = new AtomicInteger(0);
```

**Servicios conocidos con estado en Kipu:**
- `RegistroClientesServicio.java` — `ConcurrentHashMap` ✅ (thread-safe)
- `SincronizacionNubeServicio.java` — `AtomicInteger ciclosFallidos`, `volatile` flags ✅

#### 1.2 Check-Then-Act (TOCTOU)

```bash
# Patrón exists + save (race window entre check y write)
grep -rn "exists\|isPresent\|isEmpty" \
  kipu-servidor/src/main/java/com/kipu/servidor/servicio/impl/ -A 5 | \
  grep -B 5 "save\|delete\|update"
```

**Patrón peligroso:**
```java
// ❌ TOCTOU: Otro thread puede crear el mismo código entre exists() y save()
if (!productoRepositorio.existsByCodigo(dto.getCodigo())) {
    productoRepositorio.save(nuevoProducto);  // Race: puede duplicar
}

// ✅ FIX: Constraint UNIQUE en BD + manejar DataIntegrityViolationException
// O usar INSERT ... ON CONFLICT
```

### 2. Concurrencia en Cliente JavaFX

#### 2.1 Acceso a UI Desde Background Threads

```bash
# Modificaciones de UI fuera de Platform.runLater (BUG)
# Buscar setters de nodos en callbacks de CompletableFuture sin runLater
grep -rn "thenAccept\|thenApply\|whenComplete" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ -A 8 | \
  grep -v "Platform.runLater"
```

**Patrón peligroso:**
```java
// ❌ IllegalStateException: Modificar UI desde background thread
servicioProducto.listar().thenAccept(productos -> {
    labelTotal.setText(String.valueOf(productos.size()));  // NO en UI thread!
});

// ✅ FIX: Siempre envolver en Platform.runLater
servicioProducto.listar().thenAccept(productos -> {
    Platform.runLater(() -> labelTotal.setText(String.valueOf(productos.size())));
});
```

#### 2.2 Guards Atómicos de Debounce

```bash
# AtomicBoolean para prevenir operaciones concurrentes
grep -rn "AtomicBoolean\|AtomicInteger\|AtomicLong\|AtomicReference" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Verificar que el flag se resetea SIEMPRE (incluso en .exceptionally)
grep -rn "compareAndSet\|\.set(" kipu-cliente/src/main/java/com/kipu/cliente/controlador/ -B 3 -A 3
```

**Patrón peligroso:**
```java
// ❌ DEADLOCK LÓGICO: Si la request falla, guardadoEnProceso nunca se resetea
if (guardadoEnProceso.compareAndSet(false, true)) {
    servicio.guardar(datos).thenAccept(r -> {
        guardadoEnProceso.set(false);  // Solo se resetea en success
    });
    // ¿Y si falla? → guardadoEnProceso queda true PARA SIEMPRE
}

// ✅ FIX: Resetear en finally/whenComplete
servicio.guardar(datos)
    .whenComplete((r, ex) -> guardadoEnProceso.set(false));
```

#### 2.3 CompletableFuture Chains Peligrosas

```bash
# Cadenas sin .exceptionally() (errores no manejados)
grep -rn "thenAccept\|thenApply" \
  kipu-cliente/src/main/java/com/kipu/cliente/ -A 10 | \
  grep -v "exceptionally\|whenComplete\|handle"

# Múltiples futures sobre el mismo recurso (race condition)
grep -rn "sendAsync" kipu-cliente/src/main/java/com/kipu/cliente/servicio/ | wc -l
```

### 3. Concurrencia en Base de Datos

#### 3.1 Locking Optimista (@Version)

```bash
# Entidades que usan @Version (locking optimista)
grep -rn "@Version" kipu-servidor/src/main/java/com/kipu/servidor/modelo/entidad/

# Entidades que DEBERÍAN usar @Version (escritura concurrente probable)
# Candidatas: Mesa, Pedido, LineaPedido (múltiples meseros/cajeros)
```

**Entidades con escritura concurrente en Kipu:**
- `Mesa` — Múltiples meseros pueden modificar
- `Pedido` — Mesero agrega líneas + cajero factura
- `LineaPedido` — Mesero modifica cantidades

**Si no tiene @Version:**
```java
// ❌ Lost update: Mesero A lee mesa, Mesero B lee mesa, ambos escriben, A pierde
Mesa mesa = mesaRepositorio.findById(id);
mesa.setEstado("OCUPADA");
mesaRepositorio.save(mesa);  // Si B guardó entre findById y save, A sobreescribe

// ✅ FIX: Agregar @Version a la entidad
@Version
private Long version;
// Spring/JPA lanza OptimisticLockException si hay conflicto
```

#### 3.2 Deadlocks por Orden de Locks

```bash
# Servicios que hacen múltiples saves en una transacción
grep -rn "@Transactional" \
  kipu-servidor/src/main/java/com/kipu/servidor/servicio/impl/ -A 30 | \
  grep "save\|delete" | sort
```

**Regla**: Si dos transacciones modifican las mismas tablas, deben hacerlo en el **mismo orden**.
```
TX1: UPDATE mesas → UPDATE pedidos  (OK)
TX2: UPDATE pedidos → UPDATE mesas  (❌ DEADLOCK potencial con TX1)
```

#### 3.3 Queries de Diagnóstico

```sql
-- Deadlocks recientes
SELECT * FROM pg_stat_database WHERE datname = 'kipu_db';
-- Ver deadlocks en: SELECT deadlocks FROM pg_stat_database

-- Locks en espera ahora
SELECT pid, mode, relation::regclass, granted
FROM pg_locks
WHERE NOT granted AND database = (SELECT oid FROM pg_database WHERE datname = 'kipu_db');

-- Transacciones idle que retienen locks
SELECT pid, state, query, now() - state_change AS idle_duration
FROM pg_stat_activity
WHERE datname = 'kipu_db' AND state = 'idle in transaction'
ORDER BY idle_duration DESC;
```

### 4. Escenarios Concurrentes Críticos en Kipu

| Escenario | Actores | Riesgo |
|-----------|---------|--------|
| Dos meseros agregan producto a misma mesa | 2 clientes → 1 mesa | Lost update en líneas pedido |
| Mesero agrega producto + cajero factura | Mesero + Cajero → 1 pedido | Pedido facturado incompleto |
| Dos cajeros procesan mismo pedido | 2 POS → 1 pedido | Doble facturación |
| Backup cloud durante pico | Sync service + requests normales | Lock contention en BD |
| Login simultáneo con mismo código | 2 terminales → 1 usuario | Sesiones JWT duplicadas |

Para cada escenario, trazar:
1. ¿Qué entidades se tocan?
2. ¿En qué orden se adquieren los locks?
3. ¿Hay `@Version` o constraint que prevenga inconsistencia?
4. ¿El cliente maneja `OptimisticLockException` / respuesta 409?

## Thread Dumps (Runtime)

```bash
# Thread dump del servidor
jcmd $(pgrep -f kipu-servidor) Thread.print > thread-dump-server.txt

# Thread dump del cliente
jcmd $(pgrep -f kipu-cliente) Thread.print > thread-dump-client.txt

# Buscar deadlocks automáticamente
jcmd $(pgrep -f kipu-servidor) Thread.print | grep -A 20 "deadlock"

# Threads en estado BLOCKED
jcmd $(pgrep -f kipu-servidor) Thread.print | grep "BLOCKED" -B 5
```

## Output Esperado

```
DIAGNÓSTICO DE CONCURRENCIA — [Módulo/Flujo]
══════════════════════════════════════════════

MODELO DE THREADING
  [Descripción del modelo de concurrencia del componente analizado]

RACE CONDITIONS
  1. [Tipo: TOCTOU / Lost Update / Dirty Read]
     Actores: [quién puede colisionar]
     Ubicación: [archivo:línea]
     Ventana: [descripción del timing window]
     Probabilidad: Baja / Media / Alta (basada en carga Kipu)
     Fix: [propuesta]

DEADLOCK POTENCIALES
  1. [Tablas/recursos involucrados]
     Orden TX1: [recurso A → recurso B]
     Orden TX2: [recurso B → recurso A]
     Fix: [unificar orden]

ESTADO COMPARTIDO INSEGURO
  1. [Campo/variable en clase singleton]
     Ubicación: [archivo:línea]
     Protección actual: [ninguna / volatile / atomic / synchronized]
     Fix: [propuesta]

SEVERIDAD GENERAL
  [Evaluación del riesgo de concurrencia del módulo]
```
