---
name: visual-audit
description: "Auditoría visual completa de vistas JavaFX en Kipu. Use when: revisar consistencia visual, auditar vista FXML, verificar paleta de colores, validar tipografía, comprobar touch targets, revisar header/footer estándar, detectar inconsistencias visuales, revisar estados de botones, verificar contraste WCAG, revisar layout grid 8px, auditar tema luxury/premium, verificar logo KIPU."
---

# Auditoría Visual — Kipu

Procedimiento paso a paso para auditar la calidad visual de cualquier vista FXML del proyecto Kipu, verificando cumplimiento con el tema luxury/premium y las convenciones del proyecto.

## Cuándo Usar

- Al crear una vista FXML nueva (antes de entregar)
- Al modificar estilos o layout de una vista existente
- Para verificar uniformidad visual entre vistas
- Cuando un elemento se ve "diferente" al resto del sistema
- Después de agregar componentes nuevos al catálogo CSS

## Archivos de Referencia

| Archivo | Propósito |
|---------|-----------|
| `kipu-cliente/src/main/resources/css/estilos.css` | Estilos globales (~5800 líneas) |
| `kipu-cliente/src/main/resources/vista/*.fxml` | Vistas FXML |
| `kipu-cliente/src/main/resources/imagenes/LOGOPNG.png` | Logo oficial |

## Procedimiento

### 1. Inspección de Paleta de Colores

Verificar que SOLO se usan colores de la paleta oficial.

```bash
# Extraer todos los colores hex del FXML (no deberían existir — deben estar en CSS)
grep -oP '#[0-9a-fA-F]{3,8}' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | sort -u

# Extraer colores inline en style="" del FXML (anti-patrón)
grep -n 'style="[^"]*-fx-' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml

# Buscar colores fuera de paleta en estilos.css para clases de esta vista
grep -n 'fx-background-color\|fx-text-fill\|fx-border-color' kipu-cliente/src/main/resources/css/estilos.css | grep -v '#0a0a0a\|#121212\|#1a1a1a\|#2a2a2a\|#333333\|#404040\|#d4af37\|#c9a961\|#b8984e\|#f5f5f5\|#e8e8e8\|#b0b0b0\|#999999\|#a8b991\|#daa520\|#8b0000\|transparent\|derive\|linear-gradient\|#1e1e1e\|#0d0d0d\|#4a4a4a\|#666666'
```

**Paleta aceptada:**

| Rol | Colores válidos |
|-----|----------------|
| Fondos | `#0a0a0a` `#0d0d0d` `#121212` `#1a1a1a` `#1e1e1e` `#2a2a2a` |
| Bordes/grises | `#333333` `#404040` `#4a4a4a` `#666666` |
| Dorado (acentos) | `#d4af37` `#c9a961` `#b8984e` |
| Texto claro | `#f5f5f5` `#e8e8e8` `#ffffff` |
| Texto secundario | `#b0b0b0` `#999999` |
| Éxito | `#a8b991` |
| Advertencia | `#daa520` |
| Error | `#8b0000` |

**Regla del dorado**: <15% del área total. Si hay más de 3 elementos con fondo dorado en una misma vista, es excesivo.

### 2. Verificación de Tipografía

```bash
# Tamaños de fuente usados en el FXML
grep -oP 'font-size:\s*\K[0-9]+' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | sort -n | uniq -c

# Fuentes explícitas
grep -n 'font-family\|Font name=' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml
```

**Rangos válidos:**

| Nivel | Tamaño | Peso |
|-------|--------|------|
| Títulos | 18-32px | 500-700 (bold/semibold) |
| Texto principal | 14-16px | 400 (regular) |
| Texto secundario | 12-14px | 300 (light) |
| Labels pequeños | 12px (mínimo absoluto) | 300-400 |

**Errores comunes:**
- Texto <12px → Ilegible en hardware bajo
- Texto >32px que no sea título → Over-sizing
- Font-family decorativa → Solo Roboto/Open Sans/Segoe UI/Helvetica Neue/sans-serif
- Texto largo en dorado → Dorado es para títulos/acentos cortos

### 3. Verificación de Touch Targets

```bash
# Botones con tamaños definidos — verificar ≥44px
grep -n 'prefWidth\|prefHeight\|minWidth\|minHeight' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | grep -i 'button\|toggle'

# Buscar botones sin tamaño explícito (dependen de CSS — verificar en estilos.css)
grep -n '<Button\|<ToggleButton' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | grep -v 'prefWidth\|prefHeight\|minWidth\|minHeight'
```

**Reglas:**
- Todo `Button`/`ToggleButton` visible: mínimo 44×44px (touch target)
- `.boton-principal`: mínimo 60×120px
- `.boton-secundario`: mínimo 48×100px
- Botones en grid (productos/mesas): mínimo 80×80px
- Espaciado entre botones: mínimo 8px (`spacing` o `hgap`/`vgap`)

### 4. Verificación de Header y Footer

**Regla**: Toda vista post-login tiene header + footer. Excepciones: login, splash, modales.

