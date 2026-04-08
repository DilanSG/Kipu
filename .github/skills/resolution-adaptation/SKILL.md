---
name: resolution-adaptation
description: "Adaptación de resoluciones y aspect ratios CSS en Kipu. Use when: crear overrides de resolución, agregar breakpoints CSS, adaptar vista a res-small/tablet/hd/qhd/4k, verificar overrides de ratio, ratio-classic/ultrawide/superwide, agregar estilos responsive, escalar componentes por resolución, verificar escalado de fuentes, adaptar grid por pantalla."
---

# Adaptación de Resoluciones — Kipu

Procedimiento para crear, verificar y mantener los overrides CSS de resolución y aspect ratio en `estilos.css`, garantizando que toda vista se vea correcta en todas las pantallas soportadas.

## Cuándo Usar

- Al crear una vista FXML nueva que necesita overrides de resolución
- Al agregar un componente CSS nuevo que debe adaptarse a diferentes pantallas
- Para verificar que una vista existente tiene overrides completos
- Cuando una vista se ve mal en una resolución específica
- Al revisar las "brechas" en el sistema de resoluciones

## Archivos Clave

| Archivo | Propósito |
|---------|-----------|
| `kipu-cliente/src/main/resources/css/estilos.css` | Overrides líneas ~4420-5740 |
| `kipu-cliente/src/main/java/com/kipu/cliente/utilidad/DetectorPantalla.java` | Detección y aplicación de clases |
| `kipu-cliente/src/main/java/com/kipu/cliente/utilidad/ResolucionPerfil.java` | Perfiles de resolución (enum) |

## Sistema de Resoluciones

### Resolución de Diseño Base
**1920×1080 (FHD 16:9)** — Todo se diseña primero para esta resolución. Los estilos base (sin prefijo `res-*`) son FHD.

### Breakpoints de Resolución

| Clase CSS | Rango px | Factor de texto | Caso de uso |
|-----------|----------|-----------------|-------------|
| `res-small` | <1024 | 0.85 | Tablets, POS viejos |
| `res-tablet` | 1024–1365 | 0.90 | Laptops HD antiguos |
| `res-hd` | 1366–1919 | 0.95 | Laptops estándar 14" |
| *(base FHD)* | 1920–2559 | 1.00 | Full HD — sin clase |
| `res-qhd` | 2560–3839 | 1.25 | Monitores 2K/QHD |
| `res-4k` | ≥3840 | 1.80 | Ultra HD / 4K |

### Breakpoints de Aspect Ratio

| Clase CSS | Ratio | Pantallas |
|-----------|-------|-----------|
| `ratio-tall` | <1.2 | Tablets portrait, monitores rotados |
| `ratio-classic` | 1.2–1.5 | 4:3, 5:4 (CRT, POS antiguo) |
| `ratio-standard` | 1.5–1.7 | 16:10 (MacBooks, algunos portátiles) |
| `ratio-wide` | 1.7–1.9 | 16:9 (más común, base de diseño) |
| `ratio-ultrawide` | 1.9–2.2 | 21:9 (monitores gaming/pro) |
| `ratio-superwide` | >2.2 | 32:9 (Samsung Odyssey, etc.) |

## Procedimiento

### 1. Inventariar Componentes a Adaptar

Para una vista nueva o existente, identificar qué clases CSS necesitan overrides:

```bash
# Extraer todas las clases CSS de una vista
grep -oP 'styleClass="\K[^"]*' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | tr ',' '\n' | tr ' ' '\n' | sort -u

# Para cada clase, verificar si ya tiene overrides
for cls in CLASE1 CLASE2 CLASE3; do
  echo "=== $cls ==="
  for res in res-small res-tablet res-hd res-qhd res-4k; do
    if grep -q "\.$res.*\.$cls\|\.$res .*\.$cls" kipu-cliente/src/main/resources/css/estilos.css; then
      echo "  ✅ $res"
    else
      echo "  ❌ $res — FALTANTE"
    fi
  done
done
```

### 2. Determinar Qué Propiedades Escalar

**Siempre escalar:**
- `-fx-font-size` — Texto debe ser legible en toda resolución
- `-fx-min-width` / `-fx-pref-width` / `-fx-max-width` — Tamaños de contenedores, cards, tiles
- `-fx-min-height` / `-fx-pref-height` / `-fx-max-height` — Alturas de componentes
- `-fx-padding` — Espaciado interno
- Dimensiones de teclado virtual (si aplica)

