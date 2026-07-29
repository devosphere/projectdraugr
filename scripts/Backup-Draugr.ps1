$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backupDirectory = Join-Path $root 'backups'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $backupDirectory "draugr-$stamp.sql"

New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
Push-Location $root
try {
    docker compose exec -T postgres pg_dump -U draugr -d draugr | Out-File -FilePath $backupFile -Encoding utf8
    if ((Get-Item $backupFile).Length -eq 0) { throw 'Backup output was empty.' }
    Write-Host "Project Draugr backup created: $backupFile"
} finally {
    Pop-Location
}
