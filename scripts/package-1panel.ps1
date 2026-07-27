param(
    # 不填写时自动选择最高语义化版本目录。
    # 也可以手动指定：
    # -Version 0.0.3
    [string]$Version = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 项目根目录：
# H:\javaweb\aquafish
$repositoryRoot = Split-Path -Parent $PSScriptRoot

# 1Panel Aquafish 应用模板根目录。
$appRoot = Join-Path $repositoryRoot "packaging\1panel\aquafish"

if (-not (Test-Path -LiteralPath $appRoot -PathType Container)) {
    throw "1Panel application root does not exist: $appRoot"
}

# ============================================================
# 自动确定打包版本
#
# 未指定版本时，从以下目录中选择最高语义化版本：
#
# 0.0.2
# 0.0.3
# 0.0.4
# ============================================================
if ([string]::IsNullOrWhiteSpace($Version)) {
    $versionDirectories = @(
        Get-ChildItem -LiteralPath $appRoot -Directory |
            Where-Object {
                $_.Name -match '^\d+\.\d+\.\d+$'
            } |
            Sort-Object {
                [System.Version]::Parse($_.Name)
            }
    )

    if ($versionDirectories.Count -eq 0) {
        throw "No semantic version directory found in: $appRoot"
    }

    $Version = $versionDirectories[-1].Name
}

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Invalid semantic version: $Version"
}

$versionRoot = Join-Path $appRoot $Version
$composeFile = Join-Path $versionRoot "docker-compose.yml"
$versionDataFile = Join-Path $versionRoot "data.yml"

# 根目录必须存在的文件。
$requiredRootFiles = @(
    (Join-Path $appRoot "data.yml"),
    (Join-Path $appRoot "logo.png"),
    (Join-Path $appRoot "README.md"),
    (Join-Path $appRoot "README_en.md")
)

if (-not (Test-Path -LiteralPath $versionRoot -PathType Container)) {
    throw "1Panel version directory does not exist: $versionRoot"
}

# 检查应用包必需文件。
foreach ($requiredFile in $requiredRootFiles + @(
    $composeFile,
    $versionDataFile
)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing required 1Panel package file: $requiredFile"
    }
}

# ============================================================
# 校验当前版本绑定的正式 Docker Hub 镜像
# ============================================================
$expectedImage = "aqhub/aquafish:$Version"

$composeText = Get-Content `
    -LiteralPath $composeFile `
    -Raw `
    -Encoding UTF8

if (-not $composeText.Contains("image: $expectedImage")) {
    throw @"
Compose image version is incorrect.

Expected:
image: $expectedImage

File:
$composeFile
"@
}

# ============================================================
# 输出 ZIP
# ============================================================
$outputDirectory = Join-Path $repositoryRoot "packaging\1panel"
$outputFile = Join-Path `
    $outputDirectory `
    "aquafish-$Version-1panel.zip"

New-Item `
    -ItemType Directory `
    -Path $outputDirectory `
    -Force |
    Out-Null

if (Test-Path -LiteralPath $outputFile) {
    Remove-Item -LiteralPath $outputFile -Force
}

# Windows PowerShell Compress-Archive 可能保留反斜杠，
# Linux/1Panel 解压时可能无法形成正确目录结构。
#
# 这里直接使用 System.IO.Compression 创建 ZIP，
# 并强制把 ZIP 条目路径转换为正斜杠。
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sourceRoot = $appRoot.TrimEnd([char[]]"\/")

$archive = [System.IO.Compression.ZipFile]::Open(
    $outputFile,
    [System.IO.Compression.ZipArchiveMode]::Create
)

try {
    # 将 Aquafish 1Panel 应用目录中的全部版本一起打包。
    #
    # 例如：
    # aquafish/0.0.2/
    # aquafish/0.0.3/
    # aquafish/data.yml
    # aquafish/logo.png
    $sourceFiles = @(
        Get-ChildItem `
            -LiteralPath $appRoot `
            -File `
            -Recurse |
            Sort-Object FullName
    )

    foreach ($sourceFile in $sourceFiles) {
        $relativePath = $sourceFile.FullName.Substring(
            $sourceRoot.Length
        )

        $relativePath = $relativePath.TrimStart(
            [char[]]"\/"
        )

        # ZIP 内部统一使用：
        # aquafish/0.0.3/docker-compose.yml
        $entryName = "aquafish/" + (
            $relativePath -replace '\\', '/'
        )

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

Write-Host ""
Write-Host "Aquafish 1Panel package created successfully."
Write-Host "Version:         $Version"
Write-Host "Container image: $expectedImage"
Write-Host "Package file:    $outputFile"
Write-Host ""