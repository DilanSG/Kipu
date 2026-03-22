/*Copyright (c) 2026 Baryx. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Baryx (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.baryx.common.constantes;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*Constantes compartidas entre cliente y servidor.
 *Esta clase centraliza valores constantes usados en todo el sistema para evitar duplicación y facilitar mantenimiento.
 * Organización:
 * - Endpoints: rutas de la API REST
 * - Http: configuración de peticiones HTTP
 * - Jwt: configuración de tokens de autenticación
 * - Formatos: formateadores de fecha/hora
 * - Validaciones: límites y reglas de validación
 * - Ui: constantes de interfaz de usuario
 * - Mensajes: textos estándar del sistema
 * - CodigosError: códigos de error estandarizados
 * - Roles: roles de usuario predefinidos
 * - Negocio: reglas de negocio y configuraciones del dominio
 * Nomenclatura:
 * - Clases internas para agrupar constantes por categoría (Endpoints, Http, Jwt, etc.)
 * - Constantes en UPPER_SNAKE_CASE para claridad y distinción de variables regulares.
 * - Rutas de endpoints en kebab-case y en español para consistencia con el dominio del negocio.
 * - Mensajes en español para comunicación directa con el usuario.
 * - Códigos de error en UPPER_SNAKE_CASE para fácil identificación en logs y manejo de excepciones.
 *IMPORTANTE: Esta clase es final y no instanciable.
 *Todos los valores son constantes públicas para acceso directo.
 *Uso:
 * - String url = servidor + Constantes.Endpoints.PRODUCTOS;
 * - if (nombre.length() > Constantes.Validaciones.NOMBRE_MAX_LENGTH) { ... } */

public final class Constantes {
    
    // Constructor privado para prevenir instanciación. Esta es una clase de utilidad que solo contiene constantes estáticas.
    private Constantes() {
        throw new UnsupportedOperationException("Clase de constantes no instanciable");
    }
    
    // ENDPOINTS API Nomenclatura: kebab-case en español, sustantivos en plural.
    
    public static final class Endpoints {
        private Endpoints() {}
       
        public static final String USUARIOS = "/api/usuarios";
        public static final String PRODUCTOS = "/api/productos";
        public static final String CATEGORIAS = "/api/categorias";
        public static final String PEDIDOS = "/api/pedidos";
        public static final String VENTAS = "/api/ventas";
        public static final String MESAS = "/api/mesas";
        public static final String METODOS_PAGO = "/api/metodos-pago";
        public static final String REPORTES = "/api/reportes";
        public static final String LOGIN = USUARIOS + "/login";
        public static final String LOGIN_PIN = USUARIOS + "/login-pin";
        public static final String REFRESH_TOKEN = USUARIOS + "/refresh-token";
        public static final String HEALTH = USUARIOS + "/health";
        public static final String VERIFICAR_CODIGO = USUARIOS + "/verificar-codigo";
        public static final String USUARIOS_BUSCAR = USUARIOS + "/buscar";
        public static final String USUARIOS_POR_ROL = USUARIOS + "/por-rol";
        public static final String VENTAS_DIARIAS = REPORTES + "/ventas-diarias";
        public static final String PRODUCTOS_MAS_VENDIDOS = REPORTES + "/productos-mas-vendidos";
        public static final String VENTAS_POR_MESERO = REPORTES + "/ventas-por-mesero";
        public static final String LOGS_CRITICOS = "/api/logs-criticos";
        public static final String LOGS_CRITICOS_PENDIENTES = LOGS_CRITICOS + "/pendientes";
        public static final String LOGS_CRITICOS_CONTEO = LOGS_CRITICOS + "/conteo";
        public static final String CONFIGURACION_SISTEMA = "/api/sistema/configuracion";
    }
    //UPER_SNAKE_CASE METODS

    // CONFIGURACIÓN HTTP
    public static final class Http {
        private Http() {}
        
        public static final int TIMEOUT_CONEXION_SEGUNDOS = 30;
        public static final int TIMEOUT_LECTURA_SEGUNDOS = 30;
        public static final int TIMEOUT_ESCRITURA_SEGUNDOS = 30;
        public static final int MAX_REINTENTOS = 3;
        public static final int REINTENTO_DELAY_MS = 1000;
        public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
        public static final String HEADER_AUTHORIZATION = "Authorization";
        public static final String BEARER_PREFIX = "Bearer ";
        public static final String HEADER_CONTENT_TYPE = "Content-Type";
        public static final String HEADER_CLIENT_NAME = "X-Client-Name";
        public static final String HEADER_CLIENT_IP = "X-Client-IP";
        public static final int POOL_MAX_IDLE = 5;
        public static final int POOL_KEEP_ALIVE_MINUTES = 5;
    }
    
