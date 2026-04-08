# Kipu — Guía de Instalación

Sistema POS (punto de venta) + gestión de pedidos para bares, restaurantes y locales nocturnos.  
Funciona **100% en red local (LAN)**, sin necesidad de internet.

> **No necesita instalar Java.** Tanto el servidor como el cliente incluyen su propio runtime embebido.

---

## Tabla de Contenidos

- [¿Qué es Kipu?](#qué-es-kipu)
- [Contenido del Paquete](#contenido-del-paquete)
- [Requisitos Previos](#requisitos-previos)
- [Instalación en Windows](#instalación-en-windows)
- [Instalación en Linux](#instalación-en-linux)
- [Configuración en Red (LAN)](#configuración-en-red-lan)
- [Uso Básico](#uso-básico)
- [Inicio Manual del Servidor](#inicio-manual-del-servidor)
- [Actualizar Versión](#actualizar-versión)
- [Solución de Problemas](#solución-de-problemas)
- [Licencia](#licencia)

---

## ¿Qué es Kipu?

Kipu es un sistema de punto de venta diseñado para la operación nocturna de bares y locales de alimentos y bebidas. Permite gestionar mesas, pedidos, productos, facturación y pagos desde múltiples terminales conectadas a un servidor central dentro de la misma red local.

### Características principales

- **Interfaz táctil premium** con diseño luxury dorado + negro, optimizado para ambientes oscuros
- **Login dual**: administrador (usuario/contraseña) y empleados (código + PIN numérico)
- **Gestión de mesas y pedidos** en tiempo real
- **Catálogo de productos** organizado por categorías
- **POS completo** con grid de productos, carrito y panel de pagos
- **Métodos de pago configurables** (efectivo, débito, crédito, transferencia, QR, mixto)
- **Tres roles**: Mesero, Cajero y Administrador, cada uno con su interfaz y permisos
- **Host Mode**: el cliente puede levantar el servidor automáticamente (ideal para bares pequeños con un solo equipo)
- **Sin dependencia de internet** — opera exclusivamente en red local

---

## Contenido del Paquete

### Windows

```
Kipu/
├── servidor/                    Servidor portable (con JRE incluido)
│   ├── KipuServidor.exe        Ejecutable nativo del servidor
│   ├── runtime/                 Runtime Java embebido
│   ├── app/                     Librerías de la aplicación
│   └── setup-inicial.bat         Setup de BD + inicio del servidor (doble clic)
├── Kipu-1.0.0.exe              Instalador del cliente (.exe)
├── LICENSE                      Licencia del software
└── README.md                    Este archivo
```

### Linux

```
Kipu/
├── servidor/                    Servidor portable (con JRE incluido)
│   ├── bin/KipuServidor        Ejecutable nativo del servidor
│   ├── lib/                     Runtime Java embebido + librerías
│   └── setup-inicial.sh          Setup de BD + inicio del servidor
├── kipu_1.0.0_amd64.deb        Instalador del cliente (Debian/Ubuntu)
├── LICENSE                      Licencia del software
└── README.md                    Este archivo
```

> **Nota:** Según la plataforma donde se empaquetó, el instalador del cliente será `.exe`/`.msi` (Windows) o `.deb`/`.rpm` (Linux).

---

## Requisitos Previos

| Componente | Versión mínima | Descripción |
|------------|---------------|-------------|
| **PostgreSQL** | 12+ | Base de datos del servidor |
| **psql** | (incluido con PostgreSQL) | Necesario para el script de setup |

- **Sistema operativo**: Windows 10+ o Linux (Debian/Ubuntu/Fedora/RHEL)
- **RAM**: 4 GB mínimo para el equipo servidor
- **Espacio en disco**: ~500 MB (servidor + cliente con runtime embebido)
- **Red**: LAN configurada si se usan múltiples terminales

> **Java NO es necesario.** El servidor y el cliente ya incluyen su propio runtime Java. No necesita descargar ni instalar Java, JDK ni JRE.

---

## Instalación en Windows

### Paso 1 — Instalar PostgreSQL

Si aún no tiene PostgreSQL instalado:

1. Descargue el instalador desde https://www.postgresql.org/download/windows/
2. Ejecute el instalador y siga el asistente
3. **Importante**: Guarde la contraseña del usuario `postgres` que configure durante la instalación
4. Asegúrese de que la opción "Command Line Tools" esté seleccionada (incluye `psql`)

> **Tip**: Si durante el setup de Kipu le indica que `psql` no se encuentra, agregue la ruta de PostgreSQL al PATH del sistema. Normalmente es `C:\Program Files\PostgreSQL\16\bin`.

### Paso 2 — Configurar la base de datos

1. Abra la carpeta `servidor\` del paquete
2. **Doble clic** en `setup-inicial.bat`
3. El asistente le pedirá:
   - **Host y puerto de PostgreSQL** (por defecto: `localhost` y `5432`)
   - **Usuario administrador de PostgreSQL** (por defecto: `postgres`) y su contraseña
   - **Nombre de la base de datos** de Kipu (por defecto: `kipu_db`)
   - **Usuario y contraseña** para la aplicación (por defecto: `kipu_admin`)
4. El script creará automáticamente la base de datos, el usuario y los permisos necesarios
5. Generará el archivo de configuración en `C:\Users\<su_usuario>\.kipu\.env`

> **Este paso solo se ejecuta una vez**, antes del primer uso. Si necesita reconfigurar, ejecute `setup-inicial.bat` nuevamente — el script detectará la configuración existente y le preguntará si desea reconfigurar.

### Paso 3 — Instalar el cliente

1. **Doble clic** en `Kipu-1.0.0.exe` (o `Kipu-1.0.0.msi`)
2. Siga el asistente de instalación
3. Se crearán accesos directos en el menú Inicio y (opcionalmente) en el escritorio

### Paso 4 — Copiar la carpeta del servidor

Copie la carpeta `servidor\` a una ubicación permanente en el equipo:

```
C:\Kipu\servidor\
```

> El cliente buscará el servidor automáticamente en esta ruta cuando esté en Host Mode.

### Paso 5 — Iniciar Kipu

Abra **Kipu** desde el menú Inicio o el acceso directo del escritorio.  
Si el Host Mode está activado (valor por defecto), el servidor se iniciará automáticamente en segundo plano.

**Credenciales iniciales del administrador:**

| Campo | Valor |
|-------|-------|
| Usuario | `admin` |
| Contraseña | `admin123` |

> **Cambie la contraseña del administrador** después del primer inicio de sesión.

---

## Instalación en Linux

### Paso 1 — Instalar PostgreSQL

**Debian / Ubuntu:**

```bash
sudo apt update
sudo apt install postgresql postgresql-client
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

**Fedora / RHEL / CentOS:**

```bash
sudo dnf install postgresql-server postgresql
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

Verifique que PostgreSQL esté corriendo:

```bash
sudo systemctl status postgresql
```

### Paso 2 — Configurar la base de datos

```bash
cd servidor/
chmod +x setup-inicial.sh
./setup-inicial.sh
```

El asistente interactivo le pedirá los mismos datos que en Windows (host, puerto, usuario admin de PostgreSQL, credenciales de la aplicación). Al finalizar:

- Se crea la base de datos y el usuario
- Se generan los permisos
- Se guarda la configuración en `~/.kipu/.env`

### Paso 3 — Instalar el cliente

**Debian / Ubuntu (.deb):**

```bash
sudo dpkg -i kipu_1.0.0_amd64.deb
```

Si hay dependencias faltantes:

```bash
sudo apt --fix-broken install
```

**Fedora / RHEL (.rpm):**

```bash
sudo rpm -i kipu-1.0.0.x86_64.rpm
```

### Paso 4 — Copiar la carpeta del servidor

```bash
sudo mkdir -p /opt/kipu
sudo cp -r servidor/ /opt/kipu/servidor/
sudo chmod +x /opt/kipu/servidor/bin/KipuServidor
sudo chmod +x /opt/kipu/servidor/setup-inicial.sh
```

### Paso 5 — Iniciar Kipu

Abra **Kipu** desde el menú de aplicaciones. El servidor se iniciará automáticamente si el Host Mode está activado.

**Credenciales iniciales del administrador:**

| Campo | Valor |
|-------|-------|
| Usuario | `admin` |
| Contraseña | `admin123` |

> **Cambie la contraseña del administrador** después del primer inicio de sesión.

---

## Configuración en Red (LAN)

Para usar Kipu con múltiples terminales (cajeros, meseros) conectadas al mismo servidor en la red local:

### Equipo servidor (Host)

Es el PC principal donde corre PostgreSQL y el servidor Kipu.

1. Realice la instalación completa (pasos 1 a 5 según su SO)
2. Identifique la **IP local** del equipo:
   - **Windows**: Abra CMD y ejecute `ipconfig` → busque "IPv4 Address" (ej: `192.168.1.100`)
   - **Linux**: Ejecute `ip addr` o `hostname -I` (ej: `192.168.1.100`)
3. Asegúrese de que el **puerto 8080** esté permitido en el firewall:
   - **Windows**: Panel de Control → Firewall → Reglas de entrada → Nueva regla → Puerto 8080
   - **Linux**: `sudo ufw allow 8080/tcp` (Ubuntu) o `sudo firewall-cmd --add-port=8080/tcp --permanent` (Fedora)

### Equipos cliente (terminales adicionales)

Son los PCs/tablets de cajeros y meseros.

1. Instale **solo el cliente** (paso 3 según su SO)
2. No necesita PostgreSQL ni la carpeta `servidor/`
3. Abra la configuración del cliente y ajuste:
   - **Host Mode**: `false` (desactivado)
   - **IP del servidor**: La IP del equipo host (ej: `192.168.1.100`)
   - **Puerto**: `8080`

### Ejemplo de topología

```
                    ┌──────────────────┐
                    │   PC Servidor    │
                    │  192.168.1.100   │
                    │                  │
                    │  PostgreSQL      │
                    │  Servidor Kipu  │
                    │  Cliente Kipu   │
                    └───────┬──────────┘
                            │ LAN (puerto 8080)
              ┌─────────────┼─────────────┐
              │             │             │
     ┌────────┴───┐  ┌──────┴─────┐  ┌───┴────────┐
     │ PC Cajero  │  │  Tablet    │  │ PC Mesero  │
     │ (POS)      │  │  Mesero 1  │  │    2       │
     │ Cliente    │  │  Cliente   │  │  Cliente   │
     └────────────┘  └────────────┘  └────────────┘
```

> **Todos los equipos deben estar conectados a la misma red local (LAN).** Kipu no requiere ni utiliza internet.

---

## Uso Básico

### Roles del sistema

| Rol | ¿Qué puede hacer? | Cómo inicia sesión |
|-----|--------------------|--------------------|
| **Administrador** | Todo: gestionar usuarios, productos, categorías, métodos de pago, configuración | Usuario + contraseña |
| **Cajero** | Procesar pedidos, cobrar, ver ventas del día | Código + PIN |
| **Mesero** | Crear y modificar pedidos, gestionar mesas asignadas | Código + PIN |

### Flujo típico de operación

1. **El administrador** configura productos, categorías, mesas y crea usuarios (meseros y cajeros)
2. **Los meseros** inician sesión desde la comandera, ven sus mesas y toman pedidos
3. **El cajero** selecciona el mesero, visualiza los pedidos activos y procesa el cobro desde el POS
4. Los pedidos se facturan y la mesa queda liberada para el siguiente cliente

---

## Inicio Manual del Servidor

Normalmente el cliente inicia el servidor automáticamente (Host Mode). Si necesita iniciarlo por separado:

### Windows

```
cd C:\Kipu\servidor\
setup-inicial.bat
```

### Linux

```bash
cd /opt/kipu/servidor/
./setup-inicial.sh
```

Si la configuración ya existe, el script saltará el setup de base de datos y le preguntará si desea iniciar el servidor directamente.

El servidor arranca en el **puerto 8080** por defecto. Verá los logs en la consola.  
Presione `Ctrl+C` para detenerlo.

---

## Actualizar Versión

1. **Detenga el servidor** si está corriendo (cierre el cliente o `Ctrl+C` en la consola del servidor)
2. **Reemplace la carpeta** `servidor/` con la nueva versión
3. **Instale el nuevo cliente** (ejecute el instalador nuevo, desinstale el anterior si es necesario)
4. **Inicie Kipu** normalmente

> Las migraciones de base de datos se aplican automáticamente al arrancar el servidor. No necesita hacer nada manual con la BD al actualizar.

---

## Solución de Problemas

### El setup dice que `psql` no se encontró

`psql` es la herramienta de línea de comandos de PostgreSQL. Verifique que PostgreSQL esté instalado y que `psql` esté en el PATH del sistema.

- **Windows**: Agregue `C:\Program Files\PostgreSQL\16\bin` (ajuste la versión) al PATH del sistema.
- **Linux**: Instale `postgresql-client` (`sudo apt install postgresql-client`).

### El servidor no inicia

1. Verifique que PostgreSQL esté corriendo:
   - **Windows**: Abra "Servicios" y busque "postgresql" → Estado: "En ejecución"
   - **Linux**: `sudo systemctl status postgresql`
2. Verifique que el archivo de configuración exista:
   - **Windows**: `C:\Users\<su_usuario>\.kipu\.env`
   - **Linux**: `~/.kipu/.env`
3. Si no existe, ejecute nuevamente `setup-inicial.bat` (Windows) o `setup-inicial.sh` (Linux)
4. Revise los logs en la consola del servidor para más detalles

### Puerto 8080 en uso

Otro programa está usando el puerto 8080. Identifique y detenga el proceso:

- **Windows**: `netstat -ano | findstr :8080` → anote el PID → `taskkill /PID <PID> /F`
- **Linux**: `sudo lsof -i :8080` → anote el PID → `sudo kill <PID>`

### El cliente no se conecta al servidor

1. Verifique que el servidor esté corriendo (debería responder en `http://<IP>:8080`)
2. Compruebe que ambos equipos estén en la **misma red LAN**
3. Haga ping desde el cliente al servidor: `ping 192.168.1.100`
4. Verifique que el **firewall** del equipo servidor permita conexiones en el puerto 8080
5. En la configuración del cliente, confirme que la IP y el puerto sean correctos

### "No se encontró el servidor" al abrir el cliente

El cliente en Host Mode busca la carpeta del servidor en una ruta predeterminada. Asegúrese de copiar `servidor/` a:

- **Windows**: `C:\Kipu\servidor\`
- **Linux**: `/opt/kipu/servidor/`

### La base de datos no se crea correctamente

1. Verifique que puede conectarse a PostgreSQL manualmente:
   ```
   psql -h localhost -p 5432 -U postgres -d postgres
   ```
2. Confirme que el usuario `postgres` tiene permisos de superusuario
3. Si la base de datos ya existe pero quiere recrearla, elimínela primero:
   ```sql
   DROP DATABASE kipu_db;
   ```
4. Ejecute el setup nuevamente

---

## Licencia

Kipu es software source-available bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).  
El uso, copia o distribución sin autorización expresa del titular está prohibido.

Consulte el archivo `LICENSE` incluido en este paquete para los términos completos.

© 2026 Dilan Acuña / Kipu. Todos los derechos reservados.
