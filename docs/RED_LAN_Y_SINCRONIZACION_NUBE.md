# Red LAN Independiente y Sincronización con Base de Datos en Nube

## Resumen

Este documento describe la arquitectura de red para que Baryx opere **100% independiente de internet** en una red local (LAN), con una estrategia opcional de sincronización hacia una base de datos en la nube (MongoDB Atlas o similar) para respaldo y análisis centralizado multi-bar.

Incluye la funcionalidad de **Host Mode**, que permite que un cliente JavaFX arranque el servidor Spring Boot automáticamente como subproceso, eliminando la necesidad de iniciar el servidor manualmente cada jornada.

---

## 1. Arquitectura de Red Local (LAN)

### 1.1 Topología

```
                         ┌─────────────────────┐
                         │   Router / Switch    │
                         │   (Red Local LAN)    │
                         └────┬────┬────┬───────┘
                              │    │    │
                    ┌─────────┘    │    └─────────┐
                    │              │              │
             ┌──────┴──────┐ ┌────┴─────┐  ┌─────┴──────┐
             │  SERVIDOR   │ │ CLIENTE 1 │  │ CLIENTE N  │
             │ Spring Boot │ │ POS/Caja  │  │ Comandera  │
             │ + PostgreSQL│ │  JavaFX   │  │   JavaFX   │
             │ IP: Fija    │ │           │  │            │
             └─────────────┘ └──────────┘  └────────────┘
```

- **Un servidor por bar**: ejecuta Spring Boot + PostgreSQL.
- **Múltiples clientes**: terminales POS, comanderas, panel admin — todos en la misma LAN.
- **Sin gateway a internet**: el sistema funciona completo sin router con salida a internet.

### 1.2 Direccionamiento IP

#### Opción Recomendada: IP Estática en el Servidor

El servidor **debe** tener una IP fija dentro de la LAN. Los clientes pueden usar DHCP.

| Dispositivo | IP Sugerida | Rol |
|-------------|-------------|-----|
| Servidor | `192.168.1.100` | Spring Boot + PostgreSQL |
| Cliente POS 1 | DHCP o `192.168.1.201` | Caja |
| Cliente POS 2 | DHCP o `192.168.1.202` | Caja |
| Comandera 1 | DHCP o `192.168.1.211` | Mesero |
| Comandera N | DHCP | Mesero |

**Configuración del servidor (Linux)**:

```bash
# /etc/netplan/01-baryx.yaml (Ubuntu/Debian con netplan)
network:
  version: 2
  ethernets:
    eth0:
      addresses:
        - 192.168.1.100/24
      routes:
        - to: default
          via: 192.168.1.1  # Solo si hay gateway
      nameservers:
        addresses: []       # Sin DNS externo
```

**Configuración del servidor (Windows)**:

```
Panel de Control → Red → Adaptador → Propiedades → IPv4
  IP: 192.168.1.100
  Máscara: 255.255.255.0
  Gateway: (vacío o 192.168.1.1)
  DNS: (vacío)
```

#### Configuración del Cliente Baryx

El cliente se conecta al servidor usando `ConfiguracionCliente.setUrlServidor()`:

```java
// Al iniciar la aplicación, configurar la IP del servidor
ConfiguracionCliente.setUrlServidor("http://192.168.1.100:8080");
```

Esta URL se resuelve directamente por IP — **no depende de DNS**.

### 1.3 Independencia Total de Internet

#### ¿Por qué funciona sin internet?

| Componente | Dependencia de Internet | Justificación |
|------------|------------------------|---------------|
| Spring Boot | ❌ Ninguna | JAR empaquetado con todas las dependencias |
| PostgreSQL | ❌ Ninguna | Motor local, sin telemetría |
| JavaFX | ❌ Ninguna | Renderizado local, sin WebView ni CDN |
| JWT Auth | ❌ Ninguna | Generación y validación local con clave simétrica |
| HttpClient | ❌ Ninguna | Comunicación IP-a-IP dentro de la LAN |
| Flyway Migrations | ❌ Ninguna | Scripts SQL empaquetados en el JAR |

#### Requisitos para Operación Offline

