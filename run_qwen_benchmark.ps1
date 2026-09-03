[CmdletBinding()]
param(
    [int]$Limit = 0,
    [ValidateSet("auto", "cuda", "cpu")]
    [string]$Device = "auto",
    [ValidateSet("auto", "float32", "float16", "bfloat16")]
    [string]$Dtype = "auto",
    [string]$Model = "Qwen/Qwen3-0.6B",
    [string]$Dataset = "ai\benchmark\gold_manual_pl.jsonl",
    [string]$Output = "ai\benchmark\results\qwen3_0.6b",
    [int]$MaxNewTokens = 256,
    [switch]$NoResume,
    [switch]$LocalFilesOnly
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
$BenchmarkRoot = Join-Path $RepoRoot "ai\benchmark"
$VenvRoot = Join-Path $BenchmarkRoot ".venv"
$VenvPython = Join-Path $VenvRoot "Scripts\python.exe"
$Requirements = Join-Path $BenchmarkRoot "requirements.txt"
$SetupMarker = Join-Path $VenvRoot ".tiqo-requirements.sha256"

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)][string]$File,
        [Parameter(Mandatory = $true)][string[]]$CommandArgs
    )
    & $File @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw ("Command failed with exit code {0}: {1} {2}" -f $LASTEXITCODE, $File, ($CommandArgs -join " "))
    }
}

function New-BenchmarkVenv {
    $PyLauncher = Get-Command "py.exe" -ErrorAction SilentlyContinue
    if ($PyLauncher) {
        & $PyLauncher.Source -3.12 -m venv $VenvRoot
        if ($LASTEXITCODE -eq 0) { return }
        Write-Host "Python 3.12 is unavailable; trying Python 3.11..." -ForegroundColor Yellow
        & $PyLauncher.Source -3.11 -m venv $VenvRoot
        if ($LASTEXITCODE -eq 0) { return }
    }
    $Python = Get-Command "python.exe" -ErrorAction SilentlyContinue
    if (-not $Python) {
        throw "Python 3.11 or 3.12 was not found. Install 64-bit Python and run this script again."
    }
    Invoke-CheckedNative -File $Python.Source -CommandArgs @("-m", "venv", $VenvRoot)
}

$VenvWorks = $false
if (Test-Path -LiteralPath $VenvPython -PathType Leaf) {
    & $VenvPython -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)" 2>$null
    $VenvWorks = $LASTEXITCODE -eq 0
}
if (-not $VenvWorks) {
    if (Test-Path -LiteralPath $VenvRoot) {
        $Backup = "$VenvRoot.broken-$([DateTime]::Now.ToString('yyyyMMdd-HHmmss'))"
        Write-Host "Existing benchmark venv is not usable; preserving it as $Backup" -ForegroundColor Yellow
        Move-Item -LiteralPath $VenvRoot -Destination $Backup
    }
    Write-Host "Creating benchmark virtual environment..." -ForegroundColor Cyan
    New-BenchmarkVenv
}

$RequirementsHash = (Get-FileHash -LiteralPath $Requirements -Algorithm SHA256).Hash
$DependenciesReady = $false
if (Test-Path -LiteralPath $SetupMarker -PathType Leaf) {
    $RecordedHash = (Get-Content -LiteralPath $SetupMarker -Raw).Trim()
    if ($RecordedHash -eq $RequirementsHash) {
        & $VenvPython -c "import torch, transformers; raise SystemExit(0)" 2>$null
        $DependenciesReady = $LASTEXITCODE -eq 0
    }
}

if (-not $DependenciesReady) {
    Write-Host "Installing benchmark dependencies (only on first run or after a requirements change)..." -ForegroundColor Cyan
    Invoke-CheckedNative -File $VenvPython -CommandArgs @("-m", "pip", "install", "--upgrade", "pip")
    $NvidiaSmi = Get-Command "nvidia-smi.exe" -ErrorAction SilentlyContinue
    if ($NvidiaSmi) {
        Write-Host "NVIDIA GPU detected; installing the PyTorch CUDA 12.6 wheel..." -ForegroundColor Cyan
        Invoke-CheckedNative -File $VenvPython -CommandArgs @("-m", "pip", "install", "torch", "--index-url", "https://download.pytorch.org/whl/cu126")
    } else {
        Write-Host "No NVIDIA driver detected; installing CPU-compatible PyTorch..." -ForegroundColor Yellow
        Invoke-CheckedNative -File $VenvPython -CommandArgs @("-m", "pip", "install", "torch")
    }
    Invoke-CheckedNative -File $VenvPython -CommandArgs @("-m", "pip", "install", "-r", $Requirements)
    Set-Content -LiteralPath $SetupMarker -Value $RequirementsHash -Encoding Ascii
}

Write-Host "Runtime check:" -ForegroundColor Cyan
Invoke-CheckedNative -File $VenvPython -CommandArgs @(
    "-c",
    'import torch, transformers; print(f"Python dependencies OK | torch={torch.__version__} | transformers={transformers.__version__} | cuda={torch.cuda.is_available()} | gpu={torch.cuda.get_device_name(0) if torch.cuda.is_available() else ''none''}")'
)

$RunnerArgs = @(
    "-m", "ai.benchmark.run_qwen_benchmark",
    "--model", $Model,
    "--dataset", $Dataset,
    "--device", $Device,
    "--dtype", $Dtype,
    "--output", $Output,
    "--max-new-tokens", $MaxNewTokens
)
if ($Limit -gt 0) { $RunnerArgs += @("--limit", $Limit) }
if ($NoResume) { $RunnerArgs += "--no-resume" } else { $RunnerArgs += "--resume" }
if ($LocalFilesOnly) { $RunnerArgs += "--local-files-only" }

Push-Location $RepoRoot
try {
    Invoke-CheckedNative -File $VenvPython -CommandArgs $RunnerArgs
} finally {
    Pop-Location
}