**A veces escalar:**
- `-fx-border-width` — Solo si es >1px en base
- `-fx-border-radius` — Generalmente fijo (8-12px)
- `spacing` — Solo si el spacing base es >16px

**Nunca escalar:**
- Colores
- Opacidad
- `border-radius` <12px (aceptable fijo)
- Propiedades de animación

### 3. Calcular Valores por Breakpoint

Usar la tabla de escalado proporcionada. Factor de incremento típico ~10-15% por salto:

```
res-small  →  res-tablet  →  res-hd  →  (base FHD)  →  res-qhd  →  res-4k
  ×0.70        ×0.80          ×0.90       ×1.00          ×1.25        ×1.50
```

**Ejemplo para un componente con font-size base 16px:**

| Breakpoint | Factor | Resultado |
|------------|--------|-----------|
| `res-small` | 0.70 | 11px (ajustar a 12px mínimo) |
| `res-tablet` | 0.80 | 13px |
| `res-hd` | 0.90 | 14px |
| *(base FHD)* | 1.00 | 16px |
| `res-qhd` | 1.25 | 20px |
| `res-4k` | 1.50 | 24px |

**Ejemplo para un card con pref-width base 200px:**

| Breakpoint | Factor | Resultado |
|------------|--------|-----------|
| `res-small` | 0.70 | 150px |
| `res-tablet` | 0.80 | 165px |
| `res-hd` | 0.90 | 175px |
| *(base FHD)* | 1.00 | 200px |
| `res-qhd` | 1.25 | 210px |
| `res-4k` | 1.50 | 240px |

**Regla de mínimos absolutos:**
- Texto: nunca <12px (ni siquiera en `res-small`)
- Touch targets: nunca <44×44px en ninguna resolución
- Botón principal: nunca <48×80px

### 4. Escribir los Overrides CSS

**Ubicarlos en la sección correcta de `estilos.css`:**

```
Líneas ~4420-4540:  res-tablet + res-small (base)
Líneas ~4540-4686:  res-qhd + res-4k (base)
Líneas ~4686-4910:  res-hd
Líneas ~4910-5047:  res-tablet complementos
Líneas ~5048-5193:  res-small complementos
Líneas ~5194-5310:  res-qhd complementos
Líneas ~5312-5430:  res-4k complementos
Líneas ~5430-5510:  combinaciones
Líneas ~5514-5740:  ratio overrides
```

**Patrón de escritura:**

```css
/* ═══════════════════════════════════════
   COMPONENTE: [nombre] — Overrides de resolución
   ═══════════════════════════════════════ */

/* --- res-small (<1024px) --- */
.res-small .mi-componente {
    -fx-font-size: 12px;
    -fx-pref-width: 150px;
    -fx-pref-height: 120px;
    -fx-padding: 8px;
}

/* --- res-tablet (1024-1365px) --- */
.res-tablet .mi-componente {
    -fx-font-size: 13px;
    -fx-pref-width: 165px;
    -fx-pref-height: 130px;
    -fx-padding: 10px;
}

/* --- res-hd (1366-1919px) --- */
.res-hd .mi-componente {
    -fx-font-size: 14px;
    -fx-pref-width: 175px;
    -fx-pref-height: 140px;
    -fx-padding: 12px;
}

/* Base FHD (1920-2559px) — definido en estilos base, NO aquí */

/* --- res-qhd (2560-3839px) --- */
.res-qhd .mi-componente {
    -fx-font-size: 20px;
    -fx-pref-width: 210px;
    -fx-pref-height: 170px;
    -fx-padding: 16px;
}

/* --- res-4k (≥3840px) --- */
.res-4k .mi-componente {
    -fx-font-size: 24px;
    -fx-pref-width: 240px;
    -fx-pref-height: 190px;
    -fx-padding: 20px;
}
```

### 5. Agregar Overrides de Ratio (cuando aplica)

Los overrides de ratio son **independientes** de los de resolución y se aplican simultáneamente. Necesarios cuando:

- El componente tiene dimensiones horizontales sensibles al ancho disponible
- Cards/tiles que deben redistribuirse en pantallas estrechas/anchas
- Elementos que necesitan padding horizontal diferente en ultrawide

