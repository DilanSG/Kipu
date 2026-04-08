#!/bin/bash
# Kipu - Empaquetado macOS
# Genera distribuibles en packaging/distribuibles/macos/
#
#   - Kipu-VERSION.dmg   Instalador de disco para el cliente (JavaFX)
#   - KipuServidor/      Carpeta portable con JRE embebido (servidor)
#   - LICENSE
#   - README.md
#
# Requisitos:
#   - JDK 21+ con jpackage (macOS)
#   - mvn (compilación)
#   - Sólo ejecutable en macOS (usa APIs nativas de jpackage para .dmg)
#
# Uso:
#   ./scripts/package-mac.sh                  # Todo (defecto)
#   ./scripts/package-mac.sh todo [dmg|pkg]   # Servidor + cliente .dmg o .pkg
#   ./scripts/package-mac.sh servidor         # Solo servidor portable
#   ./scripts/package-mac.sh cliente [dmg|pkg]# Solo instalador cliente
# =========================================================

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

DIST_DIR="$PROJECT_DIR/packaging/distribuibles/macos"
TEMP_DIR="$PROJECT_DIR/packaging/distribuibles/temp-macos"

# Version: use KIPU_VERSION env (set by CI) or read from pom.xml
if [ -n "${KIPU_VERSION:-}" ]; then
    APP_VERSION="$KIPU_VERSION"
else
    APP_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout \
        --file "$PROJECT_DIR/pom.xml" 2>/dev/null || echo "1.0.0")
    APP_VERSION=$(echo "$APP_VERSION" | tail -1 | tr -d '[:space:]')
fi

# macOS jpackage requiere que el primer número de versión sea >= 1.
# Si la versión empieza con 0 (ej: 0.0.1), se usa 1.0.1 solo para jpackage.
JPACKAGE_VERSION="$APP_VERSION"
if [[ "$APP_VERSION" =~ ^0\. ]]; then
    JPACKAGE_VERSION="1${APP_VERSION#0}"
fi

APP_VENDOR="Kipu"
APP_COPYRIGHT="Copyright (c) 2026 Kipu"
APP_DESCRIPTION_SERVIDOR="Servidor REST API para Software POS Kipu"
APP_DESCRIPTION_CLIENTE="Software POS Kipu"

JAR_SERVIDOR="$PROJECT_DIR/kipu-servidor/target/kipu-servidor-${APP_VERSION}.jar"
JAR_CLIENTE="$PROJECT_DIR/kipu-cliente/target/kipu-cliente-${APP_VERSION}.jar"

ICON_ICNS="$PROJECT_DIR/packaging/kipu.icns"
ICON_PNG="$PROJECT_DIR/kipu-cliente/src/main/resources/imagenes/ICON.png"

NOMBRE_EJECUTABLE_SERVIDOR="KipuServidor"

print_banner() {
    echo -e "${YELLOW}KIPU - Empaquetado de Distribución macOS${NC}"
}

