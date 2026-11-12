<#
.SYNOPSIS
    Detiene todo el stack de Pulgatec (los 4 servicios web).

.DESCRIPTION
    Uso (desde la raiz del repositorio):
        .\detener-todo.ps1

    Para detener SOLO la base de datos (no recomendado, la BD es un servicio
    de Windows que arranca solo):
        .\detener-todo.ps1 -ConBase

.NOTES
    La base de datos NO se borra ni se reinicia: los datos siguen intactos.
#>
[CmdletBinding()]
param(
    [switch]$ConBase
)

function Info([string]$m) { Write-Host ("    " + $m) }
function Ok([string]$m)   { Write-Host ("    [OK] " + $m) -ForegroundColor Green }

Write-Host ''
Write-Host '   P U L G A T E C   -   apagando el stack' -ForegroundColor White
Write-Host '   --------------------------------------' -ForegroundColor DarkGray

foreach ($puerto in @(3000, 5173, 8001, 9000)) {
    $conexion = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conexion) {
        Info (":$puerto -> deteniendo PID " + $conexion.OwningProcess)
        Stop-Process -Id $conexion.OwningProcess -Force -ErrorAction SilentlyContinue
    } else {
        Info (":$puerto -> ya estaba libre")
    }
}
Start-Sleep -Seconds 2
Ok 'fronts, API y panel apagados'

if ($ConBase) {
    $svc = Get-Service -Name 'MariaDB-PulgaTec' -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -eq 'Running') {
        Stop-Service -Name 'MariaDB-PulgaTec' -Force -ErrorAction SilentlyContinue
        Ok 'servicio MariaDB-PulgaTec detenido (los datos siguen guardados)'
    }
} else {
    Write-Host '    La base de datos sigue encendida (arranca sola con Windows).' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host '    Para volver a levantar todo:  .\levantar-todo.ps1' -ForegroundColor DarkGray
Write-Host ''
