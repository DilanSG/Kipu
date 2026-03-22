#!/bin/bash
# =========================================================
# Baryx - Iniciar Servidor (consola)
# =========================================================
# Lanza el servidor Spring Boot desde terminal mostrando logs.
# Ubicacion esperada: /opt/baryx/iniciar-servidor.sh
#
# Para la configuracion inicial (PostgreSQL, .env):
#   sudo /opt/baryx/servidor/setup-inicial.sh
# =========================================================

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo -e "${YELLOW}╔══════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║    BARYX - Servidor (REST API)       ║${NC}"
echo -e "${YELLOW}╚══════════════════════════════════════╝${NC}"
echo ""

INSTALL_DIR="/opt/baryx"
SERVIDOR_BIN="$INSTALL_DIR/servidor/bin/BaryxServidor"

# Verificar que el ejecutable existe
if [ ! -f "$SERVIDOR_BIN" ]; then
    echo -e "${RED}[ERROR]${NC} No se encontro el ejecutable del servidor en:"
    echo "  $SERVIDOR_BIN"
    echo ""
    echo "Instale Baryx primero: sudo dpkg -i baryx_*.deb"
    exit 1
fi

# Verificar configuracion
if [ ! -f "$HOME/.baryx/.env" ]; then
    echo -e "${YELLOW}[AVISO]${NC} No se encontro archivo de configuracion (~/.baryx/.env)"
    echo "  Ejecute primero: sudo /opt/baryx/servidor/setup-inicial.sh"
    echo ""
    read -p "¿Desea continuar de todas formas? [s/N]: " resp
    [[ ! "$resp" =~ ^[sS]$ ]] && exit 0
    echo ""
fi

# Cargar .env
if [ -f "$HOME/.baryx/.env" ]; then
    echo -e "${BLUE}▶${NC} Cargando configuracion desde ~/.baryx/.env"
    set -a
    source "$HOME/.baryx/.env"
    set +a
fi

echo -e "${BLUE}▶${NC} Puerto: ${SERVER_PORT:-8080}"
echo ""
echo -e "${GREEN}✔${NC} Iniciando servidor..."
echo -e "${BLUE}▶${NC} Los logs se mostraran en esta terminal"
echo -e "${BLUE}▶${NC} Presione Ctrl+C para detener"
echo ""

exec "$SERVIDOR_BIN" "$@"