1. **Sin NTP remoto**: Si se necesita hora exacta, usar un servidor NTP local o aceptar la hora del sistema operativo.
2. **Sin actualizaciones automáticas**: Desactivar Windows Update / `unattended-upgrades` en el servidor para evitar reinicios.
3. **Sin DNS**: Todas las conexiones usan IP directa. No configurar DNS en las interfaces de red.
4. **Sin proxy**: `HttpClient` no debe tener proxy configurado (es el valor por defecto).

### 1.4 Descubrimiento del Servidor (Opcional)

Para evitar configurar la IP manualmente en cada cliente, se pueden usar estas estrategias:

#### Opción A: mDNS / Avahi (Linux) / Bonjour (macOS/Windows)

El servidor se anuncia en la red con un nombre `.local`:

```bash
# Instalar en el servidor (Linux)
sudo apt install avahi-daemon

# El servidor se anuncia como baryx-server.local
# Los clientes se conectan a http://baryx-server.local:8080
```

#### Opción B: Broadcast UDP (Implementación propia)

El servidor emite un paquete UDP broadcast cada 5 segundos en el puerto 9999. Los clientes escuchan ese puerto para descubrir la IP:

```java
// Servidor - Beacon UDP
DatagramSocket socket = new DatagramSocket();
socket.setBroadcast(true);
byte[] datos = "BARYX_SERVER:8080".getBytes();
DatagramPacket paquete = new DatagramPacket(
    datos, datos.length,
    InetAddress.getByName("255.255.255.255"), 9999
);
// Enviar cada 5 segundos en un hilo separado
```

```java
// Cliente - Escuchar beacon
DatagramSocket socket = new DatagramSocket(9999);
byte[] buffer = new byte[256];
DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
socket.receive(paquete); // Bloqueante hasta recibir
String ipServidor = paquete.getAddress().getHostAddress();
ConfiguracionCliente.setUrlServidor("http://" + ipServidor + ":8080");
```

#### Opción C: Archivo de configuración local (Más simple)

```properties
# baryx-cliente.properties (junto al JAR del cliente)
servidor.ip=192.168.1.100
servidor.puerto=8080
```

**Recomendación**: Opción C para instalaciones simples; Opción B para mayor flexibilidad.

### 1.5 Resiliencia de Red

#### Reintentos Automáticos

Los servicios del cliente deben manejar fallos de conectividad temporales:

```java
// Patrón de reintento con backoff exponencial
public <T> CompletableFuture<T> ejecutarConReintentos(
        Supplier<CompletableFuture<T>> operacion,
        int maxReintentos) {
    
    return operacion.get().exceptionallyCompose(ex -> {
        if (maxReintentos <= 0 || !esErrorDeRed(ex)) {
            return CompletableFuture.failedFuture(ex);
        }
        
        long espera = (long) Math.pow(2, 3 - maxReintentos) * 1000; // 1s, 2s, 4s
        return CompletableFuture.supplyAsync(() -> null, 
                CompletableFuture.delayedExecutor(espera, TimeUnit.MILLISECONDS))
                .thenCompose(v -> ejecutarConReintentos(operacion, maxReintentos - 1));
    });
}

private boolean esErrorDeRed(Throwable ex) {
    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
    return causa instanceof java.net.ConnectException
        || causa instanceof java.net.SocketTimeoutException
        || causa instanceof java.io.IOException;
}
```

#### Timeouts Configurados

| Operación | Timeout | Justificación |
|-----------|---------|---------------|
| Conexión TCP | 10 segundos | Redes LAN lentas o equipos viejos |
| Lectura de respuesta | 30 segundos | Consultas pesadas (reportes) |
| Health check | 5 segundos | Verificación rápida de disponibilidad |

#### Indicador de Estado de Conexión

El cliente debe mostrar un indicador visual del estado del servidor:

```
🟢 Conectado          → Health check exitoso
🟡 Reconectando...    → Reintento en progreso (1-3s)
🔴 Sin conexión       → Servidor no responde después de 3 reintentos
```

### 1.6 Configuración del Firewall

#### Linux (servidor)

```bash
# Abrir puerto del servidor Spring Boot
sudo ufw allow 8080/tcp

# Abrir puerto PostgreSQL solo desde la LAN (si se necesita acceso directo)
sudo ufw allow from 192.168.1.0/24 to any port 5432

# Verificar
sudo ufw status
```

