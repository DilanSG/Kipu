# Plan: Sistema de Resolución Adaptativa — Kipu

> **Fecha**: 27 de marzo de 2026  
> **Objetivo**: Que la UI de Kipu se vea nativa en cualquier pantalla, detectando automáticamente la resolución y proporción, con opción de override manual en Configuración.

---

## Diagnóstico del Estado Actual

| Aspecto | Situación | Problema |
|---------|-----------|----------|
| Base de diseño | 1920×1080 fijo, Scale transform uniforme | Se renderiza como "imagen escalada", no UI nativa |
| Tolerancia stretch | 8% por eje | Barras negras si ratio difiere >8% de 16:9 |
| Clases CSS `res-*` | 6 definidas, solo 4 con overrides parciales | Solo cubren login/menú/productos. POS, mesas, teclado, formularios sin adaptar |
| Clases CSS `ratio-*` | 3 definidas (`wide`, `standard`, `classic`) | **Cero overrides** — no hacen nada |
| Detección DPI | No existe | 4K se ve minúsculo o borroso según OS |
| Selección manual | No existe | Usuario no puede ajustar nada |
| Ultrawide (21:9+) | Sin clase CSS ni detección | Layout roto, espacio desperdiciado |
| Portrait/tablet | Sin soporte | Inutilizable en orientación vertical |

### Vistas sin overrides responsivos

- POS / Facturación (la más usada)
- Mesas activas / Detalle mesa
- Meseros
- Formularios CRUD (crear/editar productos, categorías)
- Teclado virtual
- Header / Footer
- Panel de configuración
- Logs del sistema

---

## Arquitectura: 3 Capas

```
┌──────────────────────────────────────────────────────┐
│  CAPA 1: Detección automática (inicio de la app)     │
│  → Screen.getPrimary() + getDpi() + getOutputScale() │
│  → Selecciona perfil óptimo automáticamente           │
├──────────────────────────────────────────────────────┤
│  CAPA 2: Perfiles de resolución (motor de escalado)  │
│  → Enum ResolucionPerfil con diseño base por perfil  │
│  → Adapta resolución de diseño + factor de texto     │
├──────────────────────────────────────────────────────┤
│  CAPA 3: Override manual (Configuración del ADMIN)   │
│  → ComboBox de perfiles + Slider de escala global    │
│  → Persistido en kipu-cliente.properties            │
└──────────────────────────────────────────────────────┘
```

---

## Perfiles de Resolución

| Perfil | Resolución base | Ratio | Factor texto | Target |
|--------|----------------|-------|-------------|--------|
| `SMALL_4_3` | 1024×768 | 4:3 | 0.85 | Monitores CRT, cajas registradoras antiguas |
| `SXGA_5_4` | 1280×1024 | 5:4 | 0.88 | Monitores cuadrados oficina |
| `HD_16_9` | 1366×768 | 16:9 | 0.90 | Laptops 14" gama baja |
| `HD_PLUS_16_10` | 1440×900 | 16:10 | 0.92 | MacBook Air / laptops viejos |
| `FHD_16_9` | 1920×1080 | 16:9 | 1.00 | **Diseño base (actual)** |
| `FHD_16_10` | 1920×1200 | 16:10 | 1.00 | Monitores profesionales |
| `QHD_16_9` | 2560×1440 | 16:9 | 1.25 | Monitores 27" |
| `ULTRAWIDE_21_9` | 2560×1080 | 21:9 | 1.00 | Ultrawide gaming |
| `ULTRAWIDE_QHD` | 3440×1440 | 21:9 | 1.15 | Ultrawide QHD premium |
| `UHD_16_9` | 3840×2160 | 16:9 | 1.80 | Monitores 4K |

Cada perfil define:
```java
record ResolucionPerfil(
    String id,                // "FHD_16_9"
    String nombre,            // "Full HD (1920×1080)"
    double anchoDiseno,       // Resolución lógica de diseño
    double altoDiseno,
    double factorTexto,       // Multiplicador de -fx-font-size global
    String claseResolucion,   // "res-fhd"
    String claseRatio         // "ratio-wide"
)
```

**Cambio fundamental:** En vez de renderizar siempre a 1920×1080 y escalar como imagen, el motor:
1. Detecta resolución + ratio reales
2. Elige perfil más cercano (o el seleccionado manualmente)
3. Ajusta `anchoDiseno`/`altoDiseno` al perfil → el Scale transform cubre solo la diferencia residual
4. Aplica `factorTexto` → toda la tipografía escala proporcionalmente via herencia de `.root`

---

## Clases CSS de Ratio (nuevas)

```java
if (ratio >= 2.2)       "ratio-superwide"   // 32:9
else if (ratio >= 1.9)  "ratio-ultrawide"    // 21:9
else if (ratio >= 1.7)  "ratio-wide"         // 16:9  (ya existe)
else if (ratio >= 1.5)  "ratio-standard"     // 16:10 (ya existe)
else if (ratio >= 1.2)  "ratio-classic"      // 4:3, 5:4 (ya existe)
else                    "ratio-tall"          // Portrait / tablet vertical
```

---

## Selector Manual en Configuración

Ubicación: Sección "Sistema" del panel de configuración existente (solo ADMIN)

