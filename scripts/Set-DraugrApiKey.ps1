# Store your Anthropic API key for Project Draugr, encrypted at rest.
#
# The key is encrypted with Windows DPAPI (CurrentUser scope): the ciphertext can be decrypted ONLY
# by the Windows account that ran this script, on this machine. Anyone who opens the file, copies it
# to another PC, or logs in as another user gets undecryptable ciphertext. The file lives in the
# gitignored .secrets\ folder, so it is never committed either.
#
# Usage:  right-click > "Run with PowerShell", or:  powershell -ExecutionPolicy Bypass -File scripts\Set-DraugrApiKey.ps1
# Re-run any time to rotate the key. Delete .secrets\anthropic.key to remove it (AI turns off).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir  = Join-Path $root '.secrets'
$file = Join-Path $dir 'anthropic.key'

if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

Write-Host ''
Write-Host 'Paste your Anthropic API key (starts with sk-ant-). Input is hidden.' -ForegroundColor Cyan
$secure = Read-Host -AsSecureString

if ($null -eq $secure -or $secure.Length -eq 0) {
    Write-Host 'No key entered - nothing changed.' -ForegroundColor Yellow
    exit 1
}

# Sanity check the value looks like an Anthropic key, then discard the plaintext immediately.
$plain = [System.Net.NetworkCredential]::new('', $secure).Password
$looksValid = $plain.StartsWith('sk-ant-')
$plain = $null
if (-not $looksValid) {
    Write-Host "That does not look like an Anthropic key (expected to start with 'sk-ant-'). Nothing saved." -ForegroundColor Yellow
    exit 1
}

# DPAPI-encrypt (CurrentUser) and write the ciphertext.
$secure | ConvertFrom-SecureString | Set-Content -Path $file -Encoding ASCII
$secure = $null

Write-Host ''
Write-Host "Encrypted key saved to  $file" -ForegroundColor Green
Write-Host 'It is encrypted to your Windows user on this machine, and gitignored.' -ForegroundColor Green
Write-Host 'The AI layer will be ON the next time you launch the game.' -ForegroundColor Green
Write-Host 'To turn it off and remove the key, delete that file.' -ForegroundColor Green