#### Windows (servidor)

```powershell
# Regla de entrada para Spring Boot
New-NetFirewallRule -DisplayName "Baryx Server" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow

# Regla para PostgreSQL (solo LAN)
New-NetFirewallRule -DisplayName "Baryx PostgreSQL" -Direction Inbound -Protocol TCP -LocalPort 5432 -RemoteAddress 192.168.1.0/24 -Action Allow
```

---

## 2. Host Mode — Servidor Embebido en el Cliente

### 2.1 ¿Qué es el Host Mode?

El Host Mode resuelve el problema operativo de tener que iniciar el servidor Spring Boot manualmente cada jornada. **Un cliente JavaFX (el "terminal principal") arranca el servidor automáticamente como subproceso** al abrirse. Los demás terminales se conectan a este host.

```
     TERMINAL PRINCIPAL (Host Mode)              OTROS TERMINALES
  ┌──────────────────────────────────┐    ┌────────────────────────┐
  │  JavaFX Client                   │    │  JavaFX Client         │
  │  ┌──────────────────────────┐    │    │                        │
  │  │  Spring Boot (subproceso)│    │    │  Se conecta a la IP    │
  │  │  + API REST :8080        │◄───┼────┤  del host              │
  │  └──────────────────────────┘    │    │                        │
  │                                  │    └────────────────────────┘
  │  PostgreSQL (servicio del SO)    │
  └──────────────────────────────────┘
```

### 2.2 Flujo de Operación

1. El personal enciende el PC → PostgreSQL arranca automáticamente (servicio del SO)
2. Hace doble clic en "Baryx" → La aplicación JavaFX se abre
3. Si tiene Host Mode activado:
   - Muestra splash screen: "Iniciando servidor..."
   - Lanza `java -jar baryx-servidor.jar` como subproceso
   - Espera hasta 60 segundos a que el health check responda
   - Transiciona automáticamente a la pantalla de login
4. Los demás terminales abren Baryx normalmente (sin Host Mode) → se conectan a la IP del host
5. Al cerrar la aplicación del host → el servidor se destruye automáticamente

**El personal solo ve: abrir la app y esperar unos segundos. No toca consolas ni servidores.**

### 2.3 Activación del Host Mode

El Host Mode se configura desde la pantalla de login con un checkbox:

```
  [✓] Terminal Principal (Host Mode)
  ✓ Este terminal arrancará el servidor automáticamente al abrir.
    Los demás terminales se conectarán a esta IP.
    Reinicie la aplicación para aplicar.
```

La configuración se guarda en `baryx-cliente.properties` junto al JAR:

```properties
# Generado automáticamente
host.mode=true
servidor.puerto=8080
servidor.jar.ruta=
```

### 2.4 Componentes Implementados

| Clase | Ubicación | Función |
|-------|-----------|---------|
| `ServidorEmbebido` | `cliente/utilidad/` | Gestiona el subproceso: inicio, health check, logs, shutdown |
| `ConfiguracionCliente` | `cliente/configuracion/` | Persiste la config de host mode en disco |
| `BaryxClienteApplication` | `cliente/` | Muestra splash y orquesta el arranque |
| `LoginPinController` | `cliente/controlador/` | Checkbox de activación en la UI |

### 2.5 ¿Por qué Subproceso y No In-Process?

| Aspecto | Subproceso (elegido) | In-Process (descartado) |
|---------|---------------------|------------------------|
| Classpath | Sin conflictos (JavaFX y Spring Boot separados) | Requiere fusionar dependencias |
| Memoria | Aislada (servidor tiene su propio heap) | Compartida (compite con la UI) |
| Debugging | Se puede depurar cada uno por separado | Más complejo |
| Actualización | Solo reemplazar el JAR del servidor | Recompilar todo |
| Riesgo | Si el servidor crashea, el cliente sigue vivo | Un crash mata todo |

### 2.6 Requisitos

- **PostgreSQL como servicio del SO**: DEBE seguir instalándose como servicio (systemd / Windows Service) para que arranque al encender el PC
- **JAR del servidor disponible**: en la misma carpeta que el cliente, o en `../baryx-servidor/target/`
- **Mismo JRE**: el servidor usa el mismo runtime Java que el cliente (JDK 21+)

