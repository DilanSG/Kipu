#!/bin/bash
# =========================================================
# Kipu - Iniciar Servidor (consola)
# =========================================================
# Lanza el servidor Spring Boot desde terminal mostrando logs.
# Ubicacion esperada: /opt/kipu/iniciar-servidor.sh
#
# Para la configuracion inicial (PostgreSQL, .env):
#   sudo /opt/kipu/servidor/setup-inicial.sh
# =========================================================

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo -e "${YELLOW}╔══════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║    KIPU - Servidor (REST API)       ║${NC}"
echo -e "${YELLOW}╚══════════════════════════════════════╝${NC}"
echo ""

INSTALL_DIR="/opt/kipu"
SERVIDOR_BIN="$INSTALL_DIR/servidor/bin/KipuServidor"

# Verificar que el ejecutable existe
if [ ! -f "$SERVIDOR_BIN" ]; then
    echo -e "${RED}[ERROR]${NC} No se encontro el ejecutable del servidor en:"
    echo "  $SERVIDOR_BIN"
    echo ""
    echo "Instale Kipu primero: sudo dpkg -i kipu_*.deb"
    exit 1
fi

# Verificar configuracion
if [ ! -f "$HOME/.kipu/.env" ]; then
    echo -e "${YELLOW}[AVISO]${NC} No se encontro archivo de configuracion (~/.kipu/.env)"
    echo "  Ejecute primero: sudo /opt/kipu/servidor/setup-inicial.sh"
    echo ""
    read -p "¿Desea continuar de todas formas? [s/N]: " resp
    [[ ! "$resp" =~ ^[sS]$ ]] && exit 0
    echo ""
fi

# Cargar .env
if [ -f "$HOME/.kipu/.env" ]; then
    echo -e "${BLUE}▶${NC} Cargando configuracion desde ~/.kipu/.env"
    set -a
    source "$HOME/.kipu/.env"
    set +a
fi

echo -e "${BLUE}▶${NC} Puerto: ${SERVER_PORT:-8080}"
echo ""
echo -e "${GREEN}✔${NC} Iniciando servidor..."
echo -e "${BLUE}▶${NC} Los logs se mostraran en esta terminal"
echo -e "${BLUE}▶${NC} Presione Ctrl+C para detener"
echo ""

exec "$SERVIDOR_BIN" "$@"
