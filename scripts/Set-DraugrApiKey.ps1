# Store your Anthropic API key for Project Draugr, encrypted at rest.
#
# The key is encrypted in the gitignored .secrets\ folder, so it is never committed. You choose the
# protection:
#   * With a PASSWORD (recommended): encrypted as DPAPI(AES-256(key)). Unreadable without BOTH your
#     Windows account on this machine AND the password (which is never stored). You type the password
#     at each launch to enable AI. This is what protects the key even from someone using your own
#     unlocked session — they'd still need the password.
#   * Without a password: DPAPI only — bound to your Windows user + machine, auto-enables at launch.
#     Protects against directory access / file copy / other users, but not your own unlocked session.
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\Set-DraugrApiKey.ps1
# Re-run to rotate the key or change the password. Delete .secrets\anthropic.key to remove it.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir  = Join-Path $root '.secrets'
$file = Join-Path $dir 'anthropic.key'
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

function Get-AesKey([string]$pass) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($pass)) } finally { $sha.Dispose() }
}

Write-Host ''
Write-Host 'Paste your Anthropic API key (input hidden), then Enter:' -ForegroundColor Cyan
$secure = Read-Host -AsSecureString
if ($null -eq $secure -or $secure.Length -eq 0) { Write-Host 'No key entered - nothing changed.' -ForegroundColor Yellow; exit 1 }
$plain = [System.Net.NetworkCredential]::new('', $secure).Password
$valid = $plain.StartsWith('sk-ant-'); $plain = $null
if (-not $valid) { Write-Host "That doesn't look like an Anthropic key (expected 'sk-ant-...'). Nothing saved." -ForegroundColor Yellow; exit 1 }

Write-Host ''
Write-Host 'Set a password to protect the key (recommended). You will type it at each launch to turn AI on.' -ForegroundColor Cyan
Write-Host 'Leave blank for machine-only protection (no launch prompt).' -ForegroundColor DarkGray
$pw1 = Read-Host -AsSecureString 'Password'
$pwPlain1 = [System.Net.NetworkCredential]::new('', $pw1).Password

if ([string]::IsNullOrEmpty($pwPlain1)) {
    # DPAPI-only: machine/user bound, auto-enables at launch.
    $blob = $secure | ConvertFrom-SecureString
    Set-Content -Path $file -Value @('DPAPI', $blob.Trim()) -Encoding ASCII
    Write-Host ''
    Write-Host "Saved (machine-protected, no password) to $file" -ForegroundColor Green
    Write-Host 'AI will turn on automatically at launch.' -ForegroundColor Green
} else {
    $pw2 = Read-Host -AsSecureString 'Confirm password'
    $pwPlain2 = [System.Net.NetworkCredential]::new('', $pw2).Password
    if ($pwPlain1 -ne $pwPlain2) { Write-Host 'Passwords did not match. Nothing saved.' -ForegroundColor Yellow; exit 1 }
    $aes = Get-AesKey $pwPlain1; $pwPlain1 = $null; $pwPlain2 = $null
    # Layered: DPAPI( AES-256( key ) ). Needs both this machine/user AND the password.
    $inner = $secure | ConvertFrom-SecureString -Key $aes
    $blob  = ($inner | ConvertTo-SecureString -AsPlainText -Force) | ConvertFrom-SecureString
    [Array]::Clear($aes, 0, $aes.Length)
    Set-Content -Path $file -Value @('PWD', $blob.Trim()) -Encoding ASCII
    Write-Host ''
    Write-Host "Saved (password + machine protected) to $file" -ForegroundColor Green
    Write-Host 'You will be asked for this password each time you launch. Wrong/skipped -> the game still runs, AI off.' -ForegroundColor Green
}
