#!/bin/bash
# Script para añadir encabezados de copyright a todos los archivos Java
# Kipu - Sistema POS y gestión de pedidos

HEADER='/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/'

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"

mapfile -t javaFiles < <(find "$RAIZ" -path "*/src/main/java/*" -name "*.java" -type f)

echo "Kipu - Licenciador de Archivos Java"
echo "Archivos encontrados: ${#javaFiles[@]}"

modificados=0
reemplazados=0

for file in "${javaFiles[@]}"; do
    relativo="${file#"$RAIZ"/}"
    if grep -q "Copyright" "$file"; then
        # Eliminar el bloque de copyright existente (desde /* que contiene Copyright hasta */)
        tmpfile=$(mktemp)
        sed '/\/\*.*Copyright/,/\*\//d' "$file" > "$tmpfile"
        printf '%s' "$HEADER" | cat - "$tmpfile" > "$file"
        rm "$tmpfile"
        reemplazados=$((reemplazados + 1))
        echo "  [~] $relativo (reemplazado)"
    else
        tmpfile=$(mktemp)
        printf '%s' "$HEADER" > "$tmpfile"
        cat "$file" >> "$tmpfile"
        mv "$tmpfile" "$file"
        modificados=$((modificados + 1))
        echo "  [+] $relativo (añadido)"
    fi
done

echo "Añadidos     : $modificados"
echo "Reemplazados : $reemplazados"
echo "TOTAL        : ${#javaFiles[@]}"
