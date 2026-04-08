# Kipu - Script de compilacion centralizado (PowerShell)
# Usa el POM padre para compilar los 3 modulos de una vez
# Genera JARs en kipu-servidor/target/ y kipu-cliente/target/.

# Forzar codificacion UTF-8 en la consola de Windows
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)

function Write-Banner {
    Write-Host "KIPU - Compilacion JAR Windows" -ForegroundColor Yellow
}

function Write-Step($msg)    { Write-Host "  [STEP] $msg" -ForegroundColor Blue }
function Write-Ok($msg)      { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Err($msg)     { Write-Host "  [ERROR] $msg" -ForegroundColor Red }

Write-Banner

if (-not (Test-Path "$ProjectDir\pom.xml")) {
    Write-Err "No se encontro pom.xml en $ProjectDir"
    exit 1
}

Set-Location $ProjectDir

$Mode = if ($args.Count -gt 0) { $args[0] } else { "package" }

switch ($Mode) {
    "clean" {
        Write-Step "Limpiando todos los modulos..."
        & mvn clean
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Ok "Limpieza completada"
    }
    "compile" {
        Write-Step "Compilando todos los modulos..."
        & mvn compile
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Ok "Compilacion completada"
    }
    "package" {
        Write-Step "Empacando todos los modulos (sin tests)..."
        & mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Ok "Empacado completado"
        Write-Host ""
        Write-Host "  JARs generados:" -ForegroundColor Cyan
        Write-Host "    kipu-servidor\target\kipu-servidor-1.0.0.jar"
        Write-Host "    kipu-cliente\target\kipu-cliente-1.0.0.jar"
    }
    "install" {
        Write-Step "Instalando todos los modulos en repositorio local..."
        & mvn clean install -DskipTests
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Ok "Instalacion completada"
    }
    "test" {
        Write-Step "Ejecutando tests de todos los modulos..."
        & mvn clean test
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Ok "Tests completados"
    }
    "full" {
        Write-Step "Build completo (compile + test + package)..."
        & mvn clean package
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Ok "Build completo finalizado"
    }
    default {
        Write-Host "Uso: .\build.ps1 [clean|compile|package|install|test|full]"
        Write-Host ""
        Write-Host "  clean    - Limpia artefactos de compilacion"
        Write-Host "  compile  - Solo compila (rapido)"
        Write-Host "  package  - Compila y empaca JARs (por defecto, sin tests)"
        Write-Host "  install  - Empaca e instala en repositorio local Maven"
        Write-Host "  test     - Ejecuta todos los tests"
        Write-Host "  full     - Build completo con tests"
        exit 1
    }
}

Write-Host ""
Write-Host "Para iniciar el sistema:" -ForegroundColor Cyan
Write-Host "  1. Servidor: java -jar kipu-servidor\target\kipu-servidor-1.0.0.jar"
Write-Host "  2. Cliente:  java -jar kipu-cliente\target\kipu-cliente-1.0.0.jar"
Write-Host ""
Write-Ok "Listo!"
