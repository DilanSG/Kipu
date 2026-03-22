# Baryx - Empaquetado Windows
# Genera distribuibles en packaging/distribuibles/windows/
#
# El instalador .exe (Inno Setup) incluye cliente + servidor.
# El instalador .msi (WiX) incluye solo el cliente.
# Con 'todo' se genera ademas la carpeta servidor/ portable.
#
# Los archivos temporales se crean en packaging/distribuibles/temp-windows/
# y se eliminan al finalizar, evitando conflictos con la carpeta de salida.
#
# Requisitos de compilacion:
#   - JDK 21+ (con jpackage)
#   - Maven
#   - Inno Setup 6 (para .exe) o WiX Toolset 3.x (para .msi)
#
# Opciones:
#   .\scripts\package.ps1                  # Instalador .exe (por defecto)
#   .\scripts\package.ps1 exe              # Igual que sin parametros
#   .\scripts\package.ps1 msi              # Instalador .msi (WiX)
#   .\scripts\package.ps1 todo exe         # Servidor portable + instalador .exe
#   .\scripts\package.ps1 todo msi         # Servidor portable + instalador .msi
#   .\scripts\package.ps1 servidor         # Solo servidor portable

param(
    [ValidateSet("servidor", "exe", "msi", "todo")]
    [string]$Modo = "exe",

    [ValidateSet("msi", "exe")]
    [string]$TipoPkg = "exe"
)

$ErrorActionPreference = "Stop"

# Forzar codificacion UTF-8 en la consola de Windows
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# ==================== CONSTANTES ====================

