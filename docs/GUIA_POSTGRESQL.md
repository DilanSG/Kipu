# Guía PostgreSQL - Kipu

Referencia completa para instalar, configurar y administrar la base de datos PostgreSQL del sistema Kipu.

---

## 1. Instalación de PostgreSQL

### Ubuntu/Debian (Linux)

```bash
# Instalar PostgreSQL y herramientas cliente
sudo apt update
sudo apt install postgresql postgresql-contrib postgresql-client -y

# Verificar que el servicio está corriendo
sudo systemctl status postgresql

# Habilitar inicio automático
sudo systemctl enable postgresql
```

### Verificar versión

```bash
psql --version
# psql (PostgreSQL) 16.x
```

---

## 2. Gestión del Servicio PostgreSQL

```bash
# Iniciar el servicio
sudo systemctl start postgresql

# Detener el servicio
sudo systemctl stop postgresql

# Reiniciar (aplica cambios de configuración)
sudo systemctl restart postgresql

# Recargar configuración sin reiniciar (pg_hba.conf, postgresql.conf)
sudo systemctl reload postgresql

# Ver estado
sudo systemctl status postgresql

# Ver logs en tiempo real
sudo journalctl -u postgresql -f
```

---

## 3. Acceder a PostgreSQL

### Como superusuario (postgres)

```bash
# Cambiar al usuario del sistema 'postgres' y abrir psql
sudo -u postgres psql

# O directamente
sudo -u postgres psql -d kipu_db
```

### Como usuario kipu_admin (después del setup)

```bash
# Conexión local
psql -U kipu_admin -d kipu_db -h localhost

# Con contraseña (se pedirá interactivamente)
psql -U kipu_admin -d kipu_db -h localhost -W

# Conexión con string completo
psql "postgresql://kipu_admin:Dilan5236@localhost:5432/kipu_db"
```

---

## 4. Setup Inicial de Kipu

### Paso 1: Crear la base de datos y usuario

```bash
# Entrar como superusuario
sudo -u postgres psql
```

```sql
-- Crear la base de datos
CREATE DATABASE kipu_db
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'es_ES.UTF-8'
    LC_CTYPE = 'es_ES.UTF-8'
    CONNECTION LIMIT = -1;
--Tambien con:   
CREATE DATABASE kipu_db WITH OWNER = postgres ENCODING = 'UTF8' LC_COLLATE = 'Spanish_Colombia.1252' LC_CTYPE = 'Spanish_Colombia.1252' CONNECTION LIMIT = -1;

-- Crear usuario de la aplicación
CREATE USER kipu_admin WITH ENCRYPTED PASSWORD 'Dilan5236';

-- Otorgar privilegios
GRANT ALL PRIVILEGES ON DATABASE kipu_db TO kipu_admin;

-- Conectar a la BD creada
\c kipu_db;

-- Permisos en schema public
GRANT ALL ON SCHEMA public TO kipu_admin;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO kipu_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO kipu_admin;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO kipu_admin;

-- Permisos por defecto para objetos futuros (IMPORTANTE para Flyway)
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO kipu_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO kipu_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO kipu_admin;
```

### Paso 2: Verificar

```sql
-- Verificar BD creada
SELECT datname FROM pg_database WHERE datname = 'kipu_db';

-- Verificar usuario creado
SELECT usename, usecreatedb, usesuper FROM pg_user WHERE usename = 'kipu_admin';

-- Salir
\q
```   

### Paso 3: Iniciar el servidor Spring Boot

Las tablas se crean automáticamente via Flyway al iniciar el servidor:

```bash
cd kipu-servidor
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway ejecuta las migraciones en orden:
- `V1__crear_tablas_base.sql` → Esquema completo (usuarios, categorias, productos, mesas, pedidos, lineas_pedido, cajas)
- `V2__datos_iniciales.sql` → Datos seed (admin, cajero, 8 meseros, 8 categorías, 40 productos)
- `V3__crear_metodos_pago.sql` → Tabla métodos de pago (EFECTIVO, DÉBITO, CRÉDITO, TRANSFERENCIA, QR, MIXTO)

### Paso 4: Verificar que todo se creó

```bash
psql -U kipu_admin -d kipu_db -h localhost
```

```sql
-- Ver todas las tablas
\dt

