# Baryx - Instalacion Linux

Sistema POS para bares y locales nocturnos. Funciona 100% en red local (LAN), sin internet.

> **No necesita instalar Java.** El paquete incluye todo lo necesario.

---

## Que incluye el instalador

El archivo `baryx_1.0.0_amd64.deb` instala:

- **Cliente Baryx** en `/opt/baryx/cliente/` — interfaz grafica (POS, comanderas, admin)
- **Servidor Baryx** en `/opt/baryx/servidor/` — API REST que gestiona la base de datos

Ambos con JRE embebido. Solo necesita tener **PostgreSQL** instalado.

---

## Instalacion rapida

### 1. Instalar PostgreSQL

```bash
# Debian / Ubuntu
sudo apt update
sudo apt install postgresql postgresql-client

# Fedora / RHEL
sudo dnf install postgresql-server postgresql
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

### 2. Instalar Baryx

```bash
# Debian / Ubuntu
sudo dpkg -i baryx_1.0.0_amd64.deb

# Si falla por dependencias:
sudo apt --fix-broken install

# Fedora / RHEL (si tiene .rpm)
sudo rpm -i baryx-1.0.0.x86_64.rpm
```

Se instala en `/opt/baryx/` y crea:
- Acceso directo en el menu de aplicaciones
- Comando `baryx` disponible en terminal

### 3. Configurar la base de datos (primera vez)

```bash
sudo /opt/baryx/servidor/setup-inicial.sh
```

El asistente crea la base de datos, el usuario y genera la configuracion (`~/.baryx/.env`).  
Al terminar, ofrece iniciar el servidor.

### 4. Abrir Baryx

Desde el menu de aplicaciones: **Baryx Cliente**  
O desde terminal: `baryx`

El cliente inicia el servidor automaticamente (Host Mode).

**Credenciales iniciales:** usuario `admin`, contrasena `admin123`  
Cambiar la contrasena despues del primer inicio.

---

## Uso en red (multiples terminales)

### Equipo servidor (PC principal)

1. Instalar Baryx completo (pasos 1-4)
2. Anotar la IP local (`hostname -I` o `ip addr`, ej: `192.168.1.100`)
3. Abrir el puerto 8080:
   ```bash
   # Ubuntu
   sudo ufw allow 8080/tcp
   # Fedora
   sudo firewall-cmd --add-port=8080/tcp --permanent && sudo firewall-cmd --reload
   ```

### Equipos cliente (cajeros, meseros)

1. Instalar solo el `.deb` (incluye todo, no necesitan PostgreSQL)
2. Configurar:
   - Host Mode: desactivado
   - IP del servidor: la IP del equipo principal
   - Puerto: 8080

> Todos los equipos deben estar en la misma red local.

---

## Iniciar el servidor manualmente

Si no usa Host Mode:

```bash
/opt/baryx/servidor/setup-inicial.sh
```

El script detecta si ya esta configurado y ofrece arrancar directamente.  
El servidor usa el puerto 8080. Detener con `Ctrl+C`.

---

## Actualizar version

1. Detener el servidor
2. Instalar la nueva version:
   ```bash
   sudo dpkg -i baryx_X.Y.Z_amd64.deb
   ```
3. Abrir Baryx normalmente (las migraciones de BD se aplican solas)

---

## Desinstalar

```bash
sudo apt remove baryx       # Desinstalar
sudo apt purge baryx        # Desinstalar + limpiar /opt/baryx
```

PostgreSQL y su base de datos **no** se tocan.  
Los archivos de configuracion (`~/.baryx/`) se conservan salvo con `purge`.

---

## Problemas comunes

| Problema | Solucion |
|----------|----------|
| `psql` no encontrado | `sudo apt install postgresql-client` |
| Servidor no inicia | `sudo systemctl status postgresql` — verificar que este corriendo |
| Puerto 8080 en uso | `sudo lsof -i :8080` → `sudo kill <PID>` |
| Cliente no conecta | Verificar IP, puerto 8080 y firewall del equipo servidor |
| "No se encontro el servidor" | Verificar que existe `/opt/baryx/servidor/bin/BaryxServidor` |

---

## Licencia

Software source-available bajo Licencia de Uso de Software Baryx (basada en Elastic License 2.0).  
Consulte el archivo LICENSE para los terminos completos.

(c) 2026 Dilan Acuna / Baryx. Todos los derechos reservados.
