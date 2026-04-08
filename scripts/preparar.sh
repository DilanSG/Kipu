#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  KIPU — Preparar entorno de desarrollo
#  Limpia, compila, empaca e instala todos los módulos.
#  Despliega el JAR del servidor en /opt/kipu/servidor/lib/app/
#  Después de ejecutar este script, solo haz:
#    mvn javafx:run -pl kipu-cliente
# ═══════════════════════════════════════════════════════════════
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
GOLD='\033[1;33m'
DIM='\033[2m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVIDOR_DEPLOY="/opt/kipu/servidor/lib/app"
JAR_SERVIDOR="kipu-servidor-1.0.0.jar"

paso()    { echo -e "\n${GOLD}[$1/5]${NC} ${BLUE}$2${NC}"; }
ok()      { echo -e "      ${GREEN}✓${NC} $1"; }
error()   { echo -e "      ${RED}✗${NC} $1"; exit 1; }

# ── Banner ──
echo -e "${GOLD}"
echo "  ╔══════════════════════════════════════╗"
echo "  ║     KIPU — Preparar Entorno         ║"
echo "  ╚══════════════════════════════════════╝"
echo -e "${NC}"

cd "$PROJECT_DIR"

# ── 1. Limpiar ──
paso 1 "Limpiando artefactos anteriores..."
mvn clean -q
ok "Limpieza completada"

# ── 2. Instalar kipu-common en repo local ──
paso 2 "Instalando kipu-common en repositorio local Maven..."
mvn install -pl kipu-common -DskipTests -q
ok "kipu-common instalado"

# ── 3. Compilar todo ──
paso 3 "Compilando todos los módulos..."
mvn clean compile -q
ok "Compilación exitosa (0 errores)"

# ── 4. Empacar servidor ──
paso 4 "Empacando kipu-servidor..."
mvn clean package -pl kipu-servidor -am -DskipTests -q
if [ ! -f "$PROJECT_DIR/kipu-servidor/target/$JAR_SERVIDOR" ]; then
    error "No se generó $JAR_SERVIDOR"
fi
ok "JAR generado: kipu-servidor/target/$JAR_SERVIDOR"

# ── 5. Desplegar servidor ──
paso 5 "Desplegando JAR en $SERVIDOR_DEPLOY..."
if [ -d "$SERVIDOR_DEPLOY" ]; then
    sudo cp "$PROJECT_DIR/kipu-servidor/target/$JAR_SERVIDOR" "$SERVIDOR_DEPLOY/$JAR_SERVIDOR"
    ok "JAR desplegado en $SERVIDOR_DEPLOY/$JAR_SERVIDOR"
else
    echo -e "      ${DIM}(Directorio $SERVIDOR_DEPLOY no existe, omitiendo deploy)${NC}"
fi

# ── Resumen ──
echo ""
echo -e "${GREEN}  ══════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ Todo listo. Ejecuta:${NC}"
echo -e "${GOLD}    mvn javafx:run -pl kipu-cliente${NC}"
echo -e "${GREEN}  ══════════════════════════════════════${NC}"
echo ""