-- Resultado esperado:
--  usuarios
--  categorias
--  productos
--  mesas
--  pedidos
--  lineas_pedido
--  cajas
--  metodos_pago
--  flyway_schema_history
```

---

## 5. Script Rápido (Todo en Uno)

Ejecutar el script existente del proyecto:

```bash
sudo -u postgres psql -f database/setup-database.sql
```

O manualmente:

```bash
# 1. Crear BD y usuario
sudo -u postgres psql -c "CREATE DATABASE kipu_db WITH ENCODING='UTF8';"
sudo -u postgres psql -c "CREATE USER kipu_admin WITH ENCRYPTED PASSWORD 'Dilan5236';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE kipu_db TO kipu_admin;"

# 2. Asignar permisos en schema
sudo -u postgres psql -d kipu_db -c "GRANT ALL ON SCHEMA public TO kipu_admin;"
sudo -u postgres psql -d kipu_db -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO kipu_admin;"
sudo -u postgres psql -d kipu_db -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO kipu_admin;"

# 3. Iniciar servidor (Flyway crea tablas automáticamente)
cd kipu-servidor && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 6. Comandos psql Esenciales

### Navegación

| Comando | Descripción |
|---------|-------------|
| `\l` | Listar todas las bases de datos |
| `\c kipu_db` | Conectar a una base de datos |
| `\dt` | Listar tablas del schema actual |
| `\dt+` | Listar tablas con tamaño e info extra |
| `\d nombre_tabla` | Ver estructura de una tabla (columnas, tipos, constraints) |
| `\d+ nombre_tabla` | Estructura detallada (incluye storage, description) |
| `\di` | Listar índices |
| `\ds` | Listar secuencias |
| `\df` | Listar funciones |
| `\du` | Listar usuarios/roles |
| `\dn` | Listar schemas |
| `\dv` | Listar vistas |

### Control de sesión

| Comando | Descripción |
|---------|-------------|
| `\q` | Salir de psql |
| `\x` | Toggle formato expandido (vertical) |
| `\timing` | Toggle mostrar tiempo de ejecución |
| `\e` | Abrir editor externo para query |
| `\i archivo.sql` | Ejecutar archivo SQL |
| `\pset pager off` | Desactivar paginación |
| `\! clear` | Limpiar pantalla |

### Ayuda

| Comando | Descripción |
|---------|-------------|
| `\?` | Ayuda de comandos psql |
| `\h` | Ayuda de sentencias SQL |
| `\h CREATE TABLE` | Ayuda específica de un comando |

---

## 7. Consultas Útiles para Kipu

### Estado general de la BD

```sql
-- Tamaño de la BD
SELECT pg_size_pretty(pg_database_size('kipu_db')) AS tamano_bd;

-- Tamaño por tabla
SELECT 
    tablename AS tabla,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)) AS tamano
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname || '.' || tablename) DESC;

-- Contar registros por tabla
SELECT 
    schemaname,
    relname AS tabla,
    n_live_tup AS filas_aprox
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;
```

### Verificar datos de Kipu

```sql
-- Usuarios del sistema
SELECT id_usuario, nombre_usuario, codigo, nombre_completo, rol, activo
FROM usuarios ORDER BY id_usuario;

-- Categorías
SELECT id_categoria, nombre, orden, activo FROM categorias ORDER BY orden;

-- Productos por categoría
SELECT p.codigo, p.nombre, p.precio, c.nombre AS categoria, p.stock_actual
FROM productos p
JOIN categorias c ON p.id_categoria = c.id_categoria
ORDER BY c.orden, p.nombre;

-- Métodos de pago
SELECT nombre, es_predeterminado, activo FROM metodos_pago ORDER BY orden;

-- Mesas y su estado
SELECT numero_mesa, estado, id_mesero FROM mesas ORDER BY numero_mesa;
```

### Monitoreo de conexiones

```sql
-- Conexiones activas
SELECT pid, usename, datname, client_addr, state, query_start, query
FROM pg_stat_activity
WHERE datname = 'kipu_db';

-- Contar conexiones por estado
SELECT state, COUNT(*) 
FROM pg_stat_activity 
WHERE datname = 'kipu_db'
GROUP BY state;

-- Matar una conexión específica
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid = <PID>;
```

### Estado de Flyway

```sql
-- Ver historial de migraciones
SELECT installed_rank, version, description, type, script, 
       installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

---

## 8. Operaciones Comunes

### Backup y Restore

```bash
# === BACKUP ===

# Backup completo (formato custom, comprimido)
pg_dump -U kipu_admin -h localhost -Fc kipu_db > backup_kipu_$(date +%Y%m%d_%H%M%S).dump

# Backup en SQL plano (legible)
pg_dump -U kipu_admin -h localhost kipu_db > backup_kipu_$(date +%Y%m%d_%H%M%S).sql

