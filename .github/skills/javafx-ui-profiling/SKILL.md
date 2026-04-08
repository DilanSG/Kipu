---
name: javafx-ui-profiling
description: "Perfilado de rendimiento de UI JavaFX en Kipu. Use when: interfaz lenta, frame drops, UI se congela, scene graph pesado, CSS lento, FXML tarda en cargar, scroll laggy, animaciones entrecortadas, nodos excesivos, Platform.runLater bloqueante, productos grid lento, vista de mesas lenta."
---

# Perfilado de UI JavaFX — Kipu

Skill para diagnosticar problemas de rendimiento en la interfaz JavaFX de Kipu, optimizada para hardware gama baja.

## Cuándo Usar

- La UI se siente lenta o no responde
- Transiciones entre vistas tardan más de 500ms
- Scroll en listas de productos/pedidos es laggy
- Animaciones entrecortadas (<60 FPS)
- La vista se congela momentáneamente al cargar datos
- Después de horas de uso la UI se degrada

## Targets de Rendimiento (Kipu)

| Métrica | Target | Crítico |
|---------|--------|---------|
| Frame time | <16ms (60 FPS) | >33ms (30 FPS) |
| Arranque cliente | <3s | >5s |
| Transición entre vistas | <500ms | >1s |
| Carga de grid productos | <200ms | >500ms |
| Nodos visibles simultáneos | <1000 | >2000 |
| Profundidad scene graph | <8 niveles | >12 niveles |
| Animaciones | 200-300ms | >500ms |

## Procedimiento

### 1. Diagnóstico del Scene Graph

#### 1.1 Conteo de Nodos por Vista

Estimar nodos en las vistas más pesadas:

```bash
# Nodos estáticos en FXML
grep -c "<" kipu-cliente/src/main/resources/vista/*.fxml
grep -c "<" kipu-cliente/src/main/resources/vista/subvistas/**/*.fxml

# Nodos creados dinámicamente (los más peligrosos)
grep -rn "new Button\|new Label\|new VBox\|new HBox\|new StackPane\|new GridPane\|new ImageView" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Puntos de inyección dinámica
grep -rn "getChildren().add\|getChildren().addAll\|getChildren().setAll" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

**Cálculo rápido para grids de productos:**
- Si hay 200 productos y cada tarjeta tiene ~8 nodos (VBox + ImageView + Label + Button + ...)
- Total: 200 × 8 = 1600 nodos → **EXCEDE el límite de 1000**
- Fix: VirtualFlow, paginación, o lazy rendering por categoría

#### 1.2 Profundidad de Nesting FXML

```bash
# Archivos FXML más profundos (anidamiento XML)
for f in $(find kipu-cliente/src/main/resources/vista -name "*.fxml"); do
  depth=$(awk 'BEGIN{max=0; d=0} /<[^\/!?]/{d++; if(d>max)max=d} /<\//{d--} END{print max}' "$f")
  echo "$depth $f"
done | sort -rn | head -10
```

**Vistas críticas a revisar:**
- `mesa-detalle.fxml` — La más compleja (POS con grid + carrito + pago)
- `productos.fxml` — Grid de productos con búsqueda
- `menu-principal.fxml` — Dashboard con múltiples paneles

### 2. Análisis de CSS

#### 2.1 Selectores Complejos

Regla Kipu: máximo 3 niveles de selectores CSS.

```bash
# Selectores con más de 3 niveles (costosos de resolver)
grep -n "^\." kipu-cliente/src/main/resources/css/estilos.css | \
  awk -F'[ {]' '{print NF-1, $0}' | sort -rn | head -20

# Selectores con * (wildcard — evitar)
grep -n "\*" kipu-cliente/src/main/resources/css/estilos.css

# Box-shadows (máx 2 por elemento)
grep -n "box-shadow\|-fx-effect" kipu-cliente/src/main/resources/css/estilos.css

# Filters prohibidos en Kipu
grep -n "blur\|saturate\|-fx-blend-mode" kipu-cliente/src/main/resources/css/estilos.css
```

#### 2.2 Pseudoclases y Estados Dinámicos

```bash
# Cantidad de pseudoclases (cada una requiere recálculo de estilos)
grep -c ":hover\|:pressed\|:focused\|:selected\|:disabled" \
  kipu-cliente/src/main/resources/css/estilos.css
```

**Regla**: Las pseudoclases son OK, pero si un nodo tiene >3 estados CSS, JavaFX recalcula estilos frecuentemente.

### 3. Operaciones en UI Thread

#### 3.1 Trabajo Pesado en Platform.runLater

```bash
# Todos los Platform.runLater con contexto
grep -rn "Platform.runLater" kipu-cliente/src/main/java/com/kipu/cliente/controlador/ -A 8

