---
name: memory-leak-detection
description: "Detección de fugas de memoria en JavaFX y Spring Boot. Use when: memoria crece con el tiempo, OutOfMemoryError, uso de memoria alto después de navegar entre vistas, listeners no limpiados, objetos retenidos, caché sin evicción, conexiones no cerradas, scene graph que crece."
---

# Detección de Memory Leaks — Kipu

Skill para detectar fugas de memoria en el cliente JavaFX y servidor Spring Boot de Kipu. Enfocado en los patrones reales del proyecto.

## Cuándo Usar

- La aplicación consume más memoria con el tiempo
- Después de navegar entre vistas repetidamente, la memoria no se libera
- OutOfMemoryError en cliente o servidor
- El servidor se degrada después de horas de operación continua (picos 20:00-03:00)

## Fuentes de Leaks en Kipu

### Categoría 1: Listeners y Callbacks JavaFX (ALTA probabilidad)

Los controladores de Kipu añaden listeners con `.addListener()` pero no siempre los remueven al cambiar de vista.

**Búsqueda:**

```bash
# Listeners añadidos sin cleanup
grep -rn "\.addListener\|\.setOnAction\|\.setOnMouseClicked\|\.setOnKeyPressed" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Verificar si hay cleanup explícito
grep -rn "\.removeListener\|\.removeEventHandler\|\.setOnAction(null)" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

**Patrón de leak:**
```java
// ❌ LEAK: Lambda captura referencia al controller, impide GC
someProperty.addListener((obs, old, nuevo) -> {
    this.actualizarVista(nuevo);  // 'this' retenido por el listener
});

// ✅ FIX: Guardar referencia para poder remover
private ChangeListener<String> miListener;

void initialize() {
    miListener = (obs, old, nuevo) -> actualizarVista(nuevo);
    someProperty.addListener(miListener);
}

void limpiar() {
    someProperty.removeListener(miListener);
}
```

**Archivos prioritarios a revisar:**
- `controlador/facturacion/MesaDetalleController.java` — toggle listeners, debounce transitions
- `controlador/MenuPrincipalController.java` — timeline, reloj
- `controlador/productos/ProductosController.java` — drag-and-drop listeners
- Cualquier controlador con `PauseTransition` o `Timeline`

### Categoría 2: Timelines y Animaciones No Detenidas

Kipu usa `Timeline` para el reloj en header/footer. Si no se detiene al cambiar vista, la referencia persiste.

**Búsqueda:**

```bash
# Timeline creados
grep -rn "new Timeline\|new PauseTransition\|new TranslateTransition" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Stops correspondientes
grep -rn "\.stop()\|\.pause()" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

**Checklist por controlador:**
- [ ] ¿Todo `Timeline` tiene un `.stop()` correspondiente en un método de limpieza?
- [ ] ¿El método de limpieza se invoca al salir de la vista?
- [ ] ¿`cerrarSesion()` detiene **todos** los timelines antes de navegar?

### Categoría 3: CompletableFuture Colgados

El cliente usa `CompletableFuture` extensivamente via `ServicioHttpBase`. Los futures que no completan retienen su cadena.

**Búsqueda:**

```bash
# Futures sin timeout explícito
grep -rn "CompletableFuture\|sendAsync\|thenAccept\|thenApply" \
  kipu-cliente/src/main/java/com/kipu/cliente/servicio/ -A 3

# Verificar si hay orTimeout/completeOnTimeout
grep -rn "orTimeout\|completeOnTimeout" \
  kipu-cliente/src/main/java/com/kipu/cliente/servicio/
```

**Patrón de leak:**
```java
// ❌ LEAK: Si el servidor no responde, el future nunca completa y retiene toda la cadena
httpClient.sendAsync(request, BodyHandlers.ofString())
    .thenAccept(response -> { /* referencia a datos grandes */ });

// ✅ FIX: Agregar timeout
httpClient.sendAsync(request, BodyHandlers.ofString())
    .orTimeout(30, TimeUnit.SECONDS)
    .thenAccept(response -> { /* ... */ })
    .exceptionally(ex -> { /* manejar timeout */ return null; });
```

### Categoría 4: Caché Sin Evicción

**Cachés conocidas en Kipu:**

| Caché | Archivo | Evicción |
|-------|---------|----------|
| `ipLocalCache` | `ConfiguracionCliente.java` | ❌ Nunca (estático) — OK, es inmutable |
| `atlasUriCache` | `ConfiguracionCliente.java` | ✅ Tiene `invalidarCache()` |
| License cache | `LicenciaServicio.java` | ✅ TTL 72h |

**Buscar cachés no documentadas:**

