#!/bin/bash
# Kipu - Setup Inicial y Arranque del Servidor Linux
# Script unificado que:
#   1. Configura PostgreSQL y genera el archivo .env
#      (solo la primera vez o si se elige reconfigurar)
#   2. Arranca el servidor usando el JRE embebido
# El archivo .env se guarda en ~/.kipu/.env y el servidor
# lo detecta automaticamente via CargadorArchivoEnv.
# Requisitos:
#   - PostgreSQL instalado y en ejecucion (para el setup)
#   - psql disponible en el PATH (para el setup)
set -e

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; NC='\033[0m'

paso()  { echo -e "${CYAN}[PASO]${NC} $1"; }
ok()    { echo -e "${GREEN}[OK]${NC} $1"; }
info()  { echo -e "${YELLOW}[INFO]${NC} $1"; }
err()   { echo -e "${RED}[ERROR]${NC} $1"; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_DIR="$HOME/.kipu"
ENV_FILE="$CONFIG_DIR/.env"

leer_no_vacio() {
    local prompt="$1" defecto="$2" valor
    while true; do
        read -p "$prompt [$defecto]: " valor
        valor="${valor:-$defecto}"
        [ -n "$valor" ] && echo "$valor" && return
        err "Debe ingresar un valor valido."
    done
}

leer_clave_confirmada() {
    while true; do
        read -sp "$1: " c1; echo ""
        read -sp "$2: " c2; echo ""
        [ -z "$c1" ] && err "La contrasena no puede estar vacia." && continue
        [ "$c1" != "$c2" ] && err "Las contrasenas no coinciden." && continue
        echo "$c1"; return
    done
}

escapar_sql() { echo "${1//\'/\'\'}"; }

ejecutar_psql() {
    local srv="$1" port="$2" db="$3" usr="$4" pw="$5" sql="$6" abort="${7:-false}"
    local tmp; tmp=$(mktemp); echo "$sql" > "$tmp"
    local rc=0
    if [ -z "$pw" ]; then
        psql -v ON_ERROR_STOP=1 -h "$srv" -p "$port" -U "$usr" -d "$db" -f "$tmp" 2>&1 || rc=$?
    else
        PGPASSWORD="$pw" psql -v ON_ERROR_STOP=1 -h "$srv" -p "$port" -U "$usr" -d "$db" -f "$tmp" 2>&1 || rc=$?
    fi
    rm -f "$tmp"
    [ "$abort" = "true" ] && [ "$rc" -ne 0 ] && err "psql devolvio codigo $rc" && exit 1
    return $rc
}

ejecutar_setup() {
    if ! command -v psql &> /dev/null; then
        err "No se encontro psql en PATH."
        echo "  Instale: sudo apt install postgresql-client"
        exit 1
    fi

    paso "Datos de conexion a PostgreSQL (superusuario)"
    PG_HOST=$(leer_no_vacio "Host PostgreSQL" "localhost")
    PG_PUERTO=$(leer_no_vacio "Puerto PostgreSQL" "5432")
    PG_SUPER_USUARIO=$(leer_no_vacio "Usuario administrador PostgreSQL" "postgres")

    read -p "El usuario administrador requiere contrasena? (S/n): " USAR_CLAVE
    PG_SUPER_CLAVE=""
    if [ "$USAR_CLAVE" != "n" ] && [ "$USAR_CLAVE" != "N" ]; then
        read -sp "Contrasena de $PG_SUPER_USUARIO: " PG_SUPER_CLAVE; echo ""
    fi

    paso "Datos de la base de datos de Kipu"
    DB_NOMBRE=$(leer_no_vacio "Nombre de la base de datos" "kipu_db")
    DB_USUARIO=$(leer_no_vacio "Usuario de aplicacion" "kipu_admin")
    DB_CLAVE=$(leer_clave_confirmada "Contrasena del usuario de aplicacion" "Confirmar contrasena")

    paso "Validando conexion como administrador PostgreSQL"
    ejecutar_psql "$PG_HOST" "$PG_PUERTO" "postgres" "$PG_SUPER_USUARIO" "$PG_SUPER_CLAVE" "SELECT version();" "true" > /dev/null
    ok "Conexion de administrador validada"

    U_ESC=$(escapar_sql "$DB_USUARIO")
    C_ESC=$(escapar_sql "$DB_CLAVE")
    D_ESC=$(escapar_sql "$DB_NOMBRE")

    paso "Creando/actualizando usuario de aplicacion"
    ejecutar_psql "$PG_HOST" "$PG_PUERTO" "postgres" "$PG_SUPER_USUARIO" "$PG_SUPER_CLAVE" \
        "CREATE ROLE \"$U_ESC\" WITH LOGIN PASSWORD '$C_ESC' NOSUPERUSER NOCREATEDB NOCREATEROLE;" || true
    ejecutar_psql "$PG_HOST" "$PG_PUERTO" "postgres" "$PG_SUPER_USUARIO" "$PG_SUPER_CLAVE" \
        "ALTER ROLE \"$U_ESC\" WITH PASSWORD '$C_ESC';" "true" > /dev/null
    ok "Usuario de aplicacion listo"

    paso "Creando/actualizando base de datos"
    ejecutar_psql "$PG_HOST" "$PG_PUERTO" "postgres" "$PG_SUPER_USUARIO" "$PG_SUPER_CLAVE" \
        "CREATE DATABASE \"$D_ESC\" OWNER \"$U_ESC\" ENCODING 'UTF8';" || true
    ejecutar_psql "$PG_HOST" "$PG_PUERTO" "postgres" "$PG_SUPER_USUARIO" "$PG_SUPER_CLAVE" \
        "ALTER DATABASE \"$D_ESC\" OWNER TO \"$U_ESC\";" "true" > /dev/null
    ok "Base de datos lista"

    paso "Aplicando permisos sobre schema public"
    ejecutar_psql "$PG_HOST" "$PG_PUERTO" "$DB_NOMBRE" "$PG_SUPER_USUARIO" "$PG_SUPER_CLAVE" "
ALTER SCHEMA public OWNER TO \"$U_ESC\";
GRANT ALL ON SCHEMA public TO \"$U_ESC\";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"$U_ESC\";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"$U_ESC\";
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO \"$U_ESC\";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"$U_ESC\";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO \"$U_ESC\";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO \"$U_ESC\";
" "true" > /dev/null
    ok "Permisos aplicados"

    paso "Generando archivo de configuracion .env"
    mkdir -p "$CONFIG_DIR"
    JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
    # Usar comillas simples en el heredoc (<<'EOF') para evitar expansion
    # de variables durante la escritura, y comillas simples en los valores
    # para evitar que source interprete caracteres especiales en claves.
    cat > "$ENV_FILE" << KIPUEOF
# Configuracion Kipu - Generado $(date '+%Y-%m-%d %H:%M:%S')
DB_HOST='${PG_HOST}'
DB_PORT='${PG_PUERTO}'
DB_NAME='${DB_NOMBRE}'
DB_USER='${DB_USUARIO}'
DB_PASSWORD='${DB_CLAVE}'
JWT_SECRET='${JWT_SECRET}'
KIPUEOF
    chmod 600 "$ENV_FILE"
    ok "Archivo .env creado en: $ENV_FILE"
    echo -e "${GREEN}  Setup completado exitosamente         ${NC}"
}


iniciar_servidor() {
    if [ -f "$ENV_FILE" ]; then
        set -a; source "$ENV_FILE"; set +a
    elif [ -f "$SCRIPT_DIR/.env" ]; then
        set -a; source "$SCRIPT_DIR/.env"; set +a
    else
        err "No se encontro configuracion (.env)."
        echo "  Se requiere ejecutar el setup primero."
        exit 1
    fi
    local EXE="$SCRIPT_DIR/bin/KipuServidor"
    if [ ! -f "$EXE" ]; then
        err "No se encontro el ejecutable del servidor: $EXE"
        exit 1
    fi

    echo -e "${YELLOW}KIPU - Servidor API REST${NC}"
    echo -e "  BD   : ${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-kipu_db}"
    echo -e "  User : ${DB_USER:-kipu_admin}"
    echo ""
    echo -e "  Iniciando... (Ctrl+C para detener)"
    echo ""

    exec "$EXE" "$@"
}
echo -e "${YELLOW}KIPU - Setup Inicial${NC}"

if [ -f "$ENV_FILE" ]; then
    info "Configuracion existente detectada: $ENV_FILE"
    echo ""
    read -p "Desea reconfigurar la base de datos? (s/N): " RESP_RECONFIG
    if [ "$RESP_RECONFIG" = "s" ] || [ "$RESP_RECONFIG" = "S" ]; then
        ejecutar_setup
    else
        info "Usando configuracion existente."
    fi
else
    info "Primera ejecucion — iniciando configuracion de la base de datos."
    echo ""
    ejecutar_setup
fi

echo ""
read -p "Desea iniciar el servidor ahora? (S/n): " RESP_INICIAR
if [ "$RESP_INICIAR" != "n" ] && [ "$RESP_INICIAR" != "N" ]; then
    iniciar_servidor "$@"
else
    info "Para iniciar el servidor mas adelante, ejecute este mismo script o inicie el cliente que lo hará automáticamente con esta configuración."
fi
