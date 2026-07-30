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
    # PostgreSQL is stopped by Stop-Draugr, so start it and wait until it accepts
    # connections BEFORE the backup or the reset. This must happen first: the
    # backup below is the safety net, and it needs a reachable database.
    docker compose up -d postgres
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        docker compose exec -T postgres pg_isready -U draugr -d draugr *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL did not become ready; the world was NOT reset and nothing was changed.' }

    # Create (and verify) a full backup first. Backup-Draugr.ps1 throws on an
    # empty dump, and $ErrorActionPreference='Stop' means any failure here aborts
    # the reset before the destructive DROP runs.
    & (Join-Path $PSScriptRoot 'Backup-Draugr.ps1')

    # Drop and recreate only the 'draugr' database. The Docker volume, the
    # PostgreSQL cluster, and all other databases are untouched. Flyway rebuilds
    # the full schema from the repo migrations on the next launch.
    "DROP DATABASE IF EXISTS draugr WITH (FORCE); CREATE DATABASE draugr;" | docker compose exec -T postgres psql -U draugr -d postgres -v ON_ERROR_STOP=1 | Out-Null
    Write-Host 'The local Draugr world has been reset. Launch Project-Draugr.cmd to apply current migrations and begin again.'
} finally { Pop-Location }
