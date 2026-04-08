---
name: query-analysis
description: "Análisis de queries SQL y JPA en Kipu. Use when: endpoint lento, N+1 queries, full table scan, índices faltantes, analizar EXPLAIN plan, optimizar queries Hibernate, detectar lazy loading fuera de sesión, evaluar rendimiento de consultas PostgreSQL, analizar lock contention."
---

# Análisis de Queries — Kipu

Skill para diagnosticar problemas de rendimiento en la capa de datos: JPA/Hibernate queries, PostgreSQL execution plans, índices, y transacciones.

## Cuándo Usar

- Un endpoint REST es lento y se sospecha de la BD
- Se detectan N+1 queries en los logs de Hibernate
- Una tabla crece y las consultas se degradan
- Lock contention durante picos de operación (20:00-03:00)
- Después de agregar datos seed o migrar esquemas

## Contexto de BD en Kipu

**Esquema**: `V1__esquema_completo.sql` (migración única Flyway)
**Config JPA**: `hibernate.ddl-auto: validate`, `open-in-view: false`, `enable_lazy_load_no_trans: false`
**Pool**: HikariCP — 10 max, 2 min idle, 30s connection timeout
**Escala**: 1 bar = ~100-500 productos, ~20 mesas, ~100+ pedidos/noche, 1-20 clientes concurrentes

## Procedimiento

### 1. Identificar Queries Problemáticas

#### 1.1 Análisis Estático de Repositorios

```bash
# Todas las queries custom
grep -rn "@Query" kipu-servidor/src/main/java/com/kipu/servidor/repositorio/

# findAll() sin paginación (potencial full scan)
grep -rn "findAll()\|findBy.*(" kipu-servidor/src/main/java/com/kipu/servidor/repositorio/

# Repositorios que NO extienden PagingAndSortingRepository
grep -rn "extends JpaRepository\|extends CrudRepository" \
  kipu-servidor/src/main/java/com/kipu/servidor/repositorio/
```

#### 1.2 Detectar N+1 Queries

Buscar patrones que generan N+1:

```bash
# Entidades con relaciones LAZY que se acceden en loops
grep -rn "FetchType.LAZY\|FetchType.EAGER" \
  kipu-servidor/src/main/java/com/kipu/servidor/modelo/entidad/

# Servicios que iteran sobre colecciones y acceden relaciones
grep -rn "\.get.*Dto\|\.stream()\|\.forEach\|\.map(" \
  kipu-servidor/src/main/java/com/kipu/servidor/servicio/impl/
```

**Patrón N+1 típico:**
```java
// ❌ N+1: 1 query para mesas + N queries para pedidos de cada mesa
List<Mesa> mesas = mesaRepositorio.findAll();
for (Mesa m : mesas) {
    m.getPedidoActual().getLineas(); // LAZY → 1 query por mesa
}

// ✅ FIX: JOIN FETCH en una sola query
@Query("SELECT m FROM Mesa m LEFT JOIN FETCH m.pedidoActual p LEFT JOIN FETCH p.lineas")
List<Mesa> findAllConPedidosYLineas();
```

**JOIN FETCH conocidos en Kipu:**
- `MesaRepositorio.java` — ya usa `LEFT JOIN FETCH` para pedidoActual + lineas ✅

#### 1.3 Verificar Índices en Esquema

```bash
# Índices definidos en V1
grep -n "CREATE INDEX\|CREATE UNIQUE INDEX" \
  kipu-servidor/src/main/resources/db/migration/V1__esquema_completo.sql

# Columnas usadas en WHERE/JOIN/ORDER BY en queries custom
grep -n "@Query" kipu-servidor/src/main/java/com/kipu/servidor/repositorio/ -A 5 | \
  grep -i "WHERE\|JOIN\|ORDER BY"
```

### 2. Análisis con EXPLAIN (Runtime)

#### 2.1 Queries Críticas a Analizar

```sql
-- Listar mesas con pedidos (endpoint más frecuente del POS)
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT m.*, p.*, lp.*
FROM mesas m
LEFT JOIN pedidos p ON p.id_mesa = m.id
LEFT JOIN lineas_pedido lp ON lp.id_pedido = p.id
WHERE m.activo = true;

-- Buscar productos por categoría (grid del POS)
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM productos
WHERE activo = true AND id_categoria = 1
ORDER BY nombre;

-- Login por código (mesero)
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM usuarios
WHERE codigo = 'MES001' AND activo = true;
```

#### 2.2 Interpretar EXPLAIN

