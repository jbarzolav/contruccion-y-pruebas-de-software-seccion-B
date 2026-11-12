<#
.SYNOPSIS
    Levanta TODO el stack de Pulgatec desde la terminal del editor de codigo.

.DESCRIPTION
    MariaDB (servicio Windows) + API Spring (:9000) + panel Django (:8001)
    + front del usuario (:3000) + front del admin (:5173).

    Uso (desde la raiz del repositorio):
        .\levantar-todo.ps1

    Si PowerShell bloquea la ejecucion de scripts:
        powershell -ExecutionPolicy Bypass -File .\levantar-todo.ps1

.PARAMETER SinCompilar
    No recompila el backend aunque el .jar este mas viejo que el codigo.

.PARAMETER Reinstalar
    Vuelve a instalar las dependencias de Python (pip) y de Node (npm).

.NOTES
    Los datos NO se pierden: viven en MariaDB (C:\MariaDB_Pulgatec\data\bd_pulgatec).
    Los procesos quedan en segundo plano (no abren ventanas).
    Para pararlos: .\detener-todo.ps1
    Los logs quedan en %TEMP%\opencode\logs
#>
[CmdletBinding()]
param(
    [switch]$SinCompilar,
    [switch]$Reinstalar
)

$ErrorActionPreference = 'Stop'

$repo   = $PSScriptRoot
$tmp    = Join-Path $env:TEMP 'opencode'
$logs   = Join-Path $tmp 'logs'
$dbDir  = 'C:\MariaDB_Pulgatec'

New-Item -ItemType Directory -Force -Path $logs | Out-Null

# ------------------------------------------------------------------ utilidades
function Leer-Env([string]$ruta) {
    $tabla = @{}
    if (Test-Path $ruta) {
        Get-Content $ruta | ForEach-Object {
            if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $tabla[$Matches[1]] = $Matches[2] }
        }
    }
    return $tabla
}

function Paso([string]$titulo) { Write-Host ''; Write-Host ("==> " + $titulo) -ForegroundColor Cyan }
function Info([string]$m) { Write-Host ("    " + $m) }
function Ok([string]$m)   { Write-Host ("    [OK] " + $m) -ForegroundColor Green }
function Aviso([string]$m){ Write-Host ("    [!] " + $m) -ForegroundColor Yellow }
function Mal([string]$m)  { Write-Host ("    [X] " + $m) -ForegroundColor Red }

function Detener-Puerto([int]$puerto) {
    $conexion = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conexion) {
        Info ("deteniendo lo que ocupaba :$puerto (PID " + $conexion.OwningProcess + ")")
        Stop-Process -Id $conexion.OwningProcess -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
}

function Esperar-Puerto([int]$puerto, [int]$segundos) {
    for ($i = 0; $i -lt $segundos; $i++) {
        Start-Sleep -Seconds 1
        if (Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue) { return $true }
    }
    return $false
}

