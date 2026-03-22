#!/bin/bash
# Baryx - Empaquetado Linux
# Genera distribuibles en packaging/distribuibles/linux/
#
#   - servidor/       Carpeta portable con JRE embebido
#   - Instalador      .deb/.rpm con cliente + servidor
#   - LICENSE
#   - README.md
#
# Los archivos temporales se crean en packaging/distribuibles/temp-linux/
# y se eliminan al finalizar, evitando conflictos con la carpeta de salida.
#
# El instalador .deb incluye tanto el cliente como el
# servidor en /opt/baryx/{cliente,servidor}. Se construye
# manualmente con dpkg-deb para tener control total del contenido del paquete.
#
# El cliente arranca en Host Mode por defecto: al abrirse
# lanza automaticamente el servidor desde /opt/baryx/servidor/
# usando el JRE que trae el servidor empaquetado. No se
# necesita JDK instalado en la maquina del usuario final.
#
# Requisitos de compilacion:
#   - JDK 21+ (con jpackage)
#   - Maven
#   - dpkg-deb (Linux .deb)
#   - alien (para convertir .deb a .rpm)
# Uso:
#   ./scripts/package.sh                       # Todo (defecto): host installer
#   ./scripts/package.sh host [deb|rpm]        # Instalador HOST (cliente + servidor)
#   ./scripts/package.sh cliente-solo [deb|rpm]# Instalador CLIENTE ligero (sin servidor)
#   ./scripts/package.sh servidor              # Solo servidor portable
#   ./scripts/package.sh todo [deb|rpm]        # Servidor + host installer (alias de host)
#
# Variables de entorno opcionales:
#   BARYX_ATLAS_URI  — URI del cluster MongoDB Atlas (se embebe en el JAR, NUNCA en disco)
#   BARYX_API_URL    — URL del backend web (default: https://api.baryx.app)
#   BARYX_VERSION    — Versión del paquete (default: lee de pom.xml)
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

# Carpeta de salida dentro de packaging/ (separada del proyecto)
DIST_DIR="$PROJECT_DIR/packaging/distribuibles/linux"
# Carpeta temporal separada para evitar bloqueos con la salida
TEMP_DIR="$PROJECT_DIR/packaging/distribuibles/temp-linux"

# Version: use BARYX_VERSION env (set by CI) or read from pom.xml
if [ -n "${BARYX_VERSION:-}" ]; then
    APP_VERSION="$BARYX_VERSION"
else
    APP_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout \
        --file "$PROJECT_DIR/pom.xml" 2>/dev/null || echo "1.0.0")
    APP_VERSION=$(echo "$APP_VERSION" | tail -1 | tr -d '[:space:]')
fi

APP_VENDOR="Baryx"
APP_COPYRIGHT="Copyright (c) 2026 Baryx"
APP_DESCRIPTION_SERVIDOR="Servidor REST API para Software POS Baryx"
APP_DESCRIPTION_CLIENTE="Cliente del Software POS Baryx"

JAR_SERVIDOR="$PROJECT_DIR/baryx-servidor/target/baryx-servidor-${APP_VERSION}.jar"
JAR_CLIENTE="$PROJECT_DIR/baryx-cliente/target/baryx-cliente-${APP_VERSION}.jar"

ICON_PNG="$PROJECT_DIR/baryx-cliente/src/main/resources/imagenes/ICON.png"

NOMBRE_EJECUTABLE_SERVIDOR="BaryxServidor"

print_banner() {
    echo -e "${YELLOW}BARYX - Empaquetado de Distribucion Linux${NC}"
}