# Backup solo datos (sin estructura)
pg_dump -U kipu_admin -h localhost --data-only kipu_db > datos_kipu.sql

# Backup solo estructura (sin datos)
pg_dump -U kipu_admin -h localhost --schema-only kipu_db > esquema_kipu.sql

# Backup de una tabla específica
pg_dump -U kipu_admin -h localhost -t productos kipu_db > productos_backup.sql


# === RESTORE ===

# Restaurar desde formato custom
pg_restore -U kipu_admin -h localhost -d kipu_db --clean backup_kipu.dump

# Restaurar desde SQL plano
psql -U kipu_admin -h localhost -d kipu_db < backup_kipu.sql

# Restaurar tabla específica
pg_restore -U kipu_admin -h localhost -d kipu_db -t productos backup_kipu.dump
```

### Reset Completo (Solo Desarrollo)

```bash
# Opción 1: Usar script del proyecto
sudo -u postgres psql -d kipu_db -f database/reset-database.sql
# Luego reiniciar el servidor para que Flyway recree todo

# Opción 2: Eliminar y recrear la BD completa
sudo -u postgres psql -c "DROP DATABASE IF EXISTS kipu_db;"
sudo -u postgres psql -f database/setup-database.sql
# Reiniciar servidor: Flyway ejecuta V1, V2, V3 automáticamente

# Opción 3: Solo limpiar datos (mantener estructura)
psql -U kipu_admin -h localhost -d kipu_db
```

```sql
-- Desactivar constraints temporalmente
SET session_replication_role = replica;

-- Truncar todas las tablas (excepto flyway)
TRUNCATE TABLE lineas_pedido, pedidos, mesas, productos, categorias, 
               cajas, metodos_pago, usuarios CASCADE;

-- Reactivar constraints
SET session_replication_role = DEFAULT;

-- Nota: después de truncar, re-ejecutar V2 y V3 manualmente
-- o eliminar flyway_schema_history y reiniciar servidor
DELETE FROM flyway_schema_history WHERE version IN ('2', '3');
-- Reiniciar servidor para que Flyway re-ejecute V2 y V3
```

### Resetear solo Flyway (Re-ejecutar migraciones)

```sql
-- Eliminar historial de Flyway
DROP TABLE IF EXISTS flyway_schema_history;

-- Luego al iniciar el servidor, Flyway intenta ejecutar TODAS las migraciones
-- Si las tablas ya existen, fallará. Opciones:
-- a) Eliminar las tablas primero (reset completo)
-- b) Usar baseline-on-migrate: true en application.yml (ya configurado)
```

---

## 9. Gestión de Usuarios PostgreSQL

```sql
-- Crear nuevo usuario
CREATE USER nuevo_usuario WITH ENCRYPTED PASSWORD 'password123';

-- Otorgar permisos sobre la BD
GRANT ALL PRIVILEGES ON DATABASE kipu_db TO nuevo_usuario;

-- Cambiar contraseña
ALTER USER kipu_admin WITH PASSWORD 'nueva_contraseña';

-- Revocar permisos
REVOKE ALL PRIVILEGES ON DATABASE kipu_db FROM usuario;

-- Eliminar usuario
DROP USER IF EXISTS usuario;

-- Ver permisos de un usuario
SELECT grantee, privilege_type, table_name
FROM information_schema.role_table_grants
WHERE grantee = 'kipu_admin';

-- Listar todos los usuarios
\du
```

---

## 10. Configuración de Acceso (pg_hba.conf)

Si hay problemas de autenticación:

```bash
# Encontrar el archivo de configuración
sudo -u postgres psql -c "SHOW hba_file;"
# Normalmente: /etc/postgresql/16/main/pg_hba.conf

# Editar
sudo nano /etc/postgresql/16/main/pg_hba.conf
```

Configuración recomendada para desarrollo local:

```conf
# TYPE  DATABASE    USER          ADDRESS         METHOD
local   all         postgres                      peer
local   all         all                           md5
host    all         all           127.0.0.1/32    md5
host    all         all           ::1/128         md5

# Para acceso desde LAN (ajustar rango según tu red)
host    kipu_db    kipu_admin   192.168.1.0/24  md5
```

```bash
# Aplicar cambios
sudo systemctl reload postgresql
```

### Permitir conexiones remotas (LAN)

```bash
# Editar postgresql.conf
sudo nano /etc/postgresql/16/main/postgresql.conf

# Cambiar:
listen_addresses = '*'    # o '0.0.0.0' para IPv4 solamente
port = 5432

