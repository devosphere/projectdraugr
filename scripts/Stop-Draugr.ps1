$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtimeFile = Join-Path $root '.draugr-runtime.json'

if (Test-Path $runtimeFile) {
    $runtime = Get-Content -Raw -Path $runtimeFile | ConvertFrom-Json
    foreach ($processId in @($runtime.backendProcessId, $runtime.frontendProcessId)) {
        if ($processId -and (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
            & taskkill.exe /PID $processId /T /F | Out-Null
        }
    }
    Remove-Item -LiteralPath $runtimeFile -Force
}

# Maven and Vite can outlive the small launcher process that started them on
# Windows. These two ports are reserved exclusively for the local Draugr app,
# so clear any such orphaned child process before declaring the game stopped.
foreach ($port in @(8080, 5173)) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        & taskkill.exe /PID $listener.OwningProcess /T /F | Out-Null
    }
}

Push-Location $root
try {
    docker compose stop postgres
    Write-Host 'Project Draugr has stopped. PostgreSQL data remains in its Docker volume.'
} finally {
    Pop-Location
}
