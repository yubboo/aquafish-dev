param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$appRoot = Join-Path $repositoryRoot "packaging\1panel\aquafish"

# p.bat 的一键打包入口不要求开发者手工输入版本号。未显式传入版本时，
# 从标准语义化版本目录中选择最高版本；发布旧版本或指定版本时仍可使用
# -Version 0.0.1 明确覆盖。
if ([string]::IsNullOrWhiteSpace($Version)) {
    $versionDirectories = @(
        Get-ChildItem -LiteralPath $appRoot -Directory |
            Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
            Sort-Object { [System.Version]::Parse($_.Name) }
    )

    if ($versionDirectories.Count -eq 0) {
        throw "No semantic version directory found in: $appRoot"
    }

    $Version = $versionDirectories[-1].Name
}

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

$outputDirectory = Join-Path $repositoryRoot "packaging\1panel"
$outputFile = Join-Path $outputDirectory "aquafish-$Version-1panel.zip"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

if (Test-Path -LiteralPath $outputFile) {
    Remove-Item -LiteralPath $outputFile -Force
}

# Windows PowerShell 的 Compress-Archive 会在 ZIP 条目中保留反斜杠。
# Linux/1Panel 可能把反斜杠当作普通文件名字符，导致解压后无法形成目录。
# 这里直接创建 ZIP，并统一使用 ZIP 规范要求的正斜杠条目名。
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sourceRoot = $appRoot.TrimEnd([char[]]"\/")
$archive = [System.IO.Compression.ZipFile]::Open(
    $outputFile,
    [System.IO.Compression.ZipArchiveMode]::Create
)

try {
    $sourceFiles = @(
        Get-ChildItem -LiteralPath $appRoot -File -Recurse |
            Sort-Object FullName
    )

    foreach ($sourceFile in $sourceFiles) {
        $relativePath = $sourceFile.FullName.Substring($sourceRoot.Length)
        $relativePath = $relativePath.TrimStart([char[]]"\/")
        $entryName = "aquafish/" + ($relativePath -replace '\\', '/')

        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $sourceFile.FullName,
            $entryName,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally {
    $archive.Dispose()
}

Write-Host "1Panel package created: $outputFile"
Write-Host "Container image: $expectedImage"
