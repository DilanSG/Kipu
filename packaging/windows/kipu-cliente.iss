; =========================================================
; Kipu Cliente - Script de Inno Setup
; =========================================================
; Archivo separado para facilitar mantenimiento y personalizacion.
;
; Parametros (se pasan desde package.ps1 via iscc /D):
;   /DAppVersion=1.0.0           Version de la aplicacion
;   /DAppPublisher=Kipu         Nombre del publicador
;   /DAppCopyright=...           Texto de copyright
;   /DSourceDir=C:\...\appimage  Ruta a la carpeta app-image generada por jpackage
;   /DOutputDir=C:\...\dist      Directorio de salida del instalador
;   /DServerDir=C:\...\servidor  Ruta a la carpeta del servidor portable
;   /DIconFile=C:\...\kipu.ico  (Opcional) Ruta al icono .ico
;   /DLicenseFile=C:\...\LICENSE (Opcional) Ruta al archivo de licencia
;
; Uso manual (sin package.ps1):
;   iscc /DAppVersion=1.0.0 /DSourceDir="C:\ruta\appimage\Kipu" /DServerDir="C:\ruta\dist\servidor" /DOutputDir="C:\salida" kipu-cliente.iss
; =========================================================

; --- Valores por defecto (se sobreescriben con /D desde linea de comandos) ---
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

#ifndef AppPublisher
  #define AppPublisher "Kipu"
#endif

#ifndef AppCopyright
  #define AppCopyright "Copyright (c) 2026 Dilan Acu" + #$00f1 + "a"
#endif

#ifndef SourceDir
  #error "Debe especificar /DSourceDir=<ruta a la carpeta app-image del cliente>"
#endif

#ifndef ServerDir
  #error "Debe especificar /DServerDir=<ruta a la carpeta del servidor portable>"
#endif

#ifndef OutputDir
  #define OutputDir "."
#endif

; Nombre del ejecutable generado por jpackage
#define AppExeName "Kipu.exe"

; GUID unico de la aplicacion (no cambiar entre versiones)
#define AppId "{{B4A0E7C2-9F3D-4A1B-8C5E-6D2F1A3B7E9C}"

