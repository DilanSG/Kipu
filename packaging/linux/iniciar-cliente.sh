#!/bin/bash
# =========================================================
# Kipu - Iniciar Cliente (consola)
# =========================================================
# Lanza el cliente JavaFX desde terminal mostrando logs.
# Ubicacion esperada: /opt/kipu/iniciar-cliente.sh
# =========================================================

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo -e "${YELLOW}╔══════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║    KIPU - Cliente (POS / Comandera) ║${NC}"
echo -e "${YELLOW}╚══════════════════════════════════════╝${NC}"
echo ""

INSTALL_DIR="/opt/kipu"
CLIENTE_BIN="$INSTALL_DIR/cliente/bin/Kipu"

# Verificar que el ejecutable existe
if [ ! -f "$CLIENTE_BIN" ]; then
    echo -e "${RED}[ERROR]${NC} No se encontro el ejecutable del cliente en:"
    echo "  $CLIENTE_BIN"
    echo ""
    echo "Instale Kipu primero: sudo dpkg -i kipu_*.deb"
    exit 1
fi

# Cargar .env si existe (para obtener SERVER_URL)
if [ -f "$HOME/.kipu/.env" ]; then
    echo -e "${BLUE}▶${NC} Cargando configuracion desde ~/.kipu/.env"
    set -a
    source "$HOME/.kipu/.env"
    set +a
fi

export SERVER_URL="${SERVER_URL:-http://localhost:8080}"

echo -e "${BLUE}▶${NC} URL del servidor: $SERVER_URL"
echo ""
echo -e "${GREEN}✔${NC} Iniciando cliente..."
echo -e "${BLUE}▶${NC} Los logs se mostraran en esta terminal"
echo -e "${BLUE}▶${NC} Presione Ctrl+C para detener"
echo ""

exec "$CLIENTE_BIN" "$@"
