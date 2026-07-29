param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtimeFile = Join-Path $root '.draugr-runtime.json'
$resolvedBackup = Resolve-Path -LiteralPath $BackupFile -ErrorAction Stop

if (Test-Path $runtimeFile) {
    throw 'Stop Project Draugr before restoring a backup. A restore must never replace a running world.'
}
if (-not $Force) {
    $confirmation = Read-Host "Restore '$resolvedBackup' and replace the current local Draugr world? Type RESTORE to continue"
    if ($confirmation -cne 'RESTORE') {
        Write-Host 'Restore cancelled. No data was changed.'
        exit 0
    }
}

Push-Location $root
try {
    docker compose up -d postgres
    $databaseReset = "DROP DATABASE IF EXISTS draugr WITH (FORCE); CREATE DATABASE draugr;"
    $databaseReset | docker compose exec -T postgres psql -U draugr -d postgres -v ON_ERROR_STOP=1 | Out-Null
    Get-Content -Raw -LiteralPath $resolvedBackup | docker compose exec -T postgres psql -U draugr -d draugr -v ON_ERROR_STOP=1 | Out-Null
    Write-Host "Project Draugr restored from: $resolvedBackup"
} finally {
    Pop-Location
}
