# Kipu - Instalacion Linux

Sistema POS para bares y locales nocturnos. Funciona 100% en red local (LAN), sin internet.

> **No necesita instalar Java.** El paquete incluye todo lo necesario.

---

## Que incluye el instalador

El archivo `kipu_1.0.0_amd64.deb` instala:

- **Cliente Kipu** en `/opt/kipu/cliente/` — interfaz grafica (POS, comanderas, admin)
- **Servidor Kipu** en `/opt/kipu/servidor/` — API REST que gestiona la base de datos

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

### 2. Instalar Kipu

```bash
# Debian / Ubuntu
sudo dpkg -i kipu_1.0.0_amd64.deb

# Si falla por dependencias:
sudo apt --fix-broken install

# Fedora / RHEL (si tiene .rpm)
sudo rpm -i kipu-1.0.0.x86_64.rpm
```

Se instala en `/opt/kipu/` y crea:
- Acceso directo en el menu de aplicaciones
- Comando `kipu` disponible en terminal

### 3. Configurar la base de datos (primera vez)

```bash
sudo /opt/kipu/servidor/setup-inicial.sh
```

El asistente crea la base de datos, el usuario y genera la configuracion (`~/.kipu/.env`).  
Al terminar, ofrece iniciar el servidor.

### 4. Abrir Kipu

Desde el menu de aplicaciones: **Kipu Cliente**  
O desde terminal: `kipu`

El cliente inicia el servidor automaticamente (Host Mode).

**Credenciales iniciales:** usuario `admin`, contrasena `admin123`  
Cambiar la contrasena despues del primer inicio.

---

## Uso en red (multiples terminales)

### Equipo servidor (PC principal)

1. Instalar Kipu completo (pasos 1-4)
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
/opt/kipu/servidor/setup-inicial.sh
```

El script detecta si ya esta configurado y ofrece arrancar directamente.  
El servidor usa el puerto 8080. Detener con `Ctrl+C`.

---

## Actualizar version

1. Detener el servidor
2. Instalar la nueva version:
   ```bash
   sudo dpkg -i kipu_X.Y.Z_amd64.deb
   ```
3. Abrir Kipu normalmente (las migraciones de BD se aplican solas)

---

## Desinstalar

```bash
sudo apt remove kipu       # Desinstalar
sudo apt purge kipu        # Desinstalar + limpiar /opt/kipu
```

PostgreSQL y su base de datos **no** se tocan.  
Los archivos de configuracion (`~/.kipu/`) se conservan salvo con `purge`.

---

## Problemas comunes

| Problema | Solucion |
|----------|----------|
| `psql` no encontrado | `sudo apt install postgresql-client` |
| Servidor no inicia | `sudo systemctl status postgresql` — verificar que este corriendo |
| Puerto 8080 en uso | `sudo lsof -i :8080` → `sudo kill <PID>` |
| Cliente no conecta | Verificar IP, puerto 8080 y firewall del equipo servidor |
| "No se encontro el servidor" | Verificar que existe `/opt/kipu/servidor/bin/KipuServidor` |

---

## Licencia

Software source-available bajo Licencia de Uso de Software Kipu (basada en Elastic License 2.0).  
Consulte el archivo LICENSE para los terminos completos.

(c) 2026 Dilan Acuna / Kipu. Todos los derechos reservados.
