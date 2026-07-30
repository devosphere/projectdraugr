' Project Draugr — game-like launcher.
' Runs the local stack with no console window: shows a splash, starts the
' Docker engine if needed, and opens the game in a chromeless app window.
Dim shell, fso, scriptDir, command
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
command = "powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & scriptDir & "\scripts\Start-Draugr.ps1"" -Splash -AppWindow -AutoStartDocker"
' 0 = hidden window (no console flash); False = do not wait for it to finish.
shell.Run command, 0, False
