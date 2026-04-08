-- =========================================================
-- Kipu - Configuración inicial de PostgreSQL
-- =========================================================
-- Crea el usuario y la base de datos desde cero.
--
-- Ejecutar como superusuario (postgres):
--   sudo -u postgres psql -f database/setup-database.sql
--
-- Si ya existían datos previos, ejecutar primero:
--   sudo -u postgres psql -f database/limpiar-todo.sql
--
-- Variables usadas (deben coincidir con .env):
--   DB_NAME     = kipu_db
--   DB_USER     = kipu_admin
--   DB_PASSWORD = (la que definas abajo)
-- =========================================================

-- =========================================================
-- 1. CREAR USUARIO DE APLICACIÓN
-- =========================================================
-- Un solo usuario dueño de todo: kipu_admin
-- Cambiar la contraseña en producción.

CREATE USER kipu_admin WITH
    ENCRYPTED PASSWORD 'kipu2026'
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE;

COMMENT ON ROLE kipu_admin IS 'Usuario de la aplicación Kipu (dueño de kipu_db)';

-- =========================================================
-- 2. CREAR BASE DE DATOS
-- =========================================================
-- Una sola BD con kipu_admin como dueño directo.
-- Flyway creará las tablas automáticamente al arrancar el servidor.

CREATE DATABASE kipu_db
    WITH
    OWNER = kipu_admin
    ENCODING = 'UTF8'
    LC_COLLATE = 'es_CO.UTF-8'
    LC_CTYPE = 'es_CO.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE kipu_db IS 'Base de datos del sistema POS Kipu';

-- =========================================================
-- 3. CONFIGURAR PERMISOS EN SCHEMA PUBLIC
-- =========================================================
-- Conectar a la BD recién creada para configurar el schema.

\c kipu_db;

-- kipu_admin es dueño del schema public
ALTER SCHEMA public OWNER TO kipu_admin;

-- Permisos completos (por si ya hubiera objetos)
GRANT ALL ON SCHEMA public TO kipu_admin;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO kipu_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO kipu_admin;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO kipu_admin;

-- Permisos por defecto para objetos futuros creados por postgres
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO kipu_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO kipu_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO kipu_admin;

-- =========================================================
-- 4. VERIFICACIÓN
-- =========================================================

-- Verificar BD
SELECT datname, datdba::regrole AS dueno, encoding, datcollate
FROM pg_database
WHERE datname = 'kipu_db';

-- Verificar usuario
SELECT rolname, rolcanlogin, rolsuper, rolcreatedb
FROM pg_roles
WHERE rolname = 'kipu_admin';

-- Verificar dueño del schema
SELECT schema_name, schema_owner
FROM information_schema.schemata
WHERE schema_name = 'public';

-- =========================================================
-- NOTAS
-- =========================================================
-- 1. Las tablas se crean automáticamente con Flyway al iniciar el servidor.
-- 2. El usuario kipu_admin es DUEÑO de la BD y del schema public,
--    así que tiene permisos totales sin necesidad de GRANT adicional.
-- 3. Valores por defecto del .env:
--      DB_HOST=127.0.0.1
--      DB_PORT=5432
--      DB_NAME=kipu_db
--      DB_USER=kipu_admin
--      DB_PASSWORD=kipu2026
-- 4. Para ver tablas después de arrancar el servidor:
--      sudo -u postgres psql -d kipu_db -c "\dt"