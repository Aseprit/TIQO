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
        [Parameter(Mandatory = $true)]
        [string]$File,

        [Parameter(Mandatory = $true)]
        [string[]]$CommandArgs
    )

    & $File @CommandArgs

    if ($LASTEXITCODE -ne 0) {
        throw (
            "Command failed with exit code {0}: {1} {2}" -f `
                $LASTEXITCODE,
                $File,
                ($CommandArgs -join " ")
        )
    }
}

function Test-NvidiaGpu {
    try {
        $GpuList = Get-CimInstance Win32_VideoController -ErrorAction Stop

        foreach ($Gpu in $GpuList) {
            if ($Gpu.Name -match "NVIDIA") {
                return $true
            }
        }
    }
    catch {
        Write-Host "Could not query Windows GPU information." -ForegroundColor Yellow
    }

    return $false
}

function Test-TorchAvailable {
    if (-not (Test-Path -LiteralPath $VenvPython -PathType Leaf)) {
        return $false
    }

    & $VenvPython -c "import torch; raise SystemExit(0)" 2>$null
    return ($LASTEXITCODE -eq 0)
}

function Test-TorchCuda {
    if (-not (Test-TorchAvailable)) {
        return $false
    }

    & $VenvPython -c "import torch; raise SystemExit(0 if torch.cuda.is_available() else 1)" 2>$null
    return ($LASTEXITCODE -eq 0)
}

function New-BenchmarkVenv {
    $PyLauncher = Get-Command "py.exe" -ErrorAction SilentlyContinue

    if ($PyLauncher) {
        & $PyLauncher.Source -3.12 -m venv $VenvRoot

        if ($LASTEXITCODE -eq 0) {
            return
        }

        Write-Host "Python 3.12 is unavailable; trying Python 3.11..." -ForegroundColor Yellow

        & $PyLauncher.Source -3.11 -m venv $VenvRoot

        if ($LASTEXITCODE -eq 0) {
            return
        }
    }

    $Python = Get-Command "python.exe" -ErrorAction SilentlyContinue

    if (-not $Python) {
        throw "Python 3.11 or 3.12 was not found. Install 64-bit Python and run this script again."
    }

    Invoke-CheckedNative `
        -File $Python.Source `
        -CommandArgs @(
            "-m",
            "venv",
            $VenvRoot
        )
}

# -------------------------------------------------------------------
# Virtual environment
# -------------------------------------------------------------------

$VenvWorks = $false

if (Test-Path -LiteralPath $VenvPython -PathType Leaf) {
    & $VenvPython -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)" 2>$null
    $VenvWorks = ($LASTEXITCODE -eq 0)
}

if (-not $VenvWorks) {
    if (Test-Path -LiteralPath $VenvRoot) {
        $Timestamp = [DateTime]::Now.ToString("yyyyMMdd-HHmmss")
        $Backup = "$VenvRoot.broken-$Timestamp"

        Write-Host "Existing benchmark venv is not usable; preserving it as:" -ForegroundColor Yellow
        Write-Host "  $Backup" -ForegroundColor Yellow

        Move-Item `
            -LiteralPath $VenvRoot `
            -Destination $Backup
    }

    Write-Host "Creating benchmark virtual environment..." -ForegroundColor Cyan
    New-BenchmarkVenv
}

# -------------------------------------------------------------------
# Dependency state
# -------------------------------------------------------------------

if (-not (Test-Path -LiteralPath $Requirements -PathType Leaf)) {
    throw "requirements.txt was not found at: $Requirements"
}

$RequirementsHash = (
    Get-FileHash `
        -LiteralPath $Requirements `
        -Algorithm SHA256
).Hash

$DependenciesReady = $false

if (Test-Path -LiteralPath $SetupMarker -PathType Leaf) {
    $RecordedHash = (
        Get-Content `
            -LiteralPath $SetupMarker `
            -Raw
    ).Trim()

    if ($RecordedHash -eq $RequirementsHash) {
        & $VenvPython -c "import torch, transformers; raise SystemExit(0)" 2>$null

        if ($LASTEXITCODE -eq 0) {
            $DependenciesReady = $true
        }
    }
}

# -------------------------------------------------------------------
# Dependencies
# -------------------------------------------------------------------

if (-not $DependenciesReady) {
    Write-Host "Installing benchmark dependencies..." -ForegroundColor Cyan

    Invoke-CheckedNative `
        -File $VenvPython `
        -CommandArgs @(
            "-m",
            "pip",
            "install",
            "--upgrade",
            "pip"
        )

    if (Test-TorchAvailable) {
        Write-Host "Existing PyTorch installation detected; preserving it." -ForegroundColor Green
    }
    else {
        $HasNvidiaGpu = Test-NvidiaGpu

        if ($HasNvidiaGpu) {
            Write-Host "NVIDIA GPU detected by Windows." -ForegroundColor Green
            Write-Host "Installing PyTorch CUDA 12.6..." -ForegroundColor Cyan

            Invoke-CheckedNative `
                -File $VenvPython `
                -CommandArgs @(
                    "-m",
                    "pip",
                    "install",
                    "torch==2.14.0",
                    "--index-url",
                    "https://download.pytorch.org/whl/cu126"
                )
        }
        else {
            Write-Host "No NVIDIA GPU detected by Windows." -ForegroundColor Yellow
            Write-Host "Installing CPU-compatible PyTorch..." -ForegroundColor Yellow

            Invoke-CheckedNative `
                -File $VenvPython `
                -CommandArgs @(
                    "-m",
                    "pip",
                    "install",
                    "torch==2.14.0"
                )
        }
    }

    Invoke-CheckedNative `
        -File $VenvPython `
        -CommandArgs @(
            "-m",
            "pip",
            "install",
            "-r",
            $Requirements
        )

    Set-Content `
        -LiteralPath $SetupMarker `
        -Value $RequirementsHash `
        -Encoding Ascii
}

# -------------------------------------------------------------------
# Runtime check
# -------------------------------------------------------------------

Write-Host ""
Write-Host "Runtime check:" -ForegroundColor Cyan

$RuntimeCheck = "import torch, transformers; cuda_available = torch.cuda.is_available(); gpu_name = torch.cuda.get_device_name(0) if cuda_available else 'none'; print('Python dependencies OK | torch={} | transformers={} | cuda={} | gpu={}'.format(torch.__version__, transformers.__version__, cuda_available, gpu_name))"

Invoke-CheckedNative `
    -File $VenvPython `
    -CommandArgs @(
        "-c",
        $RuntimeCheck
    )

# -------------------------------------------------------------------
# Protect against accidental CPU benchmark
# -------------------------------------------------------------------

$HasNvidiaGpu = Test-NvidiaGpu
$CudaWorks = Test-TorchCuda

if ($Device -eq "cuda" -and -not $CudaWorks) {
    throw @"
CUDA was explicitly requested, but PyTorch cannot access it.

Run:

.\ai\benchmark\.venv\Scripts\python.exe -c "import torch; print(torch.__version__); print(torch.cuda.is_available())"

Expected:
True
"@
}

if ($Device -eq "auto" -and $HasNvidiaGpu -and -not $CudaWorks) {
    throw @"
An NVIDIA GPU was detected by Windows, but this benchmark environment has no working CUDA PyTorch.

Refusing to silently run the full benchmark on CPU.

Install the CUDA build with:

.\ai\benchmark\.venv\Scripts\python.exe -m pip uninstall torch -y

.\ai\benchmark\.venv\Scripts\python.exe -m pip install torch==2.14.0 --index-url https://download.pytorch.org/whl/cu126

Then verify:

.\ai\benchmark\.venv\Scripts\python.exe -c "import torch; print(torch.__version__); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO CUDA')"
"@
}

if ($CudaWorks) {
    Write-Host ""
    Write-Host "CUDA ready. Benchmark will use the NVIDIA GPU when device=auto." -ForegroundColor Green
}
elseif ($Device -eq "cpu") {
    Write-Host ""
    Write-Host "CPU benchmark explicitly selected." -ForegroundColor Yellow
}
else {
    Write-Host ""
    Write-Host "CUDA unavailable. Running on CPU." -ForegroundColor Yellow
}

# -------------------------------------------------------------------
# Runner arguments
# -------------------------------------------------------------------

$RunnerArgs = @(
    "-m",
    "ai.benchmark.run_qwen_benchmark",

    "--model",
    $Model,

    "--dataset",
    $Dataset,

    "--device",
    $Device,

    "--dtype",
    $Dtype,

    "--output",
    $Output,

    "--max-new-tokens",
    $MaxNewTokens
)

if ($Limit -gt 0) {
    $RunnerArgs += @(
        "--limit",
        $Limit
    )
}

if ($NoResume) {
    $RunnerArgs += "--no-resume"
}
else {
    $RunnerArgs += "--resume"
}

if ($LocalFilesOnly) {
    $RunnerArgs += "--local-files-only"
}

# -------------------------------------------------------------------
# Benchmark
# -------------------------------------------------------------------

Write-Host ""
Write-Host "Starting Tiqo Qwen benchmark..." -ForegroundColor Cyan

if ($Limit -gt 0) {
    Write-Host "Mode: smoke / limited ($Limit cases)" -ForegroundColor Yellow
}
else {
    Write-Host "Mode: full benchmark" -ForegroundColor Green
}

Write-Host "Model:   $Model"
Write-Host "Dataset: $Dataset"
Write-Host "Device:  $Device"
Write-Host "Dtype:   $Dtype"
Write-Host "Output:  $Output"
Write-Host ""

Push-Location $RepoRoot
$PreviousPythonUtf8 = $env:PYTHONUTF8
$env:PYTHONUTF8 = "1"

try {
    Invoke-CheckedNative `
        -File $VenvPython `
        -CommandArgs $RunnerArgs
}
finally {
    if ($null -eq $PreviousPythonUtf8) {
        Remove-Item Env:PYTHONUTF8 -ErrorAction SilentlyContinue
    }
    else {
        $env:PYTHONUTF8 = $PreviousPythonUtf8
    }

    Pop-Location
}