```css
/* --- ratio-classic (4:3, 5:4) — pantallas más cuadradas --- */
.ratio-classic .mi-componente {
    -fx-pref-width: 170px;      /* Más estrecho */
    -fx-max-width: 200px;
}

/* --- ratio-ultrawide (21:9) — mucho espacio horizontal --- */
.ratio-ultrawide .mi-componente {
    -fx-pref-width: 220px;      /* Más ancho, aprovechar espacio */
    -fx-padding: 12px 24px;     /* Más padding horizontal */
}

/* --- ratio-superwide (32:9) --- */
.ratio-superwide .mi-componente {
    -fx-pref-width: 240px;
    -fx-padding: 12px 32px;
}

/* --- ratio-tall (portrait) --- */
.ratio-tall .mi-componente {
    -fx-pref-width: 160px;      /* Compacto */
    -fx-pref-height: 130px;
}
```

**¿Cuándo NO necesitar overrides de ratio?**
- Componentes que solo tienen font-size (el escalado de resolución basta)
- Elementos con `maxWidth="Infinity"` que ya llenan el espacio disponible
- Botones pequeños (44-60px) cuyo tamaño es fijo e independiente del ratio

### 6. Verificar Cobertura Completa

```bash
# Verificar que los 5 breakpoints de resolución existan para las clases nuevas
CLASE="mi-componente"
echo "Resoluciones:"
for res in res-small res-tablet res-hd res-qhd res-4k; do
  COUNT=$(grep -c "\.$res.*\.$CLASE\|\.$res .*\.$CLASE" kipu-cliente/src/main/resources/css/estilos.css)
  echo "  $res: $COUNT reglas"
done

echo "Ratios:"
for ratio in ratio-tall ratio-classic ratio-standard ratio-ultrawide ratio-superwide; do
  COUNT=$(grep -c "\.$ratio.*\.$CLASE\|\.$ratio .*\.$CLASE" kipu-cliente/src/main/resources/css/estilos.css)
  echo "  $ratio: $COUNT reglas"
done
```

### 7. Matriz de Componentes con Overrides Existentes

Referencia rápida de qué componentes ya tienen cobertura completa:

| Componente | small | tablet | hd | qhd | 4k | Ratios |
|------------|:-----:|:------:|:--:|:---:|:--:|:------:|
| Login form | ✅ | ✅ | ✅ | ✅ | ✅ | classic, ultra, super, tall |
| Menu principal | ✅ | ✅ | ✅ | ✅ | ✅ | classic, standard, ultra, super, tall |
| Productos grid | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| Teclado virtual | ✅ | ✅ | ✅ | ✅ | ✅ | tall |
| Billing/tabs | ✅ | ✅ | ✅ | ✅ | ✅ | classic, ultra |
| Search/input | ✅ | ✅ | ✅ | ✅ | ✅ | classic, ultra |
| Títulos sección | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| Meseros cards | ✅ | ❌ | ✅ | ✅ | ✅ | — |
| Formularios/tablas | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| Notificaciones | ✅ | ✅ | ✅ | ✅ | ✅ | classic, ultra, super, tall |
| Config panel | ✅ | ✅ | ✅ | ✅ | ✅ | classic, ultra, super, tall |

**Brechas conocidas** (sin overrides de resolución):
- Sidebar (`menu-sidebar`, `sidebar-item-activo`)
- Modales (`.modal-overlay`, `.modal-contenido`)
- Botones secundarios (`.boton-secundario`, `.boton-cancelar`)
- Scrollbars, TreeViews, DatePickers
- Badges (`.badge`, `.badge-activo`, `.badge-pendiente`, `.badge-error`)
- Context menus

## Output Esperado

```
ANÁLISIS DE RESOLUCIONES: [nombre-vista o componente]
═════════════════════════════════════════════════════

CLASES ANALIZADAS: X

COBERTURA DE RESOLUCIÓN
  .clase-1: ✅ small ✅ tablet ✅ hd ✅ qhd ✅ 4k
  .clase-2: ✅ small ❌ tablet ✅ hd ✅ qhd ❌ 4k
  .clase-3: ❌ small ❌ tablet ❌ hd ❌ qhd ❌ 4k  ← NUEVA

COBERTURA DE RATIO
  .clase-1: ✅ classic ✅ ultrawide — (no necesita tall/super)
  .clase-2: ❌ sin overrides de ratio

OVERRIDES FALTANTES
  .clase-2 → res-tablet, res-4k
  .clase-3 → TODOS (componente nuevo)

VALORES CALCULADOS (para overrides faltantes)
  [tabla con valores por breakpoint]
```
