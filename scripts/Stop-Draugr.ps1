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

Push-Location $root
try {
    docker compose stop postgres
    Write-Host 'Project Draugr has stopped. PostgreSQL data remains in its Docker volume.'
} finally {
    Pop-Location
}
