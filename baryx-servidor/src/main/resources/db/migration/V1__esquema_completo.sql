-- Flyway migration: V1__esquema_completo.sql
-- Esquema completo + datos mínimos de Baryx POS
--
-- Tablas: usuarios, categorias, productos, mesas, pedidos,
--         lineas_pedido, metodos_pago, cajas, logs_criticos,
--         configuracion_sistema, sync_cola_pendiente,
--         ventas, pagos, lineas_venta
--
-- Datos iniciales:
--   - 1 Admin (admin / admin123)
--   - 1 Cajero (código 01, PIN 1234)
--   - 1 Mesero (código 02, PIN 1234)
--   - 1 Método de pago predeterminado (EFECTIVO, código 00)
--   - Configuración de idioma por defecto (es)
--   - Función CDC + triggers de sincronización

-- =====================================================
-- 1. TABLAS PRINCIPALES
-- =====================================================

CREATE TABLE usuarios (
    id_usuario      BIGSERIAL    PRIMARY KEY,
    nombre_usuario  VARCHAR(50)  UNIQUE,
    contrasena      VARCHAR(255),
    codigo          VARCHAR(2)   UNIQUE,
    pin             VARCHAR(255),
    nombre_completo VARCHAR(100) NOT NULL,
    email           VARCHAR(100),
    rol             VARCHAR(20)  NOT NULL,
    genero          VARCHAR(20)  NOT NULL,
    bloqueado       BOOLEAN      NOT NULL DEFAULT FALSE,
    intentos_fallidos INTEGER    NOT NULL DEFAULT 0,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT chk_rol    CHECK (rol    IN ('MESERO', 'CAJERO', 'ADMIN')),
    CONSTRAINT chk_genero CHECK (genero IN ('MASCULINO', 'FEMENINO'))
);

CREATE INDEX idx_usuarios_nombre ON usuarios(nombre_usuario);
CREATE INDEX idx_usuarios_codigo ON usuarios(codigo);

CREATE TABLE categorias (
    id_categoria BIGSERIAL    PRIMARY KEY,
    nombre       VARCHAR(100) NOT NULL UNIQUE,
    descripcion  VARCHAR(255),
    orden        INTEGER      NOT NULL DEFAULT 0,
    color        VARCHAR(7)   DEFAULT '#000000',
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP
);

CREATE TABLE productos (
    id_producto   BIGSERIAL    PRIMARY KEY,
    codigo        VARCHAR(50)  NOT NULL UNIQUE,
    nombre        VARCHAR(100) NOT NULL,
    descripcion   VARCHAR(255),
    precio        NUMERIC(10,2) NOT NULL,
    id_categoria  BIGINT       NOT NULL,
    stock_actual  INTEGER      NOT NULL DEFAULT 0,
    stock_minimo  INTEGER      NOT NULL DEFAULT 0,
    requiere_stock BOOLEAN     NOT NULL DEFAULT TRUE,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT fk_productos_categoria
        FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria)
);

CREATE TABLE mesas (
    id_mesa     BIGSERIAL   PRIMARY KEY,
    numero_mesa VARCHAR(10) NOT NULL UNIQUE,
    estado      VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    id_mesero   BIGINT,
    activo      BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT fk_mesas_mesero
        FOREIGN KEY (id_mesero) REFERENCES usuarios(id_usuario),
    CONSTRAINT chk_estado_mesa
        CHECK (estado IN ('DISPONIBLE', 'OCUPADA', 'RESERVADA'))
);

CREATE INDEX idx_mesas_numero ON mesas(numero_mesa);
CREATE INDEX idx_mesas_estado ON mesas(estado);
CREATE INDEX idx_mesas_mesero ON mesas(id_mesero);

CREATE TABLE pedidos (
    id_pedido BIGSERIAL      PRIMARY KEY,
    id_mesa   BIGINT         NOT NULL,
    total     NUMERIC(10,2)  NOT NULL DEFAULT 0,
    estado    VARCHAR(20)    NOT NULL DEFAULT 'ACTIVO',
    activo    BOOLEAN        NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT fk_pedidos_mesa
        FOREIGN KEY (id_mesa) REFERENCES mesas(id_mesa) ON DELETE CASCADE
);

CREATE INDEX idx_pedidos_mesa   ON pedidos(id_mesa);
CREATE INDEX idx_pedidos_estado ON pedidos(estado);
CREATE INDEX idx_pedidos_fecha  ON pedidos(fecha_creacion);

