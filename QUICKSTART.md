# Kipu - Guía de Inicio Rápido

Esta guía te permite instalar y ejecutar Kipu en tu máquina desde cero, en Windows o Linux/Mac. Sigue los pasos en orden. Se estima 15-20 minutos siguiendo esta guía.

## Requisitos previos

Verifica que tienes instaladas las herramientas necesarias. Abre una terminal/consola y ejecuta los comandos de verificación:

| Herramienta |      Comando    | Versión mínima |                                Descargar                               |
|-------------|-----------------|----------------|------------------------------------------------------------------------|
| Java JDK    | `java -version` |      21        | [oracle.com/java](https://www.oracle.com/java/technologies/downloads/) |
| Maven       | `mvn -version`  |      3.6       | [maven.apache.org](https://maven.apache.org/download.cgi)              |
| PostgreSQL  | `psql --version`|      12        | [postgresql.org](https://www.postgresql.org/download/)                 |

Para Windows, asegúrate de agregar las herramientas al PATH del sistema.

## Paso 1: Configurar la base de datos

### Crear la base de datos desde cero

Si es la primera vez que configuras todo, se ejecuta el script de inicialización. Este script crea el usuario de conexión y la base de datos.

En la terminal, navega a la carpeta de Kipu:

**Windows (PowerShell):**
```powershell
cd C:\Users\<u>\Desktop\Kipu
psql -U postgres -f database\setup-database.sql
```

**Linux/Mac (Terminal):**
```bash
cd ~/Desktop/Kipu
cp database/setup-database.sql /tmp/
sudo -u postgres psql -f /tmp/setup-database.sql
rm /tmp/setup-database.sql
```

El sistema te pedirá la contraseña del usuario `postgres` (el superusuario de PostgreSQL).

Si todavía no tienes el servidor PostgreSQL listo, haz esto antes de ejecutar `setup-database.sql`:

**Windows (PowerShell como Administrador):**
```powershell
# Ver servicios PostgreSQL instalados
Get-Service PostgreSQL*

# Iniciar el servicio (ajusta el nombre según tu versión)
Start-Service postgresql-x64-16

# Verificar acceso con el superusuario postgres
psql -U postgres -d postgres -c "\du"
```

**Linux (systemd):**
```bash
# Iniciar servidor
sudo systemctl start postgresql

# Verificar roles existentes
sudo -u postgres psql -d postgres -c "\du"

# Si no existe el rol postgres, crearlo como superusuario
sudo -u postgres createuser --superuser --login postgres
```

**macOS (Homebrew):**
```bash
# Iniciar servidor
brew services start postgresql

# Verificar roles
psql -d postgres -c "\du"

# Si no existe el rol postgres, créalo (ejecuta con un usuario con permisos de superusuario)
createuser --superuser --login postgres
```

Si creaste el rol `postgres` manualmente, asígnale contraseña:

```sql
ALTER USER postgres WITH PASSWORD 'cambia_esta_clave';
```

El script realiza lo siguiente:
- Crea un usuario llamado `kipu_admin` con contraseña inicial `kipu2026`
- Crea una base de datos llamada `kipu_db`, propiedad de `kipu_admin`
- Configura los permisos necesarios para que la aplicación funcione

Importante: si cambias la contraseña de `kipu_admin`, recuerda actualizar también `DB_PASSWORD` en tu archivo `.env`.

### Si necesitas eliminar todo y empezar de cero

```bash
# Windows (PowerShell)
psql -U postgres -f database\limpiar-todo.sql
psql -U postgres -f database\setup-database.sql

# Linux/Mac
sudo -u postgres psql -f database/limpiar-todo.sql
sudo -u postgres psql -f database/setup-database.sql
```

### Verificar que PostgreSQL funciona

**Windows:**
```powershell
Get-Service PostgreSQL*
```

Deberías ver PostgreSQL listado. Si el estado no es "Running", busca "Servicios" en el menú de inicio y inicia PostgreSQL.

**Linux/Mac:**
```bash
sudo systemctl status postgresql
```

Si no está activo, ejecuta:
```bash
sudo systemctl start postgresql
```

---

## Paso 2: Configurar variables de entorno

La aplicación necesita conocer dónde está la base de datos y otras configuraciones. Estas se definen en un archivo `.env`.

### Crear el archivo .env

**Windows (PowerShell):**
```powershell
cd C:\Users\<tu_usuario>\Desktop\Kipu
Copy-Item ".env.example" ".env"
```

**Linux/Mac (Terminal):**
```bash
cd ~/Desktop/Kipu
cp .env.example .env
```

### Editar el archivo .env

Abre el archivo `.env` con cualquier editor de texto (Bloc de notas, VS Code, etc.). Asegúrate de que contiene:

```properties
# Base de datos
DB_HOST=127.0.0.1
DB_PORT=5432
DB_NAME=kipu_db
DB_USER=kipu_admin
DB_PASSWORD=kipu2026

# JWT - Clave secreta para tokens (mínimo 32 caracteres)
JWT_SECRET=KipuSecretKey2026WithAtLeast32CharactersForJWTTokenGeneration

# Servidor
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

Si cambiaste la contraseña de `kipu_admin`, actualiza el valor de `DB_PASSWORD`.

Para generar una clave JWT más segura:

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String((1..44 | ForEach-Object { [byte](Get-Random -Maximum 256) }))
```

**Linux/Mac:**
```bash
openssl rand -base64 44
```

---

## Paso 3: Compilar la aplicación

La compilación prepara el código fuente para ser ejecutado. Maven descargará todas las dependencias necesarias automáticamente (toma más tiempo la primera vez).

### Usar los scripts de compilación (recomendado)

**Windows (PowerShell):**
```powershell
cd C:\Users\<tu_usuario>\Desktop\Kipu
.\scripts\build.ps1
```

**Linux/Mac:**
```bash
cd ~/Desktop/Kipu
./scripts/build.sh
```

Espera a que termine. La primera compilación puede tomar 3-5 minutos. Las compilaciones posteriores serán más rápidas.

### Alternativa: Compilar directamente con Maven

Si prefieres omitir los scripts, ejecuta en cualquier plataforma:

```bash
mvn clean package -DskipTests
```

Esto limpia cualquier compilación anterior, descarga dependencias y compila el proyecto completo sin ejecutar tests (que tardarían más).

---

## Paso 4: Iniciar el sistema

Para ejecutar Kipu, necesitas abrir dos pantallas de terminal: una para el servidor y otra para el cliente.

### Opción A: Usar los scripts (recomendado)

Abre dos terminales/PowerShells en la carpeta de Kipu.

**Terminal 1 - Iniciar el servidor:**

**Windows (PowerShell):**
```powershell
cd C:\Users\<tu_usuario>\Desktop\Kipu
.\scripts\start-servidor.ps1
```

**Linux/Mac (Terminal):**
```bash
cd ~/Desktop/Kipu
./scripts/start-servidor.sh
```

Espera a ver un mensaje de confirmación como "Tomcat started on port 8080" o similar.

**Terminal 2 - Iniciar el cliente** (espera 10 segundos después de que el servidor esté listo):

**Windows (PowerShell):**
```powershell
cd C:\Users\<tu_usuario>\Desktop\Kipu
.\scripts\start-cliente.ps1
```

**Linux/Mac (Terminal):**
```bash
cd ~/Desktop/Kipu
./scripts/start-cliente.sh
```

Debería abrirse una ventana gráfica con la interfaz de Kipu automáticamente.

### Opción B: Compilar y ejecutar con Maven (para desarrollo)

Si prefieres más control o quieres hacer cambios en el código con actualizaciones automáticas:

**Terminal 1 - Servidor:**
```bash
cd <ruta_a_kipu>/kipu-servidor
mvn spring-boot:run
```

**Terminal 2 - Cliente:**
```bash
cd <ruta_a_kipu>/kipu-cliente
mvn javafx:run
```

---

## Paso 5: Acceder a la aplicación

Cuando se abra la ventana del cliente, inicia sesión con las credenciales de prueba:

| Usuario/Código | Contraseña/PIN | Rol    | Tipo de login |
|----------------|---|--------|--------|
| `01` | `1234` | CAJERO | Login PIN (solo números) |
| `02` | `1234` | MESERO | Login PIN (solo números) |
| `admin` | `admin123` | ADMIN | Login avanzado (usuario/contraseña) |

Importante: Cambia estas contraseñas antes de usar el sistema en un entorno de producción real.

---

## Paso 6: Generar ejecutables independientes (opcional)

Puedes empaquetar la aplicación en ejecutables que no requieren Java instalado en la máquina destino. Esto es útil para distribuir en otras computadoras del bar.

**Windows (PowerShell):**
```powershell
cd C:\Users\<tu_usuario>\Desktop\Kipu
.\scripts\package.ps1
```

**Linux/Mac:**
```bash
cd ~/Desktop/Kipu
./scripts/package.sh
```

El proceso toma 5-10 minutos. Los ejecutables se crearán en la carpeta `dist/`.

Para distribuir en otras máquinas, copia las carpetas de `dist/portable/` a las computadoras donde necesites ejecutar Kipu.

---

## Estructura del proyecto

Para entender cómo está organizado Kipu:

```
Kipu/
├── .env                         <- Configuración local (no incluir en versionado)
├── pom.xml                      <- Configuración de Maven para compilación
├── kipu-common/                <- Código compartido (modelos de datos)
├── kipu-servidor/              <- API REST del servidor (Spring Boot)
│   ├── src/
│   ├── logs/
│   └── pom.xml
├── kipu-cliente/               <- Interfaz gráfica (JavaFX)
│   ├── src/
│   ├── logs/
│   └── pom.xml
├── database/
│   ├── setup-database.sql       <- Crear BD y usuario desde cero
│   ├── limpiar-todo.sql         <- Eliminar BD y usuario completamente
│   └── reset-database.sql       <- Borrar datos sin eliminar estructura
├── scripts/
│   ├── build.sh / build.ps1     <- Compilar el proyecto
│   ├── start-servidor.sh / .ps1 <- Iniciar servidor
│   ├── start-cliente.sh / .ps1  <- Iniciar cliente
│   └── package.sh / .ps1        <- Generar ejecutables empaquetados
├── packaging/                   <- Recursos para empaquetado
├── dist/                        <- Ejecutables generados (después de empaquetar)
└── docs/                        <- Documentación técnica adicional
```

---

## Solución de problemas

### Problema: "El comando no se reconoce" (psql, mvn, java)

Esto significa que las herramientas están instaladas pero no están en el PATH del sistema (las carpetas de búsqueda de ejecutables).

**Solución para Windows:**
- Desinstala Java y Maven
- Descarga las versiones más recientes desde [oracle.com/java](https://www.oracle.com/java/technologies/downloads/) (Java) y [maven.apache.org](https://maven.apache.org/download.cgi) (Maven)
- Durante la instalación, marca la opción "Agregar al PATH" o "Add to PATH"
- Reinicia PowerShell y verifica que funcionen los comandos

**Solución para Linux/Mac:**
```bash
# En Debian/Ubuntu
sudo apt-get update
sudo apt-get install default-jdk maven postgresql

# En Mac (con Homebrew)
brew install openjdk maven postgresql
```

---

### Problema: PostgreSQL no está corriendo

**Windows:**
- Presiona `Win + R` y escribe `services.msc` luego Enter
- Busca "PostgreSQL" en la lista
- Si el estado no es "Running", haz clic derecho y selecciona "Iniciar"

**Linux/Mac:**
```bash
# Ver estado
sudo systemctl status postgresql

# Iniciar si no está corriendo
sudo systemctl start postgresql

# Para que inicie automáticamente
sudo systemctl enable postgresql
```

---

### Problema: Error de permiso al crear la base de datos

**Windows (PowerShell como Administrador):**
```powershell
psql -U postgres -f database\limpiar-todo.sql
psql -U postgres -f database\setup-database.sql
```

**Linux/Mac:**
```bash
sudo -u postgres psql -f database/limpiar-todo.sql
sudo -u postgres psql -f database/setup-database.sql
```

---

### Problema: El servidor no inicia o dice que el puerto está en uso

Otro programa está usando el puerto 8080. Tienes dos opciones:

Opción 1: Detener el otro programa

Opción 2: Cambiar el puerto

Edita el archivo `.env` y cambia:
```properties
SERVER_PORT=9090
```

Luego reinicia el servidor. Si editaste el puerto, debes actualizar la URL del servidor en los clientes: `SERVER_URL=http://localhost:9090`

**Para encontrar qué usa el puerto (Windows):**
```powershell
Get-NetTCPConnection -LocalPort 8080 | Format-Table
```

**Para encontrar qué usa el puerto (Linux/Mac):**
```bash
lsof -i :8080
```

---

### Problema: El cliente no se conecta al servidor

1. Verifica que el servidor está corriendo: debería ver logs en la Terminal 1

2. Prueba conectar a la URL del servidor:

**Windows (PowerShell):**
```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
```

**Linux/Mac:**
```bash
curl http://localhost:8080/actuator/health
```

Deberías recibir una respuesta JSON con estado `UP`.

3. Si el servidor está en otra máquina de la red, edita `.env`:
```properties
SERVER_URL=http://IP_DEL_SERVIDOR:8080
```

Ejemplo: `SERVER_URL=http://192.168.1.10:8080`

---

### Problema: Compilación falla

```bash
# Limpia compilaciones anteriores e intenta de nuevo
mvn clean package -DskipTests
```

Si el problema persiste, revisa los logs en:
- Servidor: `kipu-servidor/logs/kipu-servidor.log`
- Cliente: `kipu-cliente/logs/kipu-cliente.log`

---

### Problema: La interfaz gráfica se ve lenta

Verifica que:
1. Tu máquina cumple con los requisitos mínimos (RAM suficiente)
2. No haya otros programas consumiendo recursos
3. Los logs no muestren errores críticos

---

## Configuraciones avanzadas (opcional)

### Ejecutar el cliente en otra máquina de la red

Si habéis instalado Kipu en una máquina principal y querés ejecutar clientes en otras máquinas:

1. Ejecuta el servidor en la máquina principal (ej. IP `192.168.1.100`)
2. En las otras máquinas, edita `.env` o `kipu-cliente.properties`:
   ```properties
   SERVER_URL=http://192.168.1.100:8080
   ```
3. Inicia los clientes siguiendo el Paso 4

---

### Cambiar contraseña de la base de datos

Si necesitás cambiar la contraseña de `kipu_admin`:

**Windows:**
```powershell
psql -U postgres -d postgres -c "ALTER USER kipu_admin WITH PASSWORD 'nueva_contrasena';"
```

**Linux/Mac:**
```bash
sudo -u postgres psql -d postgres -c "ALTER USER kipu_admin WITH PASSWORD 'nueva_contrasena';"
```

Luego actualiza el archivo `.env` con la nueva contraseña de `DB_PASSWORD`.

---

## Próximos pasos

Después de que Kipu funcione correctamente:

1. **Cambiar contraseñas:** Accede como admin y modifica las contraseñas de prueba
2. **Crear usuarios reales:** Agrega los usuarios que trabajarán en el sistema
3. **Definir productos:** Carga los productos o bebidas que vendés
4. **Configurar mesas:** Si usarás comandera, configura las mesas del bar
5. **Instalación en múltiples máquinas:** Distribuye clientes con la URL correcta del servidor

Para documentación técnica más detallada, revisar la carpeta `docs/`.

---

## Archivos importantes

- **Configuración:** `.env` (no incluir en versionado)
- **Configuración ejemplo:** `.env.example`
- **Logs del servidor:** `kipu-servidor/logs/kipu-servidor.log`
- **Logs del cliente:** `kipu-cliente/logs/kipu-cliente.log`
- **Scripts de base de datos:** `database/`