print_step()    { echo -e "${BLUE}[ACTION]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
print_info()    { echo -e "${CYAN}[INFO]${NC} $1"; }

verificar_requisitos() {
    print_step "Verificando requisitos..."
    print_step "Verificando jpackage (JDK 14+)..."
    if ! command -v jpackage &> /dev/null; then
        print_error "jpackage no encontrado. Se requiere JDK 14+ (recomendado JDK 21)."
        echo "  Instalar: sudo apt install openjdk-21-jdk"
        exit 1
    fi
    print_info "jpackage detectado"
    print_step "Verificando JDK..."
    JAVA_VER=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VER" -lt 14 ]; then
        print_error "Se requiere JDK 14+. Version actual: $JAVA_VER"
        exit 1
    fi
    print_info "Java Development Kit $JAVA_VER detectado"
    print_step "Verificando Maven..."
    if ! command -v mvn &> /dev/null; then
        print_error "Maven no encontrado. Instalar: sudo apt install maven"
        exit 1
    fi
    print_info "Maven detectado"
    print_success "Requisitos verificados"
}

compilar_jars() {
    if [ -f "$JAR_SERVIDOR" ] && [ -f "$JAR_CLIENTE" ]; then
        print_info "JARs ya existen. Usando compilacion existente."
        print_info "  Para recompilar: ./scripts/build.sh package"
        return
    fi

    print_step "Compilando proyecto (mvn clean package -DskipTests)..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests -q
    print_success "Compilacion completada"
}

preparar_directorios() {
    print_step "Preparando directorios de distribucion..."
    rm -rf "$DIST_DIR"
    rm -rf "$TEMP_DIR"
    mkdir -p "$DIST_DIR"
    mkdir -p "$TEMP_DIR/servidor"
    mkdir -p "$TEMP_DIR/cliente"
    print_success "Directorios preparados"
    print_info "  Salida: packaging/distribuibles/linux/"
    print_info "  Temp:   packaging/distribuibles/temp-linux/"
}

preparar_recursos() {
    print_step "Preparando recursos..."
    cp "$JAR_SERVIDOR" "$TEMP_DIR/servidor/"
    cp "$JAR_CLIENTE" "$TEMP_DIR/cliente/"
    local PACKAGING_LINUX="$PROJECT_DIR/packaging/linux"
    local SCRIPT_SETUP="setup-inicial.sh"
    if [ -f "$PACKAGING_LINUX/$SCRIPT_SETUP" ]; then
        cp "$PACKAGING_LINUX/$SCRIPT_SETUP" "$TEMP_DIR/servidor/$SCRIPT_SETUP"
        chmod +x "$TEMP_DIR/servidor/$SCRIPT_SETUP"
    else
        print_error "No se encontro $PACKAGING_LINUX/$SCRIPT_SETUP"
        exit 1
    fi

    print_success "Recursos preparados"
}

generar_license() {
    if [ -f "$PROJECT_DIR/LICENSE" ]; then
        cp "$PROJECT_DIR/LICENSE" "$DIST_DIR/LICENSE"
        print_success "LICENSE copiado desde raiz del proyecto"
    else
        print_error "No se encontro LICENSE en la raiz del proyecto"
        exit 1
    fi
}

generar_readme() {
    local README_SRC="$PROJECT_DIR/packaging/linux/README.md"
    if [ -f "$README_SRC" ]; then
        cp "$README_SRC" "$DIST_DIR/README.md"
        print_success "README.md copiado desde packaging/linux/"
    else
        print_error "No se encontro $README_SRC"
        exit 1
    fi
}

empaquetar_servidor_portable() {
    print_step "Empaquetando servidor como portable (con JRE embebido)..."
    jpackage \
        --type app-image \
        --name "${NOMBRE_EJECUTABLE_SERVIDOR}" \
        --app-version "$APP_VERSION" \
        --vendor "$APP_VENDOR" \
        --copyright "$APP_COPYRIGHT" \
        --description "$APP_DESCRIPTION_SERVIDOR" \
        --input "$TEMP_DIR/servidor" \
        --main-jar "baryx-servidor-${APP_VERSION}.jar" \
        --main-class "org.springframework.boot.loader.launch.JarLauncher" \
        --dest "$TEMP_DIR" \
        --java-options "-Xms256m" \
        --java-options "-Xmx512m" \
        --java-options "-XX:+UseG1GC" \
        --java-options "-Dspring.profiles.active=prod" \
        --java-options "-Dfile.encoding=UTF-8" \
        ${ICON_PNG:+--icon "$ICON_PNG"} \
        2>&1 | while read line; do echo "    $line"; done

    if [ -d "$TEMP_DIR/${NOMBRE_EJECUTABLE_SERVIDOR}" ]; then
        mv "$TEMP_DIR/${NOMBRE_EJECUTABLE_SERVIDOR}" "$DIST_DIR/servidor"
        cp "$DIST_DIR/servidor/lib/app/setup-inicial.sh" "$DIST_DIR/servidor/" 2>/dev/null || true
        _agregar_java_al_runtime "$DIST_DIR/servidor"
        _crear_wrapper_servidor "$DIST_DIR/servidor"

        chmod +x "$DIST_DIR/servidor/setup-inicial.sh" 2>/dev/null || true
        chmod +x "$DIST_DIR/servidor/bin/${NOMBRE_EJECUTABLE_SERVIDOR}" 2>/dev/null || true
    else
        print_error "jpackage no genero la carpeta esperada"
        exit 1
    fi

    print_success "Servidor portable generado: dist/servidor/"
    print_info "  Ejecutable: dist/servidor/bin/${NOMBRE_EJECUTABLE_SERVIDOR}"
    print_info "  Setup:      dist/servidor/setup-inicial.sh"
}

_agregar_java_al_runtime() {
    local APP_IMAGE_DIR="$1"
    local RUNTIME_DIR="$APP_IMAGE_DIR/lib/runtime"

    if [ -x "$RUNTIME_DIR/bin/java" ]; then
        return 0
    fi

    print_info "Generando lanzador java en el JRE embebido del servidor..."

    local MODULOS
    MODULOS=$(grep '^MODULES=' "$RUNTIME_DIR/release" 2>/dev/null \
        | sed 's/MODULES="//;s/"$//' \
        | tr ' ' ',')

    if [ -z "$MODULOS" ]; then
        MODULOS="java.base,java.logging,java.naming,java.sql,java.xml,java.xml.crypto,java.management,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,java.instrument,java.desktop,jdk.unsupported,jdk.crypto.ec"
    fi

    local TEMP_RUNTIME="$TEMP_DIR/servidor-runtime-temp"
    rm -rf "$TEMP_RUNTIME"

    jlink \
        --add-modules "$MODULOS" \
        --output "$TEMP_RUNTIME" \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=zip-6 \
        2>&1 | while read line; do echo "    $line"; done

    if [ -x "$TEMP_RUNTIME/bin/java" ]; then
        mkdir -p "$RUNTIME_DIR/bin"
        cp "$TEMP_RUNTIME/bin/java" "$RUNTIME_DIR/bin/"
        chmod +x "$RUNTIME_DIR/bin/java"
        print_info "Lanzador java agregado al runtime del servidor"
    else
        print_info "No se pudo generar java en el runtime (se usara el native launcher)"
    fi

    rm -rf "$TEMP_RUNTIME"
}

_crear_wrapper_servidor() {
    local APP_IMAGE_DIR="$1"
    local BIN_DIR="$APP_IMAGE_DIR/bin"
    local NATIVE_LAUNCHER="$BIN_DIR/${NOMBRE_EJECUTABLE_SERVIDOR}"
    local JAVA_BIN="$APP_IMAGE_DIR/lib/runtime/bin/java"

    if [ ! -x "$JAVA_BIN" ]; then
        print_info "Conservando native launcher (java no disponible en runtime)"
        return 0
    fi

    if [ -f "$NATIVE_LAUNCHER" ]; then
        mv "$NATIVE_LAUNCHER" "${NATIVE_LAUNCHER}.native"
    fi

    # Leer opciones del .cfg para replicarlas en el wrapper
    local CFG_FILE="$APP_IMAGE_DIR/lib/app/${NOMBRE_EJECUTABLE_SERVIDOR}.cfg"
    local JAVA_OPTS=""
    if [ -f "$CFG_FILE" ]; then
        while IFS= read -r linea; do
            if [[ "$linea" == java-options=* ]]; then
                local opt="${linea#java-options=}"
                JAVA_OPTS="$JAVA_OPTS $opt"
            fi
        done < "$CFG_FILE"
    fi

    cat > "$NATIVE_LAUNCHER" << 'WRAPPER_EOF'
#!/bin/bash
# Baryx Servidor,Wrapper de lanzamiento
# Usa el JRE embebido para lanzar el servidor Spring Boot.
# Reemplaza el native launcher de jpackage que falla cuando
# multiples app-images comparten un mismo paquete .deb.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_BIN="$APP_DIR/lib/runtime/bin/java"
APP_JAR="$APP_DIR/lib/app/baryx-servidor-PLACEHOLDER_VERSION.jar"

if [ ! -x "$JAVA_BIN" ]; then
    echo "[ERROR] No se encontro el JRE embebido en: $JAVA_BIN"
    echo "  Intente reinstalar Baryx."
    exit 1
fi

if [ ! -f "$APP_JAR" ]; then
    echo "[ERROR] No se encontro el JAR del servidor en: $APP_JAR"
    exit 1
fi

exec "$JAVA_BIN" \
    PLACEHOLDER_JAVA_OPTS \
    -jar "$APP_JAR" \
    "$@"
WRAPPER_EOF

    # Sustituir placeholders
    sed -i "s/PLACEHOLDER_VERSION/${APP_VERSION}/g" "$NATIVE_LAUNCHER"
    sed -i "s|PLACEHOLDER_JAVA_OPTS|${JAVA_OPTS}|g" "$NATIVE_LAUNCHER"

    chmod +x "$NATIVE_LAUNCHER"
    print_info "Wrapper de lanzamiento creado para el servidor"
}


empaquetar_instalador() {
    local TIPO_INSTALADOR="${1:-deb}"
    local HOST_MODE="${2:-true}"
    local VARIANTE="host"
    [ "$HOST_MODE" = "false" ] && VARIANTE="cliente-solo"
    print_step "Empaquetando Baryx como instalador .$TIPO_INSTALADOR (variante: $VARIANTE)..."

    case "$TIPO_INSTALADOR" in
        deb) _construir_deb "$HOST_MODE" ;;
        rpm) _construir_rpm "$HOST_MODE" ;;
        *)
            print_error "Tipo de instalador no soportado: $TIPO_INSTALADOR (use 'deb' o 'rpm')"
            return 1
            ;;
    esac
}