### 2.7 Configuración Recomendada por Rol de Terminal

| Terminal | Host Mode | Notas |
|----------|-----------|-------|
| POS Principal (caja) | ✅ Activado | Este es el "cerebro" del bar |
| Comanderas (meseros) | ❌ Desactivado | Se conectan a la IP del POS principal |
| Panel Admin | Depende | Si está en el mismo PC que el POS, no hace falta |

---

## 3. Sincronización con Base de Datos en la Nube

### 3.1 Motivación

| Necesidad | Solución |
|-----------|----------|
| Respaldo ante fallo del servidor local | Réplica en la nube |
| Análisis centralizado de múltiples bares | Base de datos consolidada |
| Acceso remoto a reportes | Consultas a la BD en nube |
| Recuperación ante desastres | Restaurar desde la nube |

### 3.2 Arquitectura de Sincronización

```
          BAR 1 (LAN)                     BAR 2 (LAN)
  ┌──────────────────────┐        ┌──────────────────────┐
  │ PostgreSQL (Primaria)│        │ PostgreSQL (Primaria)│
  │   ↕ Operaciones CRUD │        │   ↕ Operaciones CRUD │
  │ Spring Boot Server   │        │ Spring Boot Server   │
  └──────┬───────────────┘        └──────┬───────────────┘
         │                               │
         │ Sync Batch (cuando hay inet)  │
         ▼                               ▼
  ┌──────────────────────────────────────────────────┐
  │              MongoDB Atlas (Nube)                 │
  │                                                   │
  │  Colección: ventas_bar1, ventas_bar2, ...        │
  │  Colección: inventario_bar1, inventario_bar2     │
  │  Colección: auditoria_global                     │
  │                                                   │
  │  → Solo LECTURA para reportes centralizados      │
  │  → NO es la fuente de verdad (PostgreSQL lo es)  │
  └──────────────────────────────────────────────────┘
```

**Principio fundamental**: PostgreSQL local es **siempre** la fuente de verdad. MongoDB Atlas es una **réplica de solo lectura** para respaldo y análisis.

### 3.3 ¿Por qué MongoDB Atlas?

| Criterio | MongoDB Atlas | Alternativas |
|----------|--------------|-------------|
| Free tier generoso | 512 MB gratis | Firebase: 1 GB pero con estructura rígida |
| Esquema flexible | Documentos JSON (ideal para datos de distintos bares) | PostgreSQL cloud: requiere esquema idéntico |
| API nativa Java | MongoDB Java Driver (`mongodb-driver-sync`) | — |
| Sin servidor propio | Cluster gestionado por Atlas | Supabase: opción viable también |
| Agregaciones | Pipeline de agregación potente para reportes | — |

**Alternativas válidas**:
- **Supabase** (PostgreSQL cloud): si se prefiere mantener el mismo motor.
- **Firebase Firestore**: si se quiere real-time sync nativo.
- **CouchDB**: si se quiere sincronización bidireccional nativa (CouchDB Replication Protocol).

### 3.4 Modelo de Datos en MongoDB

Los datos se sincronizan como **documentos JSON** que reflejan las entidades de PostgreSQL:

```json
// Colección: ventas
{
  "_id": "bar1_venta_12345",
  "barId": "bar1",
  "idVentaLocal": 12345,
  "fecha": "2026-01-07T23:15:00",
  "mesa": "Mesa 5",
  "mesero": "Carlos Pérez",
  "lineas": [
    {"producto": "Cerveza Artesanal", "cantidad": 2, "precio": 15000},
    {"producto": "Nachos", "cantidad": 1, "precio": 22000}
  ],
  "total": 52000,
  "metodoPago": "EFECTIVO",
  "sincronizadoEn": "2026-01-08T06:00:00Z"
}
```

```json
// Colección: inventario
{
  "_id": "bar1_producto_42",
  "barId": "bar1",
  "nombre": "Cerveza Artesanal",
  "categoria": "Bebidas",
  "precio": 15000,
  "stock": 48,
  "activo": true,
  "ultimaActualizacion": "2026-01-07T22:00:00"
}
```