```bash
# ¿Tiene BorderPane como root con <top> y <bottom>?
grep -A2 'BorderPane\|<top>\|<bottom>' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml
```

**Header requerido** (en `<top>`):
- [ ] Logo `LOGOPNG.png` con `fitWidth="120" fitHeight="50" preserveRatio="true"`
- [ ] `Region HBox.hgrow="ALWAYS"` como separador
- [ ] Labels: `labelNombreUsuario` + `labelRolUsuario`
- [ ] Botón logout `boton-logout` con 44×44px
- [ ] StyleClass: `menu-header`

**Footer requerido** (en `<bottom>`):
- [ ] Labels: `labelFechaFooter` + `labelHoraFooter` con separador `|`
- [ ] Label de versión: `Sistema Kipu v1.0.0 © 2026`
- [ ] StyleClass: `menu-footer`

### 5. Verificación de CSS y Clases

```bash
# Listar todas las styleClass usadas en el FXML
grep -oP 'styleClass="[^"]*"' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | sort -u

# Verificar que cada clase existe en estilos.css
for cls in $(grep -oP 'styleClass="\K[^"]*' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | tr ',' '\n' | tr ' ' '\n' | sort -u); do
  if ! grep -q "\.$cls" kipu-cliente/src/main/resources/css/estilos.css; then
    echo "❌ FALTANTE: .$cls"
  fi
done
```

**Reglas CSS:**
- Máximo 3 niveles de selector (`.a .b .c` OK, `.a .b .c .d` NO)
- Máximo 2 `box-shadow` por elemento
- PROHIBIDO: `blur`, `saturate`, `-fx-blend-mode`
- Preferir clases existentes de `estilos.css` antes de crear nuevas
- Nuevas clases siguen nomenclatura: `modulo-componente-variante` (ej: `logs-badge-error`)

### 6. Verificación de Estados de Botones

Para cada clase de botón usada en la vista, verificar que `estilos.css` tiene los 5 estados:

```bash
# Para una clase de botón, verificar estados
CLASE="boton-principal"
grep -n "\.$CLASE" kipu-cliente/src/main/resources/css/estilos.css | head -20
# Debe tener: base, :hover, :pressed, :focused, :disabled
```

**Estados obligatorios:**

| Estado | Efecto visual |
|--------|---------------|
| Normal | Base: color, tamaño, sombra |
| `:hover` | Elevar -2px (translate-y), sombra más intensa |
| `:pressed` | Bajar (translate-y positivo), sombra reducida |
| `:focused` | Borde dorado 2-3px |
| `:disabled` | Gris, opacity 0.5 |

### 7. Verificación de Layout

```bash
# Verificar grid de 8px (padding/spacing deben ser múltiplos de 8)
grep -oP '(top|right|bottom|left|spacing|hgap|vgap)="(\K[0-9]+)' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | sort -n | uniq -c
```

**Reglas:**
- Padding/spacing: múltiplos de 8 (8, 16, 24, 32, 40, 48...)
- Tolerancia: 4, 10, 12 aceptables en casos especiales (labels, bordes internos)
- Border-radius: 8px o 12px (consistente dentro de la misma vista)
- Máximo 3 niveles de jerarquía visual en cualquier sección

### 8. Verificación del Logo

```bash
# Usos del logo en la vista
grep -n 'LOGOPNG' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml
```

**Ubicaciones válidas:**
- **Login**: Centro-superior, 200-300px ancho
- **Header**: Esquina izquierda, 120×50px con `preserveRatio="true"`
- SIEMPRE: sin filtros, sin efectos, fondo oscuro detrás, spacing 24px

### 9. Performance Visual

```bash
# Contar nodos estáticos en el FXML
grep -c '<' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml

# Buscar efectos costosos en CSS para clases de esta vista
CLASES=$(grep -oP 'styleClass="\K[^"]*' kipu-cliente/src/main/resources/vista/ARCHIVO.fxml | tr ',' '\n' | tr ' ' '\n' | sort -u)
for cls in $CLASES; do
  grep -A5 "\.$cls" kipu-cliente/src/main/resources/css/estilos.css | grep -i 'blur\|saturate\|blend\|drop-shadow'
done
```

**Límites:**
- <1000 nodos visibles simultáneos
- Máx 2 `box-shadow` por elemento
- Animaciones 200-300ms, solo `transform`/`opacity`
- NO: parallax, partículas, 3D, blur animado

## Output Esperado

```
AUDITORÍA VISUAL: [nombre-de-vista.fxml]
════════════════════════════════════════

✅ CORRECTO
  - Paleta: Todos los colores dentro de la paleta oficial
  - Touch targets: Botones ≥44px
  - Header/Footer: Estructura estándar presente
  - ...

⚠️ ADVERTENCIAS
  - [componente]: [detalle del problema] → [solución]

❌ ERRORES
  - [componente]: [detalle del problema] → [corrección mandatoria]

📐 RESOLUCIONES (ver skill resolution-adaptation)
  - Estado: [Verificar con skill resolution-adaptation]

🌐 i18n (ver skill i18n-validation)
  - Estado: [Verificar con skill i18n-validation]
```
