param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$maven = Join-Path $root '.tools\apache-maven-3.9.11\bin\mvn.cmd'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker Desktop is required for the local MVP. Start Docker Desktop, then run this launcher again.'
}
if (-not (Test-Path $maven)) {
    throw 'The bundled Maven runtime was not found. Restore .tools before starting Draugr.'
}

Push-Location $root
try {
    docker compose up -d postgres
    Start-Process -FilePath $maven -ArgumentList 'spring-boot:run' -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Hidden
    Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev','--','--host','127.0.0.1' -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden
    if (-not $NoBrowser) { Start-Process 'http://127.0.0.1:5173' }
    Write-Host 'Project Draugr is starting. PostgreSQL, backend, and frontend were launched.'
} finally {
    Pop-Location
}