### 3.5 Estrategia de Sincronización

#### Modo Operativo: Cola + Batch Periódico

```
                    SERVIDOR LOCAL
┌─────────────────────────────────────────────┐
│                                             │
│  Operación CRUD → PostgreSQL (atómica)      │
│       │                                     │
│       ▼                                     │
│  Tabla: sync_cola_pendiente                 │
│  ┌──────────────────────────────────────┐   │
│  │ id | tabla | operacion | datos_json  │   │
│  │ 1  | ventas| INSERT   | {...}        │   │
│  │ 2  | productos| UPDATE| {...}        │   │
│  └──────────────────────────────────────┘   │
│       │                                     │
│       ▼                                     │
│  SyncService (hilo background, cada 5 min)  │
│       │                                     │
│       ├─→ ¿Hay internet? (ping a Atlas)     │
│       │    NO → Seguir acumulando en cola    │
│       │    SÍ → Enviar batch a MongoDB      │
│       │          → Marcar como sincronizado  │
│       │          → Truncar registros viejos  │
│       │                                     │
└─────────────────────────────────────────────┘
```

#### Tabla de Cola de Sincronización (PostgreSQL)

```sql
CREATE TABLE sync_cola_pendiente (
    id BIGSERIAL PRIMARY KEY,
    tabla_origen VARCHAR(100) NOT NULL,
    operacion VARCHAR(10) NOT NULL,      -- INSERT, UPDATE, DELETE
    id_registro BIGINT NOT NULL,
    datos_json JSONB NOT NULL,           -- Snapshot del registro
    fecha_creacion TIMESTAMP DEFAULT NOW(),
    sincronizado BOOLEAN DEFAULT FALSE,
    fecha_sincronizacion TIMESTAMP,
    intentos_fallidos INTEGER DEFAULT 0
);

CREATE INDEX idx_sync_pendiente ON sync_cola_pendiente (sincronizado, fecha_creacion);
```

#### Triggers para Captura de Cambios (CDC)

```sql
-- Función genérica que inserta en la cola de sincronización
CREATE OR REPLACE FUNCTION fn_sync_capturar_cambio()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO sync_cola_pendiente (tabla_origen, operacion, id_registro, datos_json)
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        CASE TG_OP
            WHEN 'DELETE' THEN OLD.id_venta  -- o el PK correspondiente
            ELSE NEW.id_venta
        END,
        CASE TG_OP
            WHEN 'DELETE' THEN row_to_json(OLD)::jsonb
            ELSE row_to_json(NEW)::jsonb
        END
    );
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Trigger en la tabla de ventas
CREATE TRIGGER trg_sync_ventas
AFTER INSERT OR UPDATE OR DELETE ON ventas
FOR EACH ROW EXECUTE FUNCTION fn_sync_capturar_cambio();
```

### 3.6 Servicio de Sincronización (Spring Boot)

