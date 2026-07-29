$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$expectedMigration = 29

try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 5
    if ($health.status -ne 'ready') { throw 'The backend did not report ready.' }
} catch {
    throw 'Project Draugr backend is not ready on port 8080. Start it with scripts\Start-Draugr.ps1, then run verification again.'
}

Push-Location $root
try {
    $migration = (docker compose exec -T postgres psql -U draugr -d draugr -At -c "SELECT COALESCE(MAX(version::int), 0) FROM flyway_schema_history WHERE success;" | Select-Object -Last 1).Trim()
    if ([int]$migration -lt $expectedMigration) {
        throw "Database migration is $migration; this build requires at least $expectedMigration. Restart the current backend so Flyway can finish startup migration."
    }
    $audit = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/audit' -TimeoutSec 5
    if (-not $audit.consistent) {
        $details = ($audit.violations -join '; ')
        throw "Persistent State Auditor found inconsistencies: $details"
    }
    Write-Host "Project Draugr verification passed: backend ready, Flyway migration $migration, Auditor consistent."
} finally {
    Pop-Location
}
