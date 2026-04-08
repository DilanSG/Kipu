---
name: i18n-validation
description: "Validación de internacionalización (i18n) en Kipu cliente. Use when: verificar textos hardcodeados, comprobar claves i18n faltantes, sincronizar archivos de idioma ES/EN/PT, agregar claves nuevas, detectar strings sin IdiomaUtil, auditar traducciones, verificar consistencia entre mensajes.properties."
---

# Validación i18n — Kipu

Procedimiento para verificar que todo texto visible al usuario usa el sistema de internacionalización y que los 3 archivos de idioma están sincronizados.

## Cuándo Usar

- Al crear una vista o controlador nuevo
- Al modificar textos visibles en la UI
- Para auditar que no hay strings hardcodeados en español
- Después de agregar claves nuevas (verificar sincronización)
- Cuando un texto aparece en español independientemente del idioma seleccionado

## Archivos Clave

| Archivo | Propósito |
|---------|-----------|
| `kipu-cliente/src/main/resources/i18n/mensajes.properties` | Español (default, ~1320 líneas) |
| `kipu-cliente/src/main/resources/i18n/mensajes_en.properties` | English (~1317 líneas) |
| `kipu-cliente/src/main/resources/i18n/mensajes_pt.properties` | Português (~1317 líneas) |
| `kipu-cliente/src/main/java/com/kipu/cliente/utilidad/IdiomaUtil.java` | Clase utilitaria de i18n |

## Convención de Claves

```
{capa}.{modulo}.{elemento}       → ctrl.logs.titulo
{capa}.{modulo}.{accion}         → ctrl.logs.marcar_resuelto
{capa}.{modulo}.{sub}.{elemento} → ctrl.logs.filtro.todos
{capa}.{modulo}.campo.{nombre}   → ctrl.logs.campo.nivel
```

**Capas:**
| Prefijo | Capa | Ejemplo |
|---------|------|---------|
| `ctrl` | Controladores JavaFX | `ctrl.usuarios.titulo` |
| `srv` | Servicios cliente | `srv.productos.error_carga` |
| `val` | Validaciones | `val.campo.requerido` |
| `nav` | Navegación | `nav.menu.productos` |
| `exito` | Mensajes de éxito | `exito.guardado` |
| `error` | Mensajes de error | `error.conexion` |
| `usuario` | Módulo usuarios | `usuario.campo.nombre` |
| `producto` | Módulo productos | `producto.campo.precio` |
| `categoria` | Módulo categorías | `categoria.exito.creada` |
| `pedido` | Módulo pedidos | `pedido.estado.pendiente` |
| `mesa` | Módulo mesas | `mesa.titulo` |

**Regla**: NO inventar prefijos nuevos. Usar los existentes. Si un módulo nuevo lo requiere, seguir el patrón `modulo.sub.elemento`.

## Procedimiento

### 1. Detectar Strings Hardcodeados en Controladores

```bash
# Buscar setText() con strings literales (anti-patrón principal)
grep -rn '\.setText("[^"]*[a-záéíóúñA-ZÁÉÍÓÚÑ]' \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ \
  --include="*.java" | grep -v 'IdiomaUtil\|obtener\|//\|/\*\|System.out\|logger\|log\.'

# Buscar new Label("texto") con texto en español
grep -rn 'new Label("[^"]*[a-záéíóúñA-ZÁÉÍÓÚÑ]' \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ \
  --include="*.java" | grep -v 'IdiomaUtil\|obtener'

# Buscar setPromptText() con strings literales
grep -rn '\.setPromptText("[^"]*[a-záéíóúñA-ZÁÉÍÓÚÑ]' \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ \
  --include="*.java" | grep -v 'IdiomaUtil\|obtener'

# Buscar Alert/Dialog con texto literal
grep -rn 'setHeaderText\|setContentText\|setTitle' \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ \
  --include="*.java" | grep '"[^"]*[a-záéíóúñA-ZÁÉÍÓÚÑ]' | grep -v 'IdiomaUtil\|obtener'
```

### 2. Detectar Strings Hardcodeados en FXML

