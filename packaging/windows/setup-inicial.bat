@echo off
chcp 65001 >nul
title KipuServidor - Setup Inicial
setlocal EnableDelayedExpansion
:: Kipu - Setup Inicial y Arranque del Servidor
:: Script unificado que:
::   1. Configura PostgreSQL y genera el archivo .env
::      (solo la primera vez o si se elige reconfigurar)
::   2. Arranca el servidor usando el JRE embebido
:: El archivo .env se guarda en %USERPROFILE%\.kipu\.env
:: y el servidor lo detecta automaticamente.
:: Requisitos:
::   - PostgreSQL instalado y en ejecucion (para el setup)
::   - psql disponible en el PATH (para el setup)
set "SCRIPT_DIR=%~dp0"
set "CONFIG_DIR=%USERPROFILE%\.kipu"
set "ENV_FILE=%CONFIG_DIR%\.env"

echo   KIPU - Setup Inicial Windows

if exist "%ENV_FILE%" (
    echo [INFO] Configuracion existente detectada: %ENV_FILE%
    echo.
    set /p "RESP_RECONFIG=Desea reconfigurar la base de datos? (s/N): "
    if /i "!RESP_RECONFIG!"=="s" (
        goto :setup_db
    ) else (
        echo [INFO] Usando configuracion existente.
        goto :preguntar_iniciar
    )
) else (
    echo [INFO] Primera ejecucion - iniciando configuracion de la base de datos.
    echo.
    goto :setup_db
)

:setup_db
where psql >nul 2>nul
if errorlevel 1 (
    echo [ERROR] psql no encontrado en PATH.
    echo   Instale PostgreSQL y asegurese de que psql.exe este en el PATH.
    echo   Normalmente: C:\Program Files\PostgreSQL\16\bin
    echo.
    pause
    exit /b 1
)

echo [PASO] Datos de conexion a PostgreSQL (superusuario)
echo.

set "PG_HOST=localhost"
set /p "PG_HOST=  Host PostgreSQL [localhost]: "

set "PG_PUERTO=5432"
set /p "PG_PUERTO=  Puerto PostgreSQL [5432]: "

set "PG_SUPER=postgres"
set /p "PG_SUPER=  Usuario admin PostgreSQL [postgres]: "

set /p "PG_SUPER_CLAVE=  Contrasena del admin PostgreSQL: "

echo.
echo [PASO] Datos de la base de datos de Kipu
echo.

set "DB_NOMBRE=kipu_db"
set /p "DB_NOMBRE=  Nombre base de datos [kipu_db]: "

set "DB_USUARIO=kipu_admin"
set /p "DB_USUARIO=  Usuario aplicacion [kipu_admin]: "

set /p "DB_CLAVE=  Contrasena del usuario aplicacion: "
set /p "DB_CLAVE_CONFIRM=  Confirmar contrasena: "

if not "!DB_CLAVE!"=="!DB_CLAVE_CONFIRM!" (
    echo.
    echo [ERROR] Las contrasenas no coinciden.
    pause
    exit /b 1
)

if "!DB_CLAVE!"=="" (
    echo.
    echo [ERROR] La contrasena no puede estar vacia.
    pause
    exit /b 1
)

echo.
echo [PASO] Validando conexion como administrador PostgreSQL...

set "PGPASSWORD=!PG_SUPER_CLAVE!"
psql -h !PG_HOST! -p !PG_PUERTO! -U !PG_SUPER! -d postgres -c "SELECT version();" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No se pudo conectar a PostgreSQL con las credenciales proporcionadas.
    set "PGPASSWORD="
    pause
    exit /b 1
)
echo [OK] Conexion de administrador validada

echo.
echo [PASO] Creando/actualizando usuario de aplicacion...