; =========================================================
; [Setup] - Configuracion general del instalador
; =========================================================
[Setup]
AppId={#AppId}
AppName={#AppPublisher}
AppVersion={#AppVersion}
AppVerName={#AppPublisher} {#AppVersion}
AppPublisher={#AppPublisher}
AppCopyright={#AppCopyright}
AppSupportURL=https://github.com/kipu
AppUpdatesURL=https://github.com/kipu

; Directorio de instalacion por defecto.
; El cliente se instala en Kipu\Cliente y el servidor en Kipu\servidor
; para que el Host Mode encuentre el servidor automaticamente.
DefaultDirName={autopf}\Kipu\Cliente
DefaultGroupName=Kipu

; Permitir al usuario elegir si crea iconos en el menu Inicio
AllowNoIcons=yes

; Directorio y nombre del instalador generado
OutputDir={#OutputDir}
OutputBaseFilename=Kipu-{#AppVersion}

; Compresion maxima
Compression=lzma2/max
SolidCompression=yes

; Estilo visual moderno del asistente
WizardStyle=modern
WizardSizePercent=120

; Permisos y compatibilidad
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog commandline
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0

; No preguntar por grupo de programas (ya tiene valor por defecto)
DisableProgramGroupPage=auto

; Permitir al usuario elegir directorio de instalacion
DisableDirPage=no

; Informacion de desinstalacion
UninstallDisplayName={#AppPublisher} Cliente
UninstallDisplayIcon={app}\{#AppExeName}

; Icono del instalador (se define condicionalmente abajo)
#ifdef IconFile
SetupIconFile={#IconFile}
#endif

; Licencia (se muestra como pagina si se proporciona)
#ifdef LicenseFile
LicenseFile={#LicenseFile}
#endif

; =========================================================
; [Languages] - Idiomas disponibles en el asistente
; =========================================================
[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

; =========================================================
; [CustomMessages] - Mensajes personalizados en el asistente
; =========================================================
[CustomMessages]
spanish.LaunchApp=Iniciar %1 ahora
english.LaunchApp=Launch %1 now
spanish.DesktopIcon=Crear acceso directo en el &escritorio
english.DesktopIcon=Create a &desktop shortcut
spanish.StartMenuIcon=Crear acceso directo en el menu &Inicio
english.StartMenuIcon=Create a &Start Menu shortcut
spanish.ConsoleShortcut=Kipu Cliente (Consola de diagnostico)
english.ConsoleShortcut=Kipu Client (Diagnostic Console)
spanish.ServerComponent=Servidor Kipu (necesario para Host Mode)
english.ServerComponent=Kipu Server (required for Host Mode)
spanish.SetupInicialDesc=Abrir configuracion de base de datos
english.SetupInicialDesc=Open database configuration

; =========================================================
; [Types] - Tipos de instalacion
; =========================================================
[Types]
Name: "completa"; Description: "Instalacion completa (recomendada)"
Name: "personalizada"; Description: "Instalacion personalizada"; Flags: iscustom

; =========================================================
; [Components] - Componentes seleccionables
; =========================================================
[Components]
Name: "principal"; Description: "Aplicacion Kipu Cliente"; Types: completa personalizada; Flags: fixed
Name: "servidor"; Description: "{cm:ServerComponent}"; Types: completa
Name: "consola"; Description: "Consola de diagnostico (iniciar-consola.bat)"; Types: completa

; =========================================================
; [Tasks] - Tareas opcionales durante la instalacion
; =========================================================
[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopIcon}"; GroupDescription: "Accesos directos:"; Flags: checkedonce
Name: "startmenuicon"; Description: "{cm:StartMenuIcon}"; GroupDescription: "Accesos directos:"

; =========================================================
; [Files] - Archivos a instalar
; =========================================================
[Files]
; Aplicacion completa (recurse todo el app-image)
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Components: principal

; Servidor portable (se instala junto al cliente: Kipu\servidor\)
; El cliente busca el servidor en {app}\..\servidor\ via ServidorEmbebido
Source: "{#ServerDir}\*"; DestDir: "{app}\..\servidor"; Flags: ignoreversion recursesubdirs createallsubdirs; Components: servidor

; =========================================================
; [Dirs] - Permisos de directorios
; =========================================================
[Dirs]
Name: "{app}\logs"; Permissions: users-modify

; =========================================================
; [Icons] - Accesos directos
; =========================================================
[Icons]
; Menu Inicio
Name: "{group}\Kipu Cliente"; Filename: "{app}\{#AppExeName}"; Tasks: startmenuicon
Name: "{group}\{cm:ConsoleShortcut}"; Filename: "{app}\iniciar-consola.bat"; Components: consola; Tasks: startmenuicon
Name: "{group}\{cm:SetupInicialDesc}"; Filename: "{app}\..\servidor\setup-inicial.bat"; Components: servidor; Tasks: startmenuicon
Name: "{group}\Desinstalar Kipu"; Filename: "{uninstallexe}"; Tasks: startmenuicon

; Escritorio
Name: "{autodesktop}\Kipu Cliente"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

; =========================================================
; [Run] - Acciones post-instalacion
; =========================================================
[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchApp,Kipu Cliente}"; Flags: nowait postinstall skipifsilent

; =========================================================
; [UninstallDelete] - Limpieza al desinstalar
; =========================================================
[UninstallDelete]
; Eliminar logs y archivos generados en runtime
Type: filesandordirs; Name: "{app}\logs"
Type: files; Name: "{app}\*.log"
; Eliminar carpeta del servidor si fue instalada
Type: filesandordirs; Name: "{app}\..\servidor"

; =========================================================
; [Code] - Pascal Script para logica personalizada
; =========================================================
[Code]
// Verificar si ya hay una version instalada y ofrecer desinstalarla
function InitializeSetup(): Boolean;
var
  UninstallKey: String;
  UninstallString: String;
  ResultCode: Integer;
begin
  Result := True;
  UninstallKey := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#AppId}_is1';

  if RegQueryStringValue(HKLM, UninstallKey, 'UninstallString', UninstallString) or
     RegQueryStringValue(HKCU, UninstallKey, 'UninstallString', UninstallString) then
  begin
    if MsgBox('Se detecto una version anterior de Kipu Cliente instalada.' + #13#10 +
              '¿Desea desinstalarla antes de continuar?', mbConfirmation, MB_YESNO) = IDYES then
    begin
      Exec(RemoveQuotes(UninstallString), '/SILENT', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
    end;
  end;
end;

// Crear directorio .kipu en el perfil del usuario si no existe
// (preparacion para el Host Mode)
procedure CurStepChanged(CurStep: TSetupStep);
var
  KipuDir: String;
begin
  if CurStep = ssPostInstall then
  begin
    KipuDir := ExpandConstant('{userappdata}\..\..\.kipu');
    if not DirExists(KipuDir) then
    begin
      CreateDir(KipuDir);
    end;
  end;
end;
