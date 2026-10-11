# PowerShell build script
$ErrorActionPreference = "Stop"
Write-Host "Compiling Image Segmentation Java Project..." -ForegroundColor Cyan

$binDir = Join-Path $PSScriptRoot "bin"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir | Out-Null
}

$sourceFiles = (Get-ChildItem -Path (Join-Path $PSScriptRoot "src/main/java") -Filter *.java -Recurse).FullName
javac -d $binDir $sourceFiles
if ($LASTEXITCODE -ne 0) {
    throw "Java compilation failed with exit code $LASTEXITCODE."
}

Write-Host "[SUCCESS] Compilation completed successfully into bin/" -ForegroundColor Green