```
┌──────────────────────────────────────────┐
│  🖥 Pantalla y Resolución                │
├──────────────────────────────────────────┤
│                                          │
│  Resolución detectada: 1920×1080 (16:9)  │
│  DPI: 96 | Escala OS: 100%              │
│                                          │
│  Perfil de pantalla:                     │
│  ┌──────────────────────────────┐        │
│  │ ▼ Automático (recomendado)   │        │
│  │   Full HD (1920×1080)        │        │
│  │   HD (1366×768)              │        │
│  │   SXGA (1280×1024)           │        │
│  │   QHD (2560×1440)            │        │
│  │   4K (3840×2160)             │        │
│  │   Ultrawide (2560×1080)      │        │
│  └──────────────────────────────┘        │
│                                          │
│  Escala de interfaz:                     │
│  ├──────────●──────────┤  110%           │
│  50%                  200%               │
│                                          │
│  [Aplicar]              [Restaurar]      │
│                                          │
│  ☑ Pantalla completa al iniciar          │
│  ☑ Animaciones activas                   │
└──────────────────────────────────────────┘
```

Persistencia en `kipu-cliente.properties`:
```properties
pantalla.perfil=AUTO
pantalla.escala.manual=1.0
```

---

## Matriz de CSS Overrides Necesarios

| Componente | res-small | res-tablet | res-hd | res-fhd | res-qhd | res-4k | ratio-classic | ratio-ultrawide |
|-----------|:---------:|:----------:|:------:|:-------:|:-------:|:------:|:------------:|:--------------:|
| Login | ✅ parcial | ✅ parcial | ❌ | base | ✅ parcial | ✅ parcial | ❌ | ❌ |
| Menu cards | ✅ parcial | ✅ parcial | ❌ | base | ✅ parcial | ✅ parcial | ❌ | ❌ |
| POS/Facturación | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Mesas | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Productos lista | parcial | parcial | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Teclado virtual | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Header/Footer | ❌ parcial | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Formularios | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Config panel | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Logs | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |
| Meseros | ❌ | ❌ | ❌ | base | ❌ | ❌ | ❌ | ❌ |

✅ = Implementado | ❌ = Falta

---

## Migración de Hardcoded a Responsive

### Prioridad Alta (afectan más la experiencia)

1. **Teclado virtual** — Teclas `60×60px` fijas → `em` relativas al font-size base
2. **Login card** — `maxWidth: 520px`, campos `min=max=420px` → porcentajes con min/max
3. **Menu cards** — `280×200px` fijo → `em` units que escalen con tipografía

### Prioridad Media

4. **Header/Footer** — Heights fijas (72px/48px) → `em`
5. **POS grid** — Column count calculado en runtime según ancho disponible
6. **Formularios CRUD** — max-width responsive
7. **Tablas** — Column widths proporcionales

### Prioridad Baja

8. Modales / diálogos
9. Badges y labels decorativos

---

## Proporciones Cubiertas

| Ratio | Valor | Ejemplo | Clase CSS | Estado |
|-------|-------|---------|-----------|--------|
| 5:4 | 1.25 | 1280×1024 | `ratio-classic` | Override nuevo |
| 4:3 | 1.33 | 1024×768 | `ratio-classic` | Override nuevo |
| 3:2 | 1.50 | Surface Pro | `ratio-standard` | Override nuevo |
| 16:10 | 1.60 | 1920×1200, MacBook | `ratio-standard` | Override nuevo |
| 16:9 | 1.78 | 1920×1080, 2560×1440 | `ratio-wide` | Base (ya funciona) |
| 21:9 | 2.33 | 2560×1080, 3440×1440 | `ratio-ultrawide` | Nuevo |
| 32:9 | 3.56 | Samsung Odyssey | `ratio-superwide` | Nuevo |
| <1.2 | variable | Tablet portrait | `ratio-tall` | Nuevo |

---

## Plan de Implementación por Pasos

| Paso | Qué | Archivos | Impacto | Esfuerzo |
|:----:|-----|----------|---------|:--------:|
| **1** | `DetectorPantalla` + `ResolucionPerfil` (motor base) | 2 archivos Java nuevos + props en ConfiguracionCliente | Infraestructura para todo lo demás | Medio |
| **2** | Refactorizar `aplicarEscalado()` para usar perfiles dinámicos | `KipuClienteApplication.java` | Elimina distorsión en todas las proporciones | Medio |
| **3** | Font-size global dinámico en `.root` | `KipuClienteApplication.java` + `estilos.css` | Toda la tipografía escala automáticamente | Bajo |
| **4** | Migrar teclado virtual a `em` | `estilos.css` (sección keyboard) | Fix más visible en resoluciones bajas | Bajo |
| **5** | Migrar login card y menu cards a `em` | `estilos.css` + login-pin.fxml + menu-principal.fxml | Elimina overflow/underflow en extremos | Bajo |
| **6** | CSS overrides para `ratio-classic` y `ratio-ultrawide` | `estilos.css` | Layout correcto en 4:3 y 21:9 | Medio |
| **7** | CSS overrides completos de `res-hd` (faltante) | `estilos.css` | Laptops 14" — caso muy común | Medio |
| **8** | CSS overrides para POS/facturación en todos los breakpoints | `estilos.css` + posible refactor de facturacion.fxml | Vista más usada, más compleja | Alto |
| **9** | Selector manual en Configuración + persistencia | FXML + Controller + ConfiguracionCliente | Control del usuario final | Medio |
| **10** | CSS overrides restantes (formularios, logs, meseros, config) | `estilos.css` | Completitud del sistema | Alto |

---

## Notas Técnicas

- **JavaFX hereda** `-fx-font-size` de `.root` a todos los nodos → cambiar el base escala toda la tipografía
- Los `style="-fx-font-size: 18px"` inline en FXML rompen la herencia → migrar a clases CSS con `em`
- `Screen.getDpi()` devuelve el DPI reportado por el OS (puede ser 96 ficticio en Linux/Windows)
- `Screen.getOutputScaleX()` es más confiable para detectar escala del OS (125%, 150%, 200%)
- El Scale transform se sigue usando, pero solo para la diferencia residual (típicamente <5%)
- Performance: cero impacto — solo se recalculan valores numéricos al cambiar de ventana
