# Kipu - Instalacion Windows

Sistema POS para bares y locales nocturnos. Funciona 100% en red local (LAN), sin internet.

> **No necesita instalar Java.** El instalador incluye todo lo necesario.

---

## Que incluye el instalador

El archivo `Kipu-1.0.0.exe` instala:

- **Cliente Kipu** — interfaz grafica para cajeros, meseros y administradores
- **Servidor Kipu** — API REST que gestiona la base de datos

Ambos con JRE embebido. Solo necesita tener **PostgreSQL** instalado.

---

## Instalacion rapida

### 1. Instalar PostgreSQL

Descargar desde https://www.postgresql.org/download/windows/ e instalar.  
Guardar la contrasena del usuario `postgres`.

> Si `psql` no se encuentra despues, agregar `C:\Program Files\PostgreSQL\16\bin` al PATH del sistema.

### 2. Ejecutar el instalador

Doble clic en `Kipu-1.0.0.exe` y seguir el asistente.

Se instala en `C:\Program Files\Kipu\` con:
- `Cliente\` — aplicacion grafica
- `servidor\` — servidor REST

### 3. Configurar la base de datos (primera vez)

Desde el menu Inicio: **Kipu > Configuracion de base de datos**  
O ejecutar directamente: `C:\Program Files\Kipu\servidor\setup-inicial.bat`

El asistente crea la base de datos, el usuario y genera la configuracion (`~\.kipu\.env`).  
Al terminar, ofrece iniciar el servidor.

### 4. Abrir Kipu

Desde el menu Inicio o el escritorio: **Kipu Cliente**

El cliente inicia el servidor automaticamente (Host Mode).

**Credenciales iniciales:** usuario `admin`, contrasena `admin123`  
Cambiar la contrasena despues del primer inicio.

---

## Uso en red (multiples terminales)

### Equipo servidor (PC principal)

1. Instalar Kipu completo (pasos 1-4)
2. Anotar la IP local (`ipconfig` → Direccion IPv4, ej: `192.168.1.100`)
3. Abrir el puerto 8080 en el firewall de Windows

### Equipos cliente (cajeros, meseros)

1. Instalar solo el cliente (solo necesitan el `.exe`)
2. Configurar:
   - Host Mode: desactivado
   - IP del servidor: la IP del equipo principal
   - Puerto: 8080

> Todos los equipos deben estar en la misma red local.

---

## Iniciar el servidor manualmente

Si no usa Host Mode, inicie el servidor con:  
Menu Inicio > **Kipu > Configuracion de base de datos**  
El script detecta si ya esta configurado y ofrece arrancar directamente.

---

## Actualizar version

1. Detener el servidor (cerrar el cliente o Ctrl+C)
2. Ejecutar el nuevo instalador — desinstala la version anterior automaticamente
3. Abrir Kipu normalmente (las migraciones de BD se aplican solas)

---

## Desinstalar

Panel de Control > Programas > **Kipu** > Desinstalar

La carpeta del servidor y los datos de configuracion (`~\.kipu\`) se eliminan.  
PostgreSQL y su base de datos **no** se tocan.

---

## Problemas comunes

| Problema | Solucion |
|----------|----------|
| `psql` no encontrado | Agregar `C:\Program Files\PostgreSQL\16\bin` al PATH |
| Servidor no inicia | Verificar que PostgreSQL este corriendo (Servicios > postgresql) |
| Puerto 8080 en uso | `netstat -ano \| findstr :8080` → `taskkill /PID <PID> /F` |
| Cliente no conecta | Verificar IP, puerto 8080 y firewall del equipo servidor |
| "No se encontro el servidor" | El cliente busca el servidor en `..\..\servidor\` relativo a su instalacion |

---

## Licencia

Software source-available bajo Licencia de Uso de Software Kipu (basada en Elastic License 2.0).  
Consulte el archivo LICENSE para los terminos completos.

(c) 2026 Dilan Acuna / Kipu. Todos los derechos reservados.
