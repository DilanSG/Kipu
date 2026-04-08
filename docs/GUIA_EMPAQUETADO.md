# Guia de Empaquetado - Kipu

Genera instaladores nativos del sistema Kipu (cliente + servidor) para Windows y Linux.

Los instaladores son **autocontenidos**: incluyen tanto el cliente como el servidor con JRE embebido. El usuario final solo necesita el instalador (`.exe` / `.deb`) y PostgreSQL.

---

## Resultado

El empaquetado genera una carpeta `dist/` con:

```
dist/
+-- servidor/                   Servidor portable (tambien empaquetado dentro del instalador)
+-- Kipu-1.0.0.exe             Instalador Windows (Inno Setup) con cliente + servidor
+-- kipu_1.0.0_amd64.deb       Instalador Linux (dpkg-deb) con cliente + servidor
+-- LICENSE
+-- README.md
```

### Que incluye cada instalador

| Instalador | Cliente | Servidor | JRE | Se necesita algo mas? |
|------------|---------|----------|-----|----------------------|
| `.exe` (Windows) | Si | Si (componente seleccionable) | Si (embebido) | Solo PostgreSQL |
| `.deb` (Linux) | Si | Si | Si (embebido) | Solo PostgreSQL |
| `.rpm` (Linux) | Si | Si | Si (embebido) | Solo PostgreSQL |

> **No es necesario distribuir la carpeta `servidor/` por separado.** El instalador ya la contiene. La carpeta `dist/servidor/` se genera como paso intermedio y queda disponible para uso portable si se desea.

### Donde se instala

| SO | Cliente | Servidor |
|----|---------|----------|
| Windows | `C:\Program Files\Kipu\Cliente\` | `C:\Program Files\Kipu\servidor\` |
| Linux | `/opt/kipu/cliente/` | `/opt/kipu/servidor/` |

---

## Requisitos de Compilacion

| Requisito | Version | Nota |
|-----------|---------|------|
| JDK | 21+ | Incluye `jpackage` |
| Maven | 3.8+ | Para compilar los JARs |
| Inno Setup | 6.x | Solo Windows, para `.exe` |
| WiX Toolset | 3.x | Solo Windows, para `.msi` (alternativa) |
| dpkg-deb | cualquiera | Solo Linux, para `.deb` |
| alien | cualquiera | Solo Linux, opcional para `.rpm` |

---

## Uso

### Windows (PowerShell)

```powershell
# Compilar primero (si no hay JARs)
.\scripts\build.ps1 package

# Generar todo: servidor portable + instalador .exe
.\scripts\package.ps1 todo exe

# Solo instalador .msi (requiere WiX)
.\scripts\package.ps1 todo msi

# Solo servidor portable (sin instalador)
.\scripts\package.ps1 servidor
```

### Linux (Bash)

```bash
# Compilar primero (si no hay JARs)
./scripts/build.sh package

# Generar todo: servidor portable + instalador .deb
./scripts/package.sh todo deb

# Generar .rpm (requiere alien, convierte desde .deb)
./scripts/package.sh todo rpm

# Solo servidor portable (sin instalador)
./scripts/package.sh servidor
```

---

## Como Funciona el Empaquetado

### Flujo general (ambos SO)

1. **Compilar JARs** — `mvn clean package -DskipTests` (si no existen)
2. **Servidor portable** — `jpackage --type app-image` genera carpeta con ejecutable + JRE
3. **Instalador** — empaqueta tanto el cliente (otro app-image) como el servidor portable

### Windows (.exe con Inno Setup)

1. `jpackage --type app-image` genera el app-image del cliente
2. Se copian DLLs nativos de JavaFX al runtime
3. Se copia `iniciar-consola.bat` (diagnostico) y `java.exe`
4. Inno Setup compila el `.exe` usando `packaging/windows/kipu-cliente.iss`
5. El `.iss` recibe por parametro (`/D`) las rutas del app-image y del servidor

El `.iss` esta parametrizado: version, rutas, icono y licencia se pasan desde `package.ps1` sin modificar el archivo.

### Linux (.deb con dpkg-deb)

1. `jpackage --type app-image` genera el app-image del cliente
2. Se construye manualmente la estructura del `.deb`:
   - `/opt/kipu/cliente/` — app-image del cliente
   - `/opt/kipu/servidor/` — servidor portable (copiado de `dist/servidor/`)
   - `/usr/share/applications/kipu.desktop` — entrada en menu de apps
   - `/usr/share/pixmaps/kipu.png` — icono
   - `DEBIAN/control`, `postinst`, `prerm`, `postrm` — desde `packaging/linux/debian/`
3. `dpkg-deb --build` genera el `.deb` final

> Se usa `dpkg-deb` manual en lugar de `jpackage --type deb` para poder incluir el servidor en el mismo paquete.

---

## Estructura de Archivos de Empaquetado

```
packaging/
+-- kipu.ico                          Icono Windows (.ico)
+-- generar-icono.sh                   Script para generar .ico desde PNG
+-- README.md                          Descripcion del directorio
+-- windows/
|   +-- kipu-cliente.iss              Config de Inno Setup (parametrizada)
|   +-- iniciar-consola.bat            Consola de diagnostico (template)
|   +-- setup-inicial.bat              Setup de BD + arranque servidor
|   +-- README.md                      README para distribucion Windows
+-- linux/
    +-- kipu.desktop                  Entrada freedesktop para menu de apps
    +-- setup-inicial.sh               Setup de BD + arranque servidor
    +-- README.md                      README para distribucion Linux
    +-- debian/
        +-- control.template           Plantilla del control file (.deb)
        +-- postinst                   Post-instalacion: permisos, symlinks
        +-- prerm                      Pre-remocion: detener servidor
        +-- postrm                     Post-remocion: limpieza en purge
```

---

## Cross-compilation

`jpackage` solo genera ejecutables para el SO donde se ejecuta:

| Compilas en | Genera para |
|-------------|-------------|
| Windows | Windows (.exe, .msi) |
| Linux | Linux (.deb, .rpm) |

Para ambos: compilar en cada SO.

---

## Distribucion

### Lo minimo para distribuir

| SO | Archivo | Tamano aprox. |
|----|---------|--------------|
| Windows | `Kipu-1.0.0.exe` | ~130 MB |
| Linux | `kipu_1.0.0_amd64.deb` | ~200 MB |

Solo se necesita **un archivo** por SO. El instalador contiene todo (cliente, servidor, JRE).

### Instalar

```powershell
# Windows: doble clic en Kipu-1.0.0.exe, o:
Start-Process .\Kipu-1.0.0.exe
```

```bash
# Linux (Debian/Ubuntu):
sudo dpkg -i kipu_1.0.0_amd64.deb

# Linux (Fedora/RHEL):
sudo rpm -i kipu-1.0.0.x86_64.rpm
```

### Desinstalar

```powershell
# Windows: Panel de Control > Programas > Kipu > Desinstalar
```

```bash
# Linux:
sudo apt remove kipu
```

---

## Solucion de Problemas

### "jpackage not found"

Verificar que JDK 21+ esta en el PATH:

```bash
java -version          # Debe mostrar 21+
which jpackage         # Debe encontrarlo
```

### "Inno Setup not found" (Windows)

Instalar desde https://jrsoftware.org/isinfo.php. Se detecta automaticamente en rutas estandar.

### "dpkg-deb not found" (Linux)

```bash
sudo apt install dpkg
```

### "alien not found" (Linux, para .rpm)

```bash
sudo apt install alien
```

### Tamano del instalador

Es ~130-200 MB porque incluye JRE completo. La ventaja: el usuario final **no necesita instalar Java**.