# Genera el app-image del cliente con jpackage (compartido por host y cliente-solo)
_generar_app_image_cliente() {
    if [ -d "$TEMP_DIR/Baryx" ]; then
        print_info "App-image del cliente ya generada, reutilizando..."
        return 0
    fi

    print_info "Generando app-image del cliente con JRE embebido..."

    local MODULOS_CLIENTE="java.base,java.desktop,java.logging,java.naming,java.sql,java.xml,java.xml.crypto,java.management,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,jdk.unsupported,jdk.crypto.ec,jdk.accessibility"

    local BARYX_API_URL_VAL="${BARYX_API_URL:-https://api.baryx.app}"
    # Atlas URI cifrada dentro del JAR via cloud.dat (ver CifradoNube.java)
    # Ya no se pasa como --java-options porque Maven la cifra en process-resources

    jpackage \
        --type app-image \
        --name "Baryx" \
        --app-version "$APP_VERSION" \
        --vendor "$APP_VENDOR" \
        --copyright "$APP_COPYRIGHT" \
        --description "$APP_DESCRIPTION_CLIENTE" \
        --input "$TEMP_DIR/cliente" \
        --main-jar "baryx-cliente-${APP_VERSION}.jar" \
        --main-class "com.baryx.cliente.BaryxClienteLauncher" \
        --dest "$TEMP_DIR" \
        --add-modules "$MODULOS_CLIENTE" \
        --java-options "-Xms512m" \
        --java-options "-Xmx1024m" \
        --java-options "-XX:+UseG1GC" \
        --java-options "-XX:MaxGCPauseMillis=50" \
        --java-options "-Dfile.encoding=UTF-8" \
        --java-options "-Dglass.win.uiScale=1.0" \
        --java-options "-Dprism.allowHiDPIScaling=false" \
        --java-options "-Dbaryx.api.url=${BARYX_API_URL_VAL}" \
        ${ICON_PNG:+--icon "$ICON_PNG"} \
        2>&1 | while read line; do echo "    $line"; done

    if [ ! -d "$TEMP_DIR/Baryx" ]; then
        print_error "jpackage no genero la app-image del cliente"
        return 1
    fi
}

