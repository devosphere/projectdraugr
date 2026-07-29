param([switch]$Force)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtimeFile = Join-Path $root '.draugr-runtime.json'

if (Test-Path $runtimeFile) { throw 'Stop Project Draugr before resetting the world.' }
if (-not $Force) {
    $confirmation = Read-Host 'This creates a backup, then permanently resets the local world. Type RESET WORLD to continue'
    if ($confirmation -cne 'RESET WORLD') { Write-Host 'Reset cancelled. No data was changed.'; exit 0 }
}

Push-Location $root
try {
    & (Join-Path $PSScriptRoot 'Backup-Draugr.ps1')
    docker compose up -d postgres
    "DROP DATABASE IF EXISTS draugr WITH (FORCE); CREATE DATABASE draugr;" | docker compose exec -T postgres psql -U draugr -d postgres -v ON_ERROR_STOP=1 | Out-Null
    Write-Host 'The local Draugr world has been reset. Launch Project-Draugr.cmd to apply current migrations and begin again.'
} finally { Pop-Location }
