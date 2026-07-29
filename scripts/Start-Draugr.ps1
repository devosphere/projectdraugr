param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$maven = Join-Path $root '.tools\apache-maven-3.9.11\bin\mvn.cmd'
$runtimeFile = Join-Path $root '.draugr-runtime.json'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker Desktop is required for the local MVP. Start Docker Desktop, then run this launcher again.'
}
if (-not (Test-Path $maven)) {
    throw 'The bundled Maven runtime was not found. Restore .tools before starting Draugr.'
}
if (-not (Test-Path (Join-Path $root 'frontend\node_modules'))) {
    throw 'Frontend dependencies are missing. Open the frontend folder once and run npm install, then launch Project Draugr again.'
}
if (Test-Path $runtimeFile) {
    throw 'Project Draugr is already marked as running. Use scripts\Stop-Draugr.ps1 before launching another instance.'
}
foreach ($port in 8080, 5173) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use by another process. Close that existing backend/frontend first, then launch Project Draugr again."
    }
}

Push-Location $root
try {
    docker compose up -d postgres
    $backend = Start-Process -FilePath $maven -ArgumentList 'spring-boot:run' -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Hidden -PassThru
    $frontend = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev','--','--host','127.0.0.1' -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -PassThru
    [pscustomobject]@{ backendProcessId = $backend.Id; frontendProcessId = $frontend.Id; startedAt = (Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -Path $runtimeFile -Encoding utf8
    $backendReady = $false
    $frontendReady = $false
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        try {
            $backendReady = (Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/api/health' -TimeoutSec 2).StatusCode -eq 200
        } catch { }
        try {
            $frontendReady = (Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:5173' -TimeoutSec 2).StatusCode -eq 200
        } catch { }
        if ($backendReady -and $frontendReady) { break }
        Start-Sleep -Seconds 1
    }
    if (-not ($backendReady -and $frontendReady)) { throw 'Project Draugr did not become ready within 90 seconds. PostgreSQL was left running for inspection.' }
    if (-not $NoBrowser) { Start-Process 'http://127.0.0.1:5173' }
    Write-Host 'Project Draugr is starting. PostgreSQL, backend, and frontend were launched.'
} catch {
    foreach ($startedProcess in @($backend, $frontend)) {
        if ($startedProcess -and (Get-Process -Id $startedProcess.Id -ErrorAction SilentlyContinue)) {
            & taskkill.exe /PID $startedProcess.Id /T /F | Out-Null
        }
    }
    if (Test-Path $runtimeFile) { Remove-Item -LiteralPath $runtimeFile -Force }
    throw
} finally {
    Pop-Location
}