psql -h !PG_HOST! -p !PG_PUERTO! -U !PG_SUPER! -d postgres -c "CREATE ROLE !DB_USUARIO! WITH LOGIN PASSWORD '!DB_CLAVE!' NOSUPERUSER NOCREATEDB NOCREATEROLE;" 2>nul
psql -h !PG_HOST! -p !PG_PUERTO! -U !PG_SUPER! -d postgres -c "ALTER ROLE !DB_USUARIO! WITH PASSWORD '!DB_CLAVE!';"
if errorlevel 1 (
    echo [ERROR] No se pudo crear/actualizar el usuario de aplicacion.
    set "PGPASSWORD="
    pause
    exit /b 1
)
echo [OK] Usuario de aplicacion listo

echo.
echo [PASO] Creando/actualizando base de datos...

psql -h !PG_HOST! -p !PG_PUERTO! -U !PG_SUPER! -d postgres -c "CREATE DATABASE !DB_NOMBRE! OWNER !DB_USUARIO! ENCODING 'UTF8';" 2>nul
psql -h !PG_HOST! -p !PG_PUERTO! -U !PG_SUPER! -d postgres -c "ALTER DATABASE !DB_NOMBRE! OWNER TO !DB_USUARIO!;"
if errorlevel 1 (
    echo [ERROR] No se pudo crear/actualizar la base de datos.
    set "PGPASSWORD="
    pause
    exit /b 1
)
echo [OK] Base de datos lista

echo.
echo [PASO] Aplicando permisos sobre schema public...

psql -h !PG_HOST! -p !PG_PUERTO! -U !PG_SUPER! -d !DB_NOMBRE! -c "ALTER SCHEMA public OWNER TO !DB_USUARIO!; GRANT ALL ON SCHEMA public TO !DB_USUARIO!; GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO !DB_USUARIO!; GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO !DB_USUARIO!; GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO !DB_USUARIO!; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO !DB_USUARIO!; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO !DB_USUARIO!; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO !DB_USUARIO!;"
if errorlevel 1 (
    echo [ERROR] No se pudieron aplicar los permisos.
    set "PGPASSWORD="
    pause
    exit /b 1
)
echo [OK] Permisos aplicados

set "PGPASSWORD="

echo.
echo [PASO] Generando archivo de configuracion .env...

for /f "delims=" %%i in ('powershell -NoProfile -Command "[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])"') do set "JWT_SECRET=%%i"

if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

(
echo # Configuracion Kipu
echo DB_HOST=!PG_HOST!
echo DB_PORT=!PG_PUERTO!
echo DB_NAME=!DB_NOMBRE!
echo DB_USER=!DB_USUARIO!
echo DB_PASSWORD=!DB_CLAVE!
echo JWT_SECRET=!JWT_SECRET!
) > "%ENV_FILE%"

echo [OK] Archivo .env creado en: %ENV_FILE%

echo   Setup completado exitosamente

:preguntar_iniciar

echo.
set "RESP_INICIAR=S"
set /p "RESP_INICIAR=Desea iniciar el servidor ahora? (S/n): "
if /i "!RESP_INICIAR!"=="n" (
    echo.
    echo [INFO] Para iniciar el servidor mas adelante, ejecute este mismo script o
    echo inicie el cliente para que este en modo host lo haga automaticamente con esta configuracion.
    echo [INFO] Para iniciar el servidor manualmente, ejecute .\setup-inicial.bat
    echo.
    pause
    exit /b 0
)

if exist "%ENV_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
        echo %%a | findstr /r "^#" >nul || set "%%a=%%b"
    )
) else (
    echo [ERROR] No se encontro configuracion. Se requiere ejecutar el setup primero.
    pause
    exit /b 1
)

:: Buscar ejecutable del servidor
set "EXE=%SCRIPT_DIR%KipuServidor.exe"
if not exist "%EXE%" (
    echo [ERROR] No se encontro KipuServidor.exe en %SCRIPT_DIR%
    pause
    exit /b 1
)

echo   KIPU - Servidor API REST
echo   BD   : %DB_HOST%:%DB_PORT%/%DB_NAME%
echo   User : %DB_USER%
echo.
echo   Iniciando... (Ctrl+C para detener)
echo.

endlocal
"%~dp0KipuServidor.exe" %*