```bash
# Buscar text="..." con texto visible en español en FXML
# Excepciones válidas: "|" (separador), "" (vacío), "X" (cerrar)
grep -rn 'text="[^"]*[a-záéíóúñA-ZÁÉÍÓÚÑ]' \
  kipu-cliente/src/main/resources/vista/ \
  --include="*.fxml" | grep -v 'fx:id\|styleClass\|style=\|url=\|Sistema Kipu v'
```

**Strings que SÍ son aceptables hardcodeados en FXML:**
- `text="|"` — Separadores visuales
- `text="X"` — Botón cerrar (universal)
- `text="Sistema Kipu v1.0.0 © 2026"` — Copyright en footer (es constante)
- Strings que son `fx:id` o `styleClass` (no son texto visible)

**Strings que NO deben estar hardcodeados:**
- Títulos de sección
- Labels de formulario
- Texto de botones
- Mensajes de estado
- Descripciones, tooltips, placeholders

### 3. Verificar Uso Correcto de IdiomaUtil

```bash
# Todas las llamadas a IdiomaUtil.obtener() — extraer claves usadas
grep -rnoP 'IdiomaUtil\.obtener\("\K[^"]+' \
  kipu-cliente/src/main/java/com/kipu/cliente/controlador/ \
  --include="*.java" | sort -u > /tmp/claves_usadas.txt

# Verificar que cada clave usada existe en mensajes.properties
while IFS=: read -r file clave; do
  if ! grep -q "^$clave=" kipu-cliente/src/main/resources/i18n/mensajes.properties; then
    echo "❌ CLAVE INEXISTENTE: $clave (usado en $file)"
  fi
done < /tmp/claves_usadas.txt
```

### 4. Sincronización de los 3 Archivos de Idioma

```bash
# Extraer claves de cada archivo (sin valores, solo keys)
grep -oP '^[a-zA-Z0-9_.]+(?==)' kipu-cliente/src/main/resources/i18n/mensajes.properties | sort > /tmp/keys_es.txt
grep -oP '^[a-zA-Z0-9_.]+(?==)' kipu-cliente/src/main/resources/i18n/mensajes_en.properties | sort > /tmp/keys_en.txt
grep -oP '^[a-zA-Z0-9_.]+(?==)' kipu-cliente/src/main/resources/i18n/mensajes_pt.properties | sort > /tmp/keys_pt.txt

# Claves en ES pero NO en EN
echo "=== Faltantes en English ==="
comm -23 /tmp/keys_es.txt /tmp/keys_en.txt

# Claves en ES pero NO en PT
echo "=== Faltantes en Português ==="
comm -23 /tmp/keys_es.txt /tmp/keys_pt.txt

# Claves en EN pero NO en ES (huérfanas)
echo "=== Huérfanas en English (sobran) ==="
comm -13 /tmp/keys_es.txt /tmp/keys_en.txt

# Claves en PT pero NO en ES (huérfanas)
echo "=== Huérfanas en Português (sobran) ==="
comm -13 /tmp/keys_es.txt /tmp/keys_pt.txt

# Conteo rápido
echo "ES: $(wc -l < /tmp/keys_es.txt) | EN: $(wc -l < /tmp/keys_en.txt) | PT: $(wc -l < /tmp/keys_pt.txt)"
```

**Estado ideal**: Los 3 archivos tienen exactamente las mismas claves, en el mismo orden.

### 5. Detectar Claves Huérfanas (definidas pero nunca usadas)

```bash
# Todas las claves definidas en mensajes.properties
grep -oP '^[a-zA-Z0-9_.]+(?==)' kipu-cliente/src/main/resources/i18n/mensajes.properties | while read clave; do
  # Buscar uso en Java y FXML
  if ! grep -rq "$clave" kipu-cliente/src/main/java/ kipu-cliente/src/main/resources/vista/ 2>/dev/null; then
    echo "⚠️  HUÉRFANA: $clave"
  fi
done
```

**Nota**: Este análisis es costoso (~1320 claves × búsqueda recursiva). Usar solo cuando se necesite limpieza general. Para auditorías puntuales, verificar solo las claves del módulo relevante.

### 6. Agregar Claves Nuevas

Cuando se necesitan claves nuevas, seguir este proceso:

**Paso 1**: Determinar el prefijo correcto según la convención:
```
ctrl.{modulo}.{elemento}    → Para controladores
srv.{modulo}.{elemento}     → Para servicios
val.{modulo}.{elemento}     → Para validaciones
{modulo}.campo.{nombre}     → Para labels de campos
{modulo}.exito.{accion}     → Para mensajes de éxito
{modulo}.error.{accion}     → Para mensajes de error
```

