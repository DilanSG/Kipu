#!/bin/bash
# =========================================================
# Convierte ICON.png a baryx.ico para empaquetado Windows
# Requiere ImageMagick: sudo apt install imagemagick
# =========================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PNG_SOURCE="$PROJECT_DIR/baryx-cliente/src/main/resources/imagenes/ICON.png"
ICO_OUTPUT="$PROJECT_DIR/packaging/baryx.ico"

if ! command -v convert &> /dev/null; then
    echo "ImageMagick no encontrado. Instalando..."
    echo "  sudo apt install imagemagick"
    echo ""
    echo "Alternativa manual:"
    echo "  Usa un conversor online (ej: https://convertio.co/png-ico/)"
    echo "  y guarda el resultado en: packaging/baryx.ico"
    exit 1
fi

if [ ! -f "$PNG_SOURCE" ]; then
    echo "No se encontró: $PNG_SOURCE"
    exit 1
fi

mkdir -p "$(dirname "$ICO_OUTPUT")"

# Generar .ico con múltiples resoluciones (16, 32, 48, 64, 128, 256)
convert "$PNG_SOURCE" \
    -resize 256x256 \
    -define icon:auto-resize=256,128,64,48,32,16 \
    "$ICO_OUTPUT"

echo "✔ Icono generado: $ICO_OUTPUT"
