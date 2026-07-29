@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Draugr.ps1"
if errorlevel 1 (
  echo.
  echo Project Draugr did not start. Review the message above, then press any key to close.
  pause >nul
)