**Paso 2**: Agregar a los 3 archivos **en la misma ubicación** (misma sección de comentarios):
```properties
# mensajes.properties
ctrl.reportes.titulo=Reportes del Sistema
ctrl.reportes.btn.generar=Generar Reporte
ctrl.reportes.filtro.periodo=Periodo

# mensajes_en.properties
ctrl.reportes.titulo=System Reports
ctrl.reportes.btn.generar=Generate Report
ctrl.reportes.filtro.periodo=Period

# mensajes_pt.properties
ctrl.reportes.titulo=Relatórios do Sistema
ctrl.reportes.btn.generar=Gerar Relatório
ctrl.reportes.filtro.periodo=Período
```

**Paso 3**: Usar en el controlador:
```java
import com.kipu.cliente.utilidad.IdiomaUtil;
import java.text.MessageFormat;

// Texto simple
labelTitulo.setText(IdiomaUtil.obtener("ctrl.reportes.titulo"));

// Texto con parámetros
String msg = MessageFormat.format(
    IdiomaUtil.obtener("ctrl.reportes.info_total"),
    totalRegistros
);
```

### 7. Verificar Calidad de Traducciones

```bash
# Buscar traducciones que son copias del español (probablemente sin traducir)
paste -d'|' \
  <(grep -oP '(?<==).+' kipu-cliente/src/main/resources/i18n/mensajes.properties) \
  <(grep -oP '(?<==).+' kipu-cliente/src/main/resources/i18n/mensajes_en.properties) | \
  awk -F'|' '$1 == $2 {print NR": "$1}' | head -20

# Lo mismo para PT
paste -d'|' \
  <(grep -oP '(?<==).+' kipu-cliente/src/main/resources/i18n/mensajes.properties) \
  <(grep -oP '(?<==).+' kipu-cliente/src/main/resources/i18n/mensajes_pt.properties) | \
  awk -F'|' '$1 == $2 {print NR": "$1}' | head -20
```

**Excepciones válidas** donde ES = EN = PT:
- Nombres propios: "Kipu", "KIPU"
- Códigos: "v1.0.0", "PIN"
- Formatos: `{0}`, `{1}`

### 8. Verificar Parámetros MessageFormat

```bash
# Claves con parámetros {0}, {1} — verificar que los 3 idiomas usan los mismos
for param in '{0}' '{1}' '{2}'; do
  echo "=== Claves con $param ==="
  
  ES=$(grep -c "$param" kipu-cliente/src/main/resources/i18n/mensajes.properties)
  EN=$(grep -c "$param" kipu-cliente/src/main/resources/i18n/mensajes_en.properties)
  PT=$(grep -c "$param" kipu-cliente/src/main/resources/i18n/mensajes_pt.properties)
  
  echo "  ES: $ES | EN: $EN | PT: $PT"
  if [ "$ES" != "$EN" ] || [ "$ES" != "$PT" ]; then
    echo "  ⚠️  DESINCRONIZADO — revisar manualmente"
  fi
done
```

## Output Esperado

```
VALIDACIÓN i18n: [nombre-vista o módulo]
═══════════════════════════════════════

STRINGS HARDCODEADOS
  Controladores: X encontrados
    - ArchivoController.java:42 → setText("Productos")  → usar ctrl.productos.titulo
    - ...
  FXML: X encontrados
    - vista.fxml:15 → text="Buscar..."  → usar ctrl.modulo.buscar

CLAVES USADAS
  Total en módulo: X
  Existentes en mensajes.properties: X
  ❌ Inexistentes: X
    - ctrl.modulo.clave_fantasma

SINCRONIZACIÓN
  ES: 1320 claves | EN: 1317 claves | PT: 1317 claves
  Faltantes en EN: X claves
    - ctrl.nueva.clave1
    - ctrl.nueva.clave2
  Faltantes en PT: X claves
    - ctrl.nueva.clave1

CALIDAD DE TRADUCCIONES
  EN idénticas a ES: X (revisar si son traducciones pendientes)
  PT idénticas a ES: X

RESUMEN
  ✅ / ⚠️ / ❌ — [estado general]
```