```java
/**
 * Servicio de sincronización con MongoDB Atlas.
 * 
 * Responsabilidades:
 * - Leer registros pendientes de la cola sync_cola_pendiente
 * - Verificar conectividad con MongoDB Atlas
 * - Enviar datos en lotes (batch) al cluster remoto
 * - Marcar registros como sincronizados en PostgreSQL
 * - Manejar fallos y reintentos con backoff exponencial
 * 
 * Ejecución:
 * - Se ejecuta como tarea programada (@Scheduled) cada 5 minutos
 * - Solo sincroniza si hay conexión a internet disponible
 * - Si no hay internet, los datos se acumulan en la cola local
 * - Al restaurarse la conexión, envía todo el backlog acumulado
 */
@Service
public class SincronizacionNubeServicio {

    private static final Logger logger = LoggerFactory.getLogger(SincronizacionNubeServicio.class);
    private static final int TAMANO_LOTE = 100;  // Registros por batch
    
    @Autowired
    private SyncColaRepositorio syncColaRepositorio;
    
    private MongoClient mongoClient;
    private MongoDatabase database;
    
    @Value("${sync.nube.habilitado:false}")
    private boolean sincronizacionHabilitada;
    
    @Value("${sync.nube.mongodb-uri:}")
    private String mongoUri;
    
    @Value("${sync.nube.bar-id:bar1}")
    private String barId;
    
    /**
     * Tarea programada que sincroniza datos cada 5 minutos.
     * Solo se ejecuta si la sincronización está habilitada.
     */
    @Scheduled(fixedDelay = 300000)  // 5 minutos
    public void sincronizarPendientes() {
        if (!sincronizacionHabilitada || mongoUri.isEmpty()) {
            return;  // Sincronización deshabilitada — operación 100% local
        }
        
        try {
            // Verificar conectividad antes de intentar
            if (!verificarConexionNube()) {
                logger.debug("Sin conexión a la nube — datos acumulados en cola local");
                return;
            }
            
            // Obtener registros pendientes en lotes
            List<SyncColaPendiente> pendientes = syncColaRepositorio
                    .findBySincronizadoFalseOrderByFechaCreacionAsc(
                        PageRequest.of(0, TAMANO_LOTE));
            
            if (pendientes.isEmpty()) return;
            
            logger.info("Sincronizando {} registros con la nube", pendientes.size());
            
            for (SyncColaPendiente registro : pendientes) {
                enviarAMongoDB(registro);
                registro.setSincronizado(true);
                registro.setFechaSincronizacion(LocalDateTime.now());
            }
            
            syncColaRepositorio.saveAll(pendientes);
            logger.info("Sincronización completada: {} registros enviados", pendientes.size());
            
        } catch (Exception e) {
            logger.error("Error en sincronización con la nube", e);
        }
    }
    
    private boolean verificarConexionNube() {
        try {
            if (mongoClient == null) {
                mongoClient = MongoClients.create(mongoUri);
                database = mongoClient.getDatabase("baryx_central");
            }
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void enviarAMongoDB(SyncColaPendiente registro) {
        MongoCollection<Document> coleccion = database
                .getCollection(registro.getTablaOrigen());
        
        Document documento = Document.parse(registro.getDatosJson().toString());
        documento.put("barId", barId);
        documento.put("sincronizadoEn", LocalDateTime.now().toString());
        
        String idCompuesto = barId + "_" + registro.getTablaOrigen() 
                + "_" + registro.getIdRegistro();
        documento.put("_id", idCompuesto);
        
        switch (registro.getOperacion()) {
            case "INSERT":
            case "UPDATE":
                coleccion.replaceOne(
                    Filters.eq("_id", idCompuesto),
                    documento,
                    new ReplaceOptions().upsert(true)
                );
                break;
            case "DELETE":
                coleccion.deleteOne(Filters.eq("_id", idCompuesto));
                break;
        }
    }
}
```

### 3.7 Configuración de MongoDB Atlas

#### application.yml (propiedades de sincronización)

```yaml
sync:
  nube:
    habilitado: false                    # Deshabilitado por defecto (100% LAN)
    mongodb-uri: "mongodb+srv://usuario:password@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority"
    bar-id: "bar1"                       # Identificador único del bar
```

#### Dependencia Maven (solo servidor)

```xml
<!-- Solo agregar cuando se habilite la sincronización -->
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongodb-driver-sync</artifactId>
    <version>5.1.0</version>
</dependency>
```

### 3.8 Flujo de Recuperación ante Desastres

```
Escenario: El servidor local muere (disco corrupto, fallo hardware)

1. Instalar nuevo servidor con PostgreSQL limpio
2. Ejecutar migrations de Flyway (esquema base)
3. Ejecutar script de restauración desde MongoDB:

   mongosh "mongodb+srv://..." --eval '
     db.ventas.find({barId: "bar1"}).forEach(doc => {
       // Generar INSERTs de PostgreSQL
       print("INSERT INTO ventas VALUES (" + ... + ");");
     });
   ' > restauracion_bar1.sql

4. Importar en PostgreSQL:
   psql baryx_db < restauracion_bar1.sql

5. Reiniciar Spring Boot — el sistema está operativo
```

---

## 4. Seguridad

### 4.1 Red LAN

| Medida | Implementación |
|--------|---------------|
| Aislamiento de red | VLAN dedicada para Baryx (si el switch lo soporta) |
| Sin acceso a internet | No configurar gateway en el servidor |
| Firewall local | Solo puertos 8080 (API) y 5432 (PostgreSQL) abiertos |
| JWT con expiración | Tokens de 24 horas, refresh tokens de 7 días |
| Transmisión en la LAN | HTTP suficiente en LAN aislada (HTTPS opcional) |