# Version: use BARYX_VERSION env (set by CI) or read from pom.xml
if ($env:BARYX_VERSION) {
    $APP_VERSION = $env:BARYX_VERSION
} else {
    try {
        $APP_VERSION = (& mvn help:evaluate -Dexpression=project.version -q -DforceStdout `
            --file (Join-Path $PROJECT_DIR "pom.xml") 2>$null) | Select-Object -Last 1
        $APP_VERSION = $APP_VERSION.Trim()
    } catch {
        $APP_VERSION = "1.0.0"
    }
    if (-not $APP_VERSION) { $APP_VERSION = "1.0.0" }
}
$APP_VENDOR = "Baryx"
$APP_COPYRIGHT = "Copyright(c) 2026 Dilan Acuña"
$APP_DESCRIPTION_SERVIDOR = "Servidor REST API para sistema POS Baryx"
$APP_DESCRIPTION_CLIENTE = "Software POS Baryx"

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_DIR = Split-Path -Parent $SCRIPT_DIR

# Carpeta de salida dentro de packaging/ (separada del proyecto)
$DIST_DIR = Join-Path $PROJECT_DIR "packaging\distribuibles\windows"
# Carpeta temporal separada para evitar bloqueos con la salida
$TEMP_DIR = Join-Path $PROJECT_DIR "packaging\distribuibles\temp-windows"

$JAR_SERVIDOR = Join-Path $PROJECT_DIR "baryx-servidor\target\baryx-servidor-$APP_VERSION.jar"
$JAR_CLIENTE = Join-Path $PROJECT_DIR "baryx-cliente\target\baryx-cliente-$APP_VERSION.jar"

$ICON_PNG = Join-Path $PROJECT_DIR "baryx-cliente\src\main\resources\imagenes\ICON.png"
$ICON_ICO = Join-Path $PROJECT_DIR "packaging\baryx.ico"

$NOMBRE_EJECUTABLE_SERVIDOR = "BaryxServidor"

$JAVAFX_VERSION = "21.0.5"

# ==================== FUNCIONES DE IMPRESION ====================

function Write-Banner {
    Write-Host ""
    Write-Host "  +=============================================+" -ForegroundColor Yellow
    Write-Host "  |  BARYX - Empaquetado de Distribucion Windows |" -ForegroundColor Yellow
    Write-Host "  +=============================================+" -ForegroundColor Yellow
    Write-Host ""
}

function Write-Step($msg)    { Write-Host "  > $msg" -ForegroundColor Blue }
function Write-Ok($msg)      { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Err($msg)     { Write-Host "  [ERROR] $msg" -ForegroundColor Red }
function Write-Note($msg)    { Write-Host "  [INFO] $msg" -ForegroundColor Cyan }

# ==================== DETECCION DE HERRAMIENTAS ====================

function Find-InnoSetup {
    # Buscar iscc.exe en PATH
    $iscc = Get-Command iscc.exe -ErrorAction SilentlyContinue
    if ($iscc) {
        return $iscc.Source
    }

    # Buscar en rutas comunes de instalacion
    $rutasCandidatas = @(
        "${env:ProgramFiles(x86)}\Inno Setup 6",
        "$env:ProgramFiles\Inno Setup 6",
        "${env:ProgramFiles(x86)}\Inno Setup 5",
        "$env:ProgramFiles\Inno Setup 5",
        "$env:LocalAppData\Programs\Inno Setup 6"
    )

    foreach ($ruta in $rutasCandidatas) {
        $isccPath = Join-Path $ruta "iscc.exe"
        if (Test-Path $isccPath) {
            return $isccPath
        }
    }

    # Buscar en el registro de Windows
    $regPaths = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1",
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 5_is1",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 5_is1"
    )
    foreach ($regPath in $regPaths) {
        try {
            $installDir = (Get-ItemProperty -Path $regPath -ErrorAction SilentlyContinue).InstallLocation
            if ($installDir) {
                $isccPath = Join-Path $installDir "iscc.exe"
                if (Test-Path $isccPath) {
                    return $isccPath
                }
            }
        } catch { }
    }

    return $null
}

function Find-WixToolset {
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($candle) {
        return (Split-Path -Parent $candle.Source)
    }

    $rutasCandidatas = @(
        "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin",
        "${env:ProgramFiles(x86)}\WiX Toolset v3.11\bin",
        "$env:ProgramFiles\WiX Toolset v3.14\bin",
        "$env:ProgramFiles\WiX Toolset v3.11\bin"
    )

    foreach ($ruta in $rutasCandidatas) {
        if (Test-Path (Join-Path $ruta "candle.exe")) {
            return $ruta
        }
    }

    return $null
}

# ==================== VERIFICACION DE REQUISITOS ====================

function Test-Requisitos {
    Write-Step "Verificando requisitos..."

    # jpackage (JDK 14+)
    $jpackagePath = Get-Command jpackage -ErrorAction SilentlyContinue
    if (-not $jpackagePath) {
        Write-Err "jpackage no encontrado. Se requiere JDK 14+ (recomendado JDK 21)."
        Write-Host "  Descargar en: https://adoptium.net/temurin/releases/?version=21"
        exit 1
    }

    # Version de Java
    $savedEAP = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $javaVer = & java -version 2>&1 | ForEach-Object ToString | Select-Object -First 1
    $ErrorActionPreference = $savedEAP
    Write-Note "Java detectado: $javaVer"

    # Maven
    $mvnPath = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvnPath) {
        Write-Err "Maven no encontrado."
        Write-Host "  Descargar en: https://maven.apache.org/download.cgi"
        exit 1
    }
    Write-Note "Maven detectado"

    # Herramienta de instalador segun el modo
    $tipoInstalador = if ($Modo -eq "exe" -or ($Modo -eq "todo" -and $TipoPkg -eq "exe")) { "exe" }
                      elseif ($Modo -eq "msi" -or ($Modo -eq "todo" -and $TipoPkg -eq "msi")) { "msi" }
                      else { $null }

    if ($tipoInstalador -eq "exe") {
        $script:IsccPath = Find-InnoSetup
        if ($script:IsccPath) {
            Write-Ok "Inno Setup detectado: $script:IsccPath"
        } else {
            Write-Err "Inno Setup no encontrado. Se requiere para generar instaladores .exe"
            Write-Host "  Descargar: https://jrsoftware.org/isdl.php"
            exit 1
        }
    }
    elseif ($tipoInstalador -eq "msi") {
        $wixDir = Find-WixToolset
        if ($wixDir) {
            if ($env:PATH -notlike "*$wixDir*") {
                $env:PATH = "$wixDir;$env:PATH"
                Write-Note "WiX Toolset agregado al PATH: $wixDir"
            }
            Write-Ok "WiX Toolset detectado: $wixDir"
        } else {
            Write-Err "WiX Toolset no encontrado. Se requiere para generar instaladores .msi"
            Write-Host "  Descargar: https://wixtoolset.org/docs/wix3/"
            exit 1
        }
    }

    Write-Ok "Requisitos verificados"
}

# ==================== COMPILACION ====================

function Build-Jars {
    if ((Test-Path $JAR_SERVIDOR) -and (Test-Path $JAR_CLIENTE)) {
        Write-Note "JARs ya existen. Usando compilacion existente."
        Write-Note "  Para recompilar: .\scripts\build.ps1 package"
        return
    }

    Write-Step "Compilando proyecto (mvn clean package -DskipTests)..."
    Push-Location $PROJECT_DIR
    & mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error en la compilacion"
        Pop-Location
        exit 1
    }
    Pop-Location
    Write-Ok "Compilacion completada"
}

# ==================== DIRECTORIOS ====================

function Initialize-Dirs {
    Write-Step "Preparando directorios de distribucion..."

    # Limpiar carpeta de salida si existe
    if (Test-Path $DIST_DIR) {
        Write-Note "Limpiando distribuibles anteriores..."
        $jarName = "baryx-servidor-$APP_VERSION.jar"
        $procesos = Get-WmiObject Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -like "*$jarName*" }

        if ($procesos) {
            Write-Note "Cerrando procesos que usan $jarName..."
            foreach ($p in $procesos) {
                Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
            }
            Start-Sleep -Seconds 2
        }

        try {
            Remove-Item -Recurse -Force $DIST_DIR
        } catch {
            Write-Err "No se pudo limpiar $DIST_DIR. Asegurese de cerrar BaryxServidor si esta en ejecucion."
            throw $_
        }
    }

    # Limpiar carpeta temporal si quedo de una ejecucion anterior
    if (Test-Path $TEMP_DIR) {
        Remove-Item -Recurse -Force $TEMP_DIR
    }

    # Crear directorios
    New-Item -ItemType Directory -Force -Path $DIST_DIR | Out-Null
    New-Item -ItemType Directory -Force -Path "$TEMP_DIR\servidor" | Out-Null
    New-Item -ItemType Directory -Force -Path "$TEMP_DIR\cliente" | Out-Null
    Write-Ok "Directorios preparados"
    Write-Note "  Salida: packaging\distribuibles\windows\"
    Write-Note "  Temp:   packaging\distribuibles\temp-windows\"
}

# ==================== RECURSOS ====================

function Initialize-Resources {
    Write-Step "Preparando recursos..."
    Copy-Item $JAR_SERVIDOR "$TEMP_DIR\servidor\"
    Copy-Item $JAR_CLIENTE "$TEMP_DIR\cliente\"

    $packagingWindows = Join-Path $PROJECT_DIR "packaging\windows"
    $scriptSetup = "setup-inicial.bat"
    $scriptSrc = Join-Path $packagingWindows $scriptSetup
    if (Test-Path $scriptSrc) {
        Copy-Item $scriptSrc "$TEMP_DIR\servidor\$scriptSetup"
    } else {
        Write-Err "No se encontro $scriptSrc"
        exit 1
    }

    Write-Ok "Recursos preparados"
}

# ==================== ICONO ====================

function Get-IconParam {
    if (Test-Path $ICON_ICO) {
        return @("--icon", $ICON_ICO)
    }
    elseif (Test-Path $ICON_PNG) {
        return @("--icon", $ICON_PNG)
    }
    return @()
}

# ==================== LICENSE Y README ====================

function New-License {
    $licenseFile = Join-Path $PROJECT_DIR "LICENSE"
    if (Test-Path $licenseFile) {
        Copy-Item $licenseFile "$DIST_DIR\LICENSE"
        Write-Ok "LICENSE copiado"
    } else {
        Write-Err "No se encontro LICENSE en la raiz del proyecto"
        exit 1
    }
}

function New-Readme {
    $readmeSrc = Join-Path $PROJECT_DIR "packaging\windows\README.md"
    if (Test-Path $readmeSrc) {
        Copy-Item $readmeSrc "$DIST_DIR\README.md"
        Write-Ok "README.md copiado desde packaging\windows\"
    } else {
        Write-Err "No se encontro $readmeSrc"
        exit 1
    }
}

# ==================== SERVIDOR PORTABLE ====================

function Build-ServidorPortable {
    Write-Step "Empaquetando servidor como portable (con JRE embebido)..."

    $iconParams = Get-IconParam
    $jpackageArgs = @(
        "--type", "app-image",
        "--name", $NOMBRE_EJECUTABLE_SERVIDOR,
        "--app-version", $APP_VERSION,
        "--vendor", $APP_VENDOR,
        "--copyright", $APP_COPYRIGHT,
        "--description", $APP_DESCRIPTION_SERVIDOR,
        "--input", "$TEMP_DIR\servidor",
        "--main-jar", "baryx-servidor-$APP_VERSION.jar",
        "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
        "--dest", $TEMP_DIR,
        "--java-options", "-Xms256m",
        "--java-options", "-Xmx512m",
        "--java-options", "-XX:+UseG1GC",
        "--java-options", "-Dspring.profiles.active=prod",
        "--java-options", "-Dfile.encoding=UTF-8"
    ) + $iconParams

    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error al empaquetar servidor portable"
        exit 1
    }

    $srcDir = "$TEMP_DIR\$NOMBRE_EJECUTABLE_SERVIDOR"
    $dstDir = "$DIST_DIR\servidor"
    if (Test-Path $srcDir) {
        Move-Item $srcDir $dstDir
        # Copiar setup-inicial.bat a la raiz del servidor portable
        $appDir = "$dstDir\app"
        $scriptSetup = Join-Path $appDir "setup-inicial.bat"
        if (Test-Path $scriptSetup) {
            Copy-Item $scriptSetup $dstDir
        }
    } else {
        Write-Err "jpackage no genero la carpeta esperada: $srcDir"
        exit 1
    }

    Write-Ok "Servidor portable generado"
    Write-Note "  Ejecutable: packaging\distribuibles\windows\servidor\$NOMBRE_EJECUTABLE_SERVIDOR.exe"
    Write-Note "  Setup:      packaging\distribuibles\windows\servidor\setup-inicial.bat"
}

# ==================== CLIENTE INSTALADOR ====================

function Build-ClienteInstalador {
    param([string]$Tipo)
    Write-Step "Empaquetando cliente como instalador .$Tipo..."

    if ($Tipo -eq "exe") {
        Build-ClienteConInnoSetup
    }
    elseif ($Tipo -eq "msi") {
        Build-ClienteConWix
    }
}

function Build-ClienteConWix {
    $iconParams = Get-IconParam
    $modulosCliente = @(
        "java.base",
        "java.desktop",
        "java.logging",
        "java.naming",
        "java.sql",
        "java.xml",
        "java.xml.crypto",
        "java.management",
        "java.net.http",
        "java.prefs",
        "java.scripting",
        "java.security.jgss",
        "java.security.sasl",
        "jdk.unsupported",
        "jdk.crypto.ec",
        "jdk.accessibility"
    ) -join ","

    $baryxApiUrl = if ($env:BARYX_API_URL) { $env:BARYX_API_URL } else { "https://api.baryx.app" }

    $jpackageArgs = @(
        "--type", "msi",
        "--name", "Baryx",
        "--app-version", $APP_VERSION,
        "--vendor", $APP_VENDOR,
        "--copyright", $APP_COPYRIGHT,
        "--description", $APP_DESCRIPTION_CLIENTE,
        "--input", "$TEMP_DIR\cliente",
        "--main-jar", "baryx-cliente-$APP_VERSION.jar",
        "--main-class", "com.baryx.cliente.BaryxClienteLauncher",
        "--dest", $DIST_DIR,
        "--install-dir", "Baryx\Cliente",
        "--add-modules", $modulosCliente,
        "--win-menu",
        "--win-menu-group", "Baryx",
        "--win-shortcut",
        "--win-shortcut-prompt",
        "--java-options", "-Xms512m",
        "--java-options", "-Xmx1024m",
        "--java-options", "-XX:+UseG1GC",
        "--java-options", "-XX:MaxGCPauseMillis=50",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Dglass.win.uiScale=1.0",
        "--java-options", "-Dprism.allowHiDPIScaling=false",
        "--java-options", "-Dbaryx.api.url=$baryxApiUrl"
    ) + $iconParams

    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error al crear instalador .msi del cliente"
        exit 1
    }

    Write-Ok "Instalador .msi del cliente generado"
}

function Build-ClienteConInnoSetup {
    Write-Step "  Generando app-image del cliente con jpackage..."

    $iconParams = Get-IconParam
    $clienteAppImageDir = "$TEMP_DIR\cliente-appimage"
    New-Item -ItemType Directory -Force -Path $clienteAppImageDir | Out-Null

    $baryxApiUrl = if ($env:BARYX_API_URL) { $env:BARYX_API_URL } else { "https://api.baryx.app" }

    $jpackageArgs = @(
        "--type", "app-image",
        "--name", "Baryx",
        "--app-version", $APP_VERSION,
        "--vendor", $APP_VENDOR,
        "--copyright", $APP_COPYRIGHT,
        "--description", $APP_DESCRIPTION_CLIENTE,
        "--input", "$TEMP_DIR\cliente",
        "--main-jar", "baryx-cliente-$APP_VERSION.jar",
        "--main-class", "com.baryx.cliente.BaryxClienteLauncher",
        "--dest", $clienteAppImageDir,
        "--add-modules", "ALL-MODULE-PATH",
        "--java-options", "-Xms512m",
        "--java-options", "-Xmx1024m",
        "--java-options", "-XX:+UseG1GC",
        "--java-options", "-XX:MaxGCPauseMillis=50",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Dglass.win.uiScale=1.0",
        "--java-options", "-Dprism.allowHiDPIScaling=false",
        "--java-options", "-Dbaryx.api.url=$baryxApiUrl"
    ) + $iconParams

    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error al generar app-image del cliente"
        exit 1
    }

    $appImagePath = "$clienteAppImageDir\Baryx"
    if (-not (Test-Path $appImagePath)) {
        Write-Err "jpackage no genero la carpeta esperada: $appImagePath"
        exit 1
    }
    Write-Ok "App-image del cliente generada"

    # ── Copiar DLLs nativos de JavaFX al runtime ──
    Write-Step "  Copiando DLLs nativos de JavaFX al runtime..."

    $javafxGraphicsWinJar = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx\javafx-graphics\$JAVAFX_VERSION\javafx-graphics-$JAVAFX_VERSION-win.jar"

    if (-not (Test-Path $javafxGraphicsWinJar)) {
        Write-Err "No se encontro javafx-graphics-$JAVAFX_VERSION-win.jar en el repositorio Maven local"
        Write-Err "  Ruta esperada: $javafxGraphicsWinJar"
        Write-Err "  Ejecute '.\scripts\build.ps1 package' primero para descargar las dependencias"
        exit 1
    }

    $dllTempDir = Join-Path $TEMP_DIR "javafx-dlls"
    New-Item -ItemType Directory -Force -Path $dllTempDir | Out-Null

    Push-Location $dllTempDir
    & jar xf $javafxGraphicsWinJar
    Pop-Location

    $runtimeBinDir = Join-Path $appImagePath "runtime\bin"
    $dllFiles = Get-ChildItem $dllTempDir -Filter "*.dll"

    if ($dllFiles.Count -eq 0) {
        Write-Err "No se extrajeron DLLs nativos de javafx-graphics-$JAVAFX_VERSION-win.jar"
        exit 1
    }

    foreach ($dll in $dllFiles) {
        Copy-Item $dll.FullName -Destination $runtimeBinDir -Force
    }

    Write-Ok "$($dllFiles.Count) DLLs nativos de JavaFX copiados a runtime\bin\"

    # ── Copiar script de consola para diagnostico ──
    Write-Step "  Copiando script de consola para diagnostico..."

    $consolaSrc = Join-Path $PROJECT_DIR "packaging\windows\iniciar-consola.bat"
    if (-not (Test-Path $consolaSrc)) {
        Write-Err "No se encontro $consolaSrc"
        exit 1
    }
    $consolaBat = Join-Path $appImagePath "iniciar-consola.bat"
    $contenido = (Get-Content $consolaSrc -Raw) -replace '\{\{APP_VERSION\}\}', $APP_VERSION
    $utf8SinBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($consolaBat, $contenido, $utf8SinBom)
    Write-Ok "  iniciar-consola.bat copiado (version $APP_VERSION)"

    # ── Copiar java.exe al runtime para modo consola ──
    Write-Step "  Copiando java.exe al runtime para modo consola..."

    $jdkBin = (Get-Command java -ErrorAction SilentlyContinue).Source | Split-Path -Parent
    if (-not $jdkBin -or -not (Test-Path "$jdkBin\java.exe")) {
        Write-Err "No se encontro java.exe en el JDK. Verifique que JAVA_HOME este configurado."
        exit 1
    }

    Copy-Item "$jdkBin\java.exe" -Destination $runtimeBinDir -Force
    Write-Ok "  java.exe copiado a runtime\bin\"

    # ── Compilar instalador con Inno Setup ──
    Write-Step "  Compilando instalador con Inno Setup..."

    $issFile = Join-Path $PROJECT_DIR "packaging\windows\baryx-cliente.iss"
    if (-not (Test-Path $issFile)) {
        Write-Err "No se encontro $issFile"
        exit 1
    }

    $isccArgs = @(
        "/DAppVersion=$APP_VERSION",
        "/DAppPublisher=$APP_VENDOR",
        "/DAppCopyright=$APP_COPYRIGHT",
        "/DSourceDir=$appImagePath",
        "/DOutputDir=$DIST_DIR"
    )

    # Incluir servidor portable en el instalador
    $serverDir = "$DIST_DIR\servidor"
    if (Test-Path $serverDir) {
        $isccArgs += "/DServerDir=$serverDir"
    } else {
        Write-Err "No se encontro la carpeta del servidor portable en $serverDir"
        Write-Err "  Asegurese de empaquetar el servidor antes del cliente (modo 'todo')"
        exit 1
    }

    if (Test-Path $ICON_ICO) {
        $isccArgs += "/DIconFile=$ICON_ICO"
    }
    elseif (Test-Path $ICON_PNG) {
        $isccArgs += "/DIconFile=$ICON_PNG"
    }

    $licenseFile = Join-Path $PROJECT_DIR "LICENSE"
    if (Test-Path $licenseFile) {
        $isccArgs += "/DLicenseFile=$licenseFile"
    }

    & $script:IsccPath @isccArgs $issFile
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Inno Setup fallo al compilar el instalador"
        exit 1
    }

    $instaladorExe = "$DIST_DIR\Baryx-$APP_VERSION.exe"
    if (Test-Path $instaladorExe) {
        Write-Ok "Instalador .exe generado: Baryx-$APP_VERSION.exe"
    } else {
        Write-Err "No se encontro el instalador generado: $instaladorExe"
        exit 1
    }
}

# ==================== LIMPIEZA ====================

function Clear-Temp {
    if (Test-Path $TEMP_DIR) {
        Remove-Item -Recurse -Force $TEMP_DIR
    }
    Write-Note "Archivos temporales eliminados"
}

# ==================== RESUMEN ====================

function Show-Resumen {
    Write-Host ""
    Write-Host "  Resumen de distribucion" -ForegroundColor Yellow
    Write-Host "  ----------------------" -ForegroundColor Yellow

    $srvDir = "$DIST_DIR\servidor"
    if (Test-Path $srvDir) {
        $size = "{0:N1} MB" -f ((Get-ChildItem $srvDir -Recurse |
            Measure-Object -Property Length -Sum).Sum / 1MB)
        Write-Host "    servidor\                  ($size) - Portable con JRE" -ForegroundColor Green
    }

    foreach ($ext in @("*.msi", "*.exe")) {
        Get-ChildItem $DIST_DIR -Filter $ext -File -ErrorAction SilentlyContinue | ForEach-Object {
            $size = "{0:N1} MB" -f ($_.Length / 1MB)
            $desc = if ($_.Extension -eq ".exe") { "Instalador (cliente + servidor)" }
                    else { "Instalador (cliente)" }
            Write-Host "    $($_.Name)  ($size) - $desc" -ForegroundColor Green
        }
    }

    if (Test-Path "$DIST_DIR\LICENSE")   { Write-Host "    LICENSE" -ForegroundColor Cyan }
    if (Test-Path "$DIST_DIR\README.md") { Write-Host "    README.md" -ForegroundColor Cyan }

    Write-Host ""
    Write-Host "    Ubicacion: $DIST_DIR\" -ForegroundColor Cyan
    Write-Host ""
}

# ==================== PUNTO DE ENTRADA ====================

Write-Banner
Test-Requisitos

switch ($Modo) {
    "servidor" {
        Build-Jars
        Initialize-Dirs
        Initialize-Resources
        Build-ServidorPortable
        New-License
        New-Readme
        Clear-Temp
    }
    "exe" {
        Build-Jars
        Initialize-Dirs
        Initialize-Resources
        Build-ServidorPortable
        Build-ClienteInstalador -Tipo "exe"
        # Eliminar carpeta servidor/ de salida (ya incluida en el .exe)
        if (Test-Path "$DIST_DIR\servidor") {
            Remove-Item -Recurse -Force "$DIST_DIR\servidor"
            Write-Note "Carpeta servidor\ eliminada de salida (ya incluida en el .exe)"
        }
        New-License
        New-Readme
        Clear-Temp
    }
    "msi" {
        Build-Jars
        Initialize-Dirs
        Initialize-Resources
        Build-ClienteInstalador -Tipo "msi"
        New-License
        New-Readme
        Clear-Temp
    }
    "todo" {
        Build-Jars
        Initialize-Dirs
        Initialize-Resources
        Build-ServidorPortable
        Build-ClienteInstalador -Tipo $TipoPkg
        New-License
        New-Readme
        Clear-Temp
    }
}

Show-Resumen
Write-Ok "Empaquetado completado!"
Write-Host ""
