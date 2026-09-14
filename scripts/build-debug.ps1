param([string]$Destination = 'S:\Tiqo\builds\Tiqo-debug.apk')
$repoPath = Split-Path $PSScriptRoot -Parent
$logDirectory = Join-Path $repoPath 'build-logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$buildLog = Join-Path $logDirectory ("debug-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
Push-Location $repoPath
try {
    & .\gradlew.bat :app:assembleDebug --console=plain *> $buildLog
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILURE — log: $buildLog"
        exit 1
    }
    $apk = Join-Path $repoPath 'app\build\outputs\apk\debug\app-debug.apk'
    if (!(Test-Path -LiteralPath $apk)) { throw 'Build finished without an APK' }
    $driveRoot = [IO.Path]::GetPathRoot($Destination)
    if (Test-Path -LiteralPath $driveRoot) {
        try {
            New-Item -ItemType Directory -Force -Path (Split-Path $Destination -Parent) -ErrorAction Stop | Out-Null
            Copy-Item -LiteralPath $apk -Destination $Destination -Force -ErrorAction Stop
            Write-Host "SUCCESS — $Destination"
        } catch {
            Add-Content -LiteralPath $buildLog -Value "Copy failed: $_"
            Write-Host "SUCCESS — APK: $apk (copy unavailable; log: $buildLog)"
        }
    } else { Write-Host "SUCCESS — APK: $apk (destination drive unavailable)" }
} catch {
    Add-Content -LiteralPath $buildLog -Value $_
    Write-Host "FAILURE — log: $buildLog"
    exit 1
} finally { Pop-Location }