# Reiniciar
sudo systemctl restart postgresql
```

---

## 11. Esquema de Tablas de Kipu

Referencia rápida de las tablas creadas por Flyway:

### usuarios
| Columna | Tipo | Notas |
|---------|------|-------|
| id_usuario | BIGSERIAL PK | Auto-increment |
| nombre_usuario | VARCHAR(50) UNIQUE | Login admin (nullable para meseros/cajeros que usan PIN) |
| contrasena | VARCHAR(255) | BCrypt hash (admin) |
| codigo | VARCHAR(2) UNIQUE | Código de 2 dígitos (cajeros/meseros) |
| pin | VARCHAR(255) | BCrypt hash del PIN |
| nombre_completo | VARCHAR(100) NOT NULL | |
| rol | VARCHAR(20) NOT NULL | CHECK: MESERO, CAJERO, ADMIN |
| genero | VARCHAR(20) NOT NULL | CHECK: MASCULINO, FEMENINO |
| bloqueado | BOOLEAN DEFAULT FALSE | Se bloquea tras 3 intentos fallidos |
| intentos_fallidos | INTEGER DEFAULT 0 | |
| activo | BOOLEAN DEFAULT TRUE | Soft delete |

### categorias
| Columna | Tipo | Notas |
|---------|------|-------|
| id_categoria | BIGSERIAL PK | |
| nombre | VARCHAR(100) NOT NULL UNIQUE | |
| descripcion | VARCHAR(255) | |
| orden | INTEGER DEFAULT 0 | Orden de display |
| activo | BOOLEAN DEFAULT TRUE | |

### productos
| Columna | Tipo | Notas |
|---------|------|-------|
| id_producto | BIGSERIAL PK | |
| codigo | VARCHAR(50) NOT NULL UNIQUE | Ej: AGU01, TEQ02 |
| nombre | VARCHAR(100) NOT NULL | |
| precio | NUMERIC(10,2) NOT NULL | |
| id_categoria | BIGINT FK → categorias | |
| stock_actual | INTEGER DEFAULT 0 | |
| stock_minimo | INTEGER DEFAULT 0 | Para alertas |
| requiere_stock | BOOLEAN DEFAULT TRUE | |

### mesas
| Columna | Tipo | Notas |
|---------|------|-------|
| id_mesa | BIGSERIAL PK | |
| numero_mesa | VARCHAR(10) NOT NULL UNIQUE | |
| estado | VARCHAR(20) DEFAULT 'DISPONIBLE' | CHECK: DISPONIBLE, OCUPADA, RESERVADA |
| id_mesero | BIGINT FK → usuarios | Nullable |

### pedidos
| Columna | Tipo | Notas |
|---------|------|-------|
| id_pedido | BIGSERIAL PK | |
| id_mesa | BIGINT FK → mesas | ON DELETE CASCADE |
| total | NUMERIC(10,2) DEFAULT 0 | |
| estado | VARCHAR(20) DEFAULT 'ACTIVO' | |

### lineas_pedido
| Columna | Tipo | Notas |
|---------|------|-------|
| id_linea_pedido | BIGSERIAL PK | |
| id_pedido | BIGINT FK → pedidos | ON DELETE CASCADE |
| id_producto | BIGINT FK → productos | |
| nombre_producto | VARCHAR(100) NOT NULL | Snapshot del nombre |
| precio_unitario | NUMERIC(10,2) NOT NULL | Snapshot del precio |
| timestamp | TIMESTAMP NOT NULL | |

### cajas
| Columna | Tipo | Notas |
|---------|------|-------|
| id_caja | BIGSERIAL PK | |
| numero_caja | VARCHAR(50) NOT NULL UNIQUE | |
| id_usuario_apertura | BIGINT FK → usuarios | |
| id_usuario_cierre | BIGINT FK → usuarios | Nullable |
| fondo_inicial | NUMERIC(10,2) NOT NULL | |
| total_efectivo/tarjeta/ventas | NUMERIC(10,2) | |
| diferencia | NUMERIC(10,2) | |
| estado | VARCHAR(20) NOT NULL | |

### metodos_pago
| Columna | Tipo | Notas |
|---------|------|-------|
| id_metodo_pago | BIGSERIAL PK | |
| nombre | VARCHAR(50) NOT NULL UNIQUE | EFECTIVO es predeterminado |
| es_predeterminado | BOOLEAN DEFAULT FALSE | |
| orden | INTEGER DEFAULT 0 | |

> Todas las tablas incluyen: `activo`, `fecha_creacion`, `fecha_actualizacion`

---

## 12. Datos Iniciales (Seed)

Datos que Flyway inserta automáticamente (V2):

| Tipo | Datos |
|------|-------|
| **Admin** | usuario: `admin`, contraseña: `admin123` |
| **Cajero** | código: `01`, PIN: `1234` (hash BCrypt) |
| **Meseros** | códigos: `02`-`09`, PIN: `1234` (hash BCrypt) |
| **Categorías** | AGUARDIENTES, TEQUILAS, WHISKYS, PASANTES, COCTELES, CERVEZAS, BEBIDAS SIN ALCOHOL, COMIDAS |
| **Productos** | 5 por categoría = 40 productos totales |
| **Métodos pago** | EFECTIVO (predeterminado), DÉBITO, CRÉDITO, TRANSFERENCIA, QR, MIXTO |

---

## 13. Troubleshooting

### Error: "authentication failed"

```bash
# Verificar método de auth en pg_hba.conf
sudo cat /etc/postgresql/16/main/pg_hba.conf | grep -v "^#" | grep -v "^$"