# Buscar operaciones costosas dentro de runLater:
# - Creación masiva de nodos
# - Parsing/transformación de datos
# - Operaciones sobre colecciones grandes
```

**Patrón problemático:**
```java
// ❌ BLOQUEANTE: Procesar 200 productos Y crear nodos en el mismo runLater
Platform.runLater(() -> {
    List<ProductoDto> filtrados = productos.stream()
        .filter(p -> p.getCategoria().equals(cat))  // Procesamiento
        .sorted(Comparator.comparing(ProductoDto::getNombre))
        .toList();
    for (ProductoDto p : filtrados) {
        gridProductos.getChildren().add(crearTarjeta(p));  // Creación de nodos
    }
});

// ✅ FIX: Procesar FUERA del UI thread, solo renderizar dentro
CompletableFuture.supplyAsync(() -> {
    return productos.stream()
        .filter(p -> p.getCategoria().equals(cat))
        .sorted(Comparator.comparing(ProductoDto::getNombre))
        .toList();
}).thenAccept(filtrados -> {
    Platform.runLater(() -> {
        gridProductos.getChildren().setAll(  // Batch update
            filtrados.stream().map(this::crearTarjeta).toList()
        );
    });
});
```

#### 3.2 Detectar Bloqueos del UI Thread

```bash
# .get() bloqueante en CompletableFuture (NUNCA en controladores JavaFX)
grep -rn "\.get()" kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Thread.sleep en controladores (NUNCA)
grep -rn "Thread.sleep" kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Operaciones de archivo síncronas
grep -rn "Files.read\|Files.write\|new File" kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

### 4. Análisis de Imágenes y Recursos

```bash
# Imágenes cargadas (cada Image ocupa memoria de GPU)
grep -rn "new Image\|ImageView\|@../imagenes/" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ \
  kipu-cliente/src/main/resources/vista/

# Tamaño de imágenes en disco
find kipu-cliente/src/main/resources/imagenes -type f -exec ls -lh {} \; | sort -k5 -rh

# ¿Se usa backgroundLoading?
grep -rn "backgroundLoading\|new Image(" kipu-cliente/src/main/java/
```

**Reglas para imágenes en hardware bajo:**
- Logo: máx 300px ancho, PNG optimizado
- Iconos: SVG o PNG <32px
- Productos: Si hay thumbnails, máx 80x80px, cargar lazy

### 5. Análisis de Bindings y Properties

```bash
# Bindings (cadenas de binding pueden causar cascadas de recálculo)
grep -rn "\.bind(\|\.bindBidirectional(\|Bindings\.\|\.addListener(" \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/

# Properties que se actualizan frecuentemente
grep -rn "\.set(\|\.setValue(" kipu-cliente/src/main/java/com/kipu/cliente/controlador/
```

**Problema**: Una cadena de bindings A→B→C→D donde A cambia frecuentemente causa cascada de invalidación.

### 6. Perfilado con Herramientas JDK

```bash
# Habilitar Pulse Logger (mide frame times de JavaFX)
java -Djavafx.pulseLogger=true -jar kipu-cliente.jar 2>&1 | grep "PULSE"

# Habilitar CSS performance logging
java -Djavafx.css.debug=true -jar kipu-cliente.jar

# Flight Recorder enfocado en JavaFX
jcmd $(pgrep -f kipu-cliente) JFR.start \
  duration=30s \
  filename=javafx-profile.jfr \
  settings=profile
```

## Anti-Patrones UI Conocidos en Kipu

| Anti-Patrón | Dónde | Fix |
|-------------|-------|-----|
| Grid sin virtualización | Controladores de productos/mesas | VirtualFlow o paginación |
| Nodos creados sin pool | `crearTarjeta*()` en loops | Reciclar nodos o usar `.setAll()` |
| Platform.runLater granular | 40+ instancias individuales | Agrupar updates en un solo runLater |
| Listeners sin cleanup | `.addListener()` distribuidos | WeakListeners o cleanup explícito |
| CSS > 3 niveles | Selectores complejos en estilos.css | Aplanar con clases específicas |
| Animaciones > 300ms | Regla del proyecto | Reducir duración, solo transform/opacity |

## Output Esperado

```
PERFIL UI JAVAFX — [Vista/Componente]
══════════════════════════════════════

SCENE GRAPH
  Nodos estáticos (FXML): ~X
  Nodos dinámicos estimados: ~X (X items × X nodos/item)
  Profundidad máxima: X niveles
  ¿Excede límite 1000?: Sí/No

CSS
  Selectores totales: X
  Selectores > 3 niveles: X
  Efectos prohibidos: X
  Box-shadows: X

UI THREAD
  Platform.runLater: X instancias
  Operaciones bloqueantes detectadas: X
  Trabajo pesado en UI thread: [descripción]

PROBLEMAS DETECTADOS
  1. [Descripción] — [Impacto en FPS/latencia]

OPTIMIZACIONES PROPUESTAS
  1. [Cambio] — [Mejora estimada]
```