```bash
# Maps estáticos que podrían crecer sin límite
grep -rn "static.*Map\|static.*List\|static.*Set" \
  kipu-cliente/src/main/java/com/kipu/cliente/ \
  kipu-servidor/src/main/java/com/kipu/servidor/

# ConcurrentHashMap sin evicción
grep -rn "ConcurrentHashMap" \
  kipu-servidor/src/main/java/com/kipu/servidor/servicio/
```

**Patrón de leak server-side:**
```java
// ❌ LEAK: Map crece indefinidamente si los clientes nunca se desregistran
private final Map<String, ClienteInfo> clientesConectados = new ConcurrentHashMap<>();

// ✅ FIX: Evicción por TTL o tamaño máximo
// Verificar que RegistroClientesServicio.java limpie entradas stale
```

### Categoría 5: Scene Graph que Crece

Kipu crea nodos dinámicamente para grids de productos y mesas.

**Búsqueda:**

```bash
# Nodos añadidos dinámicamente sin limpiar los anteriores
grep -rn "getChildren().add\|getChildren().setAll\|getChildren().clear" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# ¿Se llama clear() antes de repoblar?
```

**Patrón de leak:**
```java
// ❌ LEAK: Añadir sin limpiar — los nodos anteriores quedan huérfanos pero referenciados
gridProductos.getChildren().add(crearTarjetaProducto(producto));

// ✅ FIX: Limpiar antes de repoblar
gridProductos.getChildren().clear();
for (Producto p : productos) {
    gridProductos.getChildren().add(crearTarjetaProducto(p));
}
```

### Categoría 6: Recursos No Cerrados (Servidor)

```bash
# InputStreams, Connections, etc. sin try-with-resources
grep -rn "new FileInputStream\|new BufferedReader\|getConnection()\|openStream()" \
  kipu-servidor/src/main/java/com/kipu/servidor/

# Respuestas HTTP no consumidas en cliente
grep -rn "HttpResponse" kipu-cliente/src/main/java/ | grep -v "BodyHandlers"
```

## Procedimiento de Diagnóstico

### Paso 1: Clasificar el Síntoma

| Síntoma | Causa Probable | Categoría |
|---------|---------------|-----------|
| Memoria crece al navegar entre vistas | Listeners/Timelines no limpiados | 1, 2 |
| Memoria crece con cada pedido | Scene graph / nodos dinámicos | 5 |
| Servidor se degrada con horas de uso | Caché sin evicción / conexiones leaking | 4, 6 |
| Pico de memoria en horarios específicos | findAll() masivo / backup sin paginación | Server-side queries |
| OutOfMemoryError después de muchas requests | CompletableFuture colgados | 3 |

### Paso 2: Buscar Evidencia (Estáticamente)

Ejecutar los comandos grep de la categoría identificada. Cruzar: ¿cuántos `.addListener()` hay vs cuántos `.removeListener()`?

### Paso 3: Revisar Flujo de Lifecycle

Para cada controlador sospechoso:
1. Leer `initialize()` — ¿qué recursos se crean?
2. Buscar método de limpieza (`limpiar()`, `cerrarSesion()`, listener en scene property)
3. ¿Hay un `stage.setOnCloseRequest` o listener de window closing?
4. ¿La navegación (`NavegacionUtil`) invoca limpieza antes de cargar la nueva vista?

### Paso 4: Trazar Retención

Para cada posible leak, trazar la cadena de retención:
```
GC Root → [qué referencia al objeto] → [qué referencia eso] → Objeto leaking
```

## Herramientas de Runtime (si aplica)

```bash
# Heap dump del proceso Java
jmap -dump:live,format=b,file=heap.hprof $(pgrep -f kipu)

# Resumen de memoria
jmap -histo:live $(pgrep -f kipu) | head -30

# Monitoreo en vivo
jstat -gc $(pgrep -f kipu) 5000

# Flight Recorder (JDK 21)
jcmd $(pgrep -f kipu) JFR.start duration=60s filename=kipu.jfr
```

## Output Esperado

```
ANÁLISIS DE MEMORY LEAKS — [Componente]
════════════════════════════════════════

LEAKS CONFIRMADOS
  1. [Categoría] — [Descripción]
     Archivo: [ruta:línea]
     Retención: [cadena GC root → objeto]
     Impacto: X objetos/hora, ~Y MB/hora estimado

LEAKS POTENCIALES (requieren runtime para confirmar)
  1. [Descripción]
     Evidencia estática: [qué se encontró en código]
     Condición: [cuándo se manifestaría]

PLAN DE CORRECCIÓN
  1. [Fix ordenado por impacto]
  2. ...
```