# Si dice 'peer' para conexiones locales, cambiar a 'md5'
# O usar: psql -U kipu_admin -h localhost -d kipu_db
# (localhost fuerza conexión TCP en vez de socket Unix)
```

### Error: "database kipu_db does not exist"

```bash
# Verificar que existe
sudo -u postgres psql -c "\l" | grep kipu

# Crear si no existe
sudo -u postgres psql -f database/setup-database.sql
```

### Error: "permission denied for schema public"

```sql
-- Conectar como postgres a kipu_db
sudo -u postgres psql -d kipu_db

-- Re-otorgar permisos
GRANT ALL ON SCHEMA public TO kipu_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO kipu_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO kipu_admin;
```

### Error: "Flyway migration failed" / "relation already exists"

```sql
-- Opción A: Marcar la migración como exitosa manualmente
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (nextval('flyway_schema_history_s'), '1', 'crear tablas base', 'SQL', 'V1__crear_tablas_base.sql', NULL, 'kipu_admin', 0, TRUE);

-- Opción B: Reset completo de Flyway
DROP TABLE flyway_schema_history;
-- Luego reiniciar servidor con baseline-on-migrate: true (ya configurado)
```

### Error: "connection refused" (al iniciar servidor)

```bash
# Verificar que PostgreSQL está corriendo
sudo systemctl status postgresql

# Verificar que escucha en el puerto correcto
sudo ss -tlnp | grep 5432

# Verificar variables de entorno del servidor
echo $DB_HOST $DB_PORT $DB_NAME $DB_USER $DB_PASSWORD
```

### PostgreSQL no inicia

```bash
# Ver logs detallados
sudo journalctl -u postgresql --no-pager -n 50

# Verificar permisos del directorio de datos
ls -la /var/lib/postgresql/16/main/

# Verificar espacio en disco
df -h
```

### Conexión lenta o queries lentos

```sql
-- Ver queries activas lentas
SELECT pid, now() - query_start AS duracion, query
FROM pg_stat_activity
WHERE state = 'active' AND query_start < now() - interval '5 seconds';

-- Ver locks
SELECT l.pid, l.mode, l.granted, a.query
FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE NOT l.granted;

-- Analizar una query
EXPLAIN ANALYZE SELECT * FROM productos WHERE id_categoria = 1;
```

---

## 14. Comandos Rápidos de Referencia

```bash
# ---- ACCESO ----
sudo -u postgres psql                          # Entrar como superusuario
psql -U kipu_admin -d kipu_db -h localhost   # Entrar como app user

# ---- SETUP ----
sudo -u postgres psql -f database/setup-database.sql   # Setup inicial

# ---- BACKUP ----
pg_dump -U kipu_admin -h localhost -Fc kipu_db > backup.dump    # Backup
pg_restore -U kipu_admin -h localhost -d kipu_db --clean backup.dump  # Restore

# ---- RESET (SOLO DEV) ----
sudo -u postgres psql -c "DROP DATABASE IF EXISTS kipu_db;"
sudo -u postgres psql -f database/setup-database.sql
# Reiniciar servidor Spring Boot para que Flyway recree las tablas

# ---- SERVICIO ----
sudo systemctl start postgresql
sudo systemctl stop postgresql
sudo systemctl restart postgresql
sudo systemctl status postgresql

# ---- MONITOREO ----
psql -U kipu_admin -d kipu_db -h localhost -c "\dt"    # Ver tablas
psql -U kipu_admin -d kipu_db -h localhost -c "SELECT count(*) FROM usuarios;"
```
