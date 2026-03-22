-- =========================================================
-- Baryx - LIMPIEZA TOTAL de PostgreSQL
-- =========================================================
-- Este script ELIMINA todas las bases de datos y usuarios
-- relacionados con Baryx para empezar desde cero.
--
-- Ejecutar como superusuario (postgres):
--   sudo -u postgres psql -f database/limpiar-todo.sql
--
-- ADVERTENCIA: Se perderán TODOS los datos de todas las BD Baryx.
-- =========================================================

-- Paso 1: Desconectar a todos los clientes de las BDs Baryx
-- (necesario para poder eliminar las bases de datos)

SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname IN ('baryx_db', 'baryx_bar1', 'baryx_new', 'BaryxDB')
  AND pid <> pg_backend_pid();

-- Paso 2: Eliminar todas las bases de datos Baryx

DROP DATABASE IF EXISTS baryx_db;
DROP DATABASE IF EXISTS baryx_bar1;
DROP DATABASE IF EXISTS baryx_new;
DROP DATABASE IF EXISTS "BaryxDB";

-- Paso 3: Eliminar todos los usuarios/roles Baryx

DROP ROLE IF EXISTS baryx_admin;
DROP ROLE IF EXISTS baryx_user;
DROP ROLE IF EXISTS baryxadmin;

-- Paso 4: Verificar que todo quedó limpio

DO $$
DECLARE
    db_count INTEGER;
    user_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO db_count
    FROM pg_database
    WHERE datname LIKE 'baryx%' OR datname = 'BaryxDB';

    SELECT COUNT(*) INTO user_count
    FROM pg_roles
    WHERE rolname IN ('baryx_admin', 'baryx_user', 'baryxadmin');

    IF db_count = 0 AND user_count = 0 THEN
        RAISE NOTICE '';
        RAISE NOTICE '=============================================';
        RAISE NOTICE '  LIMPIEZA COMPLETADA EXITOSAMENTE';
        RAISE NOTICE '=============================================';
        RAISE NOTICE '  Bases de datos eliminadas: OK';
        RAISE NOTICE '  Usuarios eliminados: OK';
        RAISE NOTICE '';
        RAISE NOTICE '  Siguiente paso:';
        RAISE NOTICE '    sudo -u postgres psql -f database/setup-database.sql';
        RAISE NOTICE '=============================================';
    ELSE
        RAISE WARNING '';
        RAISE WARNING '=============================================';
        RAISE WARNING '  LIMPIEZA INCOMPLETA';
        RAISE WARNING '  BDs restantes: %', db_count;
        RAISE WARNING '  Usuarios restantes: %', user_count;
        RAISE WARNING '=============================================';
    END IF;
END $$;
