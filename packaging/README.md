# Packaging - Baryx

Recursos para generar instaladores nativos de Baryx.

## Estructura

```
packaging/
+-- baryx.ico                  Icono Windows (.ico)
+-- generar-icono.sh           Convierte LOGOPNG.png a baryx.ico (requiere ImageMagick)
+-- windows/
|   +-- baryx-cliente.iss      Config de Inno Setup (parametrizada con /D)
|   +-- iniciar-consola.bat    Template consola de diagnostico
|   +-- setup-inicial.bat      Setup BD + arranque servidor (Windows)
|   +-- README.md              Guia de instalacion para usuarios finales (Windows)
+-- linux/
    +-- baryx.desktop          Entrada freedesktop para menu de aplicaciones
    +-- setup-inicial.sh       Setup BD + arranque servidor (Linux)
    +-- README.md              Guia de instalacion para usuarios finales (Linux)
    +-- debian/
        +-- control.template   Metadatos del paquete .deb (con placeholders)
        +-- postinst           Post-instalacion: permisos, symlinks, cache iconos
        +-- prerm              Pre-remocion: detener servidor, eliminar symlinks
        +-- postrm             Post-remocion: limpieza en purge
```

## Empaquetado

```bash
# Windows (PowerShell)
.\scripts\package.ps1 todo exe

# Linux (Bash)
./scripts/package.sh todo deb
```

Ver [docs/GUIA_EMPAQUETADO.md](../docs/GUIA_EMPAQUETADO.md) para detalles completos.