# Construye un .deb con host.mode configurado
# Uso: _construir_deb <host_mode: true|false>
_construir_deb() {
    local HOST_MODE="${1:-true}"

    if ! command -v dpkg-deb &> /dev/null; then
        print_error "dpkg-deb no encontrado. Instalar: sudo apt install dpkg"
        return 1
    fi

    local PACKAGING_LINUX="$PROJECT_DIR/packaging/linux"

    _generar_app_image_cliente || return 1

    local VARIANTE="host"
    local DEB_PKG_NAME="baryx_${APP_VERSION}_amd64"
    if [ "$HOST_MODE" = "false" ]; then
        VARIANTE="cliente"
        DEB_PKG_NAME="baryx-cliente_${APP_VERSION}_amd64"
    fi

    print_info "Construyendo paquete .deb (variante: $VARIANTE)..."

    local DEB_ROOT="$TEMP_DIR/deb-root-${VARIANTE}"
    rm -rf "$DEB_ROOT"

    mkdir -p "$DEB_ROOT/DEBIAN"
    mkdir -p "$DEB_ROOT/opt/baryx/cliente"
    mkdir -p "$DEB_ROOT/usr/share/applications"
    mkdir -p "$DEB_ROOT/usr/share/pixmaps"

    cp -r "$TEMP_DIR/Baryx/"* "$DEB_ROOT/opt/baryx/cliente/"

    # Inyectar host.mode en baryx-cliente.properties dentro del app-image
    local PROPS_DIR="$DEB_ROOT/opt/baryx/cliente/lib/app"
    mkdir -p "$PROPS_DIR"
    cat > "$PROPS_DIR/baryx-cliente.properties" << EOF
# Generado por el empaquetador Baryx
# host.mode=true  → Lanza el servidor embebido al iniciar
# host.mode=false → Solo cliente, se conecta a un servidor en la LAN
host.mode=${HOST_MODE}
EOF
    print_info "host.mode=${HOST_MODE} configurado en baryx-cliente.properties"

    if [ "$HOST_MODE" = "true" ]; then
        # Incluir servidor portable en el instalador host
        if [ -d "$DIST_DIR/servidor" ]; then
            print_info "Incluyendo servidor portable en el instalador..."
            mkdir -p "$DEB_ROOT/opt/baryx/servidor"
            cp -r "$DIST_DIR/servidor/"* "$DEB_ROOT/opt/baryx/servidor/"
        else
            print_error "Servidor no encontrado en dist/servidor — se requiere para el instalador host"
            print_info "  Ejecute: ./package.sh host deb (esto empaqueta el servidor primero)"
            return 1
        fi
    fi

    if [ -f "$PACKAGING_LINUX/baryx.desktop" ]; then
        cp "$PACKAGING_LINUX/baryx.desktop" "$DEB_ROOT/usr/share/applications/"
    else
        print_info "Archivo .desktop no encontrado en $PACKAGING_LINUX/"
    fi

    if [ -f "$ICON_PNG" ]; then
        cp "$ICON_PNG" "$DEB_ROOT/usr/share/pixmaps/baryx.png"
    fi

    for script_consola in iniciar-cliente.sh iniciar-servidor.sh; do
        if [ -f "$PACKAGING_LINUX/$script_consola" ]; then
            cp "$PACKAGING_LINUX/$script_consola" "$DEB_ROOT/opt/baryx/$script_consola"
            chmod +x "$DEB_ROOT/opt/baryx/$script_consola"
        fi
    done

    print_info "Generando metadatos del paquete..."

    local INSTALLED_SIZE_KB
    INSTALLED_SIZE_KB=$(du -sk "$DEB_ROOT" 2>/dev/null | cut -f1)

    if [ -f "$PACKAGING_LINUX/debian/control.template" ]; then
        sed -e "s/{{VERSION}}/$APP_VERSION/g" \
            -e "s/{{INSTALLED_SIZE}}/$INSTALLED_SIZE_KB/g" \
            "$PACKAGING_LINUX/debian/control.template" > "$DEB_ROOT/DEBIAN/control"
        # Cambiar nombre del paquete para cliente-solo
        if [ "$HOST_MODE" = "false" ]; then
            sed -i "s/^Package: baryx$/Package: baryx-cliente/" "$DEB_ROOT/DEBIAN/control"
            sed -i "s/Servidor REST API y //" "$DEB_ROOT/DEBIAN/control"
        fi
    else
        print_error "No se encontro la plantilla de control: $PACKAGING_LINUX/debian/control.template"
        return 1
    fi

    # Scripts de mantenimiento del paquete (postinst, prerm, postrm)
    for script in postinst prerm postrm; do
        if [ -f "$PACKAGING_LINUX/debian/$script" ]; then
            cp "$PACKAGING_LINUX/debian/$script" "$DEB_ROOT/DEBIAN/"
            chmod 755 "$DEB_ROOT/DEBIAN/$script"
        fi
    done

    print_info "Construyendo paquete .deb..."
    dpkg-deb --build --root-owner-group "$DEB_ROOT" "$DIST_DIR/${DEB_PKG_NAME}.deb" \
        2>&1 | while read line; do echo "    $line"; done

    if [ -f "$DIST_DIR/${DEB_PKG_NAME}.deb" ]; then
        local tamano
        tamano=$(du -sh "$DIST_DIR/${DEB_PKG_NAME}.deb" 2>/dev/null | cut -f1)
        print_success "Paquete .deb ($VARIANTE): dist/${DEB_PKG_NAME}.deb ($tamano)"
    else
        print_error "No se pudo generar el paquete .deb"
        return 1
    fi
}

