@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-javafx.ps1"
exit /b %errorlevel%
