param(
    [string]$Version = "0.0.1"
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$appRoot = Join-Path $repositoryRoot "packaging\1panel\aquafish"
$versionRoot = Join-Path $appRoot $Version
$composeFile = Join-Path $versionRoot "docker-compose.yml"
$versionDataFile = Join-Path $versionRoot "data.yml"
$requiredRootFiles = @(
    (Join-Path $appRoot "data.yml"),
    (Join-Path $appRoot "logo.png"),
    (Join-Path $appRoot "README.md"),
    (Join-Path $appRoot "README_en.md")
)

if (-not (Test-Path -LiteralPath $versionRoot -PathType Container)) {
    throw "1Panel version directory does not exist: $versionRoot"
}

foreach ($requiredFile in $requiredRootFiles + @($composeFile, $versionDataFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing required 1Panel package file: $requiredFile"
    }
}

$expectedImage = "ghcr.io/yubboo/aquafish:$Version"
$composeText = Get-Content -LiteralPath $composeFile -Raw -Encoding UTF8
if (-not $composeText.Contains("image: $expectedImage")) {
    throw "Compose image version must be: $expectedImage"
}

$outputDirectory = Join-Path $repositoryRoot "build\1panel"
$outputFile = Join-Path $outputDirectory "aquafish-$Version-1panel.zip"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

if (Test-Path -LiteralPath $outputFile) {
    Remove-Item -LiteralPath $outputFile -Force
}

Compress-Archive -LiteralPath $appRoot -DestinationPath $outputFile `
    -CompressionLevel Optimal

Write-Host "1Panel package created: $outputFile"
Write-Host "Container image: $expectedImage"