CREATE TABLE lineas_pedido (
    id_linea_pedido BIGSERIAL      PRIMARY KEY,
    id_pedido       BIGINT         NOT NULL,
    id_producto     BIGINT         NOT NULL,
    nombre_producto VARCHAR(100)   NOT NULL,
    precio_unitario NUMERIC(10,2)  NOT NULL,
    timestamp       TIMESTAMP      NOT NULL,
    activo          BOOLEAN        NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT fk_lineas_pedido
        FOREIGN KEY (id_pedido)  REFERENCES pedidos(id_pedido)   ON DELETE CASCADE,
    CONSTRAINT fk_lineas_producto
        FOREIGN KEY (id_producto) REFERENCES productos(id_producto)
);

CREATE INDEX idx_lineas_pedido_pedido    ON lineas_pedido(id_pedido);
CREATE INDEX idx_lineas_pedido_producto  ON lineas_pedido(id_producto);
CREATE INDEX idx_lineas_pedido_timestamp ON lineas_pedido(timestamp);

CREATE TABLE metodos_pago (
    id_metodo_pago   BIGSERIAL   PRIMARY KEY,
    codigo           VARCHAR(2),
    nombre           VARCHAR(50) NOT NULL UNIQUE,
    descripcion      VARCHAR(255),
    orden            INTEGER     NOT NULL DEFAULT 0,
    es_predeterminado BOOLEAN    NOT NULL DEFAULT FALSE,
    activo           BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP
);

CREATE INDEX idx_metodos_pago_nombre ON metodos_pago(nombre);
CREATE INDEX idx_metodos_pago_activo ON metodos_pago(activo);
CREATE UNIQUE INDEX idx_metodos_pago_codigo_activo
    ON metodos_pago (codigo) WHERE activo = true;

CREATE TABLE cajas (
    id_caja              BIGSERIAL    PRIMARY KEY,
    numero_caja          VARCHAR(50)  NOT NULL UNIQUE,
    id_usuario_apertura  BIGINT       NOT NULL,
    id_usuario_cierre    BIGINT,
    fecha_apertura       TIMESTAMP    NOT NULL,
    fecha_cierre         TIMESTAMP,
    fondo_inicial        NUMERIC(10,2) NOT NULL,
    total_efectivo       NUMERIC(10,2),
    total_tarjeta        NUMERIC(10,2),
    total_ventas         NUMERIC(10,2),
    diferencia           NUMERIC(10,2),
    estado               VARCHAR(20)  NOT NULL,
    observaciones        VARCHAR(500),
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT fk_cajas_usuario_apertura
        FOREIGN KEY (id_usuario_apertura) REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_cajas_usuario_cierre
        FOREIGN KEY (id_usuario_cierre)   REFERENCES usuarios(id_usuario)
);

-- =====================================================
-- 2. LOGS CRÍTICOS
-- =====================================================