print_step()    { echo -e "${BLUE}[ACTION]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
print_info()    { echo -e "${CYAN}[INFO]${NC} $1"; }

verificar_requisitos() {
    print_step "Verificando requisitos..."

    if [[ "$OSTYPE" != "darwin"* ]]; then
        print_error "Este script sólo puede ejecutarse en macOS."
        exit 1
    fi

    if ! command -v jpackage &> /dev/null; then
        print_error "jpackage no encontrado. Se requiere JDK 21+."
        echo "  Descargar: https://adoptium.net/temurin/releases/?version=21"
        exit 1
    fi
    print_info "jpackage detectado"

    if ! command -v mvn &> /dev/null; then
        print_error "Maven no encontrado."
        exit 1
    fi
    print_info "Maven detectado"
    print_success "Requisitos verificados"
}

compilar_jars() {
    if [ -f "$JAR_SERVIDOR" ] && [ -f "$JAR_CLIENTE" ]; then
        print_info "JARs ya existen. Usando compilación existente."
        return
    fi
    print_step "Compilando proyecto (mvn clean package -DskipTests)..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests -q
    print_success "Compilación completada"
}

preparar_directorios() {
    print_step "Preparando directorios de distribución..."
    rm -rf "$DIST_DIR" "$TEMP_DIR"
    mkdir -p "$DIST_DIR" "$TEMP_DIR/servidor" "$TEMP_DIR/cliente"
    print_success "Directorios preparados"
}

preparar_recursos() {
    print_step "Preparando recursos..."
    cp "$JAR_SERVIDOR" "$TEMP_DIR/servidor/"
    cp "$JAR_CLIENTE" "$TEMP_DIR/cliente/"
    print_success "Recursos preparados"
}

# Resolve icon parameter: prefer .icns (macOS native), fall back to .png
_icon_param() {
    if [ -f "$ICON_ICNS" ]; then
        echo "--icon $ICON_ICNS"
    elif [ -f "$ICON_PNG" ]; then
        echo "--icon $ICON_PNG"
    else
        echo ""
    fi
}

empaquetar_servidor_portable() {
    print_step "Empaquetando servidor como portable (app-image con JRE embebido)..."
    ICON=$(_icon_param)

    # shellcheck disable=SC2086
    jpackage \
        --type app-image \
        --name "${NOMBRE_EJECUTABLE_SERVIDOR}" \
        --app-version "$JPACKAGE_VERSION" \
        --vendor "$APP_VENDOR" \
        --copyright "$APP_COPYRIGHT" \
        --description "$APP_DESCRIPTION_SERVIDOR" \
        --input "$TEMP_DIR/servidor" \
        --main-jar "kipu-servidor-${APP_VERSION}.jar" \
        --main-class "org.springframework.boot.loader.launch.JarLauncher" \
        --dest "$TEMP_DIR" \
        --java-options "-Xms256m" \
        --java-options "-Xmx512m" \
        --java-options "-XX:+UseG1GC" \
        --java-options "-Dspring.profiles.active=prod" \
        --java-options "-Dfile.encoding=UTF-8" \
        $ICON \
        2>&1 | while read -r line; do echo "    $line"; done

    if [ -d "$TEMP_DIR/${NOMBRE_EJECUTABLE_SERVIDOR}.app" ]; then
        mv "$TEMP_DIR/${NOMBRE_EJECUTABLE_SERVIDOR}.app" "$DIST_DIR/"
        print_success "Servidor portable generado: ${NOMBRE_EJECUTABLE_SERVIDOR}.app"
    else
        print_error "jpackage no generó la app-bundle esperada"
        exit 1
    fi
}

empaquetar_cliente_instalador() {
    local TIPO="${1:-dmg}"
    print_step "Empaquetando cliente como instalador .${TIPO}..."

    MODULOS_CLIENTE="java.base,java.desktop,java.logging,java.naming,java.sql,java.xml,java.xml.crypto,java.management,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,jdk.unsupported,jdk.crypto.ec,jdk.accessibility"
    local KIPU_API_URL_VAL="${KIPU_API_URL:-https://kipuweb.onrender.com}"
    ICON=$(_icon_param)

    # shellcheck disable=SC2086
    jpackage \
        --type "${TIPO}" \
        --name "Kipu" \
        --app-version "$JPACKAGE_VERSION" \
        --vendor "$APP_VENDOR" \
        --copyright "$APP_COPYRIGHT" \
        --description "$APP_DESCRIPTION_CLIENTE" \
        --input "$TEMP_DIR/cliente" \
        --main-jar "kipu-cliente-${APP_VERSION}.jar" \
        --main-class "com.kipu.cliente.KipuClienteLauncher" \
        --dest "$DIST_DIR" \
        --add-modules "$MODULOS_CLIENTE" \
        --java-options "-Xms512m" \
        --java-options "-Xmx1024m" \
        --java-options "-XX:+UseG1GC" \
        --java-options "-XX:MaxGCPauseMillis=50" \
        --java-options "-Dfile.encoding=UTF-8" \
        --java-options "-Dkipu.api.url=${KIPU_API_URL_VAL}" \
        $ICON \
        2>&1 | while read -r line; do echo "    $line"; done

    local OUTPUT
    OUTPUT=$(ls "$DIST_DIR/Kipu-${JPACKAGE_VERSION}"*.${TIPO} 2>/dev/null | head -1)
    if [ -f "$OUTPUT" ]; then
        local TAMANO
        TAMANO=$(du -sh "$OUTPUT" | cut -f1)
        # Renombrar al versionado real si jpackage usó una versión inflada
        if [ "$JPACKAGE_VERSION" != "$APP_VERSION" ]; then
            local REAL_NAME="$DIST_DIR/Kipu-${APP_VERSION}.${TIPO}"
            mv "$OUTPUT" "$REAL_NAME"
            OUTPUT="$REAL_NAME"
            print_info "Renombrado a $(basename "$REAL_NAME") (jpackage requiere versión >= 1.0.0)"
        fi
        print_success "Instalador .${TIPO} generado: $(basename "$OUTPUT") ($TAMANO)"
    else
        print_error "No se encontró el instalador generado en $DIST_DIR"
        exit 1
    fi
}

generar_license() {
    [ -f "$PROJECT_DIR/LICENSE" ] && cp "$PROJECT_DIR/LICENSE" "$DIST_DIR/LICENSE"
}

generar_readme() {
    local README_SRC="$PROJECT_DIR/packaging/macos/README.md"
    if [ -f "$README_SRC" ]; then
        cp "$README_SRC" "$DIST_DIR/README.md"
    fi
}

limpiar_temp() {
    rm -rf "$TEMP_DIR"
    print_info "Archivos temporales eliminados"
}

mostrar_resumen() {
    echo -e "${YELLOW}Resumen de distribución macOS${NC}"
    for archivo in "$DIST_DIR"/*.dmg "$DIST_DIR"/*.pkg; do
        [ -f "$archivo" ] && \
          echo -e "  ${GREEN}$(basename "$archivo")${NC}  ($(du -sh "$archivo" | cut -f1))"
    done
    if [ -d "$DIST_DIR/${NOMBRE_EJECUTABLE_SERVIDOR}.app" ]; then
        echo -e "  ${GREEN}${NOMBRE_EJECUTABLE_SERVIDOR}.app${NC} — Servidor portable"
    fi
    echo -e "  ${CYAN}Ubicación: $DIST_DIR/${NC}"
    echo ""
}

print_banner
verificar_requisitos

MODO="${1:-todo}"
TIPO_PKG="${2:-dmg}"

case "$MODO" in
    servidor)
        compilar_jars
        preparar_directorios
        preparar_recursos
        empaquetar_servidor_portable
        generar_license
        generar_readme
        limpiar_temp
        ;;
    cliente)
        compilar_jars
        preparar_directorios
        preparar_recursos
        empaquetar_cliente_instalador "$TIPO_PKG"
        generar_license
        generar_readme
        limpiar_temp
        ;;
    todo)
        compilar_jars
        preparar_directorios
        preparar_recursos
        empaquetar_servidor_portable
        empaquetar_cliente_instalador "$TIPO_PKG"
        generar_license
        generar_readme
        limpiar_temp
        ;;
    *)
        echo "Uso: $0 [servidor|cliente|todo] [dmg|pkg]"
        exit 1
        ;;
esac

mostrar_resumen
print_success "Empaquetado macOS completado!"
