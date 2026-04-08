@echo off
chcp 65001 >nul 2>&1
title Kipu Cliente - Consola
echo.
echo   ======================================
echo     KIPU CLIENTE - Modo Consola
echo   ======================================
echo.
:: Script de diagnostico que ejecuta el cliente Kipu en una ventana
:: de consola visible, mostrando stdout/stderr para depuracion.
:: Se incluye automaticamente en la instalacion del cliente.
::
:: El marcador {{APP_VERSION}} se reemplaza durante el empaquetado
:: por la version real (ej: 1.0.0).

set "APP_DIR=%~dp0"
set "JAVA_EXE=%APP_DIR%runtime\bin\java.exe"
set "JAR_FILE=%APP_DIR%app\kipu-cliente-{{APP_VERSION}}.jar"

if not exist "%JAVA_EXE%" (
    echo [ERROR] No se encontro java.exe en runtime\bin\
    echo         Verifique que la instalacion este completa.
    pause
    exit /b 1
)

if not exist "%JAR_FILE%" (
    echo [ERROR] No se encontro kipu-cliente-{{APP_VERSION}}.jar en app\
    pause
    exit /b 1
)

echo   Iniciando cliente...
echo   Logs visibles en esta ventana.
echo   Cierre esta ventana para detener el cliente.
echo.

"%JAVA_EXE%" -Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Dfile.encoding=UTF-8 -Dglass.win.uiScale=1.0 -Dprism.allowHiDPIScaling=false -cp "%JAR_FILE%" com.kipu.cliente.KipuClienteLauncher

echo.
echo   El cliente se ha cerrado.
pause