    // SEGURIDAD JWT
    public static final class Jwt {
        private Jwt() {}
        
        public static final long EXPIRACION_MS = 86400000L; // 24 horas
        public static final long REFRESH_EXPIRACION_MS = 604800000L; // 7 días
        public static final String CLAIM_ROL = "rol";
        public static final String CLAIM_ID_USUARIO = "idUsuario";
        public static final String CLAIM_NOMBRE_USUARIO = "nombreUsuario";
        public static final String PREFIX_BLACKLIST = "jwt:blacklist:";
    }
    
    // FORMATOS DE FECHA
    public static final class Formatos {
        private Formatos() {}
        
        private static final Locale LOCALE_ES = Locale.of("es", "ES");
        // Formatos comunes
        public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        // Formato completo con nombre del día (para headers/footers)
        public static final DateTimeFormatter FECHA_COMPLETA = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", LOCALE_ES);
        
        // Formato para base de datos y APIs
        public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        public static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        public static final DateTimeFormatter HORA_SIMPLE = DateTimeFormatter.ofPattern("HH:mm");
    }
    
    // VALIDACIONES
    public static final class Validaciones {
        private Validaciones() {}

        public static final int USUARIO_MIN_LENGTH = 4;
        public static final int USUARIO_MAX_LENGTH = 50;
        public static final int PASSWORD_MIN_LENGTH = 4;
        public static final int PIN_LENGTH = 4;
        public static final int CODIGO_LENGTH = 4;
        public static final int NOMBRE_COMPLETO_MAX_LENGTH = 100;
        public static final int LONGITUD_MIN_CONTRASENA = PASSWORD_MIN_LENGTH;
        public static final int LONGITUD_PIN = PIN_LENGTH;
        public static final int LONGITUD_CODIGO = 2; // Código de empleado es 2 dígitos
        public static final int CODIGO_MIN = 1;
        public static final int CODIGO_MAX = 99;
        public static final int PRODUCTO_NOMBRE_MAX_LENGTH = 100;
        public static final int PRODUCTO_DESCRIPCION_MAX_LENGTH = 500;
        public static final int PRODUCTO_CODIGO_MAX_LENGTH = 50;
        public static final int CATEGORIA_NOMBRE_MAX_LENGTH = 50;
        public static final int CATEGORIA_DESCRIPCION_MAX_LENGTH = 200;
        public static final int PEDIDO_OBSERVACIONES_MAX_LENGTH = 500;
        public static final int MESA_NUMERO_MAX = 999;
        public static final int MESA_NOMBRE_MAX_LENGTH = 50;
        public static final int NOMBRE_MAX_LENGTH = 100;
        public static final int DESCRIPCION_MAX_LENGTH = 500;
        public static final int OBSERVACIONES_MAX_LENGTH = 1000;    
        // Regex patterns
        public static final String PATRON_USUARIO = "^[a-zA-Z0-9_]+$";
        public static final String PATRON_PIN = "^\\d{4}$";
        public static final String PATRON_EMAIL = "^[A-Za-z0-9+_.-]+@(.+)$";
    }
    
    // UI/UX 
    public static final class Ui {
        private Ui() {}
        
        public static final int BOTON_ALTURA_MIN = 60;
        public static final int BOTON_ANCHO_MIN = 120;
        public static final int BOTON_SECUNDARIO_ALTURA_MIN = 48;
        public static final int TOUCH_TARGET_MIN = 44;
        public static final int GRID_ITEM_SIZE = 80;
        public static final int GRID_SPACING = 8;
        public static final int ANIMACION_DURACION_MS = 300;
        public static final int FADE_IN_MS = 300;
        public static final int FADE_OUT_MS = 300;
        public static final int NOTIFICACION_DURACION_MS = 3000;
        public static final int NOTIFICACION_ERROR_DURACION_MS = 5000;
        public static final int ITEMS_POR_PAGINA = 20;
        public static final int ITEMS_POR_PAGINA_GRANDE = 50;
        public static final int FONT_SIZE_TITULO = 24;
        public static final int FONT_SIZE_SUBTITULO = 18;
        public static final int FONT_SIZE_NORMAL = 14;
        public static final int FONT_SIZE_PEQUEÑO = 12;
        public static final int SPACING_XS = 4;
        public static final int SPACING_SM = 8;
        public static final int SPACING_MD = 16;
        public static final int SPACING_LG = 24;
        public static final int SPACING_XL = 32;
    }
    
    // MENSAJES
    public static final class Mensajes {
        private Mensajes() {}
        
