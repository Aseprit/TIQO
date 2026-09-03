@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_qwen_benchmark.ps1" %*
exit /b %ERRORLEVEL%