### 4.2 Conexión a MongoDB Atlas

| Medida | Implementación |
|--------|---------------|
| TLS/SSL | MongoDB Atlas fuerza conexiones TLS por defecto |
| Credenciales | URI con usuario/password específico para cada bar |
| IP Whitelist | Configurar en Atlas solo la IP pública del bar |
| Roles mínimos | Usuario Atlas con permisos solo de `readWrite` en la base `baryx_central` |
| Datos sensibles | No sincronizar contraseñas ni tokens JWT a la nube |

### 4.3 Variables de Entorno Sensibles

```bash
# Nunca hardcodear en application.yml
export MONGODB_URI="mongodb+srv://baryx_sync:PASSWORD@cluster0.xxxxx.mongodb.net/baryx_central"
export BAR_ID="bar_nombre_unico"
```

---

## 5. Resumen de Decisiones Técnicas

| Decisión | Elección | Justificación |
|----------|----------|---------------|
| Fuente de verdad | PostgreSQL local | Operación offline garantizada |
| Arranque del servidor | Host Mode (subproceso) | El personal solo abre la app, sin consolas |
| Motor de nube | MongoDB Atlas | Free tier, esquema flexible, multi-bar natural |
| Patrón de sync | Cola + Batch | Tolerante a desconexión, no bloquea operaciones |
| Captura de cambios | Triggers PostgreSQL | Transparente para la aplicación, captura todo |
| Frecuencia de sync | Cada 5 minutos | Balance entre frescura y carga de red |
| Dirección de sync | Unidireccional (local → nube) | La nube es solo respaldo, no modifica datos locales |
| Descubrimiento de red | IP estática (simple) / UDP broadcast (auto) | Sin depender de DNS ni servicios externos |
| Protocolo LAN | HTTP (no HTTPS) | LAN aislada, sin riesgo de intercepción externa |
| Reintentos de red | Backoff exponencial (3 intentos) | Redes LAN inestables en hardware viejo |

---

## 6. Checklist de Despliegue

### Terminal Principal (Host Mode)

- [ ] Instalar PostgreSQL como servicio del SO (arranca al encender el PC)
- [ ] Crear base de datos `baryx_db` y ejecutar migrations
- [ ] Copiar `baryx-cliente.jar` y `baryx-servidor-1.0.0.jar` en la misma carpeta
- [ ] Abrir la app y activar "Terminal Principal (Host Mode)" en el checkbox del login
- [ ] Reiniciar la app → debe mostrar splash de arranque del servidor
- [ ] Abrir puerto 8080 en el firewall
- [ ] Verificar que los demás terminales se conectan usando la IP de esta máquina

### Instalación del Servidor (Alternativa sin Host Mode)

- [ ] Asignar IP estática al servidor (`192.168.1.100`)
- [ ] Instalar PostgreSQL y crear base de datos `baryx_db`
- [ ] Abrir puerto 8080 en el firewall
- [ ] Copiar JAR del servidor y ejecutar con `java -jar baryx-servidor.jar`
- [ ] Verificar que el health check responde: `curl http://192.168.1.100:8080/api/usuarios/health`

### Instalación de Clientes

- [ ] Copiar JAR del cliente a cada terminal
- [ ] Configurar `servidor.ip=<IP_del_host>` en archivo de propiedades (o en la UI de login)
- [ ] Verificar conectividad: la pantalla de login debe mostrar indicador verde
- [ ] Probar login con usuario admin

### Sincronización con Nube (Opcional)

- [ ] Crear cuenta en MongoDB Atlas (plan gratuito M0)
- [ ] Crear cluster, base de datos `baryx_central`, usuario de sincronización
- [ ] Configurar IP whitelist con la IP pública del local
- [ ] Definir variable de entorno `MONGODB_URI`
- [ ] Activar `sync.nube.habilitado=true` en `application.yml`
- [ ] Verificar en Atlas que los documentos aparecen después de 5 minutos

---

*Documento técnico del sistema Baryx v1.0.0 — Red LAN y Sincronización con Nube.*
