#!/bin/bash
# Kipu - Script de compilacion centralizado
# Usa el POM padre para compilar los 3 modulos de una vez
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

print_banner() {
    echo -e "${YELLOW}KIPU - Compilacion${NC}"
}

print_step()    { echo -e "${BLUE}[ACTION]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

print_banner

if [ ! -f "$PROJECT_DIR/pom.xml" ]; then
    print_error "No se encontro pom.xml en $PROJECT_DIR"
    exit 1
fi

cd "$PROJECT_DIR"

MODE="${1:-package}"

case "$MODE" in
    clean)
        print_step "Limpiando todos los modulos..."
        mvn clean
        print_success "Limpieza completada"
        ;;
    compile)
        print_step "Compilando todos los modulos..."
        mvn compile
        print_success "Compilacion completada"
        ;;
    package)
        print_step "Empacando todos los modulos (sin tests)..."
        mvn clean package -DskipTests
        print_success "Empacado completado"
        echo ""
        echo "  JARs generados:"
        echo "    kipu-servidor/target/kipu-servidor-1.0.0.jar"
        echo "    kipu-cliente/target/kipu-cliente-1.0.0.jar"
        ;;
    install)
        print_step "Instalando todos los modulos en repositorio local..."
        mvn clean install -DskipTests
        print_success "Instalacion completada"
        ;;
    test)
        print_step "Ejecutando tests de todos los modulos..."
        mvn clean test
        print_success "Tests completados"
        ;;
    full)
        print_step "Build completo (compile + test + package)..."
        mvn clean package
        print_success "Build completo finalizado"
        ;;
    *)
        echo "Uso: $0 [clean|compile|package|install|test|full]"
        echo ""
        echo "  clean    - Limpia artefactos de compilacion"
        echo "  compile  - Solo compila (rapido)"
        echo "  package  - Compila y empaca JARs (por defecto, sin tests)"
        echo "  install  - Empaca e instala en repositorio local Maven"
        echo "  test     - Ejecuta todos los tests"
        echo "  full     - Build completo con tests"
        exit 1
        ;;
esac

echo ""
echo "Para iniciar el sistema:"
echo "  1. Servidor: ./scripts/start-servidor.sh"
echo "  2. Cliente:  ./scripts/start-cliente.sh"
echo ""
print_success "Listo!"