function Probar-Http([string]$url) {
    try {
        $r = Invoke-WebRequest $url -TimeoutSec 12 -UseBasicParsing
        return [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

function Mostrar-UltimasLineas([string]$ruta, [int]$cuantas) {
    if (Test-Path $ruta) {
        Get-Content $ruta -Tail $cuantas -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host ("        " + $_) -ForegroundColor DarkGray
        }
    }
}

Write-Host ''
Write-Host '   P U L G A T E C   -   arranque del stack completo' -ForegroundColor White
Write-Host '   -------------------------------------------------' -ForegroundColor DarkGray

# ------------------------------------------------------- 1) base de datos
Paso '1/5  Base de datos (MariaDB)'
$svc = Get-Service -Name 'MariaDB-PulgaTec' -ErrorAction SilentlyContinue
if ($svc) {
    if ($svc.Status -ne 'Running') {
        try {
            Start-Service -Name 'MariaDB-PulgaTec' -ErrorAction Stop
            Ok 'servicio MariaDB-PulgaTec iniciado'
        } catch {
            Aviso ("no se pudo iniciar el servicio (puede pedir permisos de administrador): " + $_.Exception.Message)
        }
    } else {
        Ok 'servicio MariaDB-PulgaTec ya en marcha'
    }
} else {
    Aviso 'no se encontro el servicio MariaDB-PulgaTec'
}
$kv = Leer-Env (Join-Path $dbDir 'credentials.env')
if (-not $kv['DB_HOST']) { throw ("No se encontro " + (Join-Path $dbDir 'credentials.env')) }
Info ("BD: " + $kv['DB_HOST'] + ":" + $kv['DB_PORT'] + "/" + $kv['DB_NAME'] + "  (archivos en " + (Join-Path $dbDir 'data') + ")")

# credenciales de correo (Brevo): si no hay clave, la recuperacion cae a la bandeja local
$bv = Leer-Env (Join-Path $dbDir 'brevo.env')
if (-not $bv['APP_RECUPERACION_API_KEY']) { $bv = Leer-Env (Join-Path $tmp 'brevo.env') }
if ($bv['APP_RECUPERACION_API_KEY']) { Info 'recuperacion por correo: activada (Brevo)' }
else { Aviso 'recuperacion por correo: sin clave -> cae a la bandeja local' }

# ------------------------------------------- 2) configuracion local (admin)
Paso '2/5  Configuracion local (cuenta admin + clave interna)'
$cfgLocal = Join-Path $dbDir 'app-local.json'
if (-not (Test-Path $cfgLocal)) {
    $origen = Join-Path $tmp 'admin_local_9000.json'
    if (Test-Path $origen) { Copy-Item $origen $cfgLocal -Force; Info ("creado " + $cfgLocal) }
}
$cfg = $null
if (Test-Path $cfgLocal) { $cfg = Get-Content $cfgLocal -Raw | ConvertFrom-Json }
if (-not $cfg -or -not $cfg.adminCorreo) {
    $boot = Leer-Env (Join-Path $dbDir 'admin-bootstrap.env')
    $cfg = [pscustomobject]@{
        adminCorreo = $boot['APP_ADMIN_CORREO']
        adminPass   = $boot['APP_ADMIN_PASSWORD']
        internalKey = [guid]::NewGuid().ToString('N')
        puerto      = 9000
    }
    [System.IO.File]::WriteAllText($cfgLocal, ($cfg | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
    Aviso ("se genero una clave interna nueva en " + $cfgLocal)
}
Info ("admin: " + $cfg.adminCorreo + "  |  clave interna: " + $cfg.internalKey.Length + " caracteres")

# ------------------------------------------------------- 3) API Spring :9000
Paso '3/5  API de usuario (Spring Boot, puerto 9000)'
$backend = Join-Path $repo 'backend-user-api'
$jar = Get-ChildItem (Join-Path $backend 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
$fuenteNueva = Get-ChildItem (Join-Path $backend 'src\main') -Recurse -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
$hayQueCompilar = $true
if ($SinCompilar) { $hayQueCompilar = $false }
elseif ($jar -and $fuenteNueva -and $jar.LastWriteTime -ge $fuenteNueva.LastWriteTime) { $hayQueCompilar = $false }

if ($hayQueCompilar) {
    Info 'compilando el backend (mvn -q -DskipTests package)...'
    Push-Location $backend
    try {
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw ("mvn termino con codigo " + $LASTEXITCODE) }
    } finally { Pop-Location }
    $jar = Get-ChildItem (Join-Path $backend 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    Ok ("backend compilado: " + $jar.Name)
} else {
    Info ("el .jar ya esta al dia (" + $jar.Name + ")")
}
if (-not $jar) { throw 'No se encontro el .jar del backend en backend-user-api\target' }

# imagenes del catalogo: la fuente de verdad es la carpeta del repositorio
$destImgs = Join-Path $env:TEMP 'producto-imagenes'
New-Item -ItemType Directory -Force -Path $destImgs | Out-Null
$mapa = [ordered]@{
    'laptop.jpg' = 'laptop.jpg'; 'mouse.jpg' = 'mouse.jpg'; 'cuaderno.jpg' = 'cuaderno.jpg'
    'libreta.jpg' = 'libreta.jpg'; 'cartera.jpg' = 'cartera.jpg'; 'lapicero.jpg' = 'lapicero.jpg'
    'cargador de laptop.jpg' = 'cargador-laptop.jpg'; 'plumon azul.jpg' = 'plumon-azul.jpg'
    'gancho de cabello.jpg' = 'gancho-cabello.jpg'
}
$copiadas = 0
foreach ($origen in $mapa.Keys) {
    $src = Join-Path (Join-Path $repo 'imagenes-test-prueba') $origen
    if (Test-Path $src) { Copy-Item $src (Join-Path $destImgs $mapa[$origen]) -Force; $copiadas++ }
}
Info ("imagenes del catalogo copiadas: " + $copiadas + " -> " + $destImgs)

$env:SPRING_PROFILES_ACTIVE  = 'mysql'
$env:DB_HOST = $kv['DB_HOST']; $env:DB_PORT = $kv['DB_PORT']; $env:DB_NAME = $kv['DB_NAME']
$env:DB_USER = $kv['DB_USER']; $env:DB_PASSWORD = $kv['DB_PASSWORD']
$env:APP_ADMIN_CORREO = $cfg.adminCorreo
$env:APP_ADMIN_PASSWORD = $cfg.adminPass
$env:APP_DEMO_ENABLED = 'true'
$env:ADMIN_INTERNAL_API_KEY = $cfg.internalKey
foreach ($k in $bv.Keys) { Set-Item -Path ("Env:" + $k) -Value $bv[$k] }

Detener-Puerto 9000
$logSpringOut = Join-Path $logs 'spring-9000.out.log'
$logSpringErr = Join-Path $logs 'spring-9000.err.log'
$proceso = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', ('"' + $jar.FullName + '"'), '--server.port=9000', '--spring.jpa.hibernate.ddl-auto=update') `
    -WorkingDirectory $backend -RedirectStandardOutput $logSpringOut -RedirectStandardError $logSpringErr `
    -PassThru -WindowStyle Hidden
Info ("PID " + $proceso.Id + "   log: " + $logSpringOut)
if (-not (Esperar-Puerto 9000 150)) {
    Mal 'Spring no arranco en :9000. Ultimas lineas:'
    Mostrar-UltimasLineas $logSpringErr 25
    Mostrar-UltimasLineas $logSpringOut 25
    exit 1
}
Ok 'API Spring escuchando en http://localhost:9000'

# ---------------------------------------------------- 4) panel Django :8001
Paso '4/5  Panel de administracion (Django, puerto 8001)'
$adminDir = Join-Path $repo 'backend-admin'
$python   = Join-Path $adminDir '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) { throw ("No existe el entorno virtual: " + $python) }
if ($Reinstalar) {
    Info 'instalando dependencias de Python...'
    & $python -m pip install --quiet -r (Join-Path $adminDir 'requirements.txt')
}
$kd = Leer-Env (Join-Path $dbDir 'admin-db.env')
$env:DJANGO_DB_ENGINE = 'mysql'
$env:DB_HOST = $kv['DB_HOST']; $env:DB_PORT = $kv['DB_PORT']; $env:DB_NAME = $kv['DB_NAME']
$env:DJANGO_DB_USER = $kd['DJANGO_DB_USER']; $env:DJANGO_DB_PASSWORD = $kd['DJANGO_DB_PASSWORD']
$env:USER_API_BASE_URL = 'http://localhost:9000'
$env:INTERNAL_ADMIN_API_KEY = $cfg.internalKey
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000'

Detener-Puerto 8001
$logDjangoOut = Join-Path $logs 'django-8001.out.log'
$logDjangoErr = Join-Path $logs 'django-8001.err.log'
$proceso = Start-Process -FilePath $python `
    -ArgumentList @('manage.py', 'runserver', '0.0.0.0:8001', '--noreload') `
    -WorkingDirectory $adminDir -RedirectStandardOutput $logDjangoOut -RedirectStandardError $logDjangoErr `
    -PassThru -WindowStyle Hidden
Info ("PID " + $proceso.Id + "   log: " + $logDjangoOut)
if (-not (Esperar-Puerto 8001 60)) {
    Mal 'Django no arranco en :8001. Ultimas lineas:'
    Mostrar-UltimasLineas $logDjangoErr 25
    exit 1
}
Ok 'Panel Django escuchando en http://localhost:8001'

# ------------------------------------------------- 5) front usuario :3000
Paso '5/5  Fronts (usuario :3000 y admin :5173)'
function Levantar-Front([string]$carpeta, [string]$nombre, [int]$puerto, [string[]]$argsExtra) {
    Detener-Puerto $puerto
    if ($Reinstalar) {
        Info ("instalando dependencias de " + $nombre + "...")
        Push-Location $carpeta
        try { & npm install --silent } finally { Pop-Location }
    }
    $logOut = Join-Path $logs ($nombre + '-' + $puerto + '.out.log')
    $logErr = Join-Path $logs ($nombre + '-' + $puerto + '.err.log')
    $argumentos = @('/c', 'npm run dev --') + $argsExtra
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList $argumentos -WorkingDirectory $carpeta `
            -RedirectStandardOutput $logOut -RedirectStandardError $logErr -PassThru -WindowStyle Hidden
    Info ("PID " + $proc.Id + "   log: " + $logOut)
    if (Esperar-Puerto $puerto 60) { Ok ($nombre + " escuchando en http://localhost:" + $puerto) }
    else {
        Mal ($nombre + " no arranco en :" + $puerto + ". Ultimas lineas:")
        Mostrar-UltimasLineas $logErr 15
        Mostrar-UltimasLineas $logOut 15
    }
}

Levantar-Front (Join-Path $repo 'frontend-user-web') 'front-usuario' 3000 @('--port', '3000', '--strictPort')
Levantar-Front (Join-Path $repo 'frontend-admin-web') 'front-admin'  5173 @('--port', '5173', '--strictPort')

# ------------------------------------------------------------- 6) resumen
Paso 'Resumen'
$estadoBd = (Get-Service -Name 'MariaDB-PulgaTec' -ErrorAction SilentlyContinue).Status
$filas = @(
    @('MariaDB (BD_Pulgatec)', $estadoBd, '127.0.0.1:3307  (servicio Windows)'),
    @('API Spring',            (Probar-Http 'http://localhost:9000/api/planes/premium'), 'http://localhost:9000'),
    @('Panel Django',          (Probar-Http 'http://localhost:8001/api/admin/usuarios?page=0&size=1'), 'http://localhost:8001'),
    @('Front del usuario',     (Probar-Http 'http://localhost:3000'), 'http://localhost:3000'),
    @('Front del admin',       (Probar-Http 'http://localhost:5173'), 'http://localhost:5173')
)
foreach ($fila in $filas) {
    $valor = $fila[1]
    $estado = if ($valor -is [int]) { if ($valor -gt 0) { 'OK ' + $valor } else { 'SIN RESPUESTA' } } else { $valor }
    Write-Host ("    {0,-22} {1,-15} {2}" -f $fila[0], $estado, $fila[2])
}

Write-Host ''
Write-Host '    Cuentas de prueba (las contrasenas viven en C:\MariaDB_Pulgatec\app-local.json):' -ForegroundColor White
Write-Host '      cliente  -> demo@pulgatec.com'
Write-Host '      vendedor -> desroberto5@gmail.com'
Write-Host ''
Write-Host '    Para parar todo:  .\detener-todo.ps1' -ForegroundColor DarkGray
Write-Host ('    Logs: ' + $logs) -ForegroundColor DarkGray
Write-Host ''