_construir_rpm() {
    local HOST_MODE="${1:-true}"
    local DEB_PKG_NAME="baryx_${APP_VERSION}_amd64"
    [ "$HOST_MODE" = "false" ] && DEB_PKG_NAME="baryx-cliente_${APP_VERSION}_amd64"
    local DEB_FILE="$DIST_DIR/${DEB_PKG_NAME}.deb"

    if [ ! -f "$DEB_FILE" ]; then
        print_info "Construyendo .deb primero para convertir a .rpm..."
        _construir_deb "$HOST_MODE" || return 1
    fi

    if ! command -v alien &> /dev/null; then
        print_error "alien no encontrado. Instalar: sudo apt install alien"
        print_info "  Alternativa: usar el .deb generado o instalar con dpkg -i"
        return 1
    fi

    print_info "Convirtiendo .deb a .rpm con alien..."
    cd "$DIST_DIR"
    sudo alien --to-rpm --scripts "$DEB_FILE" \
        2>&1 | while read line; do echo "    $line"; done
    cd "$PROJECT_DIR"
    print_success "Paquete .rpm generado en dist/"
}

limpiar_temp() {
    rm -rf "$TEMP_DIR"
    print_info "Archivos temporales eliminados"
}

mostrar_resumen() {
    echo -e "${YELLOW}Resumen de distribucion${NC}"

    if [ -d "$DIST_DIR/servidor" ]; then
        local tamano_srv=$(du -sh "$DIST_DIR/servidor" 2>/dev/null | cut -f1)
        echo -e "  ${GREEN}servidor/${NC}               ($tamano_srv) — Portable con JRE"
    fi

    for archivo in "$DIST_DIR"/*.deb "$DIST_DIR"/*.rpm; do
        if [ -f "$archivo" ]; then
            local nombre=$(basename "$archivo")
            local tamano=$(du -sh "$archivo" 2>/dev/null | cut -f1)
            echo -e "  ${GREEN}${nombre}${NC}  ($tamano) — Instalador"
        fi
    done

    [ -f "$DIST_DIR/LICENSE" ] && echo -e "  ${CYAN}LICENSE${NC}"
    [ -f "$DIST_DIR/README.md" ] && echo -e "  ${CYAN}README.md${NC}"

    echo ""
    echo -e "  ${CYAN}Ubicacion: $DIST_DIR/${NC}"
    echo ""
    echo -e "  ${CYAN}Instalacion: sudo dpkg -i dist/baryx_${APP_VERSION}_amd64.deb${NC}"
    echo -e "  ${CYAN}Desinstalar: sudo apt remove baryx${NC}"
    echo ""
}

print_banner
verificar_requisitos

MODO="${1:-host}"
TIPO_PKG="${2:-deb}"

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
    host)
        compilar_jars
        preparar_directorios
        preparar_recursos
        empaquetar_servidor_portable
        empaquetar_instalador "$TIPO_PKG" "true"
        generar_license
        generar_readme
        limpiar_temp
        ;;
    cliente-solo)
        compilar_jars
        preparar_directorios
        preparar_recursos
        empaquetar_instalador "$TIPO_PKG" "false"
        generar_license
        generar_readme
        limpiar_temp
        ;;
    todo)
        compilar_jars
        preparar_directorios
        preparar_recursos
        empaquetar_servidor_portable
        empaquetar_instalador "$TIPO_PKG" "true"
        empaquetar_instalador "$TIPO_PKG" "false"
        generar_license
        generar_readme
        limpiar_temp
        ;;
    *)
        echo "Uso: $0 [host|cliente-solo|servidor|todo] [deb|rpm]"
        echo ""
        echo "  host         - Instalador HOST: cliente + servidor + asistente PG (defecto)"
        echo "  cliente-solo - Instalador CLIENTE: solo se conecta a un servidor LAN"
        echo "  servidor     - Servidor portable con JRE embebido (sin instalador)"
        echo "  todo         - Genera ambos instaladores + servidor portable"
        echo ""
        echo "  Tipo de instalador: deb (defecto) o rpm"
        echo ""
        echo "Variables de entorno:"
        echo "  BARYX_ATLAS_URI  - URI MongoDB Atlas (embebida en JVM, nunca en disco)"
        echo "  BARYX_API_URL    - URL backend web (default: https://api.baryx.app)"
        echo "  BARYX_VERSION    - Version del paquete"
        exit 1
        ;;
esac

mostrar_resumen
print_success "Empaquetado completado!"
echo ""