CREATE TABLE logs_criticos (
    id_log          BIGSERIAL PRIMARY KEY,
    nivel           VARCHAR(20)   NOT NULL,
    origen          VARCHAR(100)  NOT NULL,
    mensaje         VARCHAR(500)  NOT NULL,
    detalle         TEXT,
    usuario         VARCHAR(100),
    ip_cliente      VARCHAR(45),
    nombre_cliente  VARCHAR(100),
    estado          VARCHAR(30)   NOT NULL DEFAULT 'NOTIFICACION_ERROR',
    fecha_creacion  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_logs_criticos_fecha ON logs_criticos (fecha_creacion DESC);
CREATE INDEX idx_logs_criticos_nivel ON logs_criticos (nivel);
CREATE INDEX idx_logs_criticos_estado ON logs_criticos (estado);

COMMENT ON TABLE logs_criticos IS 'Almacena errores críticos del sistema para diagnóstico y mejoras';
COMMENT ON COLUMN logs_criticos.estado IS 'Estado del log: NOTIFICACION_ERROR, EN_REVISION, RESUELTO';

-- =====================================================
-- 3. CONFIGURACIÓN DEL SISTEMA
-- =====================================================

CREATE TABLE configuracion_sistema (
    id_configuracion  BIGSERIAL    PRIMARY KEY,
    clave             VARCHAR(100) NOT NULL UNIQUE,
    valor             VARCHAR(500) NOT NULL,
    descripcion       VARCHAR(300),
    fecha_creacion    TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_configuracion_clave ON configuracion_sistema (clave);

COMMENT ON TABLE configuracion_sistema IS 'Configuraciones globales del sistema, compartidas entre todos los clientes';

-- =====================================================
-- 4. SINCRONIZACIÓN CON NUBE (Outbox + CDC)
-- =====================================================

CREATE TABLE sync_cola_pendiente (
    id                    BIGSERIAL     PRIMARY KEY,
    tabla_origen          VARCHAR(100)  NOT NULL,
    operacion             VARCHAR(10)   NOT NULL,
    id_registro           BIGINT        NOT NULL,
    datos_json            JSONB         NOT NULL,
    fecha_creacion        TIMESTAMP     NOT NULL DEFAULT NOW(),
    sincronizado          BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_sincronizacion  TIMESTAMP,
    intentos_fallidos     INTEGER       NOT NULL DEFAULT 0,
    error                 BOOLEAN       NOT NULL DEFAULT FALSE,
    detalle_error         VARCHAR(500)
);

CREATE INDEX idx_sync_pendiente ON sync_cola_pendiente (sincronizado, fecha_creacion)
    WHERE sincronizado = FALSE AND error = FALSE;

CREATE INDEX idx_sync_limpieza ON sync_cola_pendiente (sincronizado, fecha_sincronizacion)
    WHERE sincronizado = TRUE;

COMMENT ON TABLE sync_cola_pendiente IS 'Cola de eventos para sincronización con MongoDB Atlas (Outbox pattern)';

-- Función genérica CDC (excluye campos sensibles de usuarios)
CREATE OR REPLACE FUNCTION fn_sync_capturar_cambio()
RETURNS TRIGGER AS $$
DECLARE
    v_id_registro BIGINT;
    v_datos       JSONB;
BEGIN
    IF TG_TABLE_NAME = 'usuarios' THEN
        IF TG_OP = 'DELETE' THEN
            v_id_registro := OLD.id_usuario;
            v_datos := jsonb_build_object(
                'id_usuario', OLD.id_usuario,
                'nombre_usuario', OLD.nombre_usuario,
                'codigo', OLD.codigo,
                'nombre_completo', OLD.nombre_completo,
                'email', OLD.email,
                'rol', OLD.rol,
                'genero', OLD.genero,
                'bloqueado', OLD.bloqueado,
                'activo', OLD.activo,
                'fecha_creacion', OLD.fecha_creacion,
                'fecha_actualizacion', OLD.fecha_actualizacion
            );
        ELSE
            v_id_registro := NEW.id_usuario;
            v_datos := jsonb_build_object(
                'id_usuario', NEW.id_usuario,
                'nombre_usuario', NEW.nombre_usuario,
                'codigo', NEW.codigo,
                'nombre_completo', NEW.nombre_completo,
                'email', NEW.email,
                'rol', NEW.rol,
                'genero', NEW.genero,
                'bloqueado', NEW.bloqueado,
                'activo', NEW.activo,
                'fecha_creacion', NEW.fecha_creacion,
                'fecha_actualizacion', NEW.fecha_actualizacion
            );
        END IF;
    ELSE
        IF TG_OP = 'DELETE' THEN
            v_datos := to_jsonb(OLD);
            v_id_registro := (to_jsonb(OLD) ->> (
                SELECT column_name FROM information_schema.columns
                WHERE table_name = TG_TABLE_NAME
                  AND ordinal_position = 1
            ))::BIGINT;
        ELSE
            v_datos := to_jsonb(NEW);
            v_id_registro := (to_jsonb(NEW) ->> (
                SELECT column_name FROM information_schema.columns
                WHERE table_name = TG_TABLE_NAME
                  AND ordinal_position = 1
            ))::BIGINT;
        END IF;
    END IF;

    INSERT INTO sync_cola_pendiente (tabla_origen, operacion, id_registro, datos_json)
    VALUES (TG_TABLE_NAME, TG_OP, v_id_registro, v_datos);

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_sync_capturar_cambio() IS 'CDC genérico: captura cambios y los inserta en sync_cola_pendiente';

-- Triggers en tablas monitoreadas
CREATE TRIGGER trg_sync_usuarios
    AFTER INSERT OR UPDATE OR DELETE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_categorias
    AFTER INSERT OR UPDATE OR DELETE ON categorias
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_productos
    AFTER INSERT OR UPDATE OR DELETE ON productos
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_mesas
    AFTER INSERT OR UPDATE OR DELETE ON mesas
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_pedidos
    AFTER INSERT OR UPDATE OR DELETE ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_lineas_pedido
    AFTER INSERT OR UPDATE OR DELETE ON lineas_pedido
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_metodos_pago
    AFTER INSERT OR UPDATE OR DELETE ON metodos_pago
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_cajas
    AFTER INSERT OR UPDATE OR DELETE ON cajas
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_logs_criticos
    AFTER INSERT OR UPDATE OR DELETE ON logs_criticos
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

CREATE TRIGGER trg_sync_configuracion_sistema
    AFTER INSERT OR UPDATE OR DELETE ON configuracion_sistema
    FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();

-- =====================================================
-- 5. VENTAS, PAGOS Y LÍNEAS DE VENTA
-- =====================================================

CREATE TABLE ventas (
    id_venta             BIGSERIAL       PRIMARY KEY,
    id_mesa              BIGINT,
    numero_mesa          VARCHAR(10)     NOT NULL,
    id_mesero            BIGINT,
    nombre_mesero        VARCHAR(100),
    id_cajero            BIGINT,
    nombre_cajero        VARCHAR(100),
    subtotal             NUMERIC(10,2)   NOT NULL,
    impoconsumo          NUMERIC(10,2)   NOT NULL DEFAULT 0,
    propina              NUMERIC(10,2)   NOT NULL DEFAULT 0,
    total                NUMERIC(10,2)   NOT NULL,
    estado               VARCHAR(20)     NOT NULL DEFAULT 'COMPLETADA',
    activo               BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT fk_ventas_mesero
        FOREIGN KEY (id_mesero) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    CONSTRAINT fk_ventas_cajero
        FOREIGN KEY (id_cajero) REFERENCES usuarios(id_usuario) ON DELETE SET NULL
);

CREATE TABLE pagos (
    id_pago              BIGSERIAL       PRIMARY KEY,
    id_venta             BIGINT          NOT NULL,
    id_metodo_pago       BIGINT,
    nombre_metodo_pago   VARCHAR(50)     NOT NULL,
    monto                NUMERIC(10,2)   NOT NULL,
    propina              NUMERIC(10,2)   NOT NULL DEFAULT 0,
    activo               BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT fk_pagos_venta
        FOREIGN KEY (id_venta) REFERENCES ventas(id_venta) ON DELETE CASCADE,
    CONSTRAINT fk_pagos_metodo_pago
        FOREIGN KEY (id_metodo_pago) REFERENCES metodos_pago(id_metodo_pago) ON DELETE SET NULL
);

CREATE TABLE lineas_venta (
    id_linea_venta       BIGSERIAL       PRIMARY KEY,
    id_venta             BIGINT          NOT NULL,
    id_producto          BIGINT,
    nombre_producto      VARCHAR(100)    NOT NULL,
    precio_unitario      NUMERIC(10,2)   NOT NULL,
    cantidad             INTEGER         NOT NULL DEFAULT 1,
    activo               BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  TIMESTAMP,
    CONSTRAINT fk_lineas_venta_venta
        FOREIGN KEY (id_venta) REFERENCES ventas(id_venta) ON DELETE CASCADE,
    CONSTRAINT fk_lineas_venta_producto
        FOREIGN KEY (id_producto) REFERENCES productos(id_producto) ON DELETE SET NULL
);

CREATE INDEX idx_ventas_fecha ON ventas(fecha_creacion);
CREATE INDEX idx_ventas_estado ON ventas(estado);
CREATE INDEX idx_ventas_mesero ON ventas(id_mesero);
CREATE INDEX idx_ventas_cajero ON ventas(id_cajero);
CREATE INDEX idx_pagos_venta ON pagos(id_venta);
CREATE INDEX idx_lineas_venta_venta ON lineas_venta(id_venta);

-- =====================================================
-- 6. DATOS INICIALES (SEED)
-- =====================================================

INSERT INTO usuarios (nombre_usuario, contrasena, nombre_completo, email, rol, genero, activo)
VALUES ('admin',
        '$2a$10$7.OLE2A8tNJ7tv4MXIdftu.o7osbWfsECzldRmV2G7zuSK9pc0tIC',
        'Administrador', 'admin@baryx.org', 'ADMIN', 'MASCULINO', true);

-- Cajero (código: 01 / PIN: 1234)
INSERT INTO usuarios (codigo, pin, nombre_completo, rol, genero, activo)
VALUES ('01',
        '$2a$10$d1dth5jTESZGDf0m6rFA3u.364PPQOP0UOWc4TKy7Og9vJn42ZfwS',
        'Cajero', 'CAJERO', 'MASCULINO', true);

-- Mesero (código: 02 / PIN: 1234)
INSERT INTO usuarios (codigo, pin, nombre_completo, rol, genero, activo)
VALUES ('02',
        '$2a$10$d1dth5jTESZGDf0m6rFA3u.364PPQOP0UOWc4TKy7Og9vJn42ZfwS',
        'Mesero', 'MESERO', 'MASCULINO', true);

-- Método de pago predeterminado (EFECTIVO, código 00)
INSERT INTO metodos_pago (codigo, nombre, descripcion, orden, es_predeterminado, activo)
VALUES ('00', 'EFECTIVO', 'Pago en efectivo - Método predeterminado del sistema', 0, true, true);

-- Idioma por defecto del sistema
INSERT INTO configuracion_sistema (clave, valor, descripcion)
VALUES ('idioma', 'es', 'Código ISO 639-1 del idioma de la interfaz (es, en, pt)');