| Indicador | Bueno | Malo |
|-----------|-------|------|
| Seq Scan | OK en tablas <500 filas | ❌ En tablas >1000 filas con filtros |
| Index Scan | ✅ Siempre | — |
| Nested Loop | ✅ Con pocas filas exteriores | ❌ Con muchas filas exteriores |
| Hash Join | ✅ Para joins grandes | — |
| Rows (estimado vs real) | Similares | ❌ Diferencia >10x (estadísticas desactualizadas) |
| Buffers shared hit | ✅ Alto | — |
| Buffers shared read | Bajo | ❌ Alto (datos no en caché) |

### 3. Análisis de Transacciones

#### 3.1 Scope de Transacciones

```bash
# Transacciones de escritura (potencial lock contention)
grep -rn "@Transactional" kipu-servidor/src/main/java/com/kipu/servidor/servicio/impl/ | grep -v "readOnly"

# Métodos que hacen múltiples writes en una transacción
# Buscar servicios con múltiples .save() .delete() en un método
grep -rn "\.save(\|\.delete(\|\.saveAll(" \
  kipu-servidor/src/main/java/com/kipu/servidor/servicio/impl/ -B 2
```

**Checklist de transacciones:**
- [ ] ¿Las transacciones de lectura usan `@Transactional(readOnly = true)`?
- [ ] ¿Las transacciones de escritura son lo más cortas posible?
- [ ] ¿Hay transacciones que mezclan I/O externo (HTTP) con writes a BD?
- [ ] ¿El locking optimista (`@Version`) se usa en entidades con escritura concurrente?

#### 3.2 Detectar Long-Running Transactions

```sql
-- Transacciones abiertas más de 5s
SELECT pid, now() - xact_start AS duration, state, query
FROM pg_stat_activity
WHERE datname = 'kipu_db'
  AND xact_start IS NOT NULL
  AND now() - xact_start > interval '5 seconds'
ORDER BY duration DESC;

-- Locks activos
SELECT blocked_locks.pid AS blocked_pid,
       blocking_locks.pid AS blocking_pid,
       blocked_activity.query AS blocked_query,
       blocking_activity.query AS blocking_query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
  AND blocking_locks.relation = blocked_locks.relation
  AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

### 4. Monitoreo de Estadísticas PostgreSQL

```sql
-- Tablas con más accesos y su hit rate
SELECT relname,
       seq_scan, seq_tup_read,
       idx_scan, idx_tup_fetch,
       n_tup_ins, n_tup_upd, n_tup_del,
       n_live_tup, n_dead_tup
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY seq_scan + idx_scan DESC;

-- Cache hit rate (debería ser >95%)
SELECT
  sum(heap_blks_hit) / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0) AS cache_hit_rate
FROM pg_statio_user_tables;

-- Índices no usados (candidatos a eliminar)
SELECT schemaname, relname, indexrelname, idx_scan, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC;

-- Tablas que necesitan VACUUM
SELECT relname, n_dead_tup, last_vacuum, last_autovacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC;
```

## Anti-Patrones de Queries en Kipu

| Anti-Patrón | Detección | Fix |
|-------------|-----------|-----|
| Endpoints sin paginación | `List<XDto>` en controllers | Usar `Page<T>` + `Pageable` |
| `findAll()` en backup | `SincronizacionNubeServicio` L412-503 | Paginar con `PageRequest` |
| EAGER en relación grande | `FetchType.EAGER` en colecciones | Cambiar a LAZY + JOIN FETCH |
| Query sin índice | `WHERE campo = ?` sin index | Agregar índice en V1 |
| SELECT * implícito | `findAll()` de Spring Data | Proyección con `@Query` si no se usan todos los campos |
| Transacción larga con I/O | `@Transactional` en método con HTTP call | Separar lectura BD → I/O → write BD |

## Output Esperado

```
ANÁLISIS DE QUERIES — [Módulo/Endpoint]
════════════════════════════════════════

QUERIES ANALIZADAS: X

PROBLEMAS DETECTADOS
  1. [Tipo: N+1 / Full Scan / Missing Index / Long TX]
     Query: [SQL o método JPA]
     Ubicación: [archivo:línea]
     EXPLAIN: [resumen del plan si disponible]
     Impacto: [estimado en ms o recursos]

ÍNDICES RECOMENDADOS
  CREATE INDEX idx_[tabla]_[columna] ON [tabla]([columna]);

QUERIES OPTIMIZADAS (propuestas)
  [Query original] → [Query optimizada]

ESTADO GENERAL
  Cache hit rate: X%
  Tablas críticas: [tabla] — X filas, Y dead tuples
  Locks detectados: X
```