        public static final String GUARDADO_EXITOSO = "Guardado exitosamente";
        public static final String ELIMINADO_EXITOSO = "Eliminado exitosamente";
        public static final String ACTUALIZADO_EXITOSO = "Actualizado exitosamente";
        public static final String OPERACION_EXITOSA = "Operación exitosa";
        public static final String EXITO_GENERICO = "Operación realizada con éxito";
        public static final String USUARIOS_OBTENIDOS = "Usuarios obtenidos exitosamente";
        public static final String USUARIO_ENCONTRADO = "Usuario encontrado";
        public static final String USUARIO_CREADO = "Usuario creado exitosamente";
        public static final String USUARIO_ACTUALIZADO = "Usuario actualizado exitosamente";
        public static final String USUARIO_ELIMINADO = "Usuario eliminado exitosamente";
        public static final String PRODUCTOS_OBTENIDOS = "Productos obtenidos exitosamente";
        public static final String PRODUCTO_ENCONTRADO = "Producto encontrado";
        public static final String PRODUCTO_CREADO = "Producto creado exitosamente";
        public static final String PRODUCTO_ACTUALIZADO = "Producto actualizado exitosamente";
        public static final String PRODUCTO_ELIMINADO = "Producto eliminado exitosamente";
        public static final String CATEGORIAS_OBTENIDAS = "Categorías obtenidas exitosamente";
        public static final String CATEGORIA_ENCONTRADA = "Categoría encontrada";
        public static final String CATEGORIA_CREADA = "Categoría creada exitosamente";
        public static final String CATEGORIA_ACTUALIZADA = "Categoría actualizada exitosamente";
        public static final String CATEGORIA_ELIMINADA = "Categoría eliminada exitosamente";
        public static final String BUSQUEDA_COMPLETADA = "Búsqueda completada";
        public static final String SERVICIO_FUNCIONANDO = "Servicio funcionando correctamente";
        public static final String METODOS_PAGO_OBTENIDOS = "Métodos de pago obtenidos exitosamente";
        public static final String METODO_PAGO_CREADO = "Método de pago creado exitosamente";
        public static final String METODO_PAGO_ELIMINADO = "Método de pago eliminado exitosamente";
        public static final String METODO_PAGO_PREDETERMINADO_NO_ELIMINABLE = "No se puede eliminar el método de pago predeterminado";
        public static final String LOGS_OBTENIDOS = "Logs críticos obtenidos exitosamente";
        public static final String LOG_REGISTRADO = "Log crítico registrado";
        public static final String LOG_RESUELTO = "Log marcado como resuelto";
        public static final String ERROR_CARGAR_LOGS = "Error al cargar los logs del sistema";
        public static final String NOMBRE_METODO_PAGO_EXISTS = "Ya existe un método de pago con ese nombre";
        public static final String BIENVENIDO_USUARIO = "Bienvenido, ";
        public static final String CARGANDO_VERIFICANDO_CODIGO = "Verificando código...";
        public static final String CARGANDO_INICIANDO_SESION = "Iniciando sesión...";
        public static final String CREANDO_USUARIO = "Creando usuario...";
        public static final String ACTUALIZANDO_USUARIO = "Actualizando usuario...";
        public static final String CARGANDO_USUARIOS = "Cargando usuarios...";
        public static final String CARGANDO_DINERO_MESAS = "Vista de dinero en mesas - Próximamente";
        public static final String ERROR_CONEXION = "Error de conexión con el servidor";
        public static final String ERROR_DESCONOCIDO = "Ha ocurrido un error inesperado";
        public static final String ERROR_VALIDACION = "Error de validación";
        public static final String ERROR_PERMISOS = "No tiene permisos para realizar esta acción";
        public static final String NOMBRE_REQUERIDO = "El nombre es obligatorio";
        public static final String NOMBRE_COMPLETO_REQUERIDO = "El nombre completo es obligatorio";
        public static final String ROL_REQUERIDO = "El rol es obligatorio";
        public static final String GENERO_REQUERIDO = "El género es obligatorio";
        public static final String NOMBRE_USUARIO_REQUERIDO = "El nombre de usuario es obligatorio";
        public static final String CONTRASENA_REQUERIDA = "La contraseña es obligatoria";
        public static final String CONTRASENA_MIN_LENGTH = "La contraseña debe tener al menos 6 caracteres";
        public static final String CODIGO_EMPLEADO_REQUERIDO = "El código de empleado es obligatorio";
        public static final String CODIGO_EMPLEADO_EXISTS = "El código de empleado ya existe";
        public static final String PIN_REQUERIDO = "El PIN es obligatorio";
        public static final String PIN_LENGTH_INVALID = "El PIN debe tener 4 dígitos";
        public static final String PRECIO_MAYOR_CERO = "El precio debe ser mayor a cero";
        public static final String CATEGORIA_REQUERIDA = "La categoría es requerida";
        public static final String ORDEN_NEGATIVO = "El orden no puede ser negativo";
        public static final String NOMBRE_CATEGORIA_EXISTS = "Ya existe una categoría con ese nombre";
        public static final String CREDENCIALES_PIN_INVALIDAS = "Código o PIN incorrectos";
        public static final String NOMBRE_USUARIO_EXISTS = "El nombre de usuario ya existe";
        public static final String ROL_INVALIDO = "Rol inválido";
        public static final String GENERO_INVALIDO = "Género inválido";
        public static final String CODIGO_LENGTH_INVALID = "El código debe tener 2 dígitos";
        public static final String CODIGO_RANGE_INVALID = "El código debe estar entre 01 y 99";
        public static final String CODIGO_NUMERICO = "El código debe ser numérico";
        public static final String USUARIO_BLOQUEADO_MSG = "Usuario bloqueado. Contacte al administrador.";
        public static final String USUARIO_NO_ENCONTRADO_O_INACTIVO = "Usuario no encontrado o inactivo";
        public static final String ERROR_CARGAR_MENU = "Error al cargar el menú principal";
        public static final String INGRESE_USUARIO_CONTRASENA = "Por favor ingrese usuario y contraseña";
        public static final String INGRESE_PIN = "Ingrese su PIN";
        public static final String INGRESE_CODIGO = "Ingrese un código de 2 dígitos";
        public static final String ERROR_VOLVER_LOGIN = "No se pudo volver al login principal";
        public static final String ERROR_CARGAR_VISTA_PRINCIPAL = "Error al cargar la vista principal";
        public static final String ERROR_LOGIN_GENERICO = "Error al iniciar sesión";
        public static final String ERROR_CONEXION_VERIFIQUE = "No se puede conectar al servidor. Verifique la conexión.";
        public static final String SOLO_NUMEROS = "Solo se permiten números";
        public static final String CODIGO_INVALIDO = "Código inválido";
        public static final String ERROR_CARGAR_USUARIOS = "No se pudieron cargar los usuarios";
        public static final String DEBE_SELECCIONAR_ROL = "Debe seleccionar un rol";
        public static final String DEBE_SELECCIONAR_GENERO = "Debe seleccionar el género";
        public static final String DEBE_SELECCIONAR_CATEGORIA = "Debe seleccionar una categoría";
        public static final String DEBE_SELECCIONAR_TIPO_PRODUCTO = "Debe seleccionar un tipo de producto";
        public static final String CANTIDAD_REQUERIDA = "La cantidad es obligatoria";
        public static final String CANTIDAD_NEGATIVA = "La cantidad no puede ser negativa";
        public static final String CANTIDAD_GRANDE = "La cantidad es demasiado grande";
        public static final String CANTIDAD_INVALIDA = "La cantidad debe ser un número válido";
        public static final String PRECIO_GRANDE = "El precio es demasiado alto";
        public static final String PRECIO_INVALIDO = "El precio debe ser un número válido";
        public static final String TITULO_ERROR = "Error";
        public static final String TITULO_EXITO = "Éxito";
        public static final String TITULO_CONFIRMACION = "Confirmación";
        public static final String ERROR_CARGAR_CATEGORIAS = "Error al cargar categorías";
        public static final String ERROR_CARGAR_PRODUCTOS = "Error al cargar productos";
        public static final String ERROR_CARGAR_MESEROS = "Error al cargar meseros";
        public static final String ERROR_CARGAR_DETALLE_MESA = "Error al cargar el detalle de la mesa";
        public static final String ERROR_CARGAR_VISTA_MESEROS = "Error al cargar la vista de meseros";
        public static final String ERROR_CARGAR_SELECCION_MESERO = "Error al cargar la selección de mesero";
        public static final String MESA_NO_ENCONTRADA = "Mesa no encontrada";
        public static final String INGRESE_NOMBRE_MESA = "Ingrese un número o nombre de mesa";
        public static final String PRODUCTO_AGREGADO = "Producto agregado";
        public static final String NOMBRE_MIN_LENGTH = "El nombre debe tener al menos 3 caracteres";
        public static final String NOMBRE_MAX_LENGTH = "El nombre no puede exceder 100 caracteres";
        public static final String NOMBRE_MAX_LENGTH_50 = "El nombre no puede exceder 50 caracteres";
        public static final String CONFIRMAR_ELIMINACION = "¿Está seguro de eliminar este registro?";
        public static final String CONFIRMAR_SALIR = "¿Está seguro de salir?";
        public static final String CONFIRMAR_CANCELAR = "¿Está seguro de cancelar? Se perderán los cambios.";
        public static final String CONFIRMAR_ELIMINAR_USUARIO = "¿Está seguro de eliminar este usuario?";
        public static final String BTN_EDITAR = "Editar";
        public static final String BTN_ELIMINAR = "Eliminar";
        public static final String LABEL_ACTIVO = "Activo";
        public static final String LABEL_INACTIVO = "Inactivo";
        public static final String LABEL_TODOS = "Todos";
        public static final String LABEL_NUEVO_USUARIO = "Nuevo Usuario";
        public static final String LABEL_EDITAR_USUARIO = "Editar Usuario";
        public static final String LABEL_MESERO = "Mesero";
        public static final String LABEL_MESERA = "Mesera";
        public static final String LABEL_MESA = "Mesa: ";
        public static final String LABEL_TITULO_MESA = "MESA ";
        public static final String LABEL_NO_HAY_CATEGORIAS = "No hay categorías disponibles";
        public static final String LABEL_NO_HAY_PRODUCTOS = "No hay productos en esta categoría";
        public static final String LABEL_PRODUCTOS = "PRODUCTOS - ";
        public static final String ESTADO_DISPONIBLE = "DISPONIBLE";
        public static final String NO_HAY_DATOS = "No hay datos para mostrar";
        public static final String CARGANDO = "Cargando...";
        public static final String PROCESANDO = "Procesando...";
        public static final String LOGIN_EXITOSO = "Bienvenido al sistema";
        public static final String LOGOUT_EXITOSO = "Sesión cerrada correctamente";
        public static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";
        public static final String SESION_EXPIRADA = "Su sesión ha expirado. Por favor inicie sesión nuevamente";
    }
    
