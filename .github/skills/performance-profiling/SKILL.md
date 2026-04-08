---
name: performance-profiling
description: "Perfilado de rendimiento general del sistema Kipu. Use when: medir tiempos de respuesta, detectar cuellos de botella, analizar startup lento, perfilar endpoints REST, medir latencia HTTP cliente-servidor, evaluar throughput bajo carga, diagnosticar degradación progresiva de rendimiento."
---

# Perfilado de Rendimiento — Kipu

Skill para diagnosticar problemas de rendimiento en el stack completo de Kipu: JavaFX cliente → HTTP → Spring Boot servidor → PostgreSQL.

## Cuándo Usar

- El sistema "va lento" sin causa obvia
- Un endpoint REST tarda más de lo esperado
- El arranque del cliente o servidor es lento
- El rendimiento se degrada con el tiempo o bajo carga
- Se necesita establecer una línea base de rendimiento

## Procedimiento

### 1. Identificar la Capa Afectada

Antes de profundizar, determina **dónde** ocurre la lentitud:

```
Cliente (JavaFX) → Red (HTTP) → Servidor (Spring) → BD (PostgreSQL)
```

**Preguntas clave:**
- ¿La lentitud es en una acción específica o generalizada?
- ¿Ocurre desde el inicio o se degrada con el tiempo?
- ¿Es reproducible o intermitente?

### 2. Perfilar Servidor (Spring Boot)

#### 2.1 Revisar Configuración de Pool

Archivo: `kipu-servidor/src/main/resources/application.yml`

```yaml
# Configuración actual de Kipu
hikari:
  maximum-pool-size: 10       # ¿Suficiente para picos 20:00-03:00?
  minimum-idle: 2             # ¿Muy bajo para arranque rápido?
  connection-timeout: 30000   # 30s — ¿Demasiado alto?
  idle-timeout: 600000        # 10min
  max-lifetime: 1800000       # 30min
```

**Checklist de pool:**
- [ ] ¿Pool size >= número de clientes concurrentes esperados? (1-20 clientes/bar)
- [ ] ¿Connection timeout razonable? (>5s para LAN es sospechoso)
- [ ] ¿Hay queries que retienen conexiones demasiado tiempo?

#### 2.2 Analizar Endpoints Lentos

Buscar patrones problemáticos en controladores:

```bash
# Endpoints que devuelven List<T> sin paginación (potencial N+1 o carga masiva)
grep -rn "List<.*Dto>" kipu-servidor/src/main/java/com/kipu/servidor/controlador/

# Transacciones sin readOnly (posible lock contention)
grep -rn "@Transactional" kipu-servidor/src/main/java/com/kipu/servidor/servicio/ | grep -v "readOnly"

# findAll() sin paginación
grep -rn "findAll()" kipu-servidor/src/main/java/com/kipu/servidor/servicio/
```

#### 2.3 Habilitar Logging de Queries (Temporal)

En `application.yml`, temporalmente:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.stat: DEBUG
```

**⚠️ Revertir después del diagnóstico** — esto genera overhead significativo.

### 3. Perfilar Cliente (JavaFX)

#### 3.1 Medir Tiempos de Carga de Vistas

Buscar en controladores los `initialize()` y medir:

```java
// Patrón para medir en initialize():
long inicio = System.nanoTime();
// ... código de inicialización ...
long duracion = (System.nanoTime() - inicio) / 1_000_000;
System.out.println("Vista X cargada en " + duracion + "ms");
```

#### 3.2 Detectar Operaciones Pesadas en UI Thread

```bash
# Platform.runLater con operaciones potencialmente pesadas
grep -rn "Platform.runLater" kipu-cliente/src/main/java/ -A 5

# Llamadas HTTP síncronas (bloqueantes) — NO deberían existir
grep -rn "\.send(" kipu-cliente/src/main/java/ | grep -v "sendAsync"

# .get() bloqueante en CompletableFuture desde UI thread
grep -rn "\.get()" kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

#### 3.3 Conteo de Nodos del Scene Graph

Kipu está optimizado para hardware bajo. Verificar:

```bash
# Nodos creados dinámicamente en loops (productos, mesas, etc.)
grep -rn "new Button\|new Label\|new VBox\|new HBox\|getChildren().add" kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

**Regla**: <1000 nodos visibles simultáneamente.

### 4. Perfilar Red (HTTP)

#### 4.1 Latencia Cliente-Servidor

Archivo clave: `kipu-cliente/src/main/java/com/kipu/cliente/servicio/ServicioHttpBase.java`

```bash
# Timeouts configurados
grep -rn "TIMEOUT" kipu-common/src/main/java/com/kipu/common/constantes/

# Reintentos configurados
grep -rn "retry\|reintento\|REINTENTO" kipu-cliente/src/main/java/
```

#### 4.2 Serialización/Deserialización

Jackson puede ser cuello de botella con objetos grandes:

```bash
# DTOs con listas anidadas (serialización costosa)
grep -rn "List<.*Dto>" kipu-common/src/main/java/com/kipu/common/dto/
```

### 5. Perfilar Base de Datos

Ver skill [query-analysis](../query-analysis/SKILL.md) para análisis detallado de queries.

Comandos rápidos:

```sql
-- Queries activas más lentas
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active' AND datname = 'kipu_db'
ORDER BY duration DESC;

-- Tablas más grandes (indicador de carga)
SELECT relname, n_live_tup
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;

-- Índices no usados
SELECT schemaname, relname, indexrelname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND schemaname = 'public';
```

## Anti-Patrones Conocidos en Kipu

| Anti-Patrón | Dónde Buscar | Impacto |
|-------------|--------------|---------|
| Endpoints sin paginación | `*Controlador.java` retornando `List<T>` | Memoria + latencia con datos grandes |
| `findAll()` masivo | `SincronizacionNubeServicio.java` L412-503 | Pico de memoria en backup diario |
| Nodos dinámicos sin pool | Grids de productos en controladores | Scene graph bloat |
| Listeners sin cleanup | `.addListener()` sin `.removeListener()` en controladores | Memory leak progresivo |
| Platform.runLater granular | 40+ instancias individuales | Overhead de scheduling |

## Output Esperado

Al completar el perfilado, generar:

```
PERFIL DE RENDIMIENTO — [Componente/Flujo]
═══════════════════════════════════════════

MÉTRICAS BASE
  Arranque servidor: Xms
  Arranque cliente: Xms
  Endpoint más lento: [ruta] — Xms
  Nodos scene graph: ~X

CUELLOS DE BOTELLA IDENTIFICADOS
  1. [Descripción] — Impacto: Alto/Medio/Bajo
  2. ...

RECOMENDACIONES (ordenadas por impacto)
  1. [Acción concreta]
  2. ...
```
