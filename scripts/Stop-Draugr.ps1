$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

foreach ($port in 5173, 8080) {
    $listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        Stop-Process -Id $listener.OwningProcess -Force
    }
}

Push-Location $root
try {
    docker compose stop postgres
    Write-Host 'Project Draugr has stopped. PostgreSQL data remains in its Docker volume.'
} finally {
    Pop-Location
}