    // CÓDIGOS DE ERROR
    public static final class CodigosError {
        private CodigosError() {}
        
        // === Errores de recurso (404) ===
        public static final String RECURSO_NO_ENCONTRADO = "RECURSO_NO_ENCONTRADO";
        public static final String USUARIO_NO_ENCONTRADO = "USUARIO_NO_ENCONTRADO";
        public static final String PRODUCTO_NO_ENCONTRADO = "PRODUCTO_NO_ENCONTRADO";
        public static final String PEDIDO_NO_ENCONTRADO = "PEDIDO_NO_ENCONTRADO";
        
        // === Errores de validación (400) ===
        public static final String VALIDACION_ERROR = "VALIDACION_ERROR";
        public static final String CAMPO_REQUERIDO = "CAMPO_REQUERIDO";
        public static final String FORMATO_INVALIDO = "FORMATO_INVALIDO";
        public static final String DUPLICADO = "DUPLICADO";
        
        // === Errores de autenticación (401) ===
        public static final String CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS";
        public static final String TOKEN_INVALIDO = "TOKEN_INVALIDO";
        public static final String TOKEN_EXPIRADO = "TOKEN_EXPIRADO";
        public static final String USUARIO_BLOQUEADO = "USUARIO_BLOQUEADO";
        
        // === Errores de autorización (403) ===
        public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";
        public static final String PERMISOS_INSUFICIENTES = "PERMISOS_INSUFICIENTES";
        
        // === Errores de servidor (500) ===
        public static final String ERROR_INTERNO = "ERROR_INTERNO";
        public static final String ERROR_BASE_DATOS = "ERROR_BASE_DATOS";
        public static final String ERROR_CONFIGURACION = "ERROR_CONFIGURACION";
        
        // === Errores de red (cliente) ===
        public static final String ERROR_CONEXION = "ERROR_CONEXION";
        public static final String TIMEOUT = "TIMEOUT";
    }
    
    // ROLES
    public static final class Roles {
        private Roles() {}
        public static final String ADMIN = "ADMIN";
        public static final String CAJERO = "CAJERO";
        public static final String MESERO = "MESERO";
    }
    
    // CONFIGURACIÓN DE NEGOCIO
    public static final class Negocio {
        private Negocio() {}
        
        // === Intentos de login ===
        public static final int MAX_INTENTOS_LOGIN = 3;
        
        // === Pedidos ===
        public static final int PEDIDO_TIEMPO_EXPIRACION_MINUTOS = 60;
        
        // === Stock ===
        public static final int STOCK_MINIMO_ALERTA = 10;
        
        // === Caja ===
        public static final int MAXIMO_DESCUENTO_PORCENTAJE = 50;
    }
}
