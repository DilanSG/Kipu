-- =========================================================
-- Kipu - Reset de datos (conserva la BD y el usuario)
-- =========================================================
-- Elimina todas las tablas y datos dentro de kipu_db para
-- que Flyway las recree al próximo arranque del servidor.
--
-- Ejecutar:
--   sudo -u postgres psql -d kipu_db -f database/reset-database.sql
--
-- ADVERTENCIA: Se perderán TODOS los datos. Solo para desarrollo.
-- =========================================================

-- Paso 1: Eliminar todas las tablas (descomentar para ejecutar)
-- Las tablas se listan en orden inverso de dependencias.

-- DESCOMENTAR PARA EJECUTAR:
/*
DROP TABLE IF EXISTS lineas_venta CASCADE;
DROP TABLE IF EXISTS pagos CASCADE;
DROP TABLE IF EXISTS ventas CASCADE;
DROP TABLE IF EXISTS sync_cola_pendiente CASCADE;
DROP TABLE IF EXISTS configuracion_sistema CASCADE;
DROP TABLE IF EXISTS logs_criticos CASCADE;
DROP TABLE IF EXISTS lineas_pedido CASCADE;
DROP TABLE IF EXISTS detalles_pedido CASCADE;
DROP TABLE IF EXISTS metodos_pago CASCADE;
DROP TABLE IF EXISTS pedidos CASCADE;
DROP TABLE IF EXISTS mesas CASCADE;
DROP TABLE IF EXISTS productos CASCADE;
DROP TABLE IF EXISTS categorias CASCADE;
DROP TABLE IF EXISTS movimientos_caja CASCADE;
DROP TABLE IF EXISTS cajas CASCADE;
DROP TABLE IF EXISTS auditoria CASCADE;
DROP TABLE IF EXISTS usuarios CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;
DROP FUNCTION IF EXISTS fn_sync_capturar_cambio() CASCADE;
*/

-- Paso 2: Verificar que no quedan tablas

SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

-- =========================================================
-- Después de ejecutar este script:
--   1. Reiniciar el servidor para que Flyway recree las tablas
--   2. La migración V1 se ejecutará automáticamente
--   3. Se crearán los usuarios por defecto (admin, cajero, mesero)
--   4. Se creará el método de pago EFECTIVO
--   5. Se insertará la configuración de idioma
-- =========================================================
