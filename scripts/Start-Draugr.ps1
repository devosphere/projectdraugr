param(
    [switch]$NoBrowser,
    [switch]$AppWindow,
    [switch]$Splash,
    [switch]$AutoStartDocker
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$maven = Join-Path $root '.tools\apache-maven-3.9.11\bin\mvn.cmd'
$runtimeFile = Join-Path $root '.draugr-runtime.json'

$splashState = $null
function Show-Splash {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = New-Object System.Windows.Forms.Form
    $form.FormBorderStyle = 'None'
    $form.StartPosition = 'CenterScreen'
    $form.Size = New-Object System.Drawing.Size(560, 320)
    $form.TopMost = $true
    $form.BackColor = [System.Drawing.Color]::FromArgb(18, 20, 19)
    $title = New-Object System.Windows.Forms.Label
    $title.Text = 'PROJECT DRAUGR'
    $title.ForeColor = [System.Drawing.Color]::FromArgb(206, 178, 110)
    $title.Font = New-Object System.Drawing.Font('Georgia', 30, [System.Drawing.FontStyle]::Bold)
    $title.TextAlign = 'MiddleCenter'
    $title.Dock = 'Top'
    $title.Height = 190
    $status = New-Object System.Windows.Forms.Label
    $status.Text = 'Waking the world...'
    $status.ForeColor = [System.Drawing.Color]::FromArgb(181, 181, 170)
    $status.Font = New-Object System.Drawing.Font('Segoe UI', 11)
    $status.TextAlign = 'MiddleCenter'
    $status.Dock = 'Fill'
    $form.Controls.Add($status)
    $form.Controls.Add($title)
    $form.Show()
    $form.Refresh()
    return @{ Form = $form; Status = $status }
}
function Set-SplashStatus($state, $text) {
    if ($state) {
        $state.Status.Text = $text
        [System.Windows.Forms.Application]::DoEvents()
    }
}
function Close-Splash($state) {
    if ($state) { $state.Form.Close(); $state.Form.Dispose() }
}
function Show-LaunchError($state, $message) {
    if ($state) {
        Add-Type -AssemblyName System.Windows.Forms
        [System.Windows.Forms.MessageBox]::Show($message, 'Project Draugr could not start', 'OK', 'Error') | Out-Null
    }
}
function Ensure-DockerEngine([bool]$autoStart, $state) {
    docker info *> $null
    if ($LASTEXITCODE -eq 0) { return }
    if (-not $autoStart) { throw 'The Docker engine is not running. Start Docker Desktop, then launch Project Draugr again.' }
    $dockerDesktop = @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Docker\Docker\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Docker\Docker Desktop.exe')
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    # Fall back to the folder that contains the docker CLI already on PATH.
    if (-not $dockerDesktop) {
        $dockerCli = (Get-Command docker -ErrorAction SilentlyContinue).Source
        if ($dockerCli) {
            $guess = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $dockerCli))) 'Docker Desktop.exe'
            if (Test-Path $guess) { $dockerDesktop = $guess }
        }
    }
    if (-not $dockerDesktop) { throw 'Docker Desktop is not installed on this machine. Install Docker Desktop to run the local build.' }
    Set-SplashStatus $state 'Starting Docker Desktop...'
    Start-Process -FilePath $dockerDesktop | Out-Null
    for ($i = 0; $i -lt 120; $i++) {
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Set-SplashStatus $state "Starting Docker Desktop... ($([int]($i*2))s)"
        Start-Sleep -Seconds 2
    }
    throw 'The Docker engine did not become ready in time. Open Docker Desktop manually, then relaunch.'
}
function Watch-GameWindow($root) {
    # In app-window mode, closing the game window (via the Exit button's
    # window.close() or the window frame) should stop the whole local stack, the
    # way a desktop game would. We detect the isolated app-profile browser
    # process and run the clean shutdown once it is gone. Best-effort: any
    # failure here simply leaves the self-healing launcher to clean up next time.
    $profileDir = Join-Path $root '.app-profile'
    $filter = "Name='msedge.exe' OR Name='chrome.exe'"
    try {
        $appeared = $false
        for ($i = 0; $i -lt 30; $i++) {
            if (@(Get-CimInstance Win32_Process -Filter $filter -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($profileDir) }).Count -gt 0) { $appeared = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $appeared) { return }
        while (@(Get-CimInstance Win32_Process -Filter $filter -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($profileDir) }).Count -gt 0) {
            Start-Sleep -Seconds 2
        }
        & (Join-Path $PSScriptRoot 'Stop-Draugr.ps1')
    } catch { }
}
function Open-GameWindow($url, $root) {
    $browser = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'),
        (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'),
        (Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe')
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $browser) { Start-Process $url; return }
    $profileDir = Join-Path $root '.app-profile'
    $appArgs = @("--app=$url", "--user-data-dir=$profileDir", '--no-first-run', '--no-default-browser-check', '--window-size=1280,800')
    Start-Process -FilePath $browser -ArgumentList $appArgs | Out-Null
}

if ($Splash) { $splashState = Show-Splash }

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker is required for the local MVP. Install Docker Desktop, then run this launcher again.'
    }
    if (-not (Test-Path $maven)) {
        throw 'The bundled Maven runtime was not found. Restore .tools before starting Draugr.'
    }
    if (-not (Test-Path (Join-Path $root 'frontend\node_modules'))) {
        throw 'Frontend dependencies are missing. Open the frontend folder once and run npm install, then launch Project Draugr again.'
    }
    # Self-heal: a previous session that was closed without a clean shutdown can
    # leave the runtime marker or the ports occupied. Rather than forcing the
    # player to stop it manually, stop the prior instance automatically.
    $priorInstance = (Test-Path $runtimeFile) -or (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) -or (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)
    if ($priorInstance) {
        Set-SplashStatus $splashState 'Closing a previous session...'
        try { & (Join-Path $PSScriptRoot 'Stop-Draugr.ps1') } catch { }
        Start-Sleep -Seconds 3
    }
    foreach ($port in 8080, 5173) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            throw "Port $port is still in use after attempting to close the previous session. Close that process, then relaunch."
        }
    }

    Set-SplashStatus $splashState 'Checking the Docker engine...'
    Ensure-DockerEngine ([bool]$AutoStartDocker) $splashState

    Push-Location $root
    try {
        Set-SplashStatus $splashState 'Starting the world database...'
        docker compose up -d postgres
        Set-SplashStatus $splashState 'Awakening the simulation...'
        $backend = Start-Process -FilePath $maven -ArgumentList 'spring-boot:run' -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Hidden -PassThru
        $frontend = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev', '--', '--host', '127.0.0.1' -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -PassThru
        [pscustomobject]@{ backendProcessId = $backend.Id; frontendProcessId = $frontend.Id; startedAt = (Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -Path $runtimeFile -Encoding utf8
        $backendReady = $false
        $frontendReady = $false
        for ($attempt = 1; $attempt -le 120; $attempt++) {
            try { $backendReady = (Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/api/health' -TimeoutSec 2).StatusCode -eq 200 } catch { }
            try { $frontendReady = (Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:5173' -TimeoutSec 2).StatusCode -eq 200 } catch { }
            if ($backendReady -and $frontendReady) { break }
            Set-SplashStatus $splashState 'Awakening the simulation...'
            Start-Sleep -Seconds 1
        }
        if (-not ($backendReady -and $frontendReady)) { throw 'Project Draugr did not become ready in time. PostgreSQL was left running for inspection.' }
        Set-SplashStatus $splashState 'Entering the world...'
        if ($AppWindow) { Open-GameWindow 'http://127.0.0.1:5173' $root }
        elseif (-not $NoBrowser) { Start-Process 'http://127.0.0.1:5173' }
        Start-Sleep -Seconds 2
        Write-Host 'Project Draugr is starting. PostgreSQL, backend, and frontend were launched.'
        Close-Splash $splashState; $splashState = $null
        if ($AppWindow) { Watch-GameWindow $root }
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
} catch {
    Show-LaunchError $splashState $_.Exception.Message
    throw
} finally {
    Close-Splash $splashState
}
